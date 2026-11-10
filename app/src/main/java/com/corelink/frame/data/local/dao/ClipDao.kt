package com.corelink.frame.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.corelink.frame.data.local.entity.ClipEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ClipDao {
    @Query("SELECT * FROM clips WHERE projectId = :projectId ORDER BY sortOrder ASC, createdAt DESC")
    fun observeByProject(projectId: String): Flow<List<ClipEntity>>

    @Query("SELECT * FROM clips WHERE sceneId = :sceneId ORDER BY sortOrder ASC")
    fun observeByScene(sceneId: String): Flow<List<ClipEntity>>

    @Query("SELECT * FROM clips ORDER BY createdAt DESC LIMIT :limit")
    fun observeRecent(limit: Int = 20): Flow<List<ClipEntity>>

    @Query("SELECT * FROM clips WHERE id = :id")
    suspend fun getById(id: String): ClipEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(clip: ClipEntity)

    @Update
    suspend fun update(clip: ClipEntity)

    @Query("DELETE FROM clips WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("SELECT COUNT(*) FROM clips WHERE projectId = :projectId")
    suspend fun countByProject(projectId: String): Int
}
