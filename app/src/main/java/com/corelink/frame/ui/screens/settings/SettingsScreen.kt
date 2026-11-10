package com.corelink.frame.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.corelink.frame.ui.theme.Background
import com.corelink.frame.ui.theme.TextMuted
import com.corelink.frame.ui.theme.TextPrimary
import com.corelink.frame.ui.theme.TextSecondary

@Composable
fun SettingsScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
            .padding(20.dp)
    ) {
        Text(
            text = "Settings",
            color = TextPrimary,
            fontWeight = FontWeight.SemiBold,
            fontSize = 22.sp
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Preferences, storage management and diagnostics will be expanded in later milestones.",
            color = TextSecondary,
            fontSize = 14.sp
        )
        Spacer(Modifier.height(32.dp))
        Text(
            text = "Version 0.1.0 — Milestone 1 foundation",
            color = TextMuted,
            fontSize = 12.sp
        )
    }
}
