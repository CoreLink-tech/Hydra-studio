package com.corelink.frame.core.startup

import com.corelink.frame.data.local.dao.ExportTaskDao
import com.corelink.frame.data.local.dao.GenerationTaskDao
import com.corelink.frame.data.local.entity.ExportStatus
import com.corelink.frame.data.local.entity.ExportTaskEntity
import com.corelink.frame.data.local.entity.GenerationTaskEntity
import com.corelink.frame.data.local.entity.TaskStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Proves that after recovery, a stale RUNNING generation task no longer blocks
 * the next QUEUED task (countRunning becomes 0 and getNextQueued returns work).
 */
class StaleTaskRecoveryTest {

    @Test
    fun recoverInterruptedTasks_marksRunningGenerationAsFailed_andUnblocksQueue() = runBlocking {
        val generationDao = FakeGenerationTaskDao()
        val exportDao = FakeExportTaskDao()

        val running = GenerationTaskEntity(
            id = "running-1",
            modelId = "mobilei2v_270m",
            mode = "IMAGE_TO_VIDEO",
            status = TaskStatus.RUNNING.name,
            stage = "inference",
            createdAt = 1_000L,
            startedAt = 1_100L
        )
        val queued = GenerationTaskEntity(
            id = "queued-1",
            modelId = "mobilei2v_270m",
            mode = "IMAGE_TO_VIDEO",
            status = TaskStatus.QUEUED.name,
            stage = "queued",
            createdAt = 2_000L
        )
        generationDao.insert(running)
        generationDao.insert(queued)

        assertEquals(1, generationDao.countRunning())
        // While a RUNNING row exists, queue logic would refuse to start the next job.
        assertNotNull(generationDao.getNextQueued())

        val recovery = StaleTaskRecovery(generationDao, exportDao)
        val result = recovery.recoverInterruptedTasks(nowMs = 9_000L)

        assertEquals(1, result.generationTasksFailed)
        assertEquals(0, result.exportTasksFailed)
        assertEquals(0, generationDao.countRunning())

        val failed = generationDao.getById("running-1")!!
        assertEquals(TaskStatus.FAILED.name, failed.status)
        assertEquals("UNKNOWN", failed.errorReason)
        assertEquals(StaleTaskRecovery.INTERRUPTED_MESSAGE, failed.errorMessage)
        assertEquals(9_000L, failed.finishedAt)

        val next = generationDao.getNextQueued()
        assertNotNull(next)
        assertEquals("queued-1", next!!.id)
    }

    @Test
    fun recoverInterruptedTasks_marksCancellingGenerationAndRunningExport() = runBlocking {
        val generationDao = FakeGenerationTaskDao()
        val exportDao = FakeExportTaskDao()

        generationDao.insert(
            GenerationTaskEntity(
                id = "cancel-1",
                modelId = "m",
                mode = "IMAGE_TO_VIDEO",
                status = TaskStatus.CANCELLING.name,
                createdAt = 1L
            )
        )
        exportDao.insert(
            ExportTaskEntity(
                id = "exp-1",
                projectId = "p1",
                outputFileName = "out.mp4",
                status = ExportStatus.RUNNING.name,
                createdAt = 1L,
                startedAt = 2L
            )
        )

        val recovery = StaleTaskRecovery(generationDao, exportDao)
        val result = recovery.recoverInterruptedTasks(nowMs = 5_000L)

        assertEquals(1, result.generationTasksFailed)
        assertEquals(1, result.exportTasksFailed)
        assertEquals(TaskStatus.FAILED.name, generationDao.getById("cancel-1")!!.status)
        assertEquals(ExportStatus.FAILED.name, exportDao.getById("exp-1")!!.status)
        assertEquals(StaleTaskRecovery.INTERRUPTED_MESSAGE, exportDao.getById("exp-1")!!.errorMessage)
    }

    @Test
    fun recoverInterruptedTasks_leavesQueuedAndSucceededUntouched() = runBlocking {
        val generationDao = FakeGenerationTaskDao()
        val exportDao = FakeExportTaskDao()

        generationDao.insert(
            GenerationTaskEntity(
                id = "q",
                modelId = "m",
                mode = "IMAGE_TO_VIDEO",
                status = TaskStatus.QUEUED.name,
                createdAt = 1L
            )
        )
        generationDao.insert(
            GenerationTaskEntity(
                id = "ok",
                modelId = "m",
                mode = "IMAGE_TO_VIDEO",
                status = TaskStatus.SUCCEEDED.name,
                createdAt = 2L,
                finishedAt = 3L
            )
        )

        val recovery = StaleTaskRecovery(generationDao, exportDao)
        val result = recovery.recoverInterruptedTasks()

        assertEquals(0, result.generationTasksFailed)
        assertEquals(TaskStatus.QUEUED.name, generationDao.getById("q")!!.status)
        assertEquals(TaskStatus.SUCCEEDED.name, generationDao.getById("ok")!!.status)
        assertNull(generationDao.getById("ok")!!.errorMessage)
    }
}

private class FakeGenerationTaskDao : GenerationTaskDao {
    private val tasks = linkedMapOf<String, GenerationTaskEntity>()

    override fun observeAll(): Flow<List<GenerationTaskEntity>> =
        MutableStateFlow(tasks.values.toList())

    override fun observeActive(): Flow<List<GenerationTaskEntity>> =
        MutableStateFlow(tasks.values.filter {
            it.status in listOf("QUEUED", "RUNNING", "CANCELLING")
        })

    override fun observeById(id: String): Flow<GenerationTaskEntity?> =
        MutableStateFlow(tasks[id])

    override suspend fun getById(id: String): GenerationTaskEntity? = tasks[id]

    override suspend fun getNextQueued(): GenerationTaskEntity? =
        tasks.values.filter { it.status == TaskStatus.QUEUED.name }.minByOrNull { it.createdAt }

    override suspend fun countRunning(): Int =
        tasks.values.count { it.status == TaskStatus.RUNNING.name }

    override suspend fun failInterruptedTasks(message: String, finishedAt: Long): Int {
        var count = 0
        val ids = tasks.filter { (_, t) ->
            t.status == TaskStatus.RUNNING.name || t.status == TaskStatus.CANCELLING.name
        }.keys.toList()
        for (id in ids) {
            val t = tasks[id]!!
            tasks[id] = t.copy(
                status = TaskStatus.FAILED.name,
                stage = "failed",
                stageDetail = message,
                errorReason = "UNKNOWN",
                errorMessage = message,
                finishedAt = finishedAt,
                progressPercent = null
            )
            count++
        }
        return count
    }

    override suspend fun insert(task: GenerationTaskEntity) {
        tasks[task.id] = task
    }

    override suspend fun update(task: GenerationTaskEntity) {
        tasks[task.id] = task
    }

    override suspend fun deleteById(id: String) {
        tasks.remove(id)
    }

    override suspend fun deleteFinishedBefore(before: Long) {
        tasks.entries.removeIf {
            it.value.status in listOf("SUCCEEDED", "FAILED", "CANCELLED") &&
                it.value.createdAt < before
        }
    }
}

private class FakeExportTaskDao : ExportTaskDao {
    private val tasks = linkedMapOf<String, ExportTaskEntity>()

    override fun observeByProject(projectId: String): Flow<List<ExportTaskEntity>> =
        MutableStateFlow(tasks.values.filter { it.projectId == projectId })

    override fun observeById(id: String): Flow<ExportTaskEntity?> =
        MutableStateFlow(tasks[id])

    override suspend fun getById(id: String): ExportTaskEntity? = tasks[id]

    override suspend fun failInterruptedTasks(message: String, finishedAt: Long): Int {
        var count = 0
        val ids = tasks.filter { it.value.status == ExportStatus.RUNNING.name }.keys.toList()
        for (id in ids) {
            val t = tasks[id]!!
            tasks[id] = t.copy(
                status = ExportStatus.FAILED.name,
                stage = "failed",
                errorMessage = message,
                finishedAt = finishedAt,
                progressPercent = null
            )
            count++
        }
        return count
    }

    override suspend fun insert(task: ExportTaskEntity) {
        tasks[task.id] = task
    }

    override suspend fun update(task: ExportTaskEntity) {
        tasks[task.id] = task
    }

    override suspend fun deleteById(id: String) {
        tasks.remove(id)
    }
}
