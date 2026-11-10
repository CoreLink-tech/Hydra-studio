package com.corelink.frame.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Timing math for FrameEncoder — no MediaCodec / Android framework required.
 *
 * Device manual check: encode 16 frames at 8 fps → player duration ≈ 2000 ms;
 * if encode throws, the partial output file must be deleted.
 */
class FrameEncoderTimingTest {

    @Test
    fun presentationTimeUs_isMonotonicAndMatchesFrameRate() {
        val fps = 8
        assertEquals(0L, FrameEncoder.presentationTimeUs(0, fps))
        assertEquals(125_000L, FrameEncoder.presentationTimeUs(1, fps))
        assertEquals(250_000L, FrameEncoder.presentationTimeUs(2, fps))
        assertEquals(1_000_000L, FrameEncoder.presentationTimeUs(8, fps))
        for (i in 1 until 32) {
            assertTrue(
                FrameEncoder.presentationTimeUs(i, fps) >
                    FrameEncoder.presentationTimeUs(i - 1, fps)
            )
        }
    }

    @Test
    fun expectedDurationMs_matchesFramesOverFrameRate() {
        assertEquals(2000L, FrameEncoder.expectedDurationMs(16, 8))
        assertEquals(1000L, FrameEncoder.expectedDurationMs(30, 30))
        assertEquals(0L, FrameEncoder.expectedDurationMs(0, 8))
        // 17 frames at 8 fps → floor(17*1000/8) = 2125
        assertEquals(2125L, FrameEncoder.expectedDurationMs(17, 8))
    }
}
