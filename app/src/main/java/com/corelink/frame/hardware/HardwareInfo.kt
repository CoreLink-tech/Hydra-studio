package com.corelink.frame.hardware

/**
 * Snapshot of real device capabilities collected at runtime.
 * All fields come from Android system APIs. Missing values are explicit.
 */
data class HardwareInfo(
    // Device identity
    val manufacturer: String,
    val model: String,
    val device: String,
    val product: String,
    val brand: String,

    // Android
    val androidVersion: String,
    val sdkInt: Int,
    val securityPatch: String?,

    // Memory (bytes)
    val totalRamBytes: Long,
    val availableRamBytes: Long,
    val thresholdRamBytes: Long,
    val isLowMemory: Boolean,

    // CPU
    val primaryAbi: String,
    val supportedAbis: List<String>,
    val cpuCoreCount: Int?,

    // Storage (bytes)
    val internalTotalBytes: Long,
    val internalFreeBytes: Long,
    val externalTotalBytes: Long?,
    val externalFreeBytes: Long?,

    // Battery
    val batteryPercent: Int?,
    val isCharging: Boolean?,
    val batteryStatusLabel: String,

    // Thermal (API 29+)
    val thermalStatus: ThermalStatus,
    val thermalStatusLabel: String,

    // GPU (best-effort)
    val gpuRenderer: String?,

    // Derived
    val profile: HardwareProfile,
    val profileRationale: String,
    val assessedAtMillis: Long
) {
    val totalRamGb: Float get() = totalRamBytes / BYTES_PER_GB
    val availableRamGb: Float get() = availableRamBytes / BYTES_PER_GB
    val internalFreeGb: Float get() = internalFreeBytes / BYTES_PER_GB
    val internalTotalGb: Float get() = internalTotalBytes / BYTES_PER_GB

    val memoryPressurePercent: Float
        get() = if (totalRamBytes > 0) {
            ((totalRamBytes - availableRamBytes).toFloat() / totalRamBytes) * 100f
        } else 0f

    companion object {
        private const val BYTES_PER_GB = 1024f * 1024f * 1024f
    }
}

enum class ThermalStatus {
    UNKNOWN,
    NONE,
    LIGHT,
    MODERATE,
    SEVERE,
    CRITICAL,
    EMERGENCY,
    SHUTDOWN
}
