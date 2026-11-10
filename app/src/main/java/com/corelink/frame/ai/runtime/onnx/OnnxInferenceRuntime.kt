package com.corelink.frame.ai.runtime.onnx

import ai.onnxruntime.OnnxJavaType
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import ai.onnxruntime.OrtSession.SessionOptions
import com.corelink.frame.ai.runtime.InferenceRuntime
import com.corelink.frame.ai.runtime.InferenceSession
import com.corelink.frame.ai.runtime.RuntimeOptions
import com.corelink.frame.ai.runtime.TensorBufferConverters
import com.corelink.frame.ai.runtime.TensorData
import com.corelink.frame.ai.runtime.TensorDataType
import com.corelink.frame.ai.runtime.bytesPerElement
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.inject.Inject
import javax.inject.Singleton

/**
 * ONNX Runtime Mobile implementation.
 *
 * Supports FLOAT32, FLOAT16, INT32, and INT64 input/output tensors.
 *
 * FLOAT16 on CPU: ORT Mobile may run FLOAT16 models via FP32 kernels or limited
 * native FP16 support depending on the Android build. Do not assume hardware FP16
 * speed on CPU; measure on device. Prefer FLOAT32 smoke tests when validating
 * a new pack unless the graph requires FLOAT16.
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
        }
        val session = env.createSession(modelPath, sessionOptions)
        OnnxInferenceSession(env, session)
    }
}

class OnnxInferenceSession(
    private val env: OrtEnvironment,
    private val session: OrtSession
) : InferenceSession {

    override val inputNames: List<String>
        get() = session.inputNames.toList()

    override val outputNames: List<String>
        get() = session.outputNames.toList()

    /** Expose ORT type info for smoke tests (name → java type string). */
    fun inputTypeNames(): Map<String, String> =
        session.inputInfo.mapValues { (_, node) ->
            (node.info as? ai.onnxruntime.TensorInfo)?.type?.name ?: "unknown"
        }

    fun outputTypeNames(): Map<String, String> =
        session.outputInfo.mapValues { (_, node) ->
            (node.info as? ai.onnxruntime.TensorInfo)?.type?.name ?: "unknown"
        }

    fun inputShapes(): Map<String, LongArray> =
        session.inputInfo.mapValues { (_, node) ->
            (node.info as? ai.onnxruntime.TensorInfo)?.shape ?: longArrayOf()
        }

    fun outputShapes(): Map<String, LongArray> =
        session.outputInfo.mapValues { (_, node) ->
            (node.info as? ai.onnxruntime.TensorInfo)?.shape ?: longArrayOf()
        }

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
        data.data.order(ByteOrder.nativeOrder())
        data.data.rewind()
        return when (data.dataType) {
            TensorDataType.FLOAT32 -> {
                val fb = data.data.asFloatBuffer()
                OnnxTensor.createTensor(env, fb, data.shape)
            }
            TensorDataType.FLOAT16 -> {
                // Pass the raw half-precision bytes; ORT interprets via OnnxJavaType.FLOAT16.
                val dup = data.data.duplicate().order(ByteOrder.nativeOrder())
                OnnxTensor.createTensor(env, dup, data.shape, OnnxJavaType.FLOAT16)
            }
            TensorDataType.INT32 -> {
                val ib = data.data.asIntBuffer()
                OnnxTensor.createTensor(env, ib, data.shape)
            }
            TensorDataType.INT64 -> {
                val lb = data.data.asLongBuffer()
                OnnxTensor.createTensor(env, lb, data.shape)
            }
            TensorDataType.INT8, TensorDataType.UINT8 -> {
                throw IllegalArgumentException(
                    "${data.dataType} inputs are not wired yet; use FLOAT32/16 or INT32/64"
                )
            }
        }
    }

    private fun fromOnnxTensor(tensor: OnnxTensor): TensorData {
        val info = tensor.info
        val shape = info.shape
        val type = when (info.type) {
            OnnxJavaType.FLOAT -> TensorDataType.FLOAT32
            OnnxJavaType.FLOAT16 -> TensorDataType.FLOAT16
            OnnxJavaType.INT32 -> TensorDataType.INT32
            OnnxJavaType.INT64 -> TensorDataType.INT64
            OnnxJavaType.INT8 -> TensorDataType.INT8
            OnnxJavaType.UINT8 -> TensorDataType.UINT8
            else -> throw IllegalArgumentException("Unsupported ONNX output type: ${info.type}")
        }
        val count = TensorBufferConverters.elementCount(
            shape.map { if (it < 0) 1L else it }.toLongArray()
        )
        val byteSize = count * type.bytesPerElement()
        val byteBuffer = ByteBuffer.allocateDirect(byteSize).order(ByteOrder.nativeOrder())

        when (type) {
            TensorDataType.FLOAT32 -> {
                val src = tensor.floatBuffer
                byteBuffer.asFloatBuffer().put(src)
            }
            TensorDataType.FLOAT16 -> {
                // ORT may expose floatBuffer (dequantized) or raw buffer depending on version.
                // Prefer raw byte buffer when available; fall back to converting floatBuffer → FP16.
                try {
                    val raw = tensor.byteBuffer
                    if (raw != null && raw.remaining() >= byteSize) {
                        val slice = raw.duplicate()
                        val copy = ByteArray(byteSize)
                        slice.get(copy)
                        byteBuffer.put(copy)
                    } else {
                        val floats = FloatArray(count)
                        tensor.floatBuffer.get(floats)
                        TensorBufferConverters.putFloat16FromFloat32(byteBuffer, floats)
                    }
                } catch (_: Exception) {
                    val floats = FloatArray(count)
                    tensor.floatBuffer.get(floats)
                    TensorBufferConverters.putFloat16FromFloat32(byteBuffer, floats)
                }
            }
            TensorDataType.INT32 -> {
                byteBuffer.asIntBuffer().put(tensor.intBuffer)
            }
            TensorDataType.INT64 -> {
                byteBuffer.asLongBuffer().put(tensor.longBuffer)
            }
            TensorDataType.INT8, TensorDataType.UINT8 -> {
                val raw = tensor.byteBuffer
                val copy = ByteArray(byteSize)
                raw.get(copy)
                byteBuffer.put(copy)
            }
        }
        byteBuffer.rewind()
        return TensorData(shape = shape, data = byteBuffer, dataType = type)
    }
}
