package com.corelink.frame.hardware

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.opengl.GLES20
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.PowerManager
import android.os.StatFs
import android.util.Log
import java.io.File
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Collects real hardware information using Android system APIs only.
 * Never invents values. When a signal is unavailable it is reported as such.
 */
@Singleton
class DeviceProfiler @Inject constructor() {

    fun assess(context: Context): HardwareInfo {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        am.getMemoryInfo(memInfo)

        val totalRam = memInfo.totalMem
        val availRam = memInfo.availMem
        val threshold = memInfo.threshold
        val lowMem = memInfo.lowMemory

        val totalGb = totalRam / BYTES_PER_GB
        val profile = HardwareProfile.fromTotalRamGb(totalGb)
        val rationale = buildRationale(totalGb, availRam / BYTES_PER_GB, lowMem, profile)

        val (internalTotal, internalFree) = storageStats(Environment.getDataDirectory())
        val externalStats = externalStorageStats()

        val battery = readBattery(context)
        val thermal = readThermal(context)
        val gpu = readGpuRendererSafe()
        val cores = readCpuCoreCount()

        return HardwareInfo(
            manufacturer = Build.MANUFACTURER.replaceFirstChar {
                if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString()
            },
            model = Build.MODEL,
            device = Build.DEVICE,
            product = Build.PRODUCT,
            brand = Build.BRAND.replaceFirstChar {
                if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString()
            },
            androidVersion = Build.VERSION.RELEASE,
            sdkInt = Build.VERSION.SDK_INT,
            securityPatch = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                Build.VERSION.SECURITY_PATCH
            } else null,
            totalRamBytes = totalRam,
            availableRamBytes = availRam,
            thresholdRamBytes = threshold,
            isLowMemory = lowMem,
            primaryAbi = Build.SUPPORTED_ABIS.firstOrNull() ?: "unknown",
            supportedAbis = Build.SUPPORTED_ABIS.toList(),
            cpuCoreCount = cores,
            internalTotalBytes = internalTotal,
            internalFreeBytes = internalFree,
            externalTotalBytes = externalStats?.first,
            externalFreeBytes = externalStats?.second,
            batteryPercent = battery.first,
            isCharging = battery.second,
            batteryStatusLabel = battery.third,
            thermalStatus = thermal.first,
            thermalStatusLabel = thermal.second,
            gpuRenderer = gpu,
            profile = profile,
            profileRationale = rationale,
            assessedAtMillis = System.currentTimeMillis()
        )
    }

    private fun buildRationale(
        totalGb: Float,
        availGb: Float,
        lowMem: Boolean,
        profile: HardwareProfile
    ): String {
        val parts = mutableListOf<String>()
        parts.add(String.format(Locale.US, "Total RAM ≈ %.1f GB → %s profile", totalGb, profile.displayName))
        parts.add(String.format(Locale.US, "Currently available ≈ %.1f GB", availGb))
        if (lowMem) parts.add("System reports low-memory condition")
        if (totalGb < 4f) {
            parts.add("Generation will be restricted to the smallest compatible models and short clips")
        }
        return parts.joinToString(". ") + "."
    }

    private fun storageStats(path: File): Pair<Long, Long> {
        return try {
            val stat = StatFs(path.path)
            val total = stat.blockCountLong * stat.blockSizeLong
            val free = stat.availableBlocksLong * stat.blockSizeLong
            total to free
        } catch (e: Exception) {
            Log.w(TAG, "Storage stats failed for ${path.path}", e)
            0L to 0L
        }
    }

    private fun externalStorageStats(): Pair<Long, Long>? {
        return try {
            val state = Environment.getExternalStorageState()
            if (state != Environment.MEDIA_MOUNTED && state != Environment.MEDIA_MOUNTED_READ_ONLY) {
                return null
            }
            val path = Environment.getExternalStorageDirectory() ?: return null
            storageStats(path)
        } catch (e: Exception) {
            Log.w(TAG, "External storage stats unavailable", e)
            null
        }
    }

    private fun readBattery(context: Context): Triple<Int?, Boolean?, String> {
        return try {
            val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            val batteryStatus: Intent? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(null, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                @Suppress("UnspecifiedRegisterReceiverFlag")
                context.registerReceiver(null, filter)
            }

            if (batteryStatus == null) {
                return Triple(null, null, "Unavailable")
            }

            val level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            val percent = if (level >= 0 && scale > 0) {
                (level * 100) / scale
            } else null

            val status = batteryStatus.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
            val charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                    status == BatteryManager.BATTERY_STATUS_FULL

            val label = when (status) {
                BatteryManager.BATTERY_STATUS_CHARGING -> "Charging"
                BatteryManager.BATTERY_STATUS_DISCHARGING -> "Discharging"
                BatteryManager.BATTERY_STATUS_FULL -> "Full"
                BatteryManager.BATTERY_STATUS_NOT_CHARGING -> "Not charging"
                else -> "Unknown"
            }

            Triple(percent, charging, label)
        } catch (e: Exception) {
            Log.w(TAG, "Battery read failed", e)
            Triple(null, null, "Unavailable")
        }
    }

    private fun readThermal(context: Context): Pair<ThermalStatus, String> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return ThermalStatus.UNKNOWN to "Requires Android 10+"
        }
        return try {
            val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            val status = pm.currentThermalStatus
            val mapped = when (status) {
                PowerManager.THERMAL_STATUS_NONE -> ThermalStatus.NONE
                PowerManager.THERMAL_STATUS_LIGHT -> ThermalStatus.LIGHT
                PowerManager.THERMAL_STATUS_MODERATE -> ThermalStatus.MODERATE
                PowerManager.THERMAL_STATUS_SEVERE -> ThermalStatus.SEVERE
                PowerManager.THERMAL_STATUS_CRITICAL -> ThermalStatus.CRITICAL
                PowerManager.THERMAL_STATUS_EMERGENCY -> ThermalStatus.EMERGENCY
                PowerManager.THERMAL_STATUS_SHUTDOWN -> ThermalStatus.SHUTDOWN
                else -> ThermalStatus.UNKNOWN
            }
            val label = when (mapped) {
                ThermalStatus.NONE -> "None (cool)"
                ThermalStatus.LIGHT -> "Light"
                ThermalStatus.MODERATE -> "Moderate"
                ThermalStatus.SEVERE -> "Severe"
                ThermalStatus.CRITICAL -> "Critical"
                ThermalStatus.EMERGENCY -> "Emergency"
                ThermalStatus.SHUTDOWN -> "Shutdown"
                ThermalStatus.UNKNOWN -> "Unknown"
            }
            mapped to label
        } catch (e: Exception) {
            Log.w(TAG, "Thermal status unavailable", e)
            ThermalStatus.UNKNOWN to "Unavailable"
        }
    }

    /**
     * Best-effort GPU renderer string.
     * Requires a GL context; may return null on some devices/background threads.
     */
    private fun readGpuRendererSafe(): String? {
        return try {
            // This only works reliably when a GL context exists.
            // We attempt a simple query; many devices will return null here
            // when called from a background thread without an EGL context.
            val renderer = GLES20.glGetString(GLES20.GL_RENDERER)
            val vendor = GLES20.glGetString(GLES20.GL_VENDOR)
            when {
                !renderer.isNullOrBlank() && !vendor.isNullOrBlank() -> "$vendor – $renderer"
                !renderer.isNullOrBlank() -> renderer
                else -> null
            }
        } catch (e: Exception) {
            Log.d(TAG, "GPU renderer query skipped: ${e.message}")
            null
        }
    }

    private fun readCpuCoreCount(): Int? {
        return try {
            Runtime.getRuntime().availableProcessors().takeIf { it > 0 }
        } catch (e: Exception) {
            null
        }
    }

    companion object {
        private const val TAG = "DeviceProfiler"
        private const val BYTES_PER_GB = 1024f * 1024f * 1024f
    }
}
