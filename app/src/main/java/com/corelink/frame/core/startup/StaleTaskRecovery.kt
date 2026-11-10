package com.corelink.frame.core.startup

import android.util.Log
import com.corelink.frame.data.local.dao.ExportTaskDao
import com.corelink.frame.data.local.dao.GenerationTaskDao
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Clears generation/export tasks left in an in-progress state after process death.
 * Must run once at app start before [com.corelink.frame.ai.generation.GenerationQueue]
 * can pick up work; otherwise a permanent RUNNING row blocks the queue forever.
 */
@Singleton
class StaleTaskRecovery @Inject constructor(
    private val generationTaskDao: GenerationTaskDao,
    private val exportTaskDao: ExportTaskDao
) {
    suspend fun recoverInterruptedTasks(
        nowMs: Long = System.currentTimeMillis(),
        message: String = INTERRUPTED_MESSAGE
    ): RecoveryResult {
        val generationFailed = generationTaskDao.failInterruptedTasks(message, nowMs)
        val exportFailed = exportTaskDao.failInterruptedTasks(message, nowMs)
        if (generationFailed > 0 || exportFailed > 0) {
            Log.w(
                TAG,
                "Recovered interrupted tasks: generation=$generationFailed export=$exportFailed"
            )
        }
        return RecoveryResult(
            generationTasksFailed = generationFailed,
            exportTasksFailed = exportFailed
        )
    }

    data class RecoveryResult(
        val generationTasksFailed: Int,
        val exportTasksFailed: Int
    )

    companion object {
        const val INTERRUPTED_MESSAGE = "Interrupted by app restart"
        private const val TAG = "StaleTaskRecovery"
    }
}
