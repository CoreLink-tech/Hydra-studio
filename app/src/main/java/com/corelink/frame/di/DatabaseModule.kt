package com.corelink.frame.di

import android.content.Context
import androidx.room.Room
import com.corelink.frame.data.local.dao.ClipDao
import com.corelink.frame.data.local.dao.ProjectDao
import com.corelink.frame.data.local.dao.SceneDao
import com.corelink.frame.data.local.database.FrameDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): FrameDatabase {
        return Room.databaseBuilder(
            context,
            FrameDatabase::class.java,
            "frame.db"
        )
            .fallbackToDestructiveMigration() // Milestone 1–3 only; proper migrations later
            .build()
    }

    @Provides
    fun provideProjectDao(db: FrameDatabase): ProjectDao = db.projectDao()

    @Provides
    fun provideSceneDao(db: FrameDatabase): SceneDao = db.sceneDao()

    @Provides
    fun provideClipDao(db: FrameDatabase): ClipDao = db.clipDao()
}
