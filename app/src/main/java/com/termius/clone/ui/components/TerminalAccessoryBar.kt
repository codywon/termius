package com.termius.clone.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.termius.clone.data.model.TunnelRule
import com.termius.clone.terminal.engine.TerminalKeyCodes
import com.termius.clone.ui.theme.*

data class QuickCombo(
    val label: String,
    val sequence: String,
    val isDanger: Boolean = false
)

enum class AccessoryMode {
    KEYS,
    DPAD,
    QUICK
}

@Composable
fun TerminalAccessoryBar(
    isCtrlActive: Boolean,
    onToggleCtrl: () -> Unit,
    isAltActive: Boolean,
    onToggleAlt: () -> Unit,
    onSendKey: (String) -> Unit,
    onOpenCustomKeys: () -> Unit,
    customCombos: List<QuickCombo>,
    activeTunnel: TunnelRule? = null,
    onManageTunnels: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var currentMode by remember { mutableStateOf(AccessoryMode.KEYS) }
    val clipboardManager = LocalClipboardManager.current

    val defaultCombos = remember {
        listOf(
            QuickCombo("Ctrl+C", "\u0003", isDanger = true),
            QuickCombo("Ctrl+Z", "\u001A"),
            QuickCombo("Ctrl+D", "\u0004"),
            QuickCombo("Ctrl+R", "\u0012"),
            QuickCombo("Ctrl+A", "\u0001"),
            QuickCombo("Ctrl+E", "\u0005"),
            QuickCombo("sudo !!", "sudo !!\n"),
            QuickCombo(":wq", ":wq\n"),
            QuickCombo("exit", "exit\n")
        )
    }

    val quickSnippets = remember {
        listOf(
            "docker ps",
            "git status",
            "git pull",
            "htop",
            "df -h",
            "free -m",
            "systemctl status",
            "tail -f /var/log/syslog",
            "ss -tulpn"
        )
    }

    val allCombos = remember(customCombos) {
        defaultCombos + customCombos
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(ObsidianSurfaceContainerLowest)
    ) {
        // Active Port Forwarding Banner
        if (activeTunnel != null) {
            Surface(
                color = ObsidianSurfaceContainerLow,
                border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianOutlineVariant.copy(alpha = 0.3f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Default.SyncAlt,
                            contentDescription = null,
                            tint = ObsidianPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                        Column {
                            Text(
                                "PORT FORWARDING ACTIVE",
                                color = ObsidianTextPrimary,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.5.sp
                            )
                            Text(
                                "localhost:${activeTunnel.localPort} ➔ ${activeTunnel.remoteHost}:${activeTunnel.remotePort}",
                                color = ObsidianSecondary,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = ObsidianSurfaceContainerHigh,
                        onClick = onManageTunnels
                    ) {
                        Text(
                            "Manage",
                            color = ObsidianTertiary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }
            }
        }

        // Auxiliary Header & Mode Segment Switcher
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(ObsidianSurfaceContainerLow.copy(alpha = 0.95f))
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Mode Tabs (Keys / D-Pad Joystick / Quick)
            Row(
                modifier = Modifier
                    .background(ObsidianSurfaceContainerHigh.copy(alpha = 0.6f), RoundedCornerShape(6.dp))
                    .padding(2.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ModeSwitchPill(
                    label = "Keys",
                    icon = Icons.Default.Keyboard,
                    isSelected = currentMode == AccessoryMode.KEYS,
                    onClick = { currentMode = AccessoryMode.KEYS }
                )
                ModeSwitchPill(
                    label = "D-Pad",
                    icon = Icons.Default.Gamepad,
                    isSelected = currentMode == AccessoryMode.DPAD,
                    onClick = { currentMode = AccessoryMode.DPAD }
                )
                ModeSwitchPill(
                    label = "Quick",
                    icon = Icons.Default.Bolt,
                    isSelected = currentMode == AccessoryMode.QUICK,
                    onClick = { currentMode = AccessoryMode.QUICK }
                )
            }

            // Right Info Pills: Gesture & Status Indicator
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text("Tap / Hold", color = ObsidianSecondary, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                Box(
                    modifier = Modifier
                        .background(ObsidianSurfaceContainer, RoundedCornerShape(4.dp))
                        .padding(horizontal = 5.dp, vertical = 1.dp)
                ) {
                    Text(
                        "TMUX: ACTIVE",
                        color = ObsidianPrimary,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )
                }
            }
        }

        // Body Content based on Mode
        when (currentMode) {
            AccessoryMode.KEYS -> {
                // Classic Dual-Row Accessory Bar
                Column(
                    modifier = Modifier.padding(vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Row 1: Core Modifier & Navigation Keys
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        AccessoryKeyButton(text = "ESC") { onSendKey(TerminalKeyCodes.ESC) }
                        AccessoryKeyButton(text = "TAB") { onSendKey(TerminalKeyCodes.TAB) }

                        AccessoryToggleKeyButton(text = "CTRL", isActive = isCtrlActive, onClick = onToggleCtrl)
                        AccessoryToggleKeyButton(text = "ALT", isActive = isAltActive, onClick = onToggleAlt)

                        // Arrow keys
                        AccessoryIconButton(icon = Icons.Default.KeyboardArrowUp) { onSendKey(TerminalKeyCodes.ARROW_UP) }
                        AccessoryIconButton(icon = Icons.Default.KeyboardArrowDown) { onSendKey(TerminalKeyCodes.ARROW_DOWN) }
                        AccessoryIconButton(icon = Icons.AutoMirrored.Filled.ArrowBack) { onSendKey(TerminalKeyCodes.ARROW_LEFT) }
                        AccessoryIconButton(icon = Icons.AutoMirrored.Filled.ArrowForward) { onSendKey(TerminalKeyCodes.ARROW_RIGHT) }

                        // Common symbols
                        val symbols = listOf("/", "-", "~", "|", "$", ":", "=", "_", ">")
                        symbols.forEach { s ->
                            AccessoryKeyButton(text = s) { onSendKey(s) }
                        }
                    }

                    // Row 2: Quick Combos & Snippets Strip
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        allCombos.forEach { combo ->
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = if (combo.isDanger) ObsidianError.copy(alpha = 0.2f) else ObsidianSurfaceContainerHigh,
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp,
                                    if (combo.isDanger) ObsidianError.copy(alpha = 0.5f) else ObsidianOutlineVariant
                                ),
                                onClick = { onSendKey(combo.sequence) },
                                modifier = Modifier.height(28.dp)
                            ) {
                                Box(
                                    modifier = Modifier.padding(horizontal = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = combo.label,
                                        fontSize = 11.sp,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Medium,
                                        color = if (combo.isDanger) ObsidianError else ObsidianTextPrimary
                                    )
                                }
                            }
                        }

                        // + Add Combo button
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = ObsidianPrimary.copy(alpha = 0.15f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianPrimary.copy(alpha = 0.6f)),
                            onClick = onOpenCustomKeys,
                            modifier = Modifier.height(28.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, tint = ObsidianPrimary, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(3.dp))
                                Text("+ Add Combo", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = ObsidianPrimary)
                            }
                        }
                    }
                }
            }

            AccessoryMode.DPAD -> {
                // Tactile D-Pad Navigation Dock
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(ObsidianSurfaceContainerLow)
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Left Column: Modifier Controls
                        Column(
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.width(76.dp)
                        ) {
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                AccessoryMiniButton("ESC", modifier = Modifier.weight(1f)) { onSendKey(TerminalKeyCodes.ESC) }
                                AccessoryMiniButton("TAB", modifier = Modifier.weight(1f)) { onSendKey(TerminalKeyCodes.TAB) }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                AccessoryMiniToggleButton("CTRL", isActive = isCtrlActive, modifier = Modifier.weight(1f), onClick = onToggleCtrl)
                                AccessoryMiniToggleButton("ALT", isActive = isAltActive, modifier = Modifier.weight(1f), onClick = onToggleAlt)
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                AccessoryMiniButton("PgUp", modifier = Modifier.weight(1f)) { onSendKey(TerminalKeyCodes.PAGE_UP) }
                                AccessoryMiniButton("PgDn", modifier = Modifier.weight(1f)) { onSendKey(TerminalKeyCodes.PAGE_DOWN) }
                            }
                        }

                        // Center: Tactile 4-Way D-Pad Joystick
                        Box(
                            modifier = Modifier
                                .size(136.dp)
                                .background(ObsidianSurfaceContainerLowest, CircleShape)
                                .border(2.dp, ObsidianOutlineVariant.copy(alpha = 0.4f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            // UP Button
                            IconButton(
                                onClick = { onSendKey(TerminalKeyCodes.ARROW_UP) },
                                modifier = Modifier
                                    .align(Alignment.TopCenter)
                                    .padding(top = 2.dp)
                                    .size(38.dp)
                            ) {
                                Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Up", tint = ObsidianTextPrimary, modifier = Modifier.size(24.dp))
                            }

                            // DOWN Button
                            IconButton(
                                onClick = { onSendKey(TerminalKeyCodes.ARROW_DOWN) },
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .padding(bottom = 2.dp)
                                    .size(38.dp)
                            ) {
                                Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Down", tint = ObsidianTextPrimary, modifier = Modifier.size(24.dp))
                            }

                            // LEFT Button
                            IconButton(
                                onClick = { onSendKey(TerminalKeyCodes.ARROW_LEFT) },
                                modifier = Modifier
                                    .align(Alignment.CenterStart)
                                    .padding(start = 2.dp)
                                    .size(38.dp)
                            ) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Left", tint = ObsidianTextPrimary, modifier = Modifier.size(22.dp))
                            }

                            // RIGHT Button
                            IconButton(
                                onClick = { onSendKey(TerminalKeyCodes.ARROW_RIGHT) },
                                modifier = Modifier
                                    .align(Alignment.CenterEnd)
                                    .padding(end = 2.dp)
                                    .size(38.dp)
                            ) {
                                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Right", tint = ObsidianTextPrimary, modifier = Modifier.size(22.dp))
                            }

                            // CENTER DISC: ENTER KEY
                            Box(
                                modifier = Modifier
                                    .size(54.dp)
                                    .background(ObsidianSurfaceContainerHighest, CircleShape)
                                    .border(1.5.dp, ObsidianPrimary.copy(alpha = 0.5f), CircleShape)
                                    .clip(CircleShape)
                                    .clickable { onSendKey(TerminalKeyCodes.ENTER) },
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(
                                        Icons.Default.KeyboardReturn,
                                        contentDescription = "Enter",
                                        tint = ObsidianPrimary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        "ENTER",
                                        color = ObsidianTextPrimary,
                                        fontSize = 8.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            }
                        }

                        // Right Column: Quick CLI Symbols & Paste
                        Column(
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.width(76.dp)
                        ) {
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                AccessoryMiniButton("/", modifier = Modifier.weight(1f)) { onSendKey("/") }
                                AccessoryMiniButton("|", modifier = Modifier.weight(1f)) { onSendKey("|") }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                AccessoryMiniButton("~", modifier = Modifier.weight(1f)) { onSendKey("~") }
                                AccessoryMiniButton(":", modifier = Modifier.weight(1f)) { onSendKey(":") }
                            }
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = ObsidianSurfaceContainer,
                                border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianOutlineVariant.copy(alpha = 0.4f)),
                                onClick = {
                                    val clip = clipboardManager.getText()?.text
                                    if (!clip.isNullOrEmpty()) {
                                        onSendKey(clip)
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(26.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(Icons.Default.ContentPaste, contentDescription = null, tint = ObsidianPrimary, modifier = Modifier.size(12.dp))
                                    Spacer(modifier = Modifier.width(2.dp))
                                    Text("PASTE", color = ObsidianTextPrimary, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // Horizontal Quick Access Ribbon
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(vertical = 2.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val ribbonKeys = listOf("-", "_", "`", "&&", ">>", "^C", "^Z", "^D", "^R")
                        ribbonKeys.forEach { key ->
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = ObsidianSurfaceContainerLowest,
                                border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianOutlineVariant.copy(alpha = 0.3f)),
                                onClick = {
                                    when (key) {
                                        "^C" -> onSendKey("\u0003")
                                        "^Z" -> onSendKey("\u001A")
                                        "^D" -> onSendKey("\u0004")
                                        "^R" -> onSendKey("\u0012")
                                        else -> onSendKey(key)
                                    }
                                },
                                modifier = Modifier.height(26.dp)
                            ) {
                                Box(
                                    modifier = Modifier.padding(horizontal = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = key,
                                        fontSize = 11.sp,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (key.startsWith("^")) ObsidianTertiary else ObsidianSecondary
                                    )
                                }
                            }
                        }
                    }
                }
            }

            AccessoryMode.QUICK -> {
                // Quick Command / Snippet Injector Strip
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    quickSnippets.forEach { cmd ->
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = ObsidianSurfaceContainerHigh,
                            border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianOutlineVariant),
                            onClick = { onSendKey("$cmd\n") },
                            modifier = Modifier.height(30.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null, tint = ObsidianPrimary, modifier = Modifier.size(12.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = cmd,
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Medium,
                                    color = ObsidianTextPrimary
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ModeSwitchPill(
    label: String,
    icon: ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(4.dp),
        color = if (isSelected) ObsidianPrimary else Color.Transparent,
        onClick = onClick
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isSelected) ObsidianOnPrimary else ObsidianTextSecondary,
                modifier = Modifier.size(13.dp)
            )
            Text(
                text = label,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = if (isSelected) ObsidianOnPrimary else ObsidianTextSecondary
            )
        }
    }
}

@Composable
fun AccessoryMiniButton(
    text: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(5.dp),
        color = ObsidianSurfaceContainerHigh,
        border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianOutlineVariant.copy(alpha = 0.3f)),
        onClick = onClick,
        modifier = modifier.height(28.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = text,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                color = ObsidianTextPrimary
            )
        }
    }
}

@Composable
fun AccessoryMiniToggleButton(
    text: String,
    isActive: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(5.dp),
        color = if (isActive) ObsidianPrimary else ObsidianSurfaceContainerHigh,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (isActive) ObsidianPrimary else ObsidianOutlineVariant.copy(alpha = 0.3f)
        ),
        onClick = onClick,
        modifier = modifier.height(28.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = text,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                color = if (isActive) ObsidianOnPrimary else ObsidianTextSecondary
            )
        }
    }
}

@Composable
fun AccessoryKeyButton(
    text: String,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        shape = RoundedCornerShape(6.dp),
        colors = ButtonDefaults.buttonColors(containerColor = ObsidianSurfaceContainerHigh),
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
        modifier = Modifier.height(34.dp)
    ) {
        Text(text = text, fontSize = 12.sp, color = ObsidianTextPrimary, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun AccessoryToggleKeyButton(
    text: String,
    isActive: Boolean,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        shape = RoundedCornerShape(6.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isActive) ObsidianPrimary else ObsidianSurfaceContainerHigh
        ),
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
        modifier = Modifier.height(34.dp)
    ) {
        Text(
            text = text,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = if (isActive) ObsidianOnPrimary else ObsidianTextSecondary
        )
    }
}

@Composable
fun AccessoryIconButton(
    icon: ImageVector,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        shape = RoundedCornerShape(6.dp),
        colors = ButtonDefaults.buttonColors(containerColor = ObsidianSurfaceContainerHigh),
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
        modifier = Modifier.height(34.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = ObsidianTextPrimary,
            modifier = Modifier.size(16.dp)
        )
    }
}
