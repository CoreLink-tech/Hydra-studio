package com.corelink.frame.ai.models

/**
 * Data-driven description of an AI model package.
 * Nothing here implies the model is installed or runnable.
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
    /** Optional SHA-256 of the primary package file (hex). */
    val checksumSha256: String?,
    /**
     * Relative file names that must exist under FRAME/Models/<id>/ for INSTALLED.
     * Empty list = legacy size-only heuristic (flagged as unverified in install state).
     */
    val expectedFiles: List<String> = emptyList(),
    val status: ModelStatus,
    val compatibilityNotes: String
)

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
