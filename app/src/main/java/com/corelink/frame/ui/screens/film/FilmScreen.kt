package com.corelink.frame.ui.screens.film

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.ViewAgenda
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import com.corelink.frame.data.local.entity.ClipEntity
import com.corelink.frame.data.local.entity.SceneEntity
import com.corelink.frame.ui.theme.Accent
import com.corelink.frame.ui.theme.AccentSoft
import com.corelink.frame.ui.theme.Background
import com.corelink.frame.ui.theme.Border
import com.corelink.frame.ui.theme.Panel
import com.corelink.frame.ui.theme.Panel2
import com.corelink.frame.ui.theme.TextMuted
import com.corelink.frame.ui.theme.TextPrimary
import com.corelink.frame.ui.theme.TextSecondary

@Composable
fun FilmScreen(
    onBack: () -> Unit,
    onOpenEditor: (String) -> Unit = {},
    viewModel: FilmViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val project = state.project

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
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = TextPrimary)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    project?.name ?: "Film",
                    color = TextPrimary,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 18.sp
                )
                Text(
                    "${state.scenes.size} scenes · ${state.clips.size} clips",
                    color = TextSecondary,
                    fontSize = 12.sp
                )
            }
            IconButton(onClick = { viewModel.addScene() }) {
                Icon(Icons.Outlined.Add, "Add scene", tint = AccentSoft)
            }
        }

        LazyColumn(
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text(
                    "SCENES",
                    color = TextMuted,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.2.sp
                )
            }

            if (state.scenes.isEmpty()) {
                item {
                    EmptyBlock("No scenes. Tap + to add Scene 1.")
                }
            }

            items(state.scenes, key = { it.id }) { scene ->
                SceneCard(scene = scene, clipCount = state.clips.count { it.sceneId == scene.id })
            }

            item {
                Spacer(Modifier.height(8.dp))
                Text(
                    "CLIPS IN PROJECT",
                    color = TextMuted,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.2.sp
                )
            }

            if (state.clips.isEmpty()) {
                item {
                    EmptyBlock("No clips yet. Generate from Create, then attach them here.")
                }
            }

            items(state.clips, key = { it.id }) { clip ->
                ClipRow(clip)
            }

            item {
                Spacer(Modifier.height(12.dp))
                if (project != null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Panel2, RoundedCornerShape(10.dp))
                            .border(1.dp, Border, RoundedCornerShape(10.dp))
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Outlined.ViewAgenda, null, tint = AccentSoft)
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Open editor", color = TextPrimary, fontWeight = FontWeight.Medium, fontSize = 14.sp)
                            Text("Timeline editing arrives in Milestone 6", color = TextMuted, fontSize = 12.sp)
                        }
                        Text(
                            "Soon",
                            color = Accent,
                            fontSize = 12.sp
                        )
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun SceneCard(scene: SceneEntity, clipCount: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Panel, RoundedCornerShape(10.dp))
            .border(1.dp, Border, RoundedCornerShape(10.dp))
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(Panel2, RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text("${scene.sortOrder + 1}", color = AccentSoft, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(scene.name, color = TextPrimary, fontWeight = FontWeight.Medium, fontSize = 14.sp)
            Text(
                "$clipCount clips · ${(scene.durationMs / 1000f).let { String.format("%.1fs", it) }}",
                color = TextSecondary,
                fontSize = 12.sp
            )
        }
    }
}

@Composable
private fun ClipRow(clip: ClipEntity) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Panel, RoundedCornerShape(10.dp))
            .border(1.dp, Border, RoundedCornerShape(10.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Outlined.Movie, null, tint = AccentSoft, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(clip.name, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            Text(
                "${clip.width}×${clip.height} · ${clip.durationMs / 1000f}s",
                color = TextMuted,
                fontSize = 11.sp
            )
        }
    }
}

@Composable
private fun EmptyBlock(message: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Panel, RoundedCornerShape(10.dp))
            .border(1.dp, Border, RoundedCornerShape(10.dp))
            .padding(20.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(message, color = TextSecondary, fontSize = 13.sp)
    }
}
