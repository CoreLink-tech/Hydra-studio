package com.corelink.frame.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.corelink.frame.core.files.FrameFileManager
import com.corelink.frame.data.preferences.HardwarePreferences
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

data class SettingsUiState(
    val storageBytes: Long = 0L,
    val profileName: String? = null,
    val message: String? = null
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val fileManager: FrameFileManager,
    hardwarePreferences: HardwarePreferences
) : ViewModel() {

    private val tick = MutableStateFlow(0)

    val uiState: StateFlow<SettingsUiState> = combine(
        hardwarePreferences.lastProfile,
        tick
    ) { profile, _ ->
        SettingsUiState(
            storageBytes = fileManager.usedBytes(),
            profileName = profile?.displayName
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SettingsUiState())

    fun clearCache() {
        viewModelScope.launch {
            fileManager.clearCache()
            tick.update { it + 1 }
        }
    }

    fun refresh() {
        tick.update { it + 1 }
    }
}
