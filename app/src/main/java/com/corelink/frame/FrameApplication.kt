package com.corelink.frame

import android.app.Application
import android.util.Log
import com.corelink.frame.core.startup.StaleTaskRecovery
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import javax.inject.Inject

@HiltAndroidApp
class FrameApplication : Application() {

    @Inject
    lateinit var staleTaskRecovery: StaleTaskRecovery

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        // Block briefly so no queue worker can observe a stale RUNNING row
        // before recovery finishes. Work is a single UPDATE per table.
        try {
            runBlocking {
                staleTaskRecovery.recoverInterruptedTasks()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Stale task recovery failed", e)
        }
        // Non-blocking follow-up slot for future startup work
        appScope.launch { /* reserved */ }
    }

    companion object {
        private const val TAG = "FrameApplication"
    }
}
