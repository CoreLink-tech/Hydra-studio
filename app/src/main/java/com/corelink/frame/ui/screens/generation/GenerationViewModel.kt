package com.corelink.frame.ui.screens.generation

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.corelink.frame.ai.generation.GenerationQueue
import com.corelink.frame.data.local.dao.GenerationTaskDao
import com.corelink.frame.data.local.entity.GenerationTaskEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class GenerationViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val taskDao: GenerationTaskDao,
    private val generationQueue: GenerationQueue
) : ViewModel() {

    private val taskId: String = checkNotNull(savedStateHandle["taskId"])

    val task: StateFlow<GenerationTaskEntity?> = taskDao.observeById(taskId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun cancel() {
        generationQueue.requestCancel(taskId)
    }
}
