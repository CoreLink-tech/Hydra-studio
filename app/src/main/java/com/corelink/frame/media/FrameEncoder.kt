package com.corelink.frame.media

import android.graphics.Bitmap
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.view.Surface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Encodes a list of bitmaps into a playable H.264 MP4.
 * Used after inference produces frames.
 *
 * This is real encoding code — it will only succeed when given actual frames.
 */
@Singleton
class FrameEncoder @Inject constructor() {

    data class EncodeRequest(
        val frames: List<Bitmap>,
        val outputFile: File,
        val frameRate: Int = 8,
        val bitRate: Int = 2_000_000,
        val iFrameInterval: Int = 1
    )

    data class EncodeResult(
        val file: File,
        val width: Int,
        val height: Int,
        val frameCount: Int,
        val durationMs: Long
    )

    suspend fun encode(request: EncodeRequest): EncodeResult = withContext(Dispatchers.Default) {
        require(request.frames.isNotEmpty()) { "No frames to encode" }

        val width = request.frames.first().width
        val height = request.frames.first().height
        require(width > 0 && height > 0) { "Invalid frame size" }
        // MediaCodec often requires even dimensions
        val encW = width and 1.inv()
        val encH = height and 1.inv()

        val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, encW, encH).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
            setInteger(MediaFormat.KEY_BIT_RATE, request.bitRate)
            setInteger(MediaFormat.KEY_FRAME_RATE, request.frameRate)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, request.iFrameInterval)
        }

        val codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
        codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        val inputSurface: Surface = codec.createInputSurface()
        codec.start()

        val muxer = MediaMuxer(request.outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        var trackIndex = -1
        var muxerStarted = false
        val bufferInfo = MediaCodec.BufferInfo()
        val frameDurationUs = 1_000_000L / request.frameRate

        try {
            // Draw frames onto the input surface
            val canvasHelper = SurfaceFrameDrawer(inputSurface)
            request.frames.forEachIndexed { index, bitmap ->
                canvasHelper.drawBitmap(bitmap, encW, encH)
                // Drain encoder periodically
                drainEncoder(codec, bufferInfo, muxer, trackIndex, muxerStarted, force = false).also {
                    trackIndex = it.first
                    muxerStarted = it.second
                }
                // Presentation time is handled by the encoder when using surface input;
                // we still advance logically for duration calculation.
            }
            codec.signalEndOfInputStream()
            drainEncoder(codec, bufferInfo, muxer, trackIndex, muxerStarted, force = true).also {
                trackIndex = it.first
                muxerStarted = it.second
            }
        } finally {
            codec.stop()
            codec.release()
            inputSurface.release()
            if (muxerStarted) muxer.stop()
            muxer.release()
        }

        val durationMs = (request.frames.size * 1000L) / request.frameRate
        EncodeResult(
            file = request.outputFile,
            width = encW,
            height = encH,
            frameCount = request.frames.size,
            durationMs = durationMs
        )
    }

    private fun drainEncoder(
        codec: MediaCodec,
        bufferInfo: MediaCodec.BufferInfo,
        muxer: MediaMuxer,
        trackIndexIn: Int,
        muxerStartedIn: Boolean,
        force: Boolean
    ): Pair<Int, Boolean> {
        var trackIndex = trackIndexIn
        var muxerStarted = muxerStartedIn
        while (true) {
            val outIndex = codec.dequeueOutputBuffer(bufferInfo, if (force) 10_000 else 0)
            when {
                outIndex == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                    if (!force) break
                    else if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) break
                    else continue
                }
                outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    if (muxerStarted) throw RuntimeException("Format changed twice")
                    trackIndex = muxer.addTrack(codec.outputFormat)
                    muxer.start()
                    muxerStarted = true
                }
                outIndex >= 0 -> {
                    val encodedData: ByteBuffer = codec.getOutputBuffer(outIndex)
                        ?: throw RuntimeException("Encoder output buffer null")
                    if (bufferInfo.size > 0 && muxerStarted) {
                        encodedData.position(bufferInfo.offset)
                        encodedData.limit(bufferInfo.offset + bufferInfo.size)
                        muxer.writeSampleData(trackIndex, encodedData, bufferInfo)
                    }
                    codec.releaseOutputBuffer(outIndex, false)
                    if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) break
                }
            }
        }
        return trackIndex to muxerStarted
    }
}

/**
 * Helper that locks a Surface canvas and draws a bitmap scaled to the encoder size.
 */
private class SurfaceFrameDrawer(private val surface: Surface) {
    fun drawBitmap(bitmap: Bitmap, width: Int, height: Int) {
        val canvas = surface.lockCanvas(null)
        try {
            canvas.drawColor(0xFF000000.toInt())
            val src = android.graphics.Rect(0, 0, bitmap.width, bitmap.height)
            val dst = android.graphics.Rect(0, 0, width, height)
            canvas.drawBitmap(bitmap, src, dst, null)
        } finally {
            surface.unlockCanvasAndPost(canvas)
        }
    }
}
