package com.corelink.frame.ai.models

/**
 * Static catalogue of known candidate models.
 * New models are added here without changing generation code.
 * Status is RESEARCH / EXPERIMENTAL until real-device validation occurs.
 */
object ModelCatalogue {

    val all: List<ModelMetadata> = listOf(
        mobileI2V,
        cineMobile,
        wan21Light,
        sdTurboT2i
    )

    fun byId(id: String): ModelMetadata? = all.find { it.id == id }

    fun compatibleWith(minRamGb: Float): List<ModelMetadata> =
        all.filter { it.minRamGbRecommended <= minRamGb + 0.3f }

    // -------------------------------------------------------------------------

    private val mobileI2V = ModelMetadata(
        id = "mobilei2v_270m",
        displayName = "MobileI2V",
        version = "research-2025",
        description = "Lightweight image-to-video diffusion model (~270M) designed for mobile. Primary PoC candidate.",
        architecture = "Linear hybrid attention denoiser + distilled sampling",
        generationModes = setOf(GenerationMode.IMAGE_TO_VIDEO),
        parameterCountApprox = "270M",
        storageBytesApprox = 500L * 1024 * 1024, // estimate; measure after quantisation
        minRamGbRecommended = 4.0f,
        supportedResolutions = listOf("512x512", "720p (high-end only)"),
        maxFrames = 17,
        maxDurationSeconds = 3,
        runtime = ModelRuntime.ONNX_MOBILE,
        licence = "Research — verify redistribution terms in upstream repo",
        licenceNotes = "Source: https://github.com/hustvl/MobileI2V. Confirm licence before shipping.",
        sourceUrl = "https://github.com/hustvl/MobileI2V",
        checksumSha256 = null,
        status = ModelStatus.RESEARCH,
        compatibilityNotes = "Community ONNX ports exist. Peak memory on 4 GB devices is unmeasured. Start with low resolution."
    )

    private val cineMobile = ModelMetadata(
        id = "cinemobile",
        displayName = "CineMobile",
        version = "research-2026",
        description = "Mobile-oriented I2V focused on cinematic camera motion. Strong quantisation claims.",
        architecture = "Distilled + pruned DiT-style, hybrid PTQ",
        generationModes = setOf(GenerationMode.IMAGE_TO_VIDEO),
        parameterCountApprox = "compressed <1 GB footprint claimed",
        storageBytesApprox = 900L * 1024 * 1024,
        minRamGbRecommended = 3.5f,
        supportedResolutions = listOf("480p"),
        maxFrames = 49,
        maxDurationSeconds = 5,
        runtime = ModelRuntime.ONNX_MOBILE,
        licence = "Research — verify before redistribution",
        licenceNotes = "Check paper and any released weights for exact terms.",
        sourceUrl = "https://arxiv.org/abs/2607.03803",
        checksumSha256 = null,
        status = ModelStatus.RESEARCH,
        compatibilityNotes = "Reported ~1.8 GB peak on Dimensity 8400. Promising secondary candidate."
    )

    private val wan21Light = ModelMetadata(
        id = "wan21_1p3b_light",
        displayName = "Wan 2.1 1.3B Light",
        version = "2.1",
        description = "Quantised Wan 2.1 text/image-to-video. Requires high-end memory.",
        architecture = "DiT / diffusion transformer",
        generationModes = setOf(GenerationMode.TEXT_TO_VIDEO, GenerationMode.IMAGE_TO_VIDEO),
        parameterCountApprox = "1.3B",
        storageBytesApprox = 3L * 1024 * 1024 * 1024,
        minRamGbRecommended = 8.0f,
        supportedResolutions = listOf("480p", "832x480"),
        maxFrames = 81,
        maxDurationSeconds = 5,
        runtime = ModelRuntime.ONNX_MOBILE,
        licence = "Apache 2.0",
        licenceNotes = "Permissive. Still too heavy for 4 GB baseline.",
        sourceUrl = null,
        checksumSha256 = null,
        status = ModelStatus.RESEARCH,
        compatibilityNotes = "Only consider for High-performance profile after validation. Not a PoC target for 4 GB devices."
    )

    private val sdTurboT2i = ModelMetadata(
        id = "sd_turbo_t2i",
        displayName = "SD-Turbo class T2I (research)",
        version = "research",
        description = "Candidate lightweight text-to-image stage for staged text-to-video. Not validated on Android yet.",
        architecture = "Distilled latent diffusion / turbo-style",
        generationModes = setOf(GenerationMode.TEXT_TO_IMAGE),
        parameterCountApprox = "varies (turbo class)",
        storageBytesApprox = 2L * 1024 * 1024 * 1024,
        minRamGbRecommended = 6.0f,
        supportedResolutions = listOf("512x512"),
        maxFrames = 1,
        maxDurationSeconds = 0,
        runtime = ModelRuntime.ONNX_MOBILE,
        licence = "Depends on specific weights — verify before shipping",
        licenceNotes = "Placeholder catalogue entry for staged T2V. Replace with a concrete, licence-cleared package after evaluation.",
        sourceUrl = null,
        checksumSha256 = null,
        status = ModelStatus.RESEARCH,
        compatibilityNotes = "Intended only as Stage A of T2V after MobileI2V PoC succeeds. High RAM pressure on 4 GB devices."
    )
}
