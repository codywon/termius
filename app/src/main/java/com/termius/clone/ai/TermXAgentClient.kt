package com.termius.clone.ai

import android.util.Log
import com.termius.clone.data.model.AiAgentConfig
import com.termius.clone.data.model.AiChatMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

/**
 * TermX Mobile 生产级极简 ReAct Agent 执行引擎 & OpenAI 兼容流式客户端：
 * 深度融合著名的「Pi Agent」400 行极简核心范式：
 * 
 * 1. 【纯粹 ReAct 循环】：Thought(推理思考) -> Action(工具调用) -> Observation(环境观测) -> Thought -> Final Answer；
 * 2. 【70% 上下文自动压缩】：动态感知 Token 水位，超阈值时自动触发无损记忆提炼；
 * 3. 【零外部重型库纯原生实现】：纯 HttpURLConnection + SSE 流式解析，全面兼容 DeepSeek、Qwen、OpenAI、Ollama 等；
 * 4. 【严谨 Null-Safe 协议清洗】：杜绝 Android 原生 JSONObject 将 null 解析为字符串 "null" 的经典巨坑。
 */
class TermXAgentClient(
    private val toolRegistry: TermXAgentToolRegistry
) {

    companion object {
        private const val TAG = "TermXAgentClient"

        val DEFAULT_SYSTEM_PROMPT = """
            你是一个内嵌在移动端终端管理神器「TermX Mobile」中的专业自动化 SRE 运维智能体 (TermX Ops Agent)。
            
            【意图准则与行为模式】
            1. 【日常会话与通用交流】：
               当用户打招呼（如“你好”、“在吗”）、礼貌闲聊、或询问 Linux 命令用法、网络常识时，直接以亲切专业的工程师口吻回答。
               ⚠️ 严禁无端调用工具！
            2. 【自动化运维与排障 (ReAct 循环)】：
               当用户明确提出运维目标（例如：“帮我看看服务器为什么卡”、“排查 Nginx 502”、“检查 Docker 容器为什么退出”、“帮我装个常用排查工具”、“看下磁盘空间”等）：
               按需发起工具调用：Thought(分析需求) -> Action(调用工具) -> Observation(观察结果) -> Final Answer(给出结构化报告)。
            
            【远程环境感知优先铁律 (Environment-Aware)】
            1. 在向远程主机下发任何系统特定指令（如安装软件包、管理服务、修改配置）之前，强烈建议先调用 detect_host_environment 探测操作系统环境！
            2. 严禁盲猜系统环境！例如在 Alpine Linux 上绝不执行 apt 或 systemctl，在 Debian/Ubuntu 上使用 apt，在 CentOS/RHEL 上使用 yum/dnf。
            
            【安全与人工审批规范 (Human-in-the-Loop)】
            1. 优先使用无破坏性、只读探针命令 (如 uptime, free -h, df -h, ps aux, ss -tulpn)；
            2. 当必须执行高危或状态变更命令 (如 rm -rf, iptables -F, systemctl stop, reboot) 时，底层工具箱会自动暂停并弹出人工审批卡片由用户确认。请向用户明确说明该操作的必要性与潜在影响；
            3. 若用户在审批中点击拒绝，底层会返回拒绝通知，你必须立即尊重用户决定，并构思低风险/备份替代方案。
            
            【终端屏幕日志感知 (Screen Context)】
            当用户表示“看下刚才报了什么错”、“刚才命令失败了帮我分析”时，可直接调用 read_active_terminal_screen 提取当前终端最后可见输出，免去用户手动复制的繁琐。
            
            【排障报告规范】
            - 采用规范标准的 Markdown 呈现排障结果；
            - 包含：1. 现状结论；2. 关键排障发现与数据；3. 处置建议与后续维护；
            - 涉及的代码块请注明对应语言（如 bash, yaml, json, nginx 等）。
        """.trimIndent()
    }

    /**
     * 发起 ReAct Agent 智能体心跳循环 (流式推理与工具派发)
     */
    suspend fun chatStream(
        config: AiAgentConfig,
        conversationHistory: List<AiChatMessage>,
        onChunk: (delta: String, isThinking: Boolean) -> Unit,
        onToolAction: (actionText: String) -> Unit,
        onError: (errorText: String) -> Unit,
        onComplete: (fullContent: String, reasoningContent: String) -> Unit
    ) = withContext(Dispatchers.IO) {
        if (config.apiKey.isBlank()) {
            onError("未配置 API Key。请点击右上角设置图标填入大模型 API Key（推荐使用 DeepSeek、通义千问等兼容接口）")
            return@withContext
        }

        val activeHost = toolRegistry.getActiveTargetHost()
            ?: SessionManager.currentSession?.host

        val hostContextHint = if (activeHost != null) {
            "\n\n【当前关联的远程 SSH 主机】: 【${activeHost.label}】(${activeHost.username}@${activeHost.hostname}:${activeHost.port})"
        } else {
            "\n\n【当前关联的主机】: 暂未连接具体主机 (可随时调用 list_saved_hosts 获取并连接)"
        }

        val systemContent = (if (config.customPrompt.isNotBlank()) {
            "${config.customPrompt}\n\n$DEFAULT_SYSTEM_PROMPT"
        } else {
            DEFAULT_SYSTEM_PROMPT
        }) + hostContextHint

        // 1. 初始化上下文（结合 70% 水位动态无损压缩）
        var messagesArray = ContextCompactor.buildCompactedMessagesJson(
            systemContent = systemContent,
            historyList = conversationHistory,
            config = config
        )

        val toolsJson = TermXAgentToolRegistry.getToolDefinitionsJson()

        var step = 0
        val maxSteps = 5 // 移动端优化为 5 步收敛安全循环
        val fullAccumulatedReasoning = StringBuilder()
        var finalAnswerContent = StringBuilder()

        // 2. The ReAct Loop (Thought -> Action -> Observation)
        while (step < maxSteps) {
            step++

            messagesArray = ContextCompactor.compactActiveMessages(messagesArray, config)
            val isFinalStep = (step == maxSteps)

            val requestBody = JSONObject().apply {
                put("model", config.modelName)
                put("messages", messagesArray)
                if (!isFinalStep) {
                    put("tools", toolsJson)
                    put("tool_choice", "auto")
                }
                put("temperature", config.temperature)
                put("max_tokens", config.maxTokens)
                put("stream", true)
            }

            var conn: HttpURLConnection? = null
            val toolCallsDetected = mutableListOf<JSONObject>()
            val currentStepContent = StringBuilder()
            val currentStepReasoning = StringBuilder()
            var requestSucceeded = false
            var lastException: Exception? = null

            for (attempt in 1..3) {
                try {
                    val baseUrl = config.baseUrl.trim().trimEnd('/')
                    val endpoint = if (baseUrl.endsWith("/v1")) "$baseUrl/chat/completions" else "$baseUrl/v1/chat/completions"
                    val url = URL(endpoint)

                    conn = (url.openConnection() as HttpURLConnection).apply {
                        requestMethod = "POST"
                        connectTimeout = 15_000
                        readTimeout = 60_000
                        doOutput = true
                        doInput = true
                        setRequestProperty("Authorization", "Bearer ${config.apiKey.trim()}")
                        setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                        setRequestProperty("Accept", "text/event-stream")
                    }

                    conn.outputStream.use { os ->
                        os.write(requestBody.toString().toByteArray(Charsets.UTF_8))
                        os.flush()
                    }

                    val responseCode = conn.responseCode
                    if (responseCode !in 200..299) {
                        val errorBody = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: "HTTP $responseCode"
                        Log.e(TAG, "API 请求错误 ($responseCode): $errorBody")
                        if (responseCode in listOf(502, 503, 504) && attempt < 3) {
                            onToolAction("服务端波动 ($responseCode)，正在进行第 $attempt 次自动重试...")
                            kotlinx.coroutines.delay(attempt * 800L)
                            conn.disconnect()
                            continue
                        }
                        onError("大模型服务返回异常 ($responseCode): $errorBody")
                        return@withContext
                    }

                    val reader = BufferedReader(InputStreamReader(conn.inputStream, Charsets.UTF_8))
                    var line: String? = reader.readLine()
                    val toolCallMap = mutableMapOf<Int, Triple<StringBuilder, StringBuilder, StringBuilder>>()

                    while (line != null) {
                        val trimmed = line.trim()
                        if (trimmed.startsWith("data:")) {
                            val payload = trimmed.substring(5).trim()
                            if (payload == "[DONE]") {
                                break
                            }
                            try {
                                val json = JSONObject(payload)
                                val choices = json.optJSONArray("choices")
                                if (choices != null && choices.length() > 0) {
                                    val choice = choices.getJSONObject(0)
                                    val delta = choice.optJSONObject("delta")
                                    if (delta != null) {
                                        // 思考链增量
                                        if (!delta.isNull("reasoning_content")) {
                                            val reasoningDelta = delta.optString("reasoning_content", "")
                                            if (reasoningDelta.isNotEmpty() && reasoningDelta != "null") {
                                                currentStepReasoning.append(reasoningDelta)
                                                fullAccumulatedReasoning.append(reasoningDelta)
                                                onChunk(reasoningDelta, true)
                                            }
                                        }

                                        // 正文打字机增量
                                        if (!delta.isNull("content")) {
                                            val contentDelta = delta.optString("content", "")
                                            if (contentDelta.isNotEmpty() && contentDelta != "null") {
                                                currentStepContent.append(contentDelta)
                                                onChunk(contentDelta, false)
                                            }
                                        }

                                        // 工具调用增量
                                        val deltaTools = delta.optJSONArray("tool_calls")
                                        if (deltaTools != null) {
                                            for (i in 0 until deltaTools.length()) {
                                                val t = deltaTools.getJSONObject(i)
                                                val idx = t.optInt("index", 0)
                                                val id = if (!t.isNull("id")) t.optString("id", "") else ""
                                                val func = t.optJSONObject("function")
                                                val name = if (func != null && !func.isNull("name")) func.optString("name", "") else ""
                                                val argsPart = if (func != null && !func.isNull("arguments")) func.optString("arguments", "") else ""

                                                val triple = toolCallMap.getOrPut(idx) {
                                                    Triple(StringBuilder(), StringBuilder(), StringBuilder())
                                                }
                                                if (id.isNotEmpty() && id != "null" && triple.first.isEmpty()) triple.first.append(id)
                                                if (name.isNotEmpty() && name != "null" && triple.second.isEmpty()) triple.second.append(name)
                                                if (argsPart.isNotEmpty()) triple.third.append(argsPart)
                                            }
                                        }
                                    }
                                }
                            } catch (_: Exception) {}
                        }
                        line = reader.readLine()
                    }

                    // 汇总当前步解析出来的 Action
                    for ((_, triple) in toolCallMap) {
                        val id = triple.first.toString().trim()
                        val name = triple.second.toString().trim()
                        val args = triple.third.toString().trim()
                        if (name.isNotEmpty()) {
                            toolCallsDetected.add(
                                JSONObject().apply {
                                    put("id", if (id.isNotEmpty()) id else "call_${System.currentTimeMillis()}")
                                    put("type", "function")
                                    put(
                                        "function",
                                        JSONObject().apply {
                                            put("name", name)
                                            put("arguments", if (args.isNotBlank()) args else "{}")
                                        }
                                    )
                                }
                            )
                        }
                    }

                    requestSucceeded = true
                    break
                } catch (e: Exception) {
                    lastException = e
                    conn?.disconnect()
                    if (attempt < 3 && currentStepContent.isEmpty()) {
                        onToolAction("网络连接不稳定，正在进行第 $attempt 次自动重试...")
                        kotlinx.coroutines.delay(attempt * 800L)
                    } else {
                        break
                    }
                } finally {
                    conn?.disconnect()
                }
            }

            if (!requestSucceeded) {
                Log.e(TAG, "ReAct 会话通信异常", lastException)
                onError("网络通信失败 (重试 3 次后仍未成功): ${lastException?.message}")
                return@withContext
            }

            // 3. 终答判定 (Final Answer)
            if (toolCallsDetected.isEmpty() || isFinalStep) {
                finalAnswerContent = currentStepContent
                onToolAction("")
                val rawAnswer = finalAnswerContent.toString().trim()
                val safeAnswer = if (rawAnswer.isNotEmpty() && rawAnswer != "null") {
                    rawAnswer
                } else {
                    "诊断已完成。请参考上述排障分析结果。"
                }
                onComplete(safeAnswer, fullAccumulatedReasoning.toString())
                return@withContext
            }

            // 4. 执行 Action 并获取 Observation
            val assistantMsg = JSONObject().apply {
                put("role", "assistant")
                val cleanContent = currentStepContent.toString().trim()
                put("content", if (cleanContent.isNotEmpty() && cleanContent != "null") cleanContent else "")
                val callsArray = JSONArray()
                for (t in toolCallsDetected) callsArray.put(t)
                put("tool_calls", callsArray)
            }
            messagesArray.put(assistantMsg)

            // 依次执行每个工具
            for (toolObj in toolCallsDetected) {
                val callId = toolObj.getString("id")
                val funcObj = toolObj.getJSONObject("function")
                val funcName = funcObj.getString("name")
                val funcArgs = funcObj.optString("arguments", "{}")

                val statusText = when (funcName) {
                    "list_saved_hosts" -> "🖥️ [Action] 正在获取主机列表..."
                    "select_target_host" -> "🎯 [Action] 正在切换目标主机..."
                    "detect_host_environment" -> "🔍 [Action] 正在探测远程主机系统环境..."
                    "execute_shell_command" -> "⚡ [Action] 正在执行 Shell 命令..."
                    "read_active_terminal_screen" -> "📋 [Action] 正在读取当前终端屏幕日志..."
                    "web_search" -> "🌐 [Action] 正在联网检索技术资料..."
                    else -> "⚙️ [Action] 正在调用工具: $funcName..."
                }
                onToolAction(statusText)

                val observation = toolRegistry.executeTool(funcName, funcArgs)

                messagesArray.put(
                    JSONObject().apply {
                        put("role", "tool")
                        put("tool_call_id", callId)
                        put("name", funcName)
                        put("content", observation)
                    }
                )
            }
        }

        onToolAction("")
        onComplete(currentStepContent.toString().trim(), fullAccumulatedReasoning.toString())
    }
}
