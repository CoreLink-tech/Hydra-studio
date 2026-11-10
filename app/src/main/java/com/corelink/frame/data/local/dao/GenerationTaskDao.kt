package com.corelink.frame.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.corelink.frame.data.local.entity.GenerationTaskEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface GenerationTaskDao {
    @Query("SELECT * FROM generation_tasks ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<GenerationTaskEntity>>

    @Query("SELECT * FROM generation_tasks WHERE status IN ('QUEUED', 'RUNNING', 'CANCELLING') ORDER BY createdAt ASC")
    fun observeActive(): Flow<List<GenerationTaskEntity>>

    @Query("SELECT * FROM generation_tasks WHERE id = :id")
    fun observeById(id: String): Flow<GenerationTaskEntity?>

    @Query("SELECT * FROM generation_tasks WHERE id = :id")
    suspend fun getById(id: String): GenerationTaskEntity?

    @Query("SELECT * FROM generation_tasks WHERE status = 'QUEUED' ORDER BY createdAt ASC LIMIT 1")
    suspend fun getNextQueued(): GenerationTaskEntity?

    @Query("SELECT COUNT(*) FROM generation_tasks WHERE status = 'RUNNING'")
    suspend fun countRunning(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(task: GenerationTaskEntity)

    @Update
    suspend fun update(task: GenerationTaskEntity)

    @Query("DELETE FROM generation_tasks WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM generation_tasks WHERE status IN ('SUCCEEDED', 'FAILED', 'CANCELLED') AND createdAt < :before")
    suspend fun deleteFinishedBefore(before: Long)
}
