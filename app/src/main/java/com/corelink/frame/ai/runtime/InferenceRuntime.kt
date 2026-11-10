package com.corelink.frame.ai.runtime

import java.nio.ByteBuffer

/**
 * Abstraction over a mobile inference engine (ONNX Runtime Mobile, LiteRT, …).
 * Implementations must never pretend a session is ready if the model failed to load.
 */
interface InferenceRuntime {
    val name: String

    /**
     * Load model files into a session. Returns a session handle or throws.
     * Caller is responsible for closing the session.
     */
    suspend fun load(modelPath: String, options: RuntimeOptions = RuntimeOptions()): InferenceSession

    fun isAvailable(): Boolean
}

data class RuntimeOptions(
    val numThreads: Int = 2,
    val preferGpu: Boolean = true,
    val memoryPatternOptimization: Boolean = true
)

interface InferenceSession : AutoCloseable {
    val inputNames: List<String>
    val outputNames: List<String>

    /**
     * Run inference. Inputs are named tensors.
     * Implementations must release intermediate buffers promptly.
     */
    suspend fun run(inputs: Map<String, TensorData>): Map<String, TensorData>
}

/**
 * Minimal tensor carrier. Keeps the AI layer free of specific runtime types.
 */
data class TensorData(
    val shape: LongArray,
    val data: ByteBuffer,
    val dataType: TensorDataType
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is TensorData) return false
        return shape.contentEquals(other.shape) && dataType == other.dataType
    }

    override fun hashCode(): Int {
        var result = shape.contentHashCode()
        result = 31 * result + dataType.hashCode()
        return result
    }
}

enum class TensorDataType {
    FLOAT32,
    FLOAT16,
    INT8,
    INT32,
    UINT8
}
