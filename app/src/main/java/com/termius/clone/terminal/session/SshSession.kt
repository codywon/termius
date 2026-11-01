package com.termius.clone.terminal.session

import com.termius.clone.data.model.AuthType
import com.termius.clone.data.model.HostEntity
import com.termius.clone.data.model.IdentityEntity
import com.termius.clone.data.model.TerminalThemes
import com.termius.clone.terminal.engine.TerminalBuffer
import com.termius.clone.terminal.engine.TerminalEmulator
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.connection.channel.direct.Session
import net.schmizz.sshj.transport.verification.PromiscuousVerifier
import net.schmizz.sshj.userauth.keyprovider.KeyProvider
import net.schmizz.sshj.userauth.password.PasswordUtils
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID

enum class SessionState {
    DISCONNECTED,
    CONNECTING,
    AUTHENTICATING,
    CONNECTED,
    ERROR
}

class SshSession(
    val id: String = UUID.randomUUID().toString(),
    val host: HostEntity,
    val identity: IdentityEntity? = null,
    initialCols: Int = 80,
    initialRows: Int = 24
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    val terminalBuffer = TerminalBuffer(
        cols = initialCols,
        rows = initialRows,
        theme = if (host.terminalTheme.isNotBlank()) TerminalThemes.getThemeByName(host.terminalTheme) else com.termius.clone.ui.theme.ThemeManager.currentTerminalTheme
    )
    val emulator = TerminalEmulator(terminalBuffer)

    private val _sessionState = MutableStateFlow(SessionState.DISCONNECTED)
    val sessionState: StateFlow<SessionState> = _sessionState
    val isConnected: Boolean get() = _sessionState.value == SessionState.CONNECTED

    /**
     * 连接生命周期事件队列（内存环形缓冲，UI 层"连接事件记录"的数据源）。
     */
    val eventLog = SessionEventLog()

    /**
     * UI 层唯一状态来源：标签状态点、视口状态胶囊、事件记录全部订阅它。
     * 连接提示一律走这里，不再有任何一行被写进 PTY 字符缓冲。
     */
    private val _connectionEvent = MutableStateFlow<ConnectionEvent?>(null)
    val connectionEvent: StateFlow<ConnectionEvent?> = _connectionEvent

    /** 上一次失败是否属于凭据/密钥类，用于把重试文案从"重连中"改成"认证失败" */
    private var lastFailureWasAuth = false

    // 用于通知 Compose 界面重绘终端
    private val _renderTick = MutableStateFlow(0L)
    val renderTick: StateFlow<Long> = _renderTick

    private var sshClient: SSHClient? = null
    fun getClient(): SSHClient? = sshClient
    private var sshSession: Session? = null
    private var shell: Session.Shell? = null
    private var outputStream: OutputStream? = null
    private var inputStream: InputStream? = null

    // 重连与生命周期控制
    private var isUserInitiatedDisconnect = false
    private var reconnectJob: Job? = null
    private var reconnectAttempts = 0
    private val maxAutoReconnectAttempts = 3
    private var connectionJob: Job? = null

    /**
     * 连接世代号：每次 connect() 递增。旧连接的读循环退出时若世代号已过期，
     * 则不得再改会话状态、发事件或触发重连（否则重连会被旧循环二次唤醒而抖动）。
     */
    @Volatile
    private var connectionEpoch = 0

    fun connect(isReconnecting: Boolean = false) {
        connectionJob?.cancel()
        // 同步递增世代号：确保旧读循环即使此刻退出，也拿不到当前世代
        val epoch = ++connectionEpoch
        connectionJob = scope.launch {
            // 连接阶段标记：auth，用于把"凭据失败"与"网络失败"区分开
            var phase = "connect"
            try {
                cleanupConnection()
                _sessionState.value = SessionState.CONNECTING
                publishEvent(
                    type = if (isReconnecting) ConnectionEventType.RECONNECTING else ConnectionEventType.CONNECTING,
                    message = if (isReconnecting) {
                        "正在重新连接 ${host.hostname}:${host.port}"
                    } else {
                        "正在连接到 ${host.hostname}:${host.port}"
                    },
                    isAuthFailure = isReconnecting && lastFailureWasAuth
                )

                com.termius.clone.TermiusApplication.setupBouncyCastle()
                val client = SSHClient()
                // 允许未知主机指纹（首次连接记录并接受）
                client.addHostKeyVerifier(PromiscuousVerifier())
                client.connect(host.hostname, host.port)
                sshClient = client

                _sessionState.value = SessionState.AUTHENTICATING
                phase = "auth"

                val username = if (host.authType == AuthType.IDENTITY_REF && identity != null) {
                    identity.username
                } else {
                    host.username
                }

                when (host.authType) {
                    AuthType.PASSWORD -> {
                        client.authPassword(username, host.password)
                    }
                    AuthType.KEY -> {
                        if (host.privateKey.isBlank()) {
                            throw IllegalArgumentException("SSH 私钥为空，请在主机设置中粘贴有效的私钥内容或切换为密码认证！")
                        }
                        val keyProvider: KeyProvider = if (host.passphrase.isNotEmpty()) {
                            client.loadKeys(host.privateKey, null, PasswordUtils.createOneOff(host.passphrase.toCharArray()))
                        } else {
                            client.loadKeys(host.privateKey, null, null)
                        }
                        client.authPublickey(username, keyProvider)
                    }
                    AuthType.IDENTITY_REF -> {
                        if (identity != null) {
                            if (identity.privateKey.isNotEmpty()) {
                                val keyProvider: KeyProvider = if (identity.passphrase.isNotEmpty()) {
                                    client.loadKeys(identity.privateKey, null, PasswordUtils.createOneOff(identity.passphrase.toCharArray()))
                                } else {
                                    client.loadKeys(identity.privateKey, null, null)
                                }
                                client.authPublickey(username, keyProvider)
                            } else {
                                client.authPassword(username, identity.password)
                            }
                        } else {
                            client.authPassword(username, host.password)
                        }
                    }
                }

                if (!client.isAuthenticated) {
                    throw IllegalStateException("SSH 身份凭据校验失败，请检查用户名/密码或私钥！")
                }

                _sessionState.value = SessionState.CONNECTED
                reconnectAttempts = 0
                lastFailureWasAuth = false
                phase = "shell"
                publishEvent(
                    type = if (isReconnecting) ConnectionEventType.RECONNECT_SUCCESS else ConnectionEventType.CONNECTED,
                    message = if (isReconnecting) "重连成功" else "已建立连接"
                )

                // 启动 PTY Shell 会话
                val session = client.startSession()
                sshSession = session
                session.allocatePTY(
                    "xterm-256color",
                    terminalBuffer.cols,
                    terminalBuffer.rows,
                    terminalBuffer.cols * 8,
                    terminalBuffer.rows * 16,
                    emptyMap()
                )

                val sh = session.startShell()
                shell = sh
                outputStream = sh.outputStream
                inputStream = sh.inputStream

                // 启动读取协程
                startReadingLoop(epoch)

            } catch (e: Throwable) {
                // 被新的 connect() 取消时不算失败：不报错、不触发重连
                if (e is CancellationException || !isActive) throw e
                e.printStackTrace()
                _sessionState.value = SessionState.ERROR
                val errMsg = e.localizedMessage ?: e.message ?: e.javaClass.simpleName
                lastFailureWasAuth = phase == "auth"
                publishEvent(
                    type = ConnectionEventType.ERROR,
                    message = "连接失败: $errMsg",
                    isAuthFailure = lastFailureWasAuth
                )

                if (!isUserInitiatedDisconnect) {
                    // 内部会按已尝试次数决定是"排定重试"还是"达上限"，两者都会发事件
                    triggerAutoReconnect()
                }
            }
        }
    }

    private val isRenderPending = java.util.concurrent.atomic.AtomicBoolean(false)
    private var lastRenderTimestamp = 0L

    private fun scheduleRender() {
        val now = System.currentTimeMillis()
        if (now - lastRenderTimestamp >= 30L) {
            lastRenderTimestamp = now
            _renderTick.value = now
        } else {
            if (isRenderPending.compareAndSet(false, true)) {
                scope.launch(Dispatchers.Default) {
                    kotlinx.coroutines.delay(25L)
                    isRenderPending.set(false)
                    val t = System.currentTimeMillis()
                    lastRenderTimestamp = t
                    _renderTick.value = t
                }
            }
        }
    }

    private fun startReadingLoop(epoch: Int) {
        scope.launch(Dispatchers.IO) {
            val buffer = ByteArray(8192)
            try {
                val stream = inputStream ?: return@launch
                while (isActive) {
                    val read = stream.read(buffer)
                    if (read == -1) break
                    if (read > 0) {
                        emulator.processInput(buffer, 0, read)
                        scheduleRender()
                    }
                }
            } catch (e: Exception) {
                // 流断开
            } finally {
                // 世代号过期：属于上一次连接，状态与事件由新连接接管
                if (epoch == connectionEpoch) {
                    val userDisconnect = isUserInitiatedDisconnect
                    _sessionState.value = SessionState.DISCONNECTED
                    cleanupConnection()
                    if (!userDisconnect) {
                        publishEvent(ConnectionEventType.DISCONNECTED, "网络连接中断，连接已关闭")
                        triggerAutoReconnect()
                    }
                }
            }
        }
    }

    private fun triggerAutoReconnect() {
        if (reconnectAttempts < maxAutoReconnectAttempts) {
            reconnectAttempts++
            val delaySec = 3
            publishEvent(
                type = ConnectionEventType.RETRY_SCHEDULED,
                message = "网络连接中断，将在 ${delaySec} 秒后尝试自动重连 ($reconnectAttempts/$maxAutoReconnectAttempts)",
                attempt = reconnectAttempts,
                retryDelaySec = delaySec,
                isAuthFailure = lastFailureWasAuth
            )

            reconnectJob?.cancel()
            reconnectJob = scope.launch {
                delay(delaySec * 1000L)
                if (!isUserInitiatedDisconnect) {
                    connect(isReconnecting = true)
                }
            }
        } else {
            publishEvent(
                type = ConnectionEventType.RECONNECT_EXHAUSTED,
                message = "自动重连已达上限 ($maxAutoReconnectAttempts 次)，已暂停，需手动重试",
                attempt = maxAutoReconnectAttempts,
                isAuthFailure = lastFailureWasAuth
            )
        }
    }

    /**
     * 发布连接事件：同时进入 UI 状态流与事件记录，绝不触碰 PTY 缓冲。
     */
    private fun publishEvent(
        type: ConnectionEventType,
        message: String = "",
        attempt: Int = reconnectAttempts,
        maxAttempts: Int = maxAutoReconnectAttempts,
        retryDelaySec: Int = 0,
        isAuthFailure: Boolean = false
    ) {
        val event = ConnectionEvent(
            timestamp = System.currentTimeMillis(),
            type = type,
            message = message,
            attempt = attempt,
            maxAttempts = maxAttempts,
            retryDelaySec = retryDelaySec,
            isAuthFailure = isAuthFailure
        )
        eventLog.append(event)
        _connectionEvent.value = event
    }

    fun reconnect() {
        isUserInitiatedDisconnect = false
        reconnectAttempts = 0
        reconnectJob?.cancel()
        reconnectJob = null
        connect(isReconnecting = true)
    }

    private fun cleanupConnection() {
        try {
            outputStream?.close()
        } catch (_: Throwable) {}
        try {
            inputStream?.close()
        } catch (_: Throwable) {}
        try {
            shell?.close()
        } catch (_: Throwable) {}
        try {
            sshSession?.close()
        } catch (_: Throwable) {}
        try {
            sshClient?.disconnect()
        } catch (_: Throwable) {}
        try {
            sshClient?.close()
        } catch (_: Throwable) {}
        outputStream = null
        inputStream = null
        shell = null
        sshSession = null
        sshClient = null
    }

    fun write(data: String) {
        scope.launch(Dispatchers.IO) {
            try {
                outputStream?.write(data.toByteArray(Charsets.UTF_8))
                outputStream?.flush()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun write(bytes: ByteArray) {
        scope.launch(Dispatchers.IO) {
            try {
                outputStream?.write(bytes)
                outputStream?.flush()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun resize(cols: Int, rows: Int, widthPx: Int, heightPx: Int) {
        terminalBuffer.resize(cols, rows)
        scope.launch(Dispatchers.IO) {
            try {
                shell?.changeWindowDimensions(cols, rows, widthPx, heightPx)
            } catch (e: Exception) {
                // 忽略调整窗口尺寸的偶发异常
            }
        }
    }

    fun disconnect() {
        isUserInitiatedDisconnect = true
        reconnectJob?.cancel()
        reconnectJob = null
        connectionJob?.cancel()
        scope.launch(Dispatchers.IO) {
            cleanupConnection()
            _sessionState.value = SessionState.DISCONNECTED
            publishEvent(ConnectionEventType.MANUAL_DISCONNECT, "会话已手动断开")
        }
    }

    fun destroy() {
        isUserInitiatedDisconnect = true
        reconnectJob?.cancel()
        reconnectJob = null
        connectionJob?.cancel()
        scope.launch(Dispatchers.IO) {
            cleanupConnection()
            _sessionState.value = SessionState.DISCONNECTED
            scope.cancel()
        }
    }
}
