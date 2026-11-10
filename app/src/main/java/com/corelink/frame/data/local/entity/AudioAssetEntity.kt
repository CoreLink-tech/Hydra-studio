package com.corelink.frame.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "audio_assets",
    indices = [Index("projectId")]
)
data class AudioAssetEntity(
    @PrimaryKey val id: String,
    val projectId: String?,
    val name: String,
    val filePath: String,
    val durationMs: Long = 0L,
    val fileSizeBytes: Long = 0L,
    val mimeType: String? = null,
    val createdAt: Long
)
