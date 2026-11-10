package com.corelink.frame.ui.screens.film

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.corelink.frame.data.local.dao.ClipDao
import com.corelink.frame.data.local.entity.ClipEntity
import com.corelink.frame.data.local.entity.ProjectEntity
import com.corelink.frame.data.local.entity.SceneEntity
import com.corelink.frame.data.repository.ProjectRepository
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

data class FilmUiState(
    val project: ProjectEntity? = null,
    val scenes: List<SceneEntity> = emptyList(),
    val clips: List<ClipEntity> = emptyList(),
    val isAddingScene: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class FilmViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val projectRepository: ProjectRepository,
    private val clipDao: ClipDao
) : ViewModel() {

    private val projectId: String = checkNotNull(savedStateHandle["projectId"])

    private val _uiState = MutableStateFlow(FilmUiState())
    val uiState: StateFlow<FilmUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val project = projectRepository.getProject(projectId)
            _uiState.update { it.copy(project = project) }
        }
        viewModelScope.launch {
            combine(
                projectRepository.observeScenes(projectId),
                clipDao.observeByProject(projectId)
            ) { scenes, clips ->
                scenes to clips
            }.collect { (scenes, clips) ->
                _uiState.update { it.copy(scenes = scenes, clips = clips) }
            }
        }
    }

    fun addScene(name: String = "") {
        viewModelScope.launch {
            _uiState.update { it.copy(isAddingScene = true, error = null) }
            try {
                projectRepository.createScene(projectId, name)
                val project = projectRepository.getProject(projectId)
                _uiState.update { it.copy(isAddingScene = false, project = project) }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isAddingScene = false, error = e.message ?: "Could not add scene")
                }
            }
        }
    }

    fun clearError() = _uiState.update { it.copy(error = null) }
}
