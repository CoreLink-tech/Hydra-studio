package com.corelink.frame.ui.screens.editor

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.corelink.frame.data.local.dao.ClipDao
import com.corelink.frame.data.local.entity.ClipEntity
import com.corelink.frame.data.local.entity.ProjectEntity
import com.corelink.frame.data.local.entity.TimelineItemEntity
import com.corelink.frame.data.local.entity.TimelineTrackEntity
import com.corelink.frame.data.repository.ProjectRepository
import com.corelink.frame.editor.timeline.TimelineRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

data class EditorUiState(
    val project: ProjectEntity? = null,
    val tracks: List<TimelineTrackEntity> = emptyList(),
    val items: List<TimelineItemEntity> = emptyList(),
    val clips: List<ClipEntity> = emptyList(),
    val selectedItemId: String? = null,
    val playheadMs: Long = 0L,
    val durationMs: Long = 0L,
    val isPlaying: Boolean = false,
    val message: String? = null
)

@HiltViewModel
class EditorViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val projectRepository: ProjectRepository,
    private val timelineRepository: TimelineRepository,
    private val clipDao: ClipDao
) : ViewModel() {

    private val projectId: String = checkNotNull(savedStateHandle["projectId"])

    private val _uiState = MutableStateFlow(EditorUiState())
    val uiState: StateFlow<EditorUiState> = _uiState.asStateFlow()

    private var playJob: Job? = null

    init {
        viewModelScope.launch {
            timelineRepository.ensureDefaultTracks(projectId)
            val project = projectRepository.getProject(projectId)
            _uiState.update { it.copy(project = project) }
        }
        viewModelScope.launch {
            combine(
                timelineRepository.observeTracks(projectId),
                timelineRepository.observeItems(projectId),
                clipDao.observeByProject(projectId)
            ) { tracks, items, clips ->
                Triple(tracks, items, clips)
            }.collect { (tracks, items, clips) ->
                val duration = items.maxOfOrNull { it.startMs + it.durationMs } ?: 0L
                _uiState.update {
                    it.copy(
                        tracks = tracks,
                        items = items,
                        clips = clips,
                        durationMs = duration.coerceAtLeast(1000L)
                    )
                }
            }
        }
    }

    fun selectItem(id: String?) {
        _uiState.update { it.copy(selectedItemId = id) }
    }

    fun seek(ms: Long) {
        val duration = _uiState.value.durationMs
        _uiState.update { it.copy(playheadMs = ms.coerceIn(0L, duration)) }
    }

    fun togglePlay() {
        if (_uiState.value.isPlaying) pause() else play()
    }

    fun play() {
        if (_uiState.value.isPlaying) return
        _uiState.update { it.copy(isPlaying = true) }
        playJob = viewModelScope.launch {
            while (isActive) {
                delay(50)
                val state = _uiState.value
                val next = state.playheadMs + 50
                if (next >= state.durationMs) {
                    _uiState.update { it.copy(playheadMs = state.durationMs, isPlaying = false) }
                    break
                }
                _uiState.update { it.copy(playheadMs = next) }
            }
        }
    }

    fun pause() {
        playJob?.cancel()
        playJob = null
        _uiState.update { it.copy(isPlaying = false) }
    }

    fun addClipToTimeline(clipId: String) {
        viewModelScope.launch {
            val clip = _uiState.value.clips.find { it.id == clipId }
                ?: clipDao.getById(clipId)
                ?: run {
                    _uiState.update { it.copy(message = "Clip not found") }
                    return@launch
                }
            // If duration is 0 (not yet generated), use a placeholder length for layout
            val usable = if (clip.durationMs > 0) clip else clip.copy(durationMs = 3000L)
            timelineRepository.appendClipToVideoTrack(projectId, usable)
            _uiState.update { it.copy(message = "Added “${clip.name}” to timeline") }
        }
    }

    fun deleteSelected() {
        val id = _uiState.value.selectedItemId ?: return
        viewModelScope.launch {
            timelineRepository.deleteItem(id)
            _uiState.update { it.copy(selectedItemId = null, message = "Item removed") }
        }
    }

    fun splitAtPlayhead() {
        val id = _uiState.value.selectedItemId ?: return
        val playhead = _uiState.value.playheadMs
        viewModelScope.launch {
            timelineRepository.splitItem(id, playhead)
            _uiState.update { it.copy(message = "Split at playhead") }
        }
    }

    fun trimSelected(inMs: Long, outMs: Long) {
        val id = _uiState.value.selectedItemId ?: return
        viewModelScope.launch {
            timelineRepository.trimItem(id, inMs, outMs)
            _uiState.update { it.copy(message = "Trim applied") }
        }
    }


    fun addTitleAtPlayhead(text: String) {
        if (text.isBlank()) return
        viewModelScope.launch {
            timelineRepository.addTitle(
                projectId = projectId,
                text = text.trim(),
                startMs = _uiState.value.playheadMs,
                durationMs = 3000L
            )
            _uiState.update { it.copy(message = "Title added") }
        }
    }

    fun addSubtitleAtPlayhead(text: String) {
        if (text.isBlank()) return
        viewModelScope.launch {
            timelineRepository.addTitle(
                projectId = projectId,
                text = text.trim(),
                startMs = _uiState.value.playheadMs,
                durationMs = 2500L
            )
            _uiState.update { it.copy(message = "Subtitle added") }
        }
    }

    fun setSelectedVolume(volume: Float) {
        val id = _uiState.value.selectedItemId ?: return
        viewModelScope.launch {
            timelineRepository.setVolume(id, volume)
            _uiState.update { it.copy(message = "Volume ${(volume * 100).toInt()}%") }
        }
    }

    fun applyFadeOnSelected(fadeInMs: Long = 500, fadeOutMs: Long = 500) {
        val id = _uiState.value.selectedItemId ?: return
        viewModelScope.launch {
            timelineRepository.setFadeHints(id, fadeInMs, fadeOutMs)
            _uiState.update { it.copy(message = "Fade in/out applied") }
        }
    }

    fun registerAudioAssetAndAdd(
        name: String,
        path: String,
        durationMs: Long
    ) {
        viewModelScope.launch {
            timelineRepository.addAudio(
                projectId = projectId,
                name = name,
                mediaPath = path,
                durationMs = durationMs,
                startMs = _uiState.value.playheadMs
            )
            _uiState.update { it.copy(message = "Audio added to timeline") }
        }
    }

    fun clearMessage() = _uiState.update { it.copy(message = null) }

    override fun onCleared() {
        pause()
        super.onCleared()
    }
}
