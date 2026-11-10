package com.corelink.frame.ai.download

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ResumePlanTest {

    @Test
    fun emptyPartial_fromScratch() {
        assertEquals(ResumePlan.FromScratch, ResumePlan.decide(0, 1000, true))
        assertEquals(ResumePlan.FromScratch, ResumePlan.decide(-1, 1000, true))
    }

    @Test
    fun partialWithoutRange_fromScratch() {
        assertEquals(ResumePlan.FromScratch, ResumePlan.decide(500, 1000, false))
    }

    @Test
    fun partialWithRange_continue() {
        val plan = ResumePlan.decide(500, 1000, true)
        assertTrue(plan is ResumePlan.Continue)
        assertEquals(500L, (plan as ResumePlan.Continue).offset)
    }

    @Test
    fun exactSize_alreadyComplete() {
        assertEquals(ResumePlan.AlreadyComplete, ResumePlan.decide(1000, 1000, true))
    }

    @Test
    fun oversize_fromScratch() {
        assertEquals(ResumePlan.FromScratch, ResumePlan.decide(1200, 1000, true))
    }

    @Test
    fun unknownTotal_continues() {
        val plan = ResumePlan.decide(500, 0, true)
        assertTrue(plan is ResumePlan.Continue)
    }

    @Test
    fun rangeResponse_canAppend() {
        assertTrue(RangeResponse.canAppend(206, 4000, 4000))
        assertFalse(RangeResponse.canAppend(200, 4000, null))
        assertFalse(RangeResponse.canAppend(206, 4000, 0))
    }
}
