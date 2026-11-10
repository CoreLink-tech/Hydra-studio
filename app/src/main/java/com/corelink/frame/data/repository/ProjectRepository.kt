package com.corelink.frame.data.repository

import com.corelink.frame.core.files.FrameFileManager
import com.corelink.frame.data.local.dao.ClipDao
import com.corelink.frame.data.local.dao.ProjectDao
import com.corelink.frame.data.local.dao.SceneDao
import com.corelink.frame.data.local.entity.ProjectEntity
import com.corelink.frame.data.local.entity.SceneEntity
import kotlinx.coroutines.flow.Flow
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProjectRepository @Inject constructor(
    private val projectDao: ProjectDao,
    private val sceneDao: SceneDao,
    private val clipDao: ClipDao,
    private val fileManager: FrameFileManager
) {
    fun observeProjects(): Flow<List<ProjectEntity>> = projectDao.observeAll()

    suspend fun getProject(id: String): ProjectEntity? = projectDao.getById(id)

    suspend fun createProject(name: String, description: String = ""): ProjectEntity {
        val now = System.currentTimeMillis()
        val project = ProjectEntity(
            id = UUID.randomUUID().toString(),
            name = name.trim().ifEmpty { "Untitled Project" },
            description = description,
            createdAt = now,
            updatedAt = now
        )
        projectDao.insert(project)
        // Ensure filesystem structure exists
        fileManager.projectDir(project.id)
        // Default first scene
        val scene = SceneEntity(
            id = UUID.randomUUID().toString(),
            projectId = project.id,
            name = "Scene 1",
            sortOrder = 0,
            createdAt = now,
            updatedAt = now
        )
        sceneDao.insert(scene)
        return project
    }

    suspend fun renameProject(id: String, newName: String) {
        val existing = projectDao.getById(id) ?: return
        projectDao.update(
            existing.copy(
                name = newName.trim().ifEmpty { existing.name },
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun deleteProject(id: String) {
        projectDao.deleteById(id)
        // Filesystem cleanup
        fileManager.projectDir(id).deleteRecursively()
    }

    fun observeScenes(projectId: String): Flow<List<SceneEntity>> =
        sceneDao.observeByProject(projectId)

    suspend fun createScene(projectId: String, name: String): SceneEntity {
        val now = System.currentTimeMillis()
        val count = sceneDao.countByProject(projectId)
        val scene = SceneEntity(
            id = UUID.randomUUID().toString(),
            projectId = projectId,
            name = name.trim().ifEmpty { "Scene ${count + 1}" },
            sortOrder = count,
            createdAt = now,
            updatedAt = now
        )
        sceneDao.insert(scene)
        touchProject(projectId)
        return scene
    }

    private suspend fun touchProject(projectId: String) {
        val p = projectDao.getById(projectId) ?: return
        projectDao.update(p.copy(updatedAt = System.currentTimeMillis()))
    }
}
