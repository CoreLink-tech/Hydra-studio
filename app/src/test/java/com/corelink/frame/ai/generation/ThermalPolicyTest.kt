package com.corelink.frame.ai.generation

import com.corelink.frame.hardware.ThermalStatus
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ThermalPolicyTest {

    @Test
    fun severeAndAbove_shouldPause() {
        for (status in listOf(
            ThermalStatus.SEVERE,
            ThermalStatus.CRITICAL,
            ThermalStatus.EMERGENCY,
            ThermalStatus.SHUTDOWN
        )) {
            val f = ThermalPolicy.flagsFor(status, isLowMemory = false)
            assertTrue("$status should pause", f.shouldPause)
            assertTrue("$status should throttle", f.shouldThrottle)
        }
    }

    @Test
    fun moderate_throttlesButDoesNotPause() {
        val f = ThermalPolicy.flagsFor(ThermalStatus.MODERATE, isLowMemory = false)
        assertFalse(f.shouldPause)
        assertTrue(f.shouldThrottle)
    }

    @Test
    fun noneAndLight_neitherPauseNorThrottleUnlessLowMemory() {
        for (status in listOf(ThermalStatus.NONE, ThermalStatus.LIGHT, ThermalStatus.UNKNOWN)) {
            val ok = ThermalPolicy.flagsFor(status, isLowMemory = false)
            assertFalse(ok.shouldPause)
            assertFalse(ok.shouldThrottle)
            val low = ThermalPolicy.flagsFor(status, isLowMemory = true)
            assertFalse(low.shouldPause)
            assertTrue(low.shouldThrottle)
        }
    }
}
