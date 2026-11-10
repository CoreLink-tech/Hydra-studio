package com.corelink.frame.editor.timeline

import com.corelink.frame.data.local.entity.TimelineItemEntity
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pure timeline edit math (mirrors TimelineRepository trim/split/move rules)
 * without Room.
 */
class TimelineEditLogicTest {

    @Test
    fun trim_updatesSourceRangeAndDuration() {
        val item = base(sourceInMs = 0, sourceOutMs = 5000, durationMs = 5000, startMs = 0)
        val trimmed = trim(item, newIn = 1000, newOut = 3000)
        assertEquals(1000L, trimmed.sourceInMs)
        assertEquals(3000L, trimmed.sourceOutMs)
        assertEquals(2000L, trimmed.durationMs)
        assertEquals(0L, trimmed.startMs) // start unchanged by trim
    }

    @Test
    fun split_producesAdjacentPieces() {
        val item = base(sourceInMs = 0, sourceOutMs = 6000, durationMs = 6000, startMs = 0)
        val (left, right) = split(item, splitAtTimelineMs = 3000)
        assertEquals(3000L, left.durationMs)
        assertEquals(3000L, left.sourceOutMs)
        assertEquals(3000L, right.startMs)
        assertEquals(3000L, right.sourceInMs)
        assertEquals(6000L, right.sourceOutMs)
        assertEquals(3000L, right.durationMs)
    }

    @Test
    fun move_changesStartOnly() {
        val item = base(sourceInMs = 0, sourceOutMs = 2000, durationMs = 2000, startMs = 1000)
        val moved = item.copy(startMs = 5000L)
        assertEquals(5000L, moved.startMs)
        assertEquals(2000L, moved.durationMs)
        assertEquals(0L, moved.sourceInMs)
    }

    private fun base(
        sourceInMs: Long,
        sourceOutMs: Long,
        durationMs: Long,
        startMs: Long
    ) = TimelineItemEntity(
        id = "i1",
        projectId = "p",
        trackId = "t",
        mediaPath = "/a.mp4",
        label = "clip",
        type = "VIDEO",
        startMs = startMs,
        durationMs = durationMs,
        sourceInMs = sourceInMs,
        sourceOutMs = sourceOutMs
    )

    private fun trim(item: TimelineItemEntity, newIn: Long, newOut: Long): TimelineItemEntity {
        val inMs = newIn.coerceAtLeast(0L)
        val outMs = newOut.coerceAtLeast(inMs + 100L)
        return item.copy(sourceInMs = inMs, sourceOutMs = outMs, durationMs = outMs - inMs)
    }

    private fun split(
        item: TimelineItemEntity,
        splitAtTimelineMs: Long
    ): Pair<TimelineItemEntity, TimelineItemEntity> {
        val offset = splitAtTimelineMs - item.startMs
        val splitSource = item.sourceInMs + offset
        val left = item.copy(
            durationMs = offset,
            sourceOutMs = splitSource
        )
        val right = item.copy(
            id = "i2",
            startMs = splitAtTimelineMs,
            durationMs = item.durationMs - offset,
            sourceInMs = splitSource,
            sourceOutMs = item.sourceOutMs
        )
        return left to right
    }
}
