package com.corelink.frame.ui.screens.export

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.corelink.frame.data.local.dao.ExportTaskDao
import com.corelink.frame.data.local.entity.ExportTaskEntity
import com.corelink.frame.data.local.entity.ProjectEntity
import com.corelink.frame.data.repository.ProjectRepository
import com.corelink.frame.export.ExportConfig
import com.corelink.frame.export.ExportEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ExportUiState(
    val project: ProjectEntity? = null,
    val selectedPresetIndex: Int = 0,
    val fileName: String = "export_720p.mp4",
    val isExporting: Boolean = false,
    val activeTaskId: String? = null,
    val message: String? = null
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ExportViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val projectRepository: ProjectRepository,
    private val exportEngine: ExportEngine,
    private val exportTaskDao: ExportTaskDao
) : ViewModel() {

    private val projectId: String = checkNotNull(savedStateHandle["projectId"])

    private val _uiState = MutableStateFlow(ExportUiState())
    val uiState: StateFlow<ExportUiState> = _uiState.asStateFlow()

    val history: StateFlow<List<ExportTaskEntity>> = exportTaskDao.observeByProject(projectId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val activeTaskIdFlow = MutableStateFlow<String?>(null)

    val activeTask: StateFlow<ExportTaskEntity?> = activeTaskIdFlow
        .flatMapLatest { id ->
            if (id == null) flowOf(null) else exportTaskDao.observeById(id)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    init {
        viewModelScope.launch {
            val project = projectRepository.getProject(projectId)
            _uiState.update {
                it.copy(
                    project = project,
                    fileName = sanitizeFileName(project?.name ?: "export") + "_720p.mp4"
                )
            }
        }
    }

    fun selectPreset(index: Int) {
        val preset = ExportConfig.PRESETS.getOrElse(index) { ExportConfig.PRESETS.first() }
        _uiState.update {
            it.copy(
                selectedPresetIndex = index,
                fileName = sanitizeFileName(it.project?.name ?: "export") +
                    when (preset.height) {
                        720 -> "_720p.mp4"
                        480 -> "_480p.mp4"
                        else -> "_360p.mp4"
                    }
            )
        }
    }

    fun setFileName(name: String) {
        _uiState.update { it.copy(fileName = name) }
    }

    fun startExport() {
        val state = _uiState.value
        if (state.isExporting) return
        val preset = ExportConfig.PRESETS.getOrElse(state.selectedPresetIndex) {
            ExportConfig.PRESETS.first()
        }
        val config = preset.copy(fileName = state.fileName.ifBlank { preset.fileName })

        viewModelScope.launch {
            _uiState.update { it.copy(isExporting = true, message = null) }
            try {
                val taskId = exportEngine.startExport(projectId, config)
                activeTaskIdFlow.value = taskId
                _uiState.update { it.copy(activeTaskId = taskId, isExporting = false) }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isExporting = false,
                        message = e.message ?: "Could not start export"
                    )
                }
            }
        }
    }

    private fun sanitizeFileName(name: String): String =
        name.replace(Regex("[^A-Za-z0-9_\\- ]"), "").trim().ifBlank { "export" }
            .replace(" ", "_")
}
