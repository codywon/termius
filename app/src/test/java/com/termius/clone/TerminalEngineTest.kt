package com.termius.clone

import androidx.compose.ui.graphics.Color
import com.termius.clone.terminal.engine.TerminalBuffer
import com.termius.clone.terminal.engine.TerminalEmulator
import com.termius.clone.terminal.engine.TerminalKeyCodes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TerminalEngineTest {

    @Test
    fun testCtrlCodeGeneration() {
        // Ctrl+C 必须映射为 ASCII 0x03 (ETX/SIGINT)
        val ctrlC = TerminalKeyCodes.getCtrlCode('c')
        assertEquals(1, ctrlC.size)
        assertEquals(3.toByte(), ctrlC[0])

        // Ctrl+D 必须映射为 ASCII 0x04 (EOT/EOF)
        val ctrlD = TerminalKeyCodes.getCtrlCode('d')
        assertEquals(1, ctrlD.size)
        assertEquals(4.toByte(), ctrlD[0])
    }

    @Test
    fun testAltCodeGeneration() {
        val altA = TerminalKeyCodes.getAltSequence('a')
        assertEquals("\u001Ba", altA)
    }

    @Test
    fun testTerminalBufferWriteAndScroll() {
        val buffer = TerminalBuffer(cols = 10, rows = 3)
        val emulator = TerminalEmulator(buffer)

        // 写入两行字符
        emulator.processInput("Hello\r\nWorld\r\n")

        // 检查光标位置
        assertEquals(2, buffer.cursorRow)
        assertEquals(0, buffer.cursorCol)

        // 触发滚动
        emulator.processInput("Line3\r\nLine4")
        assertTrue(buffer.history.isNotEmpty())
    }

    @Test
    fun testAnsiCursorPositioning() {
        val buffer = TerminalBuffer(cols = 20, rows = 10)
        val emulator = TerminalEmulator(buffer)

        // CSI 5;8H (移动到第 5 行第 8 列，1-based)
        emulator.processInput("\u001B[5;8H")
        assertEquals(4, buffer.cursorRow)
        assertEquals(7, buffer.cursorCol)
    }
}
