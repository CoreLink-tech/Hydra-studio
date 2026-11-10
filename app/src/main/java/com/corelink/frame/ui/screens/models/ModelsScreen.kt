package com.corelink.frame.ui.screens.models

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.corelink.frame.ai.models.ModelCatalogue
import com.corelink.frame.ai.models.ModelMetadata
import com.corelink.frame.ai.models.ModelStatus
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
fun ModelsScreen() {
    val models = remember { ModelCatalogue.all }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Spacer(Modifier.height(20.dp))
            Text(
                text = "AI Models",
                color = TextPrimary,
                fontWeight = FontWeight.SemiBold,
                fontSize = 22.sp
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Catalogue of candidate models. Status remains RESEARCH until a real on-device PoC succeeds and files are validated.",
                color = TextSecondary,
                fontSize = 13.sp
            )
            Spacer(Modifier.height(8.dp))
        }

        items(models) { model ->
            ModelCard(model)
        }

        item {
            Spacer(Modifier.height(24.dp))
            Text(
                text = "Download and installation will be enabled after the first successful device proof-of-concept (Milestone 3).",
                color = TextMuted,
                fontSize = 12.sp
            )
            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun ModelCard(model: ModelMetadata) {
    val statusColor = when (model.status) {
        ModelStatus.VALIDATED -> Success
        ModelStatus.EXPERIMENTAL -> Accent
        ModelStatus.RESEARCH -> AccentSoft
        ModelStatus.INCOMPATIBLE -> Danger
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Panel, RoundedCornerShape(10.dp))
            .border(1.dp, Border, RoundedCornerShape(10.dp))
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = model.displayName,
                color = TextPrimary,
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp
            )
            Text(
                text = model.status.name,
                color = statusColor,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = model.description,
            color = TextSecondary,
            fontSize = 13.sp,
            lineHeight = 18.sp
        )
        Spacer(Modifier.height(10.dp))
        MetaRow("Params", model.parameterCountApprox)
        MetaRow("Modes", model.generationModes.joinToString { it.name.replace('_', ' ') })
        MetaRow("Min RAM", String.format("%.1f GB", model.minRamGbRecommended))
        MetaRow("Runtime", model.runtime.name.replace('_', ' '))
        MetaRow("Licence", model.licence)
        Spacer(Modifier.height(6.dp))
        Text(
            text = model.compatibilityNotes,
            color = TextMuted,
            fontSize = 12.sp,
            lineHeight = 16.sp
        )
    }
}

@Composable
private fun MetaRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = TextMuted, fontSize = 12.sp)
        Text(value, color = TextPrimary, fontSize = 12.sp)
    }
}
