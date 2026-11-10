package com.corelink.frame.ui.screens.hardware

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.corelink.frame.data.preferences.HardwarePreferences
import com.corelink.frame.hardware.DeviceProfiler
import com.corelink.frame.hardware.HardwareInfo
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class HardwareUiState(
    val isLoading: Boolean = true,
    val info: HardwareInfo? = null,
    val error: String? = null,
    val lastSavedProfileName: String? = null
)

@HiltViewModel
class HardwareViewModel @Inject constructor(
    private val deviceProfiler: DeviceProfiler,
    private val hardwarePreferences: HardwarePreferences,
    @ApplicationContext private val appContext: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(HardwareUiState())
    val uiState: StateFlow<HardwareUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val info = withContext(Dispatchers.Default) {
                    deviceProfiler.assess(appContext)
                }
                hardwarePreferences.saveAssessment(
                    profile = info.profile,
                    totalRamBytes = info.totalRamBytes,
                    rationale = info.profileRationale,
                    assessedAt = info.assessedAtMillis
                )
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        info = info,
                        error = null,
                        lastSavedProfileName = info.profile.displayName
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = e.message ?: "Hardware assessment failed"
                    )
                }
            }
        }
    }
}
