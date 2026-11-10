package com.corelink.frame.ai.models

/**
 * One file that belongs to a model pack.
 * Empty [url] / blank [sha256] means the file is not downloadable via the app yet
 * (user must import it manually).
 */
data class ModelFile(
    val name: String,
    val url: String? = null,
    val sizeBytes: Long = 0L,
    val sha256: String? = null,
    val role: String = "weight"
) {
    val isDownloadable: Boolean
        get() = !url.isNullOrBlank() && !sha256.isNullOrBlank()

    val hasChecksum: Boolean
        get() = !sha256.isNullOrBlank()
}
