package com.corelink.frame.editor.timeline

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
        val duration = clip.durationMs.coerceAtLeast(1000L)

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
}
