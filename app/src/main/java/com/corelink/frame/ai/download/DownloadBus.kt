package com.corelink.frame.ai.download

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Process-wide download events. Workers and the in-process pack runner publish here;
 * ViewModels collect [events].
 */
object DownloadBus {
    private val _events = MutableSharedFlow<DownloadEvent>(
        extraBufferCapacity = 64,
        replay = 0
    )
    val events: SharedFlow<DownloadEvent> = _events.asSharedFlow()

    fun progress(modelId: String, fileName: String, done: Long, total: Long) {
        _events.tryEmit(DownloadEvent.Progress(modelId, fileName, done, total))
    }

    fun fileInstalled(modelId: String, fileName: String) {
        _events.tryEmit(DownloadEvent.FileInstalled(modelId, fileName))
    }

    fun packInstalled(modelId: String) {
        _events.tryEmit(DownloadEvent.PackInstalled(modelId))
    }

    fun failed(modelId: String, reason: DownloadFailure, message: String) {
        _events.tryEmit(DownloadEvent.Failed(modelId, reason, message))
    }
}

sealed interface DownloadEvent {
    val modelId: String

    data class Progress(
        override val modelId: String,
        val fileName: String,
        val done: Long,
        val total: Long
    ) : DownloadEvent

    data class FileInstalled(
        override val modelId: String,
        val fileName: String
    ) : DownloadEvent

    data class PackInstalled(
        override val modelId: String
    ) : DownloadEvent

    data class Failed(
        override val modelId: String,
        val reason: DownloadFailure,
        val message: String
    ) : DownloadEvent
}
