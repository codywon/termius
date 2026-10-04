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

    private val _statusMessage = MutableStateFlow("")
    val statusMessage: StateFlow<String> = _statusMessage

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

    fun connect(isReconnecting: Boolean = false) {
        connectionJob?.cancel()
        connectionJob = scope.launch {
            try {
                cleanupConnection()
                _sessionState.value = SessionState.CONNECTING
                _statusMessage.value = if (isReconnecting) "正在重新连接 ${host.hostname}:${host.port}..." else "正在连接到 ${host.hostname}:${host.port}..."

                if (isReconnecting) {
                    emulator.printSystemLog("\u001B[36m[TermX] 正在重新连接到 ${host.hostname}:${host.port}...\u001B[0m")
                    _renderTick.value = System.currentTimeMillis()
                }

                com.termius.clone.TermiusApplication.setupBouncyCastle()
                val client = SSHClient()
                // 允许未知主机指纹（首次连接记录并接受）
                client.addHostKeyVerifier(PromiscuousVerifier())
                client.connect(host.hostname, host.port)
                sshClient = client

                _sessionState.value = SessionState.AUTHENTICATING
                _statusMessage.value = "正在验证身份凭据..."

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
                _statusMessage.value = "已建立连接"
                reconnectAttempts = 0

                if (isReconnecting) {
                    emulator.printSystemLog("\u001B[32m[TermX] 重连成功！\u001B[0m")
                    _renderTick.value = System.currentTimeMillis()
                }

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
                startReadingLoop()

            } catch (e: Throwable) {
                e.printStackTrace()
                _sessionState.value = SessionState.ERROR
                val errMsg = e.localizedMessage ?: e.message ?: e.javaClass.simpleName
                _statusMessage.value = "连接失败: $errMsg"
                emulator.printSystemLog("\u001B[31m[TermX Mobile] 连接失败: $errMsg\u001B[0m")
                _renderTick.value = System.currentTimeMillis()

                if (!isUserInitiatedDisconnect && reconnectAttempts < maxAutoReconnectAttempts) {
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

    private fun startReadingLoop() {
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
                val userDisconnect = isUserInitiatedDisconnect
                _sessionState.value = SessionState.DISCONNECTED
                _statusMessage.value = "连接已关闭"
                cleanupConnection()
                if (!userDisconnect) {
                    triggerAutoReconnect()
                }
            }
        }
    }

    private fun triggerAutoReconnect() {
        if (reconnectAttempts < maxAutoReconnectAttempts) {
            reconnectAttempts++
            val delaySec = 3
            emulator.printSystemLog("\u001B[33m[TermX] 网络连接中断，将在 ${delaySec} 秒后尝试自动重连 (${reconnectAttempts}/${maxAutoReconnectAttempts})...\u001B[0m")
            _renderTick.value = System.currentTimeMillis()

            reconnectJob?.cancel()
            reconnectJob = scope.launch {
                delay(delaySec * 1000L)
                if (!isUserInitiatedDisconnect) {
                    connect(isReconnecting = true)
                }
            }
        } else {
            emulator.printSystemLog("\u001B[31m[TermX] 自动重连已达上限 (${maxAutoReconnectAttempts}次)，已暂停。点击右上角重连按钮可手动重试。\u001B[0m")
            _renderTick.value = System.currentTimeMillis()
        }
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
            _statusMessage.value = "已断开连接"
            emulator.printSystemLog("\u001B[33m[TermX] 会话已手动断开。\u001B[0m")
            _renderTick.value = System.currentTimeMillis()
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
