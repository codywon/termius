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
        background = Color(0xFF0B141C),
        foreground = Color(0xFFDAE3EE),
        cursor = Color(0xFF67DF70),
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

    val PaperLight = TerminalThemeColors(
        name = "Paper Light",
        background = Color(0xFFF6F8FA),
        foreground = Color(0xFF1F2328),
        cursor = Color(0xFF0969DA),
        palette = listOf(
            Color(0xFF24292F), Color(0xFFCF222E), Color(0xFF1A7F37), Color(0xFF9A6700),
            Color(0xFF0969DA), Color(0xFF8250DF), Color(0xFF1B7C83), Color(0xFF57606A),
            Color(0xFF6E7781), Color(0xFFA40E26), Color(0xFF116329), Color(0xFF7D4E00),
            Color(0xFF0550AE), Color(0xFF6639BA), Color(0xFF114B5F), Color(0xFF24292F)
        )
    )

    fun getThemeByName(name: String): TerminalThemeColors {
        return when (name) {
            "Dracula" -> Dracula
            "Monokai" -> Monokai
            "Paper Light" -> PaperLight
            else -> ObsidianShell
        }
    }
}
