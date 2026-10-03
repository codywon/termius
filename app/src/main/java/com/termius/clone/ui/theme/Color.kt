package com.termius.clone.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// "Obsidian Shell" / "TermX" Design System Theme Palette (深浅色动态自适应)
val ObsidianBackground: Color @Composable get() = LocalAppTheme.current.background
val ObsidianSurface: Color @Composable get() = LocalAppTheme.current.background
val ObsidianSurfaceContainerLowest: Color @Composable get() = if (LocalAppTheme.current.isDark) Color(0xFF060F16) else Color(0xFFFFFFFF)
val ObsidianSurfaceContainerLow: Color @Composable get() = LocalAppTheme.current.surfaceContainerLow
val ObsidianSurfaceContainer: Color @Composable get() = LocalAppTheme.current.surfaceContainer
val ObsidianSurfaceContainerHigh: Color @Composable get() = LocalAppTheme.current.surfaceContainerHigh
val ObsidianSurfaceContainerHighest: Color @Composable get() = if (LocalAppTheme.current.isDark) Color(0xFF2D363E) else Color(0xFFD8DEE4)
val ObsidianSurfaceBright: Color @Composable get() = if (LocalAppTheme.current.isDark) Color(0xFF313A43) else Color(0xFFEAEFF2)

val ObsidianPrimary = Color(0xFF67DF70) // Emerald Green Neon Accent
val ObsidianPrimaryContainer = Color(0xFF3FB950)
val ObsidianOnPrimary = Color(0xFF00390D)
val ObsidianOnPrimaryContainer = Color(0xFF004311)

val ObsidianSecondary = Color(0xFFA2C9FF) // Ice Blue
val ObsidianSecondaryContainer = Color(0xFF0071C7)
val ObsidianOnSecondary = Color(0xFF00315C)
val ObsidianOnSecondaryContainer = Color(0xFFF0F4FF)

val ObsidianTertiary = Color(0xFFFABC45) // Amber Accent
val ObsidianTertiaryContainer = Color(0xFFD19821)
val ObsidianOnTertiary = Color(0xFF422C00)

val ObsidianTextPrimary: Color @Composable get() = LocalAppTheme.current.textPrimary
val ObsidianTextSecondary: Color @Composable get() = LocalAppTheme.current.textSecondary
val ObsidianTextMuted: Color @Composable get() = LocalAppTheme.current.textMuted
val ObsidianOutline: Color @Composable get() = LocalAppTheme.current.outline
val ObsidianOutlineVariant: Color @Composable get() = if (LocalAppTheme.current.isDark) Color(0xFF29313A) else Color(0xFFD0D7DE)
val ObsidianError = Color(0xFFFFB4AB)
val ObsidianWarning = Color(0xFFFABC45) // Amber Warning Accent

// Terminal 16 Colors (Obsidian Shell Optimized)
val TerminalBlack = Color(0xFF060F16)
val TerminalRed = Color(0xFFFF6E6E)
val TerminalGreen = Color(0xFF3FB950)
val TerminalYellow = Color(0xFFF1FA8C)
val TerminalBlue = Color(0xFF85CFFF)
val TerminalMagenta = Color(0xFFD6ACFF)
val TerminalCyan = Color(0xFF8BE9FD)
val TerminalWhite = Color(0xFFDAE3EE)

val TerminalBrightBlack = Color(0xFF313A43)
val TerminalBrightRed = Color(0xFFFF8585)
val TerminalBrightGreen = Color(0xFF67DF70)
val TerminalBrightYellow = Color(0xFFFFFFA5)
val TerminalBrightBlue = Color(0xFFA2C9FF)
val TerminalBrightMagenta = Color(0xFFFF92DF)
val TerminalBrightCyan = Color(0xFFA4FFFF)
val TerminalBrightWhite = Color(0xFFFFFFFF)
