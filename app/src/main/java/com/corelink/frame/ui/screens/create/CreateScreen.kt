package com.corelink.frame.ui.screens.create

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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.corelink.frame.ui.theme.Accent
import com.corelink.frame.ui.theme.AccentSoft
import com.corelink.frame.ui.theme.Background
import com.corelink.frame.ui.theme.Border
import com.corelink.frame.ui.theme.Danger
import com.corelink.frame.ui.theme.Panel
import com.corelink.frame.ui.theme.Panel2
import com.corelink.frame.ui.theme.TextMuted
import com.corelink.frame.ui.theme.TextPrimary
import com.corelink.frame.ui.theme.TextSecondary

@Composable
fun CreateScreen(
    onGenerationStarted: (String) -> Unit = {},
    viewModel: CreateViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = Accent,
        unfocusedBorderColor = Border,
        focusedTextColor = TextPrimary,
        unfocusedTextColor = TextPrimary,
        cursorColor = Accent,
        focusedLabelColor = AccentSoft,
        unfocusedLabelColor = TextMuted
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
    ) {
        Text(
            text = "Create video",
            color = TextPrimary,
            fontWeight = FontWeight.SemiBold,
            fontSize = 22.sp
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "Image-to-video is the primary path. Text-to-video arrives after a validated pipeline.",
            color = TextSecondary,
            fontSize = 13.sp
        )
        Spacer(Modifier.height(20.dp))

        // Mode toggle
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Panel, RoundedCornerShape(10.dp))
                .border(1.dp, Border, RoundedCornerShape(10.dp))
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            ModeChip("Image to video", state.mode == CreateMode.IMAGE, Modifier.weight(1f)) {
                viewModel.setMode(CreateMode.IMAGE)
            }
            ModeChip("Text to video", state.mode == CreateMode.TEXT, Modifier.weight(1f)) {
                viewModel.setMode(CreateMode.TEXT)
            }
        }

        Spacer(Modifier.height(20.dp))

        if (state.mode == CreateMode.IMAGE) {
            Text("INPUT IMAGE", color = TextMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            Spacer(Modifier.height(8.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Panel, RoundedCornerShape(10.dp))
                    .border(1.dp, Border, RoundedCornerShape(10.dp))
                    .padding(16.dp)
            ) {
                Text(
                    text = state.inputImageName ?: "No image selected",
                    color = if (state.inputImagePath != null) TextPrimary else TextMuted,
                    fontSize = 14.sp
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Image picker will use the system photo picker (SAF). Wire ActivityResult in a later pass if needed — path can be set programmatically for testing.",
                    color = TextMuted,
                    fontSize = 12.sp
                )
            }
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = state.motionPrompt,
                onValueChange = viewModel::setMotionPrompt,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Motion prompt") },
                placeholder = { Text("Camera slowly pushes in, gentle wind…") },
                minLines = 3,
                colors = fieldColors
            )
        } else {
            OutlinedTextField(
                value = state.prompt,
                onValueChange = viewModel::setPrompt,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Scene description") },
                placeholder = { Text("A lone traveller walks through an abandoned city at sunrise…") },
                minLines = 4,
                colors = fieldColors
            )
        }

        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            value = state.negativePrompt,
            onValueChange = viewModel::setNegativePrompt,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Negative prompt (optional)") },
            colors = fieldColors
        )

        Spacer(Modifier.height(20.dp))
        Text("DURATION", color = TextMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(3, 5, 6, 8).forEach { sec ->
                val selected = state.durationSeconds == sec
                Text(
                    text = "${sec}s",
                    color = if (selected) AccentSoft else TextSecondary,
                    fontSize = 13.sp,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    modifier = Modifier
                        .background(if (selected) Panel2 else Panel, RoundedCornerShape(8.dp))
                        .border(1.dp, if (selected) Accent else Border, RoundedCornerShape(8.dp))
                        .clickable { viewModel.setDuration(sec) }
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                )
            }
        }

        Spacer(Modifier.height(20.dp))
        Text("MODEL", color = TextMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        Spacer(Modifier.height(8.dp))
        viewModel.models.forEach { model ->
            val selected = state.selectedModelId == model.id
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
                    .background(if (selected) Panel2 else Panel, RoundedCornerShape(10.dp))
                    .border(1.dp, if (selected) Accent else Border, RoundedCornerShape(10.dp))
                    .clickable { viewModel.setModel(model.id) }
                    .padding(14.dp)
            ) {
                Text(model.displayName, color = TextPrimary, fontWeight = FontWeight.Medium, fontSize = 14.sp)
                Text(
                    "${model.status.name} · min ${model.minRamGbRecommended} GB · ${model.parameterCountApprox}",
                    color = TextMuted,
                    fontSize = 11.sp
                )
            }
        }

        if (state.error != null) {
            Spacer(Modifier.height(12.dp))
            Text(state.error!!, color = Danger, fontSize = 13.sp)
        }

        Spacer(Modifier.height(24.dp))
        Button(
            onClick = { viewModel.submit(onGenerationStarted) },
            enabled = !state.isSubmitting,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(10.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Background)
        ) {
            Text(
                if (state.isSubmitting) "Queueing…" else "Generate",
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(vertical = 6.dp)
            )
        }
        Spacer(Modifier.height(12.dp))
        Text(
            text = "Generation only succeeds after a validated model is installed. Until then the task will fail with a clear reason — never a fake video.",
            color = TextMuted,
            fontSize = 12.sp
        )
        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun ModeChip(label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Text(
        text = label,
        color = if (selected) AccentSoft else TextSecondary,
        fontSize = 13.sp,
        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        modifier = modifier
            .background(if (selected) Panel2 else Background, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 8.dp),
        textAlign = androidx.compose.ui.text.style.TextAlign.Center
    )
}
