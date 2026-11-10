package com.corelink.frame.ai.download

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.corelink.frame.ai.models.InstallState
import com.corelink.frame.ai.models.ModelInstallState
import com.corelink.frame.ai.models.ModelMetadata
import com.corelink.frame.core.files.FrameFileManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Install-state registry + download orchestration for multi-file model packs.
 *
 * Downloads run via WorkManager (unique work per modelId) so they survive
 * screen-off and process death. Partial `.part` files are kept for resume.
 *
 * Wi-Fi-only is the default. Packs whose declared total exceeds
 * [LARGE_PACK_CONFIRM_BYTES] require an explicit [userConfirmedLarge] flag
 * before enqueue (UI must show the size and ask).
 */
@Singleton
class ModelDownloadManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val fileManager: FrameFileManager
) {
    private val states = MutableStateFlow<Map<String, ModelInstallState>>(emptyMap())
    val installStates: Flow<Map<String, ModelInstallState>> = states.asStateFlow()

    /** Live download events (progress / installed / failed). */
    val downloadEvents: Flow<DownloadEvent> = DownloadBus.events

    /** Default: require unmetered network. */
    @Volatile
    var wifiOnly: Boolean = true

    fun getState(modelId: String): ModelInstallState {
        return states.value[modelId] ?: ModelInstallState(modelId, InstallState.NOT_PRESENT)
    }

    fun refreshLocalState(metadata: ModelMetadata) {
        val dir = fileManager.modelDir(metadata.id)
        val files = dir.listFiles()?.filter { it.isFile && !it.name.endsWith(".part") } ?: emptyList()
        val totalBytes = files.sumOf { it.length() }
        val newState = ModelInstallEvaluator.evaluate(metadata, dir, files, totalBytes)
        states.update { it + (metadata.id to newState) }
    }

    fun verifyChecksum(file: File, expectedSha256: String?): Boolean {
        if (expectedSha256.isNullOrBlank()) return true
        return Integrity.matches(Integrity.sha256(file), expectedSha256)
    }

    /**
     * Enqueue a pack download. Returns a failure reason without starting work when
     * the pack is not downloadable, storage is blocked, or a large pack needs confirmation.
     *
     * @return null when work was enqueued; otherwise an explicit [DownloadFailure].
     */
    fun enqueueDownload(
        metadata: ModelMetadata,
        userConfirmedLarge: Boolean = false,
        userAllowsCellular: Boolean = false
    ): DownloadFailure? {
        if (!metadata.isDownloadable) {
            return DownloadFailure.NOT_DOWNLOADABLE
        }
        val total = metadata.declaredTotalBytes
        if (total >= LARGE_PACK_CONFIRM_BYTES && !userConfirmedLarge) {
            // UI must show size and re-call with userConfirmedLarge = true.
            return DownloadFailure.STORAGE_BLOCKED
        }

        val free = context.filesDir.usableSpace
        val already = fileManager.modelDir(metadata.id)
            .listFiles()
            ?.filter { it.isFile && !it.name.endsWith(".part") }
            ?.sumOf { it.length() } ?: 0L
        when (StoragePolicy.check(free, total, already)) {
            StoragePolicy.Verdict.BLOCKED_INSUFFICIENT -> return DownloadFailure.STORAGE_BLOCKED
            StoragePolicy.Verdict.WARN_LOW_SPACE, StoragePolicy.Verdict.OK -> Unit
        }

        states.update {
            it + (metadata.id to ModelInstallState(
                modelId = metadata.id,
                state = InstallState.DOWNLOADING,
                localPath = fileManager.modelDir(metadata.id).absolutePath
            ))
        }

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(
                if (wifiOnly && !userAllowsCellular) NetworkType.UNMETERED
                else NetworkType.CONNECTED
            )
            .build()
        val request = OneTimeWorkRequestBuilder<ModelDownloadWorker>()
            .setConstraints(constraints)
            .setInputData(
                Data.Builder()
                    .putString(ModelDownloadWorker.KEY_MODEL_ID, metadata.id)
                    .putBoolean(ModelDownloadWorker.KEY_WIFI_ONLY, wifiOnly)
                    .build()
            )
            .build()
        WorkManager.getInstance(context)
            .enqueueUniqueWork(workName(metadata.id), ExistingWorkPolicy.KEEP, request)
        return null
    }

    fun cancel(modelId: String) {
        WorkManager.getInstance(context).cancelUniqueWork(workName(modelId))
        states.update { map ->
            val current = map[modelId] ?: return@update map
            if (current.state == InstallState.DOWNLOADING) {
                map + (modelId to current.copy(state = InstallState.PARTIAL))
            } else map
        }
    }

    fun markNotPresent(modelId: String) {
        states.update {
            it + (modelId to ModelInstallState(modelId, InstallState.NOT_PRESENT))
        }
    }

    /**
     * Legacy single-URL entry. Prefer [enqueueDownload].
     */
    @Deprecated("Use enqueueDownload(metadata)")
    suspend fun download(metadata: ModelMetadata, url: String): Result<File> {
        val reason = enqueueDownload(metadata, userConfirmedLarge = true)
        return if (reason == null) {
            Result.failure(
                UnsupportedOperationException(
                    "Download enqueued for ${metadata.id}. Observe downloadEvents / installStates for progress."
                )
            )
        } else {
            Result.failure(UnsupportedOperationException("Cannot download: $reason"))
        }
    }

    private fun isOnWifi(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    }

    private fun workName(modelId: String) = "frame-model-download-$modelId"

    companion object {
        /** Packs at or above this size require explicit user confirmation before enqueue. */
        const val LARGE_PACK_CONFIRM_BYTES: Long = 100L * 1024 * 1024
    }
}
