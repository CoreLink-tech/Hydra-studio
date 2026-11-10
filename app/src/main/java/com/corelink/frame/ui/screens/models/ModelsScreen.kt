package com.corelink.frame.ui.screens.models

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.corelink.frame.ai.models.InstallState
import com.corelink.frame.ai.models.ModelStatus
import com.corelink.frame.ui.theme.Accent
import com.corelink.frame.ui.theme.AccentSoft
import com.corelink.frame.ui.theme.Background
import com.corelink.frame.ui.theme.Border
import com.corelink.frame.ui.theme.Danger
import com.corelink.frame.ui.theme.Panel
import com.corelink.frame.ui.theme.Success
import com.corelink.frame.ui.theme.TextMuted
import com.corelink.frame.ui.theme.TextPrimary
import com.corelink.frame.ui.theme.TextSecondary

@Composable
fun ModelsScreen(
    viewModel: ModelsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    var manifestUrl by remember { mutableStateOf("") }
    var importTargetId by remember { mutableStateOf<String?>(null) }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris: List<Uri> ->
        val id = importTargetId
        if (id != null && uris.isNotEmpty()) {
            viewModel.importFiles(id, uris)
        }
        importTargetId = null
    }

    val manifestLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) viewModel.installFromManifestUri(uri)
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Spacer(Modifier.height(16.dp))
            Text("AI Models", color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 22.sp)
            Spacer(Modifier.height(4.dp))
            Text(
                "Install packs via download, file import, or manifest. " +
                    "INSTALLED only after files pass size/SHA-256 checks.",
                color = TextSecondary,
                fontSize = 13.sp
            )
            Spacer(Modifier.height(10.dp))

            // Storage + profile
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Panel, RoundedCornerShape(10.dp))
                    .border(1.dp, Border, RoundedCornerShape(10.dp))
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("FRAME storage", color = TextMuted, fontSize = 11.sp)
                    Text(
                        ModelsViewModel.formatBytes(state.storageUsedBytes),
                        color = TextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("Profile", color = TextMuted, fontSize = 11.sp)
                    Text(
                        state.profileName ?: "Not assessed",
                        color = TextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            // Wi-Fi only toggle
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Wi‑Fi only downloads", color = TextSecondary, fontSize = 13.sp)
                Switch(
                    checked = state.wifiOnly,
                    onCheckedChange = { viewModel.setWifiOnly(it) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Accent,
                        checkedTrackColor = AccentSoft
                    )
                )
            }

            Row {
                TextButton(onClick = { viewModel.refreshLocal() }) {
                    Text("Refresh", color = Accent, fontSize = 13.sp)
                }
                TextButton(onClick = { viewModel.clearModelCache() }) {
                    Text("Clear cache", color = TextSecondary, fontSize = 13.sp)
                }
                TextButton(onClick = { manifestLauncher.launch(arrayOf("application/json", "text/*")) }) {
                    Text("Manifest file", color = Accent, fontSize = 13.sp)
                }
            }

            // Manifest URL paste
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Panel, RoundedCornerShape(8.dp))
                    .border(1.dp, Border, RoundedCornerShape(8.dp))
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                BasicTextField(
                    value = manifestUrl,
                    onValueChange = { manifestUrl = it },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    textStyle = TextStyle(color = TextPrimary, fontSize = 13.sp),
                    cursorBrush = SolidColor(Accent),
                    decorationBox = { inner ->
                        if (manifestUrl.isEmpty()) {
                            Text("Manifest URL…", color = TextMuted, fontSize = 13.sp)
                        }
                        inner()
                    }
                )
                TextButton(
                    onClick = {
                        if (manifestUrl.isNotBlank()) {
                            viewModel.installFromManifestUrl(manifestUrl)
                        }
                    }
                ) {
                    Text("Load", color = Accent, fontSize = 13.sp)
                }
            }

            state.message?.let { msg ->
                Spacer(Modifier.height(6.dp))
                Text(msg, color = Accent, fontSize = 12.sp, lineHeight = 16.sp)
                TextButton(onClick = { viewModel.clearMessage() }) {
                    Text("Dismiss", color = TextMuted, fontSize = 12.sp)
                }
            }

            state.pendingLargeConfirmId?.let { id ->
                Spacer(Modifier.height(6.dp))
                Column(
                    Modifier
                        .fillMaxWidth()
                        .background(Panel, RoundedCornerShape(8.dp))
                        .border(1.dp, Accent, RoundedCornerShape(8.dp))
                        .padding(12.dp)
                ) {
                    Text(
                        "Large pack (${ModelsViewModel.formatBytes(state.pendingLargeConfirmBytes)}). " +
                            "Confirm download over current network policy?",
                        color = TextPrimary,
                        fontSize = 13.sp
                    )
                    Row {
                        TextButton(onClick = { viewModel.confirmLargeDownload() }) {
                            Text("Confirm $id", color = Accent, fontSize = 13.sp)
                        }
                        TextButton(onClick = { viewModel.dismissLargeConfirm() }) {
                            Text("Cancel", color = TextMuted, fontSize = 13.sp)
                        }
                    }
                }
            }
        }

        items(state.rows, key = { it.meta.id }) { row ->
            ModelDetailCard(
                row = row,
                onDownload = { viewModel.requestDownload(row.meta.id) },
                onCancel = { viewModel.cancelDownload(row.meta.id) },
                onImport = {
                    importTargetId = row.meta.id
                    importLauncher.launch(arrayOf("*/*"))
                },
                onSmokeTest = { viewModel.runSmokeTest(row.meta.id) },
                smokeRunning = state.smokeRunning
            )
        }

        state.lastSmokeReport?.let { report ->
            item {
                SmokeReportCard(
                    report = report,
                    canMarkExperimental = state.canMarkExperimental,
                    onDismiss = { viewModel.dismissSmokeReport() },
                    onMarkExperimental = { viewModel.markExperimentalHint(report.modelId) }
                )
            }
        }

        item {
            Spacer(Modifier.height(8.dp))
            Text(
                "Import copies files into FRAME/Models/<id>/ and checks size/SHA-256 against the catalogue. " +
                    "Download requires URL + SHA-256 on every file. Mobile data costs can be high — keep Wi‑Fi only ON.",
                color = TextMuted,
                fontSize = 12.sp,
                lineHeight = 16.sp
            )
            Spacer(Modifier.height(28.dp))
        }
    }
}

@Composable
private fun ModelDetailCard(
    row: ModelRow,
    onDownload: () -> Unit,
    onCancel: () -> Unit,
    onImport: () -> Unit,
    onSmokeTest: () -> Unit,
    smokeRunning: Boolean
) {
    val meta = row.meta
    val statusColor = when (meta.status) {
        ModelStatus.VALIDATED -> Success
        ModelStatus.EXPERIMENTAL -> Accent
        ModelStatus.RESEARCH -> AccentSoft
        ModelStatus.INCOMPATIBLE -> Danger
    }
    val installColor = when (row.install.state) {
        InstallState.INSTALLED -> Success
        InstallState.PARTIAL -> Accent
        InstallState.CORRUPT, InstallState.INCOMPATIBLE -> Danger
        InstallState.DOWNLOADING, InstallState.VERIFYING -> Accent
        InstallState.NOT_PRESENT -> TextMuted
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Panel, RoundedCornerShape(10.dp))
            .border(1.dp, Border, RoundedCornerShape(10.dp))
            .padding(14.dp)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(meta.displayName, color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            Text(meta.status.name, color = statusColor, fontSize = 11.sp, fontWeight = FontWeight.Medium)
        }
        Spacer(Modifier.height(4.dp))
        Text(meta.description, color = TextSecondary, fontSize = 13.sp, lineHeight = 18.sp)
        Spacer(Modifier.height(8.dp))
        Meta("Install", row.install.state.name, installColor)
        Meta("Params", meta.parameterCountApprox)
        Meta("Min RAM", "%.1f GB".format(meta.minRamGbRecommended))
        Meta("Runtime", meta.runtime.name.replace('_', ' '))
        Meta("On disk", if (row.install.bytesOnDisk > 0) ModelsViewModel.formatBytes(row.install.bytesOnDisk) else "—")
        Meta(
            "Files",
            if (meta.files.isEmpty()) "none (not installable)"
            else "${meta.files.size} declared" + if (meta.isDownloadable) " · downloadable" else " · import only"
        )

        // Per-file status when catalogue has a file list
        if (meta.files.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            meta.files.forEach { f ->
                val tag = when {
                    row.install.corruptFiles.contains(f.name) -> "CORRUPT"
                    row.install.missingFiles.contains(f.name) -> "missing"
                    row.install.state == InstallState.INSTALLED -> "ok"
                    row.install.state == InstallState.PARTIAL &&
                        !row.install.missingFiles.contains(f.name) -> "ok"
                    else -> "—"
                }
                val color = when (tag) {
                    "ok" -> Success
                    "CORRUPT" -> Danger
                    "missing" -> Accent
                    else -> TextMuted
                }
                Meta("  ${f.name}", tag, color)
            }
        }

        row.install.validationError?.let {
            Spacer(Modifier.height(4.dp))
            Text(it, color = Danger, fontSize = 12.sp, lineHeight = 16.sp)
        }

        if (row.install.state == InstallState.DOWNLOADING || row.progressTotal > 0L) {
            Spacer(Modifier.height(4.dp))
            val label = row.progressFile ?: "…"
            val pct = if (row.progressTotal > 0L) {
                ((row.progressDone * 100) / row.progressTotal).toInt()
            } else 0
            Text(
                "Downloading $label · $pct% (${ModelsViewModel.formatBytes(row.progressDone)})",
                color = Accent,
                fontSize = 12.sp
            )
        }

        if (row.recommended) {
            Spacer(Modifier.height(4.dp))
            Text("Fits current device profile", color = Success, fontSize = 12.sp)
        }
        row.warning?.let {
            Spacer(Modifier.height(2.dp))
            Text(it, color = Danger, fontSize = 12.sp)
        }
        Spacer(Modifier.height(2.dp))
        Text(meta.compatibilityNotes, color = TextMuted, fontSize = 12.sp, lineHeight = 16.sp)

        // Actions — thumb-sized TextButtons
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
            if (meta.isDownloadable) {
                if (row.install.state == InstallState.DOWNLOADING) {
                    TextButton(onClick = onCancel) {
                        Text("Cancel", color = Danger, fontSize = 13.sp)
                    }
                } else {
                    TextButton(onClick = onDownload) {
                        Text("Download", color = Accent, fontSize = 13.sp)
                    }
                }
            }
            TextButton(onClick = onImport) {
                Text("Import files", color = Accent, fontSize = 13.sp)
            }
            val canTest = row.install.state == InstallState.INSTALLED ||
                row.install.state == InstallState.PARTIAL
            if (canTest) {
                TextButton(onClick = onSmokeTest, enabled = !smokeRunning) {
                    Text(
                        if (smokeRunning) "Testing…" else "Test model",
                        color = if (smokeRunning) TextMuted else Accent,
                        fontSize = 13.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun SmokeReportCard(
    report: com.corelink.frame.ai.download.ModelSmokeTester.SmokeReport,
    canMarkExperimental: Boolean,
    onDismiss: () -> Unit,
    onMarkExperimental: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE)
        as android.content.ClipboardManager
    val createDoc = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(report.toJson().toByteArray(Charsets.UTF_8))
                }
            } catch (_: Exception) { /* ignore */ }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Panel, RoundedCornerShape(10.dp))
            .border(1.dp, if (report.passed) Success else Danger, RoundedCornerShape(10.dp))
            .padding(14.dp)
    ) {
        Text(report.header, color = Accent, fontSize = 12.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(6.dp))
        Text(
            "model=${report.modelId}  ${if (report.passed) "PASS" else "FAIL"}  install=${report.installState}",
            color = TextPrimary,
            fontSize = 13.sp
        )
        report.blockedReason?.let {
            Text("blocked: $it", color = Danger, fontSize = 12.sp)
        }
        Text(
            "thermal ${report.thermalBefore} → ${report.thermalAfter}",
            color = TextSecondary,
            fontSize = 12.sp
        )
        Text(
            "pssKb ${report.pssKbBefore} → ${report.pssKbAfter}  nativeHeapKb ${report.nativeHeapKbBefore} → ${report.nativeHeapKbAfter}",
            color = TextSecondary,
            fontSize = 12.sp
        )
        report.graphs.forEach { g ->
            Text(
                "${g.file}: ${if (g.passed) "PASS" else "FAIL"} load=${g.loadMs}ms run=${g.runMs}ms" +
                    (g.error?.let { " · $it" } ?: ""),
                color = if (g.passed) Success else Danger,
                fontSize = 12.sp
            )
        }
        Spacer(Modifier.height(8.dp))
        Row {
            TextButton(onClick = {
                val send = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(android.content.Intent.EXTRA_TEXT, report.toShortText())
                }
                context.startActivity(android.content.Intent.createChooser(send, "Share smoke report"))
            }) {
                Text("Share", color = Accent, fontSize = 13.sp)
            }
            TextButton(onClick = {
                clipboard.setPrimaryClip(
                    android.content.ClipData.newPlainText("FRAME smoke report", report.toShortText())
                )
            }) {
                Text("Copy", color = Accent, fontSize = 13.sp)
            }
            TextButton(onClick = {
                createDoc.launch("frame-smoke-${report.modelId}.json")
            }) {
                Text("Save", color = Accent, fontSize = 13.sp)
            }
            TextButton(onClick = onDismiss) {
                Text("Dismiss", color = TextMuted, fontSize = 13.sp)
            }
        }
        if (canMarkExperimental) {
            TextButton(onClick = onMarkExperimental) {
                Text("Mark as EXPERIMENTAL (notes only)", color = Accent, fontSize = 12.sp)
            }
            Text(
                "VALIDATED is never set from a smoke test.",
                color = TextMuted,
                fontSize = 11.sp
            )
        }
    }
}

@Composable
private fun Meta(label: String, value: String, valueColor: androidx.compose.ui.graphics.Color = TextPrimary) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 1.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = TextMuted, fontSize = 12.sp)
        Text(value, color = valueColor, fontSize = 12.sp)
    }
}
