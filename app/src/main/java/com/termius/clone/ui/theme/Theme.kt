package com.termius.clone.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val ObsidianColorScheme = darkColorScheme(
    primary = ObsidianPrimary,
    onPrimary = ObsidianOnPrimary,
    primaryContainer = ObsidianPrimaryContainer,
    onPrimaryContainer = ObsidianOnPrimaryContainer,
    secondary = ObsidianSecondary,
    onSecondary = ObsidianOnSecondary,
    secondaryContainer = ObsidianSecondaryContainer,
    onSecondaryContainer = ObsidianOnSecondaryContainer,
    tertiary = ObsidianTertiary,
    onTertiary = ObsidianOnTertiary,
    background = ObsidianBackground,
    onBackground = ObsidianTextPrimary,
    surface = ObsidianSurface,
    onSurface = ObsidianTextPrimary,
    surfaceVariant = ObsidianSurfaceContainerHigh,
    onSurfaceVariant = ObsidianTextSecondary,
    outline = ObsidianOutline,
    outlineVariant = ObsidianOutlineVariant,
    error = ObsidianError
)

@Composable
fun TeamXTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = ObsidianColorScheme,
        typography = Typography,
        content = content
    )
}

@Composable
fun TermiusCloneTheme(content: @Composable () -> Unit) = TeamXTheme(content)
