package com.corelink.frame

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class FrameApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Future: initialise logging, crash handlers, model directory structure
    }
}
