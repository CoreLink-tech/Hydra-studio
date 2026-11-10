package com.corelink.frame.ui.screens.hardware

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.StatFs
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.corelink.frame.ui.theme.AccentSoft
import com.corelink.frame.ui.theme.Background
import com.corelink.frame.ui.theme.Border
import com.corelink.frame.ui.theme.Panel
import com.corelink.frame.ui.theme.TextMuted
import com.corelink.frame.ui.theme.TextPrimary
import com.corelink.frame.ui.theme.TextSecondary
import java.util.Locale

@Composable
fun HardwareScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val info = remember { collectDeviceInfo(context) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
    ) {
        // Top bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 12.dp),
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
                fontSize = 18.sp
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Actual device information collected at runtime. No fabricated values.",
                color = TextSecondary,
                fontSize = 13.sp
            )

            Spacer(Modifier.height(8.dp))

            InfoCard("Device", listOf(
                "Manufacturer" to info.manufacturer,
                "Model" to info.model,
                "Device" to info.device,
                "Product" to info.product
            ))

            InfoCard("Android", listOf(
                "Version" to info.androidVersion,
                "SDK" to info.sdkInt.toString(),
                "Security Patch" to (info.securityPatch ?: "Unavailable")
            ))

            InfoCard("Memory", listOf(
                "Total RAM" to info.totalRam,
                "Available RAM" to info.availableRam,
                "Low Memory" to if (info.lowMemory) "Yes" else "No"
            ))

            InfoCard("CPU", listOf(
                "Architecture" to info.cpuAbi,
                "Supported ABIs" to info.supportedAbis
            ))

            InfoCard("Storage", listOf(
                "Internal Free" to info.internalFree,
                "Internal Total" to info.internalTotal
            ))

            InfoCard("Profile (preliminary)", listOf(
                "Suggested Profile" to info.suggestedProfile
            ))
        }
    }
}

@Composable
private fun InfoCard(
    title: String,
    rows: List<Pair<String, String>>
) {
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
        Spacer(Modifier.height(12.dp))
        rows.forEach { (label, value) ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(text = label, color = TextSecondary, fontSize = 13.sp)
                Text(text = value, color = TextPrimary, fontSize = 13.sp)
            }
        }
    }
}

private data class DeviceInfo(
    val manufacturer: String,
    val model: String,
    val device: String,
    val product: String,
    val androidVersion: String,
    val sdkInt: Int,
    val securityPatch: String?,
    val totalRam: String,
    val availableRam: String,
    val lowMemory: Boolean,
    val cpuAbi: String,
    val supportedAbis: String,
    val internalFree: String,
    val internalTotal: String,
    val suggestedProfile: String
)

private fun collectDeviceInfo(context: Context): DeviceInfo {
    val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    val memInfo = ActivityManager.MemoryInfo()
    am.getMemoryInfo(memInfo)

    val totalBytes = memInfo.totalMem
    val availBytes = memInfo.availMem
    val totalGb = totalBytes / (1024.0 * 1024.0 * 1024.0)
    val availGb = availBytes / (1024.0 * 1024.0 * 1024.0)

    val stat = StatFs(Environment.getDataDirectory().path)
    val freeBytes = stat.availableBlocksLong * stat.blockSizeLong
    val totalStorage = stat.blockCountLong * stat.blockSizeLong

    val profile = when {
        totalGb < 3.5 -> "Low-memory (very conservative)"
        totalGb < 5.5 -> "Balanced (4 GB class)"
        totalGb < 7.5 -> "Performance (6 GB class)"
        else -> "High-performance (8 GB+)"
    }

    return DeviceInfo(
        manufacturer = Build.MANUFACTURER.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() },
        model = Build.MODEL,
        device = Build.DEVICE,
        product = Build.PRODUCT,
        androidVersion = Build.VERSION.RELEASE,
        sdkInt = Build.VERSION.SDK_INT,
        securityPatch = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) Build.VERSION.SECURITY_PATCH else null,
        totalRam = String.format(Locale.US, "%.1f GB", totalGb),
        availableRam = String.format(Locale.US, "%.1f GB", availGb),
        lowMemory = memInfo.lowMemory,
        cpuAbi = Build.SUPPORTED_ABIS.firstOrNull() ?: "unknown",
        supportedAbis = Build.SUPPORTED_ABIS.joinToString(", "),
        internalFree = formatBytes(freeBytes),
        internalTotal = formatBytes(totalStorage),
        suggestedProfile = profile
    )
}

private fun formatBytes(bytes: Long): String {
    val gb = bytes / (1024.0 * 1024.0 * 1024.0)
    return String.format(Locale.US, "%.1f GB", gb)
}
