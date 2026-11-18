package com.termius.clone.terminal.engine

import androidx.compose.ui.graphics.Color
import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.CharsetDecoder
import java.nio.charset.CodingErrorAction

class TerminalEmulator(val buffer: TerminalBuffer) {

    private enum class State {
        NORMAL,
        ESC,
        CSI,
        OSC,
        OSC_ESC,
        CHARSET
    }

    private var state = State.NORMAL
    private val csiParams = StringBuilder()
    private val csiIntermediates = StringBuilder()
    private var csiPrivatePrefix: Char? = null
    private var isPrivateSequence = false

    private val utf8Decoder: CharsetDecoder = Charsets.UTF_8.newDecoder()
        .onMalformedInput(CodingErrorAction.REPLACE)
        .onUnmappableCharacter(CodingErrorAction.REPLACE)

    // 保存上次未收齐的多字节 UTF-8 残留字节（最多 1~3 字节）
    private val leftoverBytes: ByteBuffer = ByteBuffer.allocate(8)

    fun resetParserState() {
        buffer.withLock {
            state = State.NORMAL
            csiParams.clear()
            csiIntermediates.clear()
            csiPrivatePrefix = null
            isPrivateSequence = false
            leftoverBytes.clear()
            utf8Decoder.reset()
        }
    }

    fun processInput(text: String) {
        buffer.withLock {
            processCharSequence(text)
        }
    }

    fun processInput(bytes: ByteArray, offset: Int, length: Int) {
        if (length <= 0 || offset < 0 || offset + length > bytes.size) return
        buffer.withLock {
            val inputBuf: ByteBuffer = if (leftoverBytes.position() > 0) {
                leftoverBytes.flip()
                val combined = ByteBuffer.allocate(leftoverBytes.remaining() + length)
                combined.put(leftoverBytes)
                combined.put(bytes, offset, length)
                combined.flip()
                leftoverBytes.clear()
                combined
            } else {
                ByteBuffer.wrap(bytes, offset, length)
            }

            val charBuf = CharBuffer.allocate(inputBuf.remaining() + 4)
            while (true) {
                charBuf.clear()
                val result = utf8Decoder.decode(inputBuf, charBuf, false)
                charBuf.flip()
                if (charBuf.hasRemaining()) {
                    processCharSequence(charBuf)
                }
                if (result.isOverflow) {
                    continue
                }
                break
            }

            if (inputBuf.hasRemaining()) {
                val rem = inputBuf.remaining().coerceAtMost(leftoverBytes.capacity())
                for (k in 0 until rem) {
                    leftoverBytes.put(inputBuf.get())
                }
            }
        }
    }

    private fun processCharSequence(seq: CharSequence) {
        var i = 0
        val len = seq.length
        while (i < len) {
            val cp = Character.codePointAt(seq, i)
            val count = Character.charCount(cp)
            processCodePoint(cp)
            i += count
        }
    }

    private fun processCodePoint(cp: Int) {
        when (state) {
            State.NORMAL -> handleNormal(cp)
            State.ESC -> handleEsc(cp)
            State.CSI -> handleCsi(cp)
            State.OSC -> handleOsc(cp)
            State.OSC_ESC -> handleOscEsc(cp)
            State.CHARSET -> handleCharset(cp)
        }
    }

    private fun handleNormal(cp: Int) {
        when (cp) {
            0x1B -> state = State.ESC
            0x0D -> { // '\r'
                buffer.cursorCol = 0
                buffer.resetWrapPending()
            }
            0x0A, 0x0B, 0x0C -> buffer.newLine() // '\n', '\v', '\f'
            0x08 -> { // '\b'
                if (buffer.cursorCol > 0) buffer.cursorCol--
                buffer.resetWrapPending()
            }
            0x09 -> { // '\t'
                val nextTab = (buffer.cursorCol / 8 + 1) * 8
                buffer.cursorCol = nextTab.coerceAtMost((buffer.cols - 1).coerceAtLeast(0))
                buffer.resetWrapPending()
            }
            0x07 -> { /* Bell 蜂鸣器触发，可振动 */ }
            else -> {
                if (cp >= 0x20 && cp != 0x7F && cp !in 0x80..0x9F) {
                    buffer.writeCodePoint(cp)
                }
            }
        }
    }

    private fun handleEsc(cp: Int) {
        when (cp) {
            '['.code -> {
                state = State.CSI
                csiParams.clear()
                csiIntermediates.clear()
                csiPrivatePrefix = null
                isPrivateSequence = false
            }
            ']'.code -> {
                state = State.OSC
            }
            '('.code, ')'.code, '*'.code, '+'.code -> {
                state = State.CHARSET
            }
            '='.code, '>'.code -> { // Application / Normal Keypad
                state = State.NORMAL
            }
            'M'.code -> { // Reverse Index
                buffer.reverseIndex()
                state = State.NORMAL
            }
            '7'.code -> { // Save cursor (DECSC)
                buffer.saveCursor()
                state = State.NORMAL
            }
            '8'.code -> { // Restore cursor (DECRC)
                buffer.restoreCursor()
                state = State.NORMAL
            }
            else -> {
                state = State.NORMAL
            }
        }
    }

    private fun handleCsi(cp: Int) {
        // 中途遇到 ESC 中断当前残缺 CSI 并切回 ESC 状态
        if (cp == 0x1B) {
            csiParams.clear()
            csiIntermediates.clear()
            csiPrivatePrefix = null
            isPrivateSequence = false
            state = State.ESC
            return
        }

        // C0 控制符在 CSI 内部时按标准可先执行或不入参
        if (cp < 0x20) {
            when (cp) {
                0x08 -> if (buffer.cursorCol > 0) {
                    buffer.cursorCol--
                    buffer.resetWrapPending()
                }
                0x0A, 0x0B, 0x0C -> buffer.newLine()
                0x0D -> {
                    buffer.cursorCol = 0
                    buffer.resetWrapPending()
                }
            }
            return
        }

        // 私有前缀 (?, >, <, =) 仅出现在参数与中间字节之前
        if (cp == '?'.code || cp == '>'.code || cp == '<'.code || cp == '='.code) {
            if (csiParams.isEmpty() && csiIntermediates.isEmpty() && csiPrivatePrefix == null) {
                csiPrivatePrefix = cp.toChar()
                if (cp == '?'.code) {
                    isPrivateSequence = true
                }
            }
            return
        }

        // 参数字节 (0..9, ;, :)
        if (cp in '0'.code..'9'.code || cp == ';'.code || cp == ':'.code) {
            if (csiIntermediates.isEmpty() && csiParams.length < 256) {
                csiParams.append(cp.toChar())
            }
            return
        }

        // 中间字节 (0x20..0x2F，如空格用于 DECSCUSR \e[2 q)
        if (cp in 0x20..0x2F) {
            if (csiIntermediates.length < 16) {
                csiIntermediates.append(cp.toChar())
            }
            return
        }

        // 终结字节 (0x40..0x7E)
        if (cp in 0x40..0x7E) {
            if (csiIntermediates.isEmpty()) {
                val rawParams = csiParams.toString()
                if (csiPrivatePrefix == null || csiPrivatePrefix == '?') {
                    executeCsi(cp.toChar(), rawParams, csiPrivatePrefix == '?')
                }
            }
            csiParams.clear()
            csiIntermediates.clear()
            csiPrivatePrefix = null
            isPrivateSequence = false
            state = State.NORMAL
            return
        }

        // 其他非法字节安全退出 CSI
        csiParams.clear()
        csiIntermediates.clear()
        csiPrivatePrefix = null
        isPrivateSequence = false
        state = State.NORMAL
    }

    private fun handleOsc(cp: Int) {
        // OSC 通常用于设置窗口标题，以 BEL (\u0007) 或 ST (\u001B\) 结束
        when (cp) {
            0x07 -> state = State.NORMAL
            0x1B -> state = State.OSC_ESC
        }
    }

    private fun handleOscEsc(cp: Int) {
        if (cp == '\\'.code) {
            state = State.NORMAL
        } else {
            state = State.ESC
            handleEsc(cp)
        }
    }

    @Suppress("UNUSED_PARAMETER")
    private fun handleCharset(cp: Int) {
        state = State.NORMAL
    }

    private fun parseParams(raw: String): List<Int> {
        if (raw.isEmpty()) return emptyList()
        return raw.split(";").map { token ->
            val primary = token.substringBefore(':')
            primary.toIntOrNull() ?: 0
        }
    }

    private fun norm1(value: Int?): Int = if (value == null || value <= 0) 1 else value

    private fun executeCsi(command: Char, rawParams: String, isPrivate: Boolean) {
        if (isPrivate) {
            val params = parseParams(rawParams)
            val targets = if (params.isEmpty()) listOf(0) else params
            when (command) {
                'h' -> {
                    for (mode in targets) {
                        when (mode) {
                            25 -> buffer.isCursorVisible = true
                            1049 -> buffer.enterAltScreen(saveCursorAndClear = true)
                            47, 1047 -> buffer.enterAltScreen(saveCursorAndClear = false)
                        }
                    }
                }
                'l' -> {
                    for (mode in targets) {
                        when (mode) {
                            25 -> buffer.isCursorVisible = false
                            1049 -> buffer.exitAltScreen(restoreCursor = true)
                            47, 1047 -> buffer.exitAltScreen(restoreCursor = false)
                        }
                    }
                }
            }
            return
        }

        if (command == 'm') {
            handleSgr(rawParams)
            return
        }

        val params = parseParams(rawParams)
        val n1 = norm1(params.getOrNull(0))
        val n2 = norm1(params.getOrNull(1))
        val maxRow = (buffer.rows - 1).coerceAtLeast(0)
        val maxCol = (buffer.cols - 1).coerceAtLeast(0)

        when (command) {
            'A' -> {
                buffer.cursorRow = (buffer.cursorRow - n1).coerceAtLeast(0)
                buffer.resetWrapPending()
            }
            'B' -> {
                buffer.cursorRow = (buffer.cursorRow + n1).coerceAtMost(maxRow)
                buffer.resetWrapPending()
            }
            'C' -> {
                buffer.cursorCol = (buffer.cursorCol + n1).coerceAtMost(maxCol)
                buffer.resetWrapPending()
            }
            'D' -> {
                buffer.cursorCol = (buffer.cursorCol - n1).coerceAtLeast(0)
                buffer.resetWrapPending()
            }
            'H', 'f' -> buffer.setCursorPosition(n1, n2)
            'J' -> {
                buffer.clearScreen(params.getOrNull(0) ?: 0)
                buffer.resetWrapPending()
            }
            'K' -> {
                buffer.clearLine(params.getOrNull(0) ?: 0)
                buffer.resetWrapPending()
            }
            'd' -> {
                buffer.cursorRow = (n1 - 1).coerceIn(0, maxRow)
                buffer.resetWrapPending()
            }
            'G' -> {
                buffer.cursorCol = (n1 - 1).coerceIn(0, maxCol)
                buffer.resetWrapPending()
            }
            'r' -> {
                val top = params.getOrNull(0) ?: 1
                val bottom = params.getOrNull(1) ?: buffer.rows
                buffer.setScrollRegion(top, bottom)
            }
        }
    }

    private fun handleSgr(rawParams: String) {
        if (rawParams.isEmpty()) {
            buffer.resetAttributes()
            return
        }

        val tokens = rawParams.split(";")
        var i = 0
        while (i < tokens.size) {
            val token = tokens[i]
            if (token.contains(':')) {
                handleColonSgrToken(token)
                i++
                continue
            }

            when (val code = token.toIntOrNull() ?: 0) {
                0 -> buffer.resetAttributes()
                1 -> buffer.currentBold = true
                4 -> buffer.currentUnderline = true
                7 -> buffer.currentInverse = true
                22 -> buffer.currentBold = false
                24 -> buffer.currentUnderline = false
                27 -> buffer.currentInverse = false
                in 30..37 -> {
                    val colorIndex = code - 30
                    buffer.currentFgColor = buffer.theme.palette[colorIndex]
                }
                38 -> {
                    i += applyExtendedColorSemicolon(tokens, i, isForeground = true)
                }
                39 -> buffer.currentFgColor = Color.Unspecified
                in 40..47 -> {
                    val colorIndex = code - 40
                    buffer.currentBgColor = buffer.theme.palette[colorIndex]
                }
                48 -> {
                    i += applyExtendedColorSemicolon(tokens, i, isForeground = false)
                }
                49 -> buffer.currentBgColor = Color.Unspecified
                in 90..97 -> {
                    val colorIndex = (code - 90) + 8
                    buffer.currentFgColor = buffer.theme.palette[colorIndex]
                }
                in 100..107 -> {
                    val colorIndex = (code - 100) + 8
                    buffer.currentBgColor = buffer.theme.palette[colorIndex]
                }
            }
            i++
        }
    }

    private fun applyExtendedColorSemicolon(tokens: List<String>, index: Int, isForeground: Boolean): Int {
        if (index + 1 >= tokens.size) return 0
        val mode = tokens[index + 1].substringBefore(':').toIntOrNull() ?: return 0
        if (mode == 5 && index + 2 < tokens.size) {
            val idx = (tokens[index + 2].substringBefore(':').toIntOrNull() ?: 0).coerceIn(0, 255)
            val color = get256Color(idx)
            if (isForeground) buffer.currentFgColor = color else buffer.currentBgColor = color
            return 2
        }
        if (mode == 2 && index + 4 < tokens.size) {
            val r = (tokens[index + 2].substringBefore(':').toIntOrNull() ?: 0).coerceIn(0, 255)
            val g = (tokens[index + 3].substringBefore(':').toIntOrNull() ?: 0).coerceIn(0, 255)
            val b = (tokens[index + 4].substringBefore(':').toIntOrNull() ?: 0).coerceIn(0, 255)
            val color = Color(r, g, b)
            if (isForeground) buffer.currentFgColor = color else buffer.currentBgColor = color
            return 4
        }
        return 0
    }

    private fun handleColonSgrToken(token: String) {
        val parts = token.split(':')
        val mainCode = parts.getOrNull(0)?.toIntOrNull() ?: return
        when (mainCode) {
            4 -> {
                val style = parts.getOrNull(1)?.toIntOrNull() ?: 1
                buffer.currentUnderline = style != 0
            }
            38, 48 -> {
                val isForeground = mainCode == 38
                val colorType = parts.getOrNull(1)?.toIntOrNull() ?: return
                if (colorType == 5 && parts.size >= 3) {
                    val idx = (parts[2].toIntOrNull() ?: 0).coerceIn(0, 255)
                    val color = get256Color(idx)
                    if (isForeground) buffer.currentFgColor = color else buffer.currentBgColor = color
                } else if (colorType == 2) {
                    // 兼容 38:2:<cs>:<r>:<g>:<b> (6段) 与 38:2:<r>:<g>:<b> (5段)
                    val (rIdx, gIdx, bIdx) = if (parts.size >= 6) Triple(3, 4, 5) else if (parts.size == 5) Triple(2, 3, 4) else return
                    val r = (parts[rIdx].toIntOrNull() ?: 0).coerceIn(0, 255)
                    val g = (parts[gIdx].toIntOrNull() ?: 0).coerceIn(0, 255)
                    val b = (parts[bIdx].toIntOrNull() ?: 0).coerceIn(0, 255)
                    val color = Color(r, g, b)
                    if (isForeground) buffer.currentFgColor = color else buffer.currentBgColor = color
                }
            }
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
