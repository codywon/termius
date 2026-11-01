package com.termius.clone

import androidx.compose.ui.graphics.Color
import com.termius.clone.terminal.engine.TerminalBuffer
import com.termius.clone.terminal.engine.TerminalEmulator
import com.termius.clone.terminal.engine.TerminalKeyCodes
import com.termius.clone.terminal.session.ConnectionEvent
import com.termius.clone.terminal.session.ConnectionEventType
import com.termius.clone.terminal.session.SessionEventLog
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

    /**
     * 连接提示必须留在 UI 事件层，不得写入 PTY 缓冲：
     * 这是"重连不在正文留痕"这条约束的可回归断言。
     */
    @Test
    fun testConnectionEventsNeverTouchTerminalBuffer() {
        val buffer = TerminalBuffer(cols = 40, rows = 6)
        val emulator = TerminalEmulator(buffer)
        emulator.processInput("user@host:~$ ls")
        val before = snapshot(buffer)

        val log = SessionEventLog()
        listOf(
            ConnectionEventType.DISCONNECTED,
            ConnectionEventType.RETRY_SCHEDULED,
            ConnectionEventType.RECONNECTING,
            ConnectionEventType.RECONNECT_SUCCESS
        ).forEachIndexed { index, type ->
            log.append(
                ConnectionEvent(
                    timestamp = 1_000L + index,
                    type = type,
                    message = "网络连接中断，将在 3 秒后尝试自动重连 (1/3)",
                    attempt = 1,
                    maxAttempts = 3,
                    retryDelaySec = 3
                )
            )
        }

        assertEquals("终端缓冲必须逐格不变", before, snapshot(buffer))
        assertEquals(4, log.snapshot().size)
        assertEquals(ConnectionEventType.RECONNECT_SUCCESS, log.latest()?.type)
    }

    @Test
    fun testSessionEventLogKeepsOnlyLatestCapacity() {
        val log = SessionEventLog(capacity = 3)
        repeat(10) { index ->
            log.append(
                ConnectionEvent(
                    timestamp = index.toLong(),
                    type = ConnectionEventType.RETRY_SCHEDULED,
                    attempt = index
                )
            )
        }
        val events = log.snapshot()
        assertEquals(3, events.size)
        assertEquals(7, events.first().attempt)
        assertEquals(9, events.last().attempt)
        assertEquals(10, log.version.value)
    }

    private fun snapshot(buffer: TerminalBuffer): List<String> = (0 until buffer.rows).map { row ->
        buffer.getLineString(row)
    }
}
