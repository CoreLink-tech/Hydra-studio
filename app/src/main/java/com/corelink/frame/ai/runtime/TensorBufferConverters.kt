package com.corelink.frame.ai.runtime

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Pure-JVM buffer helpers for tensor packing / unpacking.
 * No ONNX dependency so unit tests run on the desktop JVM.
 *
 * Layout: tightly packed little-endian (or [ByteOrder.nativeOrder]) elements
 * matching [TensorDataType.bytesPerElement].
 *
 * FLOAT16 note: values are IEEE-754 binary16 bit patterns stored as unsigned
 * shorts. Conversion from/to Float uses a standard soft-float path — not
 * hardware FP16. ONNX Runtime Mobile on CPU often executes FLOAT16 graphs via
 * FP32 kernels or limited FP16 support depending on build; do not assume
 * native FP16 ALU speed on Android CPU.
 */
object TensorBufferConverters {

    fun elementCount(shape: LongArray): Int {
        require(shape.isNotEmpty()) { "shape must not be empty" }
        var n = 1L
        for (d in shape) {
            require(d > 0L) { "shape dims must be > 0 for buffer allocation, got $d" }
            n = Math.multiplyExact(n, d)
        }
        require(n <= Int.MAX_VALUE) { "tensor too large: $n elements" }
        return n.toInt()
    }

    fun allocate(shape: LongArray, type: TensorDataType): ByteBuffer {
        val bytes = elementCount(shape) * type.bytesPerElement()
        return ByteBuffer.allocateDirect(bytes).order(ByteOrder.nativeOrder())
    }

    // ---- FLOAT32 ----

    fun putFloat32(buffer: ByteBuffer, values: FloatArray) {
        buffer.order(ByteOrder.nativeOrder())
        buffer.asFloatBuffer().put(values)
        buffer.rewind()
    }

    fun getFloat32(buffer: ByteBuffer, count: Int): FloatArray {
        buffer.order(ByteOrder.nativeOrder())
        buffer.rewind()
        val out = FloatArray(count)
        buffer.asFloatBuffer().get(out)
        return out
    }

    // ---- FLOAT16 (IEEE binary16 bit patterns as Short / UInt16) ----

    fun floatToHalfBits(value: Float): Short {
        val fbits = java.lang.Float.floatToIntBits(value)
        val sign = (fbits ushr 16) and 0x8000
        var valExp = (fbits ushr 23) and 0xFF
        var mantissa = fbits and 0x7FFFFF

        when {
            valExp == 255 -> {
                // Inf / NaN
                val hMan = if (mantissa != 0) 0x200 else 0
                return (sign or 0x7C00 or hMan).toShort()
            }
            valExp > 142 -> {
                // Overflow → Inf
                return (sign or 0x7C00).toShort()
            }
            valExp < 113 -> {
                // Subnormal or zero in FP16
                if (valExp < 103) return sign.toShort()
                mantissa = mantissa or 0x800000
                val shift = 113 - valExp
                val hMan = mantissa shr (shift + 13)
                return (sign or hMan).toShort()
            }
            else -> {
                val hExp = valExp - 112
                val hMan = mantissa shr 13
                return (sign or (hExp shl 10) or hMan).toShort()
            }
        }
    }

    fun halfBitsToFloat(bits: Short): Float {
        val h = bits.toInt() and 0xFFFF
        val sign = (h ushr 15) and 0x1
        val exp = (h ushr 10) and 0x1F
        val man = h and 0x3FF
        val fbits = when {
            exp == 0 -> {
                if (man == 0) {
                    sign shl 31
                } else {
                    // Subnormal → normalize
                    var m = man
                    var e = -14
                    while (m and 0x400 == 0) {
                        m = m shl 1
                        e--
                    }
                    m = m and 0x3FF
                    (sign shl 31) or ((e + 127) shl 23) or (m shl 13)
                }
            }
            exp == 0x1F -> {
                (sign shl 31) or (0xFF shl 23) or (man shl 13)
            }
            else -> {
                (sign shl 31) or ((exp - 15 + 127) shl 23) or (man shl 13)
            }
        }
        return java.lang.Float.intBitsToFloat(fbits)
    }

    fun putFloat16FromFloat32(buffer: ByteBuffer, values: FloatArray) {
        buffer.order(ByteOrder.nativeOrder())
        val shortBuf = buffer.asShortBuffer()
        for (v in values) {
            shortBuf.put(floatToHalfBits(v))
        }
        buffer.rewind()
    }

    fun getFloat16AsFloat32(buffer: ByteBuffer, count: Int): FloatArray {
        buffer.order(ByteOrder.nativeOrder())
        buffer.rewind()
        val shortBuf = buffer.asShortBuffer()
        val out = FloatArray(count)
        for (i in 0 until count) {
            out[i] = halfBitsToFloat(shortBuf.get())
        }
        return out
    }

    // ---- INT32 ----

    fun putInt32(buffer: ByteBuffer, values: IntArray) {
        buffer.order(ByteOrder.nativeOrder())
        buffer.asIntBuffer().put(values)
        buffer.rewind()
    }

    fun getInt32(buffer: ByteBuffer, count: Int): IntArray {
        buffer.order(ByteOrder.nativeOrder())
        buffer.rewind()
        val out = IntArray(count)
        buffer.asIntBuffer().get(out)
        return out
    }

    // ---- INT64 ----

    fun putInt64(buffer: ByteBuffer, values: LongArray) {
        buffer.order(ByteOrder.nativeOrder())
        buffer.asLongBuffer().put(values)
        buffer.rewind()
    }

    fun getInt64(buffer: ByteBuffer, count: Int): LongArray {
        buffer.order(ByteOrder.nativeOrder())
        buffer.rewind()
        val out = LongArray(count)
        buffer.asLongBuffer().get(out)
        return out
    }

    /** Build a zero-filled tensor for smoke tests. */
    fun zeros(shape: LongArray, type: TensorDataType): TensorData {
        val buf = allocate(shape, type)
        // allocateDirect is already zero-filled
        return TensorData(shape, buf, type)
    }
}
