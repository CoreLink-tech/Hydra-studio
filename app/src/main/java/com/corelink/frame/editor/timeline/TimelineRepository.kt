package com.corelink.frame.editor.timeline

import android.media.MediaMetadataRetriever
import com.corelink.frame.data.local.dao.ClipDao
import com.corelink.frame.data.local.dao.TimelineDao
import com.corelink.frame.data.local.entity.ClipEntity
import com.corelink.frame.data.local.entity.TimelineItemEntity
import com.corelink.frame.data.local.entity.TimelineTrackEntity
import com.corelink.frame.data.local.entity.TrackType
import kotlinx.coroutines.flow.Flow
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TimelineRepository @Inject constructor(
    private val timelineDao: TimelineDao,
    private val clipDao: ClipDao
) {
    fun observeTracks(projectId: String): Flow<List<TimelineTrackEntity>> =
        timelineDao.observeTracks(projectId)

    fun observeItems(projectId: String): Flow<List<TimelineItemEntity>> =
        timelineDao.observeItems(projectId)

    /**
     * Ensure a project has at least one video, one audio, and one text track.
     */
    suspend fun ensureDefaultTracks(projectId: String) {
        val existing = timelineDao.getTracks(projectId)
        if (existing.isNotEmpty()) return
        val defaults = listOf(
            TrackType.VIDEO to "Video 1",
            TrackType.AUDIO to "Audio 1",
            TrackType.TEXT to "Titles"
        )
        defaults.forEachIndexed { index, (type, name) ->
            timelineDao.insertTrack(
                TimelineTrackEntity(
                    id = UUID.randomUUID().toString(),
                    projectId = projectId,
                    name = name,
                    type = type.name,
                    sortOrder = index
                )
            )
        }
    }

    /**
     * Place a generated/imported clip on the end of the primary video track.
     * Non-destructive: source in/out equal full clip duration.
     */
    suspend fun appendClipToVideoTrack(projectId: String, clip: ClipEntity): TimelineItemEntity {
        ensureDefaultTracks(projectId)
        val tracks = timelineDao.getTracks(projectId)
        val videoTrack = tracks.firstOrNull { it.type == TrackType.VIDEO.name }
            ?: tracks.first()

        val projectEnd = timelineDao.projectDurationMs(projectId)
        // Prefer real media duration over recorded/placeholder values.
        val duration = resolveMediaDurationMs(clip.filePath, clip.durationMs)

        val item = TimelineItemEntity(
            id = UUID.randomUUID().toString(),
            projectId = projectId,
            trackId = videoTrack.id,
            clipId = clip.id,
            mediaPath = clip.filePath,
            label = clip.name,
            type = TrackType.VIDEO.name,
            startMs = projectEnd,
            durationMs = duration,
            sourceInMs = 0L,
            sourceOutMs = duration,
            sortOrder = 0
        )
        timelineDao.insertItem(item)
        return item
    }

    /**
     * Read duration from the media file. Falls back to [fallbackMs] (then 1000 ms)
     * when the retriever fails or the path is empty.
     */
    fun resolveMediaDurationMs(path: String?, fallbackMs: Long): Long {
        val floor = fallbackMs.coerceAtLeast(1000L)
        if (path.isNullOrBlank()) return floor
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(path)
            val meta = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            val parsed = meta?.toLongOrNull()
            if (parsed != null && parsed > 0L) parsed else floor
        } catch (_: Exception) {
            floor
        } finally {
            try {
                retriever.release()
            } catch (_: Exception) {
                // ignore
            }
        }
    }

    suspend fun trimItem(itemId: String, newSourceInMs: Long, newSourceOutMs: Long) {
        val item = timelineDao.getItem(itemId) ?: return
        val inMs = newSourceInMs.coerceAtLeast(0L)
        val outMs = newSourceOutMs.coerceAtLeast(inMs + 100L)
        val newDuration = outMs - inMs
        timelineDao.updateItem(
            item.copy(
                sourceInMs = inMs,
                sourceOutMs = outMs,
                durationMs = newDuration
            )
        )
    }

    /**
     * Split an item at a timeline position (absolute ms).
     * Creates a second item; does not alter source files.
     */
    suspend fun splitItem(itemId: String, splitAtTimelineMs: Long) {
        val item = timelineDao.getItem(itemId) ?: return
        if (splitAtTimelineMs <= item.startMs || splitAtTimelineMs >= item.startMs + item.durationMs) return

        val offsetInItem = splitAtTimelineMs - item.startMs
        val leftDuration = offsetInItem
        val rightDuration = item.durationMs - offsetInItem
        val splitSource = item.sourceInMs + offsetInItem

        timelineDao.updateItem(
            item.copy(
                durationMs = leftDuration,
                sourceOutMs = splitSource
            )
        )
        timelineDao.insertItem(
            item.copy(
                id = UUID.randomUUID().toString(),
                startMs = splitAtTimelineMs,
                durationMs = rightDuration,
                sourceInMs = splitSource,
                sourceOutMs = item.sourceOutMs
            )
        )
    }

    suspend fun moveItem(itemId: String, newStartMs: Long) {
        val item = timelineDao.getItem(itemId) ?: return
        timelineDao.updateItem(item.copy(startMs = newStartMs.coerceAtLeast(0L)))
    }

    suspend fun deleteItem(itemId: String) {
        timelineDao.deleteItem(itemId)
    }

    suspend fun projectDurationMs(projectId: String): Long =
        timelineDao.projectDurationMs(projectId)

    suspend fun addTitle(
        projectId: String,
        text: String,
        startMs: Long,
        durationMs: Long = 3000L
    ): TimelineItemEntity {
        ensureDefaultTracks(projectId)
        val tracks = timelineDao.getTracks(projectId)
        val textTrack = tracks.firstOrNull { it.type == TrackType.TEXT.name } ?: tracks.last()
        val item = TimelineItemEntity(
            id = UUID.randomUUID().toString(),
            projectId = projectId,
            trackId = textTrack.id,
            label = text.take(32),
            type = TrackType.TEXT.name,
            startMs = startMs.coerceAtLeast(0L),
            durationMs = durationMs.coerceAtLeast(500L),
            sourceInMs = 0L,
            sourceOutMs = durationMs,
            textContent = text
        )
        timelineDao.insertItem(item)
        return item
    }

    suspend fun addAudio(
        projectId: String,
        name: String,
        mediaPath: String,
        durationMs: Long,
        startMs: Long = -1L,
        volume: Float = 1f
    ): TimelineItemEntity {
        ensureDefaultTracks(projectId)
        val tracks = timelineDao.getTracks(projectId)
        val audioTrack = tracks.firstOrNull { it.type == TrackType.AUDIO.name }
            ?: tracks.first()
        val start = if (startMs >= 0) startMs else timelineDao.projectDurationMs(projectId)
        val dur = durationMs.coerceAtLeast(500L)
        val item = TimelineItemEntity(
            id = UUID.randomUUID().toString(),
            projectId = projectId,
            trackId = audioTrack.id,
            mediaPath = mediaPath,
            label = name,
            type = TrackType.AUDIO.name,
            startMs = start,
            durationMs = dur,
            sourceInMs = 0L,
            sourceOutMs = dur,
            volume = volume.coerceIn(0f, 1f)
        )
        timelineDao.insertItem(item)
        return item
    }

    suspend fun setVolume(itemId: String, volume: Float) {
        val item = timelineDao.getItem(itemId) ?: return
        timelineDao.updateItem(item.copy(volume = volume.coerceIn(0f, 1f)))
    }

    suspend fun setTextContent(itemId: String, text: String) {
        val item = timelineDao.getItem(itemId) ?: return
        timelineDao.updateItem(
            item.copy(
                textContent = text,
                label = text.take(32)
            )
        )
    }

    suspend fun setFadeHints(itemId: String, fadeInMs: Long, fadeOutMs: Long) {
        // Encoded as volume envelope metadata in label prefix for Milestone 7 simplicity.
        // Full keyframe envelopes land with export engine.
        val item = timelineDao.getItem(itemId) ?: return
        val meta = "fade:${fadeInMs.coerceAtLeast(0)}:${fadeOutMs.coerceAtLeast(0)}"
        val base = item.label.substringAfter(" | ").ifBlank { item.label }
        timelineDao.updateItem(item.copy(label = if (item.type == "AUDIO") "$meta | $base" else item.label))
    }

}
