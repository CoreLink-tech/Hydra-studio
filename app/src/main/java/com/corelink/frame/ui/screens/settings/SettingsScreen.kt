package com.corelink.frame.ui.screens.settings

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import com.corelink.frame.ui.theme.Accent
import com.corelink.frame.ui.theme.Background
import com.corelink.frame.ui.theme.Border
import com.corelink.frame.ui.theme.Panel
import com.corelink.frame.ui.theme.TextMuted
import com.corelink.frame.ui.theme.TextPrimary
import com.corelink.frame.ui.theme.TextSecondary

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
    ) {
        Text("Settings", color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 22.sp)
        Spacer(Modifier.height(20.dp))

        Section("ABOUT")
        Card {
            Line("App", "FRAME")
            Line("Version", "0.1.0-milestone11")
            Line("Mode", "Offline-first")
        }

        Spacer(Modifier.height(16.dp))
        Section("DEVICE")
        Card {
            Line("Hardware profile", state.profileName ?: "Not assessed")
            Line("FRAME storage used", formatBytes(state.storageBytes))
        }
        Row {
            TextButton(onClick = { viewModel.refresh() }) {
                Text("Refresh storage", color = Accent)
            }
            TextButton(onClick = { viewModel.clearCache() }) {
                Text("Clear cache", color = TextSecondary)
            }
        }

        Spacer(Modifier.height(16.dp))
        Section("OFFLINE")
        Card {
            Text(
                "After models are installed on device, generation and editing do not require a network. " +
                    "Model download (when enabled) is the only optional online step.",
                color = TextSecondary,
                fontSize = 13.sp,
                lineHeight = 18.sp
            )
        }

        Spacer(Modifier.height(16.dp))
        Section("DIAGNOSTICS")
        Card {
            Text(
                "Use Hardware screen for RAM/thermal snapshot. " +
                    "Generation and export tasks store stage + error messages for QA.",
                color = TextSecondary,
                fontSize = 13.sp,
                lineHeight = 18.sp
            )
        }

        Spacer(Modifier.height(24.dp))
        Text(
            "FRAME does not ship simulated AI output. Failed generation means models are not yet validated on this device.",
            color = TextMuted,
            fontSize = 12.sp
        )
        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun Section(title: String) {
    Text(title, color = TextMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun Card(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Panel, RoundedCornerShape(10.dp))
            .border(1.dp, Border, RoundedCornerShape(10.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        content = { content() }
    )
}

@Composable
private fun Line(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = TextMuted, fontSize = 13.sp)
        Text(value, color = TextPrimary, fontSize = 13.sp)
    }
}

private fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    if (bytes < 1024 * 1024) return "${bytes / 1024} KB"
    if (bytes < 1024L * 1024 * 1024) return "${bytes / (1024 * 1024)} MB"
    return "%.1f GB".format(bytes / (1024.0 * 1024 * 1024))
}
