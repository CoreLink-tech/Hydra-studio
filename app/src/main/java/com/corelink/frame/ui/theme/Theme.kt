package com.corelink.frame.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val FrameDarkColorScheme = darkColorScheme(
    primary = Accent,
    onPrimary = Background,
    primaryContainer = Panel3,
    onPrimaryContainer = AccentSoft,
    secondary = AccentSoft,
    onSecondary = Background,
    secondaryContainer = Panel2,
    onSecondaryContainer = TextPrimary,
    tertiary = AccentDim,
    background = Background,
    onBackground = TextPrimary,
    surface = Panel,
    onSurface = TextPrimary,
    surfaceVariant = Panel2,
    onSurfaceVariant = TextSecondary,
    outline = Border,
    outlineVariant = Border,
    error = Danger,
    onError = TextPrimary,
    errorContainer = Panel3,
    onErrorContainer = Danger
)

@Composable
fun FrameTheme(
    darkTheme: Boolean = true, // FRAME is always dark cinematic
    content: @Composable () -> Unit
) {
    val colorScheme = FrameDarkColorScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = Background.toArgb()
            window.navigationBarColor = Background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = false
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = FrameTypography,
        content = content
    )
}
