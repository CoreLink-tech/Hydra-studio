package com.corelink.frame.ui.screens.models

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.corelink.frame.ai.download.DownloadEvent
import com.corelink.frame.ai.download.DownloadFailure
import com.corelink.frame.ai.download.Integrity
import com.corelink.frame.ai.download.ModelDownloadManager
import com.corelink.frame.ai.download.ModelSmokeTester
import com.corelink.frame.ai.models.InstallState
import com.corelink.frame.ai.models.ModelCatalogue
import com.corelink.frame.ai.models.ModelInstallState
import com.corelink.frame.ai.models.ModelManifest
import com.corelink.frame.ai.models.ModelManifestException
import com.corelink.frame.ai.models.ModelManifestParser
import com.corelink.frame.ai.models.ModelMetadata
import com.corelink.frame.ai.models.ModelStatus
import com.corelink.frame.core.files.FrameFileManager
import com.corelink.frame.data.preferences.HardwarePreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject

data class ModelRow(
    val meta: ModelMetadata,
    val install: ModelInstallState,
    val recommended: Boolean,
    val warning: String?,
    val progressFile: String? = null,
    val progressDone: Long = 0L,
    val progressTotal: Long = 0L
)

data class ModelsUiState(
    val rows: List<ModelRow> = emptyList(),
    val storageUsedBytes: Long = 0L,
    val profileName: String? = null,
    val message: String? = null,
    val wifiOnly: Boolean = true,
    /** When non-null, UI should ask the user to confirm a large download. */
    val pendingLargeConfirmId: String? = null,
    val pendingLargeConfirmBytes: Long = 0L,
    val smokeRunning: Boolean = false,
    val lastSmokeReport: ModelSmokeTester.SmokeReport? = null,
    val canMarkExperimental: Boolean = false
)

@HiltViewModel
class ModelsViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val downloadManager: ModelDownloadManager,
    private val fileManager: FrameFileManager,
    private val smokeTester: ModelSmokeTester,
    hardwarePreferences: HardwarePreferences
) : ViewModel() {

    private val refresh = MutableStateFlow(0)
    private val message = MutableStateFlow<String?>(null)
    private val progress = MutableStateFlow<Map<String, Triple<String, Long, Long>>>(emptyMap())
    private val pendingLarge = MutableStateFlow<Pair<String, Long>?>(null)
    private val smokeRunning = MutableStateFlow(false)
    private val lastSmoke = MutableStateFlow<ModelSmokeTester.SmokeReport?>(null)

    private data class AuxState(
        val message: String?,
        val progress: Map<String, Triple<String, Long, Long>>,
        val pending: Pair<String, Long>?
    )

    private data class SmokeAux(
        val running: Boolean,
        val report: ModelSmokeTester.SmokeReport?
    )

    private val aux = combine(message, progress, pendingLarge) { msg, prog, pending ->
        AuxState(msg, prog, pending)
    }
    private val smokeAux = combine(smokeRunning, lastSmoke) { running, report ->
        SmokeAux(running, report)
    }

    val uiState: StateFlow<ModelsUiState> = combine(
        downloadManager.installStates,
        hardwarePreferences.lastProfile,
        refresh,
        aux,
        smokeAux
    ) { installs, profile, _, auxState, smoke ->
        val ram = profile?.minRamGb?.takeIf { it > 0f } ?: 4f
        val rows = ModelCatalogue.all.map { meta ->
            val install = installs[meta.id] ?: ModelInstallState(meta.id, InstallState.NOT_PRESENT)
            val recommended = meta.minRamGbRecommended <= ram + 0.3f &&
                meta.status != ModelStatus.INCOMPATIBLE
            val warning = when {
                !meta.isInstallable -> "Not installable yet — no file list in catalogue"
                meta.status == ModelStatus.INCOMPATIBLE -> "Marked incompatible"
                meta.minRamGbRecommended > ram + 0.5f ->
                    "Needs ~${meta.minRamGbRecommended} GB RAM (device ~${"%.1f".format(ram)} GB)"
                meta.status == ModelStatus.RESEARCH ->
                    "Research only — not validated on device"
                meta.status == ModelStatus.EXPERIMENTAL ->
                    "Experimental — limited device testing"
                else -> null
            }
            val p = auxState.progress[meta.id]
            ModelRow(
                meta = meta,
                install = install,
                recommended = recommended,
                warning = warning,
                progressFile = p?.first,
                progressDone = p?.second ?: 0L,
                progressTotal = p?.third ?: 0L
            )
        }
        ModelsUiState(
            rows = rows,
            storageUsedBytes = fileManager.usedBytes(),
            profileName = profile?.displayName,
            message = auxState.message,
            wifiOnly = downloadManager.wifiOnly,
            pendingLargeConfirmId = auxState.pending?.first,
            pendingLargeConfirmBytes = auxState.pending?.second ?: 0L,
            smokeRunning = smoke.running,
            lastSmokeReport = smoke.report,
            canMarkExperimental = smoke.report?.passed == true
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ModelsUiState())

    init {
        refreshLocal()
        viewModelScope.launch {
            downloadManager.downloadEvents.collect { event ->
                when (event) {
                    is DownloadEvent.Progress -> {
                        progress.update {
                            it + (event.modelId to Triple(event.fileName, event.done, event.total))
                        }
                    }
                    is DownloadEvent.FileInstalled -> {
                        message.value = "Installed ${event.fileName}"
                    }
                    is DownloadEvent.PackInstalled -> {
                        progress.update { it - event.modelId }
                        message.value = "Pack installed: ${event.modelId}"
                        ModelCatalogue.byId(event.modelId)?.let { downloadManager.refreshLocalState(it) }
                        refresh.update { it + 1 }
                    }
                    is DownloadEvent.Failed -> {
                        progress.update { it - event.modelId }
                        message.value = "Download failed (${event.reason}): ${event.message}"
                        ModelCatalogue.byId(event.modelId)?.let { downloadManager.refreshLocalState(it) }
                        refresh.update { it + 1 }
                    }
                }
            }
        }
    }

    fun refreshLocal() {
        viewModelScope.launch {
            ModelCatalogue.all.forEach { downloadManager.refreshLocalState(it) }
            refresh.update { it + 1 }
        }
    }

    fun setWifiOnly(enabled: Boolean) {
        downloadManager.wifiOnly = enabled
        refresh.update { it + 1 }
        message.value = if (enabled) "Wi‑Fi only downloads ON" else "Wi‑Fi only OFF (mobile data allowed)"
    }

    fun clearMessage() {
        message.value = null
    }

    fun requestDownload(modelId: String, userConfirmedLarge: Boolean = false) {
        viewModelScope.launch {
            val meta = ModelCatalogue.byId(modelId) ?: return@launch
            if (!meta.isDownloadable) {
                message.value = "Not downloadable — missing URL or SHA-256 on one or more files"
                return@launch
            }
            val reason = downloadManager.enqueueDownload(
                metadata = meta,
                userConfirmedLarge = userConfirmedLarge,
                userAllowsCellular = !downloadManager.wifiOnly
            )
            when (reason) {
                null -> {
                    pendingLarge.value = null
                    message.value = "Download queued for ${meta.displayName}"
                    refresh.update { it + 1 }
                }
                DownloadFailure.STORAGE_BLOCKED -> {
                    if (meta.declaredTotalBytes >= ModelDownloadManager.LARGE_PACK_CONFIRM_BYTES &&
                        !userConfirmedLarge
                    ) {
                        pendingLarge.value = meta.id to meta.declaredTotalBytes
                        message.value =
                            "This pack is ${formatBytes(meta.declaredTotalBytes)}. Confirm to download."
                    } else {
                        message.value = "Not enough free storage (need ~1 GB margin after install)"
                    }
                }
                DownloadFailure.NOT_DOWNLOADABLE ->
                    message.value = "Model is not downloadable yet"
                else -> message.value = "Cannot start download: $reason"
            }
        }
    }

    fun confirmLargeDownload() {
        val id = pendingLarge.value?.first ?: return
        pendingLarge.value = null
        requestDownload(id, userConfirmedLarge = true)
    }

    fun dismissLargeConfirm() {
        pendingLarge.value = null
    }

    fun cancelDownload(modelId: String) {
        downloadManager.cancel(modelId)
        message.value = "Download cancelled (partial files kept for resume)"
        refreshLocal()
    }

    /**
     * Copy user-picked files into FRAME/Models/<modelId>/ and verify against catalogue files[].
     */
    fun importFiles(modelId: String, uris: List<Uri>) {
        viewModelScope.launch {
            val meta = ModelCatalogue.byId(modelId)
            if (meta == null) {
                message.value = "Unknown model id"
                return@launch
            }
            if (uris.isEmpty()) {
                message.value = "No files selected"
                return@launch
            }
            val dir = fileManager.modelDir(modelId)
            val errors = mutableListOf<String>()
            withContext(Dispatchers.IO) {
                for (uri in uris) {
                    val name = queryDisplayName(uri) ?: uri.lastPathSegment?.substringAfterLast('/') ?: "import.bin"
                    try {
                        appContext.contentResolver.openInputStream(uri)?.use { input ->
                            val dest = File(dir, name)
                            FileOutputStream(dest).use { output -> input.copyTo(output) }
                        } ?: run {
                            errors += "$name: could not open"
                            return@withContext
                        }
                        val expected = meta.files.find { it.name == name }
                        if (expected != null) {
                            val onDisk = File(dir, name)
                            if (expected.sizeBytes > 0L && onDisk.length() != expected.sizeBytes) {
                                errors += "$name: size ${onDisk.length()} != ${expected.sizeBytes}"
                            }
                            if (expected.hasChecksum &&
                                !Integrity.matches(Integrity.sha256(onDisk), expected.sha256!!)
                            ) {
                                errors += "$name: SHA-256 mismatch"
                                onDisk.delete()
                            }
                        }
                    } catch (e: Exception) {
                        errors += "$name: ${e.message}"
                    }
                }
            }
            downloadManager.refreshLocalState(meta)
            refresh.update { it + 1 }
            message.value = if (errors.isEmpty()) {
                "Imported ${uris.size} file(s) into ${meta.id}"
            } else {
                "Import finished with errors: ${errors.joinToString("; ")}"
            }
        }
    }

    /**
     * Parse a models_manifest.json from a content URI or remote URL and report the plan.
     * Does not auto-download; surfaces file list so the user can Import or Download.
     */
    fun installFromManifestUri(uri: Uri) {
        viewModelScope.launch {
            try {
                val text = withContext(Dispatchers.IO) {
                    appContext.contentResolver.openInputStream(uri)?.bufferedReader()?.readText()
                        ?: throw IllegalStateException("Could not read manifest")
                }
                applyManifest(text)
            } catch (e: Exception) {
                message.value = "Manifest error: ${e.message}"
            }
        }
    }

    fun installFromManifestUrl(url: String) {
        viewModelScope.launch {
            try {
                val text = withContext(Dispatchers.IO) {
                    val client = OkHttpClient()
                    val resp = client.newCall(Request.Builder().url(url.trim()).build()).execute()
                    if (!resp.isSuccessful) throw IllegalStateException("HTTP ${resp.code}")
                    resp.body?.string() ?: throw IllegalStateException("Empty body")
                }
                applyManifest(text)
            } catch (e: Exception) {
                message.value = "Manifest URL error: ${e.message}"
            }
        }
    }

    fun installFromManifestText(json: String) {
        viewModelScope.launch {
            try {
                applyManifest(json)
            } catch (e: Exception) {
                message.value = "Manifest error: ${e.message}"
            }
        }
    }

    private fun applyManifest(json: String) {
        val manifest: ModelManifest = try {
            ModelManifestParser.parse(json)
        } catch (e: ModelManifestException) {
            message.value = "Invalid manifest: ${e.message}"
            return
        }
        val meta = ModelCatalogue.byId(manifest.modelId)
        val summary = buildString {
            append("Manifest for ${manifest.modelId} v${manifest.version}: ")
            append("${manifest.files.size} file(s)")
            if (manifest.graphs.isNotEmpty()) append(", ${manifest.graphs.size} graph(s)")
            append(". ")
            if (meta == null) {
                append("Not in static catalogue — import files manually into FRAME/Models/${manifest.modelId}/")
            } else if (!meta.isInstallable) {
                append("Catalogue entry has empty files[]; use Import with matching names, or wait for catalogue update.")
            } else {
                val names = manifest.files.map { it.name }.toSet()
                val expected = meta.files.map { it.name }.toSet()
                val missing = expected - names
                val extra = names - expected
                if (missing.isEmpty() && extra.isEmpty()) {
                    append("Matches catalogue. Use Download (if URLs present) or Import.")
                } else {
                    if (missing.isNotEmpty()) append("Catalogue missing in manifest: ${missing.joinToString()}. ")
                    if (extra.isNotEmpty()) append("Extra in manifest: ${extra.joinToString()}.")
                }
            }
        }
        message.value = summary
        refresh.update { it + 1 }
    }

    fun clearModelCache() {
        viewModelScope.launch {
            fileManager.clearCache()
            refreshLocal()
            message.value = "Cache cleared"
        }
    }

    fun runSmokeTest(modelId: String) {
        viewModelScope.launch {
            val meta = ModelCatalogue.byId(modelId) ?: return@launch
            smokeRunning.value = true
            message.value = "Smoke test running for ${meta.displayName}…"
            try {
                val report = smokeTester.run(meta, manifest = null)
                lastSmoke.value = report
                message.value = if (report.passed) {
                    "Smoke PASS for ${meta.id} (still not a quality validation)"
                } else {
                    "Smoke FAIL for ${meta.id}: ${report.blockedReason ?: report.graphs.firstOrNull { !it.passed }?.error ?: "see report"}"
                }
            } catch (t: Throwable) {
                message.value = "Smoke test crashed: ${t.message}"
                lastSmoke.value = null
            } finally {
                smokeRunning.value = false
                refresh.update { it + 1 }
            }
        }
    }

    fun dismissSmokeReport() {
        lastSmoke.value = null
    }

    /**
     * After a full smoke PASS the UI may offer EXPERIMENTAL only.
     * VALIDATED is never set here — that requires measured end-to-end MP4 on device.
     */
    fun markExperimentalHint(modelId: String) {
        val report = lastSmoke.value
        if (report == null || !report.passed || report.modelId != modelId) {
            message.value = "Mark EXPERIMENTAL only after a full smoke PASS for this model"
            return
        }
        // Catalogue is static RESEARCH entries; we only surface a message.
        // Changing status would require a mutable overlay store (future step).
        message.value =
            "Smoke passed for $modelId. Status remains RESEARCH in catalogue until a mutable " +
                "status store is added. Do NOT claim VALIDATED. Treat as EXPERIMENTAL for your own notes."
    }

    /**
     * Debug-only: copy assets/test_fixture/* into FRAME/Models/frame_test_fixture/
     * and refresh install state. No file picker required.
     */
    fun installTestFixtureFromAssets() {
        viewModelScope.launch {
            val modelId = "frame_test_fixture"
            val meta = ModelCatalogue.byId(modelId)
            if (meta == null) {
                message.value = "Test fixture not in catalogue"
                return@launch
            }
            val dir = fileManager.modelDir(modelId)
            val names = listOf("denoiser_fp32.onnx", "denoiser_fp16.onnx", "models_manifest.json")
            val errors = mutableListOf<String>()
            withContext(Dispatchers.IO) {
                for (name in names) {
                    try {
                        appContext.assets.open("test_fixture/$name").use { input ->
                            val dest = File(dir, name)
                            FileOutputStream(dest).use { output -> input.copyTo(output) }
                        }
                    } catch (e: Exception) {
                        errors += "$name: ${e.message}"
                    }
                }
            }
            downloadManager.refreshLocalState(meta)
            refresh.update { it + 1 }
            message.value = if (errors.isEmpty()) {
                "Test fixture installed under FRAME/Models/$modelId — run smoke test"
            } else {
                "Fixture install errors: ${errors.joinToString()}"
            }
        }
    }

    private fun queryDisplayName(uri: Uri): String? {
        val cursor = appContext.contentResolver.query(uri, null, null, null, null) ?: return null
        cursor.use {
            val idx = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (idx < 0) return null
            if (!it.moveToFirst()) return null
            return it.getString(idx)
        }
    }

    companion object {
        fun formatBytes(bytes: Long): String {
            if (bytes < 1024) return "$bytes B"
            if (bytes < 1024 * 1024) return "${bytes / 1024} KB"
            if (bytes < 1024L * 1024 * 1024) return "${bytes / (1024 * 1024)} MB"
            return "%.1f GB".format(bytes / (1024.0 * 1024 * 1024))
        }
    }
}
