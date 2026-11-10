package com.corelink.frame.ai.generation

import android.graphics.Bitmap
import java.io.File

/**
 * High-level contract for producing a video clip from an image (and optional motion prompt).
 *
 * Implementations must:
 * - Perform real inference or fail explicitly.
 * - Never return a placeholder / canned MP4 as success.
 * - Report progress stages honestly (no invented percentages when the runtime cannot supply them).
 * - Support cancellation.
 * - Clean temporary resources.
 */
interface VideoGenerator {
    val modelId: String
    val isReady: Boolean

    /**
     * Prepare the generator (load model components as needed).
     * Must be called before generate().
     */
    suspend fun prepare()

    /**
     * Run image-to-video generation.
     * Returns a GenerationResult that is either Success (with a real file) or Failure.
     */
    suspend fun generate(
        request: ImageToVideoRequest,
        progress: (GenerationProgress) -> Unit = {}
    ): GenerationResult

    /**
     * Release model sessions and temporary files.
     */
    suspend fun release()
}

data class ImageToVideoRequest(
    val inputImage: Bitmap,
    val motionPrompt: String = "",
    val negativePrompt: String = "",
    val targetFrames: Int = 16,
    val targetWidth: Int = 512,
    val targetHeight: Int = 512,
    val seed: Long? = null
)

sealed class GenerationProgress {
    data class Stage(val name: String, val detail: String? = null) : GenerationProgress()
    data class FramesProduced(val count: Int, val totalHint: Int?) : GenerationProgress()
    data class MemoryHint(val availableMb: Long?) : GenerationProgress()
}

sealed class GenerationResult {
    data class Success(
        val outputFile: File,
        val width: Int,
        val height: Int,
        val frameCount: Int,
        val durationMs: Long,
        val generationTimeMs: Long,
        val modelId: String
    ) : GenerationResult()

    data class Failure(
        val reason: FailureReason,
        val message: String,
        val cause: Throwable? = null
    ) : GenerationResult()
}

enum class FailureReason {
    MODEL_NOT_INSTALLED,
    MODEL_LOAD_FAILED,
    INCOMPATIBLE_HARDWARE,
    INSUFFICIENT_MEMORY,
    INVALID_INPUT,
    INFERENCE_FAILED,
    ENCODING_FAILED,
    CANCELLED,
    UNKNOWN
}
