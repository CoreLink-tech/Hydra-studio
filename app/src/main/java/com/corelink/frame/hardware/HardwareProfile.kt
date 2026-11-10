package com.corelink.frame.hardware

/**
 * Adaptive hardware profiles used to gate models and generation settings.
 * Selection is based primarily on total RAM, refined by available memory,
 * architecture and other signals. Never assumes high-end capability.
 */
enum class HardwareProfile(
    val id: String,
    val displayName: String,
    val minRamGb: Float,
    val description: String
) {
    LOW_MEMORY(
        id = "low_memory",
        displayName = "Low-memory",
        minRamGb = 0f,
        description = "Very conservative. Smaller models only, sequential processing, short clips, aggressive cleanup."
    ),
    BALANCED(
        id = "balanced",
        displayName = "Balanced",
        minRamGb = 3.5f,
        description = "Optimised for ~4 GB devices. Careful model loading, limited frame batches, conservative settings."
    ),
    PERFORMANCE(
        id = "performance",
        displayName = "Performance",
        minRamGb = 5.5f,
        description = "More flexible generation settings and larger supported models where validated."
    ),
    HIGH_PERFORMANCE(
        id = "high_performance",
        displayName = "High-performance",
        minRamGb = 7.5f,
        description = "Higher quality options, larger frame batches where safe, additional processing flexibility."
    );

    companion object {
        fun fromTotalRamGb(totalRamGb: Float): HardwareProfile = when {
            totalRamGb < 3.5f -> LOW_MEMORY
            totalRamGb < 5.5f -> BALANCED
            totalRamGb < 7.5f -> PERFORMANCE
            else -> HIGH_PERFORMANCE
        }
    }
}
