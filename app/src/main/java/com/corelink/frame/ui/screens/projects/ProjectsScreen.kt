package com.corelink.frame.ui.screens.projects

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
fun ProjectsScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
            .padding(20.dp)
    ) {
        Text(
            text = "Projects",
            color = TextPrimary,
            fontWeight = FontWeight.SemiBold,
            fontSize = 22.sp
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Project library, scenes and clip organisation will be implemented in Milestone 5 after the generation engine is proven.",
            color = TextSecondary,
            fontSize = 14.sp
        )
        Spacer(Modifier.height(32.dp))
        Text(
            text = "No projects yet.",
            color = TextMuted,
            fontSize = 13.sp
        )
    }
}
