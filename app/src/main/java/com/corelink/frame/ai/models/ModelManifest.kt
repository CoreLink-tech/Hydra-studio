package com.corelink.frame.ai.models

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/**
 * Parsed models_manifest.json for a model pack.
 *
 * Format (see STEP 1 / docs):
 * {
 *   "modelId": "...",
 *   "version": 1,
 *   "files": [
 *     { "name": "denoiser_fp16.onnx", "url": "https://...", "sizeBytes": 0,
 *       "sha256": "...", "role": "denoiser" }
 *   ],
 *   "graphs": [
 *     { "file": "denoiser_fp16.onnx",
 *       "inputs":  [ { "name": "x", "dtype": "float16", "shape": [1,4,64,64] } ],
 *       "outputs": [ { "name": "y", "dtype": "float16", "shape": [1,4,64,64] } ] }
 *   ]
 * }
 *
 * Uses org.json (Android built-in) so we do not add a serialization dependency
 * for a single config format. Errors name the exact field that failed.
 */
data class ModelManifest(
    val modelId: String,
    val version: Int,
    val files: List<ModelFile>,
    val graphs: List<ManifestGraph>
) {
    fun toModelFiles(): List<ModelFile> = files
}

data class ManifestGraph(
    val file: String,
    val inputs: List<ManifestTensor>,
    val outputs: List<ManifestTensor>
)

data class ManifestTensor(
    val name: String,
    val dtype: String,
    val shape: List<Long>
)

object ModelManifestParser {

    private val SUPPORTED_DTYPES = setOf("float32", "float16", "int64", "int32")

    /**
     * Parse and validate. Throws [ModelManifestException] with a precise message
     * on any structural or semantic error.
     */
    fun parse(jsonText: String): ModelManifest {
        if (jsonText.isBlank()) {
            throw ModelManifestException("Manifest is empty")
        }
        val root: JSONObject = try {
            JSONObject(jsonText)
        } catch (e: JSONException) {
            throw ModelManifestException("Manifest is not valid JSON: ${e.message}")
        }

        val modelId = root.optString("modelId", "").trim()
        if (modelId.isEmpty()) {
            throw ModelManifestException("Missing or empty required field: modelId")
        }

        if (!root.has("version")) {
            throw ModelManifestException("Missing required field: version")
        }
        val version = root.optInt("version", -1)
        if (version < 1) {
            throw ModelManifestException("Field version must be a positive integer, got: $version")
        }

        val filesArray = root.optJSONArray("files")
            ?: throw ModelManifestException("Missing required field: files (array)")
        if (filesArray.length() == 0) {
            throw ModelManifestException("files array must contain at least one entry")
        }

        val files = mutableListOf<ModelFile>()
        val seenNames = mutableSetOf<String>()
        for (i in 0 until filesArray.length()) {
            val obj = filesArray.optJSONObject(i)
                ?: throw ModelManifestException("files[$i] must be an object")
            val name = obj.optString("name", "").trim()
            if (name.isEmpty()) {
                throw ModelManifestException("files[$i].name is missing or empty")
            }
            if (!seenNames.add(name)) {
                throw ModelManifestException("Duplicate file name in files[]: $name")
            }
            val url = obj.optString("url", "").trim().ifEmpty { null }
            val sizeBytes = if (obj.has("sizeBytes")) obj.optLong("sizeBytes", -1L) else 0L
            if (sizeBytes < 0L) {
                throw ModelManifestException("files[$i].sizeBytes must be >= 0 for $name")
            }
            val sha256 = obj.optString("sha256", "").trim().ifEmpty { null }
            if (sha256 != null && !isHexSha256(sha256)) {
                throw ModelManifestException(
                    "files[$i].sha256 for $name must be 64 hex characters, got length ${sha256.length}"
                )
            }
            val role = obj.optString("role", "weight").trim().ifEmpty { "weight" }
            files += ModelFile(
                name = name,
                url = url,
                sizeBytes = sizeBytes,
                sha256 = sha256,
                role = role
            )
        }

        val graphs = mutableListOf<ManifestGraph>()
        val graphsArray = root.optJSONArray("graphs")
        if (graphsArray != null) {
            for (i in 0 until graphsArray.length()) {
                val g = graphsArray.optJSONObject(i)
                    ?: throw ModelManifestException("graphs[$i] must be an object")
                val file = g.optString("file", "").trim()
                if (file.isEmpty()) {
                    throw ModelManifestException("graphs[$i].file is missing or empty")
                }
                if (files.none { it.name == file }) {
                    throw ModelManifestException(
                        "graphs[$i].file \"$file\" is not listed in files[]"
                    )
                }
                val inputs = parseTensors(g.optJSONArray("inputs"), "graphs[$i].inputs")
                val outputs = parseTensors(g.optJSONArray("outputs"), "graphs[$i].outputs")
                if (inputs.isEmpty()) {
                    throw ModelManifestException("graphs[$i].inputs must contain at least one tensor")
                }
                if (outputs.isEmpty()) {
                    throw ModelManifestException("graphs[$i].outputs must contain at least one tensor")
                }
                graphs += ManifestGraph(file = file, inputs = inputs, outputs = outputs)
            }
        }

        return ModelManifest(
            modelId = modelId,
            version = version,
            files = files,
            graphs = graphs
        )
    }

    private fun parseTensors(array: JSONArray?, path: String): List<ManifestTensor> {
        if (array == null) {
            throw ModelManifestException("Missing required field: $path")
        }
        val result = mutableListOf<ManifestTensor>()
        for (i in 0 until array.length()) {
            val t = array.optJSONObject(i)
                ?: throw ModelManifestException("$path[$i] must be an object")
            val name = t.optString("name", "").trim()
            if (name.isEmpty()) {
                throw ModelManifestException("$path[$i].name is missing or empty")
            }
            val dtype = t.optString("dtype", "").trim().lowercase()
            if (dtype !in SUPPORTED_DTYPES) {
                throw ModelManifestException(
                    "$path[$i].dtype \"$dtype\" is not supported " +
                        "(allowed: ${SUPPORTED_DTYPES.joinToString()})"
                )
            }
            val shapeArr = t.optJSONArray("shape")
                ?: throw ModelManifestException("$path[$i].shape is missing (array required)")
            if (shapeArr.length() == 0) {
                throw ModelManifestException("$path[$i].shape must not be empty")
            }
            val shape = mutableListOf<Long>()
            for (s in 0 until shapeArr.length()) {
                val dim = shapeArr.optLong(s, Long.MIN_VALUE)
                // -1 means dynamic; positive or zero are fixed
                if (dim < -1L) {
                    throw ModelManifestException(
                        "$path[$i].shape[$s] must be >= -1 (got $dim)"
                    )
                }
                shape += dim
            }
            result += ManifestTensor(name = name, dtype = dtype, shape = shape)
        }
        return result
    }

    private fun isHexSha256(value: String): Boolean {
        if (value.length != 64) return false
        return value.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }
    }
}

class ModelManifestException(message: String) : Exception(message)
