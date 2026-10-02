package com.termius.clone.terminal.engine

import androidx.compose.ui.graphics.Color

class TerminalEmulator(val buffer: TerminalBuffer) {

    private enum class State {
        NORMAL,
        ESC,
        CSI,
        OSC,
        CHARSET
    }

    private var state = State.NORMAL
    private val csiParams = StringBuilder()
    private var isPrivateSequence = false

    fun processInput(text: String) {
        for (c in text) {
            processChar(c)
        }
    }

    fun processInput(bytes: ByteArray, offset: Int, length: Int) {
        val str = String(bytes, offset, length, Charsets.UTF_8)
        processInput(str)
    }

    private fun processChar(c: Char) {
        when (state) {
            State.NORMAL -> handleNormal(c)
            State.ESC -> handleEsc(c)
            State.CSI -> handleCsi(c)
            State.OSC -> handleOsc(c)
            State.CHARSET -> handleCharset(c)
        }
    }

    private fun handleNormal(c: Char) {
        when (c) {
            '\u001B' -> state = State.ESC
            '\r' -> buffer.cursorCol = 0
            '\n' -> buffer.newLine()
            '\b' -> {
                if (buffer.cursorCol > 0) buffer.cursorCol--
            }
            '\t' -> {
                val nextTab = (buffer.cursorCol / 8 + 1) * 8
                buffer.cursorCol = nextTab.coerceAtMost(buffer.cols - 1)
            }
            '\u0007' -> { /* Bell 蜂鸣器触发，可振动 */ }
            else -> {
                if (c >= ' ') {
                    buffer.writeChar(c)
                }
            }
        }
    }

    private fun handleEsc(c: Char) {
        when (c) {
            '[' -> {
                state = State.CSI
                csiParams.clear()
                isPrivateSequence = false
            }
            ']' -> {
                state = State.OSC
            }
            '(', ')', '*', '+' -> {
                state = State.CHARSET
            }
            '=' -> { // Application Keypad
                state = State.NORMAL
            }
            '>' -> { // Normal Keypad
                state = State.NORMAL
            }
            'M' -> { // Reverse index (Scroll down)
                buffer.scrollUp()
                state = State.NORMAL
            }
            '7' -> { // Save cursor
                state = State.NORMAL
            }
            '8' -> { // Restore cursor
                state = State.NORMAL
            }
            else -> {
                state = State.NORMAL
            }
        }
    }

    private fun handleCsi(c: Char) {
        if (c == '?') {
            isPrivateSequence = true
            return
        }

        if (c in '0'..'9' || c == ';') {
            csiParams.append(c)
            return
        }

        // CSI 终结符
        val params = parseParams(csiParams.toString())
        executeCsi(c, params, isPrivateSequence)
        state = State.NORMAL
    }

    private fun handleOsc(c: Char) {
        // OSC 通常用于设置窗口标题，以 BEL (\u0007) 或 ST (\u001B\) 结束
        if (c == '\u0007' || c == '\u001B') {
            state = State.NORMAL
        }
    }

    private fun handleCharset(c: Char) {
        state = State.NORMAL
    }

    private fun parseParams(raw: String): List<Int> {
        if (raw.isEmpty()) return emptyList()
        return raw.split(";").map { it.toIntOrNull() ?: 0 }
    }

    private fun executeCsi(command: Char, params: List<Int>, isPrivate: Boolean) {
        val p1 = params.getOrNull(0) ?: 1
        val p2 = params.getOrNull(1) ?: 1

        if (isPrivate) {
            when (command) {
                'h' -> {
                    when (p1) {
                        25 -> buffer.isCursorVisible = true
                        1049, 47 -> buffer.isUsingAltScreen = true // 开启全屏备用模式 (vim, htop)
                    }
                }
                'l' -> {
                    when (p1) {
                        25 -> buffer.isCursorVisible = false
                        1049, 47 -> {
                            buffer.isUsingAltScreen = false // 退出全屏备用模式
                            buffer.altScreen = Array(buffer.rows) { TerminalLine(buffer.cols) }
                        }
                    }
                }
            }
            return
        }

        when (command) {
            'A' -> buffer.cursorRow = (buffer.cursorRow - p1).coerceAtLeast(0) // 光标上移
            'B' -> buffer.cursorRow = (buffer.cursorRow + p1).coerceAtMost(buffer.rows - 1) // 光标下移
            'C' -> buffer.cursorCol = (buffer.cursorCol + p1).coerceAtMost(buffer.cols - 1) // 光标右移
            'D' -> buffer.cursorCol = (buffer.cursorCol - p1).coerceAtLeast(0) // 光标左移
            'H', 'f' -> buffer.setCursorPosition(p1, p2) // 光标绝对定位
            'J' -> buffer.clearScreen(params.getOrNull(0) ?: 0) // 清屏
            'K' -> buffer.clearLine(params.getOrNull(0) ?: 0) // 清行
            'm' -> handleSgr(params) // 样式与颜色
            'd' -> buffer.cursorRow = (p1 - 1).coerceIn(0, buffer.rows - 1) // 纵坐标定位
            'G' -> buffer.cursorCol = (p1 - 1).coerceIn(0, buffer.cols - 1) // 横坐标定位
        }
    }

    private fun handleSgr(params: List<Int>) {
        if (params.isEmpty()) {
            buffer.resetAttributes()
            return
        }

        var i = 0
        while (i < params.size) {
            when (val code = params[i]) {
                0 -> buffer.resetAttributes()
                1 -> buffer.currentBold = true
                4 -> buffer.currentUnderline = true
                7 -> buffer.currentInverse = true
                22 -> buffer.currentBold = false
                24 -> buffer.currentUnderline = false
                27 -> buffer.currentInverse = false
                in 30..37 -> {
                    // 标准前景色
                    val colorIndex = code - 30
                    buffer.currentFgColor = buffer.theme.palette[colorIndex]
                }
                38 -> {
                    // 扩展前景色 (256 色或 24-bit TrueColor)
                    if (i + 1 < params.size) {
                        if (params[i + 1] == 5 && i + 2 < params.size) {
                            // 256 色
                            val idx = params[i + 2].coerceIn(0, 255)
                            buffer.currentFgColor = get256Color(idx)
                            i += 2
                        } else if (params[i + 1] == 2 && i + 4 < params.size) {
                            // 24-bit TrueColor
                            val r = params[i + 2].coerceIn(0, 255)
                            val g = params[i + 3].coerceIn(0, 255)
                            val b = params[i + 4].coerceIn(0, 255)
                            buffer.currentFgColor = Color(r, g, b)
                            i += 4
                        }
                    }
                }
                39 -> buffer.currentFgColor = Color.Unspecified
                in 40..47 -> {
                    // 标准背景色
                    val colorIndex = code - 40
                    buffer.currentBgColor = buffer.theme.palette[colorIndex]
                }
                48 -> {
                    // 扩展背景色
                    if (i + 1 < params.size) {
                        if (params[i + 1] == 5 && i + 2 < params.size) {
                            val idx = params[i + 2].coerceIn(0, 255)
                            buffer.currentBgColor = get256Color(idx)
                            i += 2
                        } else if (params[i + 1] == 2 && i + 4 < params.size) {
                            val r = params[i + 2].coerceIn(0, 255)
                            val g = params[i + 3].coerceIn(0, 255)
                            val b = params[i + 4].coerceIn(0, 255)
                            buffer.currentBgColor = Color(r, g, b)
                            i += 4
                        }
                    }
                }
                49 -> buffer.currentBgColor = Color.Unspecified
                in 90..97 -> {
                    // 高亮前景色
                    val colorIndex = (code - 90) + 8
                    buffer.currentFgColor = buffer.theme.palette[colorIndex]
                }
                in 100..107 -> {
                    // 高亮背景色
                    val colorIndex = (code - 100) + 8
                    buffer.currentBgColor = buffer.theme.palette[colorIndex]
                }
            }
            i++
        }
    }

    private fun get256Color(index: Int): Color {
        if (index < 16) {
            return buffer.theme.palette[index]
        }
        if (index in 16..231) {
            val idx = index - 16
            val r = (idx / 36) * 51
            val g = ((idx % 36) / 6) * 51
            val b = (idx % 6) * 51
            return Color(r, g, b)
        }
        // 232..255 灰阶
        val gray = 8 + (index - 232) * 10
        return Color(gray, gray, gray)
    }
}
