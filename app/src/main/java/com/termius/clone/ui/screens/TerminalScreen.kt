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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import com.termius.clone.terminal.session.SessionManager
import com.termius.clone.terminal.session.SessionState
import com.termius.clone.ui.components.TerminalAccessoryBar
import com.termius.clone.ui.components.TerminalInputMode
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
    var currentInputMode by remember { mutableStateOf(TerminalInputMode.IME) }

    val activeSession = sessions.find { it.id == currentSessionId }

    LaunchedEffect(sessions) {
        if (sessions.isEmpty()) {
            onNavigateBack()
        }
    }

    val theme = LocalAppTheme.current

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            Surface(color = theme.surfaceContainerLow) {
                Column(modifier = Modifier.fillMaxWidth().statusBarsPadding()) {
                    // Header Bar with Session Tabs (Clean TermX Mobile Style)
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
                                    border = if (isSelected) androidx.compose.foundation.BorderStroke(1.dp, theme.primary) else null,
                                    onClick = { SessionManager.selectSession(session.id) }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        // Status dot
                                        val dotColor = when (sessionState) {
                                            SessionState.CONNECTED -> theme.primary
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
                                            fontFamily = FontFamily.Monospace,
                                            maxLines = 1,
                                            softWrap = false
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
            if (activeSession != null && currentInputMode != TerminalInputMode.HIDDEN) {
                TerminalAccessoryBar(
                    currentMode = currentInputMode,
                    onModeChange = { newMode -> currentInputMode = newMode },
                    isCtrlActive = isCtrlActive,
                    onToggleCtrl = { isCtrlActive = !isCtrlActive },
                    isAltActive = isAltActive,
                    onToggleAlt = { isAltActive = !isAltActive },
                    onSendKey = { key -> activeSession.write(key) },
                    onRequestShowKeyboard = {
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
                    externalFocusRequester = terminalFocusRequester,
                    onTapTerminal = {
                        if (currentInputMode == TerminalInputMode.HIDDEN) {
                            currentInputMode = TerminalInputMode.IME
                        }
                    }
                )

                // 当模式为 HIDDEN 时，在右下角悬浮一个黑曜石风格的极简键盘唤醒胶囊
                if (currentInputMode == TerminalInputMode.HIDDEN) {
                    Surface(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(16.dp)
                            .clip(CircleShape)
                            .clickable {
                                currentInputMode = TerminalInputMode.IME
                                terminalFocusRequester.requestFocus()
                                keyboardController?.show()
                            },
                        shape = CircleShape,
                        color = ObsidianSurfaceContainerHigh.copy(alpha = 0.9f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianOutlineVariant)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(Icons.Default.Keyboard, contentDescription = "Open Keyboard", tint = ObsidianPrimary, modifier = Modifier.size(16.dp))
                            Text("键盘", color = ObsidianTextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                }
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
