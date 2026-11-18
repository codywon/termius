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
    var isWideChar: Boolean = false, // 中文等宽字符标记
    var extraChars: String? = null
) {
    fun reset() {
        char = ' '
        fgColor = Color.Unspecified
        bgColor = Color.Unspecified
        isBold = false
        isUnderline = false
        isInverse = false
        isWideChar = false
        extraChars = null
    }

    fun copyFrom(other: TerminalCell) {
        char = other.char
        fgColor = other.fgColor
        bgColor = other.bgColor
        isBold = other.isBold
        isUnderline = other.isUnderline
        isInverse = other.isInverse
        isWideChar = other.isWideChar
        extraChars = other.extraChars
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
            if ((cell.char != ' ' && cell.char.code > 0) || cell.extraChars != null) return true
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
    // 备用屏幕缓冲区 (用于 vim, htop, less, tmux 等交互应用)
    var altScreen: Array<TerminalLine> = Array(rows) { TerminalLine(cols) }
    // 回退历史缓冲区
    val history: ArrayDeque<TerminalLine> = ArrayDeque()

    val historySize: Int
        get() = synchronized(lock) { history.size }

    @Volatile
    var resizeVersion: Int = 0

    // DECSTBM 滚动区边界 (0-indexed, inclusive)
    @Volatile
    var scrollTop: Int = 0
    @Volatile
    var scrollBottom: Int = (rows - 1).coerceAtLeast(0)

    fun resetAltScreen() = synchronized(lock) {
        altScreen = Array(rows) { TerminalLine(cols) }
    }

    @Volatile
    var isUsingAltScreen = false

    // 光标位置 (0-indexed)
    @Volatile
    var cursorCol = 0
    @Volatile
    var cursorRow = 0
    @Volatile
    var isCursorVisible = true

    @Volatile
    private var wrapPending = false

    // ESC 7 / ESC 8 光标与属性保存槽
    private var savedCursorRow = 0
    private var savedCursorCol = 0
    private var savedWrapPending = false
    private var savedFgColor: Color = Color.Unspecified
    private var savedBgColor: Color = Color.Unspecified
    private var savedBold = false
    private var savedUnderline = false
    private var savedInverse = false

    // 1049h / 1049l 专用主屏光标备份
    private var mainSavedCursorRow = 0
    private var mainSavedCursorCol = 0

    fun resetWrapPending() = synchronized(lock) {
        wrapPending = false
    }

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
        savedCursorCol = savedCursorCol.coerceIn(0, (cols - 1).coerceAtLeast(0))
        savedCursorRow = savedCursorRow.coerceIn(0, (rows - 1).coerceAtLeast(0))
        mainSavedCursorCol = mainSavedCursorCol.coerceIn(0, (cols - 1).coerceAtLeast(0))
        mainSavedCursorRow = mainSavedCursorRow.coerceIn(0, (rows - 1).coerceAtLeast(0))
        scrollTop = 0
        scrollBottom = (rows - 1).coerceAtLeast(0)
        wrapPending = false
        resizeVersion++
    }

    fun writeChar(c: Char) {
        writeCodePoint(c.code)
    }

    fun writeCodePoint(codePoint: Int) = synchronized(lock) {
        val wide = isWide(codePoint)

        // 1. 如果此前已经写到行末 (Wrap Pending)，或者宽字符在最后一列放不下，立即换行折到下一行
        if (wrapPending || (wide && cursorCol >= cols - 1 && cols > 1)) {
            newLine()
            wrapPending = false
        }

        cursorCol = cursorCol.coerceIn(0, (cols - 1).coerceAtLeast(0))
        cursorRow = cursorRow.coerceIn(0, (rows - 1).coerceAtLeast(0))

        val screen = if (isUsingAltScreen) altScreen else mainScreen
        val line = screen.getOrNull(cursorRow) ?: return@synchronized
        val cell = line.cells.getOrNull(cursorCol) ?: return@synchronized

        // 修复宽字符半格覆写残影：
        // a) 若原格为某宽字符左半格，先清空右侧占位格
        if (cell.isWideChar) {
            line.cells.getOrNull(cursorCol + 1)?.reset()
        }
        // b) 若当前落在前一个宽字符的右半占位格，清空左侧格防止半字残影
        if (cursorCol > 0) {
            val leftCell = line.cells.getOrNull(cursorCol - 1)
            if (leftCell != null && leftCell.isWideChar) {
                leftCell.char = ' '
                leftCell.extraChars = null
                leftCell.isWideChar = false
            }
        }

        val chars = Character.toChars(codePoint)
        cell.char = chars[0]
        cell.extraChars = if (chars.size > 1) String(chars) else null
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
                if (followCell.isWideChar) {
                    line.cells.getOrNull(cursorCol + 1)?.reset()
                }
                followCell.reset()
                followCell.char = ' '
                followCell.bgColor = cell.bgColor
                followCell.isInverse = cell.isInverse
                followCell.isWideChar = false
            }
            cursorCol++
        }

        // VT100 / xterm 自动折行规范：到达行尾时标记 wrapPending，下一字符到来再换行
        if (cursorCol >= cols) {
            cursorCol = (cols - 1).coerceAtLeast(0)
            wrapPending = true
        } else {
            wrapPending = false
        }
    }

    private fun isWide(cp: Int): Boolean {
        return (cp in 0x1100..0x115F) ||       // Hangul Jamo
               (cp in 0x2E80..0x303F) ||       // CJK 部首与符号标点（含全角空格）
               (cp in 0x3040..0x33FF) ||       // 日文假名/注音/CJK 兼容
               (cp in 0x3400..0x4DBF) ||       // CJK 扩展 A
               (cp in 0x4E00..0x9FFF) ||       // CJK 统一表意文字
               (cp in 0xAC00..0xD7AF) ||       // 韩文音节
               (cp in 0xF900..0xFAFF) ||       // CJK 兼容表意文字
               (cp in 0xFE10..0xFE19) ||       // 竖排标点
               (cp in 0xFE30..0xFE6F) ||       // CJK 兼容形式
               (cp in 0xFF01..0xFF60) ||       // 全角 ASCII 变体（！、：、？等）
               (cp in 0xFFE0..0xFFE6) ||       // 全角符号
               (cp in 0x1F300..0x1FAFF) ||     // Emoji 表情与符号
               (cp in 0x20000..0x2FA1F)        // CJK 扩展 B..F 与兼容补充
    }

    fun setScrollRegion(top: Int, bottom: Int) = synchronized(lock) {
        val maxRow = (rows - 1).coerceAtLeast(0)
        val t = ((if (top <= 0) 1 else top) - 1).coerceIn(0, maxRow)
        val b = ((if (bottom <= 0) rows else bottom) - 1).coerceIn(0, maxRow)
        if (t < b || (t == 0 && b == maxRow)) {
            scrollTop = t
            scrollBottom = b
        }
        cursorRow = 0
        cursorCol = 0
        wrapPending = false
    }

    fun newLine() = synchronized(lock) {
        cursorCol = 0
        wrapPending = false
        if (cursorRow == scrollBottom) {
            scrollUp()
        } else if (cursorRow < rows - 1) {
            cursorRow++
        }
    }

    fun reverseIndex() = synchronized(lock) {
        wrapPending = false
        if (cursorRow == scrollTop) {
            scrollDown()
        } else if (cursorRow > 0) {
            cursorRow--
        }
    }

    fun scrollUp() = synchronized(lock) {
        val screen = if (isUsingAltScreen) altScreen else mainScreen
        val top = scrollTop.coerceIn(0, (rows - 1).coerceAtLeast(0))
        val bottom = scrollBottom.coerceIn(top, (rows - 1).coerceAtLeast(0))
        if (!isUsingAltScreen && top == 0) {
            if (history.size >= maxHistoryLines) {
                history.removeFirst()
            }
            val topRow = mainScreen.getOrNull(top)
            if (topRow != null) {
                history.addLast(topRow.copy())
            }
        }
        for (i in top until bottom) {
            val currLine = screen.getOrNull(i) ?: continue
            val nextLine = screen.getOrNull(i + 1) ?: continue
            currLine.copyCellsFrom(nextLine)
        }
        screen.getOrNull(bottom)?.clear()
    }

    fun scrollDown() = synchronized(lock) {
        val screen = if (isUsingAltScreen) altScreen else mainScreen
        val top = scrollTop.coerceIn(0, (rows - 1).coerceAtLeast(0))
        val bottom = scrollBottom.coerceIn(top, (rows - 1).coerceAtLeast(0))
        for (i in bottom downTo top + 1) {
            val currLine = screen.getOrNull(i) ?: continue
            val prevLine = screen.getOrNull(i - 1) ?: continue
            currLine.copyCellsFrom(prevLine)
        }
        screen.getOrNull(top)?.clear()
    }

    fun saveCursor() = synchronized(lock) {
        savedCursorRow = cursorRow
        savedCursorCol = cursorCol
        savedWrapPending = wrapPending
        savedFgColor = currentFgColor
        savedBgColor = currentBgColor
        savedBold = currentBold
        savedUnderline = currentUnderline
        savedInverse = currentInverse
    }

    fun restoreCursor() = synchronized(lock) {
        cursorRow = savedCursorRow.coerceIn(0, (rows - 1).coerceAtLeast(0))
        cursorCol = savedCursorCol.coerceIn(0, (cols - 1).coerceAtLeast(0))
        wrapPending = savedWrapPending
        currentFgColor = savedFgColor
        currentBgColor = savedBgColor
        currentBold = savedBold
        currentUnderline = savedUnderline
        currentInverse = savedInverse
    }

    fun enterAltScreen(saveCursorAndClear: Boolean) = synchronized(lock) {
        if (saveCursorAndClear) {
            if (!isUsingAltScreen) {
                mainSavedCursorRow = cursorRow
                mainSavedCursorCol = cursorCol
            }
            isUsingAltScreen = true
            altScreen = Array(rows) { TerminalLine(cols) }
            cursorRow = 0
            cursorCol = 0
            wrapPending = false
        } else {
            isUsingAltScreen = true
        }
        scrollTop = 0
        scrollBottom = (rows - 1).coerceAtLeast(0)
    }

    fun exitAltScreen(restoreCursor: Boolean) = synchronized(lock) {
        isUsingAltScreen = false
        altScreen = Array(rows) { TerminalLine(cols) }
        scrollTop = 0
        scrollBottom = (rows - 1).coerceAtLeast(0)
        if (restoreCursor) {
            cursorRow = mainSavedCursorRow.coerceIn(0, (rows - 1).coerceAtLeast(0))
            cursorCol = mainSavedCursorCol.coerceIn(0, (cols - 1).coerceAtLeast(0))
        }
        wrapPending = false
    }

    fun clearScreen(mode: Int) = synchronized(lock) {
        val screen = if (isUsingAltScreen) altScreen else mainScreen
        when (mode) {
            0 -> { // 光标到屏幕底部
                clearLine(0)
                for (r in cursorRow + 1 until rows) {
                    screen.getOrNull(r)?.clear()
                }
                wrapPending = false
            }
            1 -> { // 屏幕顶部到光标
                for (r in 0 until cursorRow) {
                    screen.getOrNull(r)?.clear()
                }
                clearLine(1)
                wrapPending = false
            }
            2 -> { // 全屏清除 (将当前屏的非空内容保存到历史以供回溯翻看)
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
                wrapPending = false
            }
            3 -> { // 全屏清除并清空历史滚动缓冲区
                for (r in 0 until rows) {
                    screen.getOrNull(r)?.clear()
                }
                history.clear()
                cursorRow = 0
                cursorCol = 0
                wrapPending = false
            }
        }
    }

    fun clearLine(mode: Int) = synchronized(lock) {
        val screen = if (isUsingAltScreen) altScreen else mainScreen
        val line = screen.getOrNull(cursorRow) ?: return@synchronized
        when (mode) {
            0 -> { // 光标到行末
                if (cursorCol > 0) {
                    val leftCell = line.cells.getOrNull(cursorCol - 1)
                    if (leftCell != null && leftCell.isWideChar) {
                        leftCell.char = ' '
                        leftCell.extraChars = null
                        leftCell.isWideChar = false
                    }
                }
                for (c in cursorCol until cols) {
                    line.cells.getOrNull(c)?.reset()
                }
            }
            1 -> { // 行首到光标
                val maxC = cursorCol.coerceAtMost((cols - 1).coerceAtLeast(0))
                if (line.cells.getOrNull(maxC)?.isWideChar == true) {
                    line.cells.getOrNull(maxC + 1)?.reset()
                }
                for (c in 0..maxC) {
                    line.cells.getOrNull(c)?.reset()
                }
            }
            2 -> { // 整行
                line.clear()
            }
        }
        wrapPending = false
    }

    fun setCursorPosition(r: Int, c: Int) = synchronized(lock) {
        cursorRow = (r - 1).coerceIn(0, (rows - 1).coerceAtLeast(0))
        cursorCol = (c - 1).coerceIn(0, (cols - 1).coerceAtLeast(0))
        wrapPending = false
    }

    fun resetAttributes() = synchronized(lock) {
        currentFgColor = Color.Unspecified
        currentBgColor = Color.Unspecified
        currentBold = false
        currentUnderline = false
        currentInverse = false
    }

    fun getLineString(r: Int): String = synchronized(lock) {
        val screen = if (isUsingAltScreen) altScreen else mainScreen
        val line = screen.getOrNull(r) ?: return@synchronized ""
        val sb = StringBuilder()
        var c = 0
        while (c < line.cells.size) {
            val cell = line.cells[c]
            sb.append(cell.extraChars ?: cell.char.toString())
            c += if (cell.isWideChar) 2 else 1
        }
        sb.toString()
    }
}
