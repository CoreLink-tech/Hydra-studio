package com.corelink.frame.ai.generation

import android.graphics.Bitmap
import com.corelink.frame.ai.download.ModelDownloadManager
import com.corelink.frame.ai.models.InstallState
import com.corelink.frame.ai.models.ModelCatalogue
import com.corelink.frame.ai.preprocessing.ImagePreprocessor
import com.corelink.frame.ai.runtime.InferenceRuntime
import com.corelink.frame.core.files.FrameFileManager
import com.corelink.frame.media.FrameEncoder
import java.io.File
import javax.inject.Inject

/**
 * Production-path generator that only succeeds when a real model is installed
 * and inference produces frames. Currently fails fast with clear reasons
 * because no validated model package is present on device.
 *
 * Flow when a model is ready:
 * 1. Check install state
 * 2. Load ONNX session
 * 3. Preprocess image
 * 4. Run inference → frames
 * 5. Encode frames to MP4
 * 6. Validate file exists and is non-empty
 */
class OnDeviceVideoGenerator @Inject constructor(
    private val runtime: InferenceRuntime,
    private val preprocessor: ImagePreprocessor,
    private val encoder: FrameEncoder,
    private val downloadManager: ModelDownloadManager,
    private val fileManager: FrameFileManager,
    override val modelId: String
) : VideoGenerator {

    private var prepared = false

    override val isReady: Boolean
        get() {
            val state = downloadManager.getState(modelId)
            return state.state == InstallState.INSTALLED && runtime.isAvailable()
        }

    override suspend fun prepare() {
        val meta = ModelCatalogue.byId(modelId)
            ?: throw IllegalStateException("Unknown model: $modelId")
        downloadManager.refreshLocalState(meta)
        if (!isReady) {
            prepared = false
            return
        }
        // Future: load session here and keep a warm reference under memory limits
        prepared = true
    }

    override suspend fun generate(
        request: ImageToVideoRequest,
        progress: (GenerationProgress) -> Unit
    ): GenerationResult {
        progress(GenerationProgress.Stage("validate", "Checking model installation"))

        val meta = ModelCatalogue.byId(modelId)
            ?: return GenerationResult.Failure(
                FailureReason.MODEL_NOT_INSTALLED,
                "Model $modelId is not in the catalogue"
            )

        downloadManager.refreshLocalState(meta)
        val install = downloadManager.getState(modelId)
        if (install.state != InstallState.INSTALLED) {
            return GenerationResult.Failure(
                FailureReason.MODEL_NOT_INSTALLED,
                "Model files are not installed. Complete the device PoC and place validated weights under FRAME/Models/$modelId/"
            )
        }

        if (!runtime.isAvailable()) {
            return GenerationResult.Failure(
                FailureReason.MODEL_LOAD_FAILED,
                "Inference runtime (${runtime.name}) is not available on this device"
            )
        }

        progress(GenerationProgress.Stage("preprocess", "Preparing input image"))
        val targetW = request.targetWidth
        val targetH = request.targetHeight
        val resized: Bitmap = preprocessor.resizeMaintainAspect(
            request.inputImage, targetW, targetH
        )

        // At this point a real implementation would:
        // 1. Convert bitmap → tensor
        // 2. Load session from install.localPath
        // 3. Run denoising loop, collecting frames
        // 4. Pass frames to FrameEncoder
        //
        // Until measured model packages exist, we stop here with an explicit failure.

        progress(GenerationProgress.Stage("inference", "Model weights present but inference graph not yet bound for this package"))

        return GenerationResult.Failure(
            FailureReason.INFERENCE_FAILED,
            "Model directory exists but the concrete inference graph for '$modelId' has not been validated on device yet. " +
                "Follow docs/POC_CHECKLIST.md: convert weights, bind input/output names, run one successful encode, then update this generator."
        )
    }

    override suspend fun release() {
        prepared = false
        // Future: close warm session, clear temp frames
        fileManager.clearCache()
    }

    /**
     * Helper used once real frames are produced by inference.
     */
    suspend fun encodeFramesToMp4(
        frames: List<Bitmap>,
        output: File,
        frameRate: Int = 8
    ): FrameEncoder.EncodeResult {
        return encoder.encode(
            FrameEncoder.EncodeRequest(
                frames = frames,
                outputFile = output,
                frameRate = frameRate
            )
        )
    }
}
