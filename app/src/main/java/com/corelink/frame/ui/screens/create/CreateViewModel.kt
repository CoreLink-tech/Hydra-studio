package com.corelink.frame.ui.screens.create

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.corelink.frame.ai.generation.GenerationQueue
import com.corelink.frame.ai.models.ModelCatalogue
import com.corelink.frame.ai.models.ModelMetadata
import com.corelink.frame.ai.models.ModelStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class CreateMode { TEXT, IMAGE }

data class CreateUiState(
    val mode: CreateMode = CreateMode.IMAGE,
    val prompt: String = "",
    val motionPrompt: String = "",
    val negativePrompt: String = "",
    val durationSeconds: Int = 3,
    val selectedModelId: String = ModelCatalogue.all.firstOrNull()?.id ?: "",
    val inputImagePath: String? = null,
    val inputImageName: String? = null,
    val isSubmitting: Boolean = false,
    val lastTaskId: String? = null,
    val error: String? = null
)

@HiltViewModel
class CreateViewModel @Inject constructor(
    private val generationQueue: GenerationQueue
) : ViewModel() {

    private val _uiState = MutableStateFlow(CreateUiState())
    val uiState: StateFlow<CreateUiState> = _uiState.asStateFlow()

    val models: List<ModelMetadata> = ModelCatalogue.all

    fun setMode(mode: CreateMode) {
        _uiState.update { it.copy(mode = mode, error = null) }
    }

    fun setPrompt(value: String) = _uiState.update { it.copy(prompt = value) }
    fun setMotionPrompt(value: String) = _uiState.update { it.copy(motionPrompt = value) }
    fun setNegativePrompt(value: String) = _uiState.update { it.copy(negativePrompt = value) }
    fun setDuration(seconds: Int) = _uiState.update { it.copy(durationSeconds = seconds) }
    fun setModel(id: String) = _uiState.update { it.copy(selectedModelId = id) }

    fun setInputImage(path: String, name: String) {
        _uiState.update {
            it.copy(inputImagePath = path, inputImageName = name, error = null)
        }
    }

    fun clearError() = _uiState.update { it.copy(error = null) }

    /**
     * Enqueue generation. Will not invent success — the queue reports real status.
     */
    fun submit(onEnqueued: (String) -> Unit) {
        val state = _uiState.value
        viewModelScope.launch {
            _uiState.update { it.copy(isSubmitting = true, error = null) }

            when (state.mode) {
                CreateMode.TEXT -> {
                    _uiState.update {
                        it.copy(
                            isSubmitting = false,
                            error = "Text-to-video is not available until a validated T2V pipeline is installed (Milestone 9)."
                        )
                    }
                    return@launch
                }
                CreateMode.IMAGE -> {
                    if (state.inputImagePath.isNullOrBlank()) {
                        _uiState.update {
                            it.copy(isSubmitting = false, error = "Select an image first.")
                        }
                        return@launch
                    }
                    val model = ModelCatalogue.byId(state.selectedModelId)
                    if (model == null || model.status == ModelStatus.INCOMPATIBLE) {
                        _uiState.update {
                            it.copy(isSubmitting = false, error = "Selected model is not available.")
                        }
                        return@launch
                    }

                    val frames = (state.durationSeconds * 8).coerceIn(8, 48)
                    val taskId = generationQueue.enqueueImageToVideo(
                        modelId = state.selectedModelId,
                        motionPrompt = state.motionPrompt,
                        inputImagePath = state.inputImagePath!!,
                        targetFrames = frames,
                        targetWidth = 512,
                        targetHeight = 512
                    )
                    _uiState.update {
                        it.copy(isSubmitting = false, lastTaskId = taskId)
                    }
                    onEnqueued(taskId)
                }
            }
        }
    }
}
