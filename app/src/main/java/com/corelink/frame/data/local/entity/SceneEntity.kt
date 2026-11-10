package com.corelink.frame.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "scenes",
    foreignKeys = [
        ForeignKey(
            entity = ProjectEntity::class,
            parentColumns = ["id"],
            childColumns = ["projectId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("projectId")]
)
data class SceneEntity(
    @PrimaryKey val id: String,
    val projectId: String,
    val name: String,
    val description: String = "",
    val sortOrder: Int = 0,
    val createdAt: Long,
    val updatedAt: Long,
    val clipCount: Int = 0,
    val durationMs: Long = 0L
)
