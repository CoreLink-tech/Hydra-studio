package com.corelink.frame.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "timeline_tracks",
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
data class TimelineTrackEntity(
    @PrimaryKey val id: String,
    val projectId: String,
    val name: String,
    val type: String, // VIDEO | AUDIO | TEXT
    val sortOrder: Int = 0,
    val isLocked: Boolean = false,
    val isMuted: Boolean = false
)

enum class TrackType {
    VIDEO,
    AUDIO,
    TEXT
}
