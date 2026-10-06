package com.termius.clone.ui.theme

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color

/**
 * 终端与界面主题配色模型
 */
enum class AppTheme(
    val id: String,
    val titleZh: String,
    val titleEn: String,
    val isDark: Boolean,
    val primary: Color,
    val primaryContainer: Color,
    val background: Color,
    val surfaceContainerLow: Color,
    val surfaceContainer: Color,
    val surfaceContainerHigh: Color,
    val outline: Color,
    val previewGradient: List<Color>
) {
    EMERALD(
        id = "emerald",
        titleZh = "黑曜石翡翠",
        titleEn = "Obsidian Emerald",
        isDark = true,
        primary = Color(0xFF67DF70),
        primaryContainer = Color(0xFF1B4D20),
        background = Color(0xFF0B141C),
        surfaceContainerLow = Color(0xFF141C24),
        surfaceContainer = Color(0xFF182028),
        surfaceContainerHigh = Color(0xFF222B33),
        outline = Color(0xFF3E4A3C),
        previewGradient = listOf(Color(0xFF67DF70), Color(0xFF3FB950))
    ),
    ICE_BLUE(
        id = "ice_blue",
        titleZh = "极客冰蓝",
        titleEn = "Cyber Ice Blue",
        isDark = true,
        primary = Color(0xFF38BDF8),
        primaryContainer = Color(0xFF0E436B),
        background = Color(0xFF0B1320),
        surfaceContainerLow = Color(0xFF111C2D),
        surfaceContainer = Color(0xFF162338),
        surfaceContainerHigh = Color(0xFF21324E),
        outline = Color(0xFF2E466E),
        previewGradient = listOf(Color(0xFF38BDF8), Color(0xFF0284C7))
    ),
    AMBER(
        id = "amber",
        titleZh = "复古琥珀",
        titleEn = "Cyber Amber",
        isDark = true,
        primary = Color(0xFFFBBF24),
        primaryContainer = Color(0xFF5B3E05),
        background = Color(0xFF14120C),
        surfaceContainerLow = Color(0xFF1F1A12),
        surfaceContainer = Color(0xFF292318),
        surfaceContainerHigh = Color(0xFF383021),
        outline = Color(0xFF52452D),
        previewGradient = listOf(Color(0xFFFBBF24), Color(0xFFD97706))
    ),
    NEON_PURPLE(
        id = "neon_purple",
        titleZh = "赛博霓虹紫",
        titleEn = "Cyberpunk Purple",
        isDark = true,
        primary = Color(0xFFC084FC),
        primaryContainer = Color(0xFF4C1D95),
        background = Color(0xFF130D1D),
        surfaceContainerLow = Color(0xFF1E152E),
        surfaceContainer = Color(0xFF271C3D),
        surfaceContainerHigh = Color(0xFF372754),
        outline = Color(0xFF583B82),
        previewGradient = listOf(Color(0xFFC084FC), Color(0xFF9333EA))
    ),
    LIGHT_PAPER(
        id = "light_paper",
        titleZh = "纯白明眸",
        titleEn = "Paper Light",
        isDark = false,
        primary = Color(0xFF0969DA),
        primaryContainer = Color(0xFFDDF4FF),
        background = Color(0xFFF6F8FA),
        surfaceContainerLow = Color(0xFFFFFFFF),
        surfaceContainer = Color(0xFFEAEEF2),
        surfaceContainerHigh = Color(0xFFE1E4E8),
        outline = Color(0xFFD0D7DE),
        previewGradient = listOf(Color(0xFF0969DA), Color(0xFF54A0FF))
    );

    val textPrimary: Color get() = if (isDark) Color(0xFFDAE3EE) else Color(0xFF1F2328)
    val textSecondary: Color get() = if (isDark) Color(0xFFBDCAB8) else Color(0xFF57606A)
    val textMuted: Color get() = if (isDark) Color(0xFF879484) else Color(0xFF8C959F)

    companion object {
        fun fromId(id: String): AppTheme = entries.find { it.id == id } ?: LIGHT_PAPER
    }
}

/**
 * 主题管理中心 (支持持久化与即时热响应)
 */
object ThemeManager {
    private const val PREFS_NAME = "termx_theme_prefs"
    private const val KEY_THEME = "current_theme_id"

    private const val KEY_FONT_SIZE = "terminal_font_size_sp"
    const val DEFAULT_FONT_SIZE = 13f

    private const val KEY_TERMINAL_THEME = "current_terminal_theme_id"

    private var prefs: SharedPreferences? = null

    var currentTheme by mutableStateOf(AppTheme.LIGHT_PAPER)
        private set

    var terminalFontSizeSp: Float by mutableFloatStateOf(DEFAULT_FONT_SIZE)
        private set

    var currentTerminalTheme by mutableStateOf<com.termius.clone.data.model.TerminalThemeColors>(com.termius.clone.data.model.TerminalThemes.PaperLight)
        private set

    fun init(context: Context) {
        if (prefs == null) {
            prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val savedId = prefs?.getString(KEY_THEME, AppTheme.LIGHT_PAPER.id) ?: AppTheme.LIGHT_PAPER.id
            currentTheme = AppTheme.fromId(savedId)
            terminalFontSizeSp = prefs?.getFloat(KEY_FONT_SIZE, DEFAULT_FONT_SIZE) ?: DEFAULT_FONT_SIZE
            val savedTerminalThemeId = prefs?.getString(KEY_TERMINAL_THEME, com.termius.clone.data.model.TerminalThemes.PaperLight.id) ?: com.termius.clone.data.model.TerminalThemes.PaperLight.id
            currentTerminalTheme = com.termius.clone.data.model.TerminalThemes.getThemeById(savedTerminalThemeId)
        }
    }

    fun setTheme(theme: AppTheme) {
        currentTheme = theme
        prefs?.edit()?.putString(KEY_THEME, theme.id)?.apply()
    }

    fun setTerminalTheme(theme: com.termius.clone.data.model.TerminalThemeColors) {
        currentTerminalTheme = theme
        prefs?.edit()?.putString(KEY_TERMINAL_THEME, theme.id)?.apply()
    }

    fun setTerminalFontSize(sizeSp: Float) {
        val coerced = (Math.round(sizeSp * 2f) / 2f).coerceIn(6f, 26f) // 0.5 步进平滑吸附，最低支持 6 SP 超紧凑海量信息显示
        terminalFontSizeSp = coerced
        prefs?.edit()?.putFloat(KEY_FONT_SIZE, coerced)?.apply()
    }
}
