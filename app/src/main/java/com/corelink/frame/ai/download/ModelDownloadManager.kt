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
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ModelDownloadManager @Inject constructor(
    private val fileManager: FrameFileManager
) {
    private val states = MutableStateFlow<Map<String, ModelInstallState>>(emptyMap())
    val installStates: Flow<Map<String, ModelInstallState>> = states.asStateFlow()

    fun getState(modelId: String): ModelInstallState {
        return states.value[modelId] ?: ModelInstallState(modelId, InstallState.NOT_PRESENT)
    }

    fun refreshLocalState(metadata: ModelMetadata) {
        val dir = fileManager.modelDir(metadata.id)
        val files = dir.listFiles()?.filter { it.isFile } ?: emptyList()
        val totalBytes = files.sumOf { it.length() }
        val newState = ModelInstallEvaluator.evaluate(metadata, dir, files, totalBytes)
        states.update { it + (metadata.id to newState) }
    }

    fun verifyChecksum(file: File, expectedSha256: String?): Boolean {
        if (expectedSha256.isNullOrBlank()) return true
        return ModelInstallEvaluator.defaultVerifyChecksum(file, expectedSha256)
    }

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
