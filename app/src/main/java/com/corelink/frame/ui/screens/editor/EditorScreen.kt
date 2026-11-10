package com.corelink.frame.ui.screens.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.ContentCut
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.TextButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.corelink.frame.data.local.entity.TimelineItemEntity
import com.corelink.frame.data.local.entity.TimelineTrackEntity
import com.corelink.frame.ui.theme.Accent
import com.corelink.frame.ui.theme.AccentSoft
import com.corelink.frame.ui.theme.Background
import com.corelink.frame.ui.theme.Border
import com.corelink.frame.ui.theme.Panel
import com.corelink.frame.ui.theme.Panel2
import com.corelink.frame.ui.theme.Panel3
import com.corelink.frame.ui.theme.TextMuted
import com.corelink.frame.ui.theme.TextPrimary
import com.corelink.frame.ui.theme.TextSecondary

/** Pixels per second of timeline for layout */
private const val PX_PER_SECOND = 48f

@Composable
fun EditorScreen(
    onBack: () -> Unit,
    viewModel: EditorViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val timelineWidth = ((state.durationMs / 1000f) * PX_PER_SECOND).dp.coerceAtLeast(320.dp)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
    ) {
        // Top bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = TextPrimary)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    state.project?.name ?: "Editor",
                    color = TextPrimary,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp
                )
                Text(
                    formatTime(state.playheadMs) + " / " + formatTime(state.durationMs),
                    color = TextSecondary,
                    fontSize = 11.sp
                )
            }
            IconButton(
                onClick = { viewModel.splitAtPlayhead() },
                enabled = state.selectedItemId != null
            ) {
                Icon(Icons.Outlined.ContentCut, "Split", tint = if (state.selectedItemId != null) AccentSoft else TextMuted)
            }
            IconButton(
                onClick = { viewModel.deleteSelected() },
                enabled = state.selectedItemId != null
            ) {
                Icon(Icons.Outlined.Delete, "Delete", tint = if (state.selectedItemId != null) AccentSoft else TextMuted)
            }
        }

        // Preview monitor
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
                .padding(horizontal = 16.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Panel)
                .border(1.dp, Border, RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("PREVIEW", color = TextMuted, fontSize = 10.sp, letterSpacing = 1.5.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text(
                    if (state.items.isEmpty()) "Add clips to the timeline"
                    else "Playhead ${formatTime(state.playheadMs)}",
                    color = TextSecondary,
                    fontSize = 13.sp
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Media3 player binds here when clips have real files",
                    color = TextMuted,
                    fontSize = 11.sp
                )
            }
        }

        // Transport
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { viewModel.togglePlay() }) {
                Icon(
                    if (state.isPlaying) Icons.Outlined.Pause else Icons.Outlined.PlayArrow,
                    contentDescription = if (state.isPlaying) "Pause" else "Play",
                    tint = AccentSoft,
                    modifier = Modifier.size(36.dp)
                )
            }
        }

        // Text / audio tools
        var showTitleDialog by remember { mutableStateOf(false) }
        var showSubtitleDialog by remember { mutableStateOf(false) }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ToolChip("Title") { showTitleDialog = true }
            ToolChip("Subtitle") { showSubtitleDialog = true }
            ToolChip("Fade") { viewModel.applyFadeOnSelected() }
        }

        // Volume for selected audio/video item
        val selected = state.items.find { it.id == state.selectedItemId }
        if (selected != null && (selected.type == "AUDIO" || selected.type == "VIDEO")) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Vol", color = TextMuted, fontSize = 11.sp, modifier = Modifier.width(28.dp))
                Slider(
                    value = selected.volume,
                    onValueChange = { viewModel.setSelectedVolume(it) },
                    modifier = Modifier.weight(1f),
                    colors = SliderDefaults.colors(
                        thumbColor = Accent,
                        activeTrackColor = AccentSoft,
                        inactiveTrackColor = Border
                    )
                )
                Text("${(selected.volume * 100).toInt()}%", color = TextSecondary, fontSize = 11.sp)
            }
        }

        if (showTitleDialog) {
            TextInputDialog(
                title = "Add title",
                onDismiss = { showTitleDialog = false },
                onConfirm = {
                    viewModel.addTitleAtPlayhead(it)
                    showTitleDialog = false
                }
            )
        }
        if (showSubtitleDialog) {
            TextInputDialog(
                title = "Add subtitle",
                onDismiss = { showSubtitleDialog = false },
                onConfirm = {
                    viewModel.addSubtitleAtPlayhead(it)
                    showSubtitleDialog = false
                }
            )
        }

        // Clip bin
        if (state.clips.isNotEmpty()) {
            Text(
                "PROJECT CLIPS — tap to add",
                color = TextMuted,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            Row(
                modifier = Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                state.clips.forEach { clip ->
                    Text(
                        text = clip.name,
                        color = TextPrimary,
                        fontSize = 12.sp,
                        modifier = Modifier
                            .background(Panel2, RoundedCornerShape(8.dp))
                            .border(1.dp, Border, RoundedCornerShape(8.dp))
                            .clickable { viewModel.addClipToTimeline(clip.id) }
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                }
            }
        }

        // Timeline
        Text(
            "TIMELINE",
            color = TextMuted,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
        )

        val hScroll = rememberScrollState()
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(bottom = 8.dp)
        ) {
            // Ruler + tracks
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .horizontalScroll(hScroll)
            ) {
                // Track labels
                Column(modifier = Modifier.width(72.dp)) {
                    Spacer(Modifier.height(20.dp)) // ruler height
                    state.tracks.forEach { track ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .padding(end = 6.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            Text(track.name, color = TextMuted, fontSize = 11.sp, maxLines = 1)
                        }
                    }
                }

                // Timeline canvas
                Box {
                    Column {
                        // Time ruler
                        Box(
                            modifier = Modifier
                                .width(timelineWidth)
                                .height(20.dp)
                                .background(Panel)
                        ) {
                            val seconds = (state.durationMs / 1000).toInt().coerceAtLeast(1)
                            for (s in 0..seconds step 1) {
                                val x = (s * PX_PER_SECOND).dp
                                Text(
                                    text = "${s}s",
                                    color = TextMuted,
                                    fontSize = 9.sp,
                                    modifier = Modifier.offset(x = x).padding(start = 2.dp)
                                )
                            }
                        }

                        state.tracks.forEach { track ->
                            TrackLane(
                                track = track,
                                items = state.items.filter { it.trackId == track.id },
                                selectedId = state.selectedItemId,
                                width = timelineWidth,
                                onSelect = { viewModel.selectItem(it) }
                            )
                        }
                    }

                    // Playhead
                    val playheadX = ((state.playheadMs / 1000f) * PX_PER_SECOND).dp
                    Box(
                        modifier = Modifier
                            .offset(x = playheadX)
                            .width(2.dp)
                            .fillMaxHeight()
                            .background(Accent)
                    )
                }
            }
        }

        state.message?.let {
            Text(
                it,
                color = AccentSoft,
                fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
            )
        }
    }
}

@Composable
private fun TrackLane(
    track: TimelineTrackEntity,
    items: List<TimelineItemEntity>,
    selectedId: String?,
    width: Dp,
    onSelect: (String) -> Unit
) {
    Box(
        modifier = Modifier
            .width(width)
            .height(48.dp)
            .background(Panel)
            .border(width = 0.5.dp, color = Border)
    ) {
        items.forEach { item ->
            val x = ((item.startMs / 1000f) * PX_PER_SECOND).dp
            val w = ((item.durationMs / 1000f) * PX_PER_SECOND).dp.coerceAtLeast(24.dp)
            val selected = item.id == selectedId
            Box(
                modifier = Modifier
                    .offset(x = x, y = 6.dp)
                    .width(w)
                    .height(36.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (selected) Panel3 else Panel2)
                    .border(1.dp, if (selected) Accent else Border, RoundedCornerShape(6.dp))
                    .clickable { onSelect(item.id) }
                    .padding(horizontal = 6.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Text(
                    item.label.ifBlank { track.type },
                    color = TextPrimary,
                    fontSize = 11.sp,
                    maxLines = 1
                )
            }
        }
    }
}

private fun formatTime(ms: Long): String {
    val totalSec = (ms / 1000).toInt()
    val m = totalSec / 60
    val s = totalSec % 60
    val frac = ((ms % 1000) / 100).toInt()
    return "%d:%02d.%d".format(m, s, frac)
}


@Composable
private fun ToolChip(label: String, onClick: () -> Unit) {
    Text(
        text = label,
        color = TextPrimary,
        fontSize = 12.sp,
        modifier = Modifier
            .background(Panel2, RoundedCornerShape(8.dp))
            .border(1.dp, Border, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    )
}

@Composable
private fun TextInputDialog(
    title: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var value by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Panel,
        titleContentColor = TextPrimary,
        textContentColor = TextSecondary,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                placeholder = { Text("Enter text", color = TextMuted) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Accent,
                    unfocusedBorderColor = Border,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                    cursorColor = Accent
                )
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(value) }) {
                Text("Add", color = Accent)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = TextSecondary)
            }
        }
    )
}
