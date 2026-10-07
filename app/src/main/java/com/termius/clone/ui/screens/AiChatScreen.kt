package com.termius.clone.ui.screens

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import android.content.res.Configuration
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.termius.clone.ai.AgentExecutionManager
import com.termius.clone.ai.TermXAgentClient
import com.termius.clone.ai.TermXAgentToolRegistry
import com.termius.clone.data.local.AiConfigManager
import com.termius.clone.data.local.AppDatabase
import com.termius.clone.data.model.AiChatMessage
import com.termius.clone.data.model.AiChatSession
import com.termius.clone.data.model.DangerousActionRequest
import com.termius.clone.terminal.session.SessionManager
import com.termius.clone.ui.components.AiActionOrbitStatusCard
import com.termius.clone.ui.components.AiThinkingCard
import com.termius.clone.ui.components.DangerousActionApprovalCard
import com.termius.clone.ui.components.MarkdownRenderer
import com.termius.clone.ui.theme.LocalAppTheme
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * 沉浸式 SRE 运维智能体工作台 (TermX Mobile AI SRE Studio)
 * 参考 mqtt-assistant-app 与 ChatGPT Mobile 现代工业质感
 */
@Composable
fun AiChatScreen(
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val theme = LocalAppTheme.current
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val scope = rememberCoroutineScope()
    val db = remember { AppDatabase.getDatabase(context) }
    val configManager = remember { AiConfigManager(context) }

    var aiConfig by remember { mutableStateOf(configManager.loadConfig()) }
    var showSettingsDialog by remember { mutableStateOf(false) }

    // 多会话状态
    var sessions by remember { mutableStateOf(configManager.loadSessions()) }
    var currentSessionId by remember { mutableStateOf(configManager.getCurrentSessionId()) }
    val currentSession = sessions.find { it.id == currentSessionId } ?: sessions.firstOrNull()

    var messages by remember { mutableStateOf(configManager.loadMessages(currentSessionId)) }
    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    // 弹窗与下拉菜单
    var showSessionMenu by remember { mutableStateOf(false) }
    var showMoreMenu by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var renameSessionTitle by remember { mutableStateOf("") }
    var sessionToDelete by remember { mutableStateOf<AiChatSession?>(null) }
    var showClearConfirmDialog by remember { mutableStateOf(false) }

    // 全局单例后台 Agent 执行状态 (与 UI 生命周期完全解耦，切出应用/息屏均持续运行)
    val executionState by AgentExecutionManager.state.collectAsState()
    val isCurrentSessionActive = (executionState.sessionId == currentSessionId)
    val isGenerating = executionState.isGenerating && isCurrentSessionActive
    val currentChunkText = if (isCurrentSessionActive) executionState.currentChunkText else ""
    val currentReasoningText = if (isCurrentSessionActive) executionState.currentReasoningText else ""
    val currentActionText = if (isCurrentSessionActive) executionState.currentActionText else ""
    val pendingApprovalRequest = if (isCurrentSessionActive) executionState.pendingApprovalRequest else null

    // 当后台执行完成时自动从数据库重载当前会话消息
    LaunchedEffect(executionState.isGenerating) {
        if (!executionState.isGenerating && executionState.sessionId == currentSessionId) {
            messages = configManager.loadMessages(currentSessionId)
            sessions = configManager.loadSessions()
        }
    }

    // 附件状态 (支持上传 log, txt, conf, json 等)
    var attachedFile by remember { mutableStateOf<AttachedFileInfo?>(null) }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            val fileInfo = getFileInfoFromUri(context, uri)
            if (fileInfo != null) {
                attachedFile = fileInfo
            }
        }
    }

    val launchAttachmentPicker = remember {
        {
            try {
                filePickerLauncher.launch(arrayOf("*/*"))
            } catch (_: Exception) {}
        }
    }

    // 活跃会话与当前主机
    val activeSession = SessionManager.currentSession
    val targetHostTitle = activeSession?.host?.let { "${it.username}@${it.label}" } ?: "自动感知主机"
    val isConnected = activeSession?.isConnected == true

    // 切换会话
    val switchSession: (String) -> Unit = { targetId ->
        currentSessionId = targetId
        configManager.setCurrentSessionId(targetId)
        messages = configManager.loadMessages(targetId)
    }

    // 新建会话
    val createSession: () -> Unit = {
        val created = configManager.createNewSession()
        sessions = configManager.loadSessions()
        switchSession(created.id)
    }

    // 监测是否处于列表最底部附近 (容差 2 个 items，避免打扰用户手动上翻查阅历史)
    val isAtBottom by remember {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            val total = layoutInfo.totalItemsCount
            if (total <= 1) true
            else {
                val lastVisible = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                lastVisible >= total - 2
            }
        }
    }

    // 1. 流式输出进行时：若用户在底部，瞬时跟随贴底推进
    LaunchedEffect(currentChunkText.length, currentActionText, isGenerating) {
        val total = listState.layoutInfo.totalItemsCount
        if (total > 0 && isAtBottom && isGenerating) {
            listState.scrollToItem(total - 1)
        }
    }

    // 2. 发送消息或生成完成瞬间：预留微弱排版测量延迟后，顺滑动画平移到最底端 (彻底解决用户必须手动下滑的痛点)
    LaunchedEffect(messages.size, isGenerating) {
        if (messages.isNotEmpty()) {
            kotlinx.coroutines.delay(100L)
            val total = listState.layoutInfo.totalItemsCount
            if (total > 0) {
                listState.animateScrollToItem(total - 1)
            }
        }
    }

    val sendMessage: (String) -> Unit = { rawPrompt ->
        val prompt = rawPrompt.trim()
        val curAttached = attachedFile
        val canProceed = (prompt.isNotBlank() || curAttached != null) && !isGenerating && pendingApprovalRequest == null

        if (canProceed) {
            // 如果附带了日志/配置文件，读取前 8000 字符文本一并注入
            val fullUserContent = if (curAttached != null) {
                val fileContentText = readFileContentText(context, curAttached.uri, maxChars = 8000)
                "【附加文件: ${curAttached.name}】\n```${curAttached.extension}\n$fileContentText\n```\n${prompt.ifBlank { "请帮我深入分析这份日志/配置文件的内容，排查异常或给出建议。" }}"
            } else {
                prompt
            }

            val userMsg = AiChatMessage(role = "user", content = fullUserContent)
            val updatedList = messages + userMsg
            messages = updatedList
            inputText = ""
            attachedFile = null
            configManager.saveMessages(currentSessionId, updatedList)
            sessions = configManager.loadSessions() // 刷新标题

            // 委托给 AgentExecutionManager 全局后台守护执行 (脱离 UI 生命周期，长任务切后台绝不中断)
            AgentExecutionManager.executePrompt(
                context = context,
                sessionId = currentSessionId,
                updatedMessages = updatedList,
                onSessionUpdated = {
                    if (currentSessionId == executionState.sessionId) {
                        messages = configManager.loadMessages(currentSessionId)
                        sessions = configManager.loadSessions()
                    }
                }
            )
        }
    }

    // 重命名弹窗
    if (showRenameDialog) {
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = {
                Text(
                    text = "重命名会话",
                    style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Bold, color = theme.textPrimary)
                )
            },
            text = {
                BasicTextField(
                    value = renameSessionTitle,
                    onValueChange = { renameSessionTitle = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(theme.surfaceContainer)
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    textStyle = TextStyle(fontSize = 14.sp, color = theme.textPrimary),
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (renameSessionTitle.isNotBlank()) {
                            configManager.renameSession(currentSessionId, renameSessionTitle.trim())
                            sessions = configManager.loadSessions()
                        }
                        showRenameDialog = false
                    }
                ) {
                    Text("确定", color = theme.primary, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false }) {
                    Text("取消", color = theme.textMuted)
                }
            },
            containerColor = theme.surfaceContainerLow,
            shape = RoundedCornerShape(16.dp)
        )
    }

    // 清空会话确认
    if (showClearConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showClearConfirmDialog = false },
            title = {
                Text(
                    text = "清空对话记录",
                    style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Bold, color = theme.textPrimary)
                )
            },
            text = {
                Text(
                    text = "确认清空当前会话的所有排障记录吗？此操作无法撤销。",
                    style = TextStyle(fontSize = 13.5.sp, color = theme.textSecondary)
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearConfirmDialog = false
                        messages = emptyList()
                        configManager.clearMessages(currentSessionId)
                    }
                ) {
                    Text("清空", color = Color(0xFFDC2626), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirmDialog = false }) {
                    Text("取消", color = theme.textMuted)
                }
            },
            containerColor = theme.surfaceContainerLow,
            shape = RoundedCornerShape(16.dp)
        )
    }

    // 删除会话确认
    if (sessionToDelete != null) {
        AlertDialog(
            onDismissRequest = { sessionToDelete = null },
            title = {
                Text(
                    text = "删除会话确认",
                    style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Bold, color = theme.textPrimary)
                )
            },
            text = {
                Text(
                    text = "确认删除会话「${sessionToDelete?.title}」？删除后该会话历史将永久移除。",
                    style = TextStyle(fontSize = 13.5.sp, color = theme.textSecondary, lineHeight = 19.sp)
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val toDelId = sessionToDelete?.id
                        sessionToDelete = null
                        if (toDelId != null) {
                            val nextId = configManager.deleteSession(toDelId)
                            sessions = configManager.loadSessions()
                            switchSession(nextId)
                        }
                    }
                ) {
                    Text("删除", color = Color(0xFFDC2626), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { sessionToDelete = null }) {
                    Text("取消", color = theme.textMuted)
                }
            },
            containerColor = theme.surfaceContainerLow,
            shape = RoundedCornerShape(16.dp)
        )
    }

    // 整个屏幕容器：使用 imePadding() 并在底栏 exclude(ime) 解决绝对贴底问题
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(theme.background)
            .imePadding()
    ) {
        // ==========================================
        // 1. ChatGPT-Style TopBar (会话下拉切换与菜单)
        // ==========================================
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = theme.surfaceContainerLow,
            shadowElevation = if (theme.isDark) 0.dp else 1.dp
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .height(52.dp)
                        .padding(horizontal = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Left: 返回按钮
                    IconButton(onClick = onNavigateBack, modifier = Modifier.size(38.dp)) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回",
                            tint = theme.textPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // Center: 会话标题与切换下拉
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { showSessionMenu = true }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = currentSession?.title?.ifBlank { "新会话" } ?: "新会话",
                                style = TextStyle(
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = theme.textPrimary
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.widthIn(max = if (isLandscape) 360.dp else 170.dp)
                            )
                            Spacer(modifier = Modifier.width(2.dp))
                            Icon(
                                imageVector = if (showSessionMenu) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = "切换会话",
                                tint = theme.textMuted,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        // Sessions Dropdown Menu
                        DropdownMenu(
                            expanded = showSessionMenu,
                            onDismissRequest = { showSessionMenu = false },
                            modifier = Modifier
                                .widthIn(min = 240.dp, max = 300.dp)
                                .background(theme.surfaceContainerLow)
                        ) {
                            Text(
                                text = "历史会话",
                                style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Bold, color = theme.textMuted),
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                            )
                            HorizontalDivider(color = theme.outline.copy(alpha = 0.15f), thickness = 0.5.dp)

                            sessions.forEach { s ->
                                val isSelected = s.id == currentSessionId
                                DropdownMenuItem(
                                    text = {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                text = s.title,
                                                style = TextStyle(
                                                    fontSize = 13.5.sp,
                                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                    color = if (isSelected) theme.primary else theme.textPrimary
                                                ),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.weight(1f)
                                            )
                                            if (sessions.size > 1) {
                                                IconButton(
                                                    onClick = { sessionToDelete = s },
                                                    modifier = Modifier.size(24.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Close,
                                                        contentDescription = "删除",
                                                        tint = theme.textMuted,
                                                        modifier = Modifier.size(13.dp)
                                                    )
                                                }
                                            }
                                        }
                                    },
                                    onClick = {
                                        switchSession(s.id)
                                        showSessionMenu = false
                                    },
                                    modifier = if (isSelected) Modifier.background(theme.surfaceContainer.copy(alpha = 0.6f)) else Modifier
                                )
                            }

                            HorizontalDivider(color = theme.outline.copy(alpha = 0.15f), thickness = 0.5.dp)
                            DropdownMenuItem(
                                text = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.Add,
                                            contentDescription = null,
                                            tint = theme.primary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "新建会话",
                                            style = TextStyle(fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, color = theme.primary)
                                        )
                                    }
                                },
                                onClick = {
                                    createSession()
                                    showSessionMenu = false
                                }
                            )
                        }
                    }

                    // Right: 新建按钮 (+) 与 更多菜单 (···)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = createSession, modifier = Modifier.size(36.dp)) {
                            Icon(Icons.Default.Add, contentDescription = "新建会话", tint = theme.textPrimary, modifier = Modifier.size(20.dp))
                        }

                        Box {
                            IconButton(onClick = { showMoreMenu = true }, modifier = Modifier.size(36.dp)) {
                                Icon(Icons.Default.MoreVert, contentDescription = "更多", tint = theme.textPrimary, modifier = Modifier.size(20.dp))
                            }

                            DropdownMenu(
                                expanded = showMoreMenu,
                                onDismissRequest = { showMoreMenu = false },
                                modifier = Modifier.background(theme.surfaceContainerLow)
                            ) {
                                DropdownMenuItem(
                                    text = { Text("重命名当前会话", fontSize = 13.sp, color = theme.textPrimary) },
                                    leadingIcon = {
                                        Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(17.dp), tint = theme.textPrimary)
                                    },
                                    onClick = {
                                        showMoreMenu = false
                                        renameSessionTitle = currentSession?.title ?: ""
                                        showRenameDialog = true
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("清空当前消息", fontSize = 13.sp, color = theme.textPrimary) },
                                    leadingIcon = {
                                        Icon(Icons.Default.DeleteSweep, contentDescription = null, modifier = Modifier.size(17.dp), tint = theme.textPrimary)
                                    },
                                    onClick = {
                                        showMoreMenu = false
                                        showClearConfirmDialog = true
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("模型与接口设置", fontSize = 13.sp, color = theme.textPrimary) },
                                    leadingIcon = {
                                        Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(17.dp), tint = theme.textPrimary)
                                    },
                                    onClick = {
                                        showMoreMenu = false
                                        showSettingsDialog = true
                                    }
                                )
                                if (sessions.size > 1) {
                                    HorizontalDivider(color = theme.outline.copy(alpha = 0.15f), thickness = 0.5.dp)
                                    DropdownMenuItem(
                                        text = { Text("删除此会话", fontSize = 13.sp, color = Color(0xFFDC2626)) },
                                        leadingIcon = {
                                            Icon(Icons.Default.DeleteOutline, contentDescription = null, modifier = Modifier.size(17.dp), tint = Color(0xFFDC2626))
                                        },
                                        onClick = {
                                            showMoreMenu = false
                                            sessionToDelete = currentSession
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
                HorizontalDivider(color = theme.outline.copy(alpha = 0.15f), thickness = 0.5.dp)
            }
        }

        // ==========================================
        // 2. 聊天消息流区域
        // ==========================================
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            if (messages.isEmpty() && !isGenerating && pendingApprovalRequest == null) {
                // 空状态引导视图
                AiEmptyWelcomeView(
                    targetHostTitle = targetHostTitle,
                    isConnected = isConnected,
                    onPromptSelected = { sendMessage(it) }
                )
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 14.dp),
                    contentPadding = PaddingValues(vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    items(messages, key = { it.id }) { msg ->
                        AiChatMessageItem(
                            message = msg,
                            onRetry = { sendMessage(it) }
                        )
                    }

                    // 正在生成中态
                    if (isGenerating) {
                        item {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                // 动作胶囊卡片
                                if (currentActionText.isNotBlank()) {
                                    AiActionOrbitStatusCard(statusText = currentActionText)
                                }

                                // 流式思考链
                                if (currentReasoningText.isNotBlank()) {
                                    AiThinkingCard(reasoningContent = currentReasoningText)
                                }

                                // 流式正文卡片
                                if (currentChunkText.isNotBlank()) {
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
                                        // Agent 图标
                                        Box(
                                            modifier = Modifier
                                                .size(28.dp)
                                                .clip(CircleShape)
                                                .background(theme.primary),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                                        }
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Surface(
                                            shape = RoundedCornerShape(4.dp, 16.dp, 16.dp, 16.dp),
                                            color = theme.surfaceContainerLow,
                                            border = BorderStroke(0.6.dp, theme.outline.copy(alpha = 0.2f)),
                                            modifier = Modifier.weight(1f, fill = false)
                                        ) {
                                            Box(modifier = Modifier.padding(14.dp)) {
                                                SelectionContainer {
                                                    MarkdownRenderer(content = currentChunkText, isUser = false)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // 人工审批卡片 (挂起等待审批)
                    pendingApprovalRequest?.let { req ->
                        item {
                            DangerousActionApprovalCard(
                                request = req,
                                onApprove = { AgentExecutionManager.approveDangerousAction(true) },
                                onReject = { AgentExecutionManager.approveDangerousAction(false) }
                            )
                        }
                    }

                    item {
                        Spacer(modifier = Modifier.height(6.dp))
                    }
                }
            }

            // 悬浮「↓ 回到底部」圆形按钮 (参考 OpenAI ChatGPT 风格：居中悬浮圆圈 + 向下箭头)
            androidx.compose.animation.AnimatedVisibility(
                visible = !isAtBottom && messages.isNotEmpty(),
                enter = fadeIn() + scaleIn(initialScale = 0.8f),
                exit = fadeOut() + scaleOut(targetScale = 0.8f),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 12.dp)
            ) {
                Surface(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .clickable {
                            scope.launch {
                                val total = listState.layoutInfo.totalItemsCount
                                if (total > 0) {
                                    listState.animateScrollToItem(total - 1)
                                }
                            }
                        },
                    shape = CircleShape,
                    color = theme.surfaceContainerHigh.copy(alpha = 0.95f),
                    border = BorderStroke(1.dp, theme.outline.copy(alpha = 0.30f)),
                    shadowElevation = 5.dp
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowDownward,
                            contentDescription = "回到底部",
                            tint = theme.textPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }

        // ==========================================
        // 3. 底部输入与操作区 (彻底解决贴底与按键杂乱)
        // ==========================================
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars.exclude(WindowInsets.ime)),
            color = theme.surfaceContainerLow,
            shadowElevation = 4.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                // 附加文件预览胶囊
                attachedFile?.let { file ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 6.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(theme.surfaceContainer)
                            .border(0.6.dp, theme.outline.copy(alpha = 0.25f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Description,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = theme.primary
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = file.name,
                            style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = theme.textPrimary),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = formatFileSize(file.sizeBytes),
                            style = TextStyle(fontSize = 10.sp, color = theme.textMuted, fontFamily = FontFamily.Monospace)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "移除附件",
                            tint = theme.textMuted,
                            modifier = Modifier
                                .size(14.dp)
                                .clickable { attachedFile = null }
                        )
                    }
                }

                // 预设运维操作胶囊 (横向排列)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(bottom = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(
                        "📋 带入终端屏幕" to "请读取当前终端屏幕最后输出并分析刚才的操作与报错：",
                        "🔍 系统全面体检" to "请帮我对当前主机进行全面的系统体检（CPU占用、内存分布、磁盘根分区空间、僵尸进程与高负载原因）",
                        "🐳 检查 Docker 状态" to "请帮我检查当前主机的 Docker 容器运行状态，看是否有异常退出或重启的容器",
                        "🌐 排查端口与服务" to "请帮我查看当前主机正在监听的网络端口及所属进程 (ss -tulpn)"
                    ).forEach { (label, prompt) ->
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = theme.surfaceContainer,
                            border = BorderStroke(0.6.dp, theme.outline.copy(alpha = 0.2f)),
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .clickable(enabled = !isGenerating && pendingApprovalRequest == null) {
                                    if (label.startsWith("📋")) {
                                        inputText = prompt
                                    } else {
                                        sendMessage(prompt)
                                    }
                                }
                        ) {
                            Text(
                                text = label,
                                fontSize = 11.sp,
                                color = theme.textSecondary,
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.5.dp)
                            )
                        }
                    }
                }

                // 核心输入卡片 (内嵌附件按钮、输入框、发送/停止无缝切换)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(22.dp))
                        .background(theme.surfaceContainer)
                        .padding(horizontal = 6.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // [+] 添加日志/配置文件
                    IconButton(
                        onClick = launchAttachmentPicker,
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "添加日志或文件",
                            tint = theme.textPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(4.dp))

                    BasicTextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        modifier = Modifier.weight(1f),
                        textStyle = TextStyle(
                            fontSize = 13.5.sp,
                            color = theme.textPrimary,
                            lineHeight = 18.sp
                        ),
                        maxLines = 4,
                        cursorBrush = SolidColor(theme.primary),
                        decorationBox = { inner ->
                            if (inputText.isEmpty()) {
                                Text(
                                    text = if (attachedFile != null) {
                                        "输入对「${attachedFile?.name}」的分析要求..."
                                    } else if (isGenerating) {
                                        "智能体执行中..."
                                    } else if (pendingApprovalRequest != null) {
                                        "请在上方卡片中确认高危操作..."
                                    } else {
                                        "输入运维需求，例如：帮我看看为什么502..."
                                    },
                                    style = TextStyle(fontSize = 13.sp, color = theme.textMuted),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            inner()
                        }
                    )

                    Spacer(modifier = Modifier.width(6.dp))

                    val canSend = (inputText.isNotBlank() || attachedFile != null) && pendingApprovalRequest == null
                    if (canSend) {
                        // 发送按钮
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(theme.primary)
                                .clickable { sendMessage(inputText) },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Send,
                                contentDescription = "发送",
                                tint = Color.White,
                                modifier = Modifier.size(15.dp)
                            )
                        }
                    } else if (isGenerating) {
                        // 停止响应按钮 (优雅保留已生成内容，防止历史断裂)
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFDC2626))
                                .clickable {
                                    AgentExecutionManager.stopExecution()
                                    val interruptedText = currentChunkText.trim()
                                    val savedContent = if (interruptedText.isNotBlank()) {
                                        "$interruptedText\n\n*(本次生成已由用户手动停止)*"
                                    } else {
                                        "*(本次操作已由用户手动停止)*"
                                    }
                                    val assistantMsg = AiChatMessage(
                                        role = "assistant",
                                        content = savedContent,
                                        reasoningContent = currentReasoningText
                                    )
                                    val updated = messages + assistantMsg
                                    messages = updated
                                    configManager.saveMessages(currentSessionId, updated)
                                    sessions = configManager.loadSessions()
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Stop,
                                contentDescription = "停止响应",
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    } else {
                        // 禁用态
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(theme.surfaceContainerHigh.copy(alpha = 0.5f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Send,
                                contentDescription = "发送",
                                tint = theme.textMuted.copy(alpha = 0.6f),
                                modifier = Modifier.size(15.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    // 设置弹窗
    if (showSettingsDialog) {
        AiSettingsDialog(
            initialConfig = aiConfig,
            onDismiss = { showSettingsDialog = false },
            onSaveConfig = { newConfig ->
                aiConfig = newConfig
                configManager.saveConfig(newConfig)
            }
        )
    }
}

/**
 * 欢迎空状态视图
 */
@Composable
private fun AiEmptyWelcomeView(
    targetHostTitle: String,
    isConnected: Boolean,
    onPromptSelected: (String) -> Unit
) {
    val theme = LocalAppTheme.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(CircleShape)
                .background(theme.primary.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.AutoAwesome,
                contentDescription = null,
                tint = theme.primary,
                modifier = Modifier.size(26.dp)
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "有什么我可以帮您排查的？",
            style = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.Bold, color = theme.textPrimary, letterSpacing = (-0.2).sp)
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = "环境感知 · 自动化探针 · 审批保护 · 联网检索",
            style = TextStyle(fontSize = 11.5.sp, color = theme.textSecondary)
        )

        Spacer(modifier = Modifier.height(12.dp))

        // 当前主机态势微徽标
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(theme.surfaceContainer)
                .border(0.6.dp, theme.outline.copy(alpha = 0.2f), RoundedCornerShape(16.dp))
                .padding(horizontal = 10.dp, vertical = 4.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(if (isConnected) Color(0xFF10B981) else Color(0xFF9CA3AF))
            )
            Text(
                text = "目标: $targetHostTitle",
                style = TextStyle(fontSize = 11.sp, color = theme.textSecondary, fontFamily = FontFamily.Monospace)
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        // 四大核心运维引导卡片
        Column(
            modifier = Modifier.widthIn(max = 680.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf(
                Triple("🔍 系统全方位体检", "CPU负载、内存分布、根分区空间与僵尸进程", "请帮我对当前主机进行全面的系统体检（CPU占用、内存分布、磁盘根分区空间、僵尸进程与高负载原因）"),
                Triple("🐳 排查 Docker 容器异常", "扫描 Exited 容器并调取最近退出日志", "请帮我检查当前主机的 Docker 容器运行状态，看是否有异常退出或重启的容器并分析原因"),
                Triple("🌐 检查对外端口与防火墙", "查看监听端口、绑定进程与防火墙状态", "请帮我查看当前主机正在监听的网络端口及所属进程 (ss -tulpn)"),
                Triple("📋 读取当前终端并分析报错", "自动感知终端最后可见日志并给出修复步骤", "请读取当前终端屏幕最后输出，分析刚才命令执行失败的原因并给出修复方案")
            ).forEach { (title, subtitle, prompt) ->
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = theme.surfaceContainerLow,
                    border = BorderStroke(0.6.dp, theme.outline.copy(alpha = 0.25f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { onPromptSelected(prompt) }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = title, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, color = theme.textPrimary)
                            Text(text = subtitle, fontSize = 10.5.sp, color = theme.textMuted)
                        }
                        Icon(Icons.Default.ArrowForward, contentDescription = null, tint = theme.primary, modifier = Modifier.size(14.dp))
                    }
                }
            }
        }
    }
}

/**
 * 单条聊天消息组件 (带专属头像与复制按钮)
 */
@Composable
private fun AiChatMessageItem(
    message: AiChatMessage,
    onRetry: (String) -> Unit
) {
    val theme = LocalAppTheme.current
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val screenWidth = configuration.screenWidthDp.dp
    val isUser = message.role == "user"

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        if (!isUser) {
            // Agent 头像
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(theme.primary),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
            }
            Spacer(modifier = Modifier.width(8.dp))
        }

        Column(
            modifier = if (isUser) {
                // 用户提问消息：靠右展示，短文本紧凑包裹，长文本自适应最大宽度（横屏下不至于拉成细长单行）
                val maxUserWidth = if (isLandscape) {
                    (screenWidth * 0.70f).coerceIn(360.dp, 640.dp)
                } else {
                    (screenWidth * 0.85f).coerceAtLeast(280.dp)
                }
                Modifier
                    .weight(1f, fill = false)
                    .widthIn(max = maxUserWidth)
            } else {
                // AI 智能体回答：横竖屏均自适应展开！
                // 横屏下充分享受大屏宽幅，彻底舒展表格、长代码/命令与排障日志，消除右侧大面积黑屏空白
                Modifier.weight(1f, fill = false)
            },
            horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
        ) {
            // 思考过程 (折叠)
            if (!isUser && message.reasoningContent.isNotBlank()) {
                AiThinkingCard(reasoningContent = message.reasoningContent, modifier = Modifier.padding(bottom = 6.dp))
            }

            // 消息主体
            Surface(
                shape = RoundedCornerShape(
                    topStart = 16.dp,
                    topEnd = 16.dp,
                    bottomStart = if (isUser) 16.dp else 4.dp,
                    bottomEnd = if (isUser) 4.dp else 16.dp
                ),
                color = if (isUser) theme.primary else if (message.isError) Color(0xFFFEF2F2) else theme.surfaceContainerLow,
                border = if (isUser) null else BorderStroke(0.6.dp, if (message.isError) Color(0xFFFCA5A5) else theme.outline.copy(alpha = 0.2f)),
                shadowElevation = 0.5.dp
            ) {
                Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                    val customSelectionColors = if (isUser) {
                        TextSelectionColors(
                            handleColor = Color.White,
                            backgroundColor = Color(0xFF022C0E).copy(alpha = 0.55f)
                        )
                    } else {
                        TextSelectionColors(
                            handleColor = theme.primary,
                            backgroundColor = theme.primary.copy(alpha = 0.35f)
                        )
                    }

                    CompositionLocalProvider(LocalTextSelectionColors provides customSelectionColors) {
                        SelectionContainer {
                            if (isUser) {
                                Text(
                                    text = message.content,
                                    color = Color.White,
                                    fontSize = 13.5.sp,
                                    lineHeight = 19.sp
                                )
                            } else {
                                MarkdownRenderer(content = message.content, isUser = false)
                            }
                        }
                    }

                    // AI 回复底部复制栏
                    if (!isUser && !message.isError && message.content.isNotBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.align(Alignment.End),
                            horizontalArrangement = Arrangement.End
                        ) {
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .clickable {
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                        clipboard.setPrimaryClip(android.content.ClipData.newPlainText("AI回答", message.content))
                                    }
                                    .padding(horizontal = 4.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = "复制", tint = theme.textMuted, modifier = Modifier.size(11.dp))
                                Spacer(modifier = Modifier.width(3.dp))
                                Text("复制", fontSize = 10.sp, color = theme.textMuted)
                            }
                        }
                    }
                }
            }

            // 错误重试按钮
            if (!isUser && message.isError) {
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFFDC2626))
                        .clickable { onRetry("重试刚才的提问") }
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, tint = Color.White, modifier = Modifier.size(12.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("点击重试", fontSize = 11.sp, color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        }

        if (isUser) {
            Spacer(modifier = Modifier.width(8.dp))
            // 用户头像
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(theme.surfaceContainerHigh),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Person, contentDescription = null, tint = theme.textPrimary, modifier = Modifier.size(16.dp))
            }
        }
    }
}

data class AttachedFileInfo(
    val uri: Uri,
    val name: String,
    val sizeBytes: Long,
    val extension: String
)

private fun getFileInfoFromUri(context: Context, uri: Uri): AttachedFileInfo? {
    var name = "unknown_file"
    var size = 0L
    try {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
            if (cursor.moveToFirst()) {
                if (nameIndex != -1) name = cursor.getString(nameIndex) ?: name
                if (sizeIndex != -1) size = cursor.getLong(sizeIndex)
            }
        }
    } catch (_: Exception) {}
    val ext = name.substringAfterLast('.', "").lowercase()
    return AttachedFileInfo(uri, name, size, ext)
}

private fun readFileContentText(context: Context, uri: Uri, maxChars: Int = 8000): String {
    return try {
        context.contentResolver.openInputStream(uri)?.use { stream ->
            val text = stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            if (text.length > maxChars) {
                text.take(maxChars) + "\n\n...[文件内容过长，TermX 已截取前 $maxChars 字符]..."
            } else {
                text
            }
        } ?: "无法读取文件流"
    } catch (e: Exception) {
        "读取文件失败: ${e.message}"
    }
}

private fun formatFileSize(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024) return String.format(java.util.Locale.US, "%.1f KB", kb)
    val mb = kb / 1024.0
    return String.format(java.util.Locale.US, "%.1f MB", mb)
}
