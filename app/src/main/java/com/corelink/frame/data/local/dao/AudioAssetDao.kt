package com.corelink.frame.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.corelink.frame.data.local.entity.AudioAssetEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AudioAssetDao {
    @Query("SELECT * FROM audio_assets WHERE projectId = :projectId OR projectId IS NULL ORDER BY createdAt DESC")
    fun observeForProject(projectId: String): Flow<List<AudioAssetEntity>>

    @Query("SELECT * FROM audio_assets WHERE id = :id")
    suspend fun getById(id: String): AudioAssetEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(asset: AudioAssetEntity)

    @Query("DELETE FROM audio_assets WHERE id = :id")
    suspend fun deleteById(id: String)
}
