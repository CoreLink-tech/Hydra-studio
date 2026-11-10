package com.corelink.frame.ai.download

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.WorkerParameters
import com.corelink.frame.ai.models.ModelCatalogue
import java.io.File

/**
 * WorkManager entry point. Downloads every file in a model pack sequentially so the job
 * survives screen-off and process death. Progress and terminal events go through [DownloadBus].
 *
 * No foreground service: HYDRA.AI used the same pattern (network-constrained unique work).
 * FOREGROUND_SERVICE_DATA_SYNC is intentionally not declared until a measured need appears.
 */
class ModelDownloadWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val modelId = inputData.getString(KEY_MODEL_ID) ?: return Result.failure()
        val metadata = ModelCatalogue.byId(modelId) ?: return Result.failure()
        if (!metadata.isDownloadable) {
            DownloadBus.failed(
                modelId,
                DownloadFailure.NOT_DOWNLOADABLE,
                "Model is not downloadable (missing URLs or checksums)"
            )
            return Result.failure()
        }

        val dir = File(applicationContext.filesDir, "FRAME/Models/$modelId").apply { mkdirs() }
        val pack = ModelPackDownloader()
        val outcome = pack.downloadPack(
            metadata = metadata,
            modelDir = dir,
            onProgress = { name, done, total -> DownloadBus.progress(modelId, name, done, total) },
            isCancelled = { isStopped }
        )
        return when (outcome) {
            is ModelPackDownloader.Result.Success -> {
                DownloadBus.packInstalled(modelId)
                Result.success(Data.Builder().putString(KEY_MODEL_ID, modelId).build())
            }
            is ModelPackDownloader.Result.Failed -> {
                DownloadBus.failed(modelId, outcome.reason, outcome.message)
                if (outcome.reason == DownloadFailure.NO_NETWORK) Result.retry()
                else Result.failure()
            }
        }
    }

    companion object {
        const val KEY_MODEL_ID = "model_id"
        const val KEY_WIFI_ONLY = "wifi_only"
    }
}
