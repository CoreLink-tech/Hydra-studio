package com.corelink.frame.ui.screens.hardware

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import com.corelink.frame.hardware.HardwareInfo
import com.corelink.frame.hardware.HardwareProfile
import com.corelink.frame.ui.theme.Accent
import com.corelink.frame.ui.theme.AccentSoft
import com.corelink.frame.ui.theme.Background
import com.corelink.frame.ui.theme.Border
import com.corelink.frame.ui.theme.Danger
import com.corelink.frame.ui.theme.Panel
import com.corelink.frame.ui.theme.Panel2
import com.corelink.frame.ui.theme.Success
import com.corelink.frame.ui.theme.TextMuted
import com.corelink.frame.ui.theme.TextPrimary
import com.corelink.frame.ui.theme.TextSecondary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HardwareScreen(
    onBack: () -> Unit,
    viewModel: HardwareViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()

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
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = TextPrimary
                )
            }
            Text(
                text = "Device Profile",
                color = TextPrimary,
                fontWeight = FontWeight.SemiBold,
                fontSize = 18.sp,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = { viewModel.refresh() }) {
                Icon(
                    imageVector = Icons.Outlined.Refresh,
                    contentDescription = "Refresh",
                    tint = TextSecondary
                )
            }
        }

        when {
            state.isLoading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = Accent, strokeWidth = 2.dp)
                        Spacer(Modifier.height(16.dp))
                        Text("Assessing device…", color = TextSecondary, fontSize = 14.sp)
                    }
                }
            }
            state.error != null -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("Assessment failed", color = Danger, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(8.dp))
                    Text(state.error ?: "", color = TextSecondary, fontSize = 13.sp)
                }
            }
            state.info != null -> {
                HardwareContent(info = state.info!!)
            }
        }
    }
}

@Composable
private fun HardwareContent(info: HardwareInfo) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            text = "All values are collected from Android system APIs at runtime. Nothing is fabricated.",
            color = TextMuted,
            fontSize = 12.sp
        )

        ProfileSummaryCard(info)
        GuidanceCard(info.profile)

        InfoSection("Device", listOf(
            "Manufacturer" to info.manufacturer,
            "Brand" to info.brand,
            "Model" to info.model,
            "Device codename" to info.device
        ))

        InfoSection("Android", listOf(
            "Version" to info.androidVersion,
            "SDK level" to info.sdkInt.toString(),
            "Security patch" to (info.securityPatch ?: "Unavailable")
        ))

        InfoSection("Memory", listOf(
            "Total RAM" to formatGb(info.totalRamGb),
            "Available now" to formatGb(info.availableRamGb),
            "Low-memory flag" to if (info.isLowMemory) "Yes" else "No",
            "Memory pressure" to String.format(Locale.US, "%.0f%%", info.memoryPressurePercent)
        ))

        InfoSection("CPU", listOf(
            "Primary ABI" to info.primaryAbi,
            "Supported ABIs" to info.supportedAbis.joinToString(", "),
            "Reported cores" to (info.cpuCoreCount?.toString() ?: "Unavailable")
        ))

        InfoSection("Storage", buildList {
            add("Internal free" to formatGb(info.internalFreeGb))
            add("Internal total" to formatGb(info.internalTotalGb))
            if (info.externalFreeBytes != null && info.externalTotalBytes != null) {
                add("External free" to formatGb(info.externalFreeBytes / BYTES_PER_GB))
                add("External total" to formatGb(info.externalTotalBytes / BYTES_PER_GB))
            } else {
                add("External storage" to "Not available / not mounted")
            }
        })

        InfoSection("Battery & Thermal", listOf(
            "Battery" to (info.batteryPercent?.let { "$it%" } ?: "Unavailable"),
            "Status" to info.batteryStatusLabel,
            "Charging" to when (info.isCharging) {
                true -> "Yes"
                false -> "No"
                null -> "Unknown"
            },
            "Thermal" to info.thermalStatusLabel
        ))

        if (info.gpuRenderer != null) {
            InfoSection("GPU", listOf("Renderer" to info.gpuRenderer))
        } else {
            InfoSection("GPU", listOf("Renderer" to "Unavailable (no GL context)"))
        }

        Text(
            text = "Assessed ${formatTimestamp(info.assessedAtMillis)}",
            color = TextMuted,
            fontSize = 11.sp,
            modifier = Modifier.padding(top = 8.dp)
        )
    }
}

@Composable
private fun ProfileSummaryCard(info: HardwareInfo) {
    val profileColor = when (info.profile) {
        HardwareProfile.LOW_MEMORY -> Danger
        HardwareProfile.BALANCED -> Accent
        HardwareProfile.PERFORMANCE -> AccentSoft
        HardwareProfile.HIGH_PERFORMANCE -> Success
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Panel, RoundedCornerShape(12.dp))
            .border(1.dp, Border, RoundedCornerShape(12.dp))
            .padding(18.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(profileColor)
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = info.profile.displayName.uppercase(),
                color = profileColor,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                letterSpacing = 1.2.sp
            )
        }
        Spacer(Modifier.height(10.dp))
        Text(
            text = info.profile.description,
            color = TextSecondary,
            fontSize = 13.sp,
            lineHeight = 18.sp
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = info.profileRationale,
            color = TextMuted,
            fontSize = 12.sp,
            lineHeight = 16.sp
        )
    }
}

@Composable
private fun GuidanceCard(profile: HardwareProfile) {
    val lines = when (profile) {
        HardwareProfile.LOW_MEMORY -> listOf(
            "Only the smallest quantised models will be offered",
            "Clip length limited (3–5 s recommended)",
            "One generation task at a time",
            "Aggressive temporary file cleanup"
        )
        HardwareProfile.BALANCED -> listOf(
            "Conservative model set suitable for ~4 GB devices",
            "Short-to-medium clips (3–8 s)",
            "Sequential model component loading",
            "Monitor thermal and battery during long runs"
        )
        HardwareProfile.PERFORMANCE -> listOf(
            "Larger models may be offered after validation",
            "More flexible resolution and duration options",
            "Limited concurrent background work allowed"
        )
        HardwareProfile.HIGH_PERFORMANCE -> listOf(
            "Highest quality settings available when models support them",
            "Larger frame batches where safe",
            "Still respects thermal and battery limits"
        )
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Panel2, RoundedCornerShape(12.dp))
            .border(1.dp, Border, RoundedCornerShape(12.dp))
            .padding(16.dp)
    ) {
        Text(
            text = "GENERATION GUIDANCE",
            color = TextMuted,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.2.sp
        )
        Spacer(Modifier.height(10.dp))
        lines.forEach { line ->
            Row(modifier = Modifier.padding(vertical = 3.dp)) {
                Text("•  ", color = AccentSoft, fontSize = 13.sp)
                Text(line, color = TextPrimary, fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun InfoSection(title: String, rows: List<Pair<String, String>>) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Panel, RoundedCornerShape(10.dp))
            .border(1.dp, Border, RoundedCornerShape(10.dp))
            .padding(16.dp)
    ) {
        Text(
            text = title.uppercase(),
            color = TextMuted,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.2.sp
        )
        Spacer(Modifier.height(10.dp))
        rows.forEach { (label, value) ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(text = label, color = TextSecondary, fontSize = 13.sp)
                Text(
                    text = value,
                    color = TextPrimary,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(start = 12.dp)
                )
            }
        }
    }
}

private const val BYTES_PER_GB = 1024f * 1024f * 1024f

private fun formatGb(value: Float): String =
    String.format(Locale.US, "%.1f GB", value)

private fun formatTimestamp(millis: Long): String {
    val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
    return sdf.format(Date(millis))
}
