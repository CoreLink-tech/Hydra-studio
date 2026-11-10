package com.corelink.frame.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.corelink.frame.data.local.entity.ProjectEntity
import com.corelink.frame.ui.theme.Accent
import com.corelink.frame.ui.theme.AccentSoft
import com.corelink.frame.ui.theme.Background
import com.corelink.frame.ui.theme.Border
import com.corelink.frame.ui.theme.Panel
import com.corelink.frame.ui.theme.Panel2
import com.corelink.frame.ui.theme.TextMuted
import com.corelink.frame.ui.theme.TextPrimary
import com.corelink.frame.ui.theme.TextSecondary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HomeScreen(
    onNavigateToCreate: () -> Unit,
    onNavigateToProjects: () -> Unit,
    onNavigateToModels: () -> Unit,
    onNavigateToHardware: () -> Unit,
    onOpenProject: (String) -> Unit = {},
    viewModel: HomeViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(Background),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .border(1.dp, TextMuted, RoundedCornerShape(7.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("F", color = AccentSoft, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("FRAME", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 18.sp, letterSpacing = 2.sp)
                    Text("OFFLINE FILM STUDIO", color = TextMuted, fontSize = 9.sp, letterSpacing = 1.5.sp)
                }
            }
        }

        item {
            Text("Welcome", color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 22.sp)
            Spacer(Modifier.height(4.dp))
            Text(
                "Create, organise and export films entirely offline.",
                color = TextSecondary,
                fontSize = 14.sp
            )
        }

        item {
            SectionLabel("CREATE")
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CreateCard(
                    title = "Text to Video",
                    subtitle = "Describe a scene",
                    icon = Icons.Outlined.TextFields,
                    onClick = onNavigateToCreate,
                    modifier = Modifier.weight(1f)
                )
                CreateCard(
                    title = "Image to Video",
                    subtitle = "Animate a still",
                    icon = Icons.Outlined.Image,
                    onClick = onNavigateToCreate,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(Panel)
                    .border(1.dp, Border, RoundedCornerShape(10.dp))
                    .clickable(onClick = onNavigateToHardware)
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(Accent)
                )
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Device Profile", color = TextPrimary, fontWeight = FontWeight.Medium, fontSize = 14.sp)
                    Text(
                        state.profileName?.let { "Current: $it" } ?: "Tap to assess hardware compatibility",
                        color = TextSecondary,
                        fontSize = 12.sp
                    )
                }
            }
        }

        item {
            SectionHeader("Recent Projects", "View all", onNavigateToProjects)
            if (state.projects.isEmpty()) {
                EmptyCard("No projects yet. Create your first film.")
            }
        }

        items(state.projects, key = { it.id }) { project ->
            ProjectRow(project = project, onClick = { onOpenProject(project.id) })
        }

        item {
            SectionHeader("AI Models", "Manage", onNavigateToModels)
            EmptyCard(
                if (state.installedModelCount == 0)
                    "No models installed · ${state.catalogueModelCount} in catalogue (research)"
                else
                    "${state.installedModelCount} installed"
            )
        }

        item {
            if (state.recentTasks.isNotEmpty()) {
                SectionLabel("RECENT ACTIVITY")
                Spacer(Modifier.height(10.dp))
                state.recentTasks.take(3).forEach { task ->
                    ActivityRow(
                        title = task.mode.replace('_', ' '),
                        subtitle = "${task.status} · ${task.stage.ifBlank { "—" }}",
                        tag = task.status
                    )
                    Spacer(Modifier.height(8.dp))
                }
            }
        }

        item { Spacer(Modifier.height(16.dp)) }
    }
}

@Composable
private fun CreateCard(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(Panel2)
            .border(1.dp, Border, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(16.dp)
    ) {
        Icon(icon, contentDescription = null, tint = AccentSoft, modifier = Modifier.size(22.dp))
        Spacer(Modifier.height(12.dp))
        Text(title, color = TextPrimary, fontWeight = FontWeight.Medium, fontSize = 14.sp)
        Spacer(Modifier.height(2.dp))
        Text(subtitle, color = TextSecondary, fontSize = 12.sp)
    }
}

@Composable
private fun ProjectRow(project: ProjectEntity, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Panel)
            .border(1.dp, Border, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .background(Panel2, RoundedCornerShape(8.dp))
                .border(1.dp, Border, RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Outlined.Movie, null, tint = AccentSoft)
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(project.name, color = TextPrimary, fontWeight = FontWeight.Medium, fontSize = 14.sp)
            Text(
                "${project.sceneCount} scenes · ${project.clipCount} clips · ${formatDate(project.updatedAt)}",
                color = TextSecondary,
                fontSize = 12.sp
            )
        }
    }
}

@Composable
private fun ActivityRow(title: String, subtitle: String, tag: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Panel, RoundedCornerShape(10.dp))
            .border(1.dp, Border, RoundedCornerShape(10.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            Text(subtitle, color = TextSecondary, fontSize = 12.sp)
        }
        Text(tag, color = TextMuted, fontSize = 10.sp)
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, color = TextMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp)
}

@Composable
private fun SectionHeader(title: String, action: String, onAction: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        SectionLabel(title.uppercase())
        Text(action, color = Accent, fontSize = 12.sp, modifier = Modifier.clickable(onClick = onAction))
    }
    Spacer(Modifier.height(10.dp))
}

@Composable
private fun EmptyCard(message: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Panel)
            .border(1.dp, Border, RoundedCornerShape(10.dp))
            .padding(20.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(message, color = TextSecondary, fontSize = 13.sp)
    }
}

private fun formatDate(millis: Long): String {
    val sdf = SimpleDateFormat("dd MMM", Locale.getDefault())
    return sdf.format(Date(millis))
}
