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
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

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

    private val stateLock = Any()
    private val executionEpoch = AtomicLong(0L)
    private var activeJob: Job? = null
    private var pendingApprovalDeferred: CompletableDeferred<Boolean>? = null
    private var agentClient: TermXAgentClient? = null
    private var toolRegistry: TermXAgentToolRegistry? = null

    private class ExecutionEpochElement(val epoch: Long) : AbstractCoroutineContextElement(Key) {
        companion object Key : CoroutineContext.Key<ExecutionEpochElement>
    }

    fun getOrInitAgentClient(context: Context): TermXAgentClient {
        synchronized(stateLock) {
            if (agentClient == null) {
                val appContext = context.applicationContext ?: context
                val db = AppDatabase.getDatabase(appContext)
                val registry = TermXAgentToolRegistry(
                    context = appContext,
                    db = db,
                    approvalRequester = approval@{ request ->
                        currentCoroutineContext().ensureActive()
                        val reqEpoch = currentCoroutineContext()[ExecutionEpochElement]?.epoch
                        val deferred = CompletableDeferred<Boolean>()
                        synchronized(stateLock) {
                            if (reqEpoch == null || executionEpoch.get() != reqEpoch) {
                                return@approval false
                            }
                            pendingApprovalDeferred = deferred
                            _state.value = _state.value.copy(pendingApprovalRequest = request)
                        }
                        try {
                            deferred.await()
                        } finally {
                            synchronized(stateLock) {
                                if (executionEpoch.get() == reqEpoch) {
                                    if (pendingApprovalDeferred === deferred) {
                                        pendingApprovalDeferred = null
                                    }
                                    if (_state.value.pendingApprovalRequest?.approvalId == request.approvalId) {
                                        _state.value = _state.value.copy(pendingApprovalRequest = null)
                                    }
                                }
                            }
                        }
                    }
                )
                toolRegistry = registry
                agentClient = TermXAgentClient(registry)
            }
            return agentClient!!
        }
    }

    /**
     * 响应人工审批闸口
     */
    fun approveDangerousAction(approved: Boolean) {
        val deferred = synchronized(stateLock) {
            val d = pendingApprovalDeferred
            pendingApprovalDeferred = null
            d
        }
        deferred?.complete(approved)
    }

    /**
     * 手动停止当前执行任务
     */
    fun stopExecution() {
        val jobToCancel: Job?
        val deferredToReject: CompletableDeferred<Boolean>?
        synchronized(stateLock) {
            executionEpoch.incrementAndGet()
            jobToCancel = activeJob
            activeJob = null
            deferredToReject = pendingApprovalDeferred
            pendingApprovalDeferred = null
            _state.value = _state.value.copy(
                isGenerating = false,
                pendingApprovalRequest = null,
                currentActionText = ""
            )
        }
        deferredToReject?.complete(false)
        jobToCancel?.cancel()
    }

    /**
     * 当指定会话被删除或清空时，仅当当前执行属于该会话才取消并重置状态
     */
    fun cancelIfSession(sessionId: String) {
        val jobToCancel: Job?
        val deferredToReject: CompletableDeferred<Boolean>?
        synchronized(stateLock) {
            if (_state.value.sessionId != sessionId) return
            executionEpoch.incrementAndGet()
            jobToCancel = activeJob
            activeJob = null
            deferredToReject = pendingApprovalDeferred
            pendingApprovalDeferred = null
            _state.value = AgentExecutionState()
        }
        deferredToReject?.complete(false)
        jobToCancel?.cancel()
    }

    /**
     * 发起 Agent 执行任务 (全生命周期保护 + 执行世代隔离)
     */
    fun executePrompt(
        context: Context,
        sessionId: String,
        updatedMessages: List<AiChatMessage>,
        onSessionUpdated: (() -> Unit)? = null
    ) {
        val appContext = context.applicationContext ?: context
        val configManager = AiConfigManager(appContext)
        val aiConfig = configManager.loadConfig()
        val client = getOrInitAgentClient(appContext)

        val oldJobToCancel: Job?
        val oldDeferredToReject: CompletableDeferred<Boolean>?
        val myEpoch: Long
        val launchedJob: Job

        synchronized(stateLock) {
            // 如果当前已有同会话任务正在生成，避免重复投递
            if (_state.value.isGenerating && _state.value.sessionId == sessionId) {
                return
            }

            myEpoch = executionEpoch.incrementAndGet()
            oldJobToCancel = activeJob
            oldDeferredToReject = pendingApprovalDeferred
            pendingApprovalDeferred = null

            toolRegistry?.currentAiSessionId = sessionId
            _state.value = AgentExecutionState(
                sessionId = sessionId,
                isGenerating = true,
                currentChunkText = "",
                currentReasoningText = "",
                currentActionText = "正在初始化运维环境与策略...",
                pendingApprovalRequest = null
            )

            launchedJob = executionScope.launch(context = ExecutionEpochElement(myEpoch), start = CoroutineStart.LAZY) {
                var taskAcquired = false
                try {
                    try {
                        SshForegroundService.acquireTask(appContext)
                        taskAcquired = true
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to acquire foreground task: ${e.message}")
                    }

                    client.chatStream(
                        config = aiConfig,
                        conversationHistory = updatedMessages,
                        onChunk = onChunk@{ delta, isThinking ->
                            synchronized(stateLock) {
                                if (executionEpoch.get() != myEpoch) return@onChunk
                                if (isThinking) {
                                    _state.value = _state.value.copy(
                                        currentReasoningText = _state.value.currentReasoningText + delta
                                    )
                                } else {
                                    _state.value = _state.value.copy(
                                        currentChunkText = _state.value.currentChunkText + delta
                                    )
                                }
                            }
                        },
                        onToolAction = onToolAction@{ action ->
                            synchronized(stateLock) {
                                if (executionEpoch.get() != myEpoch) return@onToolAction
                                _state.value = _state.value.copy(currentActionText = action)
                            }
                        },
                        onError = onError@{ err ->
                            val errMsg = AiChatMessage.assistant(content = "❌ $err", isError = true)
                            val afterError = updatedMessages + errMsg
                            var delivered = false
                            synchronized(stateLock) {
                                if (executionEpoch.get() != myEpoch) return@onError
                                if (configManager.hasSession(sessionId)) {
                                    configManager.saveMessages(sessionId, afterError)
                                }
                                _state.value = _state.value.copy(
                                    isGenerating = false,
                                    currentActionText = ""
                                )
                                delivered = true
                            }
                            if (delivered) {
                                dispatchSessionUpdated(myEpoch, onSessionUpdated)
                            }
                        },
                        onComplete = onComplete@{ full, reasoning ->
                            val assistantMsg = AiChatMessage.assistant(content = full, reasoning = reasoning)
                            val afterDone = updatedMessages + assistantMsg
                            var delivered = false
                            synchronized(stateLock) {
                                if (executionEpoch.get() != myEpoch) return@onComplete
                                if (configManager.hasSession(sessionId)) {
                                    configManager.saveMessages(sessionId, afterDone)
                                }
                                _state.value = _state.value.copy(
                                    isGenerating = false,
                                    currentActionText = ""
                                )
                                delivered = true
                            }
                            if (delivered) {
                                dispatchSessionUpdated(myEpoch, onSessionUpdated)
                            }
                        }
                    )
                } catch (e: CancellationException) {
                    Log.i(TAG, "Agent task was cancelled (epoch=$myEpoch)")
                    synchronized(stateLock) {
                        if (executionEpoch.get() == myEpoch) {
                            _state.value = _state.value.copy(
                                isGenerating = false,
                                pendingApprovalRequest = null,
                                currentActionText = ""
                            )
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Unexpected error in Agent execution", e)
                    val errMsg = AiChatMessage.assistant(content = "❌ 执行异常中断: ${e.message}", isError = true)
                    val afterError = updatedMessages + errMsg
                    var delivered = false
                    synchronized(stateLock) {
                        if (executionEpoch.get() != myEpoch) return@launch
                        if (configManager.hasSession(sessionId)) {
                            configManager.saveMessages(sessionId, afterError)
                        }
                        _state.value = _state.value.copy(
                            isGenerating = false,
                            pendingApprovalRequest = null,
                            currentActionText = ""
                        )
                        delivered = true
                    }
                    if (delivered) {
                        dispatchSessionUpdated(myEpoch, onSessionUpdated)
                    }
                } finally {
                    if (taskAcquired) {
                        try {
                            SshForegroundService.releaseTask(appContext)
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            Log.w(TAG, "Failed to release foreground task: ${e.message}")
                        }
                    }
                }
            }
            activeJob = launchedJob
        }

        oldDeferredToReject?.complete(false)
        oldJobToCancel?.cancel()
        launchedJob.start()
    }

    private fun dispatchSessionUpdated(myEpoch: Long, onSessionUpdated: (() -> Unit)?) {
        val callback = onSessionUpdated ?: return
        try {
            executionScope.launch(Dispatchers.Main) {
                if (executionEpoch.get() == myEpoch) {
                    callback.invoke()
                }
            }
        } catch (_: IllegalStateException) {
            // 兼容无 Android Main dispatcher 的纯 JVM 测试环境
            if (executionEpoch.get() == myEpoch) {
                callback.invoke()
            }
        }
    }
}
