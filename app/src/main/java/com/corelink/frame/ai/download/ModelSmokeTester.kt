package com.corelink.frame.ai.download

import android.app.ActivityManager
import android.content.Context
import android.os.Debug
import android.os.Process
import com.corelink.frame.ai.generation.ResourceMonitor
import com.corelink.frame.ai.models.InstallState
import com.corelink.frame.ai.models.ManifestGraph
import com.corelink.frame.ai.models.ManifestTensor
import com.corelink.frame.ai.models.ModelManifest
import com.corelink.frame.ai.models.ModelMetadata
import com.corelink.frame.ai.runtime.InferenceRuntime
import com.corelink.frame.ai.runtime.TensorBufferConverters
import com.corelink.frame.ai.runtime.TensorData
import com.corelink.frame.ai.runtime.TensorDataType
import com.corelink.frame.ai.runtime.onnx.OnnxInferenceSession
import com.corelink.frame.core.files.FrameFileManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * On-device smoke test for an installed model pack.
 *
 * Loads **one graph at a time** and closes it before the next.
 * Never claims quality or generation validity — only that graphs load and run.
 */
@Singleton
class ModelSmokeTester @Inject constructor(
    @ApplicationContext private val context: Context,
    private val fileManager: FrameFileManager,
    private val downloadManager: ModelDownloadManager,
    private val resourceMonitor: ResourceMonitor,
    private val inferenceRuntime: InferenceRuntime
) {
    data class GraphResult(
        val file: String,
        val passed: Boolean,
        val loadMs: Long,
        val runMs: Long,
        val error: String? = null,
        val stackTrace: String? = null,
        val sessionInputs: Map<String, String> = emptyMap(),
        val sessionOutputs: Map<String, String> = emptyMap(),
        val shapeMismatches: List<String> = emptyList()
    )

    data class SmokeReport(
        val modelId: String,
        val timestampMs: Long,
        val header: String = HEADER,
        val installState: String,
        val passed: Boolean,
        val blockedReason: String? = null,
        val thermalBefore: String,
        val thermalAfter: String,
        val pssKbBefore: Int,
        val pssKbAfter: Int,
        val nativeHeapKbBefore: Long,
        val nativeHeapKbAfter: Long,
        val graphs: List<GraphResult>
    ) {
        fun toJson(): String {
            val root = JSONObject()
            root.put("header", header)
            root.put("modelId", modelId)
            root.put("timestampMs", timestampMs)
            root.put("installState", installState)
            root.put("passed", passed)
            if (blockedReason != null) root.put("blockedReason", blockedReason)
            root.put("thermalBefore", thermalBefore)
            root.put("thermalAfter", thermalAfter)
            root.put("pssKbBefore", pssKbBefore)
            root.put("pssKbAfter", pssKbAfter)
            root.put("nativeHeapKbBefore", nativeHeapKbBefore)
            root.put("nativeHeapKbAfter", nativeHeapKbAfter)
            val arr = JSONArray()
            for (g in graphs) {
                val o = JSONObject()
                o.put("file", g.file)
                o.put("passed", g.passed)
                o.put("loadMs", g.loadMs)
                o.put("runMs", g.runMs)
                if (g.error != null) o.put("error", g.error)
                if (g.stackTrace != null) o.put("stackTrace", g.stackTrace)
                o.put("sessionInputs", JSONObject(g.sessionInputs as Map<*, *>))
                o.put("sessionOutputs", JSONObject(g.sessionOutputs as Map<*, *>))
                if (g.shapeMismatches.isNotEmpty()) {
                    o.put("shapeMismatches", JSONArray(g.shapeMismatches))
                }
                arr.put(o)
            }
            root.put("graphs", arr)
            return root.toString(2)
        }

        fun toShortText(): String = buildString {
            appendLine(header)
            appendLine("modelId=$modelId passed=$passed install=$installState")
            if (blockedReason != null) appendLine("blocked: $blockedReason")
            appendLine("thermal: $thermalBefore → $thermalAfter")
            appendLine("pssKb: $pssKbBefore → $pssKbAfter  nativeHeapKb: $nativeHeapKbBefore → $nativeHeapKbAfter")
            for (g in graphs) {
                appendLine(
                    "  ${g.file}: ${if (g.passed) "PASS" else "FAIL"} load=${g.loadMs}ms run=${g.runMs}ms" +
                        (g.error?.let { " err=$it" } ?: "")
                )
            }
        }

        companion object {
            const val HEADER = "smoke test only: not a quality or generation validation"
        }
    }

    suspend fun run(
        metadata: ModelMetadata,
        manifest: ModelManifest? = null
    ): SmokeReport = withContext(Dispatchers.Default) {
        val now = System.currentTimeMillis()
        downloadManager.refreshLocalState(metadata)
        val install = downloadManager.getState(metadata.id)

        val before = resourceMonitor.snapshot()
        val memBefore = memorySnapshot()

        if (install.state != InstallState.INSTALLED && install.state != InstallState.PARTIAL) {
            return@withContext SmokeReport(
                modelId = metadata.id,
                timestampMs = now,
                installState = install.state.name,
                passed = false,
                blockedReason = "Install state is ${install.state.name}; need at least PARTIAL/INSTALLED with model files",
                thermalBefore = before.thermalStatus.name,
                thermalAfter = before.thermalStatus.name,
                pssKbBefore = memBefore.pssKb,
                pssKbAfter = memBefore.pssKb,
                nativeHeapKbBefore = memBefore.nativeHeapKb,
                nativeHeapKbAfter = memBefore.nativeHeapKb,
                graphs = emptyList()
            )
        }

        if (before.shouldPause) {
            return@withContext SmokeReport(
                modelId = metadata.id,
                timestampMs = now,
                installState = install.state.name,
                passed = false,
                blockedReason = "ResourceMonitor.shouldPause (thermal=${before.thermalStatus})",
                thermalBefore = before.thermalStatus.name,
                thermalAfter = before.thermalStatus.name,
                pssKbBefore = memBefore.pssKb,
                pssKbAfter = memBefore.pssKb,
                nativeHeapKbBefore = memBefore.nativeHeapKb,
                nativeHeapKbAfter = memBefore.nativeHeapKb,
                graphs = emptyList()
            )
        }

        if (!inferenceRuntime.isAvailable()) {
            return@withContext SmokeReport(
                modelId = metadata.id,
                timestampMs = now,
                installState = install.state.name,
                passed = false,
                blockedReason = "Inference runtime not available",
                thermalBefore = before.thermalStatus.name,
                thermalAfter = before.thermalStatus.name,
                pssKbBefore = memBefore.pssKb,
                pssKbAfter = memBefore.pssKb,
                nativeHeapKbBefore = memBefore.nativeHeapKb,
                nativeHeapKbAfter = memBefore.nativeHeapKb,
                graphs = emptyList()
            )
        }

        val dir = fileManager.modelDir(metadata.id)
        val graphPlans: List<Pair<String, ManifestGraph?>> = when {
            manifest != null && manifest.graphs.isNotEmpty() ->
                manifest.graphs.map { it.file to it }
            else -> {
                // Discover .onnx files on disk (skip .part)
                dir.listFiles()
                    ?.filter { it.isFile && it.name.endsWith(".onnx", ignoreCase = true) }
                    ?.sortedBy { it.name }
                    ?.map { it.name to null }
                    ?: emptyList()
            }
        }

        if (graphPlans.isEmpty()) {
            return@withContext SmokeReport(
                modelId = metadata.id,
                timestampMs = now,
                installState = install.state.name,
                passed = false,
                blockedReason = "No ONNX graphs found under ${dir.absolutePath}",
                thermalBefore = before.thermalStatus.name,
                thermalAfter = before.thermalStatus.name,
                pssKbBefore = memBefore.pssKb,
                pssKbAfter = memBefore.pssKb,
                nativeHeapKbBefore = memBefore.nativeHeapKb,
                nativeHeapKbAfter = memBefore.nativeHeapKb,
                graphs = emptyList()
            )
        }

        val results = mutableListOf<GraphResult>()
        for ((fileName, graph) in graphPlans) {
            results += runOneGraph(dir, fileName, graph)
            // Yield a frame of breathing room between graphs
            System.gc()
        }

        val after = resourceMonitor.snapshot()
        val memAfter = memorySnapshot()
        val allPassed = results.isNotEmpty() && results.all { it.passed }

        SmokeReport(
            modelId = metadata.id,
            timestampMs = now,
            installState = install.state.name,
            passed = allPassed,
            blockedReason = null,
            thermalBefore = before.thermalStatus.name,
            thermalAfter = after.thermalStatus.name,
            pssKbBefore = memBefore.pssKb,
            pssKbAfter = memAfter.pssKb,
            nativeHeapKbBefore = memBefore.nativeHeapKb,
            nativeHeapKbAfter = memAfter.nativeHeapKb,
            graphs = results
        )
    }

    private suspend fun runOneGraph(
        dir: File,
        fileName: String,
        graph: ManifestGraph?
    ): GraphResult {
        val path = File(dir, fileName)
        if (!path.isFile) {
            return GraphResult(
                file = fileName,
                passed = false,
                loadMs = 0,
                runMs = 0,
                error = "File not found: ${path.absolutePath}"
            )
        }

        var loadMs = 0L
        var runMs = 0L
        try {
            val t0 = System.nanoTime()
            val session = inferenceRuntime.load(path.absolutePath)
            loadMs = (System.nanoTime() - t0) / 1_000_000L

            try {
                val onnx = session as? OnnxInferenceSession
                val sessionInputs = onnx?.inputTypeNames() ?: session.inputNames.associateWith { "?" }
                val sessionOutputs = onnx?.outputTypeNames() ?: session.outputNames.associateWith { "?" }
                val mismatches = mutableListOf<String>()

                if (graph != null) {
                    for (t in graph.inputs) {
                        if (t.name !in session.inputNames) {
                            mismatches += "manifest input \"${t.name}\" not in session"
                        }
                    }
                    for (t in graph.outputs) {
                        if (t.name !in session.outputNames) {
                            mismatches += "manifest output \"${t.name}\" not in session"
                        }
                    }
                }

                val inputTensors = buildInputs(session, onnx, graph)
                val t1 = System.nanoTime()
                val outputs = session.run(inputTensors)
                runMs = (System.nanoTime() - t1) / 1_000_000L

                if (outputs.isEmpty()) {
                    mismatches += "run() returned no outputs"
                }

                return GraphResult(
                    file = fileName,
                    passed = mismatches.isEmpty(),
                    loadMs = loadMs,
                    runMs = runMs,
                    error = if (mismatches.isEmpty()) null else mismatches.joinToString("; "),
                    sessionInputs = sessionInputs,
                    sessionOutputs = sessionOutputs,
                    shapeMismatches = mismatches
                )
            } finally {
                session.close()
            }
        } catch (t: Throwable) {
            return GraphResult(
                file = fileName,
                passed = false,
                loadMs = loadMs,
                runMs = runMs,
                error = t.message ?: t.javaClass.simpleName,
                stackTrace = t.stackTraceToString().take(2000)
            )
        }
    }

    private fun buildInputs(
        session: com.corelink.frame.ai.runtime.InferenceSession,
        onnx: OnnxInferenceSession?,
        graph: ManifestGraph?
    ): Map<String, TensorData> {
        val out = LinkedHashMap<String, TensorData>()
        if (graph != null) {
            for (t in graph.inputs) {
                val shape = t.shape.map { if (it < 0) 1L else it }.toLongArray()
                val type = dtypeFromManifest(t.dtype)
                out[t.name] = TensorBufferConverters.zeros(shape, type)
            }
            return out
        }
        // Discover from session shapes
        val shapes = onnx?.inputShapes() ?: emptyMap()
        val types = onnx?.inputTypeNames() ?: emptyMap()
        for (name in session.inputNames) {
            val rawShape = shapes[name] ?: longArrayOf(1)
            val shape = rawShape.map { if (it < 0) 1L else it }.toLongArray()
            if (shape.isEmpty()) continue
            val type = ortTypeToTensor(types[name])
            out[name] = TensorBufferConverters.zeros(shape, type)
        }
        return out
    }

    private fun dtypeFromManifest(dtype: String): TensorDataType = when (dtype.lowercase()) {
        "float32" -> TensorDataType.FLOAT32
        "float16" -> TensorDataType.FLOAT16
        "int64" -> TensorDataType.INT64
        "int32" -> TensorDataType.INT32
        else -> TensorDataType.FLOAT32
    }

    private fun ortTypeToTensor(name: String?): TensorDataType = when (name?.uppercase()) {
        "FLOAT" -> TensorDataType.FLOAT32
        "FLOAT16" -> TensorDataType.FLOAT16
        "INT64" -> TensorDataType.INT64
        "INT32" -> TensorDataType.INT32
        else -> TensorDataType.FLOAT32
    }

    private data class Mem(val pssKb: Int, val nativeHeapKb: Long)

    private fun memorySnapshot(): Mem {
        val pss = try {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val info = ActivityManager.MemoryInfo()
            am.getMemoryInfo(info)
            val memInfo = Debug.MemoryInfo()
            Debug.getMemoryInfo(memInfo)
            memInfo.totalPss
        } catch (_: Exception) {
            0
        }
        val native = Debug.getNativeHeapAllocatedSize() / 1024L
        return Mem(pssKb = pss, nativeHeapKb = native)
    }
}
