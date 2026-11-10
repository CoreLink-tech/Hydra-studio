package com.corelink.frame.ai.models

/**
 * Runtime installation state of a catalogue model on this device.
 * A model is only considered INSTALLED after files exist and integrity checks pass.
 */
data class ModelInstallState(
    val modelId: String,
    val state: InstallState,
    val localPath: String? = null,
    val bytesOnDisk: Long = 0L,
    val lastValidatedAt: Long? = null,
    val validationError: String? = null
)

enum class InstallState {
    NOT_PRESENT,
    DOWNLOADING,
    VERIFYING,
    INSTALLED,
    CORRUPT,
    INCOMPATIBLE
}
