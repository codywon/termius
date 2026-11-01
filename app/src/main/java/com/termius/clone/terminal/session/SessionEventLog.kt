package com.termius.clone.terminal.session

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 连接生命周期事件类型。
 *
 * 设计约束（重要）：这些事件**只供 UI 层消费**，永远不得写入 PTY 字符缓冲。
 * 终端滚动区在语义上属于远端服务器的输出，客户端状态混进去会打断用户输入、
 * 污染全屏程序（vim/less），并让 AI 的屏幕抓取把客户端话术误当服务器输出。
 */
enum class ConnectionEventType {
    /** 首次连接进行中 */
    CONNECTING,

    /** 某一次重连尝试进行中（手动重连时 attempt 可能为 0） */
    RECONNECTING,

    /** 首次连接成功 */
    CONNECTED,

    /** 重连成功（UI 层据此短暂显示"已恢复"） */
    RECONNECT_SUCCESS,

    /** 已排定延迟重试，UI 层据此展示倒计时 */
    RETRY_SCHEDULED,

    /** 自动重连达上限，需要用户决策 */
    RECONNECT_EXHAUSTED,

    /** 单次尝试失败（后面通常紧跟 RETRY_SCHEDULED，属瞬时事件） */
    ERROR,

    /** 非预期断开（后面通常紧跟 RETRY_SCHEDULED） */
    DISCONNECTED,

    /** 用户主动断开 */
    MANUAL_DISCONNECT
}

/**
 * 单条连接事件。既是 UI 状态胶囊的数据源，也是"连接事件记录"日志的一行。
 */
data class ConnectionEvent(
    val timestamp: Long,
    val type: ConnectionEventType,
    val message: String = "",
    val attempt: Int = 0,
    val maxAttempts: Int = 0,
    val retryDelaySec: Int = 0,
    val isAuthFailure: Boolean = false
)

/**
 * 会话连接事件环形缓冲：默认保留最近 200 条，仅驻留内存。
 *
 * 之所以不落 Room：AppDatabase 当前 version = 1 且未配置迁移，
 * 新增实体需要升版本 + 迁移脚本，属于独立改动，本轮不把持久化风险混进体验修复。
 */
class SessionEventLog(private val capacity: Int = 200) {

    private val entries = java.util.ArrayDeque<ConnectionEvent>(capacity.coerceAtLeast(1))

    private val _version = MutableStateFlow(0)

    /** 版本号，Compose 侧 collect 它来感知日志变化 */
    val version: StateFlow<Int> = _version.asStateFlow()

    @Synchronized
    fun append(event: ConnectionEvent) {
        if (entries.size >= capacity) {
            entries.removeFirst()
        }
        entries.addLast(event)
        _version.value = _version.value + 1
    }

    @Synchronized
    fun snapshot(): List<ConnectionEvent> = entries.toList()

    @Synchronized
    fun latest(): ConnectionEvent? = entries.peekLast()

    @Synchronized
    fun clear() {
        entries.clear()
        _version.value = _version.value + 1
    }
}
