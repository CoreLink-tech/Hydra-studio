package com.corelink.frame.ai.models

/**
 * Runtime installation state of a catalogue model on this device.
 * A model is only considered INSTALLED after expected files exist
 * (and checksum passes when provided).
 */
data class ModelInstallState(
    val modelId: String,
    val state: InstallState,
    val localPath: String? = null,
    val bytesOnDisk: Long = 0L,
    val lastValidatedAt: Long? = null,
    val validationError: String? = null,
    /** True when INSTALLED without a trusted checksum on metadata. */
    val checksumUnverified: Boolean = false
)

enum class InstallState {
    NOT_PRESENT,
    DOWNLOADING,
    VERIFYING,
    INSTALLED,
    CORRUPT,
    INCOMPATIBLE
}
