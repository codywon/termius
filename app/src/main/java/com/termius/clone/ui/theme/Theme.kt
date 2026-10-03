package com.termius.clone.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf

val LocalAppTheme = staticCompositionLocalOf { AppTheme.EMERALD }

@Composable
fun TermXTheme(
    theme: AppTheme = ThemeManager.currentTheme,
    content: @Composable () -> Unit
) {
    val dynamicColorScheme = darkColorScheme(
        primary = theme.primary,
        onPrimary = ObsidianOnPrimary,
        primaryContainer = theme.primaryContainer,
        onPrimaryContainer = ObsidianOnPrimaryContainer,
        secondary = ObsidianSecondary,
        onSecondary = ObsidianOnSecondary,
        secondaryContainer = ObsidianSecondaryContainer,
        onSecondaryContainer = ObsidianOnSecondaryContainer,
        tertiary = ObsidianTertiary,
        onTertiary = ObsidianOnTertiary,
        background = theme.background,
        onBackground = ObsidianTextPrimary,
        surface = theme.background,
        onSurface = ObsidianTextPrimary,
        surfaceVariant = theme.surfaceContainerHigh,
        onSurfaceVariant = ObsidianTextSecondary,
        outline = theme.outline,
        outlineVariant = ObsidianOutlineVariant,
        error = ObsidianError
    )

    CompositionLocalProvider(LocalAppTheme provides theme) {
        MaterialTheme(
            colorScheme = dynamicColorScheme,
            typography = Typography,
            content = content
        )
    }
}

@Composable
fun TermiusCloneTheme(content: @Composable () -> Unit) = TermXTheme(content = content)
