package com.corelink.frame.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.corelink.frame.ai.models.ModelCatalogue
import com.corelink.frame.data.local.dao.ClipDao
import com.corelink.frame.data.local.dao.GenerationTaskDao
import com.corelink.frame.data.local.entity.ClipEntity
import com.corelink.frame.data.local.entity.GenerationTaskEntity
import com.corelink.frame.data.local.entity.ProjectEntity
import com.corelink.frame.data.preferences.HardwarePreferences
import com.corelink.frame.data.repository.ProjectRepository
import com.corelink.frame.hardware.HardwareProfile
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.map
import javax.inject.Inject

data class HomeUiState(
    val projects: List<ProjectEntity> = emptyList(),
    val recentClips: List<ClipEntity> = emptyList(),
    val recentTasks: List<GenerationTaskEntity> = emptyList(),
    val profileName: String? = null,
    val installedModelCount: Int = 0,
    val catalogueModelCount: Int = ModelCatalogue.all.size
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    projectRepository: ProjectRepository,
    clipDao: ClipDao,
    taskDao: GenerationTaskDao,
    hardwarePreferences: HardwarePreferences
) : ViewModel() {

    val uiState: StateFlow<HomeUiState> = combine(
        projectRepository.observeProjects(),
        clipDao.observeRecent(10),
        taskDao.observeAll(),
        hardwarePreferences.lastProfile
    ) { projects, clips, tasks, profile ->
        HomeUiState(
            projects = projects.take(6),
            recentClips = clips,
            recentTasks = tasks.take(5),
            profileName = profile?.displayName,
            installedModelCount = 0, // updated when download manager reports installs
            catalogueModelCount = ModelCatalogue.all.size
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        HomeUiState()
    )
}
