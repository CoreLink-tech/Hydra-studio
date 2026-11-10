package com.corelink.frame.ai.preprocessing

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import com.corelink.frame.ai.runtime.TensorData
import com.corelink.frame.ai.runtime.TensorDataType
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Prepares bitmaps for model input.
 * Does not invent model-specific normalisation — callers supply mean/std when known.
 */
@Singleton
class ImagePreprocessor @Inject constructor() {

    /**
     * Center-crop / letterbox and resize to exact target size without distorting aspect ratio.
     */
    fun resizeMaintainAspect(
        source: Bitmap,
        targetWidth: Int,
        targetHeight: Int,
        padColor: Int = 0xFF000000.toInt()
    ): Bitmap {
        val scale = minOf(
            targetWidth.toFloat() / source.width,
            targetHeight.toFloat() / source.height
        )
        val scaledW = (source.width * scale).toInt().coerceAtLeast(1)
        val scaledH = (source.height * scale).toInt().coerceAtLeast(1)

        val matrix = Matrix().apply {
            setScale(scale, scale)
        }
        val scaled = Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)

        val output = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        canvas.drawColor(padColor)
        val left = (targetWidth - scaledW) / 2f
        val top = (targetHeight - scaledH) / 2f
        canvas.drawBitmap(scaled, left, top, Paint(Paint.FILTER_BITMAP_FLAG))
        if (scaled !== source) scaled.recycle()
        return output
    }

    /**
     * Convert ARGB bitmap to NCHW float32 tensor normalised to [0, 1] or with mean/std.
     * Shape: [1, 3, height, width]
     */
    fun bitmapToFloatTensor(
        bitmap: Bitmap,
        mean: FloatArray = floatArrayOf(0f, 0f, 0f),
        std: FloatArray = floatArrayOf(1f, 1f, 1f)
    ): TensorData {
        val w = bitmap.width
        val h = bitmap.height
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)

        val plane = w * h
        val buffer = ByteBuffer.allocateDirect(1 * 3 * plane * 4).order(ByteOrder.nativeOrder())
        val floats = buffer.asFloatBuffer()

        // Channel-first: R plane, G plane, B plane
        for (c in 0 until 3) {
            val m = mean.getOrElse(c) { 0f }
            val s = std.getOrElse(c) { 1f }.coerceAtLeast(1e-6f)
            for (i in pixels.indices) {
                val px = pixels[i]
                val channel = when (c) {
                    0 -> (px shr 16) and 0xFF
                    1 -> (px shr 8) and 0xFF
                    else -> px and 0xFF
                }
                floats.put(((channel / 255f) - m) / s)
            }
        }
        buffer.rewind()
        return TensorData(
            shape = longArrayOf(1, 3, h.toLong(), w.toLong()),
            data = buffer,
            dataType = TensorDataType.FLOAT32
        )
    }
}
