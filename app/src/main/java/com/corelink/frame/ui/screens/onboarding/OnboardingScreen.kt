package com.corelink.frame.ui.screens.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.corelink.frame.ui.theme.Accent
import com.corelink.frame.ui.theme.Background
import com.corelink.frame.ui.theme.TextPrimary
import com.corelink.frame.ui.theme.TextSecondary

@Composable
fun OnboardingScreen(
    onContinue: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "FRAME",
            color = TextPrimary,
            fontWeight = FontWeight.Bold,
            fontSize = 32.sp,
            letterSpacing = 4.sp
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "OFFLINE FILM STUDIO",
            color = TextSecondary,
            fontSize = 12.sp,
            letterSpacing = 2.sp
        )
        Spacer(Modifier.height(40.dp))
        Text(
            text = "Generate video clips with AI,\norganise them into scenes,\nedit, and export complete films.\n\nEverything runs locally on your device.\nModels must be downloaded once.\nAfter that, no internet is required.",
            color = TextSecondary,
            fontSize = 15.sp,
            textAlign = TextAlign.Center,
            lineHeight = 22.sp
        )
        Spacer(Modifier.height(48.dp))
        Button(
            onClick = onContinue,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Accent,
                contentColor = Background
            )
        ) {
            Text(
                text = "Continue",
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(vertical = 6.dp)
            )
        }
    }
}
