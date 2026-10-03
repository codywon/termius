package com.termius.clone.ui.components

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
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
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.termius.clone.terminal.engine.TerminalKeyCodes
import com.termius.clone.terminal.session.SshSession
import com.termius.clone.ui.theme.LocalAppTheme
import com.termius.clone.ui.theme.ThemeManager
import kotlinx.coroutines.delay
import kotlin.math.abs

@Composable
fun TerminalView(
    session: SshSession,
    isCtrlActive: Boolean,
    onConsumeCtrl: () -> Unit,
    isAltActive: Boolean,
    onConsumeAlt: () -> Unit,
    modifier: Modifier = Modifier,
    externalFocusRequester: FocusRequester? = null,
    onTapTerminal: (() -> Unit)? = null
) {
    // 监听重新渲染 tick
    val renderTick by session.renderTick.collectAsState()
    val keyboardController = LocalSoftwareKeyboardController.current
    val internalFocusRequester = remember { FocusRequester() }
    val focusRequester = externalFocusRequester ?: internalFocusRequester

    val appTheme = LocalAppTheme.current

    // 本地响应式终端字号 (在双指捏合缩放时仅在内存平滑变动，手势释放时才提交持久化，彻底根除卡顿迟滞)
    var localFontSizeSp by remember { mutableFloatStateOf(ThemeManager.terminalFontSizeSp) }
    LaunchedEffect(ThemeManager.terminalFontSizeSp) {
        localFontSizeSp = ThemeManager.terminalFontSizeSp
    }

    var scrollOffsetLines by remember { mutableIntStateOf(0) }
    var scrollAccumulator by remember { mutableFloatStateOf(0f) }
    var viewSize by remember { mutableStateOf(IntSize.Zero) }

    // 关键哨兵字符：确保系统输入法在按退格键时永远有字符可删，彻底解决软键盘回删不起作用的业界难题
    val sentinel = "\u200B"
    var textFieldValue by remember {
        mutableStateOf(TextFieldValue(sentinel, androidx.compose.ui.text.TextRange(sentinel.length)))
    }

    val density = LocalDensity.current
    val textPaint = remember(localFontSizeSp) {
        Paint().apply {
            isAntiAlias = true
            textSize = localFontSizeSp * density.density
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

    // 自动重算行与列并向 SSH PTY 发送尺寸更新 (加入 250ms 防抖，避免缩放途中的网络与重绘风暴)
    LaunchedEffect(viewSize, charWidth, charHeight) {
        delay(250)
        if (viewSize.width > 0 && viewSize.height > 0 && charWidth > 0 && charHeight > 0) {
            val cols = (viewSize.width / charWidth).toInt().coerceAtLeast(10)
            val rows = (viewSize.height / charHeight).toInt().coerceAtLeast(5)
            session.resize(cols, rows, viewSize.width, viewSize.height)
            scrollOffsetLines = 0 // 视口变化时锁定回底部，避免提示符错位漂移
        }
    }

    LaunchedEffect(Unit) {
        try {
            focusRequester.requestFocus()
        } catch (_: Exception) {}
    }

    val terminalTheme = com.termius.clone.ui.theme.ThemeManager.currentTerminalTheme

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(terminalTheme.background)
            .onSizeChanged { viewSize = it }
            .pointerInput(charHeight) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    var hasOperated = false
                    var isPinching = false
                    var totalMovement = 0f

                    do {
                        val event = awaitPointerEvent()
                        val pointerCount = event.changes.size

                        if (pointerCount >= 2) {
                            // 1. 双指状态：立即锁定为捏合缩放模式，绝不误触单指滚动，顺滑跟手 (120Hz 极速响应)
                            isPinching = true
                            hasOperated = true
                            val zoom = event.calculateZoom()
                            if (zoom != 1f) {
                                val updatedSize = (localFontSizeSp * zoom).coerceIn(9f, 26f)
                                localFontSizeSp = updatedSize
                            }
                            event.changes.forEach { it.consume() }
                        } else if (!isPinching) {
                            // 2. 仅当单指且未曾进入双指状态时，才处理上下滑动回溯
                            val pan = event.calculatePan()
                            val moveDelta = abs(pan.y) + abs(pan.x)
                            totalMovement += moveDelta

                            if (totalMovement > 8f) {
                                hasOperated = true
                            }

                            if (hasOperated && charHeight > 0f) {
                                // 手指向下拉 (pan.y > 0) -> 翻看上方过往历史 (增大 offset)
                                // 手指向上推 (pan.y < 0) -> 滑回最新输出行 (减小 offset)
                                scrollAccumulator += pan.y
                                val deltaLines = (scrollAccumulator / charHeight).toInt()
                                if (deltaLines != 0) {
                                    val maxScroll = session.terminalBuffer.history.size
                                    if (maxScroll > 0) {
                                        scrollOffsetLines = (scrollOffsetLines + deltaLines).coerceIn(0, maxScroll)
                                    }
                                    scrollAccumulator -= deltaLines * charHeight
                                }
                                event.changes.forEach { it.consume() }
                            }
                        }
                    } while (event.changes.any { it.pressed })

                    // 手势完全释放：若发生了捏合缩放，在此处单次持久化存储到全局并对齐到 0.5sp
                    if (isPinching) {
                        val roundedSize = (Math.round(localFontSizeSp * 2f) / 2f).coerceIn(9f, 26f)
                        localFontSizeSp = roundedSize
                        ThemeManager.setTerminalFontSize(roundedSize)
                    } else if (!hasOperated) {
                        // 若用户无明显滑动或捏合，判定为单指点击屏幕：唤起输入法并聚焦
                        try {
                            onTapTerminal?.invoke()
                            focusRequester.requestFocus()
                            keyboardController?.show()
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }
            }
    ) {
        // 关键：透明真实输入框，承接 Android 系统 IME 键盘交互
        BasicTextField(
            value = textFieldValue,
            onValueChange = { newValue ->
                if (newValue.composition != null) {
                    // 输入法正在组合候选字符（如拼音输入）
                    textFieldValue = newValue
                } else {
                    val newText = newValue.text
                    // 软键盘按下了退格键：输入框内的哨兵字符被删除 (长度小于哨兵长度或为空)
                    if (newText.isEmpty() || newText.length < sentinel.length) {
                        session.write(TerminalKeyCodes.BACKSPACE)
                        textFieldValue = TextFieldValue(sentinel, androidx.compose.ui.text.TextRange(sentinel.length))
                    } else {
                        val insertedText = newText.replace(sentinel, "")
                        if (insertedText.isNotEmpty()) {
                            if (isCtrlActive && insertedText.length == 1) {
                                session.write(TerminalKeyCodes.getCtrlCode(insertedText[0]))
                                onConsumeCtrl()
                            } else if (isAltActive && insertedText.length == 1) {
                                session.write(TerminalKeyCodes.getAltSequence(insertedText[0]))
                                onConsumeAlt()
                            } else {
                                session.write(insertedText)
                            }
                        }
                        // 无论如何，均将输入框重置为持有哨兵字符，以便下一次按退格键能继续准确捕获
                        textFieldValue = TextFieldValue(sentinel, androidx.compose.ui.text.TextRange(sentinel.length))
                    }
                }
            },
            modifier = Modifier
                .size(1.dp)
                .alpha(0f)
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
            val theme = com.termius.clone.ui.theme.ThemeManager.currentTerminalTheme
            val rows = buffer.rows
            val cols = buffer.cols

            val history = buffer.history
            val totalHistory = history.size
            val screen = buffer.currentScreen

            val defaultBg = theme.background
            val defaultFg = theme.foreground
            val defaultCursor = theme.cursor

            // 1. 先用终端配色背景完整铺满整个画布，消除任何由于尺寸变动或空白区域导致的杂色缝隙
            drawRect(color = defaultBg, size = size)

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
                            if (cell.bgColor != Color.Unspecified && cell.bgColor != defaultBg) {
                                val bgWidth = if (cell.isWideChar) charWidth * 2f else charWidth + 0.5f
                                drawRect(
                                    color = cell.bgColor,
                                    topLeft = Offset(xPos, yPos),
                                    size = Size(bgWidth, charHeight)
                                )
                            }

                            // 绘制字符
                            if (cell.char != ' ' && cell.char.code > 0) {
                                textPaint.color = (if (cell.fgColor != Color.Unspecified) cell.fgColor else defaultFg).toArgb()
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
                            color = defaultCursor.copy(alpha = 0.7f),
                            topLeft = Offset(cursorX, cursorY),
                            size = Size(charWidth, charHeight)
                        )
                    }
                } catch (e: Throwable) {
                    e.printStackTrace()
                }
            }
        }

        // 一键回到底部悬浮圆形按钮 (右下方，带圆圈向下箭头，完全不遮挡终端顶部输出)
        AnimatedVisibility(
            visible = scrollOffsetLines > 0,
            enter = fadeIn() + scaleIn(),
            exit = fadeOut() + scaleOut(),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = 16.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = appTheme.surfaceContainerHigh.copy(alpha = 0.95f),
                border = BorderStroke(1.5.dp, appTheme.primary),
                shadowElevation = 8.dp,
                modifier = Modifier
                    .size(46.dp)
                    .clickable {
                        scrollOffsetLines = 0
                        try {
                            focusRequester.requestFocus()
                            keyboardController?.show()
                        } catch (_: Exception) {}
                    }
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.ArrowDownward,
                        contentDescription = "回到底部",
                        tint = appTheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    }
}
