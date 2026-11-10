package com.corelink.frame.ai.models

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class ModelManifestParserTest {

    @Test
    fun validMinimalManifest_parses() {
        val json = """
            {
              "modelId": "fixture",
              "version": 1,
              "files": [
                { "name": "denoiser.onnx", "sizeBytes": 128, "sha256": "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa", "role": "denoiser" }
              ]
            }
        """.trimIndent()
        val m = ModelManifestParser.parse(json)
        assertEquals("fixture", m.modelId)
        assertEquals(1, m.version)
        assertEquals(1, m.files.size)
        assertEquals("denoiser.onnx", m.files[0].name)
        assertEquals(128L, m.files[0].sizeBytes)
        assertEquals("denoiser", m.files[0].role)
        assertTrue(m.graphs.isEmpty())
    }

    @Test
    fun validWithGraphs_parsesTensors() {
        val json = """
            {
              "modelId": "fixture",
              "version": 1,
              "files": [
                { "name": "g.onnx", "url": "https://example.com/g.onnx", "sizeBytes": 10,
                  "sha256": "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb", "role": "weight" }
              ],
              "graphs": [
                {
                  "file": "g.onnx",
                  "inputs":  [ { "name": "x", "dtype": "float16", "shape": [1, 4, -1, 64] } ],
                  "outputs": [ { "name": "y", "dtype": "float32", "shape": [1, 4, 64, 64] } ]
                }
              ]
            }
        """.trimIndent()
        val m = ModelManifestParser.parse(json)
        assertEquals(1, m.graphs.size)
        val g = m.graphs[0]
        assertEquals("g.onnx", g.file)
        assertEquals("float16", g.inputs[0].dtype)
        assertEquals(listOf(1L, 4L, -1L, 64L), g.inputs[0].shape)
        assertEquals("float32", g.outputs[0].dtype)
    }

    @Test
    fun missingModelId_fails() {
        expectError("modelId") {
            ModelManifestParser.parse("""{"version":1,"files":[{"name":"a.onnx"}]}""")
        }
    }

    @Test
    fun emptyFiles_fails() {
        expectError("files") {
            ModelManifestParser.parse("""{"modelId":"x","version":1,"files":[]}""")
        }
    }

    @Test
    fun badSha256Length_fails() {
        expectError("sha256") {
            ModelManifestParser.parse(
                """{"modelId":"x","version":1,"files":[{"name":"a.onnx","sha256":"abc"}]}"""
            )
        }
    }

    @Test
    fun graphFileNotInFiles_fails() {
        expectError("not listed") {
            ModelManifestParser.parse(
                """
                {
                  "modelId":"x","version":1,
                  "files":[{"name":"a.onnx"}],
                  "graphs":[{"file":"missing.onnx","inputs":[{"name":"x","dtype":"float32","shape":[1]}],
                             "outputs":[{"name":"y","dtype":"float32","shape":[1]}]}]
                }
                """.trimIndent()
            )
        }
    }

    @Test
    fun unsupportedDtype_fails() {
        expectError("dtype") {
            ModelManifestParser.parse(
                """
                {
                  "modelId":"x","version":1,
                  "files":[{"name":"a.onnx"}],
                  "graphs":[{"file":"a.onnx",
                    "inputs":[{"name":"x","dtype":"float64","shape":[1]}],
                    "outputs":[{"name":"y","dtype":"float32","shape":[1]}]}]
                }
                """.trimIndent()
            )
        }
    }

    @Test
    fun invalidJson_fails() {
        expectError("JSON") {
            ModelManifestParser.parse("{not json")
        }
    }

    @Test
    fun optionalUrlCanBeNull() {
        val m = ModelManifestParser.parse(
            """{"modelId":"x","version":1,"files":[{"name":"a.onnx","sizeBytes":0}]}"""
        )
        assertNull(m.files[0].url)
        assertNull(m.files[0].sha256)
        assertFalse(m.files[0].isDownloadable)
    }

    private fun assertFalse(condition: Boolean) {
        assertTrue(!condition)
    }

    private fun expectError(fragment: String, block: () -> Unit) {
        try {
            block()
            fail("Expected ModelManifestException containing \"$fragment\"")
        } catch (e: ModelManifestException) {
            assertTrue(
                "Message \"${e.message}\" should contain \"$fragment\"",
                e.message!!.contains(fragment, ignoreCase = true)
            )
        }
    }
}
