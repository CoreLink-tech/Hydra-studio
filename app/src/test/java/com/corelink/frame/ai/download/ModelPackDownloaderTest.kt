package com.corelink.frame.ai.download

import com.corelink.frame.ai.models.GenerationMode
import com.corelink.frame.ai.models.ModelFile
import com.corelink.frame.ai.models.ModelMetadata
import com.corelink.frame.ai.models.ModelRuntime
import com.corelink.frame.ai.models.ModelStatus
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.security.MessageDigest

class ModelPackDownloaderTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun downloadsTwoFilesSequentially() {
        val a = "file-a-content".toByteArray()
        val b = "file-b-content-xx".toByteArray()
        server.enqueue(MockResponse().setBody(okio.Buffer().write(a)))
        server.enqueue(MockResponse().setBody(okio.Buffer().write(b)))

        val meta = sampleMeta(
            listOf(
                ModelFile("a.onnx", server.url("/a.onnx").toString(), a.size.toLong(), sha(a), "enc"),
                ModelFile("b.onnx", server.url("/b.onnx").toString(), b.size.toLong(), sha(b), "dec")
            )
        )
        val dir = tmp.newFolder("pack")
        val result = ModelPackDownloader(OkHttpClient()).downloadPack(
            meta, dir, { _, _, _ -> }, { false }
        )
        assertTrue(result is ModelPackDownloader.Result.Success)
        assertEquals(a.size.toLong(), java.io.File(dir, "a.onnx").length())
        assertEquals(b.size.toLong(), java.io.File(dir, "b.onnx").length())
    }

    @Test
    fun emptyFiles_notDownloadable() {
        val meta = sampleMeta(emptyList())
        val result = ModelPackDownloader(OkHttpClient()).downloadPack(
            meta, tmp.newFolder(), { _, _, _ -> }, { false }
        )
        assertEquals(
            DownloadFailure.NOT_DOWNLOADABLE,
            (result as ModelPackDownloader.Result.Failed).reason
        )
    }

    private fun sampleMeta(files: List<ModelFile>) = ModelMetadata(
        id = "pack",
        displayName = "Pack",
        version = "1",
        description = "",
        architecture = "",
        generationModes = setOf(GenerationMode.IMAGE_TO_VIDEO),
        parameterCountApprox = "1",
        storageBytesApprox = 1,
        minRamGbRecommended = 4f,
        supportedResolutions = listOf("512"),
        maxFrames = 16,
        maxDurationSeconds = 3,
        runtime = ModelRuntime.ONNX_MOBILE,
        licence = "test",
        licenceNotes = "",
        sourceUrl = null,
        files = files,
        status = ModelStatus.RESEARCH,
        compatibilityNotes = ""
    )

    private fun sha(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}
