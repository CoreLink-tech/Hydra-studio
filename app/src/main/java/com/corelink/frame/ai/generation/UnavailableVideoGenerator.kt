package com.corelink.frame.ai.generation

/**
 * Honest placeholder used until a real runtime + model combination is validated.
 * Always returns Failure. Never produces a fake video file.
 */
class UnavailableVideoGenerator(
    override val modelId: String,
    private val reason: String = "No validated on-device model is installed yet. Complete Milestone 3 device PoC first."
) : VideoGenerator {

    override val isReady: Boolean = false

    override suspend fun prepare() {
        // no-op
    }

    override suspend fun generate(
        request: ImageToVideoRequest,
        progress: (GenerationProgress) -> Unit
    ): GenerationResult {
        progress(GenerationProgress.Stage("unavailable", reason))
        return GenerationResult.Failure(
            reason = FailureReason.MODEL_NOT_INSTALLED,
            message = reason
        )
    }

    override suspend fun release() {
        // no-op
    }
}
