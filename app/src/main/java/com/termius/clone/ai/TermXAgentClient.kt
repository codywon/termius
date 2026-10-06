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
import com.termius.clone.terminal.session.SessionManager

/**
 * TermX Mobile 生产级极简 ReAct Agent 执行引擎 & OpenAI 兼容流式客户端：
 * 深度融合著名的「Pi Agent」400 行极简核心范式：
 * 
 * 1. 【纯粹 ReAct 循环】：Thought(推理思考) -> Action(工具调用) -> Observation(环境观测) -> Thought -> Final Answer；
 * 2. 【70% 上下文自动压缩】：动态感知 Token 水位，超阈值时自动触发无损记忆提炼；
 * 3. 【零外部重型库纯原生实现】：纯 HttpURLConnection + SSE 流式解析，全面兼容 DeepSeek、Qwen、OpenAI、Ollama 等；
 * 4. 【行动驱动与授权执行保障】：杜绝答非所问，用户一旦确认方案立即执行工具！
 */
class TermXAgentClient(
    private val toolRegistry: TermXAgentToolRegistry
) {

    companion object {
        private const val TAG = "TermXAgentClient"

        val DEFAULT_SYSTEM_PROMPT = """
            你是一个内嵌在移动端终端管理工具「TermX Mobile」中的专业自动化 SRE 运维智能体 (TermX Ops Agent)。
            
            【核心行为模式与铁律】
            1. 【行动驱动 (Action-Driven) 与即刻执行】：
               你不是一个只会纸上谈兵的聊天机器人，你拥有真正管理远程服务器的工具权限！
               👉 当用户提出排查、体检、清理或执行需求时，优先调用对应工具获取真实数据！
               👉 【方案确认与授权即刻执行铁律】：当你此前向用户提出了方案选择（如方案 A/B、清理步骤）或索取执行确认，而用户回复了选项或肯定词（如“1方案B 2同意”、“方案B”、“同意”、“好的执行”、“确认”等）时：
                  ⚡ 你必须立即将其视为最高优先级的执行授权指令，立刻调用对应工具 (如 execute_shell_command) 执行清理或配置命令！
                  ⚡ 严禁只做口头敷衍！严禁回复“好的收到”、“诊断已完成”、“请参考上述结果”而不调用工具！必须立刻行动！
               👉 【极简模糊推进铁律 (针对“继续”、“接着做”、“开始”、“搞一下”、“处理”等)】：
                  当用户输入极简肯定词（如“继续”、“接着做”、“开始吧”、“好的处理”、“go”）时：
                  ⚡ 严禁反问用户！严禁复读套话！严禁要求用户先提供具体命令！
                  ⚡ 你必须向上回溯历史会话中用户此前最早提出的核心诉求（例如体检、清理磁盘、查网络报错、查端口服务等）：
                     - 若历史涉及磁盘清理或体检：立刻调用 execute_shell_command 现场执行 df -h 查看根分区空间，并查找占用大文件展开处置！
                     - 若历史涉及网络或服务报错：立刻调用 execute_shell_command 现场执行 systemctl --failed 或 journalctl -xe -n 30 抓取错误！
                     - 若历史暂无明确线索：立刻调用 detect_host_environment 或执行 uptime、free -m 展开主动现场诊断！
                  ⚡ 行动是唯一的回复！必须立即调用工具继续推进运维！
               👉 【引用上下文指令但历史中断时的现场重测铁律】：
                  当用户表示“按以上建议清理磁盘”、“解决上面的报错”，若历史中因中途停止而缺少具体指标或诊断数据：
                  ⚡ 绝不能直接回复空话！你应主动调用 execute_shell_command（如执行 df -h 查根目录、查看大文件或 journalctl）现场重新获取当前服务器状态并展开处置！
            
            2. 【日常交流】：
               当用户仅进行常规问候或纯知识问答时，以专业、沉稳的工程师口吻作答，不调用工具。
            
            【远程环境感知优先 (Environment-Aware)】
            在下发任何系统特定指令（如包管理、服务管理）前，先调用 detect_host_environment 探测操作系统环境，严禁盲猜命令！
            
            【安全与人工审批规范 (Human-in-the-Loop)】
            高危操作 (如 rm -rf, iptables -F, systemctl stop, reboot) 底层工具会自动弹出审批卡片。
            向用户展示执行计划时，说明清楚具体路径与影响。
            
            【排障报告与表格排版规范】
            1. 采用清晰工整的 Markdown 呈现排障或执行结果，包含：1. 执行总结；2. 关键指标或数据清单；3. 后续维护建议；
            2. 【表格换行强制要求】：输出表格时，表头行、分隔线 (|:---|:---|) 与每条数据行之间，必须使用独立的换行符 (\n) 分行书写，严禁将多行表格内容粘连在同一行！
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
        val maxSteps = 6 // 增强为 6 步收敛安全循环，确保足够完成 工具调用 -> 观测 -> 最终报告
        val fullAccumulatedReasoning = StringBuilder()
        val fullAccumulatedContent = StringBuilder()
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
                                        // 思考链增量 (兼容 reasoning_content, reasoning, thought)
                                        val reasoningDelta = if (!delta.isNull("reasoning_content")) {
                                            delta.optString("reasoning_content", "")
                                        } else if (!delta.isNull("reasoning")) {
                                            delta.optString("reasoning", "")
                                        } else if (!delta.isNull("thought")) {
                                            delta.optString("thought", "")
                                        } else ""

                                        if (reasoningDelta.isNotEmpty() && reasoningDelta != "null") {
                                            currentStepReasoning.append(reasoningDelta)
                                            fullAccumulatedReasoning.append(reasoningDelta)
                                            onChunk(reasoningDelta, true)
                                        }

                                        // 正文打字机增量 (兼容 content, text)
                                        val contentDelta = if (!delta.isNull("content")) {
                                            delta.optString("content", "")
                                        } else if (!delta.isNull("text")) {
                                            delta.optString("text", "")
                                        } else ""

                                        if (contentDelta.isNotEmpty() && contentDelta != "null") {
                                            currentStepContent.append(contentDelta)
                                            fullAccumulatedContent.append(contentDelta)
                                            onChunk(contentDelta, false)
                                        }

                                        // 工具调用增量
                                        val deltaTools = delta.optJSONArray("tool_calls")
                                        if (deltaTools != null) {
                                            for (i in 0 until deltaTools.length()) {
                                                val t = deltaTools.getJSONObject(i)
                                                val idx = if (t.has("index")) t.optInt("index", i) else i
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
                                    } else {
                                        // 非 delta 模式 (部分代理直接吐 message 或 text)
                                        val fallbackMsg = choice.optJSONObject("message")
                                        val fbContent = fallbackMsg?.optString("content", "") ?: choice.optString("text", "")
                                        if (fbContent.isNotEmpty() && fbContent != "null") {
                                            currentStepContent.append(fbContent)
                                            fullAccumulatedContent.append(fbContent)
                                            onChunk(fbContent, false)
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
                val rawAnswer = (if (fullAccumulatedContent.isNotBlank()) fullAccumulatedContent.toString() else finalAnswerContent.toString()).trim()
                val safeAnswer = if (rawAnswer.isNotEmpty() && rawAnswer != "null") {
                    rawAnswer
                } else {
                    if (fullAccumulatedReasoning.isNotBlank()) {
                        fullAccumulatedReasoning.toString().trim()
                    } else {
                        val lastUserText = conversationHistory.lastOrNull { it.role == "user" }?.content?.trim() ?: ""
                        if (lastUserText.contains("继续") || lastUserText.contains("开始") || lastUserText.length <= 4) {
                            "已接收到您的接续指令。当前已连接主机环境，建议直接点击下方快捷胶囊「🔍 系统全面体检」或输入排查需求，我将立即下发命令展开处置。"
                        } else {
                            "已接收到您的运维需求。若需要对主机进行状态诊断，建议直接点击下方「🔍 系统全面体检」或输入具体排障指令，我将立刻为您执行。"
                        }
                    }
                }
                onComplete(safeAnswer, fullAccumulatedReasoning.toString())
                return@withContext
            }

            // 4. 执行 Action 并获取 Observation
            val assistantMsg = JSONObject().apply {
                put("role", "assistant")
                val cleanContent = currentStepContent.toString().trim()
                if (cleanContent.isNotEmpty() && cleanContent != "null") {
                    put("content", cleanContent)
                } else {
                    put("content", JSONObject.NULL)
                }
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
                    "list_saved_hosts" -> "🖥️ 正在获取主机列表..."
                    "select_target_host" -> "🎯 正在切换目标主机..."
                    "detect_host_environment" -> "🔍 正在探测系统环境..."
                    "execute_shell_command" -> "⚡ 正在执行 Shell 命令..."
                    "read_active_terminal_screen" -> "📋 正在读取终端屏幕日志..."
                    "web_search" -> "🌐 正在联网检索技术资料..."
                    else -> "⚙️ 正在调用工具: $funcName..."
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
        val exitAnswer = (if (fullAccumulatedContent.isNotBlank()) fullAccumulatedContent.toString() else finalAnswerContent.toString()).trim()
        onComplete(exitAnswer, fullAccumulatedReasoning.toString())
    }
}
