package com.termius.clone.terminal.engine

import androidx.compose.ui.graphics.Color
import com.termius.clone.data.model.TerminalThemeColors
import com.termius.clone.data.model.TerminalThemes

/**
 * 单个字符单元格属性
 */
data class TerminalCell(
    var char: Char = ' ',
    var fgColor: Color = Color.Unspecified,
    var bgColor: Color = Color.Unspecified,
    var isBold: Boolean = false,
    var isUnderline: Boolean = false,
    var isInverse: Boolean = false,
    var isWideChar: Boolean = false // 中文等宽字符标记
) {
    fun reset() {
        char = ' '
        fgColor = Color.Unspecified
        bgColor = Color.Unspecified
        isBold = false
        isUnderline = false
        isInverse = false
        isWideChar = false
    }

    fun copyFrom(other: TerminalCell) {
        char = other.char
        fgColor = other.fgColor
        bgColor = other.bgColor
        isBold = other.isBold
        isUnderline = other.isUnderline
        isInverse = other.isInverse
        isWideChar = other.isWideChar
    }
}

/**
 * 终端行结构
 */
class TerminalLine(val cols: Int) {
    val cells: Array<TerminalCell> = Array(cols) { TerminalCell() }

    fun clear() {
        for (cell in cells) {
            cell.reset()
        }
    }

    fun copy(): TerminalLine {
        val newLine = TerminalLine(cols)
        for (i in 0 until cols) {
            newLine.cells[i].copyFrom(cells[i])
        }
        return newLine
    }

    fun copyCellsFrom(source: TerminalLine) {
        val count = minOf(cells.size, source.cells.size)
        for (i in 0 until count) {
            cells[i].copyFrom(source.cells[i])
        }
    }

    fun hasContent(): Boolean {
        for (cell in cells) {
            if (cell.char != ' ' && cell.char.code > 0) return true
        }
        return false
    }
}

/**
 * 终端屏幕与滚屏回退缓冲区
 */
class TerminalBuffer(
    var cols: Int = 80,
    var rows: Int = 24,
    var theme: TerminalThemeColors = TerminalThemes.ObsidianShell
) {
    val lock = Any()
    private val maxHistoryLines = 2000

    // 主屏幕缓冲区
    var mainScreen: Array<TerminalLine> = Array(rows) { TerminalLine(cols) }
        private set
    // 备用屏幕缓冲区 (用于 vim, htop, less, tmux 等交互应用)
    var altScreen: Array<TerminalLine> = Array(rows) { TerminalLine(cols) }
        private set
    // 回退历史缓冲区
    val history: ArrayDeque<TerminalLine> = ArrayDeque()

    @Volatile
    var isUsingAltScreen = false

    // 光标位置 (0-indexed)
    @Volatile
    var cursorCol = 0
    @Volatile
    var cursorRow = 0
    @Volatile
    var isCursorVisible = true

    // 当前绘制样式
    var currentFgColor: Color = Color.Unspecified
    var currentBgColor: Color = Color.Unspecified
    var currentBold = false
    var currentUnderline = false
    var currentInverse = false

    val currentScreen: Array<TerminalLine>
        get() = synchronized(lock) { if (isUsingAltScreen) altScreen else mainScreen }

    fun <T> withLock(block: () -> T): T = synchronized(lock, block)

    fun resize(newCols: Int, newRows: Int) = synchronized(lock) {
        if (newCols <= 0 || newRows <= 0) return@synchronized
        if (newCols == cols && newRows == rows) return@synchronized

        val oldCols = cols
        val oldRows = rows
        val oldMainScreen = mainScreen
        val oldAltScreen = altScreen

        cols = newCols
        rows = newRows

        val newMainScreen = Array(rows) { TerminalLine(cols) }
        val newAltScreen = Array(rows) { TerminalLine(cols) }

        if (newRows < oldRows && !isUsingAltScreen) {
            // 终端行数变小（如软键盘弹起或切换为横屏）：
            // 仅当当前光标行超出新视口时，才向上滚动让光标位于底部
            val linesToScroll = if (cursorRow >= newRows) {
                cursorRow - newRows + 1
            } else {
                0
            }

            for (r in 0 until linesToScroll) {
                val oldLine = oldMainScreen.getOrNull(r)
                if (oldLine != null && oldLine.hasContent()) {
                    if (history.size >= maxHistoryLines) history.removeFirst()
                    history.addLast(oldLine.copy())
                }
            }

            for (r in 0 until newRows) {
                val oldR = r + linesToScroll
                val oldLine = oldMainScreen.getOrNull(oldR) ?: continue
                newMainScreen[r].copyCellsFrom(oldLine)
            }
            cursorRow = (cursorRow - linesToScroll).coerceIn(0, (newRows - 1).coerceAtLeast(0))
        } else {
            // 终端行数变大（如从横屏切回竖屏，或者收起键盘）：
            // 严禁倒腾 history，避免历史顺序颠倒与并发数组越界崩溃！
            // 直接将旧屏幕内容安全复制到新屏幕，多出行保留为空白行供后续输出
            val copyRows = minOf(oldRows, newRows)
            for (r in 0 until copyRows) {
                val oldLine = oldMainScreen.getOrNull(r) ?: continue
                newMainScreen[r].copyCellsFrom(oldLine)
            }
            cursorRow = cursorRow.coerceIn(0, (newRows - 1).coerceAtLeast(0))
        }

        // 备用屏幕安全拷贝
        val minAltRows = minOf(oldRows, rows)
        for (r in 0 until minAltRows) {
            val oldLine = oldAltScreen.getOrNull(r) ?: continue
            newAltScreen[r].copyCellsFrom(oldLine)
        }

        mainScreen = newMainScreen
        altScreen = newAltScreen
        cursorCol = cursorCol.coerceIn(0, (cols - 1).coerceAtLeast(0))
        cursorRow = cursorRow.coerceIn(0, (rows - 1).coerceAtLeast(0))
    }

    fun writeChar(c: Char) = synchronized(lock) {
        val wide = isWide(c)

        cursorCol = cursorCol.coerceIn(0, (cols - 1).coerceAtLeast(0))
        cursorRow = cursorRow.coerceIn(0, (rows - 1).coerceAtLeast(0))

        if (wide && cursorCol >= cols - 1) {
            newLine()
        } else if (cursorCol >= cols) {
            newLine()
        }

        val screen = if (isUsingAltScreen) altScreen else mainScreen
        val line = screen.getOrNull(cursorRow) ?: return@synchronized
        val cell = line.cells.getOrNull(cursorCol) ?: return@synchronized
        cell.char = c
        cell.fgColor = currentFgColor
        cell.bgColor = currentBgColor
        cell.isBold = currentBold
        cell.isUnderline = currentUnderline
        cell.isInverse = currentInverse
        cell.isWideChar = wide

        cursorCol++

        // 宽字符占用两个字符宽度，第二单元格置空占位，避免后续字符覆写
        if (wide && cursorCol < cols) {
            val followCell = line.cells.getOrNull(cursorCol)
            if (followCell != null) {
                followCell.reset()
                followCell.char = ' '
                followCell.bgColor = cell.bgColor
                followCell.isWideChar = false
            }
            cursorCol++
        }
        cursorCol = cursorCol.coerceIn(0, (cols - 1).coerceAtLeast(0))
    }

    private fun isWide(c: Char): Boolean {
        val code = c.code
        return (code in 0x4E00..0x9FFF) ||       // CJK 统一表意文字
               (code in 0x3400..0x4DBF) ||       // CJK 扩展 A
               (code in 0x20000..0x2A6DF) ||     // CJK 扩展 B
               (code in 0xF900..0xFAFF) ||       // CJK 兼容表意文字
               (code in 0x3000..0x303F) ||       // CJK 符号和标点（全角空格、逗号、顿号等）
               (code in 0xFF01..0xFF60) ||       // 全角 ASCII 变体（！、：、？、（、）等）
               (code in 0xFFE0..0xFFE6) ||       // 全角符号
               (code in 0xAC00..0xD7AF)          // 韩文音节
    }

    fun newLine() = synchronized(lock) {
        cursorCol = 0
        if (cursorRow < rows - 1) {
            cursorRow++
        } else {
            scrollUp()
        }
    }

    fun scrollUp() = synchronized(lock) {
        val screen = if (isUsingAltScreen) altScreen else mainScreen
        if (!isUsingAltScreen) {
            if (history.size >= maxHistoryLines) {
                history.removeFirst()
            }
            val topRow = mainScreen.getOrNull(0)
            if (topRow != null) {
                history.addLast(topRow.copy())
            }
        }
        for (i in 0 until rows - 1) {
            val currLine = screen.getOrNull(i) ?: continue
            val nextLine = screen.getOrNull(i + 1) ?: continue
            currLine.copyCellsFrom(nextLine)
        }
        screen.getOrNull(rows - 1)?.clear()
    }

    fun clearScreen(mode: Int) = synchronized(lock) {
        val screen = if (isUsingAltScreen) altScreen else mainScreen
        when (mode) {
            0 -> { // 光标到屏幕底部
                clearLine(0)
                for (r in cursorRow + 1 until rows) {
                    screen.getOrNull(r)?.clear()
                }
            }
            1 -> { // 屏幕顶部到光标
                for (r in 0 until cursorRow) {
                    screen.getOrNull(r)?.clear()
                }
                clearLine(1)
            }
            2, 3 -> { // 全屏清除 (将当前屏的非空内容保存到历史以供回溯翻看)
                if (!isUsingAltScreen) {
                    for (r in 0 until rows) {
                        val row = mainScreen.getOrNull(r)
                        if (row != null && row.hasContent()) {
                            if (history.size >= maxHistoryLines) history.removeFirst()
                            history.addLast(row.copy())
                        }
                    }
                }
                for (r in 0 until rows) {
                    screen.getOrNull(r)?.clear()
                }
                cursorRow = 0
                cursorCol = 0
            }
        }
    }

    fun clearLine(mode: Int) = synchronized(lock) {
        val screen = if (isUsingAltScreen) altScreen else mainScreen
        val line = screen.getOrNull(cursorRow) ?: return@synchronized
        when (mode) {
            0 -> { // 光标到行末
                for (c in cursorCol until cols) {
                    line.cells.getOrNull(c)?.reset()
                }
            }
            1 -> { // 行首到光标
                val maxC = cursorCol.coerceAtMost(cols - 1)
                for (c in 0..maxC) {
                    line.cells.getOrNull(c)?.reset()
                }
            }
            2 -> { // 整行
                line.clear()
            }
        }
    }

    fun setCursorPosition(r: Int, c: Int) = synchronized(lock) {
        cursorRow = (r - 1).coerceIn(0, (rows - 1).coerceAtLeast(0))
        cursorCol = (c - 1).coerceIn(0, (cols - 1).coerceAtLeast(0))
    }

    fun resetAttributes() = synchronized(lock) {
        currentFgColor = Color.Unspecified
        currentBgColor = Color.Unspecified
        currentBold = false
        currentUnderline = false
        currentInverse = false
    }
}
