package com.corelink.frame.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "clips",
    foreignKeys = [
        ForeignKey(
            entity = ProjectEntity::class,
            parentColumns = ["id"],
            childColumns = ["projectId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = SceneEntity::class,
            parentColumns = ["id"],
            childColumns = ["sceneId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [Index("projectId"), Index("sceneId")]
)
data class ClipEntity(
    @PrimaryKey val id: String,
    val projectId: String,
    val sceneId: String? = null,
    val name: String,
    val filePath: String,
    val thumbnailPath: String? = null,
    val durationMs: Long = 0L,
    val width: Int = 0,
    val height: Int = 0,
    val frameRate: Float = 0f,
    val fileSizeBytes: Long = 0L,
    val source: ClipSource = ClipSource.GENERATED,
    val modelId: String? = null,
    val prompt: String? = null,
    val motionPrompt: String? = null,
    val sortOrder: Int = 0,
    val createdAt: Long,
    val updatedAt: Long
)

enum class ClipSource {
    GENERATED,
    IMPORTED
}
