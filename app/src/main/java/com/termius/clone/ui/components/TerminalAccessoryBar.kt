package com.termius.clone.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.termius.clone.terminal.engine.TerminalKeyCodes
import com.termius.clone.ui.theme.*

data class QuickCombo(
    val label: String,
    val sequence: String,
    val isDanger: Boolean = false
)

@Composable
fun TerminalAccessoryBar(
    isCtrlActive: Boolean,
    onToggleCtrl: () -> Unit,
    isAltActive: Boolean,
    onToggleAlt: () -> Unit,
    onSendKey: (String) -> Unit,
    onOpenCustomKeys: () -> Unit,
    customCombos: List<QuickCombo>,
    modifier: Modifier = Modifier
) {
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

    val allCombos = remember(customCombos) {
        defaultCombos + customCombos
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(ObsidianSurfaceContainerLowest)
            .padding(vertical = 4.dp),
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

        // Row 2: Stitch Quick Combos & Snippets Strip
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
