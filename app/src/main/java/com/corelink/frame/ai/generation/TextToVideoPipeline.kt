package com.corelink.frame.ai.generation

import android.graphics.Bitmap
import com.corelink.frame.ai.download.ModelDownloadManager
import com.corelink.frame.ai.models.InstallState
import com.corelink.frame.ai.models.ModelCatalogue
import com.corelink.frame.ai.models.ModelStatus
import com.corelink.frame.core.files.FrameFileManager
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Staged text-to-video orchestration:
 *
 *   Stage A — Text → Image (small local T2I model, when validated)
 *   Stage B — Image → Video (existing I2V path)
 *
 * Until both stages have validated on-device models, this pipeline
 * returns explicit Failure results. It never invents frames or MP4s.
 */
@Singleton
class TextToVideoPipeline @Inject constructor(
    private val downloadManager: ModelDownloadManager,
    private val fileManager: FrameFileManager
) {
    data class Request(
        val prompt: String,
        val negativePrompt: String = "",
        val motionPrompt: String = "",
        val targetFrames: Int = 16,
        val targetWidth: Int = 512,
        val targetHeight: Int = 512,
        val seed: Long? = null
    )

    /**
     * Returns true only when a validated T2I model AND a validated I2V model
     * are installed. Research-only catalogue entries do not count.
     */
    fun isAvailable(): Boolean {
        val t2i = ModelCatalogue.all.filter {
            it.generationModes.any { m -> m.name == "TEXT_TO_IMAGE" } ||
                it.id.contains("t2i") || it.id.contains("turbo")
        }
        // Current catalogue has no dedicated validated T2I yet
        val hasValidatedT2i = t2i.any { meta ->
            meta.status == ModelStatus.VALIDATED &&
                downloadManager.getState(meta.id).state == InstallState.INSTALLED
        }
        val hasValidatedI2v = ModelCatalogue.all.any { meta ->
            meta.generationModes.any { it.name.contains("IMAGE_TO_VIDEO") } &&
                meta.status == ModelStatus.VALIDATED &&
                downloadManager.getState(meta.id).state == InstallState.INSTALLED
        }
        return hasValidatedT2i && hasValidatedI2v
    }

    suspend fun generate(
        request: Request,
        progress: (GenerationProgress) -> Unit
    ): GenerationResult {
        if (request.prompt.isBlank()) {
            return GenerationResult.Failure(
                FailureReason.INVALID_INPUT,
                "Prompt is empty"
            )
        }

        progress(GenerationProgress.Stage("t2v_check", "Checking text-to-video availability"))

        if (!isAvailable()) {
            return GenerationResult.Failure(
                FailureReason.MODEL_NOT_INSTALLED,
                "Text-to-video is not available yet. " +
                    "It requires a validated on-device text-to-image model plus a validated image-to-video model. " +
                    "Image-to-video is the primary path; complete its device PoC first (see docs/T2V_PIPELINE.md)."
            )
        }

        // --- Stage A: Text → Image (placeholder for real T2I session) ---
        progress(GenerationProgress.Stage("t2i", "Text-to-image stage"))
        val still: Bitmap = runTextToImage(request)
            ?: return GenerationResult.Failure(
                FailureReason.INFERENCE_FAILED,
                "Text-to-image stage failed to produce a still frame"
            )

        // Persist intermediate still for debugging / reuse
        val stillFile = File(fileManager.cacheDir(), "t2i_${System.currentTimeMillis()}.png")
        FileOutputStream(stillFile).use { out ->
            still.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        progress(GenerationProgress.Stage("t2i_done", stillFile.absolutePath))

        // --- Stage B: Image → Video ---
        progress(GenerationProgress.Stage("i2v", "Image-to-video stage"))
        // Real I2V would call OnDeviceVideoGenerator here with [still].
        return GenerationResult.Failure(
            FailureReason.INFERENCE_FAILED,
            "T2I and I2V models are marked available but the bound inference graphs are not wired for this build. " +
                "Update OnDeviceVideoGenerator after device PoC."
        )
    }

    /**
     * Future: load T2I ONNX session, run diffusion, return bitmap.
     * Currently returns null to force explicit failure path.
     */
    private suspend fun runTextToImage(request: Request): Bitmap? {
        // No validated T2I weights in this repository.
        return null
    }
}
