package com.termius.clone.ui.screens

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.termius.clone.ai.TermXAgentClient
import com.termius.clone.ai.TermXAgentToolRegistry
import com.termius.clone.data.local.AiConfigManager
import com.termius.clone.data.local.AppDatabase
import com.termius.clone.data.model.AiChatMessage
import com.termius.clone.data.model.DangerousActionRequest
import com.termius.clone.terminal.session.SessionManager
import com.termius.clone.ui.components.AiThinkingCard
import com.termius.clone.ui.components.AiToolActionPill
import com.termius.clone.ui.components.DangerousActionApprovalCard
import com.termius.clone.ui.components.MarkdownText
import com.termius.clone.ui.theme.LocalAppTheme
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * TermX Mobile 全功能 AI SRE 运维智能体工作台
 */
@Composable
fun AiChatScreen(
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val theme = LocalAppTheme.current
    val scope = rememberCoroutineScope()
    val db = remember { AppDatabase.getDatabase(context) }
    val configManager = remember { AiConfigManager(context) }

    var aiConfig by remember { mutableStateOf(configManager.loadConfig()) }
    var showSettingsDialog by remember { mutableStateOf(false) }

    // 消息列表与输入框
    var messages by remember { mutableStateOf(configManager.loadHistory()) }
    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    // 生成与流式状态
    var isGenerating by remember { mutableStateOf(false) }
    var currentChunkText by remember { mutableStateOf("") }
    var currentReasoningText by remember { mutableStateOf("") }
    var currentActionText by remember { mutableStateOf("") }
    var activeJob by remember { mutableStateOf<Job?>(null) }

    // 人工审批闸口状态
    var pendingApprovalRequest by remember { mutableStateOf<DangerousActionRequest?>(null) }
    var pendingApprovalDeferred by remember { mutableStateOf<CompletableDeferred<Boolean>?>(null) }

    // 初始化 ToolRegistry 与 AgentClient
    val toolRegistry = remember {
        TermXAgentToolRegistry(
            context = context,
            db = db,
            approvalRequester = { request ->
                val deferred = CompletableDeferred<Boolean>()
                pendingApprovalRequest = request
                pendingApprovalDeferred = deferred
                try {
                    deferred.await()
                } finally {
                    pendingApprovalRequest = null
                    pendingApprovalDeferred = null
                }
            }
        )
    }

    val agentClient = remember(toolRegistry) {
        TermXAgentClient(toolRegistry)
    }

    // 活跃会话与当前主机
    val activeSession = SessionManager.currentSession
    val targetHostTitle = activeSession?.host?.let { "${it.username}@${it.label}" } ?: "自动感知主机"

    // 自动滚到底部
    LaunchedEffect(messages.size, currentChunkText, currentActionText, pendingApprovalRequest) {
        if (messages.isNotEmpty() || isGenerating || pendingApprovalRequest != null) {
            val totalCount = messages.size + (if (isGenerating) 1 else 0) + (if (pendingApprovalRequest != null) 1 else 0)
            if (totalCount > 0) {
                listState.animateScrollToItem(totalCount - 1)
            }
        }
    }

    val sendMessage: (String) -> Unit = { content ->
        val trimmed = content.trim()
        if (trimmed.isNotBlank() && !isGenerating && pendingApprovalRequest == null) {
            val userMsg = AiChatMessage(role = "user", content = trimmed)
            val updatedList = messages + userMsg
            messages = updatedList
            inputText = ""
            configManager.saveHistory(updatedList)

            isGenerating = true
            currentChunkText = ""
            currentReasoningText = ""
            currentActionText = ""

            activeJob = scope.launch {
                agentClient.chatStream(
                    config = aiConfig,
                    conversationHistory = updatedList,
                    onChunk = { delta, isThinking ->
                        if (isThinking) {
                            currentReasoningText += delta
                        } else {
                            currentChunkText += delta
                        }
                    },
                    onToolAction = { action ->
                        currentActionText = action
                    },
                    onError = { err ->
                        val errMsg = AiChatMessage(
                            role = "assistant",
                            content = "❌ 发生错误: $err",
                            isError = true
                        )
                        val afterError = messages + errMsg
                        messages = afterError
                        configManager.saveHistory(afterError)
                        isGenerating = false
                        currentChunkText = ""
                        currentReasoningText = ""
                        currentActionText = ""
                    },
                    onComplete = { full, reasoning ->
                        val assistantMsg = AiChatMessage(
                            role = "assistant",
                            content = full,
                            reasoningContent = reasoning
                        )
                        val afterDone = messages + assistantMsg
                        messages = afterDone
                        configManager.saveHistory(afterDone)
                        isGenerating = false
                        currentChunkText = ""
                        currentReasoningText = ""
                        currentActionText = ""
                    }
                )
            }
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            Surface(
                color = theme.surfaceContainerLow,
                shadowElevation = if (theme.isDark) 0.dp else 1.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .height(52.dp)
                        .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回", tint = theme.textPrimary)
                    }

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 4.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "TermX SRE 运维智能体",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = theme.textPrimary
                            )
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = theme.primary,
                                modifier = Modifier.size(14.dp)
                            )
                        }

                        Text(
                            text = "🟢 当前关联: $targetHostTitle",
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            color = theme.textSecondary
                        )
                    }

                    // 清空对话
                    if (messages.isNotEmpty()) {
                        IconButton(
                            onClick = {
                                messages = emptyList()
                                configManager.clearHistory()
                            }
                        ) {
                            Icon(Icons.Default.DeleteSweep, contentDescription = "清空对话", tint = theme.textMuted)
                        }
                    }

                    // 设置弹窗
                    IconButton(onClick = { showSettingsDialog = true }) {
                        Icon(Icons.Default.Settings, contentDescription = "模型设置", tint = theme.textPrimary)
                    }
                }
            }
        },
        containerColor = theme.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding()
        ) {
            // 消息区域
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                if (messages.isEmpty() && !isGenerating && pendingApprovalRequest == null) {
                    // 空状态引导
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .background(theme.primary.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = theme.primary,
                                modifier = Modifier.size(30.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        Text(
                            text = "掌上自动化 SRE 运维助手",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = theme.textPrimary
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = "感知目标环境 · 自动执行诊断 · 高危操作人工审批 · 联网检索",
                            fontSize = 12.sp,
                            color = theme.textSecondary
                        )

                        Spacer(modifier = Modifier.height(24.dp))

                        // 快速引导卡片
                        Column(
                            modifier = Modifier.fillMaxWidth(0.9f),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf(
                                "🔍 对当前服务器做一次系统体检 (CPU/内存/磁盘)" to "请帮我对当前主机进行全面的系统体检（CPU占用、内存分布、磁盘根分区空间、僵尸进程与高负载原因）",
                                "🐳 排查 Docker 容器状态与退出日志" to "请帮我排查当前主机的 Docker 服务及所有容器状态，看是否有异常 Exited 容器并分析日志",
                                "🌐 查看对外暴露端口与防火墙规则" to "请帮我查看当前主机监听的对外网络端口与防火墙状态 (ss -tulpn)",
                                "📋 读取当前终端屏幕并分析报错原因" to "请帮我读取当前终端屏幕最后输出，分析刚才命令执行失败的原因并给出修复方案"
                            ).forEach { (label, prompt) ->
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = theme.surfaceContainerLow,
                                    border = androidx.compose.foundation.BorderStroke(0.6.dp, theme.outline.copy(alpha = 0.25f)),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .clickable { sendMessage(prompt) }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(text = label, fontSize = 12.5.sp, color = theme.textPrimary)
                                        Icon(Icons.Default.ArrowForward, contentDescription = null, tint = theme.primary, modifier = Modifier.size(14.dp))
                                    }
                                }
                            }
                        }
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 14.dp),
                        contentPadding = PaddingValues(vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        items(messages) { msg ->
                            if (msg.role == "user") {
                                // 用户提问
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .widthIn(max = 290.dp)
                                            .clip(RoundedCornerShape(16.dp, 16.dp, 4.dp, 16.dp))
                                            .background(theme.primary)
                                            .padding(horizontal = 14.dp, vertical = 10.dp)
                                    ) {
                                        Text(
                                            text = msg.content,
                                            color = Color.White,
                                            fontSize = 13.5.sp,
                                            lineHeight = 19.sp
                                        )
                                    }
                                }
                            } else if (msg.role == "assistant") {
                                // AI 回复
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    // 思考过程 (折叠)
                                    if (msg.reasoningContent.isNotBlank()) {
                                        AiThinkingCard(reasoningContent = msg.reasoningContent)
                                    }

                                    // 正文
                                    Surface(
                                        shape = RoundedCornerShape(4.dp, 16.dp, 16.dp, 16.dp),
                                        color = theme.surfaceContainerLow,
                                        border = androidx.compose.foundation.BorderStroke(0.6.dp, theme.outline.copy(alpha = 0.2f)),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Box(modifier = Modifier.padding(14.dp)) {
                                            MarkdownText(
                                                markdown = msg.content,
                                                textColor = theme.textPrimary,
                                                linkColor = theme.primary,
                                                fontSizeSp = 13.5f
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // 正在生成中状态
                        if (isGenerating) {
                            item {
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    // 工具动作胶囊
                                    if (currentActionText.isNotBlank()) {
                                        AiToolActionPill(actionText = currentActionText)
                                    }

                                    // 流式思考链
                                    if (currentReasoningText.isNotBlank()) {
                                        AiThinkingCard(reasoningContent = currentReasoningText)
                                    }

                                    // 流式正文
                                    if (currentChunkText.isNotBlank()) {
                                        Surface(
                                            shape = RoundedCornerShape(4.dp, 16.dp, 16.dp, 16.dp),
                                            color = theme.surfaceContainerLow,
                                            border = androidx.compose.foundation.BorderStroke(0.6.dp, theme.outline.copy(alpha = 0.2f)),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Box(modifier = Modifier.padding(14.dp)) {
                                                MarkdownText(
                                                    markdown = currentChunkText,
                                                    textColor = theme.textPrimary,
                                                    linkColor = theme.primary,
                                                    fontSizeSp = 13.5f
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // 人工审批卡片 (挂起等待)
                        pendingApprovalRequest?.let { req ->
                            item {
                                DangerousActionApprovalCard(
                                    request = req,
                                    onApprove = {
                                        pendingApprovalDeferred?.complete(true)
                                    },
                                    onReject = {
                                        pendingApprovalDeferred?.complete(false)
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // 底部操作区
            Surface(
                color = theme.surfaceContainerLow,
                shadowElevation = if (theme.isDark) 0.dp else 2.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    // 快捷操作胶囊 (Chips)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(bottom = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // 1. 带入终端屏幕
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = theme.primary.copy(alpha = 0.12f),
                            border = androidx.compose.foundation.BorderStroke(0.8.dp, theme.primary.copy(alpha = 0.35f)),
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .clickable(enabled = !isGenerating && pendingApprovalRequest == null) {
                                    val screenPrompt = "请读取当前终端屏幕最后输出并分析刚才的操作与报错："
                                    inputText = screenPrompt
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(Icons.Default.Terminal, contentDescription = null, tint = theme.primary, modifier = Modifier.size(13.dp))
                                Text("📋 带入终端屏幕", fontSize = 11.5.sp, color = theme.primary, fontWeight = FontWeight.SemiBold)
                            }
                        }

                        // 2. 一键体检
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = theme.surfaceContainer,
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .clickable(enabled = !isGenerating && pendingApprovalRequest == null) {
                                    sendMessage("请帮我对当前主机进行全面的系统体检（CPU占用、内存分布、磁盘根分区空间、僵尸进程与高负载原因）")
                                }
                        ) {
                            Text(
                                text = "🔍 系统全面体检",
                                fontSize = 11.5.sp,
                                color = theme.textSecondary,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                            )
                        }

                        // 3. 检查 Docker
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = theme.surfaceContainer,
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .clickable(enabled = !isGenerating && pendingApprovalRequest == null) {
                                    sendMessage("请帮我检查当前主机的 Docker 容器运行状态，看是否有异常退出或重启的容器")
                                }
                        ) {
                            Text(
                                text = "🐳 检查 Docker 状态",
                                fontSize = 11.5.sp,
                                color = theme.textSecondary,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                            )
                        }

                        // 4. 排查端口
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = theme.surfaceContainer,
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .clickable(enabled = !isGenerating && pendingApprovalRequest == null) {
                                    sendMessage("请帮我查看当前主机正在监听的网络端口及所属进程 (ss -tulpn)")
                                }
                        ) {
                            Text(
                                text = "🌐 排查端口与服务",
                                fontSize = 11.5.sp,
                                color = theme.textSecondary,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                            )
                        }
                    }

                    // 输入框与发送按钮
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(14.dp))
                                .background(theme.surfaceContainer)
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            BasicTextField(
                                value = inputText,
                                onValueChange = { inputText = it },
                                modifier = Modifier.fillMaxWidth(),
                                textStyle = TextStyle(
                                    fontSize = 13.5.sp,
                                    color = theme.textPrimary,
                                    lineHeight = 18.sp
                                ),
                                maxLines = 4,
                                cursorBrush = SolidColor(theme.primary),
                                enabled = !isGenerating && pendingApprovalRequest == null,
                                decorationBox = { inner ->
                                    if (inputText.isEmpty()) {
                                        Text(
                                            text = if (pendingApprovalRequest != null) "等待安全审批操作..." else "输入运维需求，例如：帮我看看为什么502...",
                                            style = TextStyle(fontSize = 13.sp, color = theme.textMuted)
                                        )
                                    }
                                    inner()
                                }
                            )
                        }

                        if (isGenerating) {
                            IconButton(
                                onClick = {
                                    activeJob?.cancel()
                                    isGenerating = false
                                    currentChunkText = ""
                                    currentActionText = ""
                                },
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFFEF4444))
                            ) {
                                Icon(Icons.Default.Stop, contentDescription = "停止生成", tint = Color.White, modifier = Modifier.size(20.dp))
                            }
                        } else {
                            IconButton(
                                onClick = { sendMessage(inputText) },
                                enabled = inputText.isNotBlank() && pendingApprovalRequest == null,
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(if (inputText.isNotBlank() && pendingApprovalRequest == null) theme.primary else theme.surfaceContainer)
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Filled.Send,
                                    contentDescription = "发送",
                                    tint = if (inputText.isNotBlank() && pendingApprovalRequest == null) Color.White else theme.textMuted,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
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
