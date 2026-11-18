package com.termius.clone.terminal.session

import com.termius.clone.data.model.AuthType
import com.termius.clone.data.model.HostEntity
import com.termius.clone.data.model.IdentityEntity
import com.termius.clone.data.model.TerminalThemes
import com.termius.clone.data.security.KeyVaultManager
import com.termius.clone.terminal.engine.TerminalBuffer
import com.termius.clone.terminal.engine.TerminalEmulator
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
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
import java.util.concurrent.atomic.AtomicBoolean

enum class SessionState {
    DISCONNECTED,
    CONNECTING,
    AUTHENTICATING,
    CONNECTED,
    ERROR
}

class SshSession(
    val id: String = UUID.randomUUID().toString(),
    @Volatile var host: HostEntity,
    @Volatile var identity: IdentityEntity? = null,
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
    @Volatile
    private var lastFailureWasAuth = false

    // 用于通知 Compose 界面重绘终端
    private val _renderTick = MutableStateFlow(0L)
    val renderTick: StateFlow<Long> = _renderTick

    @Volatile
    private var sshClient: SSHClient? = null
    fun getClient(): SSHClient? = sshClient

    @Volatile
    private var connectingClient: SSHClient? = null

    @Volatile
    private var sshSession: Session? = null
    @Volatile
    private var shell: Session.Shell? = null
    @Volatile
    private var outputStream: OutputStream? = null
    @Volatile
    private var inputStream: InputStream? = null

    // 重连与生命周期控制
    @Volatile
    private var isUserInitiatedDisconnect = false
    val isManuallyDisconnected: Boolean get() = isUserInitiatedDisconnect

    private var reconnectJob: Job? = null
    @Volatile
    private var reconnectAttempts = 0
    private val maxAutoReconnectAttempts = 3
    private var connectionJob: Job? = null

    // 单队列协程串行写入通道，消除多线程并发 write 乱序
    private val writeChannel = Channel<ByteArray>(Channel.UNLIMITED)

    /**
     * 连接世代号：每次 connect()/disconnect()/destroy() 递增。旧连接阻塞 IO 唤醒后若世代号已过期，
     * 则仅释放自己的局部资源，绝不污染新连接状态、发事件或触发重连。
     */
    @Volatile
    private var connectionEpoch = 0

    init {
        scope.launch(Dispatchers.IO) {
            for (bytes in writeChannel) {
                try {
                    val out = outputStream
                    if (out != null) {
                        out.write(bytes)
                        out.flush()
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    fun updateCredentials(newHost: HostEntity, newIdentity: IdentityEntity?) {
        host = newHost
        identity = newIdentity
        terminalBuffer.theme = if (newHost.terminalTheme.isNotBlank()) {
            TerminalThemes.getThemeByName(newHost.terminalTheme)
        } else {
            com.termius.clone.ui.theme.ThemeManager.currentTerminalTheme
        }
    }

    fun connect(isReconnecting: Boolean = false) {
        val prevConnecting = connectingClient
        connectingClient = null
        connectionJob?.cancel()
        // 同步递增世代号：确保旧连接即使此刻退出，也拿不到当前世代
        val epoch = ++connectionEpoch
        connectionJob = scope.launch {
            var localClient: SSHClient? = null
            var localSession: Session? = null
            var localShell: Session.Shell? = null
            var phase = "connect"

            try {
                closeQuietly(null, null, prevConnecting)
                cleanupConnection()
                if (!isActive || epoch != connectionEpoch) return@launch

                val currentHost = host
                val currentIdentity = identity

                _sessionState.value = SessionState.CONNECTING
                publishEvent(
                    type = if (isReconnecting) ConnectionEventType.RECONNECTING else ConnectionEventType.CONNECTING,
                    message = if (isReconnecting) {
                        "正在重新连接 ${currentHost.hostname}:${currentHost.port}"
                    } else {
                        "正在连接到 ${currentHost.hostname}:${currentHost.port}"
                    },
                    isAuthFailure = isReconnecting && lastFailureWasAuth
                )

                com.termius.clone.TermiusApplication.setupBouncyCastle()
                val client = SSHClient().apply {
                    connectTimeout = 15_000
                    timeout = 30_000
                    // 允许未知主机指纹（首次连接记录并接受）
                    addHostKeyVerifier(PromiscuousVerifier())
                }
                localClient = client
                connectingClient = client

                client.connect(currentHost.hostname, currentHost.port)
                if (!isActive || epoch != connectionEpoch) {
                    closeQuietly(localShell, localSession, localClient)
                    return@launch
                }

                try {
                    client.connection.keepAlive.keepAliveInterval = 20
                } catch (_: Exception) {}

                _sessionState.value = SessionState.AUTHENTICATING
                phase = "auth"

                val username = if (currentHost.authType == AuthType.IDENTITY_REF && currentIdentity != null) {
                    currentIdentity.username
                } else {
                    currentHost.username
                }

                when (currentHost.authType) {
                    AuthType.PASSWORD -> {
                        val plainPassword = KeyVaultManager.decrypt(currentHost.password)
                        client.authPassword(username, plainPassword)
                    }
                    AuthType.KEY -> {
                        val plainKey = KeyVaultManager.decrypt(currentHost.privateKey)
                        val plainPassphrase = KeyVaultManager.decrypt(currentHost.passphrase)
                        if (plainKey.isBlank()) {
                            throw IllegalArgumentException("SSH 私钥为空，请在主机设置中粘贴有效的私钥内容或切换为密码认证！")
                        }
                        val keyProvider: KeyProvider = if (plainPassphrase.isNotEmpty()) {
                            client.loadKeys(plainKey, null, PasswordUtils.createOneOff(plainPassphrase.toCharArray()))
                        } else {
                            client.loadKeys(plainKey, null, null)
                        }
                        client.authPublickey(username, keyProvider)
                    }
                    AuthType.IDENTITY_REF -> {
                        if (currentIdentity != null) {
                            val plainIdKey = KeyVaultManager.decrypt(currentIdentity.privateKey)
                            if (plainIdKey.isNotEmpty()) {
                                val plainIdPassphrase = KeyVaultManager.decrypt(currentIdentity.passphrase)
                                val keyProvider: KeyProvider = if (plainIdPassphrase.isNotEmpty()) {
                                    client.loadKeys(plainIdKey, null, PasswordUtils.createOneOff(plainIdPassphrase.toCharArray()))
                                } else {
                                    client.loadKeys(plainIdKey, null, null)
                                }
                                client.authPublickey(username, keyProvider)
                            } else {
                                val plainIdPassword = KeyVaultManager.decrypt(currentIdentity.password)
                                client.authPassword(username, plainIdPassword)
                            }
                        } else {
                            val plainPassword = KeyVaultManager.decrypt(currentHost.password)
                            client.authPassword(username, plainPassword)
                        }
                    }
                }

                if (!isActive || epoch != connectionEpoch) {
                    closeQuietly(localShell, localSession, localClient)
                    return@launch
                }

                if (!client.isAuthenticated) {
                    throw IllegalStateException("SSH 身份凭据校验失败，请检查用户名/密码或私钥！")
                }

                // 启动 PTY Shell 会话
                val session = client.startSession()
                localSession = session
                if (!isActive || epoch != connectionEpoch) {
                    closeQuietly(localShell, localSession, localClient)
                    return@launch
                }

                session.allocatePTY(
                    "xterm-256color",
                    terminalBuffer.cols,
                    terminalBuffer.rows,
                    terminalBuffer.cols * 8,
                    terminalBuffer.rows * 16,
                    emptyMap()
                )
                if (!isActive || epoch != connectionEpoch) {
                    closeQuietly(localShell, localSession, localClient)
                    return@launch
                }

                val sh = session.startShell()
                localShell = sh
                if (!isActive || epoch != connectionEpoch) {
                    closeQuietly(localShell, localSession, localClient)
                    return@launch
                }

                if (connectingClient === client) {
                    connectingClient = null
                }
                sshClient = client
                sshSession = session
                shell = sh
                outputStream = sh.outputStream
                inputStream = sh.inputStream
                emulator.resetParserState()

                _sessionState.value = SessionState.CONNECTED
                reconnectAttempts = 0
                lastFailureWasAuth = false
                phase = "shell"
                publishEvent(
                    type = if (isReconnecting) ConnectionEventType.RECONNECT_SUCCESS else ConnectionEventType.CONNECTED,
                    message = if (isReconnecting) "重连成功" else "已建立连接"
                )

                // 在 connectionJob 内部直接运行读循环
                val readBuffer = ByteArray(8192)
                val stream = sh.inputStream
                try {
                    while (isActive && epoch == connectionEpoch) {
                        val read = stream.read(readBuffer)
                        if (read == -1) break
                        if (read > 0) {
                            emulator.processInput(readBuffer, 0, read)
                            scheduleRender()
                        }
                    }
                } catch (e: Throwable) {
                    if (e is CancellationException) throw e
                    // 正常流断开交由 finally 收尾
                }
            } catch (e: Throwable) {
                closeQuietly(localShell, localSession, localClient)
                if (e is CancellationException) throw e
                if (!isActive || epoch != connectionEpoch) return@launch

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
            } finally {
                if (connectingClient === localClient) {
                    connectingClient = null
                }
                if (epoch == connectionEpoch) {
                    val wasShell = (phase == "shell")
                    cleanupConnection()
                    if (wasShell) {
                        val userDisconnect = isUserInitiatedDisconnect
                        _sessionState.value = SessionState.DISCONNECTED
                        if (!userDisconnect) {
                            publishEvent(ConnectionEventType.DISCONNECTED, "网络连接中断，连接已关闭")
                            triggerAutoReconnect()
                        }
                    }
                } else {
                    closeQuietly(localShell, localSession, localClient)
                }
            }
        }
    }

    private val isRenderPending = AtomicBoolean(false)
    private var lastRenderTimestamp = 0L

    private fun scheduleRender() {
        val now = System.currentTimeMillis()
        if (now - lastRenderTimestamp >= 30L) {
            lastRenderTimestamp = now
            _renderTick.value = now
        } else {
            if (isRenderPending.compareAndSet(false, true)) {
                scope.launch(Dispatchers.Default) {
                    delay(25L)
                    isRenderPending.set(false)
                    val t = System.currentTimeMillis()
                    lastRenderTimestamp = t
                    _renderTick.value = t
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

    private fun closeQuietly(
        targetShell: Session.Shell?,
        targetSession: Session?,
        targetClient: SSHClient?
    ) {
        try {
            targetShell?.close()
        } catch (_: Throwable) {}
        try {
            targetSession?.close()
        } catch (_: Throwable) {}
        try {
            targetClient?.disconnect()
        } catch (_: Throwable) {}
        try {
            targetClient?.close()
        } catch (_: Throwable) {}
    }

    private fun cleanupConnection() {
        try {
            outputStream?.close()
        } catch (_: Throwable) {}
        try {
            inputStream?.close()
        } catch (_: Throwable) {}
        closeQuietly(shell, sshSession, sshClient)
        outputStream = null
        inputStream = null
        shell = null
        sshSession = null
        sshClient = null
    }

    fun write(data: String) {
        write(data.toByteArray(Charsets.UTF_8))
    }

    fun write(bytes: ByteArray) {
        if (bytes.isEmpty()) return
        writeChannel.trySend(bytes)
    }

    fun resize(cols: Int, rows: Int, widthPx: Int, heightPx: Int) {
        terminalBuffer.resize(cols, rows)
        scope.launch(Dispatchers.IO) {
            try {
                shell?.changeWindowDimensions(cols, rows, widthPx, heightPx)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // 忽略调整窗口尺寸的偶发异常
            }
        }
    }

    fun disconnect() {
        isUserInitiatedDisconnect = true
        val disconnectEpoch = ++connectionEpoch
        reconnectJob?.cancel()
        reconnectJob = null
        val pending = connectingClient
        connectingClient = null
        connectionJob?.cancel()
        scope.launch(Dispatchers.IO) {
            closeQuietly(null, null, pending)
            if (disconnectEpoch == connectionEpoch) {
                cleanupConnection()
                _sessionState.value = SessionState.DISCONNECTED
                publishEvent(ConnectionEventType.MANUAL_DISCONNECT, "会话已手动断开")
            }
        }
    }

    fun destroy() {
        isUserInitiatedDisconnect = true
        ++connectionEpoch
        reconnectJob?.cancel()
        reconnectJob = null
        val pending = connectingClient
        connectingClient = null
        connectionJob?.cancel()
        writeChannel.close()
        scope.launch(Dispatchers.IO) {
            closeQuietly(null, null, pending)
            cleanupConnection()
            _sessionState.value = SessionState.DISCONNECTED
            scope.cancel()
        }
    }
}
