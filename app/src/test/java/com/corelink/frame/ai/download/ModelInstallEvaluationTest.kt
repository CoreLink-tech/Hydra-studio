package com.corelink.frame.ai.download

import com.corelink.frame.ai.models.GenerationMode
import com.corelink.frame.ai.models.InstallState
import com.corelink.frame.ai.models.ModelFile
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
import java.security.MessageDigest

class ModelInstallEvaluationTest {

    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun emptyFilesList_notPresent() {
        val dir = tmp.newFolder("empty_meta")
        val state = ModelInstallEvaluator.evaluate(
            sampleMeta(files = emptyList()),
            dir,
            emptyList(),
            0L
        )
        assertEquals(InstallState.NOT_PRESENT, state.state)
    }

    @Test
    fun emptyFilesList_withOrphanFiles_isCorrupt() {
        val dir = tmp.newFolder("orphan")
        File(dir, "stray.bin").writeBytes(ByteArray(100))
        val state = ModelInstallEvaluator.evaluate(
            sampleMeta(files = emptyList()),
            dir,
            dir.listFiles()!!.toList(),
            100L
        )
        assertEquals(InstallState.CORRUPT, state.state)
        assertTrue(state.validationError!!.contains("empty files[]"))
    }

    @Test
    fun noFilesOnDisk_notPresent_withMissingList() {
        val dir = tmp.newFolder("none")
        val state = ModelInstallEvaluator.evaluate(
            sampleMeta(
                files = listOf(
                    ModelFile("a.onnx", sizeBytes = 10, sha256 = shaOf("a")),
                    ModelFile("b.onnx", sizeBytes = 10, sha256 = shaOf("b"))
                )
            ),
            dir,
            emptyList(),
            0L
        )
        assertEquals(InstallState.NOT_PRESENT, state.state)
        assertEquals(listOf("a.onnx", "b.onnx"), state.missingFiles)
    }

    @Test
    fun oneOfTwoPresent_isPartial() {
        val dir = tmp.newFolder("partial")
        val a = File(dir, "a.onnx").also { it.writeText("aaaa") }
        val state = ModelInstallEvaluator.evaluate(
            sampleMeta(
                files = listOf(
                    ModelFile("a.onnx", sizeBytes = a.length(), sha256 = shaOf("aaaa")),
                    ModelFile("b.onnx", sizeBytes = 4, sha256 = shaOf("bbbb"))
                )
            ),
            dir,
            listOf(a),
            a.length()
        )
        assertEquals(InstallState.PARTIAL, state.state)
        assertEquals(listOf("b.onnx"), state.missingFiles)
        assertTrue(state.validationError!!.contains("Missing"))
    }

    @Test
    fun wrongSize_isCorrupt() {
        val dir = tmp.newFolder("size")
        val f = File(dir, "model.onnx").also { it.writeText("weights") }
        val state = ModelInstallEvaluator.evaluate(
            sampleMeta(
                files = listOf(
                    ModelFile("model.onnx", sizeBytes = 9999L, sha256 = shaOf("weights"))
                )
            ),
            dir,
            listOf(f),
            f.length()
        )
        assertEquals(InstallState.CORRUPT, state.state)
        assertTrue(state.corruptFiles.contains("model.onnx"))
        assertTrue(state.validationError!!.contains("size"))
    }

    @Test
    fun checksumMismatch_isCorrupt() {
        val dir = tmp.newFolder("badhash")
        val f = File(dir, "model.onnx").also { it.writeText("weights") }
        val state = ModelInstallEvaluator.evaluate(
            sampleMeta(
                files = listOf(
                    ModelFile(
                        "model.onnx",
                        sizeBytes = f.length(),
                        sha256 = "0000000000000000000000000000000000000000000000000000000000000000"
                    )
                )
            ),
            dir,
            listOf(f),
            f.length()
        )
        assertEquals(InstallState.CORRUPT, state.state)
        assertTrue(state.validationError!!.contains("SHA-256"))
        assertEquals(listOf("model.onnx"), state.corruptFiles)
    }

    @Test
    fun allPresentWithChecksum_installedVerified() {
        val dir = tmp.newFolder("ok")
        val aContent = "weights-a"
        val bContent = "weights-b"
        val a = File(dir, "a.onnx").also { it.writeText(aContent) }
        val b = File(dir, "b.onnx").also { it.writeText(bContent) }
        val state = ModelInstallEvaluator.evaluate(
            sampleMeta(
                files = listOf(
                    ModelFile("a.onnx", sizeBytes = a.length(), sha256 = shaOf(aContent)),
                    ModelFile("b.onnx", sizeBytes = b.length(), sha256 = shaOf(bContent))
                )
            ),
            dir,
            listOf(a, b),
            a.length() + b.length()
        )
        assertEquals(InstallState.INSTALLED, state.state)
        assertFalse(state.checksumUnverified)
        assertTrue(state.missingFiles.isEmpty())
        assertTrue(state.corruptFiles.isEmpty())
    }

    @Test
    fun presentWithoutChecksum_installedUnverified() {
        val dir = tmp.newFolder("nocheck")
        val f = File(dir, "model.onnx").also { it.writeText("weights") }
        val state = ModelInstallEvaluator.evaluate(
            sampleMeta(
                files = listOf(
                    ModelFile("model.onnx", sizeBytes = f.length(), sha256 = null)
                )
            ),
            dir,
            listOf(f),
            f.length()
        )
        assertEquals(InstallState.INSTALLED, state.state)
        assertTrue(state.checksumUnverified)
        assertTrue(state.validationError!!.contains("no SHA-256"))
    }

    @Test
    fun sizeZeroMeansSkipSizeCheck() {
        val dir = tmp.newFolder("zerosize")
        val f = File(dir, "model.onnx").also { it.writeText("anything") }
        val state = ModelInstallEvaluator.evaluate(
            sampleMeta(
                files = listOf(
                    ModelFile("model.onnx", sizeBytes = 0L, sha256 = shaOf("anything"))
                )
            ),
            dir,
            listOf(f),
            f.length()
        )
        assertEquals(InstallState.INSTALLED, state.state)
        assertFalse(state.checksumUnverified)
    }

    private fun sampleMeta(files: List<ModelFile>) = ModelMetadata(
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
        files = files,
        status = ModelStatus.RESEARCH,
        compatibilityNotes = ""
    )

    private fun shaOf(text: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(text.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }
}
