package com.corelink.frame.ai.models

/**
 * Data-driven description of an AI model package.
 * Nothing here implies the model is installed or runnable.
 *
 * Multi-file packs use [files]. An empty list means the package is
 * not installable yet (research placeholder only).
 */
data class ModelMetadata(
    val id: String,
    val displayName: String,
    val version: String,
    val description: String,
    val architecture: String,
    val generationModes: Set<GenerationMode>,
    val parameterCountApprox: String,
    val storageBytesApprox: Long,
    val minRamGbRecommended: Float,
    val supportedResolutions: List<String>,
    val maxFrames: Int,
    val maxDurationSeconds: Int,
    val runtime: ModelRuntime,
    val licence: String,
    val licenceNotes: String,
    val sourceUrl: String?,
    /**
     * Files that make up this pack. Empty = not installable yet
     * (shown clearly in UI; evaluator returns NOT_PRESENT).
     * Prefer entries with sha256 so install can be verified.
     */
    val files: List<ModelFile> = emptyList(),
    val status: ModelStatus,
    val compatibilityNotes: String
) {
    /** True when the catalogue entry has at least one file definition. */
    val isInstallable: Boolean
        get() = files.isNotEmpty()

    /** True when every file has a URL and SHA-256 (app can download the pack). */
    val isDownloadable: Boolean
        get() = files.isNotEmpty() && files.all { it.isDownloadable }

    /** Sum of declared sizeBytes (0 if unknown). */
    val declaredTotalBytes: Long
        get() = files.sumOf { it.sizeBytes.coerceAtLeast(0L) }
}

enum class GenerationMode {
    IMAGE_TO_VIDEO,
    TEXT_TO_VIDEO,
    TEXT_TO_IMAGE
}

enum class ModelRuntime {
    ONNX_MOBILE,
    LITERT,
    UNKNOWN
}

enum class ModelStatus {
    RESEARCH,
    EXPERIMENTAL,
    VALIDATED,
    INCOMPATIBLE
}
