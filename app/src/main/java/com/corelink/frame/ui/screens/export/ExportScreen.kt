package com.corelink.frame.ui.screens.export

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.corelink.frame.data.local.entity.ExportStatus
import com.corelink.frame.data.local.entity.ExportTaskEntity
import com.corelink.frame.export.ExportConfig
import com.corelink.frame.ui.theme.Accent
import com.corelink.frame.ui.theme.AccentSoft
import com.corelink.frame.ui.theme.Background
import com.corelink.frame.ui.theme.Border
import com.corelink.frame.ui.theme.Danger
import com.corelink.frame.ui.theme.Panel
import com.corelink.frame.ui.theme.Panel2
import com.corelink.frame.ui.theme.Success
import com.corelink.frame.ui.theme.TextMuted
import com.corelink.frame.ui.theme.TextPrimary
import com.corelink.frame.ui.theme.TextSecondary

@Composable
fun ExportScreen(
    onBack: () -> Unit,
    viewModel: ExportViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val history by viewModel.history.collectAsState()
    val active by viewModel.activeTask.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = TextPrimary)
            }
            Column {
                Text("Export", color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
                Text(
                    state.project?.name ?: "",
                    color = TextSecondary,
                    fontSize = 12.sp
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text("RESOLUTION", color = TextMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ExportConfig.PRESETS.forEachIndexed { index, preset ->
                    val selected = state.selectedPresetIndex == index
                    Text(
                        text = "${preset.height}p",
                        color = if (selected) AccentSoft else TextSecondary,
                        fontSize = 13.sp,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        modifier = Modifier
                            .background(if (selected) Panel2 else Panel, RoundedCornerShape(8.dp))
                            .border(1.dp, if (selected) Accent else Border, RoundedCornerShape(8.dp))
                            .clickable { viewModel.selectPreset(index) }
                            .padding(horizontal = 14.dp, vertical = 10.dp)
                    )
                }
            }

            val preset = ExportConfig.PRESETS.getOrElse(state.selectedPresetIndex) { ExportConfig.PRESETS.first() }
            InfoLine("Size", "${preset.width}×${preset.height}")
            InfoLine("Frame rate", "${preset.frameRate} fps")
            InfoLine("Video bitrate", "${preset.videoBitrate / 1_000_000} Mbps")

            OutlinedTextField(
                value = state.fileName,
                onValueChange = viewModel::setFileName,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Filename") },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Accent,
                    unfocusedBorderColor = Border,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                    cursorColor = Accent
                )
            )

            Button(
                onClick = { viewModel.startExport() },
                enabled = !state.isExporting,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Background)
            ) {
                Text(
                    if (state.isExporting) "Starting…" else "Export MP4",
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(vertical = 6.dp)
                )
            }

            Text(
                "Export only succeeds when timeline clips point to real media files. " +
                    "Missing media produces a clear failure — never a fake file.",
                color = TextMuted,
                fontSize = 12.sp
            )

            active?.let { task ->
                Spacer(Modifier.height(8.dp))
                ActiveExportCard(task)
            }

            if (history.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text("HISTORY", color = TextMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                history.take(8).forEach { task ->
                    HistoryRow(task)
                    Spacer(Modifier.height(6.dp))
                }
            }

            state.message?.let {
                Text(it, color = Danger, fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun ActiveExportCard(task: ExportTaskEntity) {
    val color = when (task.status) {
        ExportStatus.SUCCEEDED.name -> Success
        ExportStatus.FAILED.name -> Danger
        else -> AccentSoft
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Panel, RoundedCornerShape(10.dp))
            .border(1.dp, Border, RoundedCornerShape(10.dp))
            .padding(14.dp)
    ) {
        Text(task.status, color = color, fontWeight = FontWeight.Bold, fontSize = 12.sp, letterSpacing = 1.sp)
        Spacer(Modifier.height(6.dp))
        Text(task.stage.ifBlank { "—" }, color = TextPrimary, fontSize = 14.sp)
        task.errorMessage?.let {
            Spacer(Modifier.height(4.dp))
            Text(it, color = Danger, fontSize = 12.sp)
        }
        task.outputPath?.let {
            Spacer(Modifier.height(4.dp))
            Text(it, color = TextSecondary, fontSize = 11.sp)
        }
        task.progressPercent?.let { pct ->
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { pct / 100f },
                modifier = Modifier.fillMaxWidth(),
                color = Accent,
                trackColor = Border
            )
        } ?: run {
            if (task.status == ExportStatus.RUNNING.name) {
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    color = Accent,
                    trackColor = Border
                )
            }
        }
    }
}

@Composable
private fun HistoryRow(task: ExportTaskEntity) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Panel, RoundedCornerShape(8.dp))
            .border(1.dp, Border, RoundedCornerShape(8.dp))
            .padding(12.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(task.outputFileName, color = TextPrimary, fontSize = 13.sp)
            Text(
                "${task.width}×${task.height} · ${task.status}",
                color = TextMuted,
                fontSize = 11.sp
            )
        }
        task.fileSizeBytes?.let {
            Text("${it / 1024} KB", color = TextSecondary, fontSize = 11.sp)
        }
    }
}

@Composable
private fun InfoLine(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = TextMuted, fontSize = 13.sp)
        Text(value, color = TextPrimary, fontSize = 13.sp)
    }
}
