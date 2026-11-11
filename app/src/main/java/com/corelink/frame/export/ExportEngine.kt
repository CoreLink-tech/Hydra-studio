package com.corelink.frame.export

import android.content.Context
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.audio.ChannelMixingAudioProcessor
import androidx.media3.common.audio.ChannelMixingMatrix
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.Transformer
import com.corelink.frame.core.files.FrameFileManager
import com.corelink.frame.data.local.dao.ExportTaskDao
import com.corelink.frame.data.local.dao.TimelineDao
import com.corelink.frame.data.local.entity.ExportStatus
import com.corelink.frame.data.local.entity.ExportTaskEntity
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
 * Timeline fidelity (Media3 1.5.0):
 * - sourceInMs/sourceOutMs → [MediaItem.ClippingConfiguration]
 * - volume → [ChannelMixingAudioProcessor] gain matrix on the item Effects
 * - item order → sorted by startMs within each track type
 * - gaps / startMs offsets: NOT supported by EditedMediaItemSequence (concat only).
 *   Detected gaps cause a clear export failure (see [ExportTimelinePlanner]).
 * - TEXT items: not rendered; export fails with an explicit message.
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

        val allItems = withContext(Dispatchers.IO) {
            val tracks = timelineDao.getTracks(task.projectId)
            tracks.flatMap { timelineDao.getItemsForTrack(it.id) }
                .sortedBy { it.startMs }
        }

        val plan = ExportTimelinePlanner.plan(allItems)

        if (plan.hasUnsupportedTitles) {
            val labels = plan.textItems.map { it.label.ifBlank { it.textContent ?: it.id } }
            fail(
                task.id,
                "Export does not support title/subtitle items yet. " +
                    "Remove or flatten these TEXT items first: ${labels.joinToString()}"
            )
            return
        }

        if (plan.video.isEmpty()) {
            fail(
                task.id,
                "No video clips with valid files on the timeline. " +
                    "Generate or import clips, add them in the editor, then export."
            )
            return
        }

        // Media3 1.5 EditedMediaItemSequence concatenates only — no gap/startMs API.
        if (plan.hasVideoGaps || plan.hasAudioGaps) {
            fail(
                task.id,
                "Timeline has gaps between clips. Media3 Transformer 1.5.0 cannot insert " +
                    "silence/black between items in an EditedMediaItemSequence. " +
                    "Move clips so they abut on the timeline, or wait for gap-filler support. " +
                    "Video gaps ms=${plan.videoGapsMs}, audio gaps ms=${plan.audioGapsMs}."
            )
            return
        }

        val missing = (plan.video + plan.audio)
            .map { it.mediaPath }
            .filter { !File(it).exists() }
        if (missing.isNotEmpty()) {
            fail(
                task.id,
                "Media files missing on disk (${missing.size}). Re-link clips or re-generate."
            )
            return
        }

        onProgress(ExportProgress.Stage("composing", "Building composition with trims/volume"))
        update(task.id) { it.copy(stage = "composing") }

        val outDir = fileManager.projectExportsDir(task.projectId)
        val outFile = File(outDir, config.fileName)
        if (outFile.exists()) outFile.delete()

        try {
            onProgress(ExportProgress.Stage("encoding", "Media3 Transformer"))
            update(task.id) { it.copy(stage = "encoding", progressPercent = 10) }

            val editedVideo = plan.video.map { buildEditedItem(it) }
            val sequences = mutableListOf(
                EditedMediaItemSequence(editedVideo)
            )
            if (plan.audio.isNotEmpty()) {
                sequences.add(EditedMediaItemSequence(plan.audio.map { buildEditedItem(it) }))
            }

            val composition = Composition.Builder(sequences).build()

            val transformError = transform(composition, outFile.absolutePath)
            if (transformError != null) {
                fail(task.id, transformError)
                return
            }

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
     * Applies source in/out via ClippingConfiguration and volume via a channel-mix gain.
     */
    private fun buildEditedItem(clip: ExportTimelinePlanner.PlannedClip): EditedMediaItem {
        val (clipStart, clipEnd) = ExportTimelinePlanner.clippingFor(clip)
        val clippingBuilder = MediaItem.ClippingConfiguration.Builder()
            .setStartPositionMs(clipStart)
        if (clipEnd != null) {
            clippingBuilder.setEndPositionMs(clipEnd)
        }
        val mediaItem = MediaItem.Builder()
            .setUri(clip.mediaPath)
            .setClippingConfiguration(clippingBuilder.build())
            .build()

        val builder = EditedMediaItem.Builder(mediaItem)
        if (clip.volume < 0.999f) {
            builder.setEffects(
                Effects(
                    /* audioProcessors = */ listOf(volumeProcessor(clip.volume)),
                    /* videoEffects = */ emptyList()
                )
            )
        }
        return builder.build()
    }

    /**
     * Constant-gain channel mixer. Works for mono/stereo input once configured
     * by the transformer pipeline when formats become available.
     */
    private fun volumeProcessor(volume: Float): AudioProcessor {
        val gain = volume.coerceIn(0f, 1f)
        val processor = ChannelMixingAudioProcessor()
        // Pre-register common channel counts with a constant-gain diagonal matrix.
        for (channels in 1..8) {
            val coeffs = FloatArray(channels * channels)
            for (i in 0 until channels) {
                coeffs[i * channels + i] = gain
            }
            processor.putChannelMixingMatrix(
                ChannelMixingMatrix(channels, channels, coeffs)
            )
        }
        return processor
    }

    private suspend fun transform(composition: Composition, outputPath: String): String? =
        suspendCancellableCoroutine { cont ->
            val transformer = Transformer.Builder(context)
                .setVideoMimeType(MimeTypes.VIDEO_H264)
                .setAudioMimeType(MimeTypes.AUDIO_AAC)
                .addListener(object : Transformer.Listener {
                    override fun onCompleted(
                        composition: Composition,
                        exportResult: androidx.media3.transformer.ExportResult
                    ) {
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
