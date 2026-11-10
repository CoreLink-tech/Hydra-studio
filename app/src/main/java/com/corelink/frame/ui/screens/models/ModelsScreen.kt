package com.corelink.frame.ui.screens.models

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
fun ModelsScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
            .padding(20.dp)
    ) {
        Text(
            text = "AI Models",
            color = TextPrimary,
            fontWeight = FontWeight.SemiBold,
            fontSize = 22.sp
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Model catalogue, download manager and validation will be built in Milestone 10. First a working inference PoC is required.",
            color = TextSecondary,
            fontSize = 14.sp
        )
        Spacer(Modifier.height(32.dp))
        Text(
            text = "No models installed.",
            color = TextMuted,
            fontSize = 13.sp
        )
    }
}
