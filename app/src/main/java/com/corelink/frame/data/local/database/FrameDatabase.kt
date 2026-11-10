package com.corelink.frame.data.local.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.corelink.frame.data.local.dao.ClipDao
import com.corelink.frame.data.local.dao.ProjectDao
import com.corelink.frame.data.local.dao.SceneDao
import com.corelink.frame.data.local.entity.ClipEntity
import com.corelink.frame.data.local.entity.ProjectEntity
import com.corelink.frame.data.local.entity.SceneEntity

@Database(
    entities = [
        ProjectEntity::class,
        SceneEntity::class,
        ClipEntity::class
    ],
    version = 2,
    exportSchema = true
)
abstract class FrameDatabase : RoomDatabase() {
    abstract fun projectDao(): ProjectDao
    abstract fun sceneDao(): SceneDao
    abstract fun clipDao(): ClipDao
}
