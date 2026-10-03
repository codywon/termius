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
    private val maxHistoryLines = 2000

    // 主屏幕缓冲区
    var mainScreen: Array<TerminalLine> = Array(rows) { TerminalLine(cols) }
    // 备用屏幕缓冲区 (用于 vim, htop, less, tmux 等交互应用)
    var altScreen: Array<TerminalLine> = Array(rows) { TerminalLine(cols) }
    // 回退历史缓冲区
    val history: ArrayDeque<TerminalLine> = ArrayDeque()

    var isUsingAltScreen = false

    // 光标位置 (0-indexed)
    var cursorCol = 0
    var cursorRow = 0
    var isCursorVisible = true

    // 当前绘制样式
    var currentFgColor: Color = Color.Unspecified
    var currentBgColor: Color = Color.Unspecified
    var currentBold = false
    var currentUnderline = false
    var currentInverse = false

    val currentScreen: Array<TerminalLine>
        get() = if (isUsingAltScreen) altScreen else mainScreen

    fun resize(newCols: Int, newRows: Int) {
        if (newCols == cols && newRows == rows) return
        val oldCols = cols
        val oldRows = rows
        val oldMainScreen = mainScreen
        val oldAltScreen = altScreen

        cols = newCols
        rows = newRows

        val newMainScreen = Array(rows) { TerminalLine(cols) }
        val newAltScreen = Array(rows) { TerminalLine(cols) }
        val minCols = minOf(oldCols, cols)

        if (oldRows > rows && !isUsingAltScreen) {
            // 终端行数变小（如软键盘弹起），将顶部超出视口的有效行推进历史
            val pushCount = oldRows - rows
            for (r in 0 until pushCount) {
                if (oldMainScreen[r].hasContent()) {
                    if (history.size >= maxHistoryLines) history.removeFirst()
                    history.addLast(oldMainScreen[r].copy())
                }
            }
            for (r in 0 until rows) {
                val oldR = r + pushCount
                if (oldR in 0 until oldRows) {
                    for (c in 0 until minCols) {
                        newMainScreen[r].cells[c].copyFrom(oldMainScreen[oldR].cells[c])
                    }
                }
            }
        } else {
            // 终端行数变大或不变（如软键盘收起），保持原有行
            val minRows = minOf(oldRows, rows)
            for (r in 0 until minRows) {
                for (c in 0 until minCols) {
                    newMainScreen[r].cells[c].copyFrom(oldMainScreen[r].cells[c])
                }
            }
        }

        val minAltRows = minOf(oldRows, rows)
        for (r in 0 until minAltRows) {
            for (c in 0 until minCols) {
                newAltScreen[r].cells[c].copyFrom(oldAltScreen[r].cells[c])
            }
        }

        mainScreen = newMainScreen
        altScreen = newAltScreen
        cursorCol = cursorCol.coerceIn(0, cols - 1)
        cursorRow = cursorRow.coerceIn(0, rows - 1)
    }

    fun writeChar(c: Char) {
        val wide = isWide(c)

        if (wide && cursorCol >= cols - 1) {
            newLine()
        } else if (cursorCol >= cols) {
            newLine()
        }

        val line = currentScreen[cursorRow]
        val cell = line.cells[cursorCol]
        cell.char = c
        cell.fgColor = if (currentFgColor == Color.Unspecified) theme.foreground else currentFgColor
        cell.bgColor = if (currentBgColor == Color.Unspecified) theme.background else currentBgColor
        cell.isBold = currentBold
        cell.isUnderline = currentUnderline
        cell.isInverse = currentInverse
        cell.isWideChar = wide

        cursorCol++

        // 宽字符占用两个字符宽度，第二单元格置空占位，避免后续字符覆写
        if (wide && cursorCol < cols) {
            val followCell = line.cells[cursorCol]
            followCell.reset()
            followCell.char = ' '
            followCell.bgColor = cell.bgColor
            followCell.isWideChar = false
            cursorCol++
        }
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

    fun newLine() {
        cursorCol = 0
        if (cursorRow < rows - 1) {
            cursorRow++
        } else {
            scrollUp()
        }
    }

    fun scrollUp() {
        if (!isUsingAltScreen) {
            if (history.size >= maxHistoryLines) {
                history.removeFirst()
            }
            history.addLast(mainScreen[0].copy())
        }
        for (i in 0 until rows - 1) {
            for (j in 0 until cols) {
                currentScreen[i].cells[j].copyFrom(currentScreen[i + 1].cells[j])
            }
        }
        currentScreen[rows - 1].clear()
    }

    fun clearScreen(mode: Int) {
        when (mode) {
            0 -> { // 光标到屏幕底部
                clearLine(0)
                for (r in cursorRow + 1 until rows) {
                    currentScreen[r].clear()
                }
            }
            1 -> { // 屏幕顶部到光标
                for (r in 0 until cursorRow) {
                    currentScreen[r].clear()
                }
                clearLine(1)
            }
            2, 3 -> { // 全屏清除 (将当前屏的非空内容保存到历史以供回溯翻看)
                if (!isUsingAltScreen) {
                    for (r in 0 until rows) {
                        if (mainScreen[r].hasContent()) {
                            if (history.size >= maxHistoryLines) history.removeFirst()
                            history.addLast(mainScreen[r].copy())
                        }
                    }
                }
                for (r in 0 until rows) {
                    currentScreen[r].clear()
                }
                cursorRow = 0
                cursorCol = 0
            }
        }
    }

    fun clearLine(mode: Int) {
        val line = currentScreen[cursorRow]
        when (mode) {
            0 -> { // 光标到行末
                for (c in cursorCol until cols) {
                    line.cells[c].reset()
                }
            }
            1 -> { // 行首到光标
                for (c in 0..cursorCol.coerceAtMost(cols - 1)) {
                    line.cells[c].reset()
                }
            }
            2 -> { // 整行
                line.clear()
            }
        }
    }

    fun setCursorPosition(r: Int, c: Int) {
        cursorRow = (r - 1).coerceIn(0, rows - 1)
        cursorCol = (c - 1).coerceIn(0, cols - 1)
    }

    fun resetAttributes() {
        currentFgColor = Color.Unspecified
        currentBgColor = Color.Unspecified
        currentBold = false
        currentUnderline = false
        currentInverse = false
    }
}
