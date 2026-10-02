package com.termius.clone.terminal.tunnel

import com.termius.clone.data.model.TunnelRule
import com.termius.clone.data.model.TunnelType
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import net.schmizz.sshj.SSHClient
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

    private val _rules = MutableStateFlow<List<TunnelRule>>(
        listOf(
            TunnelRule(
                id = "rule-1",
                name = "Web API Gateway",
                type = TunnelType.LOCAL,
                localPort = 8080,
                remoteHost = "127.0.0.1",
                remotePort = 3000,
                isRunning = true,
                description = "Forward local 8080 to remote backend container"
            ),
            TunnelRule(
                id = "rule-2",
                name = "MySQL DB Tunnel",
                type = TunnelType.LOCAL,
                localPort = 3307,
                remoteHost = "127.0.0.1",
                remotePort = 3306,
                isRunning = false,
                description = "Secure access to internal MySQL database"
            ),
            TunnelRule(
                id = "rule-3",
                name = "Redis Cache Inspector",
                type = TunnelType.LOCAL,
                localPort = 6380,
                remoteHost = "127.0.0.1",
                remotePort = 6379,
                isRunning = false,
                description = "Direct localhost connection to remote Redis"
            )
        )
    )
    val rules: StateFlow<List<TunnelRule>> = _rules.asStateFlow()

    private val serverSockets = ConcurrentHashMap<String, ServerSocket>()
    private val tunnelJobs = ConcurrentHashMap<String, Job>()

    fun addRule(rule: TunnelRule) {
        _rules.value = _rules.value + rule
    }

    fun removeRule(ruleId: String) {
        stopTunnel(ruleId)
        _rules.value = _rules.value.filter { it.id != ruleId }
    }

    fun toggleTunnel(ruleId: String, sshClient: SSHClient?) {
        val rule = _rules.value.find { it.id == ruleId } ?: return
        if (rule.isRunning) {
            stopTunnel(ruleId)
        } else {
            startTunnel(rule, sshClient)
        }
    }

    fun startTunnel(rule: TunnelRule, sshClient: SSHClient?) {
        stopTunnel(rule.id)

        val job = scope.launch {
            try {
                val serverSocket = ServerSocket(rule.localPort, 50, InetAddress.getByName("127.0.0.1"))
                serverSockets[rule.id] = serverSocket

                updateRuleState(rule.id, isRunning = true)

                while (isActive && !serverSocket.isClosed) {
                    try {
                        val clientSocket = serverSocket.accept()
                        launch {
                            handleClientSocket(clientSocket, rule, sshClient)
                        }
                    } catch (e: Exception) {
                        break
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                updateRuleState(rule.id, isRunning = false)
            }
        }
        tunnelJobs[rule.id] = job
    }

    private suspend fun handleClientSocket(localSocket: Socket, rule: TunnelRule, sshClient: SSHClient?) {
        withContext(Dispatchers.IO) {
            try {
                if (sshClient != null && sshClient.isConnected && sshClient.isAuthenticated) {
                    val channel = sshClient.newDirectConnection(rule.remoteHost, rule.remotePort)
                    val localIn = localSocket.getInputStream()
                    val localOut = localSocket.getOutputStream()
                    val remoteIn = channel.inputStream
                    val remoteOut = channel.outputStream

                    val job1 = launch {
                        val buffer = ByteArray(4096)
                        var read: Int
                        while (localIn.read(buffer).also { read = it } != -1) {
                            remoteOut.write(buffer, 0, read)
                            remoteOut.flush()
                        }
                    }

                    val job2 = launch {
                        val buffer = ByteArray(4096)
                        var read: Int
                        while (remoteIn.read(buffer).also { read = it } != -1) {
                            localOut.write(buffer, 0, read)
                            localOut.flush()
                        }
                    }

                    job1.join()
                    job2.join()
                    channel.close()
                }
            } catch (e: Exception) {
                // 连接断开或异常
            } finally {
                try {
                    localSocket.close()
                } catch (e: Exception) {
                    // Ignore
                }
            }
        }
    }

    fun stopTunnel(ruleId: String) {
        tunnelJobs.remove(ruleId)?.cancel()
        try {
            serverSockets.remove(ruleId)?.close()
        } catch (e: Exception) {
            // Ignore
        }
        updateRuleState(ruleId, isRunning = false)
    }

    private fun updateRuleState(ruleId: String, isRunning: Boolean) {
        _rules.value = _rules.value.map {
            if (it.id == ruleId) it.copy(isRunning = isRunning) else it
        }
    }
}
