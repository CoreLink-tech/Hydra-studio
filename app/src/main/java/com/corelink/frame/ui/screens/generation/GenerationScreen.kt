package com.corelink.frame.ui.screens.generation

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import com.corelink.frame.data.local.entity.TaskStatus
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
fun GenerationScreen(
    onBack: () -> Unit,
    viewModel: GenerationViewModel = hiltViewModel()
) {
    val task by viewModel.task.collectAsState()

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
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextPrimary)
            }
            Text(
                text = "Generation",
                color = TextPrimary,
                fontWeight = FontWeight.SemiBold,
                fontSize = 18.sp
            )
        }

        val t = task
        if (t == null) {
            Text(
                "Task not found",
                color = TextSecondary,
                modifier = Modifier.padding(20.dp)
            )
            return
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            StatusCard(t.status, t.stage, t.stageDetail, t.progressPercent)

            InfoRow("Model", t.modelId)
            InfoRow("Mode", t.mode.replace('_', ' '))
            if (t.motionPrompt.isNotBlank()) InfoRow("Motion", t.motionPrompt)
            InfoRow("Target", "${t.targetWidth}×${t.targetHeight} · ${t.targetFrames} frames")

            if (t.errorMessage != null) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Panel, RoundedCornerShape(10.dp))
                        .border(1.dp, Danger.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
                        .padding(14.dp)
                ) {
                    Text("Error", color = Danger, fontWeight = FontWeight.Medium, fontSize = 13.sp)
                    Spacer(Modifier.height(4.dp))
                    Text(t.errorMessage, color = TextSecondary, fontSize = 13.sp)
                    if (t.errorReason != null) {
                        Spacer(Modifier.height(4.dp))
                        Text(t.errorReason, color = TextMuted, fontSize = 11.sp)
                    }
                }
            }

            if (t.outputPath != null) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Panel, RoundedCornerShape(10.dp))
                        .border(1.dp, Success.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
                        .padding(14.dp)
                ) {
                    Text("Output ready", color = Success, fontWeight = FontWeight.Medium, fontSize = 13.sp)
                    Spacer(Modifier.height(4.dp))
                    Text(t.outputPath, color = TextSecondary, fontSize = 12.sp)
                }
            }

            Spacer(Modifier.weight(1f))

            val canCancel = t.status == TaskStatus.QUEUED.name ||
                t.status == TaskStatus.RUNNING.name ||
                t.status == TaskStatus.CANCELLING.name
            if (canCancel) {
                Button(
                    onClick = { viewModel.cancel() },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Panel,
                        contentColor = Danger
                    )
                ) {
                    Text(
                        if (t.status == TaskStatus.CANCELLING.name) "Cancelling…" else "Cancel generation",
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusCard(
    status: String,
    stage: String,
    detail: String,
    percent: Int?
) {
    val color = when (status) {
        TaskStatus.SUCCEEDED.name -> Success
        TaskStatus.FAILED.name -> Danger
        TaskStatus.CANCELLED.name, TaskStatus.CANCELLING.name -> TextMuted
        else -> AccentSoft
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Panel, RoundedCornerShape(12.dp))
            .border(1.dp, Border, RoundedCornerShape(12.dp))
            .padding(16.dp)
    ) {
        Text(status, color = color, fontWeight = FontWeight.Bold, fontSize = 13.sp, letterSpacing = 1.sp)
        Spacer(Modifier.height(8.dp))
        Text(stage.ifBlank { "—" }, color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Medium)
        if (detail.isNotBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(detail, color = TextSecondary, fontSize = 13.sp)
        }
        Spacer(Modifier.height(12.dp))
        if (percent != null) {
            LinearProgressIndicator(
                progress = { percent / 100f },
                modifier = Modifier.fillMaxWidth(),
                color = Accent,
                trackColor = Border
            )
            Spacer(Modifier.height(4.dp))
            Text("$percent%", color = TextMuted, fontSize = 11.sp)
        } else {
            // Indeterminate only while actively running
            if (status == TaskStatus.RUNNING.name || status == TaskStatus.QUEUED.name) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    color = Accent,
                    trackColor = Border
                )
                Spacer(Modifier.height(4.dp))
                Text("Stage-based progress (no reliable % from runtime)", color = TextMuted, fontSize = 11.sp)
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = TextMuted, fontSize = 13.sp)
        Text(value, color = TextPrimary, fontSize = 13.sp, modifier = Modifier.padding(start = 12.dp))
    }
}
