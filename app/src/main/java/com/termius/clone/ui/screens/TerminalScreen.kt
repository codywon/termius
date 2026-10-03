package com.termius.clone.ui.screens

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.termius.clone.terminal.session.SessionManager
import com.termius.clone.terminal.session.SessionState
import com.termius.clone.ui.components.TerminalAccessoryBar
import com.termius.clone.ui.components.TerminalInputMode
import com.termius.clone.ui.components.TerminalView
import com.termius.clone.ui.theme.*
import com.termius.clone.util.Strings

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TerminalScreen(
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val terminalFocusRequester = remember { FocusRequester() }

    val sessions by SessionManager.sessions.collectAsState()
    val currentSessionId by SessionManager.currentSessionId.collectAsState()

    var isCtrlActive by remember { mutableStateOf(false) }
    var isAltActive by remember { mutableStateOf(false) }
    var currentInputMode by remember { mutableStateOf(TerminalInputMode.IME) }
    var showDisconnectDialog by remember { mutableStateOf(false) }

    val activeSession = sessions.find { it.id == currentSessionId }

    LaunchedEffect(sessions) {
        if (sessions.isEmpty()) {
            onNavigateBack()
        }
    }

    val theme = LocalAppTheme.current
    val termTheme = ThemeManager.currentTerminalTheme

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            Surface(
                color = theme.surfaceContainerLow,
                shadowElevation = if (theme.isDark) 0.dp else 1.dp
            ) {
                Column(modifier = Modifier.fillMaxWidth().statusBarsPadding()) {
                    // Header Bar with Session Tabs (Clean TermX Mobile Style, compact in landscape)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(
                                horizontal = 4.dp,
                                vertical = if (isLandscape) 1.dp else 4.dp
                            ),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = onNavigateBack,
                            modifier = if (isLandscape) Modifier.size(32.dp) else Modifier
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = theme.textPrimary, modifier = if (isLandscape) Modifier.size(18.dp) else Modifier.size(24.dp))
                        }

                        // Session Tabs Strip (极简现代扁平微胶囊设计，彻底去除绿框灰底)
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
                                    color = if (isSelected) {
                                        theme.primary.copy(alpha = if (theme.isDark) 0.20f else 0.12f)
                                    } else {
                                        Color.Transparent
                                    },
                                    border = if (isSelected) {
                                        androidx.compose.foundation.BorderStroke(1.dp, theme.primary.copy(alpha = 0.35f))
                                    } else null,
                                    onClick = {
                                        if (isSelected && (sessionState == SessionState.DISCONNECTED || sessionState == SessionState.ERROR)) {
                                            session.reconnect()
                                        } else {
                                            SessionManager.selectSession(session.id)
                                        }
                                    }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(
                                            horizontal = if (isLandscape) 8.dp else 10.dp,
                                            vertical = if (isLandscape) 3.dp else 6.dp
                                        ),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                                    ) {
                                        // Status dot
                                        val dotColor = when (sessionState) {
                                            SessionState.CONNECTED -> Color(0xFF22C55E)
                                            SessionState.CONNECTING, SessionState.AUTHENTICATING -> Color(0xFFF59E0B)
                                            SessionState.ERROR, SessionState.DISCONNECTED -> Color(0xFFEF4444)
                                        }
                                        Box(
                                            modifier = Modifier
                                                .size(if (isLandscape) 6.dp else 7.dp)
                                                .background(dotColor, CircleShape)
                                        )

                                        Text(
                                            text = "${session.host.username}@${session.host.label}",
                                            color = if (isSelected) {
                                                if (theme.isDark) theme.primary else theme.textPrimary
                                            } else {
                                                theme.textSecondary
                                            },
                                            fontSize = if (isLandscape) 11.sp else 12.sp,
                                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                            fontFamily = FontFamily.Monospace,
                                            maxLines = 1,
                                            softWrap = false
                                        )

                                        if (sessions.size > 1) {
                                            IconButton(
                                                onClick = { SessionManager.closeSession(context, session.id) },
                                                modifier = Modifier.size(if (isLandscape) 14.dp else 16.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Close,
                                                    contentDescription = "Close",
                                                    tint = if (isSelected) theme.primary.copy(alpha = 0.7f) else theme.textMuted,
                                                    modifier = Modifier.size(if (isLandscape) 10.dp else 12.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        if (activeSession != null) {
                            val activeState by activeSession.sessionState.collectAsState()
                            val isConnected = activeState == SessionState.CONNECTED ||
                                    activeState == SessionState.CONNECTING ||
                                    activeState == SessionState.AUTHENTICATING

                            if (isConnected) {
                                IconButton(
                                    onClick = { showDisconnectDialog = true },
                                    modifier = if (isLandscape) Modifier.size(32.dp) else Modifier
                                ) {
                                    Icon(
                                        Icons.Default.PowerSettingsNew,
                                        contentDescription = if (Strings.isZh) "断开连接" else "Disconnect",
                                        tint = Color(0xFFEF4444).copy(alpha = 0.88f),
                                        modifier = if (isLandscape) Modifier.size(18.dp) else Modifier.size(24.dp)
                                    )
                                }
                            } else {
                                IconButton(
                                    onClick = { activeSession.reconnect() },
                                    modifier = if (isLandscape) Modifier.size(32.dp) else Modifier
                                ) {
                                    Icon(
                                        Icons.Default.Refresh,
                                        contentDescription = if (Strings.isZh) "重新连接" else "Reconnect",
                                        tint = Color(0xFF22C55E),
                                        modifier = if (isLandscape) Modifier.size(18.dp) else Modifier.size(24.dp)
                                    )
                                }
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
        containerColor = termTheme.background
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(termTheme.background)
        ) {
            if (activeSession != null) {
                val activeState by activeSession.sessionState.collectAsState()

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

                // 当会话断开或发生错误时，在顶部提供平滑的一键快速重连微胶囊
                if (activeState == SessionState.DISCONNECTED || activeState == SessionState.ERROR) {
                    Surface(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 10.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .clickable { activeSession.reconnect() },
                        shape = RoundedCornerShape(16.dp),
                        color = theme.surfaceContainerHigh.copy(alpha = 0.95f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF22C55E).copy(alpha = 0.45f)),
                        shadowElevation = 4.dp
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, tint = Color(0xFF22C55E), modifier = Modifier.size(15.dp))
                            Text(
                                text = if (Strings.isZh) "连接已断开 · 点击快速重连" else "Disconnected · Tap to reconnect",
                                color = theme.textPrimary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                // 当模式为 HIDDEN 时，在右下角悬浮一个极简键盘唤醒胶囊
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
                        color = theme.surfaceContainerHigh.copy(alpha = 0.95f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, theme.outline.copy(alpha = 0.5f)),
                        shadowElevation = 4.dp
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(Icons.Default.Keyboard, contentDescription = "Open Keyboard", tint = theme.primary, modifier = Modifier.size(16.dp))
                            Text(if (Strings.isZh) "键盘" else "Keyboard", color = theme.textPrimary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            } else {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(if (Strings.isZh) "暂无活跃终端会话" else "No active terminal sessions", color = theme.textMuted)
                }
            }
        }
    }

    if (showDisconnectDialog && activeSession != null) {
        AlertDialog(
            onDismissRequest = { showDisconnectDialog = false },
            title = {
                Text(
                    text = if (Strings.isZh) "断开会话" else "Disconnect Session",
                    fontWeight = FontWeight.Bold,
                    color = theme.textPrimary
                )
            },
            text = {
                Text(
                    text = if (Strings.isZh) {
                        "确定要断开与 ${activeSession.host.label} (${activeSession.host.hostname}) 的 SSH 会话吗？断开后可随时点击顶栏刷新按钮重新连接。"
                    } else {
                        "Are you sure you want to disconnect from ${activeSession.host.label} (${activeSession.host.hostname})? You can reconnect anytime."
                    },
                    color = theme.textPrimary,
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDisconnectDialog = false
                        activeSession.disconnect()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444), contentColor = Color.White)
                ) {
                    Text(if (Strings.isZh) "断开连接" else "Disconnect", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = {
                        val sid = activeSession.id
                        showDisconnectDialog = false
                        SessionManager.closeSession(context, sid)
                        if (sessions.size <= 1) {
                            onNavigateBack()
                        }
                    }) {
                        Text(if (Strings.isZh) "关闭标签" else "Close Tab", color = theme.textMuted)
                    }
                    TextButton(onClick = { showDisconnectDialog = false }) {
                        Text(if (Strings.isZh) "取消" else "Cancel", color = theme.textSecondary)
                    }
                }
            },
            containerColor = theme.surfaceContainerLow
        )
    }
}
