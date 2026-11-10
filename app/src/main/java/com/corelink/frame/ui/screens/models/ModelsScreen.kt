package com.corelink.frame.ui.screens.models

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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
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

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Spacer(Modifier.height(20.dp))
            Text("AI Models", color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 22.sp)
            Spacer(Modifier.height(4.dp))
            Text(
                "Catalogue, install state, and device compatibility. " +
                    "Download stays disabled until verified package URLs exist.",
                color = TextSecondary,
                fontSize = 13.sp
            )
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Panel, RoundedCornerShape(10.dp))
                    .border(1.dp, Border, RoundedCornerShape(10.dp))
                    .padding(14.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("FRAME storage", color = TextMuted, fontSize = 11.sp)
                    Text(formatBytes(state.storageUsedBytes), color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                }
                Column {
                    Text("Profile", color = TextMuted, fontSize = 11.sp)
                    Text(state.profileName ?: "Not assessed", color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                }
            }
            Row {
                TextButton(onClick = { viewModel.refreshLocal() }) {
                    Text("Refresh", color = Accent)
                }
                TextButton(onClick = { viewModel.clearModelCache() }) {
                    Text("Clear cache", color = TextSecondary)
                }
            }
        }

        items(state.rows, key = { it.meta.id }) { row ->
            ModelDetailCard(row)
        }

        item {
            Spacer(Modifier.height(8.dp))
            Text(
                "Install is INSTALLED only after files exist under FRAME/Models/<id>/ and pass checks. " +
                    "Research models cannot be claimed working.",
                color = TextMuted,
                fontSize = 12.sp
            )
            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun ModelDetailCard(row: ModelRow) {
    val meta = row.meta
    val statusColor = when (meta.status) {
        ModelStatus.VALIDATED -> Success
        ModelStatus.EXPERIMENTAL -> Accent
        ModelStatus.RESEARCH -> AccentSoft
        ModelStatus.INCOMPATIBLE -> Danger
    }
    val installColor = when (row.install.state) {
        InstallState.INSTALLED -> Success
        InstallState.CORRUPT, InstallState.INCOMPATIBLE -> Danger
        InstallState.DOWNLOADING, InstallState.VERIFYING -> Accent
        else -> TextMuted
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Panel, RoundedCornerShape(10.dp))
            .border(1.dp, Border, RoundedCornerShape(10.dp))
            .padding(16.dp)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(meta.displayName, color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            Text(meta.status.name, color = statusColor, fontSize = 11.sp, fontWeight = FontWeight.Medium)
        }
        Spacer(Modifier.height(4.dp))
        Text(meta.description, color = TextSecondary, fontSize = 13.sp, lineHeight = 18.sp)
        Spacer(Modifier.height(10.dp))
        Meta("Install", row.install.state.name, installColor)
        Meta("Params", meta.parameterCountApprox)
        Meta("Min RAM", "%.1f GB".format(meta.minRamGbRecommended))
        Meta("Runtime", meta.runtime.name.replace('_', ' '))
        Meta("Licence", meta.licence)
        Meta("On disk", if (row.install.bytesOnDisk > 0) formatBytes(row.install.bytesOnDisk) else "—")
        if (row.recommended) {
            Spacer(Modifier.height(6.dp))
            Text("Fits current device profile", color = Success, fontSize = 12.sp)
        }
        row.warning?.let {
            Spacer(Modifier.height(4.dp))
            Text(it, color = Danger, fontSize = 12.sp)
        }
        Spacer(Modifier.height(4.dp))
        Text(meta.compatibilityNotes, color = TextMuted, fontSize = 12.sp, lineHeight = 16.sp)
    }
}

@Composable
private fun Meta(label: String, value: String, valueColor: androidx.compose.ui.graphics.Color = TextPrimary) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = TextMuted, fontSize = 12.sp)
        Text(value, color = valueColor, fontSize = 12.sp)
    }
}

private fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    if (bytes < 1024 * 1024) return "${bytes / 1024} KB"
    if (bytes < 1024L * 1024 * 1024) return "${bytes / (1024 * 1024)} MB"
    return "%.1f GB".format(bytes / (1024.0 * 1024 * 1024))
}
