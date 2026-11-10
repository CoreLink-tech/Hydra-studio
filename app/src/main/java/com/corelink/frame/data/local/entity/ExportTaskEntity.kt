package com.corelink.frame.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "export_tasks",
    indices = [Index("projectId"), Index("status")]
)
data class ExportTaskEntity(
    @PrimaryKey val id: String,
    val projectId: String,
    val outputFileName: String,
    val outputPath: String? = null,
    val width: Int = 1280,
    val height: Int = 720,
    val frameRate: Int = 30,
    val videoBitrate: Int = 4_000_000,
    val status: String = ExportStatus.PENDING.name,
    val stage: String = "",
    val progressPercent: Int? = null,
    val errorMessage: String? = null,
    val fileSizeBytes: Long? = null,
    val durationMs: Long? = null,
    val createdAt: Long,
    val startedAt: Long? = null,
    val finishedAt: Long? = null
)

enum class ExportStatus {
    PENDING,
    RUNNING,
    SUCCEEDED,
    FAILED,
    CANCELLED
}
