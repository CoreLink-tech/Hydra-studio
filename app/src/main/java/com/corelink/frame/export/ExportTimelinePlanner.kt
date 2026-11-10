package com.corelink.frame.export

import com.corelink.frame.data.local.entity.TimelineItemEntity
import com.corelink.frame.data.local.entity.TrackType

/**
 * Pure planning for export composition. No Media3 types so unit tests can run
 * without Android instrumentation.
 *
 * Media3 Transformer 1.5.0 [EditedMediaItemSequence] only concatenates items
 * back-to-back. It has no gap/startMs API. Gaps are therefore detected and
 * reported; callers decide whether to fail or continue without padding.
 */
object ExportTimelinePlanner {

    data class PlannedClip(
        val itemId: String,
        val mediaPath: String,
        val label: String,
        val sourceInMs: Long,
        val sourceOutMs: Long,
        val startMs: Long,
        val durationMs: Long,
        val volume: Float,
        val type: String
    ) {
        /** Clipped media duration that should be fed to the encoder. */
        val clippedDurationMs: Long get() = (sourceOutMs - sourceInMs).coerceAtLeast(0L)
    }

    data class PlanResult(
        val video: List<PlannedClip>,
        val audio: List<PlannedClip>,
        val textItems: List<TimelineItemEntity>,
        /** Gaps between consecutive video items on the timeline (ms). */
        val videoGapsMs: List<Long>,
        /** Gaps between consecutive audio items on the timeline (ms). */
        val audioGapsMs: List<Long>
    ) {
        val hasUnsupportedTitles: Boolean get() = textItems.isNotEmpty()
        val hasVideoGaps: Boolean get() = videoGapsMs.any { it > GAP_TOLERANCE_MS }
        val hasAudioGaps: Boolean get() = audioGapsMs.any { it > GAP_TOLERANCE_MS }
    }

    const val GAP_TOLERANCE_MS = 50L

    fun plan(items: List<TimelineItemEntity>): PlanResult {
        val textItems = items.filter { it.type == TrackType.TEXT.name }

        val video = items
            .filter { it.type == TrackType.VIDEO.name && !it.mediaPath.isNullOrBlank() }
            .sortedBy { it.startMs }
            .map { it.toPlanned() }

        val audio = items
            .filter { it.type == TrackType.AUDIO.name && !it.mediaPath.isNullOrBlank() }
            .sortedBy { it.startMs }
            .map { it.toPlanned() }

        return PlanResult(
            video = video,
            audio = audio,
            textItems = textItems,
            videoGapsMs = computeGaps(video),
            audioGapsMs = computeGaps(audio)
        )
    }

    /**
     * Builds ClippingConfiguration-equivalent values for a planned clip.
     * sourceOutMs of 0 with sourceInMs 0 means "use full media" when duration is unknown.
     */
    fun clippingFor(clip: PlannedClip): Pair<Long, Long?> {
        val start = clip.sourceInMs.coerceAtLeast(0L)
        val end = when {
            clip.sourceOutMs > start -> clip.sourceOutMs
            clip.durationMs > 0 -> start + clip.durationMs
            else -> null // open-ended: start only
        }
        return start to end
    }

    private fun TimelineItemEntity.toPlanned(): PlannedClip {
        val path = requireNotNull(mediaPath)
        val inMs = sourceInMs.coerceAtLeast(0L)
        val outMs = when {
            sourceOutMs > inMs -> sourceOutMs
            durationMs > 0 -> inMs + durationMs
            else -> inMs
        }
        return PlannedClip(
            itemId = id,
            mediaPath = path,
            label = label,
            sourceInMs = inMs,
            sourceOutMs = outMs,
            startMs = startMs.coerceAtLeast(0L),
            durationMs = durationMs.coerceAtLeast(0L),
            volume = volume.coerceIn(0f, 1f),
            type = type
        )
    }

    private fun computeGaps(clips: List<PlannedClip>): List<Long> {
        if (clips.isEmpty()) return emptyList()
        val gaps = mutableListOf<Long>()
        // Leading gap: first clip does not start at 0
        if (clips.first().startMs > GAP_TOLERANCE_MS) {
            gaps += clips.first().startMs
        }
        for (i in 0 until clips.lastIndex) {
            val end = clips[i].startMs + clips[i].clippedDurationMs.coerceAtLeast(clips[i].durationMs)
            val nextStart = clips[i + 1].startMs
            gaps += (nextStart - end).coerceAtLeast(0L)
        }
        return gaps
    }
}
