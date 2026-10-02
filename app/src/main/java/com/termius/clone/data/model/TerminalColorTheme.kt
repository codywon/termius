package com.termius.clone.data.model

import androidx.compose.ui.graphics.Color
import com.termius.clone.ui.theme.*

data class TerminalThemeColors(
    val name: String,
    val background: Color,
    val foreground: Color,
    val cursor: Color,
    val palette: List<Color> // 16 基础色: 0-7 标准色, 8-15 高亮色
)

object TerminalThemes {
    val ObsidianShell = TerminalThemeColors(
        name = "Obsidian Shell",
        background = ObsidianBackground,
        foreground = ObsidianTextPrimary,
        cursor = ObsidianPrimary,
        palette = listOf(
            TerminalBlack, TerminalRed, TerminalGreen, TerminalYellow,
            TerminalBlue, TerminalMagenta, TerminalCyan, TerminalWhite,
            TerminalBrightBlack, TerminalBrightRed, TerminalBrightGreen, TerminalBrightYellow,
            TerminalBrightBlue, TerminalBrightMagenta, TerminalBrightCyan, TerminalBrightWhite
        )
    )

    val Dracula = TerminalThemeColors(
        name = "Dracula",
        background = Color(0xFF282A36),
        foreground = Color(0xFFF8F8F2),
        cursor = Color(0xFFFF79C6),
        palette = listOf(
            Color(0xFF21222C), Color(0xFFFF5555), Color(0xFF50FA7B), Color(0xFFF1FA8C),
            Color(0xFFBD93F9), Color(0xFFFF79C6), Color(0xFF8BE9FD), Color(0xFFF8F8F2),
            Color(0xFF6272A4), Color(0xFFFF6E6E), Color(0xFF69FF94), Color(0xFFFFFFA5),
            Color(0xFFD6ACFF), Color(0xFFFF92DF), Color(0xFFA4FFFF), Color(0xFFFFFFFF)
        )
    )

    val Monokai = TerminalThemeColors(
        name = "Monokai",
        background = Color(0xFF272822),
        foreground = Color(0xFFF8F8F2),
        cursor = Color(0xFFF8F8F0),
        palette = listOf(
            Color(0xFF272822), Color(0xFFF92672), Color(0xFFA6E22E), Color(0xFFF4BF75),
            Color(0xFF66D9EF), Color(0xFFAE81FF), Color(0xFFA1EFE4), Color(0xFFF8F8F2),
            Color(0xFF75715E), Color(0xFFF92672), Color(0xFFA6E22E), Color(0xFFF4BF75),
            Color(0xFF66D9EF), Color(0xFFAE81FF), Color(0xFFA1EFE4), Color(0xFFF9F8F5)
        )
    )

    fun getThemeByName(name: String): TerminalThemeColors {
        return when (name) {
            "Dracula" -> Dracula
            "Monokai" -> Monokai
            else -> ObsidianShell
        }
    }
}
