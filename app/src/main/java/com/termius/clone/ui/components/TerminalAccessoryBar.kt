package com.termius.clone.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.termius.clone.terminal.engine.TerminalKeyCodes
import com.termius.clone.ui.theme.*

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
    val clipboardManager = LocalClipboardManager.current

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .imePadding(),
        color = ObsidianSurfaceContainerLowest,
        shadowElevation = 4.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 6.dp, vertical = 5.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // ESC
            AccessoryButton(label = "ESC", onClick = { onSendKey(TerminalKeyCodes.ESC) })

            // TAB
            AccessoryButton(label = "TAB", onClick = { onSendKey(TerminalKeyCodes.TAB) })

            // CTRL (Toggle active state)
            AccessoryButton(
                label = "CTRL",
                isActive = isCtrlActive,
                activeBg = ObsidianPrimary.copy(alpha = 0.25f),
                activeBorder = ObsidianPrimary,
                onClick = onToggleCtrl
            )

            // ALT (Toggle active state)
            AccessoryButton(
                label = "ALT",
                isActive = isAltActive,
                activeBg = ObsidianPrimary.copy(alpha = 0.25f),
                activeBorder = ObsidianPrimary,
                onClick = onToggleAlt
            )

            // Essential terminal symbols
            AccessoryButton(label = "/", onClick = { onSendKey("/") })
            AccessoryButton(label = "-", onClick = { onSendKey("-") })
            AccessoryButton(label = "|", onClick = { onSendKey("|") })
            AccessoryButton(label = "~", onClick = { onSendKey("~") })
            AccessoryButton(label = ":", onClick = { onSendKey(":") })
            AccessoryButton(label = "$", onClick = { onSendKey("$") })

            // Arrow Keys
            AccessoryIconButton(icon = Icons.Default.KeyboardArrowUp) {
                onSendKey(TerminalKeyCodes.ARROW_UP)
            }
            AccessoryIconButton(icon = Icons.Default.KeyboardArrowDown) {
                onSendKey(TerminalKeyCodes.ARROW_DOWN)
            }
            AccessoryIconButton(icon = Icons.AutoMirrored.Filled.ArrowBack) {
                onSendKey(TerminalKeyCodes.ARROW_LEFT)
            }
            AccessoryIconButton(icon = Icons.AutoMirrored.Filled.ArrowForward) {
                onSendKey(TerminalKeyCodes.ARROW_RIGHT)
            }

            // High frequency commands
            AccessoryButton(
                label = "Ctrl+C",
                textColor = ObsidianError,
                onClick = { onSendKey(TerminalKeyCodes.CTRL_C) }
            )

            AccessoryButton(
                label = "Ctrl+D",
                textColor = ObsidianTextSecondary,
                onClick = { onSendKey("\u0004") }
            )

            // PASTE from Clipboard
            Surface(
                modifier = Modifier
                    .height(34.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .clickable {
                        val clip = clipboardManager.getText()?.text
                        if (!clip.isNullOrEmpty()) {
                            onSendKey(clip)
                        }
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
                    Text(
                        text = "PASTE",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = ObsidianTextPrimary,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            // Keyboard Toggle Button
            Surface(
                modifier = Modifier
                    .height(34.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .clickable(onClick = onToggleKeyboard),
                shape = RoundedCornerShape(6.dp),
                color = ObsidianPrimary.copy(alpha = 0.15f),
                border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianPrimary.copy(alpha = 0.5f))
            ) {
                Box(
                    modifier = Modifier.padding(horizontal = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Keyboard,
                        contentDescription = "Toggle Keyboard",
                        tint = ObsidianPrimary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
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
            modifier = Modifier.padding(horizontal = 9.dp),
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
            modifier = Modifier.padding(horizontal = 8.dp),
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
