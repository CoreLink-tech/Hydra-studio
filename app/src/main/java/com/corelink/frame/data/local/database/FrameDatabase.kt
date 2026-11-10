package com.corelink.frame.data.local.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.corelink.frame.data.local.dao.AudioAssetDao
import com.corelink.frame.data.local.dao.ClipDao
import com.corelink.frame.data.local.dao.GenerationTaskDao
import com.corelink.frame.data.local.dao.ProjectDao
import com.corelink.frame.data.local.dao.SceneDao
import com.corelink.frame.data.local.dao.TimelineDao
import com.corelink.frame.data.local.entity.AudioAssetEntity
import com.corelink.frame.data.local.entity.ClipEntity
import com.corelink.frame.data.local.entity.GenerationTaskEntity
import com.corelink.frame.data.local.entity.ProjectEntity
import com.corelink.frame.data.local.entity.SceneEntity
import com.corelink.frame.data.local.entity.TimelineItemEntity
import com.corelink.frame.data.local.entity.TimelineTrackEntity

@Database(
    entities = [
        ProjectEntity::class,
        SceneEntity::class,
        ClipEntity::class,
        GenerationTaskEntity::class,
        TimelineTrackEntity::class,
        TimelineItemEntity::class,
        AudioAssetEntity::class
    ],
    version = 5,
    exportSchema = true
)
abstract class FrameDatabase : RoomDatabase() {
    abstract fun projectDao(): ProjectDao
    abstract fun sceneDao(): SceneDao
    abstract fun clipDao(): ClipDao
    abstract fun generationTaskDao(): GenerationTaskDao
    abstract fun timelineDao(): TimelineDao
    abstract fun audioAssetDao(): AudioAssetDao
}
