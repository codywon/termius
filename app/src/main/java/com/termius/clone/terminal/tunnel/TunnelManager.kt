package com.termius.clone.terminal.tunnel

import com.termius.clone.data.model.TunnelRule
import com.termius.clone.data.model.TunnelType
import com.termius.clone.terminal.session.SshSession
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import net.schmizz.sshj.SSHClient
import java.io.DataInputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap

data class ActiveTunnelState(
    val rule: TunnelRule,
    val isRunning: Boolean,
    val activeConnections: Int = 0,
    val errorMessage: String? = null
)

object TunnelManager {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _rules = MutableStateFlow<List<TunnelRule>>(emptyList())
    val rules: StateFlow<List<TunnelRule>> = _rules.asStateFlow()

    private val serverSockets = ConcurrentHashMap<String, ServerSocket>()
    private val tunnelJobs = ConcurrentHashMap<String, Job>()
    private val tunnelHostIds = ConcurrentHashMap<String, Long>()

    fun addRule(rule: TunnelRule) {
        _rules.update { it + rule }
    }

    fun removeRule(ruleId: String) {
        stopTunnel(ruleId)
        _rules.update { list -> list.filter { it.id != ruleId } }
    }

    fun toggleTunnel(ruleId: String, sshClient: SSHClient?) {
        val rule = _rules.value.find { it.id == ruleId } ?: return
        if (rule.isRunning) {
            stopTunnel(ruleId)
        } else {
            startTunnel(rule, sshClient)
        }
    }

    fun toggleTunnel(ruleId: String, session: SshSession?) {
        val rule = _rules.value.find { it.id == ruleId } ?: return
        if (rule.isRunning) {
            stopTunnel(ruleId)
        } else {
            startTunnel(rule, session)
        }
    }

    fun startTunnel(rule: TunnelRule, sshClient: SSHClient?) {
        startTunnelInternal(rule, boundHostId = rule.hostId) { sshClient }
    }

    fun startTunnel(rule: TunnelRule, session: SshSession?) {
        val boundHostId = rule.hostId ?: session?.host?.id
        startTunnelInternal(rule, boundHostId = boundHostId) { session?.getClient() }
    }

    private fun startTunnelInternal(
        rule: TunnelRule,
        boundHostId: Long?,
        clientProvider: () -> SSHClient?
    ) {
        stopTunnel(rule.id)

        if (rule.type == TunnelType.REMOTE) {
            // 当前未支持远程端口转发，立即复位状态避免 UI 假运行
            updateRuleState(rule.id, isRunning = false)
            return
        }

        if (boundHostId != null) {
            tunnelHostIds[rule.id] = boundHostId
        }

        val job = scope.launch {
            var serverSocket: ServerSocket? = null
            try {
                serverSocket = ServerSocket(rule.localPort, 50, InetAddress.getByName("127.0.0.1"))
                serverSockets[rule.id] = serverSocket
                updateRuleState(rule.id, isRunning = true)

                while (isActive && !serverSocket.isClosed) {
                    val clientSocket = try {
                        serverSocket.accept()
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        break
                    }
                    launch {
                        handleClientSocket(clientSocket, rule, clientProvider)
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                runCatching { serverSocket?.close() }
                if (serverSocket != null) {
                    serverSockets.remove(rule.id, serverSocket)
                }
                val currentJob = coroutineContext[Job]
                val isStillOwner = currentJob == null || tunnelJobs[rule.id] === currentJob
                if (isStillOwner) {
                    tunnelJobs.remove(rule.id)
                    tunnelHostIds.remove(rule.id)
                    updateRuleState(rule.id, isRunning = false)
                }
            }
        }
        tunnelJobs[rule.id] = job
    }

    private suspend fun handleClientSocket(
        localSocket: Socket,
        rule: TunnelRule,
        clientProvider: () -> SSHClient?
    ) {
        withContext(Dispatchers.IO) {
            try {
                val sshClient = clientProvider()
                if (sshClient == null || !sshClient.isConnected || !sshClient.isAuthenticated) {
                    return@withContext
                }

                val targetEndpoint = when (rule.type) {
                    TunnelType.LOCAL -> rule.remoteHost to rule.remotePort
                    TunnelType.DYNAMIC -> {
                        negotiateSocks5Connect(localSocket) ?: return@withContext
                    }
                    TunnelType.REMOTE -> return@withContext
                }

                val channel = sshClient.newDirectConnection(targetEndpoint.first, targetEndpoint.second)
                try {
                    if (rule.type == TunnelType.DYNAMIC) {
                        sendSocks5Success(localSocket.getOutputStream())
                    }

                    val localIn = localSocket.getInputStream()
                    val localOut = localSocket.getOutputStream()
                    val remoteIn = channel.inputStream
                    val remoteOut = channel.outputStream

                    coroutineScope {
                        val job1 = launch {
                            try {
                                copyLocalToRemote(localIn, remoteOut)
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                runCatching { channel.close() }
                                throw e
                            }
                        }

                        val job2 = launch {
                            try {
                                copyRemoteToLocal(remoteIn, localOut, localSocket)
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                runCatching { localSocket.close() }
                                throw e
                            }
                        }

                        job1.invokeOnCompletion { cause ->
                            if (cause != null && cause !is CancellationException) {
                                job2.cancel()
                            }
                        }
                        job2.invokeOnCompletion { cause ->
                            if (cause != null && cause !is CancellationException) {
                                job1.cancel()
                            }
                        }
                    }
                } finally {
                    runCatching { channel.close() }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // 单条转发连接断开或异常，不影响监听协程
            } finally {
                runCatching { localSocket.close() }
            }
        }
    }

    private suspend fun copyLocalToRemote(localIn: InputStream, remoteOut: OutputStream) {
        val buffer = ByteArray(8192)
        while (currentCoroutineContext().isActive) {
            val read = localIn.read(buffer)
            if (read == -1) {
                runCatching { remoteOut.close() }
                break
            }
            if (read > 0) {
                remoteOut.write(buffer, 0, read)
                remoteOut.flush()
            }
        }
    }

    private suspend fun copyRemoteToLocal(
        remoteIn: InputStream,
        localOut: OutputStream,
        localSocket: Socket
    ) {
        val buffer = ByteArray(8192)
        while (currentCoroutineContext().isActive) {
            val read = remoteIn.read(buffer)
            if (read == -1) {
                runCatching { localSocket.shutdownOutput() }
                break
            }
            if (read > 0) {
                localOut.write(buffer, 0, read)
                localOut.flush()
            }
        }
    }

    /**
     * 轻量 SOCKS5 (RFC 1928) 握手与 CONNECT 目标解析，仅支持无认证 (0x00) + CONNECT (0x01)
     */
    private fun negotiateSocks5Connect(localSocket: Socket): Pair<String, Int>? {
        val prevTimeout = runCatching { localSocket.soTimeout }.getOrDefault(0)
        return try {
            localSocket.soTimeout = 10_000
            val dis = DataInputStream(localSocket.getInputStream())
            val out = localSocket.getOutputStream()

            val ver = dis.readUnsignedByte()
            if (ver != 0x05) return null
            val nMethods = dis.readUnsignedByte()
            val methods = ByteArray(nMethods)
            dis.readFully(methods)

            if (!methods.contains(0x00.toByte())) {
                out.write(byteArrayOf(0x05, 0xFF.toByte()))
                out.flush()
                return null
            }
            out.write(byteArrayOf(0x05, 0x00))
            out.flush()

            val reqVer = dis.readUnsignedByte()
            val cmd = dis.readUnsignedByte()
            dis.readUnsignedByte() // RSV
            val atyp = dis.readUnsignedByte()
            if (reqVer != 0x05) return null

            val host: String? = when (atyp) {
                0x01 -> {
                    val addr = ByteArray(4)
                    dis.readFully(addr)
                    InetAddress.getByAddress(addr).hostAddress
                }
                0x03 -> {
                    val len = dis.readUnsignedByte()
                    val domain = ByteArray(len)
                    dis.readFully(domain)
                    String(domain, Charsets.UTF_8)
                }
                0x04 -> {
                    val addr = ByteArray(16)
                    dis.readFully(addr)
                    InetAddress.getByAddress(addr).hostAddress
                }
                else -> null
            }

            if (host.isNullOrBlank()) {
                out.write(byteArrayOf(0x05, 0x08, 0x00, 0x01, 0, 0, 0, 0, 0, 0))
                out.flush()
                return null
            }

            val port = dis.readUnsignedShort()
            if (cmd != 0x01) {
                out.write(byteArrayOf(0x05, 0x07, 0x00, 0x01, 0, 0, 0, 0, 0, 0))
                out.flush()
                return null
            }

            localSocket.soTimeout = prevTimeout
            host to port
        } catch (_: Exception) {
            null
        }
    }

    private fun sendSocks5Success(out: OutputStream) {
        out.write(byteArrayOf(0x05, 0x00, 0x00, 0x01, 0, 0, 0, 0, 0, 0))
        out.flush()
    }

    fun stopTunnel(ruleId: String) {
        tunnelJobs.remove(ruleId)?.cancel()
        tunnelHostIds.remove(ruleId)
        runCatching { serverSockets.remove(ruleId)?.close() }
        updateRuleState(ruleId, isRunning = false)
    }

    fun stopTunnelsForHost(hostId: Long) {
        val ruleIds = _rules.value
            .filter { it.hostId == hostId || tunnelHostIds[it.id] == hostId }
            .map { it.id }
        for (ruleId in ruleIds) {
            stopTunnel(ruleId)
        }
    }

    private fun updateRuleState(ruleId: String, isRunning: Boolean) {
        _rules.update { list ->
            list.map { if (it.id == ruleId) it.copy(isRunning = isRunning) else it }
        }
    }
}
