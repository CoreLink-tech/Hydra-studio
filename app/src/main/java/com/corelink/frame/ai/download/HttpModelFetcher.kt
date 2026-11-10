package com.corelink.frame.ai.download

import com.corelink.frame.ai.models.ModelFile
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/**
 * Resumable HTTP download of one [ModelFile] into [partFile], then SHA-256 verification and an
 * atomic rename to [finalFile]. Pure JVM (OkHttp only), so it can be tested with MockWebServer.
 *
 * Ported from HYDRA.AI core/download and adapted to FRAME's multi-file [ModelFile].
 */
class HttpModelFetcher(
    private val client: OkHttpClient,
) {
    sealed interface Result {
        data class Installed(val bytes: Long) : Result
        data class Failed(val reason: DownloadFailure, val message: String) : Result
    }

    /**
     * @param onProgress called with (downloaded, total) as bytes arrive.
     * @param isCancelled polled between chunks so a cancel stops quickly and keeps the partial file.
     */
    fun fetch(
        file: ModelFile,
        partFile: File,
        finalFile: File,
        onProgress: (Long, Long) -> Unit,
        isCancelled: () -> Boolean,
    ): Result {
        val url = file.url
        if (url.isNullOrBlank()) {
            return Result.Failed(DownloadFailure.NOT_DOWNLOADABLE, "No download URL for ${file.name}")
        }
        if (file.sha256.isNullOrBlank()) {
            return Result.Failed(DownloadFailure.NOT_DOWNLOADABLE, "No SHA-256 for ${file.name}")
        }

        val total = file.sizeBytes
        val plan = ResumePlan.decide(
            partialBytes = if (partFile.exists()) partFile.length() else 0L,
            totalBytes = total,
            supportsRange = true
        )
        var offset = when (plan) {
            ResumePlan.FromScratch -> 0L
            is ResumePlan.Continue -> plan.offset
            ResumePlan.AlreadyComplete -> return verifyAndInstall(file, partFile, finalFile)
        }
        if (offset == 0L && partFile.exists()) partFile.delete()

        val builder = Request.Builder().url(url)
        if (offset > 0L) builder.header("Range", "bytes=$offset-")
        val response = try {
            client.newCall(builder.build()).execute()
        } catch (e: IOException) {
            return Result.Failed(DownloadFailure.NO_NETWORK, e.message ?: "network error")
        }

        response.use { resp ->
            if (!resp.isSuccessful && resp.code != 206) {
                return Result.Failed(DownloadFailure.HTTP_ERROR, "HTTP ${resp.code}")
            }
            val contentRangeStart = resp.header("Content-Range")
                ?.substringAfter("bytes ", "")
                ?.substringBefore("-")
                ?.toLongOrNull()
            if (offset > 0L && !RangeResponse.canAppend(resp.code, offset, contentRangeStart)) {
                // Server ignored the Range header: start over instead of corrupting the file.
                offset = 0L
                partFile.delete()
            }
            val body = resp.body ?: return Result.Failed(DownloadFailure.IO_ERROR, "empty body")
            val append = offset > 0L
            try {
                FileOutputStream(partFile, append).use { sink ->
                    body.byteStream().use { input ->
                        val buffer = ByteArray(1 shl 16)
                        var written = offset
                        while (true) {
                            if (isCancelled()) {
                                return Result.Failed(DownloadFailure.CANCELLED, "cancelled")
                            }
                            val read = input.read(buffer)
                            if (read < 0) break
                            sink.write(buffer, 0, read)
                            written += read
                            val reportTotal = if (total > 0L) total else written
                            onProgress(written, reportTotal)
                        }
                    }
                }
            } catch (e: IOException) {
                return Result.Failed(DownloadFailure.IO_ERROR, e.message ?: "write error")
            }
        }
        return verifyAndInstall(file, partFile, finalFile)
    }

    private fun verifyAndInstall(file: ModelFile, partFile: File, finalFile: File): Result {
        if (!partFile.exists()) {
            return Result.Failed(DownloadFailure.INCOMPLETE, "partial file missing after download")
        }
        if (file.sizeBytes > 0L && partFile.length() != file.sizeBytes) {
            return Result.Failed(
                DownloadFailure.INCOMPLETE,
                "incomplete download: got ${partFile.length()}, expected ${file.sizeBytes}"
            )
        }
        val actual = Integrity.sha256(partFile)
        if (!Integrity.matches(actual, file.sha256!!)) {
            partFile.delete()
            return Result.Failed(DownloadFailure.CHECKSUM_MISMATCH, "checksum mismatch for ${file.name}")
        }
        finalFile.parentFile?.mkdirs()
        if (finalFile.exists()) finalFile.delete()
        if (!partFile.renameTo(finalFile)) {
            return Result.Failed(DownloadFailure.IO_ERROR, "could not move ${file.name} into place")
        }
        return Result.Installed(finalFile.length())
    }
}
