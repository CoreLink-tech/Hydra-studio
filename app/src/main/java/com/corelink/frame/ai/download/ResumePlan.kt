package com.corelink.frame.ai.download

/**
 * Decides how to resume a partial download. Pure logic so it can be unit tested without a device.
 */
sealed interface ResumePlan {
    /** Start over: no usable partial file, or the server ignored the Range header. */
    data object FromScratch : ResumePlan

    /** Ask the server for bytes from [offset] onwards (HTTP 206). */
    data class Continue(val offset: Long) : ResumePlan

    /** The partial file already holds the full size; only verification is left. */
    data object AlreadyComplete : ResumePlan

    companion object {
        /**
         * @param partialBytes size of the local .part file
         * @param totalBytes expected total size from the catalogue, or -1 / 0 if unknown
         * @param supportsRange whether the server is expected to honour Range
         */
        fun decide(partialBytes: Long, totalBytes: Long, supportsRange: Boolean): ResumePlan {
            if (partialBytes <= 0L) return FromScratch
            if (!supportsRange) return FromScratch
            if (totalBytes > 0L && partialBytes == totalBytes) return AlreadyComplete
            if (totalBytes > 0L && partialBytes > totalBytes) return FromScratch
            return Continue(partialBytes)
        }
    }
}

/** Validates the HTTP status for a resumed request. */
object RangeResponse {
    /**
     * Returns true when the response can be appended to the partial file.
     * A 200 response to a Range request means the server sent the whole file, so we must truncate.
     */
    fun canAppend(status: Int, requestedOffset: Long, contentRangeStart: Long?): Boolean =
        status == 206 && contentRangeStart == requestedOffset
}
