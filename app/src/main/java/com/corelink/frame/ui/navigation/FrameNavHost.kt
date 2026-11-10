package com.corelink.frame.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Create
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.corelink.frame.ui.screens.create.CreateScreen
import com.corelink.frame.ui.screens.generation.GenerationScreen
import com.corelink.frame.ui.screens.hardware.HardwareScreen
import com.corelink.frame.ui.screens.home.HomeScreen
import com.corelink.frame.ui.screens.models.ModelsScreen
import com.corelink.frame.ui.screens.onboarding.OnboardingScreen
import com.corelink.frame.ui.screens.projects.ProjectsScreen
import com.corelink.frame.ui.screens.settings.SettingsScreen
import com.corelink.frame.ui.theme.Accent
import com.corelink.frame.ui.theme.AccentSoft
import com.corelink.frame.ui.theme.Background
import com.corelink.frame.ui.theme.Border
import com.corelink.frame.ui.theme.Panel
import com.corelink.frame.ui.theme.TextMuted
import com.corelink.frame.ui.theme.TextPrimary
import com.corelink.frame.ui.theme.TextSecondary

@Composable
fun FrameNavHost(
    startDestination: String = FrameDestination.Home.route
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    val showBottomBar = currentDestination?.route in listOf(
        FrameDestination.Home.route,
        FrameDestination.Create.route,
        FrameDestination.Projects.route,
        FrameDestination.Models.route,
        FrameDestination.Settings.route
    )

    Scaffold(
        containerColor = Background,
        bottomBar = {
            if (showBottomBar) {
                FrameBottomBar(
                    currentRoute = currentDestination?.route,
                    onNavigate = { route ->
                        navController.navigate(route) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = startDestination,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(FrameDestination.Onboarding.route) {
                OnboardingScreen(
                    onContinue = {
                        navController.navigate(FrameDestination.Home.route) {
                            popUpTo(FrameDestination.Onboarding.route) { inclusive = true }
                        }
                    }
                )
            }
            composable(FrameDestination.Home.route) {
                HomeScreen(
                    onNavigateToCreate = { navController.navigate(FrameDestination.Create.route) },
                    onNavigateToProjects = { navController.navigate(FrameDestination.Projects.route) },
                    onNavigateToModels = { navController.navigate(FrameDestination.Models.route) },
                    onNavigateToHardware = { navController.navigate(FrameDestination.Hardware.route) }
                )
            }
            composable(FrameDestination.Create.route) {
                CreateScreen(
                    onGenerationStarted = { taskId ->
                        navController.navigate(FrameDestination.Generation.createRoute(taskId))
                    }
                )
            }
            composable(FrameDestination.Generation.route) {
                GenerationScreen(
                    onBack = { navController.popBackStack() }
                )
            }
            composable(FrameDestination.Projects.route) {
                ProjectsScreen()
            }
            composable(FrameDestination.Models.route) {
                ModelsScreen()
            }
            composable(FrameDestination.Hardware.route) {
                HardwareScreen(
                    onBack = { navController.popBackStack() }
                )
            }
            composable(FrameDestination.Settings.route) {
                SettingsScreen()
            }
        }
    }
}

@Composable
private fun FrameBottomBar(
    currentRoute: String?,
    onNavigate: (String) -> Unit
) {
    data class BottomItem(
        val route: String,
        val label: String,
        val icon: ImageVector
    )

    val items = listOf(
        BottomItem(FrameDestination.Home.route, "Home", Icons.Outlined.Home),
        BottomItem(FrameDestination.Create.route, "Create", Icons.Outlined.Create),
        BottomItem(FrameDestination.Projects.route, "Projects", Icons.Outlined.Folder),
        BottomItem(FrameDestination.Models.route, "Models", Icons.Outlined.Memory),
        BottomItem(FrameDestination.Settings.route, "Settings", Icons.Outlined.Settings)
    )

    NavigationBar(
        containerColor = Panel,
        contentColor = TextPrimary,
        tonalElevation = 0.dp
    ) {
        items.forEach { item ->
            val selected = currentRoute == item.route
            NavigationBarItem(
                selected = selected,
                onClick = { onNavigate(item.route) },
                icon = {
                    Icon(
                        imageVector = item.icon,
                        contentDescription = item.label
                    )
                },
                label = {
                    Text(
                        text = item.label,
                        maxLines = 1
                    )
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = AccentSoft,
                    selectedTextColor = AccentSoft,
                    unselectedIconColor = TextMuted,
                    unselectedTextColor = TextMuted,
                    indicatorColor = Border.copy(alpha = 0.4f)
                )
            )
        }
    }
}
