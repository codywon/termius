package com.termius.clone.data.model

import androidx.compose.ui.graphics.Color
import com.termius.clone.ui.theme.*

data class TerminalThemeColors(
    val id: String,
    val nameZh: String,
    val nameEn: String,
    val background: Color,
    val foreground: Color,
    val cursor: Color,
    val palette: List<Color> // 16 基础色: 0-7 标准色, 8-15 高亮色
)

object TerminalThemes {
    // 1. 经典黑白 (默认：深黑底纯白字，高对比度，专业运维首选)
    val ClassicDark = TerminalThemeColors(
        id = "classic_dark",
        nameZh = "经典黑白",
        nameEn = "Classic Dark",
        background = Color(0xFF0D1117),
        foreground = Color(0xFFF0F6FC),
        cursor = Color(0xFF58A6FF),
        palette = listOf(
            Color(0xFF161B22), Color(0xFFF85149), Color(0xFF3FB950), Color(0xFFD29922),
            Color(0xFF58A6FF), Color(0xFFBC8CFF), Color(0xFF39C5CF), Color(0xFFE6EDF3),
            Color(0xFF484F58), Color(0xFFFF7B72), Color(0xFF56D364), Color(0xFFE3B341),
            Color(0xFF79C0FF), Color(0xFFD2A8FF), Color(0xFF56D4DD), Color(0xFFFFFFFF)
        )
    )

    // 2. 黑客绿字 (Matrix：黑底绿字，黑客经典)
    val HackerGreen = TerminalThemeColors(
        id = "hacker_green",
        nameZh = "黑客绿字",
        nameEn = "Hacker Green",
        background = Color(0xFF050D08),
        foreground = Color(0xFF00FF66),
        cursor = Color(0xFF00FF66),
        palette = listOf(
            Color(0xFF0D2818), Color(0xFFFF4D4D), Color(0xFF00FF66), Color(0xFFFFCC00),
            Color(0xFF00B4D8), Color(0xFF9D4EDD), Color(0xFF00F5D4), Color(0xFFB7E4C7),
            Color(0xFF2D6A4F), Color(0xFFFF6666), Color(0xFF52B788), Color(0xFFFFD60A),
            Color(0xFF90E0EF), Color(0xFFC77DFF), Color(0xFF70E000), Color(0xFFFFFFFF)
        )
    )

    // 3. 复古琥珀 (Amber CRT：温暖琥珀橙)
    val RetroAmber = TerminalThemeColors(
        id = "retro_amber",
        nameZh = "复古琥珀",
        nameEn = "Retro Amber",
        background = Color(0xFF120D04),
        foreground = Color(0xFFFFB000),
        cursor = Color(0xFFFFB000),
        palette = listOf(
            Color(0xFF2B1D04), Color(0xFFFF5722), Color(0xFF4CAF50), Color(0xFFFFB000),
            Color(0xFF03A9F4), Color(0xFFE040FB), Color(0xFF00BCD4), Color(0xFFFFE082),
            Color(0xFF5D4037), Color(0xFFFF7043), Color(0xFF81C784), Color(0xFFFFCA28),
            Color(0xFF4FC3F7), Color(0xFFEA80FC), Color(0xFF4DD0E1), Color(0xFFFFFFFF)
        )
    )

    // 4. 极客冰蓝 (Cyber Ice：深黑底冰蓝字)
    val CyberIceBlue = TerminalThemeColors(
        id = "cyber_ice_blue",
        nameZh = "极客冰蓝",
        nameEn = "Cyber Ice Blue",
        background = Color(0xFF081018),
        foreground = Color(0xFF38BDF8),
        cursor = Color(0xFF38BDF8),
        palette = listOf(
            Color(0xFF101F30), Color(0xFFF43F5E), Color(0xFF10B981), Color(0xFFF59E0B),
            Color(0xFF0EA5E9), Color(0xFF8B5CF6), Color(0xFF06B6D4), Color(0xFFE0F2FE),
            Color(0xFF334155), Color(0xFFFB7185), Color(0xFF34D399), Color(0xFFFBBF24),
            Color(0xFF38BDF8), Color(0xFFA78BFA), Color(0xFF22D3EE), Color(0xFFFFFFFF)
        )
    )

    // 5. 霓虹深紫 (Neon Purple：深紫黑底粉紫字)
    val NeonPurple = TerminalThemeColors(
        id = "neon_purple",
        nameZh = "霓虹深紫",
        nameEn = "Neon Purple",
        background = Color(0xFF18122B),
        foreground = Color(0xFFE9D5FF),
        cursor = Color(0xFFC084FC),
        palette = listOf(
            Color(0xFF271C3D), Color(0xFFFF5555), Color(0xFF50FA7B), Color(0xFFF1FA8C),
            Color(0xFFBD93F9), Color(0xFFFF79C6), Color(0xFF8BE9FD), Color(0xFFF8F8F2),
            Color(0xFF6272A4), Color(0xFFFF6E6E), Color(0xFF69FF94), Color(0xFFFFFFA5),
            Color(0xFFD6ACFF), Color(0xFFFF92DF), Color(0xFFA4FFFF), Color(0xFFFFFFFF)
        )
    )

    // 6. 白底黑字 (Paper Light：纸质白底黑字)
    val PaperLight = TerminalThemeColors(
        id = "paper_light",
        nameZh = "纯白明眸",
        nameEn = "Paper Light",
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

    val allThemes = listOf(
        ClassicDark,
        HackerGreen,
        RetroAmber,
        CyberIceBlue,
        NeonPurple,
        PaperLight
    )

    fun getThemeById(id: String): TerminalThemeColors {
        return allThemes.find { it.id == id } ?: ClassicDark
    }

    // 兼容历史引用
    val ObsidianShell get() = ClassicDark
    val Dracula get() = NeonPurple
    val Monokai get() = HackerGreen
}
