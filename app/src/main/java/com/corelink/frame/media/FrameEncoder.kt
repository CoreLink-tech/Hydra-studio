package com.corelink.frame.media

import android.graphics.Bitmap
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Encodes a list of bitmaps into a playable H.264 MP4.
 *
 * Input mode: **ByteBuffer (YUV420 semi-planar / NV12)** rather than a Surface + EGL
 * presentation-time path.
 *
 * Why ByteBuffer on low-end devices:
 * - Presentation timestamps are set explicitly on [MediaCodec.queueInputBuffer]
 *   (`frameIndex * 1_000_000 / frameRate`) — no reliance on eglPresentationTimeANDROID.
 * - Avoids EGL context creation, which is fragile on some entry-level GPUs.
 * - Trade-off: CPU cost to convert ARGB → NV12; acceptable for short AI clips
 *   (tens of frames), which is the product’s generation target.
 *
 * On failure the partial output file is deleted and resources are released.
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
        require(request.frameRate > 0) { "frameRate must be > 0" }

        val width = request.frames.first().width
        val height = request.frames.first().height
        require(width > 0 && height > 0) { "Invalid frame size" }
        val encW = width and 1.inv()
        val encH = height and 1.inv()

        val expectedDurationMs = expectedDurationMs(request.frames.size, request.frameRate)

        var codec: MediaCodec? = null
        var muxer: MediaMuxer? = null
        var muxerStarted = false
        var success = false

        try {
            val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, encW, encH).apply {
                setInteger(
                    MediaFormat.KEY_COLOR_FORMAT,
                    MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar
                )
                setInteger(MediaFormat.KEY_BIT_RATE, request.bitRate)
                setInteger(MediaFormat.KEY_FRAME_RATE, request.frameRate)
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, request.iFrameInterval)
            }

            codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
            codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            codec.start()

            muxer = MediaMuxer(
                request.outputFile.absolutePath,
                MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4
            )

            val bufferInfo = MediaCodec.BufferInfo()
            var trackIndex = -1
            val nv12 = ByteArray(encW * encH * 3 / 2)
            val maxDrainIterations = request.frames.size * 64 + 256

            // Feed frames with explicit PTS
            for (index in request.frames.indices) {
                val ptsUs = presentationTimeUs(index, request.frameRate)
                var inputIndex = codec.dequeueInputBuffer(INPUT_TIMEOUT_US)
                var waitLoops = 0
                while (inputIndex < 0 && waitLoops < 50) {
                    // Drain while waiting for an input slot so the codec cannot stall.
                    val drained = drainEncoder(
                        codec = codec,
                        muxer = muxer,
                        bufferInfo = bufferInfo,
                        trackIndex = trackIndex,
                        muxerStarted = muxerStarted,
                        endOfStream = false,
                        maxIterations = 32
                    )
                    trackIndex = drained.trackIndex
                    muxerStarted = drained.muxerStarted
                    inputIndex = codec.dequeueInputBuffer(INPUT_TIMEOUT_US)
                    waitLoops++
                }
                if (inputIndex < 0) {
                    throw IllegalStateException("Encoder input buffer unavailable")
                }

                val inputBuffer = codec.getInputBuffer(inputIndex)
                    ?: throw IllegalStateException("Null encoder input buffer")
                inputBuffer.clear()

                val frame = request.frames[index]
                argbToNv12(frame, encW, encH, nv12)
                inputBuffer.put(nv12)

                codec.queueInputBuffer(
                    inputIndex,
                    0,
                    nv12.size,
                    ptsUs,
                    0
                )

                val drained = drainEncoder(
                    codec = codec,
                    muxer = muxer,
                    bufferInfo = bufferInfo,
                    trackIndex = trackIndex,
                    muxerStarted = muxerStarted,
                    endOfStream = false,
                    maxIterations = 32
                )
                trackIndex = drained.trackIndex
                muxerStarted = drained.muxerStarted
            }

            // Signal end of stream
            val eosIndex = codec.dequeueInputBuffer(INPUT_TIMEOUT_US)
            if (eosIndex >= 0) {
                codec.queueInputBuffer(
                    eosIndex,
                    0,
                    0,
                    presentationTimeUs(request.frames.size, request.frameRate),
                    MediaCodec.BUFFER_FLAG_END_OF_STREAM
                )
            } else {
                Log.w(TAG, "Could not queue EOS input; draining anyway")
            }

            val finalDrain = drainEncoder(
                codec = codec,
                muxer = muxer,
                bufferInfo = bufferInfo,
                trackIndex = trackIndex,
                muxerStarted = muxerStarted,
                endOfStream = true,
                maxIterations = maxDrainIterations
            )
            muxerStarted = finalDrain.muxerStarted

            if (!muxerStarted) {
                throw IllegalStateException("Muxer never started — no encoded frames")
            }

            success = true
            EncodeResult(
                file = request.outputFile,
                width = encW,
                height = encH,
                frameCount = request.frames.size,
                durationMs = expectedDurationMs
            )
        } catch (e: Exception) {
            Log.e(TAG, "encode failed", e)
            throw e
        } finally {
            try {
                codec?.stop()
            } catch (_: Exception) {
            }
            try {
                codec?.release()
            } catch (_: Exception) {
            }
            try {
                if (muxerStarted) {
                    muxer?.stop()
                }
            } catch (_: Exception) {
            }
            try {
                muxer?.release()
            } catch (_: Exception) {
            }
            if (!success && request.outputFile.exists()) {
                if (!request.outputFile.delete()) {
                    Log.w(TAG, "Could not delete partial output ${request.outputFile}")
                }
            }
        }
    }

    private data class DrainState(val trackIndex: Int, val muxerStarted: Boolean)

    /**
     * Drains encoder output. When [endOfStream] is true, loops until EOS flag or
     * [maxIterations] is hit (never spins forever).
     */
    private fun drainEncoder(
        codec: MediaCodec,
        muxer: MediaMuxer,
        bufferInfo: MediaCodec.BufferInfo,
        trackIndex: Int,
        muxerStarted: Boolean,
        endOfStream: Boolean,
        maxIterations: Int
    ): DrainState {
        var track = trackIndex
        var started = muxerStarted
        var iterations = 0
        var sawEos = false

        while (iterations < maxIterations && !sawEos) {
            iterations++
            val timeoutUs = if (endOfStream) DRAIN_TIMEOUT_US else 0L
            val outIndex = codec.dequeueOutputBuffer(bufferInfo, timeoutUs)
            when {
                outIndex == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                    if (!endOfStream) break
                    // Still waiting for EOS; continue until maxIterations
                    continue
                }
                outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    if (started) {
                        throw IllegalStateException("Encoder output format changed twice")
                    }
                    track = muxer.addTrack(codec.outputFormat)
                    muxer.start()
                    started = true
                }
                outIndex == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED -> {
                    // Deprecated signal; ignore on modern API levels.
                }
                outIndex >= 0 -> {
                    val encoded = codec.getOutputBuffer(outIndex)
                    if (encoded != null && bufferInfo.size > 0 && started) {
                        encoded.position(bufferInfo.offset)
                        encoded.limit(bufferInfo.offset + bufferInfo.size)
                        muxer.writeSampleData(track, encoded, bufferInfo)
                    }
                    val isEos = bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                    codec.releaseOutputBuffer(outIndex, false)
                    if (isEos) {
                        sawEos = true
                    }
                }
            }
        }

        if (endOfStream && !sawEos) {
            Log.w(TAG, "Drain ended without EOS after $iterations iterations")
        }
        return DrainState(track, started)
    }

    companion object {
        private const val TAG = "FrameEncoder"
        private const val INPUT_TIMEOUT_US = 10_000L
        private const val DRAIN_TIMEOUT_US = 10_000L

        /** PTS in microseconds for frame [index] (0-based). */
        fun presentationTimeUs(index: Int, frameRate: Int): Long {
            require(frameRate > 0)
            return index.toLong() * 1_000_000L / frameRate
        }

        /** Nominal media duration for [frameCount] frames at [frameRate]. */
        fun expectedDurationMs(frameCount: Int, frameRate: Int): Long {
            require(frameRate > 0)
            if (frameCount <= 0) return 0L
            return frameCount.toLong() * 1000L / frameRate
        }

        /**
         * Converts an ARGB_8888 bitmap into NV12 (YUV420 semi-planar) for [encW]x[encH].
         * Output size must be encW * encH * 3 / 2.
         */
        fun argbToNv12(bitmap: Bitmap, encW: Int, encH: Int, out: ByteArray) {
            require(out.size >= encW * encH * 3 / 2)
            val srcW = bitmap.width
            val srcH = bitmap.height
            val pixels = IntArray(srcW * srcH)
            bitmap.getPixels(pixels, 0, srcW, 0, 0, srcW, srcH)

            val ySize = encW * encH
            var yIndex = 0
            var uvIndex = ySize

            for (j in 0 until encH) {
                val srcJ = (j * srcH) / encH
                for (i in 0 until encW) {
                    val srcI = (i * srcW) / encW
                    val px = pixels[srcJ * srcW + srcI]
                    val r = (px shr 16) and 0xFF
                    val g = (px shr 8) and 0xFF
                    val b = px and 0xFF
                    // ITU-R BT.601
                    val y = ((66 * r + 129 * g + 25 * b + 128) shr 8) + 16
                    out[yIndex++] = y.coerceIn(0, 255).toByte()

                    if (j % 2 == 0 && i % 2 == 0) {
                        val u = ((-38 * r - 74 * g + 112 * b + 128) shr 8) + 128
                        val v = ((112 * r - 94 * g - 18 * b + 128) shr 8) + 128
                        out[uvIndex++] = u.coerceIn(0, 255).toByte()
                        out[uvIndex++] = v.coerceIn(0, 255).toByte()
                    }
                }
            }
        }
    }
}
