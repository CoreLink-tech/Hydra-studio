package com.corelink.frame.ai.download

/**
 * Explicit failure reasons for model downloads.
 * UI and workers map these to user-visible messages; never swallow.
 */
enum class DownloadFailure {
    NO_NETWORK,
    HTTP_ERROR,
    IO_ERROR,
    CHECKSUM_MISMATCH,
    INCOMPLETE,
    CANCELLED,
    STORAGE_BLOCKED,
    WAITING_FOR_WIFI,
    NOT_DOWNLOADABLE
}
