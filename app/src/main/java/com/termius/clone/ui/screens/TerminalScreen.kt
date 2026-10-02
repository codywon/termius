package com.termius.clone.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.termius.clone.terminal.session.SessionManager
import com.termius.clone.terminal.session.SessionState
import com.termius.clone.terminal.tunnel.TunnelManager
import com.termius.clone.ui.components.QuickCombo
import com.termius.clone.ui.components.TerminalAccessoryBar
import com.termius.clone.ui.components.TerminalView
import com.termius.clone.ui.theme.*
import kotlinx.coroutines.delay
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TerminalScreen(
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val sessions by SessionManager.sessions.collectAsState()
    val currentSessionId by SessionManager.currentSessionId.collectAsState()
    val tunnels by TunnelManager.rules.collectAsState()

    var isCtrlActive by remember { mutableStateOf(false) }
    var isAltActive by remember { mutableStateOf(false) }
    var showAddComboDialog by remember { mutableStateOf(false) }
    var showPortForwardingDialog by remember { mutableStateOf(false) }

    // 会话在线运行计时器 (TeamX Telemetry)
    var uptimeSeconds by remember { mutableLongStateOf(42L) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            uptimeSeconds++
        }
    }

    val formattedUptime = remember(uptimeSeconds) {
        val hours = uptimeSeconds / 3600
        val mins = (uptimeSeconds % 3600) / 60
        val secs = uptimeSeconds % 60
        String.format(Locale.US, "%02d:%02d:%02d", hours, mins, secs)
    }

    // 自定义组合键列表
    var customCombos by remember {
        mutableStateOf(
            listOf(
                QuickCombo("docker ps", "docker ps\n"),
                QuickCombo("ll -h", "ls -lah\n")
            )
        )
    }

    val activeSession = sessions.find { it.id == currentSessionId }
    val activeTunnel = tunnels.firstOrNull { it.isRunning }

    LaunchedEffect(sessions) {
        if (sessions.isEmpty()) {
            onNavigateBack()
        }
    }

    Scaffold(
        topBar = {
            Surface(color = ObsidianSurfaceContainerLow) {
                Column(modifier = Modifier.fillMaxWidth().statusBarsPadding()) {
                    // Header Bar with Session Tabs
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = ObsidianTextPrimary)
                        }

                        // Session Tabs Strip
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            for (session in sessions) {
                                val isSelected = session.id == currentSessionId
                                val sessionState by session.sessionState.collectAsState()

                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (isSelected) ObsidianSurfaceContainerHighest else ObsidianSurfaceContainer,
                                    border = if (isSelected) androidx.compose.foundation.BorderStroke(1.dp, ObsidianPrimary) else null,
                                    onClick = { SessionManager.selectSession(session.id) }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        // Status dot
                                        val dotColor = when (sessionState) {
                                            SessionState.CONNECTED -> ObsidianPrimary
                                            SessionState.CONNECTING, SessionState.AUTHENTICATING -> ObsidianTertiary
                                            SessionState.ERROR, SessionState.DISCONNECTED -> ObsidianError
                                        }
                                        Box(
                                            modifier = Modifier
                                                .size(7.dp)
                                                .background(dotColor, androidx.compose.foundation.shape.CircleShape)
                                        )

                                        Text(
                                            text = "${session.host.username}@${session.host.label}",
                                            color = if (isSelected) Color.White else ObsidianTextSecondary,
                                            fontSize = 12.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            fontFamily = FontFamily.Monospace
                                        )

                                        IconButton(
                                            onClick = { SessionManager.closeSession(context, session.id) },
                                            modifier = Modifier.size(16.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Close,
                                                contentDescription = "Close",
                                                tint = ObsidianTextMuted,
                                                modifier = Modifier.size(12.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Port Forwarding Shortcut
                        IconButton(onClick = { showPortForwardingDialog = true }) {
                            Icon(
                                Icons.Default.SyncAlt,
                                contentDescription = "Tunnels",
                                tint = if (activeTunnel != null) ObsidianPrimary else ObsidianTextSecondary
                            )
                        }

                        if (activeSession != null) {
                            IconButton(onClick = { activeSession.disconnect() }) {
                                Icon(Icons.Default.PowerSettingsNew, contentDescription = "Disconnect", tint = ObsidianError)
                            }
                        }
                    }

                    // Session Telemetry Strip
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(ObsidianSurfaceContainerLowest)
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Quick context info
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                color = ObsidianSurfaceContainerHigh,
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                                ) {
                                    Icon(Icons.Default.Lan, contentDescription = null, tint = ObsidianSecondary, modifier = Modifier.size(12.dp))
                                    Text("80x24 xterm-256", color = ObsidianTextSecondary, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                                }
                            }

                            Surface(
                                color = ObsidianSurfaceContainerHigh,
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                                ) {
                                    Icon(Icons.Default.Speed, contentDescription = null, tint = ObsidianPrimary, modifier = Modifier.size(12.dp))
                                    Text("Load: 0.12", color = ObsidianPrimary, fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                                }
                            }
                        }

                        // Telemetry Badge (Ping + Elapsed Uptime)
                        Surface(
                            color = ObsidianSurfaceContainerHigh,
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Icon(Icons.Default.Sensors, contentDescription = null, tint = ObsidianPrimary, modifier = Modifier.size(12.dp))
                                Text("38ms", color = ObsidianPrimary, fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                                Text("|", color = ObsidianOutlineVariant, fontSize = 10.sp)
                                Icon(Icons.Default.Timer, contentDescription = null, tint = ObsidianTextSecondary, modifier = Modifier.size(12.dp))
                                Text(formattedUptime, color = ObsidianTextSecondary, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                            }
                        }
                    }
                }
            }
        },
        bottomBar = {
            if (activeSession != null) {
                TerminalAccessoryBar(
                    isCtrlActive = isCtrlActive,
                    onToggleCtrl = { isCtrlActive = !isCtrlActive },
                    isAltActive = isAltActive,
                    onToggleAlt = { isAltActive = !isAltActive },
                    onSendKey = { key -> activeSession.write(key) },
                    onOpenCustomKeys = { showAddComboDialog = true },
                    customCombos = customCombos,
                    activeTunnel = activeTunnel,
                    onManageTunnels = { showPortForwardingDialog = true }
                )
            }
        },
        containerColor = ObsidianBackground
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(ObsidianBackground)
        ) {
            if (activeSession != null) {
                TerminalView(
                    session = activeSession,
                    isCtrlActive = isCtrlActive,
                    onConsumeCtrl = { isCtrlActive = false },
                    isAltActive = isAltActive,
                    onConsumeAlt = { isAltActive = false }
                )
            } else {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No active terminal sessions", color = ObsidianTextMuted)
                }
            }

            // Port Forwarding Dialog
            if (showPortForwardingDialog) {
                PortForwardingDialog(
                    activeSession = activeSession,
                    onDismissRequest = { showPortForwardingDialog = false }
                )
            }

            // Add Custom Combo Dialog
            if (showAddComboDialog) {
                var comboLabel by remember { mutableStateOf("") }
                var comboCmd by remember { mutableStateOf("") }

                AlertDialog(
                    onDismissRequest = { showAddComboDialog = false },
                    title = { Text("Add Custom Hotkey / Combo", color = ObsidianTextPrimary, fontWeight = FontWeight.Bold) },
                    text = {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text("Define a custom macro or quick command to pin to your terminal toolbar.", fontSize = 12.sp, color = ObsidianTextSecondary)
                            OutlinedTextField(
                                value = comboLabel,
                                onValueChange = { comboLabel = it },
                                label = { Text("Key Label") },
                                placeholder = { Text("e.g. git status") },
                                singleLine = true,
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = comboCmd,
                                onValueChange = { comboCmd = it },
                                label = { Text("Command Sequence") },
                                placeholder = { Text("git status\n") },
                                minLines = 2,
                                maxLines = 4,
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                if (comboLabel.isNotBlank()) {
                                    val seq = if (comboCmd.endsWith("\n")) comboCmd else "$comboCmd\n"
                                    customCombos = customCombos + QuickCombo(comboLabel.trim(), seq)
                                    showAddComboDialog = false
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = ObsidianPrimary),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Add to Toolbar", color = ObsidianOnPrimary, fontWeight = FontWeight.Bold)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showAddComboDialog = false }) {
                            Text("Cancel", color = ObsidianTextMuted)
                        }
                    },
                    containerColor = ObsidianSurfaceContainerLow
                )
            }
        }
    }
}
