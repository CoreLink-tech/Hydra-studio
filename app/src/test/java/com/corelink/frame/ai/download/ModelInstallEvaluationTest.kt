package com.corelink.frame.ai.download

import com.corelink.frame.ai.models.GenerationMode
import com.corelink.frame.ai.models.InstallState
import com.corelink.frame.ai.models.ModelMetadata
import com.corelink.frame.ai.models.ModelRuntime
import com.corelink.frame.ai.models.ModelStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class ModelInstallEvaluationTest {

    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun missingExpectedFile_isCorrupt() {
        val dir = tmp.newFolder("m1")
        File(dir, "other.bin").writeBytes(ByteArray(2_000_000))
        val state = ModelInstallEvaluator.evaluate(
            sampleMeta(expectedFiles = listOf("model.onnx")),
            dir,
            dir.listFiles()!!.toList(),
            dir.listFiles()!!.sumOf { it.length() }
        )
        assertEquals(InstallState.CORRUPT, state.state)
        assertTrue(state.validationError!!.contains("Missing"))
    }

    @Test
    fun expectedFilePresent_withoutChecksum_installedUnverified() {
        val dir = tmp.newFolder("m2")
        File(dir, "model.onnx").writeText("weights")
        val state = ModelInstallEvaluator.evaluate(
            sampleMeta(expectedFiles = listOf("model.onnx"), checksum = null),
            dir,
            dir.listFiles()!!.toList(),
            7L
        )
        assertEquals(InstallState.INSTALLED, state.state)
        assertTrue(state.checksumUnverified)
    }

    @Test
    fun checksumMismatch_isCorrupt() {
        val dir = tmp.newFolder("m3")
        File(dir, "model.onnx").writeText("weights")
        val state = ModelInstallEvaluator.evaluate(
            sampleMeta(
                expectedFiles = listOf("model.onnx"),
                checksum = "0000000000000000000000000000000000000000000000000000000000000000"
            ),
            dir,
            dir.listFiles()!!.toList(),
            7L
        )
        assertEquals(InstallState.CORRUPT, state.state)
        assertTrue(state.validationError!!.contains("SHA-256"))
    }

    @Test
    fun checksumMatch_installedVerified() {
        val dir = tmp.newFolder("m4")
        val f = File(dir, "model.onnx")
        f.writeText("weights")
        val digest = java.security.MessageDigest.getInstance("SHA-256")
            .digest(f.readBytes())
            .joinToString("") { "%02x".format(it) }
        val state = ModelInstallEvaluator.evaluate(
            sampleMeta(expectedFiles = listOf("model.onnx"), checksum = digest),
            dir,
            listOf(f),
            f.length()
        )
        assertEquals(InstallState.INSTALLED, state.state)
        assertFalse(state.checksumUnverified)
    }

    private fun sampleMeta(
        expectedFiles: List<String>,
        checksum: String? = null
    ) = ModelMetadata(
        id = "test",
        displayName = "Test",
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
        checksumSha256 = checksum,
        expectedFiles = expectedFiles,
        status = ModelStatus.RESEARCH,
        compatibilityNotes = ""
    )
}
