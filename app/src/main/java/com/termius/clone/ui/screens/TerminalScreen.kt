package com.termius.clone.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
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
import com.termius.clone.terminal.session.ConnectionEvent
import com.termius.clone.terminal.session.ConnectionEventType
import com.termius.clone.terminal.session.SessionManager
import com.termius.clone.terminal.session.SessionState
import com.termius.clone.terminal.session.SshSession
import com.termius.clone.ui.components.TerminalAccessoryBar
import com.termius.clone.ui.components.TerminalInputMode
import com.termius.clone.ui.components.TerminalView
import com.termius.clone.ui.theme.*
import com.termius.clone.util.Strings
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun TerminalScreen(
    onNavigateBack: () -> Unit,
    onNavigateToAi: () -> Unit = {}
) {
    // 拦截系统返回手势（如屏幕边缘侧滑返回、物理返回键等），平滑退回到主页而不是直接将应用最小化
    BackHandler {
        onNavigateBack()
    }

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
    var eventLogSession by remember { mutableStateOf<SshSession?>(null) }

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
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(if (isLandscape) Modifier else Modifier.statusBarsPadding())
                ) {
                    // Header Bar with Session Tabs (Clean TermX Mobile Style, compact in landscape)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(if (isLandscape) 34.dp else 46.dp)
                            .padding(
                                horizontal = 4.dp,
                                vertical = if (isLandscape) 0.dp else 4.dp
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
                                val connectionEvent by session.connectionEvent.collectAsState()
                                val isRecovering = connectionEvent?.type == ConnectionEventType.RETRY_SCHEDULED ||
                                        connectionEvent?.type == ConnectionEventType.RECONNECTING

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
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .combinedClickable(
                                            onClick = {
                                                if (isSelected && (sessionState == SessionState.DISCONNECTED || sessionState == SessionState.ERROR)) {
                                                    session.reconnect()
                                                } else {
                                                    SessionManager.selectSession(session.id)
                                                }
                                            },
                                            onLongClick = { eventLogSession = session }
                                        )
                                ) {
                                    Row(
                                        modifier = Modifier.padding(
                                            horizontal = if (isLandscape) 8.dp else 10.dp,
                                            vertical = if (isLandscape) 3.dp else 6.dp
                                        ),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                                    ) {
                                        // Status dot（四态：已连接 / 重连中脉冲 / 失败 / 用户主动断开）
                                        val isManualDisconnect = connectionEvent?.type == ConnectionEventType.MANUAL_DISCONNECT
                                        val dotColor = when {
                                            sessionState == SessionState.CONNECTED -> StatusGreen
                                            sessionState == SessionState.CONNECTING ||
                                                    sessionState == SessionState.AUTHENTICATING ||
                                                    isRecovering -> StatusAmber
                                            isManualDisconnect -> StatusMuted
                                            else -> StatusRed
                                        }
                                        val pulseTransition = rememberInfiniteTransition(label = "tabStatePulse")
                                        val pulseAlpha by pulseTransition.animateFloat(
                                            initialValue = 0.35f,
                                            targetValue = 1f,
                                            animationSpec = infiniteRepeatable(
                                                animation = tween(720),
                                                repeatMode = RepeatMode.Reverse
                                            ),
                                            label = "tabStatePulseAlpha"
                                        )
                                        Box(
                                            modifier = Modifier
                                                .size(if (isLandscape) 6.dp else 7.dp)
                                                .alpha(if (isRecovering) pulseAlpha else 1f)
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

                                        // 重连进度：就地带一个微型计数，不占额外行高、不写正文
                                        if (isRecovering) {
                                            val attempt = connectionEvent?.attempt ?: 0
                                            val maxAttempts = connectionEvent?.maxAttempts ?: 0
                                            if (attempt > 0 && maxAttempts > 0) {
                                                Text(
                                                    text = "$attempt/$maxAttempts",
                                                    color = StatusAmber,
                                                    fontSize = if (isLandscape) 9.sp else 9.5.sp,
                                                    fontFamily = FontFamily.Monospace,
                                                    fontWeight = FontWeight.SemiBold,
                                                    maxLines = 1
                                                )
                                            }
                                        }

                                        IconButton(
                                            onClick = {
                                                val isLast = sessions.size <= 1
                                                SessionManager.closeSession(context, session.id)
                                                if (isLast) {
                                                    onNavigateBack()
                                                }
                                            },
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

                        if (activeSession != null) {
                            val activeState by activeSession.sessionState.collectAsState()
                            val isConnected = activeState == SessionState.CONNECTED ||
                                    activeState == SessionState.CONNECTING ||
                                    activeState == SessionState.AUTHENTICATING

                            IconButton(
                                onClick = {
                                    if (isConnected) {
                                        showDisconnectDialog = true
                                    } else {
                                        val isLast = sessions.size <= 1
                                        SessionManager.closeSession(context, activeSession.id)
                                        if (isLast) {
                                            onNavigateBack()
                                        }
                                    }
                                },
                                modifier = if (isLandscape) Modifier.size(32.dp) else Modifier
                            ) {
                                Icon(
                                    Icons.Default.PowerSettingsNew,
                                    contentDescription = if (isConnected) (if (Strings.isZh) "断开连接" else "Disconnect") else (if (Strings.isZh) "关闭标签" else "Close Tab"),
                                    tint = if (isConnected) Color(0xFFEF4444).copy(alpha = 0.88f) else theme.textMuted,
                                    modifier = if (isLandscape) Modifier.size(18.dp) else Modifier.size(24.dp)
                                )
                            }
                        }

                        // AI SRE 运维助手入口按钮 (璀璨星辉)
                        IconButton(
                            onClick = onNavigateToAi,
                            modifier = if (isLandscape) Modifier.size(32.dp) else Modifier.size(36.dp)
                        ) {
                            Icon(
                                Icons.Default.AutoAwesome,
                                contentDescription = "AI 智能运维助手",
                                tint = theme.primary,
                                modifier = if (isLandscape) Modifier.size(18.dp) else Modifier.size(22.dp)
                            )
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

                // 连接状态一律在 UI 层就地呈现（重连进度 / 已恢复 / 失败重试），PTY 正文零写入
                ConnectionStatusChip(
                    session = activeSession,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 10.dp)
                )

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

    eventLogSession?.let { session ->
        ConnectionEventLogDialog(session = session, onDismiss = { eventLogSession = null })
    }
}

// ==========================================
// 连接状态 UI（PTY 正文零写入的呈现层）
// ==========================================

private val StatusGreen = Color(0xFF22C55E)
private val StatusAmber = Color(0xFFF59E0B)
private val StatusRed = Color(0xFFEF4444)
private val StatusMuted = Color(0xFF94A3B8)

/** 状态胶囊的渲染规格 */
private data class StatusChipSpec(
    val text: String,
    val accent: Color,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val clickable: Boolean
)

/**
 * 将连接事件翻译成一张极简状态胶囊。
 *
 * 原则：进度类提示常驻（它表达"正在发生"，必须与真实状态同生命周期），
 * 成功类短暂停留后淡出，失败/断开类常驻且可点击重试。
 */
private fun buildStatusChipSpec(
    event: ConnectionEvent?,
    sessionState: SessionState,
    countdown: Int,
    showRecovered: Boolean
): StatusChipSpec? {
    val zh = Strings.isZh

    if (showRecovered) {
        return StatusChipSpec(
            text = if (zh) "已恢复" else "Recovered",
            accent = StatusGreen,
            icon = Icons.Default.CheckCircle,
            clickable = false
        )
    }

    if (event != null && (event.type == ConnectionEventType.RETRY_SCHEDULED || event.type == ConnectionEventType.RECONNECTING)) {
        val progress = if (event.attempt > 0 && event.maxAttempts > 0) " ${event.attempt}/${event.maxAttempts}" else ""
        val countdownText = if (event.type == ConnectionEventType.RETRY_SCHEDULED && countdown > 0) " · ${countdown}s" else ""
        val text = if (event.isAuthFailure) {
            if (zh) "认证失败$progress$countdownText" else "Auth failed$progress$countdownText"
        } else {
            if (zh) "重连中$progress$countdownText" else "Reconnecting$progress$countdownText"
        }
        return StatusChipSpec(
            text = text,
            accent = if (event.isAuthFailure) StatusRed else StatusAmber,
            icon = Icons.Default.Refresh,
            clickable = false
        )
    }

    if (event != null && event.type == ConnectionEventType.RECONNECT_EXHAUSTED) {
        val text = if (event.isAuthFailure) {
            if (zh) "认证失败 · 点击重试" else "Auth failed · Tap to retry"
        } else {
            if (zh) "重连失败 ${event.attempt}/${event.maxAttempts} · 点击重试" else "Reconnect failed · Tap to retry"
        }
        return StatusChipSpec(text, StatusRed, Icons.Default.Refresh, clickable = true)
    }

    if (sessionState == SessionState.DISCONNECTED || sessionState == SessionState.ERROR) {
        val manual = event?.type == ConnectionEventType.MANUAL_DISCONNECT
        val text = when (event?.type) {
            ConnectionEventType.MANUAL_DISCONNECT -> if (zh) "已断开 · 点击重连" else "Disconnected · Tap to reconnect"
            ConnectionEventType.DISCONNECTED -> if (zh) "连接中断 · 点击重连" else "Connection lost · Tap to reconnect"
            ConnectionEventType.ERROR -> if (zh) "连接失败 · 点击重试" else "Connection failed · Tap to retry"
            else -> if (zh) "已断开 · 点击重连" else "Disconnected · Tap to reconnect"
        }
        return StatusChipSpec(
            text = text,
            accent = if (manual) StatusMuted else StatusRed,
            icon = Icons.Default.Refresh,
            clickable = true
        )
    }

    return null
}

/**
 * 终端视口顶部的连接状态胶囊：一次性小标签，不向 PTY 写任何一个字节。
 */
@Composable
private fun ConnectionStatusChip(session: SshSession, modifier: Modifier = Modifier) {
    val theme = LocalAppTheme.current
    val event by session.connectionEvent.collectAsState()
    val sessionState by session.sessionState.collectAsState()

    var countdown by remember(session.id) { mutableIntStateOf(0) }
    var showRecovered by remember(session.id) { mutableStateOf(false) }

    LaunchedEffect(event?.timestamp, event?.type) {
        val current = event ?: return@LaunchedEffect
        if (current.type == ConnectionEventType.RETRY_SCHEDULED && current.retryDelaySec > 0) {
            countdown = current.retryDelaySec
            while (countdown > 0) {
                delay(1_000L)
                countdown -= 1
            }
        }
    }

    LaunchedEffect(event?.timestamp, event?.type) {
        val current = event ?: return@LaunchedEffect
        if (current.type == ConnectionEventType.RECONNECT_SUCCESS) {
            showRecovered = true
            delay(1_500L)
            showRecovered = false
        }
    }

    val spec = buildStatusChipSpec(event, sessionState, countdown, showRecovered)
    // 保留最后一张非空规格，让退场动画有内容可淡出
    val retained = remember { mutableStateOf(spec) }
    LaunchedEffect(spec) { if (spec != null) retained.value = spec }

    AnimatedVisibility(
        visible = spec != null,
        enter = fadeIn(animationSpec = tween(160)),
        exit = fadeOut(animationSpec = tween(220)),
        modifier = modifier
    ) {
        val shown = spec ?: retained.value
        if (shown != null) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = theme.surfaceContainerHigh.copy(alpha = 0.95f),
                border = androidx.compose.foundation.BorderStroke(1.dp, shown.accent.copy(alpha = 0.45f)),
                shadowElevation = 4.dp,
                modifier = if (shown.clickable) {
                    Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .clickable { session.reconnect() }
                } else {
                    Modifier
                }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(shown.icon, contentDescription = null, tint = shown.accent, modifier = Modifier.size(15.dp))
                    Text(
                        text = shown.text,
                        color = theme.textPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }
        }
    }
}

private fun connectionEventTypeLabel(type: ConnectionEventType): String {
    val zh = Strings.isZh
    return when (type) {
        ConnectionEventType.CONNECTING -> if (zh) "连接中" else "Connecting"
        ConnectionEventType.RECONNECTING -> if (zh) "重连中" else "Reconnecting"
        ConnectionEventType.CONNECTED -> if (zh) "已连接" else "Connected"
        ConnectionEventType.RECONNECT_SUCCESS -> if (zh) "重连成功" else "Reconnected"
        ConnectionEventType.RETRY_SCHEDULED -> if (zh) "等待重试" else "Retry scheduled"
        ConnectionEventType.RECONNECT_EXHAUSTED -> if (zh) "重连中止" else "Reconnect stopped"
        ConnectionEventType.ERROR -> if (zh) "失败" else "Failed"
        ConnectionEventType.DISCONNECTED -> if (zh) "断开" else "Disconnected"
        ConnectionEventType.MANUAL_DISCONNECT -> if (zh) "手动断开" else "Manual disconnect"
    }
}

/**
 * 连接事件记录：状态提示的"历史可查"出口（正文里不再留任何痕迹）。
 */
@Composable
private fun ConnectionEventLogDialog(session: SshSession, onDismiss: () -> Unit) {
    val theme = LocalAppTheme.current
    val context = LocalContext.current
    val version by session.eventLog.version.collectAsState()
    val events = remember(version) { session.eventLog.snapshot().asReversed() }
    val timeFormat = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }

    fun formatLine(event: ConnectionEvent): String {
        val time = timeFormat.format(Date(event.timestamp))
        val detail = if (event.message.isNotBlank()) "  ${event.message}" else ""
        return "$time  ${connectionEventTypeLabel(event.type)}$detail"
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (Strings.isZh) "连接事件记录" else "Connection Events",
                fontWeight = FontWeight.Bold,
                color = theme.textPrimary
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "${session.host.username}@${session.host.hostname}:${session.host.port}",
                    color = theme.textMuted,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1
                )
                if (events.isEmpty()) {
                    Text(
                        text = if (Strings.isZh) "暂无连接事件" else "No connection events yet",
                        color = theme.textMuted,
                        fontSize = 13.sp
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.heightIn(max = 340.dp),
                        verticalArrangement = Arrangement.spacedBy(7.dp)
                    ) {
                        items(events, key = { "${it.timestamp}-${it.type.name}-${it.message.hashCode()}" }) { item ->
                            val accent = when (item.type) {
                                ConnectionEventType.RECONNECT_SUCCESS, ConnectionEventType.CONNECTED -> StatusGreen
                                ConnectionEventType.RETRY_SCHEDULED, ConnectionEventType.RECONNECTING,
                                ConnectionEventType.CONNECTING -> if (item.isAuthFailure) StatusRed else StatusAmber
                                ConnectionEventType.MANUAL_DISCONNECT -> StatusMuted
                                else -> StatusRed
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.Top,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .padding(top = 5.dp)
                                        .size(6.dp)
                                        .background(accent, CircleShape)
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text(
                                            text = timeFormat.format(Date(item.timestamp)),
                                            color = theme.textMuted,
                                            fontSize = 11.sp,
                                            fontFamily = FontFamily.Monospace
                                        )
                                        Text(
                                            text = connectionEventTypeLabel(item.type),
                                            color = accent,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                    if (item.message.isNotBlank()) {
                                        Text(
                                            text = item.message,
                                            color = theme.textSecondary,
                                            fontSize = 12.sp,
                                            lineHeight = 16.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(Strings.close, color = theme.primary)
            }
        },
        dismissButton = {
            if (events.isNotEmpty()) {
                TextButton(onClick = {
                    val dump = session.eventLog.snapshot().joinToString("\n") { formatLine(it) }
                    try {
                        (context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager)
                            ?.setPrimaryClip(ClipData.newPlainText("TermX connection events", dump))
                    } catch (_: Throwable) {
                        // 剪贴板不可用时静默失败，不打断诊断流程
                    }
                }) {
                    Text(Strings.copy, color = theme.textSecondary)
                }
            }
        },
        containerColor = theme.surfaceContainerLow
    )
}
