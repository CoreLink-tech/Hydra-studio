package com.corelink.frame.ai.download

import com.corelink.frame.ai.models.InstallState
import com.corelink.frame.ai.models.ModelInstallState
import com.corelink.frame.ai.models.ModelMetadata
import com.corelink.frame.core.files.FrameFileManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.io.File
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages model package download, verification and local installation state.
 *
 * Download of multi-hundred-MB files requires network and is intentionally
 * not started automatically. This class provides the structure and integrity
 * checks required by the specification.
 */
@Singleton
class ModelDownloadManager @Inject constructor(
    private val fileManager: FrameFileManager
) {
    private val states = MutableStateFlow<Map<String, ModelInstallState>>(emptyMap())
    val installStates: Flow<Map<String, ModelInstallState>> = states.asStateFlow()

    fun getState(modelId: String): ModelInstallState {
        return states.value[modelId] ?: ModelInstallState(modelId, InstallState.NOT_PRESENT)
    }

    /**
     * Scan local model directory and update install state.
     * A model is INSTALLED only when the expected files exist.
     */
    fun refreshLocalState(metadata: ModelMetadata) {
        val dir = fileManager.modelDir(metadata.id)
        val files = dir.listFiles()?.filter { it.isFile } ?: emptyList()
        val totalBytes = files.sumOf { it.length() }

        val newState = when {
            files.isEmpty() -> ModelInstallState(metadata.id, InstallState.NOT_PRESENT)
            totalBytes < 1_000_000L -> ModelInstallState(
                modelId = metadata.id,
                state = InstallState.CORRUPT,
                localPath = dir.absolutePath,
                bytesOnDisk = totalBytes,
                validationError = "Model directory contains insufficient data"
            )
            else -> ModelInstallState(
                modelId = metadata.id,
                state = InstallState.INSTALLED,
                localPath = dir.absolutePath,
                bytesOnDisk = totalBytes,
                lastValidatedAt = System.currentTimeMillis()
            )
        }
        states.update { it + (metadata.id to newState) }
    }

    /**
     * Verify SHA-256 if a trusted checksum is supplied.
     */
    fun verifyChecksum(file: File, expectedSha256: String?): Boolean {
        if (expectedSha256.isNullOrBlank()) return true
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(8192)
            var read: Int
            while (input.read(buffer).also { read = it } > 0) {
                digest.update(buffer, 0, read)
            }
        }
        val actual = digest.digest().joinToString("") { "%02x".format(it) }
        return actual.equals(expectedSha256, ignoreCase = true)
    }

    /**
     * Placeholder for resumable download. Real implementation will use
     * HttpURLConnection / OkHttp with Range headers once model URLs are final.
     */
    suspend fun download(metadata: ModelMetadata, url: String): Result<File> {
        return Result.failure(
            UnsupportedOperationException(
                "Model download is not enabled until a verified package URL and checksum are published. " +
                    "See docs/MODEL_EVALUATION.md and docs/POC_CHECKLIST.md."
            )
        )
    }

    fun markNotPresent(modelId: String) {
        states.update {
            it + (modelId to ModelInstallState(modelId, InstallState.NOT_PRESENT))
        }
    }
}
