package com.corelink.frame.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.corelink.frame.data.local.entity.TimelineItemEntity
import com.corelink.frame.data.local.entity.TimelineTrackEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TimelineDao {
    @Query("SELECT * FROM timeline_tracks WHERE projectId = :projectId ORDER BY sortOrder ASC")
    fun observeTracks(projectId: String): Flow<List<TimelineTrackEntity>>

    @Query("SELECT * FROM timeline_tracks WHERE projectId = :projectId ORDER BY sortOrder ASC")
    suspend fun getTracks(projectId: String): List<TimelineTrackEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTrack(track: TimelineTrackEntity)

    @Update
    suspend fun updateTrack(track: TimelineTrackEntity)

    @Query("DELETE FROM timeline_tracks WHERE id = :id")
    suspend fun deleteTrack(id: String)

    @Query("SELECT * FROM timeline_items WHERE projectId = :projectId ORDER BY startMs ASC")
    fun observeItems(projectId: String): Flow<List<TimelineItemEntity>>

    @Query("SELECT * FROM timeline_items WHERE trackId = :trackId ORDER BY startMs ASC")
    suspend fun getItemsForTrack(trackId: String): List<TimelineItemEntity>

    @Query("SELECT * FROM timeline_items WHERE id = :id")
    suspend fun getItem(id: String): TimelineItemEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItem(item: TimelineItemEntity)

    @Update
    suspend fun updateItem(item: TimelineItemEntity)

    @Query("DELETE FROM timeline_items WHERE id = :id")
    suspend fun deleteItem(id: String)

    @Query("SELECT COALESCE(MAX(startMs + durationMs), 0) FROM timeline_items WHERE projectId = :projectId")
    suspend fun projectDurationMs(projectId: String): Long
}
