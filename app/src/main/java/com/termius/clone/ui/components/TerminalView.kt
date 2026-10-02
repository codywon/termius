package com.termius.clone.ui.components

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
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
    modifier: Modifier = Modifier,
    externalFocusRequester: FocusRequester? = null
) {
    // 监听重新渲染 tick
    val renderTick by session.renderTick.collectAsState()
    val keyboardController = LocalSoftwareKeyboardController.current
    val internalFocusRequester = remember { FocusRequester() }
    val focusRequester = externalFocusRequester ?: internalFocusRequester

    var fontSizeSp by remember { mutableFloatStateOf(13f) }
    var scrollOffsetLines by remember { mutableIntStateOf(0) }
    var viewSize by remember { mutableStateOf(IntSize.Zero) }

    // 用于承载系统 IME 输入法的隐藏输入框状态
    var textFieldValue by remember { mutableStateOf(TextFieldValue("")) }

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
        try {
            focusRequester.requestFocus()
        } catch (_: Exception) {}
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(session.terminalBuffer.theme.background)
            .onSizeChanged { viewSize = it }
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = {
                        try {
                            focusRequester.requestFocus()
                            keyboardController?.show()
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
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
    ) {
        // 关键：透明真实输入框，承接 Android 系统 IME 键盘交互
        BasicTextField(
            value = textFieldValue,
            onValueChange = { newValue ->
                val newText = newValue.text
                if (newText.isNotEmpty()) {
                    if (isCtrlActive && newText.length == 1) {
                        session.write(TerminalKeyCodes.getCtrlCode(newText[0]))
                        onConsumeCtrl()
                    } else if (isAltActive && newText.length == 1) {
                        session.write(TerminalKeyCodes.getAltSequence(newText[0]))
                        onConsumeAlt()
                    } else {
                        session.write(newText)
                    }
                    textFieldValue = TextFieldValue("")
                } else {
                    textFieldValue = newValue
                }
            },
            modifier = Modifier
                .size(1.dp)
                .alpha(0.01f)
                .focusRequester(focusRequester)
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
                },
            keyboardOptions = KeyboardOptions(
                autoCorrect = false,
                keyboardType = KeyboardType.Ascii,
                imeAction = ImeAction.None
            ),
            keyboardActions = KeyboardActions(
                onAny = {
                    session.write(TerminalKeyCodes.ENTER)
                }
            )
        )

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
                                val bgWidth = if (cell.isWideChar) charWidth * 2f else charWidth + 0.5f
                                drawRect(
                                    color = cell.bgColor,
                                    topLeft = Offset(xPos, yPos),
                                    size = Size(bgWidth, charHeight)
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
