package com.termius.clone

import androidx.compose.ui.graphics.Color
import com.termius.clone.terminal.engine.TerminalBuffer
import com.termius.clone.terminal.engine.TerminalCell
import com.termius.clone.terminal.engine.TerminalEmulator
import com.termius.clone.terminal.engine.TerminalKeyCodes
import com.termius.clone.terminal.session.ConnectionEvent
import com.termius.clone.terminal.session.ConnectionEventType
import com.termius.clone.terminal.session.SessionEventLog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicReference

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

    @Test
    fun testTerminalBufferContractPropertiesAndCellExtraChars() {
        val buffer = TerminalBuffer(cols = 10, rows = 4)
        assertEquals(0, buffer.historySize)
        assertEquals(0, buffer.resizeVersion)

        // 相同尺寸不递增 resizeVersion
        buffer.resize(10, 4)
        assertEquals(0, buffer.resizeVersion)

        // 变更尺寸才递增 resizeVersion
        buffer.resize(12, 4)
        assertEquals(1, buffer.resizeVersion)
        buffer.resize(12, 6)
        assertEquals(2, buffer.resizeVersion)

        val cell = TerminalCell(char = '\uD83D', isWideChar = true, extraChars = "\uD83D\uDE00")
        val copy = TerminalCell()
        copy.copyFrom(cell)
        assertEquals("\uD83D\uDE00", copy.extraChars)
        assertTrue(copy.isWideChar)

        copy.reset()
        assertNull(copy.extraChars)
        assertFalse(copy.isWideChar)
        assertEquals(' ', copy.char)
    }

    @Test
    fun testSplitUtf8DecodingAndSupplementaryWideChars() {
        val buffer = TerminalBuffer(cols = 20, rows = 4)
        val emulator = TerminalEmulator(buffer)

        // 将 "中😀𠮷!" (3字节中文 + 4字节Emoji + 4字节Ext-B + ASCII) 逐块切碎发送
        val payload = "中\uD83D\uDE00\uD842\uDFB7!".toByteArray(Charsets.UTF_8)
        // 故意切在每个多字节序列内部
        val chunks = listOf(
            payload.copyOfRange(0, 2),   // '中' 前2字节
            payload.copyOfRange(2, 5),   // '中' 尾字节 + '😀' 前2字节
            payload.copyOfRange(5, 9),   // '😀' 后2字节 + '𠮷' 前2字节
            payload.copyOfRange(9, payload.size) // '𠮷' 后2字节 + '!'
        )
        for (chunk in chunks) {
            emulator.processInput(chunk, 0, chunk.size)
        }

        val cells = buffer.mainScreen[0].cells
        assertEquals('中', cells[0].char)
        assertNull(cells[0].extraChars)
        assertTrue(cells[0].isWideChar)
        assertFalse(cells[1].isWideChar) // 右半占位格

        assertEquals('\uD83D', cells[2].char)
        assertEquals("\uD83D\uDE00", cells[2].extraChars)
        assertTrue(cells[2].isWideChar)

        assertEquals('\uD842', cells[4].char)
        assertEquals("\uD842\uDFB7", cells[4].extraChars)
        assertTrue(cells[4].isWideChar)

        assertEquals('!', cells[6].char)
        assertEquals("中\uD83D\uDE00\uD842\uDFB7!", buffer.getLineString(0).trimEnd())

        // 验证 resetParserState 会清空未完成的残余 UTF-8 字节
        val brokenPrefix = byteArrayOf(0xE4.toByte(), 0xB8.toByte())
        emulator.processInput(brokenPrefix, 0, brokenPrefix.size)
        emulator.resetParserState()
        emulator.processInput("OK".toByteArray(Charsets.UTF_8), 0, 2)
        assertTrue(buffer.getLineString(0).contains("OK"))
    }

    @Test
    fun testWideCharPartialOverwriteCleanup() {
        val buffer = TerminalBuffer(cols = 10, rows = 2)
        val emulator = TerminalEmulator(buffer)

        emulator.processInput("中文A")
        // '中' 在 0-1 列，移动到第 2 列(1-based)即 index=1 (右半占位格) 覆写 'X'
        emulator.processInput("\u001B[1;2HX")
        val cells = buffer.mainScreen[0].cells
        assertEquals(' ', cells[0].char)
        assertFalse(cells[0].isWideChar)
        assertNull(cells[0].extraChars)
        assertEquals('X', cells[1].char)

        // '文' 在 2-3 列，移动到第 3 列(1-based)即 index=2 (左半格) 覆写 'Y'
        emulator.processInput("\u001B[1;3HY")
        assertEquals('Y', cells[2].char)
        assertFalse(cells[2].isWideChar)
        assertEquals(' ', cells[3].char)
        assertFalse(cells[3].isWideChar)
        assertEquals('A', cells[4].char)
    }

    @Test
    fun testDecstbmScrollRegionAndReverseIndex() {
        val buffer = TerminalBuffer(cols = 10, rows = 5)
        val emulator = TerminalEmulator(buffer)

        emulator.processInput("Row1\r\nRow2\r\nRow3\r\nRow4\r\nRow5")
        // 设置滚动区 2..4 行 (1-based)
        emulator.processInput("\u001B[2;4r")
        // DECSTBM 光标归位到 (0,0)，跳到第 4 行 (scrollBottom=3) 再换行触发区域上滚
        emulator.processInput("\u001B[4;1H\nNew4")
        assertEquals("Row1", buffer.getLineString(0).trimEnd())
        assertEquals("Row3", buffer.getLineString(1).trimEnd())
        assertEquals("Row4", buffer.getLineString(2).trimEnd())
        assertEquals("New4", buffer.getLineString(3).trimEnd())
        assertEquals("Row5", buffer.getLineString(4).trimEnd())

        // 跳回 scrollTop (第 2 行) 执行 ESC M (Reverse Index) 触发区域下滚
        emulator.processInput("\u001B[2;1H\u001BMTop2")
        assertEquals("Row1", buffer.getLineString(0).trimEnd())
        assertEquals("Top2", buffer.getLineString(1).trimEnd())
        assertEquals("Row3", buffer.getLineString(2).trimEnd())
        assertEquals("Row4", buffer.getLineString(3).trimEnd())
        assertEquals("Row5", buffer.getLineString(4).trimEnd())
    }

    @Test
    fun testSaveRestoreCursorAndAltScreen1049() {
        val buffer = TerminalBuffer(cols = 20, rows = 5)
        val emulator = TerminalEmulator(buffer)

        emulator.processInput("MainLine\u001B[3;6H")
        // ESC 7 保存当前光标 (row=2, col=5)
        emulator.processInput("\u001B7\u001B[1;1H")
        assertEquals(0, buffer.cursorRow)
        assertEquals(0, buffer.cursorCol)
        // ESC 8 恢复光标
        emulator.processInput("\u001B8")
        assertEquals(2, buffer.cursorRow)
        assertEquals(5, buffer.cursorCol)

        // 1049h 保存主屏光标并切备用屏，1049l 清备用屏并恢复主屏光标
        emulator.processInput("\u001B[?1049h\u001B[4;10HAltText")
        assertTrue(buffer.isUsingAltScreen)
        assertEquals("AltText", buffer.getLineString(3).trim())

        emulator.processInput("\u001B[?1049l")
        assertFalse(buffer.isUsingAltScreen)
        assertEquals(2, buffer.cursorRow)
        assertEquals(5, buffer.cursorCol)
        assertEquals("MainLine", buffer.getLineString(0).trimEnd())
    }

    @Test
    fun testCsi3JClearsHistoryAndScreen() {
        val buffer = TerminalBuffer(cols = 10, rows = 2)
        val emulator = TerminalEmulator(buffer)

        emulator.processInput("L1\r\nL2\r\nL3\r\nL4")
        assertTrue(buffer.historySize > 0)

        emulator.processInput("\u001B[3J")
        assertEquals(0, buffer.historySize)
        assertEquals("", buffer.getLineString(0).trim())
        assertEquals("", buffer.getLineString(1).trim())
    }

    @Test
    fun testCsiIntermediateAndPrivatePrefixes() {
        val buffer = TerminalBuffer(cols = 20, rows = 3)
        val emulator = TerminalEmulator(buffer)

        // 带中间字节空格的 DECSCUSR (\e[2 q)，以及私有前缀 > < = 不得落屏
        emulator.processInput("\u001B[2 q\u001B[>0c\u001B[<u\u001B[=1hHello")
        assertEquals("Hello", buffer.getLineString(0).trimEnd())

        // 超长 CSI 参数 (> 256) 不崩溃且安全消费
        val longParams = "1;".repeat(200) + "0"
        emulator.processInput("\u001B[${longParams}mWorld")
        assertEquals("HelloWorld", buffer.getLineString(0).trimEnd())
    }

    @Test
    fun testSgrSemicolonAndColonColorsAndUnderline() {
        val buffer = TerminalBuffer(cols = 20, rows = 3)
        val emulator = TerminalEmulator(buffer)

        // 冒号 TrueColor 前景、冒号 256 色背景、波浪下划线 4:3
        emulator.processInput("\u001B[4:3;38:2::12:34:56;48:5:196mA")
        val cellA = buffer.mainScreen[0].cells[0]
        assertEquals('A', cellA.char)
        assertTrue(cellA.isUnderline)
        assertEquals(Color(12, 34, 56), cellA.fgColor)
        assertEquals(Color(255, 0, 0), cellA.bgColor) // 256色 196 = (5*51, 0, 0)

        // 4:0 关闭下划线，分号 TrueColor
        emulator.processInput("\u001B[4:0;38;2;7;8;9mB")
        val cellB = buffer.mainScreen[0].cells[1]
        assertEquals('B', cellB.char)
        assertFalse(cellB.isUnderline)
        assertEquals(Color(7, 8, 9), cellB.fgColor)
    }

    @Test
    fun testCsiZeroNormalizationAndWrapPendingReset() {
        val buffer = TerminalBuffer(cols = 5, rows = 3)
        val emulator = TerminalEmulator(buffer)

        // 写满 5 个字符触发 wrapPending，光标停在 col=4，\b 退格至 col=3 并清 wrapPending，写 'X' 不得换行
        emulator.processInput("12345\bX")
        assertEquals(0, buffer.cursorRow)
        assertEquals("123X5", buffer.getLineString(0))

        // 行末再次写满后执行 CSI 0D (归一化为左移 1 格到 col=3) 并清 wrapPending，写 'Y' 不得换行
        emulator.processInput("\u001B[1;1H12345\u001B[0DY")
        assertEquals(0, buffer.cursorRow)
        assertEquals("123Y5", buffer.getLineString(0))

        // CSI 0;0H 归一化为 (1, 1) 即 (0, 0)
        emulator.processInput("\u001B[0;0H")
        assertEquals(0, buffer.cursorRow)
        assertEquals(0, buffer.cursorCol)
    }

    @Test
    fun testOscTerminationWithBelAndStAndEscFallback() {
        val buffer = TerminalBuffer(cols = 20, rows = 3)
        val emulator = TerminalEmulator(buffer)

        // BEL 结束与 ST (\u001B\) 结束（反斜杠绝不能落屏）
        emulator.processInput("\u001B]0;WinTitle\u0007A\u001B]2;OtherTitle\u001B\\B")
        // OSC 中遇到 ESC 后若不是 '\'，需回退到 ESC 处理后续序列
        emulator.processInput("\u001B]0;Aborted\u001B[31mC")
        assertEquals("ABC", buffer.getLineString(0).trimEnd())
        assertEquals(buffer.theme.palette[1], buffer.mainScreen[0].cells[2].fgColor)
    }

    @Test
    fun testCtrlCodeSpecialKeysAndZeroCapacityEventLog() {
        assertEquals(byteArrayOf(0x00).toList(), TerminalKeyCodes.getCtrlCode(' ').toList())
        assertEquals(byteArrayOf(0x1F).toList(), TerminalKeyCodes.getCtrlCode('/').toList())
        assertEquals(byteArrayOf(0x7F).toList(), TerminalKeyCodes.getCtrlCode('?').toList())

        val zeroLog = SessionEventLog(capacity = 0)
        zeroLog.append(ConnectionEvent(timestamp = 1L, type = ConnectionEventType.CONNECTING))
        zeroLog.append(ConnectionEvent(timestamp = 2L, type = ConnectionEventType.CONNECTED))
        assertEquals(1, zeroLog.snapshot().size)
        assertEquals(ConnectionEventType.CONNECTED, zeroLog.latest()?.type)
    }

    @Test
    fun testConcurrentProcessInputAndResize() {
        val buffer = TerminalBuffer(cols = 40, rows = 12)
        val emulator = TerminalEmulator(buffer)
        val failure = AtomicReference<Throwable?>(null)
        val latch = CountDownLatch(2)

        val writer = Thread {
            try {
                repeat(400) { idx ->
                    emulator.processInput("Line $idx 中文 😀\r\n")
                }
            } catch (t: Throwable) {
                failure.compareAndSet(null, t)
            } finally {
                latch.countDown()
            }
        }
        val resizer = Thread {
            try {
                repeat(200) { idx ->
                    val cols = if (idx % 2 == 0) 24 else 50
                    val rows = if (idx % 2 == 0) 8 else 16
                    buffer.resize(cols, rows)
                }
            } catch (t: Throwable) {
                failure.compareAndSet(null, t)
            } finally {
                latch.countDown()
            }
        }
        writer.start()
        resizer.start()
        latch.await()

        assertNull("并发输入与 resize 不应抛出异常", failure.get())
    }

    private fun snapshot(buffer: TerminalBuffer): List<String> = (0 until buffer.rows).map { row ->
        buffer.getLineString(row)
    }
}
