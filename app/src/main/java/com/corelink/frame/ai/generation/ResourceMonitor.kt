package com.corelink.frame.ai.generation

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.PowerManager
import com.corelink.frame.hardware.ThermalStatus
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

data class ResourceSnapshot(
    val availableRamMb: Long,
    val totalRamMb: Long,
    val isLowMemory: Boolean,
    val thermalStatus: ThermalStatus,
    val shouldThrottle: Boolean,
    val shouldPause: Boolean
)

/**
 * Lightweight resource checks used by the generation queue.
 * Does not fabricate values; unavailable signals are reported as UNKNOWN.
 *
 * Thermal policy (matches docs/QA_AND_RELEASE.md):
 * - SEVERE and above → shouldPause (block new generation)
 * - MODERATE → throttle only
 */
@Singleton
class ResourceMonitor @Inject constructor(
    @ApplicationContext private val context: Context
) {
    fun snapshot(): ResourceSnapshot {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val mem = ActivityManager.MemoryInfo()
        am.getMemoryInfo(mem)

        val thermal = readThermal()
        val flags = ThermalPolicy.flagsFor(thermal, mem.lowMemory)

        return ResourceSnapshot(
            availableRamMb = mem.availMem / (1024 * 1024),
            totalRamMb = mem.totalMem / (1024 * 1024),
            isLowMemory = mem.lowMemory,
            thermalStatus = thermal,
            shouldThrottle = flags.shouldThrottle,
            shouldPause = flags.shouldPause
        )
    }

    private fun readThermal(): ThermalStatus {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return ThermalStatus.UNKNOWN
        return try {
            val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            when (pm.currentThermalStatus) {
                PowerManager.THERMAL_STATUS_NONE -> ThermalStatus.NONE
                PowerManager.THERMAL_STATUS_LIGHT -> ThermalStatus.LIGHT
                PowerManager.THERMAL_STATUS_MODERATE -> ThermalStatus.MODERATE
                PowerManager.THERMAL_STATUS_SEVERE -> ThermalStatus.SEVERE
                PowerManager.THERMAL_STATUS_CRITICAL -> ThermalStatus.CRITICAL
                PowerManager.THERMAL_STATUS_EMERGENCY -> ThermalStatus.EMERGENCY
                PowerManager.THERMAL_STATUS_SHUTDOWN -> ThermalStatus.SHUTDOWN
                else -> ThermalStatus.UNKNOWN
            }
        } catch (_: Exception) {
            ThermalStatus.UNKNOWN
        }
    }
}

/** Pure thermal/memory policy — unit-testable without Android services. */
object ThermalPolicy {
    data class Flags(val shouldThrottle: Boolean, val shouldPause: Boolean)

    fun flagsFor(thermal: ThermalStatus, isLowMemory: Boolean): Flags {
        val shouldPause = when (thermal) {
            ThermalStatus.SEVERE,
            ThermalStatus.CRITICAL,
            ThermalStatus.EMERGENCY,
            ThermalStatus.SHUTDOWN -> true
            else -> false
        }
        val shouldThrottle = shouldPause ||
            thermal == ThermalStatus.MODERATE ||
            isLowMemory
        return Flags(shouldThrottle = shouldThrottle, shouldPause = shouldPause)
    }
}
