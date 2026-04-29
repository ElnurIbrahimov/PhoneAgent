package com.phoneagent.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = Primary,
    onPrimary = OnBackground,
    primaryContainer = PrimaryMuted,
    onPrimaryContainer = OnBackground,
    secondary = Secondary,
    onSecondary = OnBackground,
    secondaryContainer = SecondaryMuted,
    onSecondaryContainer = OnBackground,
    background = Background,
    onBackground = OnBackground,
    surface = Surface,
    onSurface = OnSurface,
    surfaceVariant = SurfaceElevated,
    onSurfaceVariant = OnSurfaceMuted,
    error = Error,
    onError = OnBackground,
    outline = SurfaceBorder,
    outlineVariant = SurfaceBorder.copy(alpha = 0.5f)
)

@Composable
fun PhoneAgentTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = DarkColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        typography = PhoneAgentTypography,
        shapes = PhoneAgentShapes,
        content = content
    )
}
