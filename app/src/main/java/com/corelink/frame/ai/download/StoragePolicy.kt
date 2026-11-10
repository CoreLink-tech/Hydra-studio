package com.corelink.frame.ai.download

/** Storage checks before a download starts. */
object StoragePolicy {
    /** Keep this much free after the download so the phone never runs out mid-use. */
    const val SAFETY_MARGIN_BYTES: Long = 1L shl 30 // 1 GiB

    /** Warn when free space after the download would fall below this. */
    const val WARN_FREE_BYTES: Long = 3L shl 30 // 3 GiB

    enum class Verdict { OK, WARN_LOW_SPACE, BLOCKED_INSUFFICIENT }

    fun check(freeBytes: Long, modelBytes: Long, alreadyHaveBytes: Long = 0L): Verdict {
        val needed = (modelBytes - alreadyHaveBytes).coerceAtLeast(0L)
        val afterDownload = freeBytes - needed
        return when {
            afterDownload < SAFETY_MARGIN_BYTES -> Verdict.BLOCKED_INSUFFICIENT
            afterDownload < WARN_FREE_BYTES -> Verdict.WARN_LOW_SPACE
            else -> Verdict.OK
        }
    }
}

/**
 * Wi-Fi preference. Cellular is only used when the user explicitly allows it
 * (or wifiOnly is off). Default policy in the app is wifi-only ON.
 */
object NetworkPolicy {
    enum class Decision { PROCEED, WAIT_FOR_WIFI }

    fun decide(onWifi: Boolean, wifiOnly: Boolean, userAllowsCellular: Boolean): Decision =
        when {
            onWifi -> Decision.PROCEED
            wifiOnly && !userAllowsCellular -> Decision.WAIT_FOR_WIFI
            else -> Decision.PROCEED
        }
}
