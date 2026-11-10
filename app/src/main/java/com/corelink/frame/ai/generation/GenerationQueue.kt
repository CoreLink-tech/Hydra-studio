package com.corelink.frame.ai.generation

import android.graphics.BitmapFactory
import com.corelink.frame.ai.models.GenerationMode
import com.corelink.frame.ai.runtime.InferenceRuntime
import com.corelink.frame.core.files.FrameFileManager
import com.corelink.frame.data.local.dao.GenerationTaskDao
import com.corelink.frame.data.local.entity.GenerationTaskEntity
import com.corelink.frame.data.local.entity.TaskStatus
import com.corelink.frame.ai.download.ModelDownloadManager
import com.corelink.frame.ai.preprocessing.ImagePreprocessor
import com.corelink.frame.media.FrameEncoder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single-flight generation queue.
 * On low/mid-range devices only one heavy generation job runs at a time.
 * Progress is stage-based unless a real percentage is known.
 */
@Singleton
class GenerationQueue @Inject constructor(
    private val taskDao: GenerationTaskDao,
    private val runtime: InferenceRuntime,
    private val preprocessor: ImagePreprocessor,
    private val encoder: FrameEncoder,
    private val downloadManager: ModelDownloadManager,
    private val fileManager: FrameFileManager,
    private val resourceMonitor: ResourceMonitor,
    private val textToVideoPipeline: TextToVideoPipeline
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val mutex = Mutex()
    private var workerJob: Job? = null
    private val cancellingIds = mutableSetOf<String>()

    private val _activeTaskId = MutableStateFlow<String?>(null)
    val activeTaskId: StateFlow<String?> = _activeTaskId.asStateFlow()

    fun enqueueImageToVideo(
        modelId: String,
        motionPrompt: String,
        inputImagePath: String,
        targetFrames: Int = 16,
        targetWidth: Int = 512,
        targetHeight: Int = 512,
        projectId: String? = null,
        negativePrompt: String = ""
    ): String {
        val id = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        val task = GenerationTaskEntity(
            id = id,
            modelId = modelId,
            mode = GenerationMode.IMAGE_TO_VIDEO.name,
            motionPrompt = motionPrompt,
            negativePrompt = negativePrompt,
            inputImagePath = inputImagePath,
            targetFrames = targetFrames,
            targetWidth = targetWidth,
            targetHeight = targetHeight,
            status = TaskStatus.QUEUED.name,
            stage = "queued",
            createdAt = now,
            projectId = projectId
        )
        scope.launch {
            taskDao.insert(task)
            ensureWorker()
        }
        return id
    }


    fun enqueueTextToVideo(
        modelId: String,
        prompt: String,
        negativePrompt: String = "",
        motionPrompt: String = "",
        targetFrames: Int = 16,
        targetWidth: Int = 512,
        targetHeight: Int = 512,
        projectId: String? = null
    ): String {
        val id = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        val task = GenerationTaskEntity(
            id = id,
            modelId = modelId,
            mode = GenerationMode.TEXT_TO_VIDEO.name,
            prompt = prompt,
            motionPrompt = motionPrompt,
            negativePrompt = negativePrompt,
            targetFrames = targetFrames,
            targetWidth = targetWidth,
            targetHeight = targetHeight,
            status = TaskStatus.QUEUED.name,
            stage = "queued",
            createdAt = now,
            projectId = projectId
        )
        scope.launch {
            taskDao.insert(task)
            ensureWorker()
        }
        return id
    }

    fun requestCancel(taskId: String) {
        scope.launch {
            cancellingIds += taskId
            val task = taskDao.getById(taskId) ?: return@launch
            if (task.status == TaskStatus.QUEUED.name) {
                taskDao.update(
                    task.copy(
                        status = TaskStatus.CANCELLED.name,
                        stage = "cancelled",
                        finishedAt = System.currentTimeMillis()
                    )
                )
            } else if (task.status == TaskStatus.RUNNING.name) {
                taskDao.update(
                    task.copy(
                        status = TaskStatus.CANCELLING.name,
                        stage = "cancelling"
                    )
                )
            }
        }
    }

    private fun ensureWorker() {
        if (workerJob?.isActive == true) return
        workerJob = scope.launch {
            processLoop()
        }
    }

    private suspend fun processLoop() {
        while (true) {
            val next = mutex.withLock {
                if (taskDao.countRunning() > 0) return@withLock null
                taskDao.getNextQueued()
            } ?: break

            runTask(next)
        }
        _activeTaskId.value = null
    }

    private suspend fun runTask(task: GenerationTaskEntity) {
        _activeTaskId.value = task.id
        val started = System.currentTimeMillis()
        taskDao.update(
            task.copy(
                status = TaskStatus.RUNNING.name,
                stage = "starting",
                startedAt = started
            )
        )

        // Resource gate
        val resources = resourceMonitor.snapshot()
        if (resources.shouldPause) {
            fail(task, FailureReason.INSUFFICIENT_MEMORY,
                "Device thermal state is too high (${resources.thermalStatus}). Try again when cooler.")
            return
        }
        if (resources.isLowMemory && resources.availableRamMb < 400) {
            fail(task, FailureReason.INSUFFICIENT_MEMORY,
                "Available RAM is only ${resources.availableRamMb} MB. Free memory and retry.")
            return
        }

        if (cancellingIds.contains(task.id)) {
            cancel(task)
            return
        }

        if (task.mode == GenerationMode.TEXT_TO_VIDEO.name) {
            try {
                val result = textToVideoPipeline.generate(
                    TextToVideoPipeline.Request(
                        prompt = task.prompt,
                        negativePrompt = task.negativePrompt,
                        motionPrompt = task.motionPrompt,
                        targetFrames = task.targetFrames,
                        targetWidth = task.targetWidth,
                        targetHeight = task.targetHeight
                    )
                ) { progress ->
                    when (progress) {
                        is GenerationProgress.Stage -> {
                            scope.launch { updateStage(task.id, progress.name, progress.detail ?: "") }
                        }
                        else -> {}
                    }
                }
                when (result) {
                    is GenerationResult.Success -> {
                        val finished = System.currentTimeMillis()
                        val current = taskDao.getById(task.id) ?: return
                        taskDao.update(
                            current.copy(
                                status = TaskStatus.SUCCEEDED.name,
                                stage = "done",
                                progressPercent = 100,
                                outputPath = result.outputFile.absolutePath,
                                generationTimeMs = result.generationTimeMs,
                                finishedAt = finished
                            )
                        )
                    }
                    is GenerationResult.Failure -> {
                        if (result.reason == FailureReason.CANCELLED || cancellingIds.contains(task.id)) {
                            cancel(task)
                        } else {
                            fail(task, result.reason, result.message)
                        }
                    }
                }
            } catch (e: Exception) {
                fail(task, FailureReason.UNKNOWN, e.message ?: "T2V error")
            } finally {
                cancellingIds.remove(task.id)
                _activeTaskId.value = null
                ensureWorker()
            }
            return
        }

        val generator = OnDeviceVideoGenerator(
            runtime = runtime,
            preprocessor = preprocessor,
            encoder = encoder,
            downloadManager = downloadManager,
            fileManager = fileManager,
            modelId = task.modelId
        )

        try {
            generator.prepare()
            updateStage(task.id, "prepared", "Model path checked")

            val imagePath = task.inputImagePath
            if (imagePath.isNullOrBlank()) {
                fail(task, FailureReason.INVALID_INPUT, "No input image path")
                return
            }
            val bitmap = BitmapFactory.decodeFile(imagePath)
            if (bitmap == null) {
                fail(task, FailureReason.INVALID_INPUT, "Could not decode input image")
                return
            }

            if (cancellingIds.contains(task.id)) {
                cancel(task)
                return
            }

            val request = ImageToVideoRequest(
                inputImage = bitmap,
                motionPrompt = task.motionPrompt,
                negativePrompt = task.negativePrompt,
                targetFrames = task.targetFrames,
                targetWidth = task.targetWidth,
                targetHeight = task.targetHeight
            )

            val result = generator.generate(request) { progress ->
                when (progress) {
                    is GenerationProgress.Stage -> {
                        scope.launch {
                            updateStage(task.id, progress.name, progress.detail ?: "")
                        }
                    }
                    is GenerationProgress.FramesProduced -> {
                        val pct = progress.totalHint?.let {
                            if (it > 0) ((progress.count * 100) / it).coerceIn(0, 99) else null
                        }
                        scope.launch {
                            val current = taskDao.getById(task.id) ?: return@launch
                            taskDao.update(
                                current.copy(
                                    stage = "frames",
                                    stageDetail = "${progress.count} frames",
                                    progressPercent = pct
                                )
                            )
                        }
                    }
                    is GenerationProgress.MemoryHint -> {
                        // optional logging
                    }
                }
            }

            when (result) {
                is GenerationResult.Success -> {
                    val finished = System.currentTimeMillis()
                    val current = taskDao.getById(task.id) ?: return
                    taskDao.update(
                        current.copy(
                            status = TaskStatus.SUCCEEDED.name,
                            stage = "done",
                            stageDetail = "MP4 ready",
                            progressPercent = 100,
                            outputPath = result.outputFile.absolutePath,
                            generationTimeMs = result.generationTimeMs,
                            finishedAt = finished
                        )
                    )
                }
                is GenerationResult.Failure -> {
                    if (result.reason == FailureReason.CANCELLED || cancellingIds.contains(task.id)) {
                        cancel(task)
                    } else {
                        fail(task, result.reason, result.message)
                    }
                }
            }
        } catch (e: Exception) {
            fail(task, FailureReason.UNKNOWN, e.message ?: "Unexpected error")
        } finally {
            cancellingIds.remove(task.id)
            try {
                generator.release()
            } catch (_: Exception) {
            }
            _activeTaskId.value = null
            // Kick worker for next queued item
            ensureWorker()
        }
    }

    private suspend fun updateStage(taskId: String, stage: String, detail: String) {
        val current = taskDao.getById(taskId) ?: return
        if (current.status == TaskStatus.CANCELLING.name || current.status == TaskStatus.CANCELLED.name) return
        taskDao.update(
            current.copy(stage = stage, stageDetail = detail)
        )
    }

    private suspend fun fail(task: GenerationTaskEntity, reason: FailureReason, message: String) {
        val current = taskDao.getById(task.id) ?: task
        taskDao.update(
            current.copy(
                status = TaskStatus.FAILED.name,
                stage = "failed",
                stageDetail = message,
                errorReason = reason.name,
                errorMessage = message,
                finishedAt = System.currentTimeMillis()
            )
        )
    }

    private suspend fun cancel(task: GenerationTaskEntity) {
        val current = taskDao.getById(task.id) ?: task
        taskDao.update(
            current.copy(
                status = TaskStatus.CANCELLED.name,
                stage = "cancelled",
                stageDetail = "Cancelled by user",
                finishedAt = System.currentTimeMillis()
            )
        )
    }
}
