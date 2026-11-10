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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
fun HomeScreen(
    onNavigateToCreate: () -> Unit,
    onNavigateToProjects: () -> Unit,
    onNavigateToModels: () -> Unit,
    onNavigateToHardware: () -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(Background),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        item {
            // Brand header
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .border(1.dp, TextMuted, RoundedCornerShape(7.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "F",
                        color = AccentSoft,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        text = "FRAME",
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        letterSpacing = 2.sp
                    )
                    Text(
                        text = "OFFLINE FILM STUDIO",
                        color = TextMuted,
                        fontSize = 9.sp,
                        letterSpacing = 1.5.sp
                    )
                }
            }
        }

        item {
            Text(
                text = "Welcome",
                color = TextPrimary,
                fontWeight = FontWeight.SemiBold,
                fontSize = 22.sp
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Create, organise and export films entirely offline.",
                color = TextSecondary,
                fontSize = 14.sp
            )
        }

        item {
            // Generation shortcuts
            Text(
                text = "CREATE",
                color = TextMuted,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.4.sp
            )
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
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
            // Device profile teaser
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
                    Text(
                        text = "Device Profile",
                        color = TextPrimary,
                        fontWeight = FontWeight.Medium,
                        fontSize = 14.sp
                    )
                    Text(
                        text = "Tap to assess hardware compatibility",
                        color = TextSecondary,
                        fontSize = 12.sp
                    )
                }
            }
        }

        item {
            // Placeholder sections
            SectionHeader(title = "Recent Projects", action = "View all", onAction = onNavigateToProjects)
            PlaceholderCard("No projects yet. Create your first film.")
        }

        item {
            SectionHeader(title = "Installed Models", action = "Manage", onAction = onNavigateToModels)
            PlaceholderCard("No models installed. Download a model to begin generation.")
        }
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
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = AccentSoft,
            modifier = Modifier.size(22.dp)
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = title,
            color = TextPrimary,
            fontWeight = FontWeight.Medium,
            fontSize = 14.sp
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = subtitle,
            color = TextSecondary,
            fontSize = 12.sp
        )
    }
}

@Composable
private fun SectionHeader(
    title: String,
    action: String,
    onAction: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title.uppercase(),
            color = TextMuted,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.4.sp
        )
        Text(
            text = action,
            color = Accent,
            fontSize = 12.sp,
            modifier = Modifier.clickable(onClick = onAction)
        )
    }
    Spacer(Modifier.height(10.dp))
}

@Composable
private fun PlaceholderCard(message: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Panel)
            .border(1.dp, Border, RoundedCornerShape(10.dp))
            .padding(20.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = message,
            color = TextSecondary,
            fontSize = 13.sp
        )
    }
}
