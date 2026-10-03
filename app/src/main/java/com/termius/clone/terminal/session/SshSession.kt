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
        theme = TerminalThemes.getThemeByName(host.terminalTheme)
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

    fun connect() {
        scope.launch {
            try {
                _sessionState.value = SessionState.CONNECTING
                _statusMessage.value = "正在连接到 ${host.hostname}:${host.port}..."

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
                terminalBuffer.clearScreen(2)
                emulator.processInput("\r\n\u001B[31m[TermX Mobile] 连接失败: $errMsg\u001B[0m\r\n\r\n\u001B[33m提示: 请检查主机 IP、端口以及密码/私钥是否配置正确。\u001B[0m\r\n")
                _renderTick.value = System.currentTimeMillis()
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
                // 流已关闭
            } finally {
                _sessionState.value = SessionState.DISCONNECTED
                _statusMessage.value = "连接已关闭"
            }
        }
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
        scope.launch(Dispatchers.IO) {
            try {
                outputStream?.close()
                inputStream?.close()
                shell?.close()
                sshSession?.close()
                sshClient?.disconnect()
                sshClient?.close()
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _sessionState.value = SessionState.DISCONNECTED
                _statusMessage.value = "已断开连接"
            }
        }
    }
}
