package com.corelink.frame.ai.runtime.onnx

import com.corelink.frame.ai.runtime.InferenceRuntime
import com.corelink.frame.ai.runtime.InferenceSession
import com.corelink.frame.ai.runtime.RuntimeOptions
import com.corelink.frame.ai.runtime.TensorData
import com.corelink.frame.ai.runtime.TensorDataType
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import ai.onnxruntime.OrtSession.SessionOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.inject.Inject
import javax.inject.Singleton

/**
 * ONNX Runtime Mobile implementation.
 *
 * This class will load and run real ONNX models once model files are present
 * and validated. Until a model package is installed, load() will throw.
 *
 * Note: Full GPU / NNAPI execution providers require additional native
 * configuration on device. CPU EP is always available.
 */
@Singleton
class OnnxInferenceRuntime @Inject constructor() : InferenceRuntime {

    override val name: String = "ONNX Runtime Mobile"

    private val env: OrtEnvironment by lazy { OrtEnvironment.getEnvironment() }

    override fun isAvailable(): Boolean {
        return try {
            OrtEnvironment.getEnvironment()
            true
        } catch (e: Throwable) {
            false
        }
    }

    override suspend fun load(
        modelPath: String,
        options: RuntimeOptions
    ): InferenceSession = withContext(Dispatchers.IO) {
        val sessionOptions = SessionOptions().apply {
            setIntraOpNumThreads(options.numThreads)
            if (options.memoryPatternOptimization) {
                setOptimizationLevel(SessionOptions.OptLevel.ALL_OPT)
            }
            // GPU / NNAPI EPs can be added here after device validation
        }

        val session = env.createSession(modelPath, sessionOptions)
        OnnxInferenceSession(env, session)
    }
}

/**
 * Thin wrapper around OrtSession that satisfies InferenceSession.
 * Closes the underlying session on close().
 */
class OnnxInferenceSession(
    private val env: OrtEnvironment,
    private val session: OrtSession
) : InferenceSession {

    override val inputNames: List<String>
        get() = session.inputNames.toList()

    override val outputNames: List<String>
        get() = session.outputNames.toList()

    override suspend fun run(inputs: Map<String, TensorData>): Map<String, TensorData> =
        withContext(Dispatchers.Default) {
            val onnxInputs = HashMap<String, OnnxTensor>()
            try {
                for ((name, tensor) in inputs) {
                    onnxInputs[name] = toOnnxTensor(tensor)
                }
                val results = session.run(onnxInputs)
                results.use { result ->
                    val out = LinkedHashMap<String, TensorData>()
                    for (name in outputNames) {
                        val value = result.get(name).orElse(null) ?: continue
                        if (value is OnnxTensor) {
                            out[name] = fromOnnxTensor(value)
                        }
                    }
                    out
                }
            } finally {
                onnxInputs.values.forEach { it.close() }
            }
        }

    override fun close() {
        session.close()
    }

    private fun toOnnxTensor(data: TensorData): OnnxTensor {
        // Currently supports FLOAT32 buffers. Expand as models require.
        require(data.dataType == TensorDataType.FLOAT32) {
            "Only FLOAT32 tensors are supported in the initial runtime"
        }
        data.data.rewind()
        val floatBuffer = data.data.asFloatBuffer()
        return OnnxTensor.createTensor(env, floatBuffer, data.shape)
    }

    private fun fromOnnxTensor(tensor: OnnxTensor): TensorData {
        val info = tensor.info
        val shape = info.shape
        val floatBuffer = tensor.floatBuffer
        val byteBuffer = ByteBuffer.allocateDirect(floatBuffer.remaining() * 4)
            .order(ByteOrder.nativeOrder())
        val outFloat = byteBuffer.asFloatBuffer()
        outFloat.put(floatBuffer)
        byteBuffer.rewind()
        return TensorData(
            shape = shape,
            data = byteBuffer,
            dataType = TensorDataType.FLOAT32
        )
    }
}
