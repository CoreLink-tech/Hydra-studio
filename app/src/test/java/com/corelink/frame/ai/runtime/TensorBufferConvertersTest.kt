package com.corelink.frame.ai.runtime

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class TensorBufferConvertersTest {

    @Test
    fun float32_roundTrip() {
        val shape = longArrayOf(1, 4)
        val values = floatArrayOf(0f, 1f, -2.5f, 3.25f)
        val buf = TensorBufferConverters.allocate(shape, TensorDataType.FLOAT32)
        TensorBufferConverters.putFloat32(buf, values)
        val back = TensorBufferConverters.getFloat32(buf, 4)
        assertArrayEquals(values, back, 0f)
    }

    @Test
    fun float16_roundTrip_commonValues() {
        val samples = floatArrayOf(0f, 1f, -1f, 0.5f, 2f, -0.25f, 65504f)
        for (v in samples) {
            val bits = TensorBufferConverters.floatToHalfBits(v)
            val back = TensorBufferConverters.halfBitsToFloat(bits)
            if (v == 0f) {
                assertEquals(0f, back, 0f)
            } else {
                val rel = abs(back - v) / abs(v)
                assertTrue("v=$v back=$back rel=$rel", rel < 0.001f || abs(back - v) < 0.01f)
            }
        }
    }

    @Test
    fun float16_buffer_roundTrip() {
        val shape = longArrayOf(1, 3)
        val values = floatArrayOf(0.5f, -1f, 2f)
        val buf = TensorBufferConverters.allocate(shape, TensorDataType.FLOAT16)
        TensorBufferConverters.putFloat16FromFloat32(buf, values)
        assertEquals(6, buf.capacity()) // 3 * 2 bytes
        val back = TensorBufferConverters.getFloat16AsFloat32(buf, 3)
        for (i in values.indices) {
            assertTrue(abs(back[i] - values[i]) < 0.01f)
        }
    }

    @Test
    fun int32_roundTrip() {
        val shape = longArrayOf(2, 2)
        val values = intArrayOf(1, -2, 3, 0)
        val buf = TensorBufferConverters.allocate(shape, TensorDataType.INT32)
        TensorBufferConverters.putInt32(buf, values)
        assertArrayEquals(values, TensorBufferConverters.getInt32(buf, 4))
    }

    @Test
    fun int64_roundTrip() {
        val shape = longArrayOf(3)
        val values = longArrayOf(1L, -2L, Long.MAX_VALUE)
        val buf = TensorBufferConverters.allocate(shape, TensorDataType.INT64)
        TensorBufferConverters.putInt64(buf, values)
        assertArrayEquals(values, TensorBufferConverters.getInt64(buf, 3))
    }

    @Test
    fun zeros_areZero() {
        val t = TensorBufferConverters.zeros(longArrayOf(2, 2), TensorDataType.FLOAT32)
        val vals = TensorBufferConverters.getFloat32(t.data, 4)
        assertArrayEquals(floatArrayOf(0f, 0f, 0f, 0f), vals, 0f)
    }

    @Test
    fun bytesPerElement() {
        assertEquals(4, TensorDataType.FLOAT32.bytesPerElement())
        assertEquals(2, TensorDataType.FLOAT16.bytesPerElement())
        assertEquals(4, TensorDataType.INT32.bytesPerElement())
        assertEquals(8, TensorDataType.INT64.bytesPerElement())
    }

    @Test
    fun elementCount() {
        assertEquals(24, TensorBufferConverters.elementCount(longArrayOf(2, 3, 4)))
    }
}
