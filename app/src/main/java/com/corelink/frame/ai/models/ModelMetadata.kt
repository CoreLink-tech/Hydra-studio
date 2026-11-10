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
    val checksumSha256: String?,
    val status: ModelStatus,
    val compatibilityNotes: String
)

enum class GenerationMode {
    IMAGE_TO_VIDEO,
    TEXT_TO_VIDEO
}

enum class ModelRuntime {
    ONNX_MOBILE,
    LITERT,
    UNKNOWN
}

enum class ModelStatus {
    /** Catalogue entry only — weights not yet validated on Android */
    RESEARCH,
    /** Conversion path exists but not yet measured on target hardware */
    EXPERIMENTAL,
    /** Measured on device; profile restrictions apply */
    VALIDATED,
    /** Confirmed incompatible with current runtime / hardware class */
    INCOMPATIBLE
}
