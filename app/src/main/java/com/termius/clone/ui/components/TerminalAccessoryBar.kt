package com.termius.clone.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridItemScope
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import android.content.res.Configuration
import android.widget.Toast
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.zIndex
import kotlin.math.roundToInt
import com.termius.clone.data.local.QuickCommandManager
import com.termius.clone.data.model.QuickCommand
import com.termius.clone.terminal.engine.TerminalKeyCodes
import com.termius.clone.ui.theme.*

enum class TerminalInputMode {
    IME,          // 输入法模式：系统软键盘 + 上方单行全宽横滑辅助键
    SHORTCUTS,    // 快捷键模式：收起软键盘，展示快捷指令网格与自定义管理
    PC_KEYBOARD,  // 电脑键盘模式：收起软键盘，展示 F1-F12、方向键、翻页键等 PC 键位
    HIDDEN        // 完全隐藏：全屏终端
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TerminalAccessoryBar(
    currentMode: TerminalInputMode,
    onModeChange: (TerminalInputMode) -> Unit,
    isCtrlActive: Boolean,
    onToggleCtrl: () -> Unit,
    isAltActive: Boolean,
    onToggleAlt: () -> Unit,
    onSendKey: (String) -> Unit,
    onRequestShowKeyboard: () -> Unit,
    modifier: Modifier = Modifier
) {
    val theme = LocalAppTheme.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val clipboardManager = LocalClipboardManager.current
    val commands by QuickCommandManager.commands.collectAsState()
    val context = LocalContext.current

    val handleExecuteCommand: (QuickCommand) -> Unit = { cmd ->
        if (cmd.id == "ctrl_v" || cmd.command == "__CLIPBOARD_PASTE__") {
            val clipText = clipboardManager.getText()?.text
            if (!clipText.isNullOrEmpty()) {
                onSendKey(clipText)
            } else {
                Toast.makeText(context, "剪贴板为空", Toast.LENGTH_SHORT).show()
            }
        } else {
            onSendKey(cmd.command)
        }
    }

    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    val accessoryBtnHeight = if (isLandscape) 26.dp else 34.dp
    val accessoryFontSize = if (isLandscape) 10.sp else 11.sp

    var isEditMode by remember { mutableStateOf(false) }
    var editingCommand by remember { mutableStateOf<QuickCommand?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }
    var contextMenuCommand by remember { mutableStateOf<QuickCommand?>(null) }

    // 如果处于 HIDDEN 状态，不渲染主体
    if (currentMode == TerminalInputMode.HIDDEN) {
        return
    }

    // 关键优化：横屏输入法模式下，为了给终端争取极致的可视高度 (避免被软键盘和上下两层功能条挤满)
    // 将模式切换栏与常用辅助按键彻底合并为单行紧凑栏 (高度仅 34dp，参考网易 UU 远程和 Termius 最佳实践)
    if (isLandscape && currentMode == TerminalInputMode.IME) {
        Surface(
            color = theme.surfaceContainerLow,
            modifier = modifier
                .fillMaxWidth()
                .imePadding()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(34.dp)
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 1. 左侧三模微型切换胶囊 (紧凑型，高 26dp)
                Row(
                    modifier = Modifier
                        .height(26.dp)
                        .background(theme.surfaceContainerHigh, RoundedCornerShape(6.dp))
                        .padding(2.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(22.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(theme.primary.copy(alpha = 0.25f))
                            .clickable {
                                onModeChange(TerminalInputMode.IME)
                                onRequestShowKeyboard()
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Keyboard,
                            contentDescription = "输入法",
                            tint = theme.primary,
                            modifier = Modifier.size(15.dp)
                        )
                    }

                    Box(
                        modifier = Modifier
                            .size(22.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .clickable {
                                keyboardController?.hide()
                                onModeChange(TerminalInputMode.SHORTCUTS)
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.FlashOn,
                            contentDescription = "快捷键",
                            tint = theme.textMuted,
                            modifier = Modifier.size(15.dp)
                        )
                    }

                    Box(
                        modifier = Modifier
                            .size(22.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .clickable {
                                keyboardController?.hide()
                                onModeChange(TerminalInputMode.PC_KEYBOARD)
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Laptop,
                            contentDescription = "电脑键盘",
                            tint = theme.textMuted,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }

                // 细垂直分割线
                Box(
                    modifier = Modifier
                        .padding(horizontal = 5.dp)
                        .width(1.dp)
                        .height(18.dp)
                        .background(ObsidianOutlineVariant.copy(alpha = 0.5f))
                )

                // 2. 中间横滑按键列表
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AccessoryButton(label = "ESC", height = 26.dp, fontSize = 10.sp, onClick = { onSendKey(TerminalKeyCodes.ESC) })
                    AccessoryButton(label = "TAB", height = 26.dp, fontSize = 10.sp, onClick = { onSendKey(TerminalKeyCodes.TAB) })
                    AccessoryButton(label = "⌫", height = 26.dp, fontSize = 10.sp, textColor = ObsidianError, onClick = { onSendKey(TerminalKeyCodes.BACKSPACE) })
                    AccessoryButton(
                        label = "CTRL",
                        height = 26.dp,
                        fontSize = 10.sp,
                        isActive = isCtrlActive,
                        activeBg = ObsidianPrimary.copy(alpha = 0.25f),
                        activeBorder = ObsidianPrimary,
                        onClick = onToggleCtrl
                    )
                    AccessoryButton(
                        label = "ALT",
                        height = 26.dp,
                        fontSize = 10.sp,
                        isActive = isAltActive,
                        activeBg = ObsidianPrimary.copy(alpha = 0.25f),
                        activeBorder = ObsidianPrimary,
                        onClick = onToggleAlt
                    )
                    AccessoryButton(label = "/", height = 26.dp, fontSize = 10.sp, onClick = { onSendKey("/") })
                    AccessoryButton(label = "-", height = 26.dp, fontSize = 10.sp, onClick = { onSendKey("-") })
                    AccessoryButton(label = "|", height = 26.dp, fontSize = 10.sp, onClick = { onSendKey("|") })
                    AccessoryButton(label = "~", height = 26.dp, fontSize = 10.sp, onClick = { onSendKey("~") })
                    AccessoryButton(label = ":", height = 26.dp, fontSize = 10.sp, onClick = { onSendKey(":") })
                    AccessoryButton(label = "$", height = 26.dp, fontSize = 10.sp, onClick = { onSendKey("$") })

                    // 方向键
                    AccessoryIconButton(icon = Icons.Default.KeyboardArrowUp, size = 26.dp) { onSendKey(TerminalKeyCodes.ARROW_UP) }
                    AccessoryIconButton(icon = Icons.Default.KeyboardArrowDown, size = 26.dp) { onSendKey(TerminalKeyCodes.ARROW_DOWN) }
                    AccessoryIconButton(icon = Icons.AutoMirrored.Filled.ArrowBack, size = 26.dp) { onSendKey(TerminalKeyCodes.ARROW_LEFT) }
                    AccessoryIconButton(icon = Icons.AutoMirrored.Filled.ArrowForward, size = 26.dp) { onSendKey(TerminalKeyCodes.ARROW_RIGHT) }

                    // Ctrl+C 中断
                    AccessoryButton(label = "Ctrl+C", height = 26.dp, fontSize = 10.sp, textColor = ObsidianError, onClick = { onSendKey("\u0003") })

                    // PASTE 剪贴板
                    Surface(
                        modifier = Modifier
                            .height(26.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .clickable {
                                val clip = clipboardManager.getText()?.text
                                if (!clip.isNullOrEmpty()) onSendKey(clip)
                            },
                        shape = RoundedCornerShape(6.dp),
                        color = ObsidianSurfaceContainerHigh,
                        border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianOutlineVariant)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Icon(Icons.Default.ContentPaste, contentDescription = "Paste", tint = ObsidianTextPrimary, modifier = Modifier.size(11.dp))
                            Text("PASTE", fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = ObsidianTextPrimary, fontFamily = FontFamily.Monospace)
                        }
                    }
                }

                // 细垂直分割线
                Box(
                    modifier = Modifier
                        .padding(horizontal = 3.dp)
                        .width(1.dp)
                        .height(18.dp)
                        .background(ObsidianOutlineVariant.copy(alpha = 0.5f))
                )

                // 3. 右侧收起键盘图标
                IconButton(
                    onClick = {
                        keyboardController?.hide()
                        onModeChange(TerminalInputMode.HIDDEN)
                    },
                    modifier = Modifier.size(26.dp)
                ) {
                    Icon(
                        Icons.Default.KeyboardHide,
                        contentDescription = "收起键盘",
                        tint = ObsidianTextMuted,
                        modifier = Modifier.size(17.dp)
                    )
                }
            }
        }
        return
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .then(
                // 只有在输入法模式下才应用 imePadding 贴合系统键盘；快捷键与电脑键盘模式下收起输入法，不使用 imePadding 避免叠加
                if (currentMode == TerminalInputMode.IME) Modifier.imePadding() else Modifier
            )
            .background(theme.surfaceContainerLow)
    ) {
        // -------------------------------------------------------------------------
        // 1. 顶层 Tab 控制栏 (参考网易 UU 远程：输入法 | 快捷键 | 电脑键盘 | 关闭)
        // -------------------------------------------------------------------------
        Surface(
            color = theme.surfaceContainerLow,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(if (isLandscape) 30.dp else 42.dp)
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Tab 1: 输入法
                UUTabItem(
                    title = "输入法",
                    icon = Icons.Default.Keyboard,
                    isSelected = currentMode == TerminalInputMode.IME,
                    isLandscape = isLandscape,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        onModeChange(TerminalInputMode.IME)
                        onRequestShowKeyboard()
                    }
                )

                // Tab 2: 快捷键
                UUTabItem(
                    title = "快捷键",
                    icon = Icons.Default.FlashOn,
                    isSelected = currentMode == TerminalInputMode.SHORTCUTS,
                    isLandscape = isLandscape,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        keyboardController?.hide()
                        onModeChange(TerminalInputMode.SHORTCUTS)
                    }
                )

                // Tab 3: 电脑键盘
                UUTabItem(
                    title = "电脑键盘",
                    icon = Icons.Default.Laptop,
                    isSelected = currentMode == TerminalInputMode.PC_KEYBOARD,
                    isLandscape = isLandscape,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        keyboardController?.hide()
                        onModeChange(TerminalInputMode.PC_KEYBOARD)
                    }
                )

                // 关闭按钮 (X)
                IconButton(
                    onClick = {
                        keyboardController?.hide()
                        onModeChange(TerminalInputMode.HIDDEN)
                    },
                    modifier = Modifier.size(if (isLandscape) 26.dp else 32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Cancel,
                        contentDescription = "Close Bar",
                        tint = ObsidianTextMuted,
                        modifier = Modifier.size(if (isLandscape) 16.dp else 20.dp)
                    )
                }
            }
        }

        // -------------------------------------------------------------------------
        // 2. 模式分支展示区
        // -------------------------------------------------------------------------
        when (currentMode) {
            TerminalInputMode.IME -> {
                // 输入法模式：经典全宽单行横滑按键条（100% 宽度全部用于按键，绝不挤压）
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 6.dp, vertical = if (isLandscape) 2.dp else 5.dp)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(if (isLandscape) 4.dp else 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AccessoryButton(label = "ESC", height = accessoryBtnHeight, fontSize = accessoryFontSize, onClick = { onSendKey(TerminalKeyCodes.ESC) })
                    AccessoryButton(label = "TAB", height = accessoryBtnHeight, fontSize = accessoryFontSize, onClick = { onSendKey(TerminalKeyCodes.TAB) })
                    AccessoryButton(label = "⌫", height = accessoryBtnHeight, fontSize = accessoryFontSize, textColor = ObsidianError, onClick = { onSendKey(TerminalKeyCodes.BACKSPACE) })
                    AccessoryButton(
                        label = "CTRL",
                        height = accessoryBtnHeight,
                        fontSize = accessoryFontSize,
                        isActive = isCtrlActive,
                        activeBg = ObsidianPrimary.copy(alpha = 0.25f),
                        activeBorder = ObsidianPrimary,
                        onClick = onToggleCtrl
                    )
                    AccessoryButton(
                        label = "ALT",
                        height = accessoryBtnHeight,
                        fontSize = accessoryFontSize,
                        isActive = isAltActive,
                        activeBg = ObsidianPrimary.copy(alpha = 0.25f),
                        activeBorder = ObsidianPrimary,
                        onClick = onToggleAlt
                    )
                    AccessoryButton(label = "/", height = accessoryBtnHeight, fontSize = accessoryFontSize, onClick = { onSendKey("/") })
                    AccessoryButton(label = "-", height = accessoryBtnHeight, fontSize = accessoryFontSize, onClick = { onSendKey("-") })
                    AccessoryButton(label = "|", height = accessoryBtnHeight, fontSize = accessoryFontSize, onClick = { onSendKey("|") })
                    AccessoryButton(label = "~", height = accessoryBtnHeight, fontSize = accessoryFontSize, onClick = { onSendKey("~") })
                    AccessoryButton(label = ":", height = accessoryBtnHeight, fontSize = accessoryFontSize, onClick = { onSendKey(":") })
                    AccessoryButton(label = "$", height = accessoryBtnHeight, fontSize = accessoryFontSize, onClick = { onSendKey("$") })

                    // 方向键
                    AccessoryIconButton(icon = Icons.Default.KeyboardArrowUp, size = accessoryBtnHeight) { onSendKey(TerminalKeyCodes.ARROW_UP) }
                    AccessoryIconButton(icon = Icons.Default.KeyboardArrowDown, size = accessoryBtnHeight) { onSendKey(TerminalKeyCodes.ARROW_DOWN) }
                    AccessoryIconButton(icon = Icons.AutoMirrored.Filled.ArrowBack, size = accessoryBtnHeight) { onSendKey(TerminalKeyCodes.ARROW_LEFT) }
                    AccessoryIconButton(icon = Icons.AutoMirrored.Filled.ArrowForward, size = accessoryBtnHeight) { onSendKey(TerminalKeyCodes.ARROW_RIGHT) }

                    // Ctrl+C 中断
                    AccessoryButton(label = "Ctrl+C", height = accessoryBtnHeight, fontSize = accessoryFontSize, textColor = ObsidianError, onClick = { onSendKey("\u0003") })

                    // PASTE 剪贴板
                    Surface(
                        modifier = Modifier
                            .height(accessoryBtnHeight)
                            .clip(RoundedCornerShape(6.dp))
                            .clickable {
                                val clip = clipboardManager.getText()?.text
                                if (!clip.isNullOrEmpty()) onSendKey(clip)
                            },
                        shape = RoundedCornerShape(6.dp),
                        color = ObsidianSurfaceContainerHigh,
                        border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianOutlineVariant)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = if (isLandscape) 6.dp else 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Icon(Icons.Default.ContentPaste, contentDescription = "Paste", tint = ObsidianTextPrimary, modifier = Modifier.size(if (isLandscape) 11.dp else 13.dp))
                            Text("PASTE", fontSize = accessoryFontSize, fontWeight = FontWeight.SemiBold, color = ObsidianTextPrimary, fontFamily = FontFamily.Monospace)
                        }
                    }
                }
            }

            TerminalInputMode.SHORTCUTS -> {
                // 快捷键模式：竖屏 270dp 3列，横屏 148dp 5列紧凑排布 (参考网易 UU 远程，只占大半屏，终端清晰可见)
                val shortcutsPanelHeight = if (isLandscape) 148.dp else 270.dp
                val gridColumns = if (isLandscape) 5 else 3

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(shortcutsPanelHeight)
                        .padding(horizontal = 4.dp, vertical = 4.dp)
                ) {
                    // 左侧工具操作列 (紧凑优雅型，仅占 50dp，将 85%+ 空间全部留给卡片网格)
                    Column(
                        modifier = Modifier
                            .width(if (isLandscape) 44.dp else 50.dp)
                            .fillMaxHeight(),
                        verticalArrangement = Arrangement.spacedBy(if (isLandscape) 4.dp else 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // 编辑 / 完成 按钮
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(if (isLandscape) 38.dp else 48.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { isEditMode = !isEditMode },
                            shape = RoundedCornerShape(8.dp),
                            color = if (isEditMode) theme.primary.copy(alpha = 0.2f) else theme.surfaceContainerHigh,
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (isEditMode) theme.primary else theme.outline.copy(alpha = 0.4f)
                            )
                        ) {
                            Column(
                                modifier = Modifier.fillMaxSize(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = if (isEditMode) Icons.Default.Check else Icons.Default.Edit,
                                    contentDescription = "Edit",
                                    tint = if (isEditMode) theme.primary else theme.textSecondary,
                                    modifier = Modifier.size(if (isLandscape) 14.dp else 16.dp)
                                )
                                Spacer(modifier = Modifier.height(1.dp))
                                Text(
                                    text = if (isEditMode) "完成" else "编辑",
                                    color = if (isEditMode) theme.primary else theme.textSecondary,
                                    fontSize = if (isLandscape) 9.sp else 10.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }

                        // 新增指令按钮
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(if (isLandscape) 38.dp else 48.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { showAddDialog = true },
                            shape = RoundedCornerShape(8.dp),
                            color = theme.surfaceContainerHigh,
                            border = androidx.compose.foundation.BorderStroke(1.dp, theme.outline.copy(alpha = 0.4f))
                        ) {
                            Column(
                                modifier = Modifier.fillMaxSize(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = "Add",
                                    tint = theme.primary,
                                    modifier = Modifier.size(if (isLandscape) 14.dp else 16.dp)
                                )
                                Spacer(modifier = Modifier.height(1.dp))
                                Text("添加", color = theme.primary, fontSize = if (isLandscape) 9.sp else 10.sp, fontWeight = FontWeight.Medium)
                            }
                        }

                        if (isEditMode) {
                            // 恢复默认按钮
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(if (isLandscape) 34.dp else 44.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { QuickCommandManager.resetToDefaults() },
                                shape = RoundedCornerShape(8.dp),
                                color = theme.surfaceContainer,
                                border = androidx.compose.foundation.BorderStroke(1.dp, theme.outline.copy(alpha = 0.4f))
                            ) {
                                Column(
                                    modifier = Modifier.fillMaxSize(),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Icon(Icons.Default.Refresh, contentDescription = "Reset", tint = theme.textMuted, modifier = Modifier.size(13.dp))
                                    Text("重置", color = theme.textMuted, fontSize = 9.sp)
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    val gridState = rememberLazyGridState()
                    val reorderState = rememberReorderableLazyGridState(
                        gridState = gridState,
                        onMove = { fromIndex, toIndex ->
                            QuickCommandManager.move(fromIndex, toIndex)
                        }
                    )

                    // 右侧指令网格 (自适应列数：竖屏 3 列，横屏 5 列)
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(gridColumns),
                        state = gridState,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        verticalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        itemsIndexed(commands, key = { _, item -> item.id }) { index, item ->
                            val isDraggingThis = reorderState.draggingKey == item.id
                            Box(
                                modifier = Modifier
                                    .animateItemPlacement()
                                    .zIndex(if (isDraggingThis) 99f else 1f)
                            ) {
                                QuickCommandCard(
                                    command = item,
                                    index = index,
                                    totalCount = commands.size,
                                    columns = gridColumns,
                                    isLandscape = isLandscape,
                                    isEditMode = isEditMode,
                                    reorderState = reorderState,
                                    onClick = { handleExecuteCommand(item) },
                                    onLongClick = { contextMenuCommand = item },
                                    onEdit = { editingCommand = item },
                                    onDelete = { QuickCommandManager.deleteCommand(item.id) }
                                )
                            }
                        }
                    }
                }
            }

            TerminalInputMode.PC_KEYBOARD -> {
                // 电脑全键盘模式：竖屏 270dp，横屏 148dp 精致紧凑 4 行 (参考网易 UU 远程，只占大半屏，终端清晰可见)
                val pcPanelHeight = if (isLandscape) 148.dp else 270.dp
                val rowSpacing = if (isLandscape) 3.dp else 5.dp

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(pcPanelHeight)
                        .padding(horizontal = 6.dp, vertical = if (isLandscape) 4.dp else 6.dp),
                    verticalArrangement = Arrangement.spacedBy(rowSpacing)
                ) {
                    // 第 1 行：F1 - F12
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(if (isLandscape) 3.dp else 4.dp)
                    ) {
                        AccessoryButton("F1", height = accessoryBtnHeight, fontSize = accessoryFontSize) { onSendKey(TerminalKeyCodes.F1) }
                        AccessoryButton("F2", height = accessoryBtnHeight, fontSize = accessoryFontSize) { onSendKey(TerminalKeyCodes.F2) }
                        AccessoryButton("F3", height = accessoryBtnHeight, fontSize = accessoryFontSize) { onSendKey(TerminalKeyCodes.F3) }
                        AccessoryButton("F4", height = accessoryBtnHeight, fontSize = accessoryFontSize) { onSendKey(TerminalKeyCodes.F4) }
                        AccessoryButton("F5", height = accessoryBtnHeight, fontSize = accessoryFontSize) { onSendKey(TerminalKeyCodes.F5) }
                        AccessoryButton("F6", height = accessoryBtnHeight, fontSize = accessoryFontSize) { onSendKey(TerminalKeyCodes.F6) }
                        AccessoryButton("F7", height = accessoryBtnHeight, fontSize = accessoryFontSize) { onSendKey(TerminalKeyCodes.F7) }
                        AccessoryButton("F8", height = accessoryBtnHeight, fontSize = accessoryFontSize) { onSendKey(TerminalKeyCodes.F8) }
                        AccessoryButton("F9", height = accessoryBtnHeight, fontSize = accessoryFontSize) { onSendKey(TerminalKeyCodes.F9) }
                        AccessoryButton("F10", height = accessoryBtnHeight, fontSize = accessoryFontSize) { onSendKey(TerminalKeyCodes.F10) }
                        AccessoryButton("F11", height = accessoryBtnHeight, fontSize = accessoryFontSize) { onSendKey(TerminalKeyCodes.F11) }
                        AccessoryButton("F12", height = accessoryBtnHeight, fontSize = accessoryFontSize) { onSendKey(TerminalKeyCodes.F12) }
                    }

                    // 第 2 行：编辑控制键
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(if (isLandscape) 3.dp else 4.dp)
                    ) {
                        AccessoryButton("ESC", height = accessoryBtnHeight, fontSize = accessoryFontSize, modifier = Modifier.weight(1f)) { onSendKey(TerminalKeyCodes.ESC) }
                        AccessoryButton("TAB", height = accessoryBtnHeight, fontSize = accessoryFontSize, modifier = Modifier.weight(1f)) { onSendKey(TerminalKeyCodes.TAB) }
                        AccessoryButton(
                            "CTRL",
                            height = accessoryBtnHeight,
                            fontSize = accessoryFontSize,
                            isActive = isCtrlActive,
                            activeBg = ObsidianPrimary.copy(alpha = 0.25f),
                            activeBorder = ObsidianPrimary,
                            modifier = Modifier.weight(1.1f)
                        ) { onToggleCtrl() }
                        AccessoryButton(
                            "ALT",
                            height = accessoryBtnHeight,
                            fontSize = accessoryFontSize,
                            isActive = isAltActive,
                            activeBg = ObsidianPrimary.copy(alpha = 0.25f),
                            activeBorder = ObsidianPrimary,
                            modifier = Modifier.weight(1.1f)
                        ) { onToggleAlt() }
                        AccessoryButton("INS", height = accessoryBtnHeight, fontSize = accessoryFontSize, modifier = Modifier.weight(1f)) { onSendKey(TerminalKeyCodes.INSERT) }
                        AccessoryButton("DEL", height = accessoryBtnHeight, fontSize = accessoryFontSize, textColor = ObsidianError, modifier = Modifier.weight(1f)) { onSendKey(TerminalKeyCodes.DELETE) }
                    }

                    // 第 3 行：翻页与跳转
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(if (isLandscape) 3.dp else 4.dp)
                    ) {
                        AccessoryButton("HOME", height = accessoryBtnHeight, fontSize = accessoryFontSize, modifier = Modifier.weight(1f)) { onSendKey(TerminalKeyCodes.HOME) }
                        AccessoryButton("END", height = accessoryBtnHeight, fontSize = accessoryFontSize, modifier = Modifier.weight(1f)) { onSendKey(TerminalKeyCodes.END) }
                        AccessoryButton("PGUP", height = accessoryBtnHeight, fontSize = accessoryFontSize, modifier = Modifier.weight(1f)) { onSendKey(TerminalKeyCodes.PAGE_UP) }
                        AccessoryButton("PGDN", height = accessoryBtnHeight, fontSize = accessoryFontSize, modifier = Modifier.weight(1f)) { onSendKey(TerminalKeyCodes.PAGE_DOWN) }
                        AccessoryButton("⌫ 退格", height = accessoryBtnHeight, fontSize = accessoryFontSize, textColor = ObsidianError, modifier = Modifier.weight(1.3f)) { onSendKey(TerminalKeyCodes.BACKSPACE) }
                        AccessoryButton(
                            "ENTER",
                            height = accessoryBtnHeight,
                            fontSize = accessoryFontSize,
                            textColor = Color.Black,
                            bgColor = ObsidianPrimary,
                            modifier = Modifier.weight(1.4f)
                        ) { onSendKey(TerminalKeyCodes.ENTER) }
                    }

                    // 第 4 行：十字全向键 + 核心符号
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(if (isLandscape) 3.dp else 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AccessoryButton("/", height = accessoryBtnHeight, fontSize = accessoryFontSize, modifier = Modifier.weight(0.9f)) { onSendKey("/") }
                        AccessoryButton("|", height = accessoryBtnHeight, fontSize = accessoryFontSize, modifier = Modifier.weight(0.9f)) { onSendKey("|") }
                        AccessoryButton("~", height = accessoryBtnHeight, fontSize = accessoryFontSize, modifier = Modifier.weight(0.9f)) { onSendKey("~") }
                        AccessoryButton(":", height = accessoryBtnHeight, fontSize = accessoryFontSize, modifier = Modifier.weight(0.9f)) { onSendKey(":") }

                        Spacer(modifier = Modifier.width(if (isLandscape) 3.dp else 4.dp))

                        AccessoryIconButton(icon = Icons.AutoMirrored.Filled.ArrowBack, size = accessoryBtnHeight, modifier = Modifier.weight(1f)) { onSendKey(TerminalKeyCodes.ARROW_LEFT) }
                        AccessoryIconButton(icon = Icons.Default.KeyboardArrowUp, size = accessoryBtnHeight, modifier = Modifier.weight(1f)) { onSendKey(TerminalKeyCodes.ARROW_UP) }
                        AccessoryIconButton(icon = Icons.Default.KeyboardArrowDown, size = accessoryBtnHeight, modifier = Modifier.weight(1f)) { onSendKey(TerminalKeyCodes.ARROW_DOWN) }
                        AccessoryIconButton(icon = Icons.AutoMirrored.Filled.ArrowForward, size = accessoryBtnHeight, modifier = Modifier.weight(1f)) { onSendKey(TerminalKeyCodes.ARROW_RIGHT) }
                    }
                }
            }

            TerminalInputMode.HIDDEN -> {
                // 不展示任何内容
            }
        }
    }

    // -------------------------------------------------------------------------
    // 3. 添加 / 编辑指令弹窗
    // -------------------------------------------------------------------------
    if (showAddDialog) {
        CommandEditDialog(
            initialCommand = null,
            onDismiss = { showAddDialog = false },
            onSave = { title, subtitle, cmd ->
                QuickCommandManager.addCommand(title, subtitle, cmd)
                showAddDialog = false
            }
        )
    }

    editingCommand?.let { target ->
        CommandEditDialog(
            initialCommand = target,
            onDismiss = { editingCommand = null },
            onSave = { title, subtitle, cmd ->
                QuickCommandManager.updateCommand(target.id, title, subtitle, cmd)
                editingCommand = null
            }
        )
    }

    contextMenuCommand?.let { cmd ->
        AlertDialog(
            onDismissRequest = { contextMenuCommand = null },
            title = {
                Text(
                    text = cmd.title + if (cmd.subtitle.isNotBlank()) " (${cmd.subtitle})" else "",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = theme.textPrimary
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = if (cmd.id == "ctrl_v" || cmd.command == "__CLIPBOARD_PASTE__")
                            "执行指令: [粘贴剪贴板内容]"
                        else
                            "执行命令: ${cmd.command.replace("\n", " [↵回车]")}",
                        fontSize = 12.sp,
                        color = theme.textSecondary,
                        fontFamily = FontFamily.Monospace
                    )
                    HorizontalDivider(color = theme.outline.copy(alpha = 0.3f), thickness = 0.5.dp)

                    // 选项 1: 编辑指令
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                editingCommand = cmd
                                contextMenuCommand = null
                            },
                        color = theme.surfaceContainer,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = null, tint = theme.primary, modifier = Modifier.size(17.dp))
                            Text("编辑此快捷键", color = theme.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        }
                    }

                    // 选项 2: 拖拽排序管理
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                isEditMode = true
                                contextMenuCommand = null
                            },
                        color = theme.surfaceContainer,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(Icons.Default.DragHandle, contentDescription = null, tint = theme.primary, modifier = Modifier.size(17.dp))
                            Text("进入排序模式 (按住卡片自由拖动)", color = theme.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        }
                    }

                    // 选项 3: 快速前移 / 后移 (免拖拽微调)
                    val cmdIndex = commands.indexOfFirst { it.id == cmd.id }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .clickable(enabled = cmdIndex > 0) {
                                    QuickCommandManager.moveUp(cmdIndex)
                                    contextMenuCommand = null
                                },
                            color = if (cmdIndex > 0) theme.surfaceContainer else theme.surfaceContainer.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(Icons.Default.ArrowBack, contentDescription = null, tint = if (cmdIndex > 0) theme.primary else theme.textMuted, modifier = Modifier.size(15.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("向前移动", color = if (cmdIndex > 0) theme.textPrimary else theme.textMuted, fontSize = 12.sp)
                            }
                        }

                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .clickable(enabled = cmdIndex < commands.size - 1) {
                                    QuickCommandManager.moveDown(cmdIndex)
                                    contextMenuCommand = null
                                },
                            color = if (cmdIndex < commands.size - 1) theme.surfaceContainer else theme.surfaceContainer.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Text("向后移动", color = if (cmdIndex < commands.size - 1) theme.textPrimary else theme.textMuted, fontSize = 12.sp)
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(Icons.Default.ArrowForward, contentDescription = null, tint = if (cmdIndex < commands.size - 1) theme.primary else theme.textMuted, modifier = Modifier.size(15.dp))
                            }
                        }
                    }

                    // 选项 4: 删除指令
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                QuickCommandManager.deleteCommand(cmd.id)
                                contextMenuCommand = null
                            },
                        color = theme.surfaceContainer,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = null, tint = Color(0xFFEF4444), modifier = Modifier.size(17.dp))
                            Text("删除此快捷键", color = Color(0xFFEF4444), fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { contextMenuCommand = null }) {
                    Text("取消", color = theme.textSecondary)
                }
            },
            containerColor = theme.surfaceContainerLow
        )
    }
}

/**
 * UU 远程风格的模式切换 Tab 项
 */
@Composable
private fun UUTabItem(
    title: String,
    icon: ImageVector,
    isSelected: Boolean,
    isLandscape: Boolean = false,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val theme = LocalAppTheme.current
    Box(
        modifier = modifier
            .fillMaxHeight()
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.padding(horizontal = if (isLandscape) 2.dp else 4.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = if (isSelected) theme.primary else theme.textMuted,
                modifier = Modifier.size(if (isLandscape) 13.dp else 15.dp)
            )
            Spacer(modifier = Modifier.width(if (isLandscape) 2.dp else 4.dp))
            Text(
                text = title,
                fontSize = if (isLandscape) 11.sp else 13.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                color = if (isSelected) theme.primary else theme.textSecondary
            )
        }

        // 底部激活指示线
        if (isSelected) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth(0.6f)
                    .height(if (isLandscape) 2.dp else 2.5.dp)
                    .background(theme.primary, RoundedCornerShape(1.dp))
            )
        }
    }
}

/**
 * 专为 LazyVerticalGrid 打造的高性能平滑拖拽重排状态管理器
 * 极致体验与防抖架构：
 * 1. 核心区同心命中判定 (Core Box Hit-Test)：目标卡片内缩 22% 宽度与 20% 高度，在卡片间建立 44%+ 物理死区，彻底消灭临界点震荡；
 * 2. 240ms 让位冷却保护 (Swap Cooldown)：保证上一次平移动画从容就位，杜绝每秒几十次的疯狂来回互换，彻底根治周围卡片闪烁；
 * 3. 刻度感单次触觉反馈：仅在成功换位瞬间触发一次清脆震动，告别马达高频狂震；
 * 4. 坐标位移平滑差量补偿：卡片视觉位置绝对恒定跟随手指，松手平稳吸附。
 */
class ReorderableLazyGridState(
    val gridState: LazyGridState,
    val onMove: (fromIndex: Int, toIndex: Int) -> Unit
) {
    var draggingKey by mutableStateOf<Any?>(null)
    var dragOffset by mutableStateOf(Offset.Zero)
    var currentDraggingIndex by mutableStateOf<Int?>(null)

    // 上一次触发交换的时间戳，用于严格防抖与防止反向回弹
    private var lastSwapTimestamp = 0L

    val isDragging: Boolean get() = draggingKey != null

    fun onDragStart(key: Any, index: Int) {
        draggingKey = key
        currentDraggingIndex = index
        dragOffset = Offset.Zero
        lastSwapTimestamp = System.currentTimeMillis()
    }

    fun onDrag(dragAmount: Offset, haptic: androidx.compose.ui.hapticfeedback.HapticFeedback? = null) {
        dragOffset += dragAmount

        val currentIndex = currentDraggingIndex ?: return
        val now = System.currentTimeMillis()
        // 关键防抖 1：240ms 冷却保护，等待上一次让位动画平稳完成
        if (now - lastSwapTimestamp < 240L) return

        val layoutInfo = gridState.layoutInfo
        val currentItem = layoutInfo.visibleItemsInfo.firstOrNull { it.key == draggingKey } ?: return

        // 当前被拖动卡片在视口内的实时视觉中心坐标
        val currentCenterX = currentItem.offset.x + currentItem.size.width / 2f + dragOffset.x
        val currentCenterY = currentItem.offset.y + currentItem.size.height / 2f + dragOffset.y

        // 关键防抖 2：严格的核心区死区判定 (Core Box Hit-Test)
        // 目标卡片四周内缩 22% 宽度与 20% 高度，只有手指真正深潜入目标卡片的核心区域才判定意图交换
        val targetItem = layoutInfo.visibleItemsInfo.firstOrNull { item ->
            if (item.key == draggingKey) return@firstOrNull false
            val coreMarginX = item.size.width * 0.22f
            val coreMarginY = item.size.height * 0.20f
            val coreLeft = item.offset.x + coreMarginX
            val coreRight = item.offset.x + item.size.width - coreMarginX
            val coreTop = item.offset.y + coreMarginY
            val coreBottom = item.offset.y + item.size.height - coreMarginY

            currentCenterX in coreLeft..coreRight && currentCenterY in coreTop..coreBottom
        }

        if (targetItem != null && targetItem.index != currentIndex) {
            val targetIndex = targetItem.index

            // 计算位移差，保持视觉绝对位置恒定无瞬移跳变
            val deltaX = currentItem.offset.x - targetItem.offset.x
            val deltaY = currentItem.offset.y - targetItem.offset.y

            onMove(currentIndex, targetIndex)
            currentDraggingIndex = targetIndex
            lastSwapTimestamp = now
            dragOffset += Offset(deltaX.toFloat(), deltaY.toFloat())

            // 仅在成功换位瞬间触发一次清脆刻度感震动，杜绝高频乱震
            haptic?.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
    }

    fun onDragEnd() {
        draggingKey = null
        currentDraggingIndex = null
        dragOffset = Offset.Zero
    }

    fun onDragCancel() {
        draggingKey = null
        currentDraggingIndex = null
        dragOffset = Offset.Zero
    }
}

@Composable
fun rememberReorderableLazyGridState(
    gridState: LazyGridState,
    onMove: (fromIndex: Int, toIndex: Int) -> Unit
): ReorderableLazyGridState {
    return remember(gridState) {
        ReorderableLazyGridState(gridState, onMove)
    }
}

/**
 * 快捷指令网格卡片 (双行显示：上部标题 + 下部中文注释)
 * 极致去图标化极简重构：
 * 1. 零视觉污染：彻底移除卡片上拥挤繁琐的铅笔图标与拖动手柄，文字排布舒展大气；
 * 2. 随心编辑：编辑模式下轻触卡片任意位置，直接弹出快捷指令编辑对话框；
 * 3. 全卡即手柄：编辑模式下按住卡片任意位置 (长按 100ms) 即可直接抓起丝滑拖拽；
 * 4. 优雅删除：仅在编辑模式右上角保留一个精致微小的红色半透明删除角标；
 * 5. 醒目语义：Ctrl+C 与 Ctrl+V 专属高亮区分，日常运维一目了然。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun QuickCommandCard(
    command: QuickCommand,
    index: Int,
    totalCount: Int,
    columns: Int = 3,
    isLandscape: Boolean = false,
    isEditMode: Boolean,
    reorderState: ReorderableLazyGridState,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val theme = LocalAppTheme.current
    val haptic = LocalHapticFeedback.current

    val isDraggingThis = reorderState.draggingKey == command.id
    val cardHeight = if (isLandscape) 38.dp else 52.dp

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(cardHeight)
            .graphicsLayer {
                if (isDraggingThis) {
                    translationX = reorderState.dragOffset.x
                    translationY = reorderState.dragOffset.y
                    scaleX = 1.08f
                    scaleY = 1.08f
                    shadowElevation = 20f
                }
            }
            .clip(RoundedCornerShape(8.dp))
            .combinedClickable(
                onClick = {
                    if (isEditMode) {
                        onEdit() // 编辑模式下轻点整张卡片任意位置直接编辑
                    } else {
                        onClick() // 普通模式下执行指令
                    }
                },
                onLongClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    if (isEditMode) {
                        reorderState.onDragStart(command.id, index)
                    } else {
                        onLongClick()
                    }
                }
            )
            .then(
                if (isEditMode) {
                    // 编辑模式下整张卡片都是拖拽热区，长按 100ms 震动抓起直接拖动
                    Modifier.pointerInput(command.id) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                reorderState.onDragStart(command.id, index)
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                reorderState.onDrag(dragAmount, haptic)
                            },
                            onDragEnd = { reorderState.onDragEnd() },
                            onDragCancel = { reorderState.onDragCancel() }
                        )
                    }
                } else Modifier
            ),
        shape = RoundedCornerShape(8.dp),
        color = if (isDraggingThis) theme.surfaceContainerHigh else if (isEditMode) theme.surfaceContainerHigh.copy(alpha = 0.7f) else theme.surfaceContainerLow,
        border = androidx.compose.foundation.BorderStroke(
            if (isDraggingThis) 1.5.dp else 1.dp,
            if (isDraggingThis) theme.primary else if (isEditMode) theme.primary.copy(alpha = 0.5f) else theme.outline.copy(alpha = 0.35f)
        ),
        shadowElevation = if (isDraggingThis) 12.dp else 0.dp
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 4.dp, vertical = if (isLandscape) 2.dp else 4.dp)
        ) {
            // 中心标题与副标题 (纯净居中排布，零多余图标遮挡，文字空间释放 100%)
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = if (isEditMode) 8.dp else 2.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                val titleColor = when {
                    command.id == "ctrl_c" -> ObsidianError
                    command.id == "ctrl_v" -> theme.primary
                    else -> theme.textPrimary
                }
                Text(
                    text = command.title,
                    color = titleColor,
                    fontSize = if (isLandscape) 11.sp else 12.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (command.subtitle.isNotBlank() && !isLandscape) {
                    Text(
                        text = command.subtitle,
                        color = theme.textSecondary,
                        fontSize = 10.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // 编辑状态下仅在右上角保留一个精致小巧的深红半透明圆底删除角标 (完全不遮挡文字)
            if (isEditMode) {
                Box(
                    modifier = Modifier
                        .size(if (isLandscape) 16.dp else 18.dp)
                        .align(Alignment.TopEnd)
                        .clip(CircleShape)
                        .background(Color(0xFFEF4444).copy(alpha = 0.18f))
                        .clickable(onClick = onDelete),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Delete",
                        tint = Color(0xFFEF4444),
                        modifier = Modifier.size(if (isLandscape) 9.dp else 11.dp)
                    )
                }
            }
        }
    }
}

/**
 * 自定义指令添加/编辑弹窗 (自适应深浅色主题，支持完全编辑名称、注释与指令文本)
 */
@Composable
private fun CommandEditDialog(
    initialCommand: QuickCommand?,
    onDismiss: () -> Unit,
    onSave: (title: String, subtitle: String, cmd: String) -> Unit
) {
    val theme = LocalAppTheme.current
    var title by remember { mutableStateOf(initialCommand?.title ?: "") }
    var subtitle by remember { mutableStateOf(initialCommand?.subtitle ?: "") }
    var commandText by remember { mutableStateOf(initialCommand?.command ?: "") }
    var appendEnter by remember { mutableStateOf(initialCommand?.command?.endsWith("\n") ?: true) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = theme.surfaceContainerLow,
            border = androidx.compose.foundation.BorderStroke(1.dp, theme.outline.copy(alpha = 0.4f)),
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            shadowElevation = 8.dp
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = if (initialCommand == null) "添加快捷运维指令" else "编辑快捷指令",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = theme.textPrimary
                )

                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("指令名称 (如: 重启服务)") },
                    singleLine = true,
                    shape = RoundedCornerShape(8.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = theme.surfaceContainer,
                        unfocusedContainerColor = theme.surfaceContainer,
                        focusedBorderColor = theme.primary,
                        unfocusedBorderColor = theme.outline.copy(alpha = 0.4f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = subtitle,
                    onValueChange = { subtitle = it },
                    label = { Text("中文注释 (如: restart)") },
                    singleLine = true,
                    shape = RoundedCornerShape(8.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = theme.surfaceContainer,
                        unfocusedContainerColor = theme.surfaceContainer,
                        focusedBorderColor = theme.primary,
                        unfocusedBorderColor = theme.outline.copy(alpha = 0.4f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = commandText,
                    onValueChange = { commandText = it },
                    label = { Text("执行命令 (如: systemctl restart nginx)") },
                    singleLine = true,
                    shape = RoundedCornerShape(8.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = theme.surfaceContainer,
                        unfocusedContainerColor = theme.surfaceContainer,
                        focusedBorderColor = theme.primary,
                        unfocusedBorderColor = theme.outline.copy(alpha = 0.4f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable { appendEnter = !appendEnter }
                ) {
                    Checkbox(
                        checked = appendEnter,
                        onCheckedChange = { appendEnter = it },
                        colors = CheckboxDefaults.colors(checkedColor = theme.primary)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("自动追加回车 (直接在终端执行)", fontSize = 12.sp, color = theme.textSecondary)
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("取消", color = theme.textSecondary)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (title.isNotBlank() && commandText.isNotBlank()) {
                                val finalCmd = if (appendEnter && !commandText.endsWith("\n")) {
                                    commandText + "\n"
                                } else {
                                    commandText
                                }
                                onSave(title.trim(), subtitle.trim(), finalCmd)
                            }
                        },
                        enabled = title.isNotBlank() && commandText.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = theme.primary,
                            contentColor = if (theme.isDark) Color.Black else Color.White
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("保存", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

/**
 * 经典辅助文字按键 (支持横竖屏自适应高度与字号)
 */
@Composable
private fun AccessoryButton(
    label: String,
    isActive: Boolean = false,
    activeBg: Color? = null,
    activeBorder: Color? = null,
    textColor: Color? = null,
    bgColor: Color? = null,
    height: Dp = 34.dp,
    fontSize: TextUnit = 11.sp,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val theme = LocalAppTheme.current
    val resolvedActiveBg = activeBg ?: theme.primary.copy(alpha = 0.2f)
    val resolvedActiveBorder = activeBorder ?: theme.primary
    val resolvedTextColor = textColor ?: theme.textPrimary
    val resolvedBgColor = bgColor ?: theme.surfaceContainerHigh

    Surface(
        modifier = modifier
            .height(height)
            .clip(RoundedCornerShape(6.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(6.dp),
        color = if (isActive) resolvedActiveBg else resolvedBgColor,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (isActive) resolvedActiveBorder else theme.outline.copy(alpha = 0.35f)
        )
    ) {
        Box(
            modifier = Modifier.padding(horizontal = if (height < 30.dp) 6.dp else 9.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                fontSize = fontSize,
                fontWeight = FontWeight.SemiBold,
                color = if (isActive) theme.primary else resolvedTextColor,
                fontFamily = FontFamily.Monospace,
                maxLines = 1,
                softWrap = false
            )
        }
    }
}

/**
 * 经典辅助图标按键 (方向键等，支持横竖屏紧凑尺寸)
 */
@Composable
private fun AccessoryIconButton(
    icon: ImageVector,
    size: Dp = 34.dp,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val theme = LocalAppTheme.current
    val iconSize = if (size < 30.dp) 15.dp else 18.dp

    Surface(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(6.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(6.dp),
        color = theme.surfaceContainerHigh,
        border = androidx.compose.foundation.BorderStroke(1.dp, theme.outline.copy(alpha = 0.35f))
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = theme.textPrimary,
                modifier = Modifier.size(iconSize)
            )
        }
    }
}
