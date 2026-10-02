package com.termius.clone.ui.components

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.sp
import com.termius.clone.terminal.engine.TerminalKeyCodes
import com.termius.clone.terminal.session.SshSession

@Composable
fun TerminalView(
    session: SshSession,
    isCtrlActive: Boolean,
    onConsumeCtrl: () -> Unit,
    isAltActive: Boolean,
    onConsumeAlt: () -> Unit,
    modifier: Modifier = Modifier
) {
    // 监听重新渲染 tick
    val renderTick by session.renderTick.collectAsState()
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusRequester = remember { FocusRequester() }

    var fontSizeSp by remember { mutableFloatStateOf(13f) }
    var scrollOffsetLines by remember { mutableIntStateOf(0) }
    var viewSize by remember { mutableStateOf(IntSize.Zero) }

    val density = LocalDensity.current
    val textPaint = remember(fontSizeSp) {
        Paint().apply {
            isAntiAlias = true
            textSize = fontSizeSp * density.density
            typeface = Typeface.MONOSPACE
            style = Paint.Style.FILL
        }
    }

    val charWidth = remember(textPaint) { textPaint.measureText("W") }
    val charHeight = remember(textPaint) {
        val fm = textPaint.fontMetrics
        fm.descent - fm.ascent
    }
    val baselineOffset = remember(textPaint) { -textPaint.fontMetrics.ascent }

    // 自动重算行与列并向 SSH PTY 发送尺寸更新
    LaunchedEffect(viewSize, charWidth, charHeight) {
        if (viewSize.width > 0 && viewSize.height > 0 && charWidth > 0 && charHeight > 0) {
            val cols = (viewSize.width / charWidth).toInt().coerceAtLeast(10)
            val rows = (viewSize.height / charHeight).toInt().coerceAtLeast(5)
            session.resize(cols, rows, viewSize.width, viewSize.height)
        }
    }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(session.terminalBuffer.theme.background)
            .onSizeChanged { viewSize = it }
            .focusRequester(focusRequester)
            .focusable()
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = {
                        focusRequester.requestFocus()
                        keyboardController?.show()
                    }
                )
            }
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    // 双指缩放调整字体大小
                    if (zoom != 1f) {
                        fontSizeSp = (fontSizeSp * zoom).coerceIn(9f, 24f)
                    }
                    // 纵向拖动滚动回退历史
                    if (pan.y != 0f && charHeight > 0) {
                        val linesScrolled = (pan.y / charHeight).toInt()
                        val maxScroll = session.terminalBuffer.history.size
                        scrollOffsetLines = (scrollOffsetLines - linesScrolled).coerceIn(0, maxScroll)
                    }
                }
            }
            .onKeyEvent { keyEvent ->
                if (keyEvent.type == KeyEventType.KeyDown) {
                    val nativeEvent = keyEvent.nativeKeyEvent
                    when (nativeEvent.keyCode) {
                        android.view.KeyEvent.KEYCODE_DEL -> {
                            session.write(TerminalKeyCodes.BACKSPACE)
                            return@onKeyEvent true
                        }
                        android.view.KeyEvent.KEYCODE_ENTER -> {
                            session.write(TerminalKeyCodes.ENTER)
                            return@onKeyEvent true
                        }
                        android.view.KeyEvent.KEYCODE_TAB -> {
                            session.write(TerminalKeyCodes.TAB)
                            return@onKeyEvent true
                        }
                        android.view.KeyEvent.KEYCODE_ESCAPE -> {
                            session.write(TerminalKeyCodes.ESC)
                            return@onKeyEvent true
                        }
                    }

                    val unicodeChar = nativeEvent.unicodeChar
                    if (unicodeChar > 0) {
                        val char = unicodeChar.toChar()
                        if (isCtrlActive) {
                            session.write(TerminalKeyCodes.getCtrlCode(char))
                            onConsumeCtrl()
                        } else if (isAltActive) {
                            session.write(TerminalKeyCodes.getAltSequence(char))
                            onConsumeAlt()
                        } else {
                            session.write(char.toString())
                        }
                        return@onKeyEvent true
                    }
                }
                false
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val unusedTick = renderTick // 订阅触发 Compose 刷新
            val buffer = session.terminalBuffer
            val theme = buffer.theme
            val rows = buffer.rows
            val cols = buffer.cols

            val history = buffer.history
            val totalHistory = history.size
            val screen = buffer.currentScreen

            drawIntoCanvas { canvas ->
                try {
                    val nativeCanvas = canvas.nativeCanvas

                    for (r in 0 until rows) {
                        val line = if (scrollOffsetLines > 0 && !buffer.isUsingAltScreen) {
                            val historyIndex = totalHistory - scrollOffsetLines + r
                            if (historyIndex in 0 until totalHistory) {
                                history.getOrNull(historyIndex)
                            } else {
                                val screenRow = historyIndex - totalHistory
                                if (screenRow in 0 until rows) screen.getOrNull(screenRow) else null
                            }
                        } else {
                            screen.getOrNull(r)
                        } ?: continue

                        val yPos = r * charHeight
                        val cells = line.cells
                        val limitCols = minOf(cols, cells.size)

                        for (c in 0 until limitCols) {
                            val cell = cells.getOrNull(c) ?: continue
                            val xPos = c * charWidth

                            // 绘制自定义背景色
                            if (cell.bgColor != Color.Unspecified && cell.bgColor != theme.background) {
                                drawRect(
                                    color = cell.bgColor,
                                    topLeft = Offset(xPos, yPos),
                                    size = Size(charWidth + 0.5f, charHeight)
                                )
                            }

                            // 绘制字符
                            if (cell.char != ' ' && cell.char.code > 0) {
                                textPaint.color = (if (cell.fgColor != Color.Unspecified) cell.fgColor else theme.foreground).toArgb()
                                textPaint.isFakeBoldText = cell.isBold
                                textPaint.isUnderlineText = cell.isUnderline

                                nativeCanvas.drawText(
                                    cell.char.toString(),
                                    xPos,
                                    yPos + baselineOffset,
                                    textPaint
                                )
                            }
                        }
                    }

                    // 绘制终端光标 (如果未滚动且光标可见)
                    if (scrollOffsetLines == 0 && buffer.isCursorVisible) {
                        val safeCol = buffer.cursorCol.coerceIn(0, maxOf(0, cols - 1))
                        val safeRow = buffer.cursorRow.coerceIn(0, maxOf(0, rows - 1))
                        val cursorX = safeCol * charWidth
                        val cursorY = safeRow * charHeight
                        drawRect(
                            color = theme.cursor.copy(alpha = 0.7f),
                            topLeft = Offset(cursorX, cursorY),
                            size = Size(charWidth, charHeight)
                        )
                    }
                } catch (e: Throwable) {
                    e.printStackTrace()
                }
            }
        }
    }
}
