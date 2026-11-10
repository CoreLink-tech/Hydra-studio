package com.corelink.frame.ui.screens.models

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.corelink.frame.ai.download.ModelDownloadManager
import com.corelink.frame.ai.models.InstallState
import com.corelink.frame.ai.models.ModelCatalogue
import com.corelink.frame.ai.models.ModelInstallState
import com.corelink.frame.ai.models.ModelMetadata
import com.corelink.frame.ai.models.ModelStatus
import com.corelink.frame.core.files.FrameFileManager
import com.corelink.frame.data.preferences.HardwarePreferences
import com.corelink.frame.hardware.HardwareProfile
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ModelRow(
    val meta: ModelMetadata,
    val install: ModelInstallState,
    val recommended: Boolean,
    val warning: String?
)

data class ModelsUiState(
    val rows: List<ModelRow> = emptyList(),
    val storageUsedBytes: Long = 0L,
    val profileName: String? = null,
    val message: String? = null
)

@HiltViewModel
class ModelsViewModel @Inject constructor(
    private val downloadManager: ModelDownloadManager,
    private val fileManager: FrameFileManager,
    hardwarePreferences: HardwarePreferences
) : ViewModel() {

    private val refresh = MutableStateFlow(0)

    val uiState: StateFlow<ModelsUiState> = combine(
        downloadManager.installStates,
        hardwarePreferences.lastProfile,
        refresh
    ) { installs, profile, _ ->
        val ram = profile?.minRamGb?.takeIf { it > 0f } ?: 4f
        val rows = ModelCatalogue.all.map { meta ->
            val install = installs[meta.id] ?: ModelInstallState(meta.id, InstallState.NOT_PRESENT)
            val recommended = meta.minRamGbRecommended <= ram + 0.3f &&
                meta.status != ModelStatus.INCOMPATIBLE
            val warning = when {
                meta.status == ModelStatus.INCOMPATIBLE -> "Marked incompatible"
                meta.minRamGbRecommended > ram + 0.5f ->
                    "Needs ~${meta.minRamGbRecommended} GB RAM (device ~${"%.1f".format(ram)} GB)"
                meta.status == ModelStatus.RESEARCH ->
                    "Research only — not validated on device"
                meta.status == ModelStatus.EXPERIMENTAL ->
                    "Experimental — limited device testing"
                else -> null
            }
            ModelRow(meta, install, recommended, warning)
        }
        ModelsUiState(
            rows = rows,
            storageUsedBytes = fileManager.usedBytes(),
            profileName = profile?.displayName
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ModelsUiState())

    init {
        refreshLocal()
    }

    fun refreshLocal() {
        viewModelScope.launch {
            ModelCatalogue.all.forEach { downloadManager.refreshLocalState(it) }
            refresh.update { it + 1 }
        }
    }

    fun requestDownload(modelId: String) {
        viewModelScope.launch {
            val meta = ModelCatalogue.byId(modelId) ?: return@launch
            val result = downloadManager.download(meta, meta.sourceUrl ?: "")
            val msg = result.exceptionOrNull()?.message
                ?: "Download started"
            refresh.update { it + 1 }
            // surface via re-collect; message optional
        }
    }

    fun clearModelCache() {
        viewModelScope.launch {
            fileManager.clearCache()
            refreshLocal()
        }
    }
}
