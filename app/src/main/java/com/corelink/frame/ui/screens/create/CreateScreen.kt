package com.corelink.frame.ui.screens.create

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
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
fun CreateScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
            .padding(20.dp)
    ) {
        Text(
            text = "Create Video",
            color = TextPrimary,
            fontWeight = FontWeight.SemiBold,
            fontSize = 22.sp
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Text-to-video and Image-to-video generation will be available after the AI inference pipeline is validated (Milestone 3–4).",
            color = TextSecondary,
            fontSize = 14.sp
        )
        Spacer(Modifier.height(24.dp))
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(vertical = 40.dp)
        ) {
            Text(
                text = "Generation UI under construction.\nCore engine comes first.",
                color = TextMuted,
                fontSize = 13.sp
            )
        }
    }
}
