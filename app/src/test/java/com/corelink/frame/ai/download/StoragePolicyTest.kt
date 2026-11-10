package com.corelink.frame.ai.download

import org.junit.Assert.assertEquals
import org.junit.Test

class StoragePolicyTest {

    @Test
    fun enoughSpace_ok() {
        val free = 10L * 1024 * 1024 * 1024
        val model = 500L * 1024 * 1024
        assertEquals(StoragePolicy.Verdict.OK, StoragePolicy.check(free, model))
    }

    @Test
    fun lowButSafe_warn() {
        // After download: 2 GiB free → WARN
        val model = 1L * 1024 * 1024 * 1024
        val free = 3L * 1024 * 1024 * 1024
        assertEquals(StoragePolicy.Verdict.WARN_LOW_SPACE, StoragePolicy.check(free, model))
    }

    @Test
    fun tooTight_blocked() {
        val model = 2L * 1024 * 1024 * 1024
        val free = 2L * 1024 * 1024 * 1024 + 100
        assertEquals(StoragePolicy.Verdict.BLOCKED_INSUFFICIENT, StoragePolicy.check(free, model))
    }

    @Test
    fun alreadyHaveReducesNeed() {
        val free = 2L * 1024 * 1024 * 1024
        val model = 2L * 1024 * 1024 * 1024
        val already = 2L * 1024 * 1024 * 1024
        // needed = 0 → after = free → OK if free >= 3GiB? free is 2GiB → WARN
        assertEquals(
            StoragePolicy.Verdict.WARN_LOW_SPACE,
            StoragePolicy.check(free, model, already)
        )
    }

    @Test
    fun networkWifiOnly_waits() {
        assertEquals(
            NetworkPolicy.Decision.WAIT_FOR_WIFI,
            NetworkPolicy.decide(onWifi = false, wifiOnly = true, userAllowsCellular = false)
        )
        assertEquals(
            NetworkPolicy.Decision.PROCEED,
            NetworkPolicy.decide(onWifi = true, wifiOnly = true, userAllowsCellular = false)
        )
        assertEquals(
            NetworkPolicy.Decision.PROCEED,
            NetworkPolicy.decide(onWifi = false, wifiOnly = true, userAllowsCellular = true)
        )
    }
}
