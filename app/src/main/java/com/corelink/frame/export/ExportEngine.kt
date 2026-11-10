package com.corelink.frame.export

import android.content.Context
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.Transformer
import com.corelink.frame.core.files.FrameFileManager
import com.corelink.frame.data.local.dao.ExportTaskDao
import com.corelink.frame.data.local.dao.TimelineDao
import com.corelink.frame.data.local.entity.ExportStatus
import com.corelink.frame.data.local.entity.ExportTaskEntity
import com.corelink.frame.data.local.entity.TrackType
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * Builds an MP4 from the project timeline using Media3 Transformer when
 * real media files are present. Validates the output file before success.
 *
 * If the timeline has no usable media paths, fails with a clear message
 * instead of writing a fake file.
 */
@Singleton
class ExportEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val timelineDao: TimelineDao,
    private val exportTaskDao: ExportTaskDao,
    private val fileManager: FrameFileManager
) {
    private val tag = "ExportEngine"

    suspend fun startExport(
        projectId: String,
        config: ExportConfig,
        onProgress: (ExportProgress) -> Unit = {}
    ): String {
        val id = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        val task = ExportTaskEntity(
            id = id,
            projectId = projectId,
            outputFileName = config.fileName,
            width = config.width,
            height = config.height,
            frameRate = config.frameRate,
            videoBitrate = config.videoBitrate,
            status = ExportStatus.PENDING.name,
            stage = "queued",
            createdAt = now
        )
        exportTaskDao.insert(task)

        withContext(Dispatchers.Default) {
            runExport(task, config, onProgress)
        }
        return id
    }

    private suspend fun runExport(
        task: ExportTaskEntity,
        config: ExportConfig,
        onProgress: (ExportProgress) -> Unit
    ) {
        val started = System.currentTimeMillis()
        update(task.id) {
            it.copy(
                status = ExportStatus.RUNNING.name,
                stage = "preparing",
                startedAt = started
            )
        }
        onProgress(ExportProgress.Stage("preparing", "Reading timeline"))

        val items = timelineDao.observeItems(task.projectId).let {
            // one-shot read via getTracks + items
            timelineDao.getTracks(task.projectId)
            // Use projectDuration and collect items through a direct query path
        }
        // Direct query for items — use suspend helpers
        val allItems = withContext(Dispatchers.IO) {
            // TimelineDao has observe only; add a one-shot via existing project duration + track items
            val tracks = timelineDao.getTracks(task.projectId)
            tracks.flatMap { timelineDao.getItemsForTrack(it.id) }
                .sortedBy { it.startMs }
        }

        val videoItems = allItems.filter {
            it.type == TrackType.VIDEO.name && !it.mediaPath.isNullOrBlank()
        }
        val audioItems = allItems.filter {
            it.type == TrackType.AUDIO.name && !it.mediaPath.isNullOrBlank()
        }

        if (videoItems.isEmpty()) {
            fail(
                task.id,
                "No video clips with valid files on the timeline. " +
                    "Generate or import clips, add them in the editor, then export."
            )
            return
        }

        // Verify files exist
        val missing = videoItems.mapNotNull { it.mediaPath }.filter { !File(it).exists() }
        if (missing.isNotEmpty()) {
            fail(
                task.id,
                "Media files missing on disk (${missing.size}). Re-link clips or re-generate."
            )
            return
        }

        onProgress(ExportProgress.Stage("composing", "Building composition"))
        update(task.id) { it.copy(stage = "composing") }

        val outDir = fileManager.projectExportsDir(task.projectId)
        val outFile = File(outDir, config.fileName)
        if (outFile.exists()) outFile.delete()

        try {
            onProgress(ExportProgress.Stage("encoding", "Media3 Transformer"))
            update(task.id) { it.copy(stage = "encoding", progressPercent = 10) }

            val editedVideoItems = videoItems.map { item ->
                val mediaItem = MediaItem.fromUri(requireNotNull(item.mediaPath))
                EditedMediaItem.Builder(mediaItem).build()
            }
            val videoSequence = EditedMediaItemSequence(editedVideoItems)

            val sequences = mutableListOf(videoSequence)
            if (audioItems.isNotEmpty()) {
                val editedAudio = audioItems.map { item ->
                    EditedMediaItem.Builder(MediaItem.fromUri(requireNotNull(item.mediaPath))).build()
                }
                sequences.add(EditedMediaItemSequence(editedAudio))
            }

            val composition = Composition.Builder(sequences).build()

            val result = transform(composition, outFile.absolutePath)
            if (result != null) {
                fail(task.id, result)
                return
            }

            // Validate output
            onProgress(ExportProgress.Stage("validating", "Checking output file"))
            if (!outFile.exists() || outFile.length() < 1000L) {
                fail(task.id, "Export finished but output file is missing or too small.")
                return
            }

            val duration = allItems.maxOfOrNull { it.startMs + it.durationMs } ?: 0L
            val finished = System.currentTimeMillis()
            update(task.id) {
                it.copy(
                    status = ExportStatus.SUCCEEDED.name,
                    stage = "done",
                    progressPercent = 100,
                    outputPath = outFile.absolutePath,
                    fileSizeBytes = outFile.length(),
                    durationMs = duration,
                    finishedAt = finished
                )
            }
            onProgress(ExportProgress.Percent(100))
            onProgress(ExportProgress.Stage("done", outFile.absolutePath))
            Log.i(tag, "Export OK: ${outFile.absolutePath} (${outFile.length()} bytes)")
        } catch (e: Exception) {
            Log.e(tag, "Export failed", e)
            fail(task.id, e.message ?: "Export failed", e)
        }
    }

    /**
     * Runs Media3 Transformer and suspends until completion.
     * Returns an error message on failure, null on success.
     */
    private suspend fun transform(composition: Composition, outputPath: String): String? =
        suspendCancellableCoroutine { cont ->
            val transformer = Transformer.Builder(context)
                .setVideoMimeType(MimeTypes.VIDEO_H264)
                .setAudioMimeType(MimeTypes.AUDIO_AAC)
                .addListener(object : Transformer.Listener {
                    override fun onCompleted(composition: Composition, exportResult: androidx.media3.transformer.ExportResult) {
                        if (cont.isActive) cont.resume(null)
                    }

                    override fun onError(
                        composition: Composition,
                        exportResult: androidx.media3.transformer.ExportResult,
                        exportException: ExportException
                    ) {
                        if (cont.isActive) {
                            cont.resume(exportException.message ?: "Transformer error")
                        }
                    }
                })
                .build()

            cont.invokeOnCancellation {
                try {
                    transformer.cancel()
                } catch (_: Exception) {
                }
            }

            try {
                transformer.start(composition, outputPath)
            } catch (e: Exception) {
                if (cont.isActive) cont.resume(e.message ?: "Failed to start transformer")
            }
        }

    private suspend fun fail(taskId: String, message: String, cause: Throwable? = null) {
        if (cause != null) Log.e(tag, message, cause)
        update(taskId) {
            it.copy(
                status = ExportStatus.FAILED.name,
                stage = "failed",
                errorMessage = message,
                finishedAt = System.currentTimeMillis()
            )
        }
    }

    private suspend fun update(id: String, block: (ExportTaskEntity) -> ExportTaskEntity) {
        val current = exportTaskDao.getById(id) ?: return
        exportTaskDao.update(block(current))
    }
}
