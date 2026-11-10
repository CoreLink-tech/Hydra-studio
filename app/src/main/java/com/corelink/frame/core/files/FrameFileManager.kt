package com.corelink.frame.core.files

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Organised local storage for FRAME.
 *
 * FRAME/
 *   Projects/<projectId>/
 *     clips/
 *     audio/
 *     exports/
 *     temporary/
 *   Models/<modelId>/
 *   Cache/
 *   Logs/
 */
@Singleton
class FrameFileManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val root: File by lazy {
        File(context.filesDir, "FRAME").also { it.mkdirs() }
    }

    fun projectsDir(): File = File(root, "Projects").also { it.mkdirs() }

    fun projectDir(projectId: String): File =
        File(projectsDir(), projectId).also { it.mkdirs() }

    fun projectClipsDir(projectId: String): File =
        File(projectDir(projectId), "clips").also { it.mkdirs() }

    fun projectAudioDir(projectId: String): File =
        File(projectDir(projectId), "audio").also { it.mkdirs() }

    fun projectExportsDir(projectId: String): File =
        File(projectDir(projectId), "exports").also { it.mkdirs() }

    fun projectTempDir(projectId: String): File =
        File(projectDir(projectId), "temporary").also { it.mkdirs() }

    fun modelsDir(): File = File(root, "Models").also { it.mkdirs() }

    fun modelDir(modelId: String): File =
        File(modelsDir(), modelId).also { it.mkdirs() }

    fun cacheDir(): File = File(root, "Cache").also { it.mkdirs() }

    fun logsDir(): File = File(root, "Logs").also { it.mkdirs() }

    fun usedBytes(): Long = root.walkTopDown().filter { it.isFile }.map { it.length() }.sum()

    fun clearCache() {
        cacheDir().listFiles()?.forEach { it.deleteRecursively() }
    }

    fun clearProjectTemp(projectId: String) {
        projectTempDir(projectId).listFiles()?.forEach { it.deleteRecursively() }
    }
}
