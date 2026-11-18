package com.termius.clone.ai

import android.content.Context
import android.util.Log
import com.termius.clone.data.local.AppDatabase
import com.termius.clone.data.model.AuthType
import com.termius.clone.data.model.DangerousActionRequest
import com.termius.clone.data.model.HostEntity
import com.termius.clone.data.model.SystemEnvInfo
import com.termius.clone.data.security.KeyVaultManager
import com.termius.clone.terminal.session.SessionManager
import com.termius.clone.terminal.session.SshSession
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.connection.channel.direct.Session
import net.schmizz.sshj.transport.verification.PromiscuousVerifier
import net.schmizz.sshj.userauth.password.PasswordUtils
import org.json.JSONArray
import org.json.JSONObject
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.CodingErrorAction
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.coroutineContext

/**
 * TermX Mobile AI SRE 智能体运维工具箱 (Tool Registry)
 * 具备：主机感知与自连、远程环境探测、安全命令执行(带人工审批)、屏幕上下文读取与双轨联网搜索。
 */
class TermXAgentToolRegistry(
    private val context: Context,
    private val db: AppDatabase,
    // 人工审批回调闸口 (挂起协程直到用户在 UI 点击批准或拒绝)
    var approvalRequester: (suspend (DangerousActionRequest) -> Boolean)? = null
) {

    companion object {
        private const val TAG = "TermXToolRegistry"
        private const val DEFAULT_SESSION_KEY = "__default__"

        /**
         * UTF-8 流式解码器：保留跨块拆分的多字节字符残留，彻底消除中文/Emoji 跨读取边界变 U+FFFD ()
         */
        internal fun readStreamWithUtf8Decoder(stream: InputStream, maxChars: Int): String {
            val decoder = Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPLACE)
                .onUnmappableCharacter(CodingErrorAction.REPLACE)
            val readBuf = ByteArray(2048)
            val carry = ByteArray(4)
            var carryLen = 0
            val sb = StringBuilder()
            var truncated = false
            val charBuf = CharBuffer.allocate(2048)

            fun decodeBytes(bytes: ByteArray, length: Int, endOfInput: Boolean) {
                val inBuf = ByteBuffer.wrap(bytes, 0, length)
                while (true) {
                    charBuf.clear()
                    val result = decoder.decode(inBuf, charBuf, endOfInput)
                    charBuf.flip()
                    if (!truncated && charBuf.hasRemaining()) {
                        val remainingCap = maxChars - sb.length
                        if (remainingCap > 0) {
                            val toAppend = minOf(remainingCap, charBuf.remaining())
                            sb.append(charBuf, 0, toAppend)
                            if (toAppend < charBuf.remaining() || sb.length >= maxChars) {
                                truncated = true
                            }
                        } else {
                            truncated = true
                        }
                    }
                    if (result.isUnderflow) {
                        carryLen = inBuf.remaining()
                        if (carryLen > 0) {
                            inBuf.get(carry, 0, carryLen)
                        }
                        break
                    }
                    if (result.isOverflow) continue
                    break
                }
            }

            try {
                while (true) {
                    val combined = ByteArray(carryLen + readBuf.size)
                    if (carryLen > 0) {
                        System.arraycopy(carry, 0, combined, 0, carryLen)
                    }
                    val n = stream.read(combined, carryLen, readBuf.size)
                    if (n == -1) {
                        if (carryLen > 0) {
                            decodeBytes(carry, carryLen, endOfInput = true)
                        } else {
                            decodeBytes(ByteArray(0), 0, endOfInput = true)
                        }
                        charBuf.clear()
                        decoder.flush(charBuf)
                        charBuf.flip()
                        if (!truncated && charBuf.hasRemaining()) {
                            val remainingCap = maxChars - sb.length
                            if (remainingCap > 0) {
                                sb.append(charBuf, 0, minOf(remainingCap, charBuf.remaining()))
                            }
                        }
                        break
                    }
                    val totalBytes = carryLen + n
                    carryLen = 0
                    decodeBytes(combined, totalBytes, endOfInput = false)
                }
            } catch (_: Exception) {
                // 超时或协程取消强制关闭流时返回已解码部分内容
            }

            if (truncated) {
                val head = sb.substring(0, minOf(sb.length, maxChars / 2))
                return "$head\n... [输出超过限制已截断] ..."
            }
            return sb.toString()
        }

        /**
         * 导出 OpenAI 兼容的 Function Calling 声明元数据
         */
        fun getToolDefinitionsJson(): JSONArray {
            return JSONArray().apply {
                // 1. 列出可用主机
                put(JSONObject().apply {
                    put("type", "function")
                    put("function", JSONObject().apply {
                        put("name", "list_saved_hosts")
                        put("description", "列出用户在 TermX 中保存的所有远程 SSH 主机列表，包括主机别名、IP地址、端口、用户名及当前是否处于活跃连接状态。")
                        put("parameters", JSONObject().apply {
                            put("type", "object")
                            put("properties", JSONObject())
                        })
                    })
                })

                // 2. 选择目标主机
                put(JSONObject().apply {
                    put("type", "function")
                    put("function", JSONObject().apply {
                        put("name", "select_target_host")
                        put("description", "选择或切换当前准备进行运维诊断的目标 SSH 主机。")
                        put("parameters", JSONObject().apply {
                            put("type", "object")
                            put("properties", JSONObject().apply {
                                put("hostId", JSONObject().apply {
                                    put("type", "integer")
                                    put("description", "目标主机的唯一数据库 ID (可通过 list_saved_hosts 获取)")
                                })
                            })
                            put("required", JSONArray().apply { put("hostId") })
                        })
                    })
                })

                // 3. 自动探测目标主机的系统环境
                put(JSONObject().apply {
                    put("type", "function")
                    put("function", JSONObject().apply {
                        put("name", "detect_host_environment")
                        put("description", "自动深度探测远程主机的系统环境画像，包括 Linux 发行版(如 Ubuntu/Debian/CentOS/Alpine)、架构(x86_64/arm64)、内核版本、可用包管理器(apt/yum/apk等)及已安装的关键服务(docker/nginx/systemd)。后续生成命令前强烈建议先调用此工具！")
                        put("parameters", JSONObject().apply {
                            put("type", "object")
                            put("properties", JSONObject())
                        })
                    })
                })

                // 4. 执行 Shell 命令 (带高危拦截与人工审批)
                put(JSONObject().apply {
                    put("type", "function")
                    put("function", JSONObject().apply {
                        put("name", "execute_shell_command")
                        put("description", "在当前选中的远程主机上执行一条 bash/shell 运维命令，并捕获输出结果(stdout/stderr)及退出码。注意：高危命令(如 rm -rf、关机、清空防火墙等)系统会自动暂停并弹出人工审批卡片由用户确认。")
                        put("parameters", JSONObject().apply {
                            put("type", "object")
                            put("properties", JSONObject().apply {
                                put("command", JSONObject().apply {
                                    put("type", "string")
                                    put("description", "要执行的 shell 命令语句，如 'uptime && free -h', 'docker ps', 'df -h' 等")
                                })
                                put("timeoutSeconds", JSONObject().apply {
                                    put("type", "integer")
                                    put("description", "执行超时时间(秒)，默认 15 秒")
                                })
                            })
                            put("required", JSONArray().apply { put("command") })
                        })
                    })
                })

                // 5. 读取当前终端屏幕文本
                put(JSONObject().apply {
                    put("type", "function")
                    put("function", JSONObject().apply {
                        put("name", "read_active_terminal_screen")
                        put("description", "直接抓取并读取用户当前前台终端屏幕的最近 50~100 行可见输出内容。当用户说'帮我看看刚才终端报什么错'时，调用此工具即可免去用户手动复制的麻烦。")
                        put("parameters", JSONObject().apply {
                            put("type", "object")
                            put("properties", JSONObject())
                        })
                    })
                })

                // 6. 联网技术检索
                put(JSONObject().apply {
                    put("type", "function")
                    put("function", JSONObject().apply {
                        put("name", "web_search")
                        put("description", "遇到未知的 Linux 报错代码、Docker 容器异常、Nginx 复杂语法或特定开源工具手册时，进行实时公网搜索。")
                        put("parameters", JSONObject().apply {
                            put("type", "object")
                            put("properties", JSONObject().apply {
                                put("query", JSONObject().apply {
                                    put("type", "string")
                                    put("description", "检索关键词，例如 'nginx 502 bad gateway upstream timed out' 或 'docker exit code 137'")
                                })
                            })
                            put("required", JSONArray().apply { put("query") })
                        })
                    })
                })
            }
        }
    }

    // 多会话隔离：按 AI sessionId 记录显式 select_target_host 选定的主机 ID（绝不锁定全局或锁死终端回退主机）
    private val selectedHostIdBySession = ConcurrentHashMap<String, Long>()
    private val cachedEnvInfoByHostId = ConcurrentHashMap<Long, SystemEnvInfo>()

    @Volatile
    var currentAiSessionId: String? = null

    private fun currentSessionKey(): String =
        currentAiSessionId?.takeIf { it.isNotBlank() } ?: DEFAULT_SESSION_KEY

    fun getActiveTargetHost(): HostEntity? {
        val key = currentSessionKey()
        val explicitId = selectedHostIdBySession[key] ?: return null
        val host = try {
            runBlocking(Dispatchers.IO) {
                db.hostDao().getHostById(explicitId)
            }
        } catch (_: Exception) {
            null
        }
        if (host == null) {
            selectedHostIdBySession.remove(key)
        }
        return host
    }

    fun setActiveTargetHost(host: HostEntity?) {
        val key = currentSessionKey()
        if (host == null) {
            selectedHostIdBySession.remove(key)
        } else {
            selectedHostIdBySession[key] = host.id
            cachedEnvInfoByHostId.remove(host.id)
        }
    }

    suspend fun resolveTargetHost(): HostEntity? = ensureTargetHost()

    /**
     * 核心工具执行派发器
     */
    suspend fun executeTool(name: String, argsJson: String): String = withContext(Dispatchers.IO) {
        val args = try {
            JSONObject(argsJson)
        } catch (_: Exception) {
            JSONObject()
        }

        try {
            when (name) {
                "list_saved_hosts" -> handleListSavedHosts()
                "select_target_host" -> handleSelectTargetHost(args.optLong("hostId", 0L))
                "detect_host_environment" -> handleDetectHostEnvironment()
                "execute_shell_command" -> {
                    val cmd = args.optString("command", "")
                        .ifBlank { args.optString("cmd", "") }
                        .ifBlank { args.optString("shell", "") }
                        .ifBlank { args.optString("script", "") }
                        .ifBlank { args.optString("input", "") }
                        .ifBlank { if (!argsJson.trim().startsWith("{")) argsJson.trim() else "" }
                        .trim()
                    val timeout = args.optInt("timeoutSeconds", 15)
                    handleExecuteShellCommand(cmd, timeout)
                }
                "read_active_terminal_screen" -> handleReadActiveTerminalScreen()
                "web_search" -> {
                    val query = args.optString("query", "")
                        .ifBlank { args.optString("q", "") }
                        .ifBlank { args.optString("keyword", "") }
                        .ifBlank { args.optString("input", "") }
                        .ifBlank { if (!argsJson.trim().startsWith("{")) argsJson.trim() else "" }
                        .trim()
                    handleWebSearch(query)
                }
                else -> "未知工具名称: $name"
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "执行工具 $name 异常", e)
            "工具执行出错: ${e.message}"
        }
    }

    private suspend fun handleListSavedHosts(): String {
        val hosts = db.hostDao().getAllHostsSync()
        if (hosts.isEmpty()) {
            return "当前 TermX 中未保存任何 SSH 主机。请先在主机列表添加服务器。"
        }

        val activeSessions = SessionManager.sessions.value
        val sb = StringBuilder("TermX 已保存的主机列表 (共 ${hosts.size} 台):\n")
        hosts.forEach { h ->
            val isConnected = activeSessions.any { it.host.id == h.id }
            val statusTag = if (isConnected) " [🟢 当前活跃连接]" else ""
            sb.append("- [ID: ${h.id}] 【${h.label}】: ${h.username}@${h.hostname}:${h.port} (分组: ${h.groupName})$statusTag\n")
        }
        return sb.toString()
    }

    private suspend fun handleSelectTargetHost(hostId: Long): String {
        if (hostId <= 0L) {
            return "错误: 请提供有效的主机 ID。"
        }
        val host = db.hostDao().getHostById(hostId)
            ?: return "错误: 未找到 ID 为 $hostId 的主机配置。"

        selectedHostIdBySession[currentSessionKey()] = host.id
        cachedEnvInfoByHostId.remove(host.id)
        return "已成功切换目标主机为: 【${host.label}】(${host.username}@${host.hostname}:${host.port})。后续命令将在此主机执行。"
    }

    private suspend fun ensureTargetHost(): HostEntity? {
        val key = currentSessionKey()
        val explicitHostId = selectedHostIdBySession[key]
        if (explicitHostId != null) {
            val latestHost = db.hostDao().getHostById(explicitHostId)
            if (latestHost != null) {
                return latestHost
            }
            // 主机已被删除时自动清除无效绑定，回退实时终端探测
            selectedHostIdBySession.remove(key)
        }

        // 未显式指定主机时每次实时读取前台活跃会话主机（绝不反写缓存锁死）
        val foregroundSession = SessionManager.currentSession
        if (foregroundSession != null) {
            return foregroundSession.host
        }

        // 其次实时读取任意已连接会话的主机
        val anyActive = SessionManager.sessions.value.firstOrNull()
        if (anyActive != null) {
            return anyActive.host
        }

        // 再次回退数据库中第一台主机
        return db.hostDao().getAllHostsSync().firstOrNull()
    }

    private suspend fun handleDetectHostEnvironment(): String {
        val host = ensureTargetHost()
            ?: return "无法探测环境: 当前没有可用或已选中的主机。请先通过 list_saved_hosts 选择目标主机。"

        // 执行复合轻量环境探测脚本
        val probeScript = """
            echo "---OS---" && (cat /etc/os-release 2>/dev/null || cat /etc/issue 2>/dev/null || uname -s)
            echo "---KERNEL---" && uname -r -m
            echo "---PKGMGR---" && which apt yum dnf apk pacman zypper 2>/dev/null
            echo "---SERVICES---" && which docker nginx systemctl service containerd 2>/dev/null
        """.trimIndent()

        val probeResult = runRawSshCommand(host, probeScript, 10)
        if (probeResult.startsWith("SSH 连接失败") || probeResult.startsWith("执行超时") || probeResult.startsWith("执行异常")) {
            return "环境探测失败: $probeResult"
        }

        val lines = probeResult.lines()
        var osName = "Linux"
        var osVersion = ""
        var arch = "x86_64"
        var kernel = ""
        var pkgMgr = "apt"
        val tools = mutableListOf<String>()

        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.startsWith("PRETTY_NAME=")) {
                osName = trimmed.substringAfter("PRETTY_NAME=").removeSurrounding("\"")
            } else if (trimmed.startsWith("VERSION_ID=")) {
                osVersion = trimmed.substringAfter("VERSION_ID=").removeSurrounding("\"")
            }
            if (trimmed.contains("x86_64") || trimmed.contains("aarch64") || trimmed.contains("arm64")) {
                val parts = trimmed.split(" ")
                if (parts.size >= 2) {
                    kernel = parts[0]
                    arch = parts[1]
                }
            }
            if (trimmed.endsWith("/apt")) pkgMgr = "apt"
            else if (trimmed.endsWith("/yum")) pkgMgr = "yum"
            else if (trimmed.endsWith("/dnf")) pkgMgr = "dnf"
            else if (trimmed.endsWith("/apk")) pkgMgr = "apk (Alpine)"
            else if (trimmed.endsWith("/pacman")) pkgMgr = "pacman (Arch)"

            if (trimmed.endsWith("/docker")) tools.add("docker")
            if (trimmed.endsWith("/nginx")) tools.add("nginx")
            if (trimmed.endsWith("/systemctl")) tools.add("systemd")
        }

        val info = SystemEnvInfo(
            osName = osName,
            osVersion = osVersion,
            architecture = arch,
            kernelVersion = kernel,
            packageManager = pkgMgr,
            installedTools = tools,
            rawSummary = probeResult.take(600)
        )
        cachedEnvInfoByHostId[host.id] = info

        return """
            【目标主机环境画像探测完成】:
            - 主机标签: ${host.label} (${host.username}@${host.hostname}:${host.port})
            - 操作系统: $osName $osVersion
            - 系统架构: $arch (内核: $kernel)
            - 首选包管理器: $pkgMgr
            - 已发现的核心服务与环境: ${if (tools.isNotEmpty()) tools.joinToString(", ") else "未发现常规服务"}
            ⚠️ 提示：请根据上述 ${pkgMgr} 和 ${osName} 环境生成对应的兼容命令！
        """.trimIndent()
    }

    private suspend fun handleExecuteShellCommand(command: String, timeoutSeconds: Int): String {
        if (command.isBlank()) return "命令不能为空。"

        val host = ensureTargetHost()
            ?: return "无法执行命令: 未指定目标主机，且本地没有任何可用主机。请先添加主机。"

        // ==========================================
        // 核心安全护栏：Human-in-the-Loop 审批拦截
        // ==========================================
        val dangerousReq = DangerousActionGuard.checkCommandRisk(command, host.label)
        if (dangerousReq != null) {
            val requester = approvalRequester
            if (requester != null) {
                val isApproved = requester(dangerousReq)
                if (!isApproved) {
                    return """
                        🚨 [安全拦截] 该命令涉及高危操作 (${dangerousReq.riskLevel})，已触发人工审批。
                        ❌ 用户选择【拒绝执行】此命令！
                        原因说明: ${dangerousReq.reason}
                        建议: 请向用户汇报操作已终止，并提供更安全、无破坏性或备份后的替代方案。
                    """.trimIndent()
                }
            } else {
                return "🚨 [安全拦截] 命令被标记为高危 (${dangerousReq.riskLevel})，但当前缺少审批组件，已拒绝执行。"
            }
        }

        // 执行命令
        return runRawSshCommand(host, command, timeoutSeconds.coerceIn(3, 60))
    }

    private suspend fun handleReadActiveTerminalScreen(): String {
        val session = SessionManager.currentSession
            ?: return "当前前台没有活跃的终端会话。请先打开并连接一个终端。"

        val buffer = session.terminalBuffer
        val rows = buffer.rows
        val sb = StringBuilder()
        val readLines = minOf(rows, 60)
        val startRow = maxOf(0, rows - readLines)

        for (r in startRow until rows) {
            val lineText = buffer.getLineString(r).trimEnd()
            if (lineText.isNotBlank()) {
                sb.append(lineText).append("\n")
            }
        }

        val result = sb.toString().trim()
        val terminalBlock = if (result.isNotBlank()) {
            "【当前终端屏幕内容 (最后 $readLines 行可见输出)】:\n```\n$result\n```"
        } else {
            "当前终端屏幕内容为空或尚未产生输出。"
        }

        // 客户端连接事件单独成节，让模型知道刚断过连，但不会把 TermX 提示误当成服务器输出
        val recentEvents = session.eventLog.snapshot().takeLast(6)
        if (recentEvents.isEmpty()) return terminalBlock

        val eventFormatter = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US)
        val eventLines = recentEvents.joinToString("\n") { event ->
            val time = eventFormatter.format(java.util.Date(event.timestamp))
            val detail = if (event.message.isNotBlank()) "  ${event.message}" else ""
            "- $time  ${event.type.name}$detail"
        }
        return "$terminalBlock\n\n【客户端连接状态（TermX 自身事件，非远端服务器输出）】:\n$eventLines"
    }

    private suspend fun handleWebSearch(query: String): String {
        if (query.isBlank()) return "搜索关键词不能为空。"
        val searchRes = WebSearchHelper.search(query, maxResults = 3)
        if (searchRes.items.isEmpty()) {
            return "联网搜索未能找到相关结果 (来源: ${searchRes.source}, 原因: ${searchRes.error ?: "无数据"})。"
        }

        val sb = StringBuilder("【联网检索结果 (来源: ${searchRes.source})】:\n")
        searchRes.items.forEachIndexed { idx, item ->
            sb.append("${idx + 1}. [${item.title}](${item.url})\n")
            sb.append("   摘要: ${item.snippet}\n")
        }
        return sb.toString()
    }

    /**
     * 底层 SSH 执行实现：优先复用活跃的 SshSession 连接通道，若无则建立独立安全通道
     */
    private suspend fun runRawSshCommand(host: HostEntity, command: String, timeoutSec: Int): String = withContext(Dispatchers.IO) {
        coroutineContext.ensureActive()

        // 1. 尝试复用已连接会话的 SSHClient
        val existingSession = SessionManager.sessions.value.find { it.host.id == host.id }
        val activeClient = existingSession?.getClient()

        if (activeClient != null && activeClient.isConnected && activeClient.isAuthenticated) {
            return@withContext executeViaClient(activeClient, command, timeoutSec)
        }

        // 2. 建立临时连接
        com.termius.clone.TermiusApplication.setupBouncyCastle()
        val client = SSHClient().apply {
            connectTimeout = 15_000
            timeout = 30_000
            addHostKeyVerifier(PromiscuousVerifier())
        }
        val cancelHandle = coroutineContext[Job]?.invokeOnCompletion { cause ->
            if (cause != null) {
                try {
                    client.disconnect()
                } catch (_: Exception) {}
            }
        }

        try {
            coroutineContext.ensureActive()
            client.connect(host.hostname, host.port)
            val username = host.username
            val hostPassword = KeyVaultManager.decrypt(host.password)
            val hostPrivateKey = KeyVaultManager.decrypt(host.privateKey)
            val hostPassphrase = KeyVaultManager.decrypt(host.passphrase)

            when (host.authType) {
                AuthType.PASSWORD -> client.authPassword(username, hostPassword)
                AuthType.KEY -> {
                    if (hostPrivateKey.isNotBlank()) {
                        val kp = if (hostPassphrase.isNotBlank()) {
                            client.loadKeys(hostPrivateKey, null, PasswordUtils.createOneOff(hostPassphrase.toCharArray()))
                        } else {
                            client.loadKeys(hostPrivateKey, null, null)
                        }
                        client.authPublickey(username, kp)
                    } else {
                        client.authPassword(username, hostPassword)
                    }
                }
                AuthType.IDENTITY_REF -> {
                    val identity = host.identityId?.let { db.identityDao().getIdentityById(it) }
                    if (identity != null) {
                        val authUser = identity.username.ifBlank { username }
                        val idPassword = KeyVaultManager.decrypt(identity.password)
                        val idPrivateKey = KeyVaultManager.decrypt(identity.privateKey)
                        val idPassphrase = KeyVaultManager.decrypt(identity.passphrase)
                        if (idPrivateKey.isNotBlank()) {
                            val kp = if (idPassphrase.isNotBlank()) {
                                client.loadKeys(idPrivateKey, null, PasswordUtils.createOneOff(idPassphrase.toCharArray()))
                            } else {
                                client.loadKeys(idPrivateKey, null, null)
                            }
                            client.authPublickey(authUser, kp)
                        } else {
                            client.authPassword(authUser, idPassword)
                        }
                    } else {
                        client.authPassword(username, hostPassword)
                    }
                }
            }

            coroutineContext.ensureActive()
            return@withContext executeViaClient(client, command, timeoutSec)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "SSH 连接失败: ${host.hostname}", e)
            return@withContext "SSH 连接失败 (${host.hostname}:${host.port}): ${e.message}"
        } finally {
            cancelHandle?.dispose()
            try {
                client.disconnect()
            } catch (_: Exception) {}
        }
    }

    private suspend fun executeViaClient(client: SSHClient, command: String, timeoutSec: Int): String {
        val safeTimeoutSec = timeoutSec.coerceIn(1, 300)
        return try {
            coroutineScope {
                coroutineContext.ensureActive()
                val session = client.startSession()
                var cmd: Session.Command? = null
                val timedOut = AtomicBoolean(false)
                val cancelHandle = coroutineContext[Job]?.invokeOnCompletion { cause ->
                    if (cause != null) {
                        closeSessionAndCommand(cmd, session)
                    }
                }

                try {
                    val execCmd = session.exec(command)
                    cmd = execCmd
                    val startMs = System.currentTimeMillis()

                    val watchdog = launch(Dispatchers.IO) {
                        delay(safeTimeoutSec * 1000L)
                        if (timedOut.compareAndSet(false, true)) {
                            closeSessionAndCommand(execCmd, session)
                        }
                    }

                    // 并发读取 stdout 与 stderr，彻底消除串行读流导致的管道窗口死锁
                    val stdoutDeferred = async(Dispatchers.IO) {
                        readStreamWithUtf8Decoder(execCmd.inputStream, 8192)
                    }
                    val stderrDeferred = async(Dispatchers.IO) {
                        readStreamWithUtf8Decoder(execCmd.errorStream, 4096)
                    }

                    val stdout = stdoutDeferred.await()
                    val stderr = stderrDeferred.await()

                    if (!timedOut.get()) {
                        withContext(Dispatchers.IO) {
                            try {
                                val elapsedMs = System.currentTimeMillis() - startMs
                                val remainingMs = (safeTimeoutSec * 1000L - elapsedMs).coerceAtLeast(200L)
                                execCmd.join(remainingMs, TimeUnit.MILLISECONDS)
                            } catch (e: CancellationException) {
                                throw e
                            } catch (_: Exception) {}
                        }
                    }
                    watchdog.cancel()
                    coroutineContext.ensureActive()

                    val exitStatus = execCmd.exitStatus
                    val elapsedTotal = System.currentTimeMillis() - startMs
                    if (timedOut.get() || (exitStatus == null && elapsedTotal >= safeTimeoutSec * 1000L - 100L)) {
                        val sb = StringBuilder("执行超时: 命令在 ${safeTimeoutSec} 秒内未完成，已强制中断。")
                        if (stdout.isNotBlank()) {
                            sb.append("\n--- [STDOUT (超时前部分输出)] ---\n").append(stdout.trim())
                        }
                        if (stderr.isNotBlank()) {
                            sb.append("\n--- [STDERR (超时前部分输出)] ---\n").append(stderr.trim())
                        }
                        return@coroutineScope sb.toString().trim()
                    }

                    val exitCode = exitStatus ?: -1
                    val sb = StringBuilder()
                    sb.append("退出码: $exitCode\n")
                    if (stdout.isNotBlank()) {
                        sb.append("--- [STDOUT] ---\n").append(stdout.trim()).append("\n")
                    }
                    if (stderr.isNotBlank()) {
                        sb.append("--- [STDERR] ---\n").append(stderr.trim()).append("\n")
                    }
                    if (stdout.isBlank() && stderr.isBlank()) {
                        sb.append(if (exitCode == 0) "(命令执行成功，无任何文本输出)" else "(命令执行结束，无文本输出)")
                    }
                    sb.toString().trim()
                } finally {
                    cancelHandle?.dispose()
                    closeSessionAndCommand(cmd, session)
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            "执行异常: ${e.message}"
        }
    }

    private fun closeSessionAndCommand(cmd: Session.Command?, session: Session?) {
        try {
            cmd?.close()
        } catch (_: Exception) {}
        try {
            session?.close()
        } catch (_: Exception) {}
    }
}
