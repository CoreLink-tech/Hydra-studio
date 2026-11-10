package com.corelink.frame.ai.download

import com.corelink.frame.ai.models.ModelFile
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.security.MessageDigest

class HttpModelFetcherTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var server: MockWebServer
    private val body = ByteArray(10_000) { (it % 251).toByte() }
    private val sha: String by lazy {
        MessageDigest.getInstance("SHA-256").digest(body).joinToString("") { "%02x".format(it) }
    }

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun modelFile(size: Long = body.size.toLong()) = ModelFile(
        name = "m.onnx",
        url = server.url("/m.onnx").toString(),
        sizeBytes = size,
        sha256 = sha,
        role = "weight"
    )

    private fun fetcher() = HttpModelFetcher(OkHttpClient())

    @Test
    fun downloadsAndVerifies() {
        server.enqueue(MockResponse().setBody(okio.Buffer().write(body)))
        val dir = tmp.newFolder()
        val result = fetcher().fetch(
            modelFile(),
            File(dir, "m.part"),
            File(dir, "m.onnx"),
            { _, _ -> },
            { false }
        )
        assertTrue(result is HttpModelFetcher.Result.Installed)
        assertEquals(body.size.toLong(), File(dir, "m.onnx").length())
        assertFalse(File(dir, "m.part").exists())
    }

    @Test
    fun resumesFromPartialWithRange() {
        val dir = tmp.newFolder()
        val part = File(dir, "m.part").apply { writeBytes(body.copyOfRange(0, 4_000)) }
        server.enqueue(
            MockResponse().setResponseCode(206)
                .setHeader("Content-Range", "bytes 4000-9999/10000")
                .setBody(okio.Buffer().write(body.copyOfRange(4_000, body.size)))
        )
        val result = fetcher().fetch(
            modelFile(),
            part,
            File(dir, "m.onnx"),
            { _, _ -> },
            { false }
        )
        assertTrue(result is HttpModelFetcher.Result.Installed)
        assertEquals("bytes=4000-", server.takeRequest().getHeader("Range"))
    }

    @Test
    fun checksumMismatchFailsAndRemovesPart() {
        server.enqueue(MockResponse().setBody(okio.Buffer().write(ByteArray(body.size))))
        val dir = tmp.newFolder()
        val part = File(dir, "m.part")
        val result = fetcher().fetch(
            modelFile(),
            part,
            File(dir, "m.onnx"),
            { _, _ -> },
            { false }
        )
        assertEquals(
            DownloadFailure.CHECKSUM_MISMATCH,
            (result as HttpModelFetcher.Result.Failed).reason
        )
        assertFalse(part.exists())
    }

    @Test
    fun httpErrorReported() {
        server.enqueue(MockResponse().setResponseCode(500))
        val dir = tmp.newFolder()
        val result = fetcher().fetch(
            modelFile(),
            File(dir, "m.part"),
            File(dir, "m.onnx"),
            { _, _ -> },
            { false }
        )
        assertEquals(DownloadFailure.HTTP_ERROR, (result as HttpModelFetcher.Result.Failed).reason)
    }

    @Test
    fun missingUrl_notDownloadable() {
        val dir = tmp.newFolder()
        val file = ModelFile("x.onnx", url = null, sizeBytes = 10, sha256 = sha)
        val result = fetcher().fetch(file, File(dir, "x.part"), File(dir, "x.onnx"), { _, _ -> }, { false })
        assertEquals(DownloadFailure.NOT_DOWNLOADABLE, (result as HttpModelFetcher.Result.Failed).reason)
    }
}
