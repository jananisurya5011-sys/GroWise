package com.simats.growise.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val LightColorScheme = lightColorScheme(
    primary = TerracottaPrimary,
    secondary = GoldenYellow,
    background = PeachBackground,
    surface = SurfaceColor,
    onPrimary = White
)

@Composable
fun GroWiseTheme(
    // We remove the darkTheme parameter entirely so it can never be toggled
    content: @Composable () -> Unit
) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            // Force the status bar to match the peach background
            window.statusBarColor = LightColorScheme.background.toArgb()
            // Force dark icons on the status bar (wifi, battery, etc.)
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = true
        }
    }

    MaterialTheme(
        colorScheme = LightColorScheme,
        typography = Typography,
        content = content
    )
}