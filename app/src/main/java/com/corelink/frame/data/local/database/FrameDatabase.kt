package com.corelink.frame.data.local.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.corelink.frame.data.local.dao.ProjectDao
import com.corelink.frame.data.local.entity.ProjectEntity

@Database(
    entities = [ProjectEntity::class],
    version = 1,
    exportSchema = true
)
abstract class FrameDatabase : RoomDatabase() {
    abstract fun projectDao(): ProjectDao
}
