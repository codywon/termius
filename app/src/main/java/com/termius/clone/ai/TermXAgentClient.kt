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
               👉 【软件与服务升级/更新执行铁律 (针对“帮我升级xxx”、“更新xxx到最新版”等)】：
                  当用户提出软件、服务或开源工具的升级需求（如升级 cliproxyapi、cpa manager plus、docker 容器、nginx、node 等）：
                  ⚡ 严禁纸上谈兵！严禁要求用户先提供具体升级命令！严禁口头敷衍！
                  ⚡ 你必须立即调用 execute_shell_command 主动展开排查并闭环推进：
                     第一步【探测运行环境与部署形态】：
                        - 立即执行探测命令排查该软件在当前主机上的存在形式：
                          例如：ps aux | grep -i <app> 查看运行进程；
                          例如：docker ps -a | grep -i <app> 查看是否为 Docker 容器；
                          例如：systemctl list-unit-files | grep -i <app> 查看是否为系统服务；
                          例如：which <app>、find / -name <app> 2>/dev/null 查找文件与安装目录。
                     第二步【获取版本与升级方案】：
                        - 若为 Docker 部署：检查其 compose 文件位置或镜像 tag，执行 docker compose pull && docker compose up -d 或 docker pull；
                        - 若为 Git 源码部署：进入其目录执行 git pull 与相关构建/重启命令；
                        - 若为特定专用或私有工具 (如 cliproxyapi / cpa manager plus)：若不确定更新方式，立即调用 web_search 搜索其开源更新指南，或直接检查其安装目录下的 update.sh / package.json / 脚本；
                     第三步【执行升级与验证状态】：
                        - 明确执行升级指令，并在升级后检查进程状态、端口与 --version 输出，给用户呈现完整的升级前后状态对比报告！
               👉 【未连接目标主机时的排查引导铁律】：
                  若当前未连接具体 SSH 主机而用户提出了运维或升级诉求：
                  ⚡ 立刻调用 list_saved_hosts 获取用户保存的所有主机列表并展示，友好询问用户准备连接哪台主机进行操作。
            
            2. 【日常交流】：
               当用户仅进行常规问候或纯知识问答时，以专业、沉稳的工程师口吻作答，不调用工具。
            
            【远程环境感知优先 (Environment-Aware)】
            在下发任何系统特定指令（如包管理、服务管理）前，先调用 detect_host_environment 探测操作系统环境，严禁盲猜命令！
            
            【安全与人工审批规范 (Human-in-the-Loop)】
            高危操作 (如 rm -rf, iptables -F, systemctl stop, reboot) 底层工具会自动弹出审批卡片。
            向用户展示执行计划时，说明清楚具体路径与影响。
            
            【系统工具清单与调用协议 (双轨制)】
            系统赋予你以下 6 项核心自动化运维工具：
            1. list_saved_hosts: 列出已保存的主机
            2. select_target_host: 切换目标主机，参数格式: {"hostId": 1}
            3. detect_host_environment: 深度探测当前远程主机的 Linux 环境画像
            4. execute_shell_command: 在目标主机上执行 Shell 命令并捕获输出，参数格式: {"command": "...", "timeoutSeconds": 15}
            5. read_active_terminal_screen: 抓取当前终端屏幕最近输出内容
            6. web_search: 联网检索运维技术文档与开源手册，参数格式: {"query": "..."}

            ⚡ 工具调用方式（双轨支持）：
            - 方式 1: 标准 OpenAI Function Calling (原生 tool_calls)；
            - 方式 2 (文本 ReAct 兼容): 若当前未触发原生 tools，可直接在正文中输出以下任一格式工具块，系统将自动拦截并执行：
              ```tool:execute_shell_command
              {"command": "docker ps -a"}
              ```
              或:
              <tool_call>
              {"name": "execute_shell_command", "arguments": {"command": "docker ps -a"}}
              </tool_call>
            
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
        var useNativeTools = true // 初始尝试原生 Function Calling，遇到空响应或不兼容时自动降级

        // 2. The ReAct Loop (Thought -> Action -> Observation)
        while (step < maxSteps) {
            step++

            messagesArray = ContextCompactor.compactActiveMessages(messagesArray, config)
            val isFinalStep = (step == maxSteps)

            var toolCallsDetected = mutableListOf<JSONObject>()
            val currentStepContent = StringBuilder()
            val currentStepReasoning = StringBuilder()
            var requestSucceeded = false

            // 单步执行循环（支持原生 tools 遇到静默空返回时自适应切换为纯文本兼容重试）
            var shouldRetryWithoutTools = false
            do {
                shouldRetryWithoutTools = false
                currentStepContent.clear()
                currentStepReasoning.clear()
                toolCallsDetected.clear()

                val requestBody = JSONObject().apply {
                    put("model", config.modelName)
                    put("messages", messagesArray)
                    if (!isFinalStep && useNativeTools) {
                        put("tools", toolsJson)
                        put("tool_choice", "auto")
                    }
                    put("temperature", config.temperature)
                    put("max_tokens", config.maxTokens)
                    put("stream", true)
                }

                var conn: HttpURLConnection? = null
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
                            
                            // 针对部分严格模型 (如 DashScope/Groq/Ollama) 明确报错不支持 tools
                            if (useNativeTools && (errorBody.contains("tools") || errorBody.contains("tool_choice") || errorBody.contains("function"))) {
                                Log.w(TAG, "检测到模型明确拒绝原生 tools 参数，立即降级为 Pi-Agent 纯文本兼容模式...")
                                useNativeTools = false
                                shouldRetryWithoutTools = true
                                conn.disconnect()
                                break
                            }

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
                        val toolCallMap = mutableMapOf<Int, Triple<StringBuilder, StringBuilder, StringBuilder>>()
                        
                        // 读取首个非空行以探测是 SSE 流式协议还是整包 JSON
                        var firstLine: String? = null
                        while (true) {
                            val line = reader.readLine() ?: break
                            if (line.isNotBlank()) {
                                firstLine = line.trim()
                                break
                            }
                        }

                        if (firstLine != null) {
                            if (firstLine.startsWith("{") || (!firstLine.startsWith("data:") && !firstLine.startsWith(":"))) {
                                // 模式 A: 服务端返回了整包 JSON (部分反代或非流式兼容)
                                val remainingText = reader.use { it.readText() }
                                val fullJsonStr = firstLine + "\n" + remainingText
                                try {
                                    val rootJson = JSONObject(fullJsonStr)
                                    if (rootJson.has("error")) {
                                        val errObj = rootJson.optJSONObject("error")
                                        val errMsg = errObj?.optString("message", "") ?: rootJson.optString("error", "未知业务异常")
                                        onError("大模型服务返回业务错误: $errMsg")
                                        return@withContext
                                    }

                                    val choices = rootJson.optJSONArray("choices")
                                    if (choices != null && choices.length() > 0) {
                                        val choice = choices.getJSONObject(0)
                                        val message = choice.optJSONObject("message")
                                        if (message != null) {
                                            // 思考链
                                            val reasoning = extractContentText(message, "reasoning_content")
                                                .ifEmpty { extractContentText(message, "reasoning") }
                                            if (reasoning.isNotBlank()) {
                                                currentStepReasoning.append(reasoning)
                                                fullAccumulatedReasoning.append(reasoning)
                                                onChunk(reasoning, true)
                                            }

                                            // 正文 (支持 String 与 Content Block Array 结构)
                                            val content = extractContentText(message, "content")
                                                .ifEmpty { extractContentText(message, "text") }
                                            if (content.isNotBlank() && content != "null") {
                                                currentStepContent.append(content)
                                                fullAccumulatedContent.append(content)
                                                onChunk(content, false)
                                            }

                                            // 工具调用
                                            val toolsArr = message.optJSONArray("tool_calls")
                                            if (toolsArr != null) {
                                                for (i in 0 until toolsArr.length()) {
                                                    val t = toolsArr.getJSONObject(i)
                                                    toolCallsDetected.add(normalizeToolCall(t))
                                                }
                                            }
                                        }
                                    }
                                } catch (e: Exception) {
                                    Log.e(TAG, "解析整包 JSON 异常", e)
                                }
                            } else {
                                // 模式 B: 标准 SSE 流式解析
                                var currentLine: String? = firstLine
                                while (currentLine != null) {
                                    val trimmed = currentLine.trim()
                                    if (trimmed.startsWith("data:")) {
                                        val payload = trimmed.substring(5).trim()
                                        if (payload == "[DONE]") {
                                            break
                                        }
                                        try {
                                            val json = JSONObject(payload)
                                            if (json.has("error")) {
                                                val errObj = json.optJSONObject("error")
                                                val errMsg = errObj?.optString("message", "") ?: json.optString("error", "流式通信错误")
                                                onError("大模型服务返回业务错误: $errMsg")
                                                return@withContext
                                            }

                                            val choices = json.optJSONArray("choices")
                                            if (choices != null && choices.length() > 0) {
                                                val choice = choices.getJSONObject(0)
                                                val delta = choice.optJSONObject("delta")
                                                if (delta != null) {
                                                    // 思考链增量
                                                    val reasoningDelta = extractContentText(delta, "reasoning_content")
                                                        .ifEmpty { extractContentText(delta, "reasoning") }
                                                        .ifEmpty { extractContentText(delta, "thought") }

                                                    if (reasoningDelta.isNotEmpty() && reasoningDelta != "null") {
                                                        currentStepReasoning.append(reasoningDelta)
                                                        fullAccumulatedReasoning.append(reasoningDelta)
                                                        onChunk(reasoningDelta, true)
                                                    }

                                                    // 正文打字机增量 (兼容 String 与 Array 块)
                                                    val contentDelta = extractContentText(delta, "content")
                                                        .ifEmpty { extractContentText(delta, "text") }

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
                                                            val name = if (func != null && !func.isNull("name")) {
                                                                func.optString("name", "")
                                                            } else if (!t.isNull("name")) {
                                                                t.optString("name", "")
                                                            } else ""
                                                            val argsPart = if (func != null && !func.isNull("arguments")) {
                                                                func.optString("arguments", "")
                                                            } else if (!t.isNull("arguments")) {
                                                                t.optString("arguments", "")
                                                            } else ""

                                                            val triple = toolCallMap.getOrPut(idx) {
                                                                Triple(StringBuilder(), StringBuilder(), StringBuilder())
                                                            }
                                                            if (id.isNotEmpty() && id != "null" && triple.first.isEmpty()) triple.first.append(id)
                                                            if (name.isNotEmpty() && name != "null" && triple.second.isEmpty()) triple.second.append(name)
                                                            if (argsPart.isNotEmpty()) triple.third.append(argsPart)
                                                        }
                                                    }
                                                } else {
                                                    // 非 delta 模式 (部分代理直接吐 message)
                                                    val fallbackMsg = choice.optJSONObject("message")
                                                    if (fallbackMsg != null) {
                                                        val fbContent = extractContentText(fallbackMsg, "content")
                                                            .ifEmpty { extractContentText(fallbackMsg, "text") }
                                                        if (fbContent.isNotEmpty() && fbContent != "null") {
                                                            currentStepContent.append(fbContent)
                                                            fullAccumulatedContent.append(fbContent)
                                                            onChunk(fbContent, false)
                                                        }
                                                        val fbTools = fallbackMsg.optJSONArray("tool_calls")
                                                        if (fbTools != null) {
                                                            for (i in 0 until fbTools.length()) {
                                                                toolCallsDetected.add(normalizeToolCall(fbTools.getJSONObject(i)))
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        } catch (e: Exception) {
                                            Log.w(TAG, "解析 SSE payload 块异常: ${e.message}")
                                        }
                                    }
                                    currentLine = reader.readLine()
                                }
                            }
                        }

                        // 汇总流式解析出来的原生 Action
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

                        // 【核心增强 1: 文本 ReAct 工具块双轨自动捕获】
                        // 若原生 tools 未匹配到调用，但模型在正文输出了 ```tool:xxx 或 <tool_call>
                        if (toolCallsDetected.isEmpty() && currentStepContent.isNotEmpty()) {
                            val textTools = extractTextualToolCalls(currentStepContent.toString())
                            if (textTools.isNotEmpty()) {
                                toolCallsDetected.addAll(textTools)
                            }
                        }

                        // 【核心增强 2: Pi-Agent 自适应免 tools 降级自愈】
                        // 如果传入了原生 tools 参数，但服务端返回了彻底的空白 (无正文、无思考、无工具调用)
                        if (currentStepContent.isEmpty() && currentStepReasoning.isEmpty() && toolCallsDetected.isEmpty() && useNativeTools) {
                            Log.w(TAG, "当前模型在配置 tools 时返回彻底空白，立即自动降级为 Pi-Agent 纯文本兼容模式重试...")
                            onToolAction("模型不支持原生工具参数，正在自适应降级为纯文本工具模式重试...")
                            useNativeTools = false
                            shouldRetryWithoutTools = true
                            conn.disconnect()
                            break
                        }

                        requestSucceeded = true
                        break
                    } catch (e: Exception) {
                        lastException = e
                        conn?.disconnect()
                        if (attempt < 3 && currentStepContent.isEmpty() && toolCallsDetected.isEmpty()) {
                            onToolAction("网络连接不稳定，正在进行第 $attempt 次自动重试...")
                            kotlinx.coroutines.delay(attempt * 800L)
                        } else {
                            break
                        }
                    } finally {
                        conn?.disconnect()
                    }
                }

                if (!requestSucceeded && !shouldRetryWithoutTools) {
                    Log.e(TAG, "ReAct 会话通信异常", lastException)
                    onError("网络通信失败 (重试 3 次后仍未成功): ${lastException?.message}")
                    return@withContext
                }
            } while (shouldRetryWithoutTools)

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
                        "⚠️ 大模型服务本次未返回有效回答或工具调用。请确认您在设置中配置的模型支持当前功能（推荐使用 deepseek-chat 或通义千问兼容模型），或检查 API 额度与网络连接后再次重试。"
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
                // 仅原生 tool call 写入 assistant.tool_calls 字段
                val nativeCalls = toolCallsDetected.filter { !it.getString("id").startsWith("call_txt_") }
                if (nativeCalls.isNotEmpty()) {
                    val callsArray = JSONArray()
                    for (t in nativeCalls) callsArray.put(t)
                    put("tool_calls", callsArray)
                }
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
                        if (callId.startsWith("call_txt_")) {
                            put("role", "user")
                            put("content", "【工具执行观测结果 (Tool Observation for $funcName)】:\n$observation")
                        } else {
                            put("role", "tool")
                            put("tool_call_id", callId)
                            put("name", funcName)
                            put("content", observation)
                        }
                    }
                )
            }
        }

        onToolAction("")
        val exitAnswer = (if (fullAccumulatedContent.isNotBlank()) fullAccumulatedContent.toString() else finalAnswerContent.toString()).trim()
        onComplete(exitAnswer, fullAccumulatedReasoning.toString())
    }

    /**
     * 规范化工具调用对象（兼容顶层 name/arguments 与 function 嵌套层级）
     */
    private fun normalizeToolCall(t: JSONObject): JSONObject {
        val id = if (!t.isNull("id")) t.optString("id", "") else "call_${System.currentTimeMillis()}"
        val func = t.optJSONObject("function")
        val name = if (func != null && !func.isNull("name")) {
            func.optString("name", "")
        } else if (!t.isNull("name")) {
            t.optString("name", "")
        } else ""
        val args = if (func != null && !func.isNull("arguments")) {
            func.optString("arguments", "{}")
        } else if (!t.isNull("arguments")) {
            t.optString("arguments", "{}")
        } else "{}"

        return JSONObject().apply {
            put("id", id)
            put("type", "function")
            put("function", JSONObject().apply {
                put("name", name)
                put("arguments", args)
            })
        }
    }

    /**
     * 安全提取 content 或 reasoning 文本（兼容 String 与 JSONArray 块结构）
     */
    private fun extractContentText(jsonObj: JSONObject, key: String): String {
        if (jsonObj.isNull(key)) return ""
        val raw = jsonObj.opt(key) ?: return ""
        return when (raw) {
            is String -> raw
            is JSONArray -> {
                val sb = StringBuilder()
                for (i in 0 until raw.length()) {
                    val item = raw.optJSONObject(i)
                    if (item != null) {
                        val text = item.optString("text", "")
                        if (text.isNotEmpty()) sb.append(text)
                    } else {
                        sb.append(raw.optString(i, ""))
                    }
                }
                sb.toString()
            }
            else -> raw.toString()
        }
    }

    /**
     * 从大模型纯文本回复中正则提取文本格式的工具调用（参考 Pi-Agent 文本 ReAct 兼容机制）
     */
    private fun extractTextualToolCalls(content: String): List<JSONObject> {
        val results = mutableListOf<JSONObject>()
        val validToolNames = setOf(
            "list_saved_hosts", "select_target_host", "detect_host_environment",
            "execute_shell_command", "read_active_terminal_screen", "web_search"
        )

        // 格式 1: ```tool:execute_shell_command\n{"command": "..."}\n```
        val codeBlockRegex = Regex("```(?:tool:)?([a-zA-Z0-9_]+)\\s*\\n([\\s\\S]*?)```")
        for (match in codeBlockRegex.findAll(content)) {
            val funcName = match.groupValues[1].trim()
            val argsStr = match.groupValues[2].trim()
            if (validToolNames.contains(funcName) && argsStr.startsWith("{") && argsStr.endsWith("}")) {
                results.add(JSONObject().apply {
                    put("id", "call_txt_${System.currentTimeMillis()}_${results.size}")
                    put("type", "function")
                    put("function", JSONObject().apply {
                        put("name", funcName)
                        put("arguments", argsStr)
                    })
                })
            }
        }

        // 格式 2: <tool_call>{"name": "...", "arguments": {...}}</tool_call>
        val tagRegex = Regex("<tool_call>\\s*([\\s\\S]*?)\\s*</tool_call>")
        for (match in tagRegex.findAll(content)) {
            val jsonStr = match.groupValues[1].trim()
            try {
                val parsed = JSONObject(jsonStr)
                val name = parsed.optString("name", "")
                val argsObj = parsed.opt("arguments")
                val args = when (argsObj) {
                    is JSONObject -> argsObj.toString()
                    is String -> argsObj
                    else -> "{}"
                }
                if (validToolNames.contains(name)) {
                    results.add(JSONObject().apply {
                        put("id", "call_txt_${System.currentTimeMillis()}_${results.size}")
                        put("type", "function")
                        put("function", JSONObject().apply {
                            put("name", name)
                            put("arguments", args)
                        })
                    })
                }
            } catch (_: Exception) {}
        }

        return results
    }
}
