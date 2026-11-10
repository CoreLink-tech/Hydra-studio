package com.corelink.frame.ai.models

/**
 * Runtime installation state of a catalogue model on this device.
 *
 * INSTALLED is only returned when every file in [ModelMetadata.files]
 * exists, matches size (when declared > 0), and passes SHA-256
 * (when provided). PARTIAL means some but not all files are present.
 */
data class ModelInstallState(
    val modelId: String,
    val state: InstallState,
    val localPath: String? = null,
    val bytesOnDisk: Long = 0L,
    val lastValidatedAt: Long? = null,
    val validationError: String? = null,
    /**
     * True when INSTALLED but one or more files had no trusted checksum
     * in metadata (legacy / size-only path). Prefer false.
     */
    val checksumUnverified: Boolean = false,
    /** Names of files still missing when state is PARTIAL or CORRUPT. */
    val missingFiles: List<String> = emptyList(),
    /** Names of files that failed size or checksum checks. */
    val corruptFiles: List<String> = emptyList()
)

enum class InstallState {
    NOT_PRESENT,
    /** Some expected files present, others missing. */
    PARTIAL,
    DOWNLOADING,
    VERIFYING,
    INSTALLED,
    CORRUPT,
    INCOMPATIBLE
}
