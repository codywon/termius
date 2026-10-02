package com.termius.clone.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.termius.clone.terminal.engine.TerminalKeyCodes
import com.termius.clone.ui.theme.*

enum class AuxiliaryTab {
    NONE,
    SHORTCUTS,
    PC_KEYBOARD
}

@Composable
fun TerminalAccessoryBar(
    isCtrlActive: Boolean,
    onToggleCtrl: () -> Unit,
    isAltActive: Boolean,
    onToggleAlt: () -> Unit,
    onSendKey: (String) -> Unit,
    onToggleKeyboard: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var expandedTab by remember { mutableStateOf(AuxiliaryTab.NONE) }
    val clipboardManager = LocalClipboardManager.current

    Column(
        modifier = modifier
            .fillMaxWidth()
            .imePadding()
            .background(ObsidianSurfaceContainerLowest)
    ) {
        // 1. 经典单行辅助条（永远常驻，极简紧凑）
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 左侧横向滑动核心键区
            Row(
                modifier = Modifier
                    .weight(1f)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // ESC
                AccessoryButton(label = "ESC", onClick = { onSendKey(TerminalKeyCodes.ESC) })

                // TAB
                AccessoryButton(label = "TAB", onClick = { onSendKey(TerminalKeyCodes.TAB) })

                // CTRL
                AccessoryButton(
                    label = "CTRL",
                    isActive = isCtrlActive,
                    activeBg = ObsidianPrimary.copy(alpha = 0.25f),
                    activeBorder = ObsidianPrimary,
                    onClick = onToggleCtrl
                )

                // ALT
                AccessoryButton(
                    label = "ALT",
                    isActive = isAltActive,
                    activeBg = ObsidianPrimary.copy(alpha = 0.25f),
                    activeBorder = ObsidianPrimary,
                    onClick = onToggleAlt
                )

                // 核心终端字符
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

                // 常用中断
                AccessoryButton(label = "Ctrl+C", textColor = ObsidianError, onClick = { onSendKey("\u0003") })

                // PASTE
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
                        modifier = Modifier.padding(horizontal = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Icon(Icons.Default.ContentPaste, contentDescription = "Paste", tint = ObsidianTextPrimary, modifier = Modifier.size(13.dp))
                        Text("PASTE", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = ObsidianTextPrimary, fontFamily = FontFamily.Monospace)
                    }
                }
            }

            Spacer(modifier = Modifier.width(6.dp))

            // 右侧三模式切换控制器 (输入法 / 快捷键 / 电脑键盘)
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 1. 手机输入法 Tab
                ModeSwitchButton(
                    icon = Icons.Default.Keyboard,
                    label = "输入法",
                    isActive = false,
                    onClick = {
                        expandedTab = AuxiliaryTab.NONE
                        onToggleKeyboard()
                    }
                )

                // 2. 快捷键 Tab
                ModeSwitchButton(
                    icon = Icons.Default.FlashOn,
                    label = "快捷键",
                    isActive = expandedTab == AuxiliaryTab.SHORTCUTS,
                    onClick = {
                        expandedTab = if (expandedTab == AuxiliaryTab.SHORTCUTS) AuxiliaryTab.NONE else AuxiliaryTab.SHORTCUTS
                    }
                )

                // 3. 电脑全键盘 Tab
                ModeSwitchButton(
                    icon = Icons.Default.Laptop,
                    label = "电脑键盘",
                    isActive = expandedTab == AuxiliaryTab.PC_KEYBOARD,
                    onClick = {
                        expandedTab = if (expandedTab == AuxiliaryTab.PC_KEYBOARD) AuxiliaryTab.NONE else AuxiliaryTab.PC_KEYBOARD
                    }
                )
            }
        }

        // 2. 展开面板区：快捷键抽屉 (Shortcuts Drawer)
        AnimatedVisibility(
            visible = expandedTab == AuxiliaryTab.SHORTCUTS,
            enter = expandVertically(),
            exit = shrinkVertically()
        ) {
            Surface(
                color = ObsidianSurfaceContainerLow,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 220.dp)
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("⚡ 快捷指令与终端宏 (快捷键模式)", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = ObsidianPrimary)
                        IconButton(onClick = { expandedTab = AuxiliaryTab.NONE }, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = ObsidianTextMuted, modifier = Modifier.size(16.dp))
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    val shortcuts = remember {
                        listOf(
                            "Ctrl+C" to "\u0003",
                            "Ctrl+Z" to "\u001A",
                            "Ctrl+D" to "\u0004",
                            "Ctrl+L (清屏)" to "\u000C",
                            "Ctrl+A (行首)" to "\u0001",
                            "Ctrl+E (行尾)" to "\u0005",
                            "Ctrl+U (清除)" to "\u0015",
                            "Ctrl+R (搜索)" to "\u0012",
                            "sudo !!" to "sudo !!\n",
                            ":wq (保存退出)" to ":wq\n",
                            ":q! (强制退出)" to ":q!\n",
                            "docker ps" to "docker ps\n",
                            "git status" to "git status\n",
                            "htop" to "htop\n",
                            "tail -f" to "tail -f ",
                            "df -h" to "df -h\n",
                            "free -m" to "free -m\n",
                            "ls -lah" to "ls -lah\n"
                        )
                    }

                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 100.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(shortcuts) { (label, cmd) ->
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = ObsidianSurfaceContainerHigh,
                                border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianOutlineVariant),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onSendKey(cmd) }
                            ) {
                                Box(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 7.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = label,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium,
                                        fontFamily = FontFamily.Monospace,
                                        color = if (label.startsWith("Ctrl+C")) ObsidianError else ObsidianTextPrimary,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // 3. 展开面板区：全功能虚拟电脑键盘 (PC Keyboard Drawer)
        AnimatedVisibility(
            visible = expandedTab == AuxiliaryTab.PC_KEYBOARD,
            enter = expandVertically(),
            exit = shrinkVertically()
        ) {
            Surface(
                color = ObsidianSurfaceContainerLow,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 240.dp)
            ) {
                Column(modifier = Modifier.padding(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("💻 虚拟电脑键盘 (PC Keyboard Mode)", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = ObsidianSecondary)
                        IconButton(onClick = { expandedTab = AuxiliaryTab.NONE }, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = ObsidianTextMuted, modifier = Modifier.size(16.dp))
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // Row 1: F1 - F12
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        val fKeys = listOf(
                            "F1" to TerminalKeyCodes.F1, "F2" to TerminalKeyCodes.F2, "F3" to TerminalKeyCodes.F3,
                            "F4" to TerminalKeyCodes.F4, "F5" to TerminalKeyCodes.F5, "F6" to TerminalKeyCodes.F6,
                            "F7" to TerminalKeyCodes.F7, "F8" to TerminalKeyCodes.F8, "F9" to TerminalKeyCodes.F9,
                            "F10" to TerminalKeyCodes.F10, "F11" to TerminalKeyCodes.F11, "F12" to TerminalKeyCodes.F12
                        )
                        fKeys.forEach { (label, code) ->
                            PcKeyButton(label = label, onClick = { onSendKey(code) }, width = 36.dp)
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // Row 2: 编辑键区 (ESC, Insert, Delete, Home, End, PgUp, PgDn)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        PcKeyButton("ESC", { onSendKey(TerminalKeyCodes.ESC) }, width = 46.dp, color = ObsidianError.copy(alpha = 0.8f))
                        PcKeyButton("Insert", { onSendKey(TerminalKeyCodes.INSERT) }, width = 50.dp)
                        PcKeyButton("Del", { onSendKey(TerminalKeyCodes.DELETE) }, width = 42.dp)
                        PcKeyButton("Home", { onSendKey(TerminalKeyCodes.HOME) }, width = 48.dp)
                        PcKeyButton("End", { onSendKey(TerminalKeyCodes.END) }, width = 44.dp)
                        PcKeyButton("PgUp", { onSendKey(TerminalKeyCodes.PAGE_UP) }, width = 48.dp)
                        PcKeyButton("PgDn", { onSendKey(TerminalKeyCodes.PAGE_DOWN) }, width = 48.dp)
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // Row 3: 控制区 (TAB, Backspace, Enter, 方向键)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        PcKeyButton("TAB", { onSendKey(TerminalKeyCodes.TAB) }, width = 48.dp)
                        PcKeyButton("BKSP", { onSendKey(TerminalKeyCodes.BACKSPACE) }, width = 50.dp)
                        PcKeyButton("ENTER", { onSendKey(TerminalKeyCodes.ENTER) }, width = 60.dp, color = ObsidianPrimary)

                        Spacer(modifier = Modifier.width(6.dp))

                        // 十字方向键
                        PcKeyButton("←", { onSendKey(TerminalKeyCodes.ARROW_LEFT) }, width = 38.dp)
                        PcKeyButton("↑", { onSendKey(TerminalKeyCodes.ARROW_UP) }, width = 38.dp)
                        PcKeyButton("↓", { onSendKey(TerminalKeyCodes.ARROW_DOWN) }, width = 38.dp)
                        PcKeyButton("→", { onSendKey(TerminalKeyCodes.ARROW_RIGHT) }, width = 38.dp)
                    }
                }
            }
        }
    }
}

@Composable
private fun ModeSwitchButton(
    icon: ImageVector,
    label: String,
    isActive: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .height(34.dp)
            .clip(RoundedCornerShape(6.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(6.dp),
        color = if (isActive) ObsidianPrimary.copy(alpha = 0.2f) else ObsidianSurfaceContainerHigh,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (isActive) ObsidianPrimary else ObsidianOutlineVariant
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (isActive) ObsidianPrimary else ObsidianTextSecondary,
                modifier = Modifier.size(13.dp)
            )
            Text(
                text = label,
                fontSize = 11.sp,
                fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                color = if (isActive) ObsidianPrimary else ObsidianTextPrimary
            )
        }
    }
}

@Composable
private fun PcKeyButton(
    label: String,
    onClick: () -> Unit,
    width: androidx.compose.ui.unit.Dp = 40.dp,
    color: Color = ObsidianTextPrimary
) {
    Surface(
        modifier = Modifier
            .width(width)
            .height(34.dp)
            .clip(RoundedCornerShape(5.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(5.dp),
        color = ObsidianSurfaceContainerHighest,
        border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianOutlineVariant)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = label,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                fontFamily = FontFamily.Monospace,
                color = color
            )
        }
    }
}

@Composable
private fun AccessoryButton(
    label: String,
    onClick: () -> Unit,
    isActive: Boolean = false,
    activeBg: Color = ObsidianPrimary.copy(alpha = 0.2f),
    activeBorder: Color = ObsidianPrimary,
    textColor: Color = ObsidianTextPrimary
) {
    val bg = if (isActive) activeBg else ObsidianSurfaceContainerHigh
    val border = if (isActive) activeBorder else ObsidianOutlineVariant

    Surface(
        modifier = Modifier
            .height(34.dp)
            .clip(RoundedCornerShape(6.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(6.dp),
        color = bg,
        border = androidx.compose.foundation.BorderStroke(1.dp, border)
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                fontSize = 12.sp,
                fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                color = if (isActive) ObsidianPrimary else textColor,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

@Composable
private fun AccessoryIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .height(34.dp)
            .clip(RoundedCornerShape(6.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(6.dp),
        color = ObsidianSurfaceContainerHigh,
        border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianOutlineVariant)
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 7.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = ObsidianTextPrimary,
                modifier = Modifier.size(15.dp)
            )
        }
    }
}
