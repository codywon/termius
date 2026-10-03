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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
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

    var isEditMode by remember { mutableStateOf(false) }
    var editingCommand by remember { mutableStateOf<QuickCommand?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }
    var contextMenuCommand by remember { mutableStateOf<QuickCommand?>(null) }

    // 如果处于 HIDDEN 状态，不渲染主体
    if (currentMode == TerminalInputMode.HIDDEN) {
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
                    .height(42.dp)
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Tab 1: 输入法
                UUTabItem(
                    title = "输入法",
                    icon = Icons.Default.Keyboard,
                    isSelected = currentMode == TerminalInputMode.IME,
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
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Cancel,
                        contentDescription = "Close Bar",
                        tint = ObsidianTextMuted,
                        modifier = Modifier.size(20.dp)
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
                        .padding(horizontal = 6.dp, vertical = 5.dp)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AccessoryButton(label = "ESC", onClick = { onSendKey(TerminalKeyCodes.ESC) })
                    AccessoryButton(label = "TAB", onClick = { onSendKey(TerminalKeyCodes.TAB) })
                    AccessoryButton(label = "⌫", textColor = ObsidianError, onClick = { onSendKey(TerminalKeyCodes.BACKSPACE) })
                    AccessoryButton(
                        label = "CTRL",
                        isActive = isCtrlActive,
                        activeBg = ObsidianPrimary.copy(alpha = 0.25f),
                        activeBorder = ObsidianPrimary,
                        onClick = onToggleCtrl
                    )
                    AccessoryButton(
                        label = "ALT",
                        isActive = isAltActive,
                        activeBg = ObsidianPrimary.copy(alpha = 0.25f),
                        activeBorder = ObsidianPrimary,
                        onClick = onToggleAlt
                    )
                    AccessoryButton(label = "/", onClick = { onSendKey("/") })
                    AccessoryButton(label = "-", onClick = { onSendKey("-") })
                    AccessoryButton(label = "|", onClick = { onSendKey("|") })
                    AccessoryButton(label = "~", onClick = { onSendKey("~") })
                    AccessoryButton(label = ":", onClick = { onSendKey(":") })
                    AccessoryButton(label = "$", onClick = { onSendKey("$") })

                    // 方向键
                    AccessoryIconButton(icon = Icons.Default.KeyboardArrowUp) { onSendKey(TerminalKeyCodes.ARROW_UP) }
                    AccessoryIconButton(icon = Icons.Default.KeyboardArrowDown) { onSendKey(TerminalKeyCodes.ARROW_DOWN) }
                    AccessoryIconButton(icon = Icons.AutoMirrored.Filled.ArrowBack) { onSendKey(TerminalKeyCodes.ARROW_LEFT) }
                    AccessoryIconButton(icon = Icons.AutoMirrored.Filled.ArrowForward) { onSendKey(TerminalKeyCodes.ARROW_RIGHT) }

                    // Ctrl+C 中断
                    AccessoryButton(label = "Ctrl+C", textColor = ObsidianError, onClick = { onSendKey("\u0003") })

                    // PASTE 剪贴板
                    Surface(
                        modifier = Modifier
                            .height(34.dp)
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
                            modifier = Modifier.padding(horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(Icons.Default.ContentPaste, contentDescription = "Paste", tint = ObsidianTextPrimary, modifier = Modifier.size(13.dp))
                            Text("PASTE", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = ObsidianTextPrimary, fontFamily = FontFamily.Monospace)
                        }
                    }
                }
            }

            TerminalInputMode.SHORTCUTS -> {
                // 快捷键模式：高度约 270dp，完全替代输入法
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(270.dp)
                        .padding(8.dp)
                ) {
                    // 左侧工具操作列 (参考网易 UU 远程：[编辑/完成]、[添加])
                    Column(
                        modifier = Modifier
                            .width(70.dp)
                            .fillMaxHeight(),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // 编辑 / 完成 按钮
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp)
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
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = if (isEditMode) "完成" else "编辑",
                                    color = if (isEditMode) theme.primary else theme.textSecondary,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }

                        // 新增指令按钮
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp)
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
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text("添加", color = theme.primary, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                            }
                        }

                        if (isEditMode) {
                            // 恢复默认按钮
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
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
                                    Icon(Icons.Default.Refresh, contentDescription = "Reset", tint = theme.textMuted, modifier = Modifier.size(16.dp))
                                    Text("重置", color = theme.textMuted, fontSize = 10.sp)
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    // 右侧指令网格 (3列)
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        itemsIndexed(commands, key = { _, item -> item.id }) { index, item ->
                            QuickCommandCard(
                                command = item,
                                index = index,
                                totalCount = commands.size,
                                isEditMode = isEditMode,
                                onClick = { onSendKey(item.command) },
                                onLongClick = { contextMenuCommand = item },
                                onEdit = { editingCommand = item },
                                onDelete = { QuickCommandManager.deleteCommand(item.id) },
                                onMove = { fromIndex, toIndex -> QuickCommandManager.move(fromIndex, toIndex) }
                            )
                        }
                    }
                }
            }

            TerminalInputMode.PC_KEYBOARD -> {
                // 电脑全键盘模式：高度约 270dp，完全替代输入法
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(270.dp)
                        .padding(horizontal = 6.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    // 第 1 行：F1 - F12
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        AccessoryButton("F1") { onSendKey(TerminalKeyCodes.F1) }
                        AccessoryButton("F2") { onSendKey(TerminalKeyCodes.F2) }
                        AccessoryButton("F3") { onSendKey(TerminalKeyCodes.F3) }
                        AccessoryButton("F4") { onSendKey(TerminalKeyCodes.F4) }
                        AccessoryButton("F5") { onSendKey(TerminalKeyCodes.F5) }
                        AccessoryButton("F6") { onSendKey(TerminalKeyCodes.F6) }
                        AccessoryButton("F7") { onSendKey(TerminalKeyCodes.F7) }
                        AccessoryButton("F8") { onSendKey(TerminalKeyCodes.F8) }
                        AccessoryButton("F9") { onSendKey(TerminalKeyCodes.F9) }
                        AccessoryButton("F10") { onSendKey(TerminalKeyCodes.F10) }
                        AccessoryButton("F11") { onSendKey(TerminalKeyCodes.F11) }
                        AccessoryButton("F12") { onSendKey(TerminalKeyCodes.F12) }
                    }

                    // 第 2 行：编辑控制键
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        AccessoryButton("ESC", modifier = Modifier.weight(1f)) { onSendKey(TerminalKeyCodes.ESC) }
                        AccessoryButton("TAB", modifier = Modifier.weight(1f)) { onSendKey(TerminalKeyCodes.TAB) }
                        AccessoryButton(
                            "CTRL",
                            isActive = isCtrlActive,
                            activeBg = ObsidianPrimary.copy(alpha = 0.25f),
                            activeBorder = ObsidianPrimary,
                            modifier = Modifier.weight(1.1f)
                        ) { onToggleCtrl() }
                        AccessoryButton(
                            "ALT",
                            isActive = isAltActive,
                            activeBg = ObsidianPrimary.copy(alpha = 0.25f),
                            activeBorder = ObsidianPrimary,
                            modifier = Modifier.weight(1.1f)
                        ) { onToggleAlt() }
                        AccessoryButton("INS", modifier = Modifier.weight(1f)) { onSendKey(TerminalKeyCodes.INSERT) }
                        AccessoryButton("DEL", textColor = ObsidianError, modifier = Modifier.weight(1f)) { onSendKey(TerminalKeyCodes.DELETE) }
                    }

                    // 第 3 行：翻页与跳转
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        AccessoryButton("HOME", modifier = Modifier.weight(1f)) { onSendKey(TerminalKeyCodes.HOME) }
                        AccessoryButton("END", modifier = Modifier.weight(1f)) { onSendKey(TerminalKeyCodes.END) }
                        AccessoryButton("PGUP", modifier = Modifier.weight(1f)) { onSendKey(TerminalKeyCodes.PAGE_UP) }
                        AccessoryButton("PGDN", modifier = Modifier.weight(1f)) { onSendKey(TerminalKeyCodes.PAGE_DOWN) }
                        AccessoryButton("⌫ 退格", textColor = ObsidianError, modifier = Modifier.weight(1.3f)) { onSendKey(TerminalKeyCodes.BACKSPACE) }
                        AccessoryButton(
                            "ENTER",
                            textColor = Color.Black,
                            bgColor = ObsidianPrimary,
                            modifier = Modifier.weight(1.4f)
                        ) { onSendKey(TerminalKeyCodes.ENTER) }
                    }

                    // 第 4 行：十字全向键 + 核心符号
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AccessoryButton("/", modifier = Modifier.weight(0.9f)) { onSendKey("/") }
                        AccessoryButton("|", modifier = Modifier.weight(0.9f)) { onSendKey("|") }
                        AccessoryButton("~", modifier = Modifier.weight(0.9f)) { onSendKey("~") }
                        AccessoryButton(":", modifier = Modifier.weight(0.9f)) { onSendKey(":") }

                        Spacer(modifier = Modifier.width(4.dp))

                        AccessoryIconButton(icon = Icons.AutoMirrored.Filled.ArrowBack, modifier = Modifier.weight(1f)) { onSendKey(TerminalKeyCodes.ARROW_LEFT) }
                        AccessoryIconButton(icon = Icons.Default.KeyboardArrowUp, modifier = Modifier.weight(1f)) { onSendKey(TerminalKeyCodes.ARROW_UP) }
                        AccessoryIconButton(icon = Icons.Default.KeyboardArrowDown, modifier = Modifier.weight(1f)) { onSendKey(TerminalKeyCodes.ARROW_DOWN) }
                        AccessoryIconButton(icon = Icons.AutoMirrored.Filled.ArrowForward, modifier = Modifier.weight(1f)) { onSendKey(TerminalKeyCodes.ARROW_RIGHT) }
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
                        text = "执行命令: ${cmd.command.replace("\n", " [↵回车]")}",
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
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = null, tint = theme.primary, modifier = Modifier.size(18.dp))
                            Text("编辑此快捷键", color = theme.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        }
                    }

                    // 选项 2: 拖拽排序
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
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(Icons.Default.DragHandle, contentDescription = null, tint = theme.primary, modifier = Modifier.size(18.dp))
                            Text("排序管理 (按住手柄自由拖动)", color = theme.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        }
                    }

                    // 选项 3: 删除指令
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
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = null, tint = Color(0xFFEF4444), modifier = Modifier.size(18.dp))
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
            modifier = Modifier.padding(horizontal = 4.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = if (isSelected) theme.primary else theme.textMuted,
                modifier = Modifier.size(15.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = title,
                fontSize = 13.sp,
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
                    .height(2.5.dp)
                    .background(theme.primary, RoundedCornerShape(1.dp))
            )
        }
    }
}

/**
 * 快捷指令网格卡片 (双行显示：上部标题 + 下部中文注释)
 * 支持：
 * 1. 轻按发送指令 / 编辑模式下点击直接编辑
 * 2. 长按弹出操作菜单 (编辑、拖动排序、删除)
 * 3. 编辑模式下：移除左右小箭头彻底杜绝误触，支持按住拖拽手柄自由拖动排序
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun QuickCommandCard(
    command: QuickCommand,
    index: Int,
    totalCount: Int,
    isEditMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onMove: (fromIndex: Int, toIndex: Int) -> Unit
) {
    val theme = LocalAppTheme.current
    val haptic = LocalHapticFeedback.current

    var dragOffsetX by remember { mutableStateOf(0f) }
    var dragOffsetY by remember { mutableStateOf(0f) }
    var isDragging by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .zIndex(if (isDragging) 10f else 1f)
            .offset { IntOffset(dragOffsetX.roundToInt(), dragOffsetY.roundToInt()) }
            .graphicsLayer {
                if (isDragging) {
                    scaleX = 1.08f
                    scaleY = 1.08f
                    shadowElevation = 14f
                }
            }
            .clip(RoundedCornerShape(8.dp))
            .combinedClickable(
                onClick = {
                    if (isEditMode) {
                        onEdit()
                    } else {
                        onClick()
                    }
                },
                onLongClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onLongClick()
                }
            ),
        shape = RoundedCornerShape(8.dp),
        color = if (isDragging) theme.surfaceContainerHigh else theme.surfaceContainerLow,
        border = androidx.compose.foundation.BorderStroke(
            if (isDragging) 1.5.dp else 1.dp,
            if (isDragging) theme.primary else if (isEditMode) theme.primary.copy(alpha = 0.55f) else theme.outline.copy(alpha = 0.35f)
        ),
        shadowElevation = if (isDragging) 8.dp else 0.dp
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 6.dp, vertical = 4.dp)
        ) {
            // 中心标题与副标题
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = if (isEditMode) 16.dp else 2.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = command.title,
                    color = theme.textPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (command.subtitle.isNotBlank()) {
                    Text(
                        text = command.subtitle,
                        color = theme.textSecondary,
                        fontSize = 10.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // 编辑状态下的操作界面：杜绝误触，分立清晰
            if (isEditMode) {
                // 左下角：清晰的编辑铅笔小标识
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .align(Alignment.BottomStart)
                        .clip(CircleShape)
                        .clickable(onClick = onEdit),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Edit",
                        tint = theme.primary,
                        modifier = Modifier.size(13.dp)
                    )
                }

                // 右上角：明确的删除红色小按钮 (带圆底，防误触)
                Box(
                    modifier = Modifier
                        .size(22.dp)
                        .align(Alignment.TopEnd)
                        .clip(CircleShape)
                        .background(Color(0xFFEF4444).copy(alpha = 0.14f))
                        .clickable(onClick = onDelete),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Delete",
                        tint = Color(0xFFEF4444),
                        modifier = Modifier.size(13.dp)
                    )
                }

                // 右下角：拖动手柄（专供拖动重排，彻底替代旧箭头）
                Box(
                    modifier = Modifier
                        .size(26.dp)
                        .align(Alignment.BottomEnd)
                        .clip(RoundedCornerShape(4.dp))
                        .pointerInput(command.id, index) {
                            detectDragGestures(
                                onDragStart = {
                                    isDragging = true
                                    dragOffsetX = 0f
                                    dragOffsetY = 0f
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    dragOffsetX += dragAmount.x
                                    dragOffsetY += dragAmount.y

                                    val thresholdX = 85.dp.toPx() * 0.6f
                                    val thresholdY = 60.dp.toPx() * 0.6f

                                    var targetIndex = index
                                    if (dragOffsetX > thresholdX && (index % 3) < 2) {
                                        targetIndex += 1
                                        dragOffsetX -= 85.dp.toPx()
                                    } else if (dragOffsetX < -thresholdX && (index % 3) > 0) {
                                        targetIndex -= 1
                                        dragOffsetX += 85.dp.toPx()
                                    }

                                    if (dragOffsetY > thresholdY && targetIndex + 3 < totalCount) {
                                        targetIndex += 3
                                        dragOffsetY -= 60.dp.toPx()
                                    } else if (dragOffsetY < -thresholdY && targetIndex - 3 >= 0) {
                                        targetIndex -= 3
                                        dragOffsetY += 60.dp.toPx()
                                    }

                                    if (targetIndex != index && targetIndex in 0 until totalCount) {
                                        onMove(index, targetIndex)
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    }
                                },
                                onDragEnd = {
                                    isDragging = false
                                    dragOffsetX = 0f
                                    dragOffsetY = 0f
                                },
                                onDragCancel = {
                                    isDragging = false
                                    dragOffsetX = 0f
                                    dragOffsetY = 0f
                                }
                            )
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.DragHandle,
                        contentDescription = "Drag Handle",
                        tint = if (isDragging) theme.primary else theme.textMuted,
                        modifier = Modifier.size(16.dp)
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
 * 经典辅助文字按键
 */
@Composable
private fun AccessoryButton(
    label: String,
    isActive: Boolean = false,
    activeBg: Color? = null,
    activeBorder: Color? = null,
    textColor: Color? = null,
    bgColor: Color? = null,
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
            .height(34.dp)
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
            modifier = Modifier.padding(horizontal = 9.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                fontSize = 11.sp,
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
 * 经典辅助图标按键 (方向键等)
 */
@Composable
private fun AccessoryIconButton(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val theme = LocalAppTheme.current
    Surface(
        modifier = modifier
            .size(34.dp)
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
                modifier = Modifier.size(18.dp)
            )
        }
    }
}
