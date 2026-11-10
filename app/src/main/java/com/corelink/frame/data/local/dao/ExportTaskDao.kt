package com.corelink.frame.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.corelink.frame.data.local.entity.ExportTaskEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ExportTaskDao {
    @Query("SELECT * FROM export_tasks WHERE projectId = :projectId ORDER BY createdAt DESC")
    fun observeByProject(projectId: String): Flow<List<ExportTaskEntity>>

    @Query("SELECT * FROM export_tasks WHERE id = :id")
    fun observeById(id: String): Flow<ExportTaskEntity?>

    @Query("SELECT * FROM export_tasks WHERE id = :id")
    suspend fun getById(id: String): ExportTaskEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(task: ExportTaskEntity)

    @Update
    suspend fun update(task: ExportTaskEntity)

    @Query("DELETE FROM export_tasks WHERE id = :id")
    suspend fun deleteById(id: String)
}
