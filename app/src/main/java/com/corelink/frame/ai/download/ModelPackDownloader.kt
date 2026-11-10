package com.corelink.frame.ai.download

import com.corelink.frame.ai.models.ModelFile
import com.corelink.frame.ai.models.ModelMetadata
import okhttp3.OkHttpClient
import java.io.File

/**
 * Downloads every file in a multi-file model pack sequentially into [modelDir].
 * Each file uses a `.part` sibling, verifies SHA-256, then renames atomically.
 * Pure JVM so unit tests can drive it with MockWebServer.
 */
class ModelPackDownloader(
    private val client: OkHttpClient = HttpClients.default(),
    private val fetcher: HttpModelFetcher = HttpModelFetcher(client)
) {
    sealed interface Result {
        data object Success : Result
        data class Failed(val reason: DownloadFailure, val message: String, val fileName: String?) : Result
    }

    fun downloadPack(
        metadata: ModelMetadata,
        modelDir: File,
        onProgress: (fileName: String, done: Long, total: Long) -> Unit,
        isCancelled: () -> Boolean
    ): Result {
        if (metadata.files.isEmpty()) {
            return Result.Failed(
                DownloadFailure.NOT_DOWNLOADABLE,
                "Catalogue entry has no files (not installable yet)",
                null
            )
        }
        val notDownloadable = metadata.files.filter { !it.isDownloadable }
        if (notDownloadable.isNotEmpty()) {
            return Result.Failed(
                DownloadFailure.NOT_DOWNLOADABLE,
                "Missing URL or SHA-256 for: ${notDownloadable.joinToString { it.name }}",
                notDownloadable.first().name
            )
        }

        modelDir.mkdirs()
        for (file in metadata.files) {
            if (isCancelled()) {
                return Result.Failed(DownloadFailure.CANCELLED, "cancelled", file.name)
            }
            val outcome = downloadOne(file, modelDir, onProgress, isCancelled)
            if (outcome is Result.Failed) return outcome
            DownloadBus.fileInstalled(metadata.id, file.name)
        }
        return Result.Success
    }

    private fun downloadOne(
        file: ModelFile,
        modelDir: File,
        onProgress: (fileName: String, done: Long, total: Long) -> Unit,
        isCancelled: () -> Boolean
    ): Result {
        val finalFile = File(modelDir, file.name)
        // Already present and matching size + checksum → skip
        if (finalFile.isFile &&
            (file.sizeBytes <= 0L || finalFile.length() == file.sizeBytes) &&
            Integrity.matches(Integrity.sha256(finalFile), file.sha256!!)
        ) {
            onProgress(file.name, file.sizeBytes.coerceAtLeast(finalFile.length()), file.sizeBytes.coerceAtLeast(finalFile.length()))
            return Result.Success
        }

        val partFile = File(modelDir, file.name + ".part")
        val result = fetcher.fetch(
            file = file,
            partFile = partFile,
            finalFile = finalFile,
            onProgress = { done, total -> onProgress(file.name, done, total) },
            isCancelled = isCancelled
        )
        return when (result) {
            is HttpModelFetcher.Result.Installed -> Result.Success
            is HttpModelFetcher.Result.Failed ->
                Result.Failed(result.reason, result.message, file.name)
        }
    }
}
