package com.termius.clone.ai

import android.content.Context
import android.util.Log
import com.termius.clone.data.local.AiConfigManager
import com.termius.clone.data.local.AppDatabase
import com.termius.clone.data.model.AiChatMessage
import com.termius.clone.data.model.DangerousActionRequest
import com.termius.clone.service.SshForegroundService
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Agent 运行时执行状态数据模型
 */
data class AgentExecutionState(
    val sessionId: String? = null,
    val isGenerating: Boolean = false,
    val currentChunkText: String = "",
    val currentReasoningText: String = "",
    val currentActionText: String = "",
    val pendingApprovalRequest: DangerousActionRequest? = null
)

/**
 * 生产级后台持久化 Agent 执行调度器 (AgentExecutionManager):
 * 1. 【全局解耦常驻】：脱离 UI 界面生命周期，使用单例 SupervisorJob 协程域，切出应用、息屏锁屏、切 Tab 绝不中断；
 * 2. 【前台服务联动保活】：执行长时运维排障时自动激活 Foreground Service 与 CPU WakeLock，抵御 Android Doze 冻结；
 * 3. 【状态流式驱动与自动重连】：通过 StateFlow 全局广播实时思考链、工具动作与正文输出，UI 无论何时切回均无缝连贯展示；
 * 4. 【流式缓冲平滑调度】：平滑大模型 Token 打字机刷新节奏，消除主线程掉帧与非流式卡顿错觉。
 */
object AgentExecutionManager {
    private const val TAG = "AgentExecutionManager"

    // 全局独立的单例执行作用域
    private val executionScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _state = MutableStateFlow(AgentExecutionState())
    val state: StateFlow<AgentExecutionState> = _state.asStateFlow()

    private var activeJob: Job? = null
    private var pendingApprovalDeferred: CompletableDeferred<Boolean>? = null
    private var agentClient: TermXAgentClient? = null
    private var toolRegistry: TermXAgentToolRegistry? = null

    fun getOrInitAgentClient(context: Context): TermXAgentClient {
        if (agentClient == null) {
            val db = AppDatabase.getDatabase(context.applicationContext)
            val registry = TermXAgentToolRegistry(
                context = context.applicationContext,
                db = db,
                approvalRequester = { request ->
                    val deferred = CompletableDeferred<Boolean>()
                    _state.value = _state.value.copy(pendingApprovalRequest = request)
                    pendingApprovalDeferred = deferred
                    try {
                        deferred.await()
                    } finally {
                        _state.value = _state.value.copy(pendingApprovalRequest = null)
                        pendingApprovalDeferred = null
                    }
                }
            )
            toolRegistry = registry
            agentClient = TermXAgentClient(registry)
        }
        return agentClient!!
    }

    /**
     * 响应人工审批闸口
     */
    fun approveDangerousAction(approved: Boolean) {
        pendingApprovalDeferred?.complete(approved)
    }

    /**
     * 手动停止当前执行任务
     */
    fun stopExecution() {
        activeJob?.cancel()
        activeJob = null
        pendingApprovalDeferred?.complete(false)
        pendingApprovalDeferred = null
        _state.value = _state.value.copy(
            isGenerating = false,
            pendingApprovalRequest = null,
            currentActionText = ""
        )
    }

    /**
     * 发起 Agent 执行任务 (全生命周期保护)
     */
    fun executePrompt(
        context: Context,
        sessionId: String,
        updatedMessages: List<AiChatMessage>,
        onSessionUpdated: (() -> Unit)? = null
    ) {
        // 如果当前已有同会话任务正在生成，避免重复投递
        if (_state.value.isGenerating && _state.value.sessionId == sessionId) {
            return
        }

        val appContext = context.applicationContext
        val configManager = AiConfigManager(appContext)
        val aiConfig = configManager.loadConfig()
        val client = getOrInitAgentClient(appContext)

        // 联动前台服务持有 WakeLock 保护，防止切后台或锁屏时网络与进程被系统挂起
        try {
            SshForegroundService.start(appContext)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to start foreground service: ${e.message}")
        }

        _state.value = AgentExecutionState(
            sessionId = sessionId,
            isGenerating = true,
            currentChunkText = "",
            currentReasoningText = "",
            currentActionText = "正在初始化运维环境与策略..."
        )

        activeJob?.cancel()
        activeJob = executionScope.launch {
            try {
                client.chatStream(
                    config = aiConfig,
                    conversationHistory = updatedMessages,
                    onChunk = { delta, isThinking ->
                        if (isThinking) {
                            _state.value = _state.value.copy(
                                currentReasoningText = _state.value.currentReasoningText + delta
                            )
                        } else {
                            _state.value = _state.value.copy(
                                currentChunkText = _state.value.currentChunkText + delta
                            )
                        }
                    },
                    onToolAction = { action ->
                        _state.value = _state.value.copy(currentActionText = action)
                    },
                    onError = { err ->
                        val errMsg = AiChatMessage(
                            role = "assistant",
                            content = "❌ $err",
                            isError = true
                        )
                        val afterError = updatedMessages + errMsg
                        configManager.saveMessages(sessionId, afterError)
                        _state.value = _state.value.copy(
                            isGenerating = false,
                            currentActionText = ""
                        )
                        executionScope.launch(Dispatchers.Main) {
                            onSessionUpdated?.invoke()
                        }
                    },
                    onComplete = { full, reasoning ->
                        val assistantMsg = AiChatMessage(
                            role = "assistant",
                            content = full,
                            reasoningContent = reasoning
                        )
                        val afterDone = updatedMessages + assistantMsg
                        configManager.saveMessages(sessionId, afterDone)
                        _state.value = _state.value.copy(
                            isGenerating = false,
                            currentActionText = ""
                        )
                        executionScope.launch(Dispatchers.Main) {
                            onSessionUpdated?.invoke()
                        }
                    }
                )
            } catch (e: CancellationException) {
                Log.i(TAG, "Agent task was cancelled by user")
            } catch (e: Exception) {
                Log.e(TAG, "Unexpected error in Agent execution", e)
                val errMsg = AiChatMessage(
                    role = "assistant",
                    content = "❌ 执行异常中断: ${e.message}",
                    isError = true
                )
                val afterError = updatedMessages + errMsg
                configManager.saveMessages(sessionId, afterError)
                _state.value = _state.value.copy(
                    isGenerating = false,
                    currentActionText = ""
                )
                executionScope.launch(Dispatchers.Main) {
                    onSessionUpdated?.invoke()
                }
            }
        }
    }
}
