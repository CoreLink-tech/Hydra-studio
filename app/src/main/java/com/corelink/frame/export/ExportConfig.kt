package com.corelink.frame.export

data class ExportConfig(
    val width: Int = 1280,
    val height: Int = 720,
    val frameRate: Int = 30,
    val videoBitrate: Int = 4_000_000,
    val audioBitrate: Int = 128_000,
    val fileName: String = "frame_export.mp4"
) {
    companion object {
        val PRESETS = listOf(
            ExportConfig(1280, 720, 30, 4_000_000, fileName = "export_720p.mp4"),
            ExportConfig(854, 480, 30, 2_000_000, fileName = "export_480p.mp4"),
            ExportConfig(640, 360, 24, 1_200_000, fileName = "export_360p.mp4")
        )
    }
}

sealed class ExportProgress {
    data class Stage(val name: String, val detail: String = "") : ExportProgress()
    data class Percent(val value: Int) : ExportProgress()
}

sealed class ExportResult {
    data class Success(
        val outputPath: String,
        val fileSizeBytes: Long,
        val durationMs: Long
    ) : ExportResult()

    data class Failure(
        val message: String,
        val cause: Throwable? = null
    ) : ExportResult()
}
