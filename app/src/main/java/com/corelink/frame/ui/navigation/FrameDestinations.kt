package com.corelink.frame.ui.navigation

sealed class FrameDestination(val route: String) {
    data object Onboarding : FrameDestination("onboarding")
    data object Home : FrameDestination("home")
    data object Create : FrameDestination("create")
    data object Generation : FrameDestination("generation/{taskId}") {
        fun createRoute(taskId: String) = "generation/$taskId"
    }
    data object Projects : FrameDestination("projects")
    data object Film : FrameDestination("film/{projectId}") {
        fun createRoute(projectId: String) = "film/$projectId"
    }
    data object Editor : FrameDestination("editor/{projectId}") {
        fun createRoute(projectId: String) = "editor/$projectId"
    }
    data object Export : FrameDestination("export/{projectId}") {
        fun createRoute(projectId: String) = "export/$projectId"
    }
    data object Models : FrameDestination("models")
    data object Hardware : FrameDestination("hardware")
    data object Settings : FrameDestination("settings")
    data object ClipDetails : FrameDestination("clip/{clipId}") {
        fun createRoute(clipId: String) = "clip/$clipId"
    }
}

val bottomNavItems = listOf(
    FrameDestination.Home,
    FrameDestination.Create,
    FrameDestination.Projects,
    FrameDestination.Models,
    FrameDestination.Settings
)
