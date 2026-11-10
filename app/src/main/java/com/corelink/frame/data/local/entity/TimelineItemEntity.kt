package com.corelink.frame.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A clip/audio/text placed on the timeline.
 * Source media is never modified — in/out points reference the original asset.
 */
@Entity(
    tableName = "timeline_items",
    foreignKeys = [
        ForeignKey(
            entity = TimelineTrackEntity::class,
            parentColumns = ["id"],
            childColumns = ["trackId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = ProjectEntity::class,
            parentColumns = ["id"],
            childColumns = ["projectId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("trackId"), Index("projectId")]
)
data class TimelineItemEntity(
    @PrimaryKey val id: String,
    val projectId: String,
    val trackId: String,
    val clipId: String? = null,          // link to ClipEntity when video
    val mediaPath: String? = null,       // direct path for imported audio etc.
    val label: String = "",
    val type: String = "VIDEO",          // VIDEO | AUDIO | TEXT
    /** Position on the timeline (ms from project start) */
    val startMs: Long = 0L,
    /** Duration on the timeline (ms) */
    val durationMs: Long = 0L,
    /** In-point within the source media (ms) — non-destructive trim */
    val sourceInMs: Long = 0L,
    /** Out-point within the source media (ms) */
    val sourceOutMs: Long = 0L,
    val textContent: String? = null,     // for TEXT items
    val volume: Float = 1f,
    val sortOrder: Int = 0
)
