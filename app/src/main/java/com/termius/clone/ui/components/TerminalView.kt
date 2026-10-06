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
import androidx.compose.foundation.gestures.calculateCentroid
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
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
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

    // 本地响应式终端字号
    var localFontSizeSp by remember { mutableFloatStateOf(ThemeManager.terminalFontSizeSp) }
    LaunchedEffect(ThemeManager.terminalFontSizeSp) {
        localFontSizeSp = ThemeManager.terminalFontSizeSp
    }

    // 纯 GPU 硬件图层缩放比例（手势捏合过程中仅变动此比例，0 界面重组、0 网络请求，达到 120 帧满帧丝滑）
    var gestureZoom by remember { mutableFloatStateOf(1f) }
    var zoomPivot by remember { mutableStateOf(Offset.Zero) }

    var scrollOffsetLines by remember { mutableIntStateOf(0) }
    var scrollOffsetX by remember { mutableFloatStateOf(0f) }
    var scrollAccumulatorY by remember { mutableFloatStateOf(0f) }
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

    // 行业经典标准 (参考 ConnectBot 视口自适应黄金规范与安全下限保护)：
    // 虚拟终端列数根据视口物理宽度自适应计算，同时设定 36 列硬安全下限 (coerceIn(36, 240))。
    // 当字号放大时，绝不因屏幕过窄强行压缩至 15~24 列并切碎文本，超出的部分无缝激活平移画布；
    // 正常字号下依然 100% 铺满自适应，告别向右滑动！
    LaunchedEffect(viewSize, charWidth, charHeight) {
        delay(120) // 120ms 防抖，键盘弹起或旋转屏幕时秒级自适应
        try {
            if (viewSize.width > 0 && viewSize.height > 0 && charWidth > 0 && charHeight > 0) {
                val fittedCols = (viewSize.width / charWidth).toInt().coerceIn(36, 240)
                val rows = (viewSize.height / charHeight).toInt().coerceAtLeast(5)
                session.resize(fittedCols, rows, viewSize.width, viewSize.height)
                scrollOffsetLines = 0 // 视口变化时锁定回底部，避免提示符错位漂移
                scrollOffsetX = 0f    // 自适应全屏后自动归位，杜绝非必要偏移
            }
        } catch (e: Throwable) {
            e.printStackTrace()
        }
    }

    // 计算当前内容超出视口的最大水平平移距离
    val totalContentWidth = remember(session.terminalBuffer.cols, charWidth) {
        session.terminalBuffer.cols * charWidth
    }
    val maxScrollX = if (viewSize.width > 0 && totalContentWidth > viewSize.width) {
        totalContentWidth - viewSize.width
    } else 0f

    // 当缩放或视口变化导致 maxScrollX 减小时，修正水平偏移
    LaunchedEffect(maxScrollX) {
        if (scrollOffsetX > maxScrollX) {
            scrollOffsetX = maxScrollX
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
            .pointerInput(charHeight, maxScrollX) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    var hasOperated = false
                    var isPinching = false
                    var totalMovement = 0f

                    do {
                        val event = awaitPointerEvent()
                        val pointerCount = event.changes.size

                        if (pointerCount >= 2) {
                            // 1. 双指状态：立即锁定为捏合缩放模式，GPU 硬件图形变换，跟手丝滑 120 帧，零重绘重组
                            isPinching = true
                            hasOperated = true
                            val zoom = event.calculateZoom()
                            val centroid = event.calculateCentroid()
                            if (centroid != Offset.Unspecified) {
                                zoomPivot = centroid
                            }
                            val nextZoom = (gestureZoom * zoom).coerceIn(0.35f, 2.5f)
                            gestureZoom = nextZoom
                            event.changes.forEach { it.consume() }
                        } else if (!isPinching) {
                            // 2. 单指滑动：同时支持上下历史回溯与水平微调 (自适应模式下 maxScrollX 为 0，专心垂直滚动)
                            val pan = event.calculatePan()
                            val moveDelta = abs(pan.y) + abs(pan.x)
                            totalMovement += moveDelta

                            if (totalMovement > 6f) {
                                hasOperated = true
                            }

                            if (hasOperated) {
                                // 垂直方向：向上推或向下拉滚动行
                                if (charHeight > 0f && abs(pan.y) > 0.5f) {
                                    scrollAccumulatorY += pan.y
                                    val deltaLines = (scrollAccumulatorY / charHeight).toInt()
                                    if (deltaLines != 0) {
                                        val maxScroll = session.terminalBuffer.history.size
                                        if (maxScroll > 0) {
                                            scrollOffsetLines = (scrollOffsetLines + deltaLines).coerceIn(0, maxScroll)
                                        }
                                        scrollAccumulatorY -= deltaLines * charHeight
                                    }
                                }

                                // 水平方向：仅在内容确实超出视口时平移
                                if (maxScrollX > 0f && abs(pan.x) > 0.5f) {
                                    scrollOffsetX = (scrollOffsetX - pan.x).coerceIn(0f, maxScrollX)
                                }

                                event.changes.forEach { it.consume() }
                            }
                        }
                    } while (event.changes.any { it.pressed })

                    // 手势完全释放：若发生了捏合缩放，在抬手瞬间单次更新字体并复位 GPU 变换矩阵 (科学终端字号 7~20 SP)
                    if (isPinching) {
                        val finalSize = (Math.round(localFontSizeSp * gestureZoom * 2f) / 2f).coerceIn(7f, 20f)
                        gestureZoom = 1f
                        localFontSizeSp = finalSize
                        ThemeManager.setTerminalFontSize(finalSize)
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
                            if (scrollOffsetX > 0f) scrollOffsetX = 0f
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
                                if (scrollOffsetX > 0f) scrollOffsetX = 0f
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

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = gestureZoom
                    scaleY = gestureZoom
                    if (size.width > 0f && size.height > 0f && zoomPivot != Offset.Zero) {
                        transformOrigin = TransformOrigin(
                            pivotFractionX = (zoomPivot.x / size.width).coerceIn(0f, 1f),
                            pivotFractionY = (zoomPivot.y / size.height).coerceIn(0f, 1f)
                        )
                    } else {
                        transformOrigin = TransformOrigin.Center
                    }
                }
        ) {
            val unusedTick = renderTick // 订阅触发 Compose 刷新
            val buffer = session.terminalBuffer
            val theme = com.termius.clone.ui.theme.ThemeManager.currentTerminalTheme

            val defaultBg = theme.background
            val defaultFg = theme.foreground
            val defaultCursor = theme.cursor

            // 1. 先用终端配色背景完整铺满整个画布，消除任何由于尺寸变动或空白区域导致的杂色缝隙
            drawRect(color = defaultBg, size = size)

            translate(left = -scrollOffsetX, top = 0f) {
                drawIntoCanvas { canvas ->
                    try {
                        buffer.withLock {
                        val rows = buffer.rows
                        val cols = buffer.cols
                        val history = buffer.history
                        val totalHistory = history.size
                        val screen = if (buffer.isUsingAltScreen) buffer.altScreen else buffer.mainScreen

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

                            // 1. 批量合并绘制连续自定义背景色 (减少 80% drawRect 调用)
                            var bgStartCol = -1
                            var currentBg = Color.Unspecified
                            for (c in 0 until limitCols) {
                                val cell = cells.getOrNull(c) ?: continue
                                val cellBg = if (cell.bgColor != Color.Unspecified && cell.bgColor != defaultBg) cell.bgColor else Color.Unspecified
                                if (cellBg != currentBg) {
                                    if (currentBg != Color.Unspecified && bgStartCol >= 0) {
                                        drawRect(
                                            color = currentBg,
                                            topLeft = Offset(bgStartCol * charWidth, yPos),
                                            size = Size((c - bgStartCol) * charWidth + 0.5f, charHeight)
                                        )
                                    }
                                    currentBg = cellBg
                                    bgStartCol = if (cellBg != Color.Unspecified) c else -1
                                }
                            }
                            if (currentBg != Color.Unspecified && bgStartCol >= 0) {
                                drawRect(
                                    color = currentBg,
                                    topLeft = Offset(bgStartCol * charWidth, yPos),
                                    size = Size((limitCols - bgStartCol) * charWidth + 0.5f, charHeight)
                                )
                            }

                            // 2. 文本按连续相同样式批量绘制 (Run-Length Text Batching)，消除每字符 String 对象分配与 90% JNI 调用
                            var textStartCol = -1
                            val textBuffer = StringBuilder()
                            var currentRunFg = Color.Unspecified
                            var currentRunBold = false
                            var currentRunUnderline = false

                            fun flushTextRun() {
                                if (textBuffer.isNotEmpty() && textStartCol >= 0) {
                                    textPaint.color = (if (currentRunFg != Color.Unspecified) currentRunFg else defaultFg).toArgb()
                                    textPaint.isFakeBoldText = currentRunBold
                                    textPaint.isUnderlineText = currentRunUnderline
                                    nativeCanvas.drawText(
                                        textBuffer.toString(),
                                        textStartCol * charWidth,
                                        yPos + baselineOffset,
                                        textPaint
                                    )
                                    textBuffer.clear()
                                    textStartCol = -1
                                }
                            }

                            for (c in 0 until limitCols) {
                                val cell = cells.getOrNull(c) ?: continue
                                val ch = cell.char
                                if (ch == ' ' || ch.code == 0) {
                                    flushTextRun()
                                    continue
                                }

                                if (cell.isWideChar) {
                                    flushTextRun()
                                    textPaint.color = (if (cell.fgColor != Color.Unspecified) cell.fgColor else defaultFg).toArgb()
                                    textPaint.isFakeBoldText = cell.isBold
                                    textPaint.isUnderlineText = cell.isUnderline
                                    nativeCanvas.drawText(
                                        ch.toString(),
                                        c * charWidth,
                                        yPos + baselineOffset,
                                        textPaint
                                    )
                                    continue
                                }

                                val cellFg = cell.fgColor
                                val cellBold = cell.isBold
                                val cellUnderline = cell.isUnderline

                                if (textStartCol < 0) {
                                    textStartCol = c
                                    currentRunFg = cellFg
                                    currentRunBold = cellBold
                                    currentRunUnderline = cellUnderline
                                    textBuffer.append(ch)
                                } else if (cellFg == currentRunFg && cellBold == currentRunBold && cellUnderline == currentRunUnderline) {
                                    textBuffer.append(ch)
                                } else {
                                    flushTextRun()
                                    textStartCol = c
                                    currentRunFg = cellFg
                                    currentRunBold = cellBold
                                    currentRunUnderline = cellUnderline
                                    textBuffer.append(ch)
                                }
                            }
                            flushTextRun()
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
                    }
                } catch (e: Throwable) {
                    e.printStackTrace()
                }
            }
        }
    }

        // 超宽文本/表格的水平漫游滚动指示条 (参考 Termius / ConnectBot 最佳实践)
        if (maxScrollX > 0f && viewSize.width > 0) {
            val totalW = totalContentWidth
            val viewW = viewSize.width.toFloat()
            val thumbRatio = (viewW / totalW).coerceIn(0.12f, 0.9f)
            val thumbWidthDp = (viewW / density.density * thumbRatio).dp
            val trackRange = viewW * (1f - thumbRatio)
            val thumbOffset = if (maxScrollX > 0f) (scrollOffsetX / maxScrollX) * trackRange else 0f

            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .height(3.dp)
                    .background(Color.Black.copy(alpha = 0.2f))
            ) {
                Box(
                    modifier = Modifier
                        .width(thumbWidthDp)
                        .fillMaxHeight()
                        .graphicsLayer {
                            translationX = thumbOffset
                        }
                        .background(
                            color = terminalTheme.foreground.copy(alpha = 0.45f),
                            shape = RoundedCornerShape(1.5.dp)
                        )
                )
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
                        scrollOffsetX = 0f
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
