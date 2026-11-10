package com.corelink.frame.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "generation_tasks",
    indices = [Index("status"), Index("createdAt")]
)
data class GenerationTaskEntity(
    @PrimaryKey val id: String,
    val modelId: String,
    val mode: String, // IMAGE_TO_VIDEO | TEXT_TO_VIDEO
    val prompt: String = "",
    val motionPrompt: String = "",
    val negativePrompt: String = "",
    val inputImagePath: String? = null,
    val targetFrames: Int = 16,
    val targetWidth: Int = 512,
    val targetHeight: Int = 512,
    val status: String = TaskStatus.QUEUED.name,
    val stage: String = "",
    val stageDetail: String = "",
    val progressPercent: Int? = null, // null = unknown / stage-only
    val errorMessage: String? = null,
    val errorReason: String? = null,
    val outputClipId: String? = null,
    val outputPath: String? = null,
    val generationTimeMs: Long? = null,
    val createdAt: Long,
    val startedAt: Long? = null,
    val finishedAt: Long? = null,
    val projectId: String? = null
)

enum class TaskStatus {
    QUEUED,
    RUNNING,
    CANCELLING,
    CANCELLED,
    SUCCEEDED,
    FAILED
}
