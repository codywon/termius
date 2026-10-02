package com.termius.clone.terminal.engine

object TerminalKeyCodes {
    const val ESC = "\u001B"
    const val TAB = "\t"
    const val ENTER = "\r"
    const val BACKSPACE = "\u007F"

    // VT100 / Xterm 箭头控制序列
    const val ARROW_UP = "\u001B[A"
    const val ARROW_DOWN = "\u001B[B"
    const val ARROW_RIGHT = "\u001B[C"
    const val ARROW_LEFT = "\u001B[D"

    const val HOME = "\u001B[H"
    const val END = "\u001B[F"
    const val PAGE_UP = "\u001B[5~"
    const val PAGE_DOWN = "\u001B[6~"
    const val INSERT = "\u001B[2~"
    const val DELETE = "\u001B[3~"

    // F1 - F12
    const val F1 = "\u001BOP"
    const val F2 = "\u001BOQ"
    const val F3 = "\u001BOR"
    const val F4 = "\u001BOS"
    const val F5 = "\u001B[15~"
    const val F6 = "\u001B[17~"
    const val F7 = "\u001B[18~"
    const val F8 = "\u001B[19~"
    const val F9 = "\u001B[20~"
    const val F10 = "\u001B[21~"
    const val F11 = "\u001B[23~"
    const val F12 = "\u001B[24~"

    /**
     * 将 Ctrl + 字符 转换为 ASCII 控制码 (例如 Ctrl+C -> 0x03)
     */
    fun getCtrlCode(char: Char): ByteArray {
        val uppercase = char.uppercaseChar()
        return if (uppercase in '@'..'_') {
            byteArrayOf((uppercase.code - 64).toByte())
        } else {
            char.toString().toByteArray(Charsets.UTF_8)
        }
    }

    /**
     * Alt (Meta) 键前缀：在字符前添加 ESC (\u001B)
     */
    fun getAltSequence(char: Char): String {
        return "$ESC$char"
    }
}
