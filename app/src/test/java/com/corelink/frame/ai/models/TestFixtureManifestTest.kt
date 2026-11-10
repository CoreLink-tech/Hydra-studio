package com.corelink.frame.ai.models

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Ensures the committed test_fixture assets match the catalogue SHA-256 / sizes
 * and that models_manifest.json parses cleanly.
 *
 * Assets live under app/src/main/assets/test_fixture/ and are also readable from
 * the project tree during unit tests.
 */
class TestFixtureManifestTest {

    private fun fixtureDir(): File {
        // Works when tests run from the app module or repo root.
        val candidates = listOf(
            File("src/main/assets/test_fixture"),
            File("app/src/main/assets/test_fixture"),
            File("../app/src/main/assets/test_fixture")
        )
        return candidates.firstOrNull { it.isDirectory }
            ?: error("test_fixture assets not found from ${File(".").absolutePath}")
    }

    @Test
    fun committedFilesMatchManifestSizesAndSha256() {
        val dir = fixtureDir()
        val manifestText = File(dir, "models_manifest.json").readText()
        val manifest = ModelManifestParser.parse(manifestText)
        assertEquals("frame_test_fixture", manifest.modelId)
        assertEquals(1, manifest.version)
        assertTrue(manifest.files.isNotEmpty())

        for (f in manifest.files) {
            val onDisk = File(dir, f.name)
            assertTrue("${f.name} missing", onDisk.isFile)
            if (f.sizeBytes > 0L) {
                assertEquals("${f.name} size", f.sizeBytes, onDisk.length())
            }
            if (f.hasChecksum) {
                val actual = java.security.MessageDigest.getInstance("SHA-256")
                    .digest(onDisk.readBytes())
                    .joinToString("") { "%02x".format(it) }
                assertEquals("${f.name} sha256", f.sha256!!.lowercase(), actual.lowercase())
            }
        }
    }

    @Test
    fun catalogueEntryMatchesFixtureFiles() {
        val meta = ModelCatalogue.byId("frame_test_fixture")
            ?: error("frame_test_fixture missing from catalogue")
        assertTrue(meta.isInstallable)
        assertEquals(2, meta.files.size)
        val dir = fixtureDir()
        for (f in meta.files) {
            val onDisk = File(dir, f.name)
            assertTrue(onDisk.isFile)
            assertEquals(f.sizeBytes, onDisk.length())
            val actual = java.security.MessageDigest.getInstance("SHA-256")
                .digest(onDisk.readBytes())
                .joinToString("") { "%02x".format(it) }
            assertEquals(f.sha256!!.lowercase(), actual.lowercase())
        }
    }
}
