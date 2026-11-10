package com.corelink.frame.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "projects")
data class ProjectEntity(
    @PrimaryKey val id: String,
    val name: String,
    val description: String = "",
    val createdAt: Long,
    val updatedAt: Long,
    val thumbnailPath: String? = null,
    val sceneCount: Int = 0,
    val clipCount: Int = 0,
    val durationMs: Long = 0L
)
