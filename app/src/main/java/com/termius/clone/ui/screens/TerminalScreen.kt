package com.termius.clone.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.termius.clone.terminal.session.SessionManager
import com.termius.clone.terminal.session.SessionState
import com.termius.clone.ui.components.TerminalAccessoryBar
import com.termius.clone.ui.components.TerminalView
import com.termius.clone.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TerminalScreen(
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val terminalFocusRequester = remember { FocusRequester() }

    val sessions by SessionManager.sessions.collectAsState()
    val currentSessionId by SessionManager.currentSessionId.collectAsState()

    var isCtrlActive by remember { mutableStateOf(false) }
    var isAltActive by remember { mutableStateOf(false) }

    val activeSession = sessions.find { it.id == currentSessionId }

    LaunchedEffect(sessions) {
        if (sessions.isEmpty()) {
            onNavigateBack()
        }
    }

    Scaffold(
        topBar = {
            Surface(color = ObsidianSurfaceContainerLow) {
                Column(modifier = Modifier.fillMaxWidth().statusBarsPadding()) {
                    // Header Bar with Session Tabs (Clean Termius Style)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp, vertical = 4.dp),
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
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
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

                        if (activeSession != null) {
                            IconButton(onClick = { activeSession.disconnect() }) {
                                Icon(Icons.Default.PowerSettingsNew, contentDescription = "Disconnect", tint = ObsidianError)
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
                    onToggleKeyboard = {
                        try {
                            terminalFocusRequester.requestFocus()
                            keyboardController?.show()
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
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
                    onConsumeAlt = { isAltActive = false },
                    externalFocusRequester = terminalFocusRequester
                )
            } else {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No active terminal sessions", color = ObsidianTextMuted)
                }
            }
        }
    }
}
