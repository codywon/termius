package com.termius.clone.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

val LocalAppTheme = staticCompositionLocalOf { AppTheme.EMERALD }
val LocalTextPrimary = staticCompositionLocalOf { Color(0xFFDAE3EE) }
val LocalTextSecondary = staticCompositionLocalOf { Color(0xFFBDCAB8) }
val LocalTextMuted = staticCompositionLocalOf { Color(0xFF879484) }

@Composable
fun TermXTheme(
    theme: AppTheme = ThemeManager.currentTheme,
    content: @Composable () -> Unit
) {
    val dynamicColorScheme = if (theme.isDark) {
        darkColorScheme(
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
            onBackground = theme.textPrimary,
            surface = theme.background,
            onSurface = theme.textPrimary,
            surfaceVariant = theme.surfaceContainerHigh,
            onSurfaceVariant = theme.textSecondary,
            outline = theme.outline,
            outlineVariant = ObsidianOutlineVariant,
            error = ObsidianError
        )
    } else {
        lightColorScheme(
            primary = theme.primary,
            onPrimary = Color.White,
            primaryContainer = theme.primaryContainer,
            onPrimaryContainer = theme.primary,
            secondary = Color(0xFF0969DA),
            onSecondary = Color.White,
            background = theme.background,
            onBackground = theme.textPrimary,
            surface = theme.surfaceContainerLow,
            onSurface = theme.textPrimary,
            surfaceVariant = theme.surfaceContainerHigh,
            onSurfaceVariant = theme.textSecondary,
            outline = theme.outline,
            outlineVariant = Color(0xFFD0D7DE),
            error = Color(0xFFCF222E)
        )
    }

    CompositionLocalProvider(
        LocalAppTheme provides theme,
        LocalTextPrimary provides theme.textPrimary,
        LocalTextSecondary provides theme.textSecondary,
        LocalTextMuted provides theme.textMuted
    ) {
        MaterialTheme(
            colorScheme = dynamicColorScheme,
            typography = Typography,
            content = content
        )
    }
}

@Composable
fun TermiusCloneTheme(content: @Composable () -> Unit) = TermXTheme(content = content)
