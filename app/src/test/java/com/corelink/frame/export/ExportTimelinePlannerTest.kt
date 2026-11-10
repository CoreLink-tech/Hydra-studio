package com.corelink.frame.export

import com.corelink.frame.data.local.entity.TimelineItemEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for trim/split/gap/volume planning.
 *
 * Manual device checks after a green assembleDebug:
 * 1. TRIM: place one clip, set sourceInMs=1000 sourceOutMs=3000 → export should
 *    be ~2s of the middle of the source, not the full file.
 * 2. SPLIT: split a 6s clip at 3s → two planned items with contiguous source ranges
 *    that do not each re-export the full media.
 * 3. GAP: leave 1s empty between two clips → plan.hasVideoGaps true; export must
 *    fail with the Media3 gap message (not silently close the gap).
 * 4. VOLUME: audio item volume=0.25 → PlannedClip.volume is 0.25 (gain applied
 *    in ExportEngine via ChannelMixingAudioProcessor).
 */
class ExportTimelinePlannerTest {

    @Test
    fun trim_usesSourceInAndOut_notFullFile() {
        val items = listOf(
            video(
                id = "v1",
                startMs = 0,
                durationMs = 2000,
                sourceInMs = 1000,
                sourceOutMs = 3000,
                path = "/clips/a.mp4"
            )
        )
        val plan = ExportTimelinePlanner.plan(items)
        assertEquals(1, plan.video.size)
        assertEquals(1000L, plan.video[0].sourceInMs)
        assertEquals(3000L, plan.video[0].sourceOutMs)
        assertEquals(2000L, plan.video[0].clippedDurationMs)

        val (start, end) = ExportTimelinePlanner.clippingFor(plan.video[0])
        assertEquals(1000L, start)
        assertEquals(3000L, end)
    }

    @Test
    fun split_producesTwoClipsWithAdjacentSourceRanges() {
        // Simulates TimelineRepository.splitItem at 3000ms on a 0–6000 source.
        val items = listOf(
            video(
                id = "left",
                startMs = 0,
                durationMs = 3000,
                sourceInMs = 0,
                sourceOutMs = 3000,
                path = "/clips/a.mp4"
            ),
            video(
                id = "right",
                startMs = 3000,
                durationMs = 3000,
                sourceInMs = 3000,
                sourceOutMs = 6000,
                path = "/clips/a.mp4"
            )
        )
        val plan = ExportTimelinePlanner.plan(items)
        assertEquals(2, plan.video.size)
        assertFalse(plan.hasVideoGaps)
        assertEquals(0L, plan.video[0].sourceInMs)
        assertEquals(3000L, plan.video[0].sourceOutMs)
        assertEquals(3000L, plan.video[1].sourceInMs)
        assertEquals(6000L, plan.video[1].sourceOutMs)
        // Each half is 3s of source — not a full 6s re-export per item.
        assertEquals(3000L, plan.video[0].clippedDurationMs)
        assertEquals(3000L, plan.video[1].clippedDurationMs)
    }

    @Test
    fun gap_betweenVideoClips_isDetected() {
        val items = listOf(
            video(id = "a", startMs = 0, durationMs = 2000, sourceInMs = 0, sourceOutMs = 2000, path = "/a.mp4"),
            video(id = "b", startMs = 5000, durationMs = 2000, sourceInMs = 0, sourceOutMs = 2000, path = "/b.mp4")
        )
        val plan = ExportTimelinePlanner.plan(items)
        assertTrue(plan.hasVideoGaps)
        assertEquals(listOf(3000L), plan.videoGapsMs)
    }

    @Test
    fun leadingGap_firstStartMsGreaterThanZero_isDetected() {
        val items = listOf(
            video(id = "a", startMs = 1500, durationMs = 2000, sourceInMs = 0, sourceOutMs = 2000, path = "/a.mp4")
        )
        val plan = ExportTimelinePlanner.plan(items)
        assertTrue(plan.hasVideoGaps)
        assertEquals(listOf(1500L), plan.videoGapsMs)
    }

    @Test
    fun leadingGap_audio_isDetected() {
        val items = listOf(
            TimelineItemEntity(
                id = "aud",
                projectId = "p",
                trackId = "t",
                mediaPath = "/music.mp3",
                label = "music",
                type = "AUDIO",
                startMs = 2000,
                durationMs = 4000,
                sourceInMs = 0,
                sourceOutMs = 4000,
                volume = 1f
            )
        )
        val plan = ExportTimelinePlanner.plan(items)
        assertTrue(plan.hasAudioGaps)
        assertEquals(listOf(2000L), plan.audioGapsMs)
    }

    @Test
    fun volume_isPreservedOnPlannedAudio() {
        val items = listOf(
            TimelineItemEntity(
                id = "aud",
                projectId = "p",
                trackId = "t",
                mediaPath = "/music.mp3",
                label = "music",
                type = "AUDIO",
                startMs = 0,
                durationMs = 4000,
                sourceInMs = 0,
                sourceOutMs = 4000,
                volume = 0.25f
            )
        )
        val plan = ExportTimelinePlanner.plan(items)
        assertEquals(1, plan.audio.size)
        assertEquals(0.25f, plan.audio[0].volume, 0.001f)
    }

    @Test
    fun textItems_flagUnsupportedTitles() {
        val items = listOf(
            video(id = "v", startMs = 0, durationMs = 1000, sourceInMs = 0, sourceOutMs = 1000, path = "/v.mp4"),
            TimelineItemEntity(
                id = "title",
                projectId = "p",
                trackId = "t",
                label = "Opening title",
                type = "TEXT",
                startMs = 0,
                durationMs = 2000,
                textContent = "Opening title"
            )
        )
        val plan = ExportTimelinePlanner.plan(items)
        assertTrue(plan.hasUnsupportedTitles)
        assertEquals(1, plan.textItems.size)
    }

    private fun video(
        id: String,
        startMs: Long,
        durationMs: Long,
        sourceInMs: Long,
        sourceOutMs: Long,
        path: String
    ) = TimelineItemEntity(
        id = id,
        projectId = "p",
        trackId = "vt",
        mediaPath = path,
        label = id,
        type = "VIDEO",
        startMs = startMs,
        durationMs = durationMs,
        sourceInMs = sourceInMs,
        sourceOutMs = sourceOutMs
    )
}
