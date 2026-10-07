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
            
            【多模态视觉感知与图像排障 (Vision-Enabled)】
            用户可能会向你提交系统架构图、云监控大盘截图 (Prometheus / Grafana)、终端报错截屏、机房物理设备照片或运维拓扑图：
            1. 仔细观察图中的指标走势、异常报警红点、图表坐标轴、报错日志文字与代码调用链；
            2. 结合图片中的视觉线索精准定位根因，并主动调用系统工具 (如 execute_shell_command) 在服务器现场核查与修复！

            【排障报告与表格排版规范】
            1. 采用清晰工整的 Markdown 呈现排障或执行结果，包含：1. 执行总结；2. 关键指标或数据清单；3. 后续维护建议；
            2. 【表格换行强制要求】：输出表格时，表头行、分隔线 (|:---|:---|) 与每条数据行之间，必须使用独立的换行符 (\n) 分行书写，严禁将多行表格内容粘连在同一行！
        """.trimIndent()

        val VALID_TOOL_NAMES = setOf(
            "list_saved_hosts",
            "select_target_host",
            "detect_host_environment",
            "execute_shell_command",
            "read_active_terminal_screen",
            "web_search"
        )

        /**
         * 智能工具名称推断：
         * 1. 优先从 function.name / tool.name 获取；
         * 2. 当 name 为空时，从 id (如 execute_shell_command-1791345712310871728-67) 智能回溯识别！
         */
        fun resolveToolName(funcObj: JSONObject?, toolObj: JSONObject?, id: String = ""): String {
            var name = when {
                funcObj != null && !funcObj.isNull("name") -> funcObj.optString("name", "").trim()
                toolObj != null && !toolObj.isNull("name") -> toolObj.optString("name", "").trim()
                else -> ""
            }
            if (name.isNotEmpty() && name != "null") return name

            // 从 id 智能回溯匹配 (兼容 Gemini / CLIProxyAPI 网关)
            val cleanId = id.trim()
            if (cleanId.isNotEmpty() && cleanId != "null") {
                for (validTool in VALID_TOOL_NAMES) {
                    if (cleanId.startsWith(validTool, ignoreCase = true) || cleanId.contains(validTool, ignoreCase = true)) {
                        return validTool
                    }
                }
                val match = Regex("^([a-zA-Z0-9_]+)[-_]").find(cleanId)
                if (match != null) {
                    val candidate = match.groupValues[1]
                    if (candidate.isNotBlank() && candidate != "call") return candidate
                }
            }
            return ""
        }

        /**
         * 多态提取工具入参字符串：
         * 兼容 arguments、args、parameters、input、params 等多种字段，
         * 兼容 JSONObject、JSONArray、String 等多种数据类型。
         */
        fun extractToolArgumentsString(funcObj: JSONObject?, toolObj: JSONObject?): String {
            val raw = funcObj?.opt("arguments")
                ?: toolObj?.opt("arguments")
                ?: funcObj?.opt("args")
                ?: toolObj?.opt("args")
                ?: funcObj?.opt("parameters")
                ?: toolObj?.opt("parameters")
                ?: funcObj?.opt("input")
                ?: toolObj?.opt("input")
                ?: funcObj?.opt("params")
                ?: toolObj?.opt("params")
                ?: return ""

            return when (raw) {
                is JSONObject -> raw.toString()
                is JSONArray -> raw.toString()
                is String -> {
                    val trimmed = raw.trim()
                    if (trimmed == "null") "" else raw
                }
                else -> raw.toString()
            }
        }

        /**
         * 将流式聚合的 toolCallMap 安全刷入 toolCallsDetected，自带智能 ID 回溯与去重
         */
        fun flushToolCallsFromMap(
            toolCallMap: Map<Int, Triple<StringBuilder, StringBuilder, StringBuilder>>,
            toolCallsDetected: MutableList<JSONObject>
        ) {
            for ((_, triple) in toolCallMap) {
                val id = triple.first.toString().trim()
                var name = triple.second.toString().trim()
                val args = triple.third.toString().trim()

                if (name.isEmpty() && id.isNotEmpty()) {
                    name = resolveToolName(null, null, id)
                }

                if (name.isNotEmpty()) {
                    val finalId = if (id.isNotEmpty() && id != "null") id else "call_${System.currentTimeMillis()}_${toolCallsDetected.size}"
                    val exists = toolCallsDetected.any {
                        it.optString("id") == finalId || (it.optJSONObject("function")?.optString("name") == name && finalId.startsWith("call_"))
                    }
                    if (!exists) {
                        toolCallsDetected.add(
                            JSONObject().apply {
                                put("id", finalId)
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
            }
        }

        /**
         * 已执行自动化运维工具现场记录
         */
        data class ExecutedToolRecord(
            val step: Int,
            val toolName: String,
            val commandOrArgs: String,
            val observation: String
        )

        /**
         * 为纯文本模式 (Tier 2 / Tier 3 / Final Step) 平滑净化上下文，杜绝无 tools 时的 role: "tool" 与 tool_calls 导致的 400 报错或网关崩溃
         */
        fun sanitizeMessagesForTextMode(source: JSONArray, isFinalStep: Boolean = false): JSONArray {
            val clean = JSONArray()
            for (i in 0 until source.length()) {
                val msg = source.getJSONObject(i)
                val role = msg.optString("role", "")
                when (role) {
                    "tool" -> {
                        val toolName = msg.optString("name", "tool")
                        val content = msg.optString("content", "")
                        clean.put(JSONObject().apply {
                            put("role", "user")
                            put("content", "【工具执行观测结果 (Tool Observation for $toolName)】:\n$content")
                        })
                    }
                    "assistant" -> {
                        val rawContent = msg.optString("content", "")
                        val toolCalls = msg.optJSONArray("tool_calls")
                        if (toolCalls != null && toolCalls.length() > 0) {
                            val sb = StringBuilder()
                            if (rawContent.isNotBlank() && rawContent != "null") {
                                sb.append(rawContent).append("\n\n")
                            }
                            sb.append("【执行操作计划】:")
                            for (j in 0 until toolCalls.length()) {
                                val tc = toolCalls.getJSONObject(j)
                                val fn = tc.optJSONObject("function")?.optString("name") ?: ""
                                val args = tc.optJSONObject("function")?.optString("arguments") ?: ""
                                sb.append("\n- 调用工具 `$fn` 参数: $args")
                            }
                            clean.put(JSONObject().apply {
                                put("role", "assistant")
                                put("content", sb.toString().trim())
                            })
                        } else {
                            clean.put(JSONObject().apply {
                                put("role", "assistant")
                                put("content", if (rawContent.isNotBlank() && rawContent != "null") rawContent else "已规划下一步操作。")
                            })
                        }
                    }
                    else -> clean.put(msg)
                }
            }
            if (isFinalStep) {
                clean.put(JSONObject().apply {
                    put("role", "user")
                    put("content", "【系统指令】: 自动化运维工具执行阶段已完成。请基于上述所有工具返回的真实观测结果与命令输出，向用户输出完整的 Markdown 格式运维总结报告与排障结论。无需再调用任何工具。")
                })
            }
            return clean
        }

        /**
         * 汇总已执行工具现场观测记录，生成专业自动化运维排障报告
         */
        fun buildExecutedToolsReport(records: List<ExecutedToolRecord>, hostLabel: String): String {
            val sb = StringBuilder()
            sb.append("### 🚀 自动化运维执行与排障报告\n\n")
            sb.append("智能体已针对【$hostLabel】完成现场运维操作，以下为各步骤执行记录与命令观测输出：\n\n")
            sb.append("| 步骤 | 调用的运维工具 | 目标指令 / 参数 | 执行状态 |\n")
            sb.append("|:---|:---|:---|:---|\n")
            for (r in records) {
                val cleanArgs = r.commandOrArgs.replace("\n", " ").take(60)
                val statusTag = if (r.observation.startsWith("SSH 连接失败") || r.observation.startsWith("命令不能为空") || r.observation.contains("error", ignoreCase = true)) "⚠️ 需关注" else "✅ 成功"
                sb.append("| 第 ${r.step} 步 | `${r.toolName}` | `$cleanArgs` | $statusTag |\n")
            }
            sb.append("\n#### 📋 第一现场执行与观测详情\n\n")
            for (r in records) {
                sb.append("**第 ${r.step} 步 [${r.toolName}]**\n")
                sb.append("```shell\n")
                sb.append("# 执行指令 / 参数:\n")
                sb.append(r.commandOrArgs.trim()).append("\n\n")
                sb.append("# 真实输出与观测:\n")
                sb.append(r.observation.trim()).append("\n")
                sb.append("```\n\n")
            }
            sb.append("💡 **运维建议**：如需进一步针对上述服务或进程执行后续操作，您可以直接回复“继续”或下达具体指令。")
            return sb.toString().trim()
        }
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
        var useStream = true // 初始尝试 SSE 流式，遇到流式截断或 null 缺陷时自愈降级为稳定非流式
        val executedToolRecords = mutableListOf<ExecutedToolRecord>()

        // 2. The ReAct Loop (Thought -> Action -> Observation)
        while (step < maxSteps) {
            step++

            messagesArray = ContextCompactor.compactActiveMessages(messagesArray, config)
            val isFinalStep = (step == maxSteps)

            var toolCallsDetected = mutableListOf<JSONObject>()
            val currentStepContent = StringBuilder()
            val currentStepReasoning = StringBuilder()
            val stepStartContentLength = fullAccumulatedContent.length
            val stepStartReasoningLength = fullAccumulatedReasoning.length

            var stepSucceeded = false
            var lastRawSnippet = ""
            var tierAttempt = 0
            val maxTierAttempts = 3 // Tier 1: stream+tools -> Tier 2: stream+text -> Tier 3: non-stream+text (CLIProxyAPI 终极克星)

            while (tierAttempt < maxTierAttempts && !stepSucceeded) {
                tierAttempt++
                currentStepContent.clear()
                currentStepReasoning.clear()
                toolCallsDetected.clear()
                lastRawSnippet = ""

                // 回滚全量缓存，杜绝降级重试时脏数据污染
                fullAccumulatedContent.setLength(stepStartContentLength)
                fullAccumulatedReasoning.setLength(stepStartReasoningLength)

                val currentNativeTools = useNativeTools
                val currentStream = useStream

                val outgoingMessages = if (!currentNativeTools || isFinalStep) {
                    sanitizeMessagesForTextMode(messagesArray, isFinalStep)
                } else {
                    messagesArray
                }

                val requestBody = JSONObject().apply {
                    put("model", config.modelName)
                    put("messages", outgoingMessages)
                    if (!isFinalStep && currentNativeTools) {
                        put("tools", toolsJson)
                        put("tool_choice", "auto")
                    }
                    put("temperature", config.temperature)
                    put("max_tokens", config.maxTokens)
                    put("stream", currentStream)
                }

                var conn: HttpURLConnection? = null
                var attemptSucceeded = false

                for (attempt in 1..2) {
                    try {
                        val baseUrl = config.baseUrl.trim().trimEnd('/')
                        val endpoint = when {
                            baseUrl.endsWith("/chat/completions") -> baseUrl
                            baseUrl.endsWith("/v1") -> "$baseUrl/chat/completions"
                            else -> "$baseUrl/v1/chat/completions"
                        }
                        val url = URL(endpoint)

                        conn = (url.openConnection() as HttpURLConnection).apply {
                            requestMethod = "POST"
                            connectTimeout = 15_000
                            readTimeout = 90_000 // 放宽至 90 秒，保障 CLI 代理在非流式模式下有充裕时间完成子进程执行
                            doOutput = true
                            doInput = true
                            setRequestProperty("Authorization", "Bearer ${config.apiKey.trim()}")
                            setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                            setRequestProperty("Accept", if (currentStream) "text/event-stream" else "application/json")
                        }

                        conn.outputStream.use { os ->
                            os.write(requestBody.toString().toByteArray(Charsets.UTF_8))
                            os.flush()
                        }

                        val responseCode = conn.responseCode
                        if (responseCode !in 200..299) {
                            val errorBody = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: "HTTP $responseCode"
                            lastRawSnippet = errorBody
                            Log.e(TAG, "API 请求错误 ($responseCode): $errorBody")

                            // 针对模型明确拒绝 tools 参数 (400 等)，降级为纯文本模式重试
                            if (currentNativeTools && isToolParamRejected(errorBody)) {
                                Log.w(TAG, "检测到模型明确拒绝原生 tools 参数，自适应降级为纯文本模式...")
                                onToolAction("模型不支持原生工具参数，正在自适应降级为纯文本模式...")
                                useNativeTools = false
                                conn.disconnect()
                                break // 跳出 attempt，直接进入下一次 tier 降级
                            }

                            // 针对服务端不支持 stream 流式协议 (400 stream not supported 或 406 Not Acceptable)
                            if (currentStream && (errorBody.contains("stream", ignoreCase = true) || responseCode == 406)) {
                                Log.w(TAG, "检测到网关不支持 stream 流式协议，自适应降级为非流式稳定模式...")
                                onToolAction("服务端不支持流式协议，正在降级为非流式稳定模式...")
                                useStream = false
                                conn.disconnect()
                                break
                            }

                            if (responseCode in listOf(502, 503, 504) && attempt < 2) {
                                onToolAction("服务端波动 ($responseCode)，正在进行自动重试...")
                                kotlinx.coroutines.delay(800L)
                                conn.disconnect()
                                continue
                            }

                            onError("大模型服务返回异常 ($responseCode): $errorBody")
                            return@withContext
                        }

                        val reader = BufferedReader(InputStreamReader(conn.inputStream, Charsets.UTF_8))

                        if (!currentStream) {
                            // 模式 1: 显式非流式单次请求 (针对 CLIProxyAPI 流式 null bug 的绝对克星)
                            val fullBody = reader.use { it.readText() }
                            lastRawSnippet = fullBody.take(2000)
                            parseNonStreamResponse(
                                fullJsonStr = fullBody,
                                currentStepReasoning = currentStepReasoning,
                                currentStepContent = currentStepContent,
                                fullAccumulatedReasoning = fullAccumulatedReasoning,
                                fullAccumulatedContent = fullAccumulatedContent,
                                toolCallsDetected = toolCallsDetected,
                                onChunk = onChunk
                            )
                        } else {
                            // 模式 2: 流式请求 (优先 SSE，若首行是整包 JSON 则自适应解析)
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
                                    // 服务端在 stream:true 下直接回送了整包 JSON (部分反代网关行为)
                                    val remainingText = reader.use { it.readText() }
                                    val fullJsonStr = firstLine + "\n" + remainingText
                                    lastRawSnippet = fullJsonStr.take(2000)
                                    parseNonStreamResponse(
                                        fullJsonStr = fullJsonStr,
                                        currentStepReasoning = currentStepReasoning,
                                        currentStepContent = currentStepContent,
                                        fullAccumulatedReasoning = fullAccumulatedReasoning,
                                        fullAccumulatedContent = fullAccumulatedContent,
                                        toolCallsDetected = toolCallsDetected,
                                        onChunk = onChunk
                                    )
                                } else {
                                    // 标准 SSE 逐行流式解析
                                    val toolCallMap = mutableMapOf<Int, Triple<StringBuilder, StringBuilder, StringBuilder>>()
                                    val rawSnippetBuilder = StringBuilder()
                                    var currentLine: String? = firstLine

                                    try {
                                        while (currentLine != null) {
                                            val trimmed = currentLine.trim()
                                            if (rawSnippetBuilder.length < 2000) {
                                                rawSnippetBuilder.append(trimmed).append("\n")
                                            }
                                            if (trimmed.startsWith("data:")) {
                                                val payload = trimmed.substring(5).trim()
                                                if (payload == "[DONE]") {
                                                    break
                                                }
                                                if (payload.isNotEmpty()) {
                                                    parseSseChunk(
                                                        payload = payload,
                                                        currentStepReasoning = currentStepReasoning,
                                                        currentStepContent = currentStepContent,
                                                        fullAccumulatedReasoning = fullAccumulatedReasoning,
                                                        fullAccumulatedContent = fullAccumulatedContent,
                                                        toolCallMap = toolCallMap,
                                                        toolCallsDetected = toolCallsDetected,
                                                        onChunk = onChunk
                                                    )
                                                }
                                            }
                                            currentLine = reader.readLine()
                                        }
                                    } finally {
                                        lastRawSnippet = rawSnippetBuilder.toString().take(2000)
                                        // 汇总原生 Action (自带智能 ID 回溯提取与多态参数兼容)
                                        flushToolCallsFromMap(toolCallMap, toolCallsDetected)
                                    }
                                }
                            }
                        }

                        attemptSucceeded = true
                        break
                    } catch (e: Exception) {
                        conn?.disconnect()
                        if (attempt < 2 && currentStepContent.isBlank() && toolCallsDetected.isEmpty()) {
                            onToolAction("网络通信波动，正在进行第 $attempt 次自动重试...")
                            kotlinx.coroutines.delay(800L)
                        } else {
                            break
                        }
                    } finally {
                        conn?.disconnect()
                    }
                }

                // 严格清洗纯空白不可见字符 (如单个换行符 \n 或空格)，杜绝误判
                if (currentStepContent.isBlank()) {
                    currentStepContent.clear()
                }
                if (currentStepReasoning.isBlank()) {
                    currentStepReasoning.clear()
                }

                // 统一双轨文本 ReAct 工具抽取
                if (toolCallsDetected.isEmpty() && currentStepContent.isNotBlank()) {
                    val textTools = extractTextualToolCalls(currentStepContent.toString())
                    if (textTools.isNotEmpty()) {
                        toolCallsDetected.addAll(textTools)
                    }
                }

                // 核心自愈判断：是否拿到了任何有效数据 (必须使用 isNotBlank 严格过滤纯空白字符)
                val hasValidOutput = currentStepContent.isNotBlank() ||
                        currentStepReasoning.isNotBlank() ||
                        toolCallsDetected.isNotEmpty()

                if (hasValidOutput) {
                    stepSucceeded = true
                } else {
                    // 恢复全量累积缓存，杜绝无效空白字符残留污染
                    fullAccumulatedContent.setLength(stepStartContentLength)
                    fullAccumulatedReasoning.setLength(stepStartReasoningLength)

                    Log.w(TAG, "本次 Tier 请求未获取到有效非空白数据 (useNativeTools=$currentNativeTools, useStream=$currentStream)")
                    if (useNativeTools) {
                        useNativeTools = false
                        onToolAction("模型未返回有效工具调用，自适应降级为纯文本工具模式重试...")
                    } else if (useStream) {
                        useStream = false
                        onToolAction("流式传输未接收到有效数据 (CLIProxyAPI 兼容降级)，自适应切换为非流式稳定模式重试...")
                    }
                }
            }

            if (!stepSucceeded) {
                if (executedToolRecords.isNotEmpty()) {
                    val fallbackReport = buildExecutedToolsReport(executedToolRecords, activeHost?.label ?: "目标服务器")
                    onComplete(fallbackReport, fullAccumulatedReasoning.toString())
                    return@withContext
                }
                val sampleSnippet = lastRawSnippet.trim().take(2000)
                val rawSampleText = when {
                    sampleSnippet.isNotBlank() -> "\n\n【服务端原始响应采样镜像 (TermX v2.0.13 | Step $step/$maxSteps | Tier $tierAttempt)】:\n```\n$sampleSnippet\n```"
                    else -> "\n\n【响应诊断】: 服务端返回了 HTTP 200，但数据体为空 (0 字节空响应)。"
                }
                val failMsg = "⚠️ 大模型服务本次未返回有效回答或工具调用。\n已自动尝试 [原生流式] -> [文本流式] -> [非流式稳定] 三级自愈链路。$rawSampleText\n\n💡 排查建议：\n1. 若使用 CLIProxyAPI，请检查其控制台日志是否提示上游 CLI (如 Claude/Gemini/Codex) 登录失效、限流或命令超时；\n2. 检查设置中的模型名称是否与后端代理配置一致；\n3. 检查 API 额度与网络连接状态。"
                onError(failMsg)
                return@withContext
            }

            // 3. 终答判定 (Final Answer)
            if (toolCallsDetected.isEmpty() || isFinalStep) {
                finalAnswerContent = currentStepContent
                onToolAction("")
                val rawAnswer = (if (fullAccumulatedContent.isNotBlank()) fullAccumulatedContent.toString() else finalAnswerContent.toString()).trim()
                val safeAnswer = if (rawAnswer.isNotBlank() && rawAnswer != "null") {
                    rawAnswer
                } else if (fullAccumulatedReasoning.isNotBlank()) {
                    fullAccumulatedReasoning.toString().trim()
                } else if (executedToolRecords.isNotEmpty()) {
                    // 🌟 核心突破：Observation-First 自愈兜底！
                    // 若模型在多步执行中未给出最终总结文字，但此前工具已成功在服务器上执行，优先组织呈现真实运维排障报告！
                    buildExecutedToolsReport(executedToolRecords, activeHost?.label ?: "目标服务器")
                } else {
                    val sampleSnippet = lastRawSnippet.trim().take(2000)
                    val diagDetail = if (sampleSnippet.isNotBlank()) {
                        "\n\n【服务端原始响应采样镜像 (TermX v2.0.13 | Step $step/$maxSteps)】:\n```\n$sampleSnippet\n```"
                    } else {
                        "\n\n【传输诊断】: 服务端返回了 HTTP 200，但数据流中无任何有效文字 (仅接收到空白换行或 0 字节)。"
                    }
                    "⚠️ 大模型服务本次未返回有效回答或工具调用。\n已自动尝试 [原生流式] -> [文本流式] -> [非流式稳定] 三级自愈链路。$diagDetail\n\n💡 建议排查方向：\n1. 检查后端代理 (如 CLIProxyAPI) 日志，确认上游 CLI (如 Claude/Gemini/Codex) 登录认证是否有效、是否发生进程超时或被防火墙拦截；\n2. 检查设置中的模型名称是否与后端代理所支持的模型一致；\n3. 检查 API 额度与主机网络连通性。"
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
                executedToolRecords.add(
                    ExecutedToolRecord(
                        step = step,
                        toolName = funcName,
                        commandOrArgs = funcArgs,
                        observation = observation
                    )
                )

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
        val safeExitAnswer = if (exitAnswer.isNotBlank() && exitAnswer != "null") {
            exitAnswer
        } else if (executedToolRecords.isNotEmpty()) {
            buildExecutedToolsReport(executedToolRecords, activeHost?.label ?: "目标服务器")
        } else {
            exitAnswer
        }
        onComplete(safeExitAnswer, fullAccumulatedReasoning.toString())
    }

    /**
     * 解析非流式整包 JSON 响应 (兼容 choices[0].message、choices[0].text 与顶层 content/response)
     */
    private fun parseNonStreamResponse(
        fullJsonStr: String,
        currentStepReasoning: StringBuilder,
        currentStepContent: StringBuilder,
        fullAccumulatedReasoning: StringBuilder,
        fullAccumulatedContent: StringBuilder,
        toolCallsDetected: MutableList<JSONObject>,
        onChunk: (delta: String, isThinking: Boolean) -> Unit
    ) {
        val trimmed = fullJsonStr.trim()
        if (trimmed.isEmpty()) return

        try {
            if (trimmed.startsWith("{")) {
                val rootJson = JSONObject(trimmed)
                if (rootJson.has("error")) {
                    val errObj = rootJson.optJSONObject("error")
                    val errMsg = errObj?.optString("message", "") ?: rootJson.optString("error", "未知业务异常")
                    Log.w(TAG, "非流式返回业务错误: $errMsg")
                    return
                }

                var extractedReasoning = ""
                var extractedContent = ""

                val choices = rootJson.optJSONArray("choices")
                if (choices != null && choices.length() > 0) {
                    val choice = choices.getJSONObject(0)
                    val message = choice.optJSONObject("message")

                    extractedReasoning = if (message != null) {
                        extractContentText(message, "reasoning_content")
                            .ifEmpty { extractContentText(message, "reasoning") }
                            .ifEmpty { extractContentText(message, "thought") }
                    } else {
                        extractContentText(choice, "reasoning_content")
                            .ifEmpty { extractContentText(choice, "reasoning") }
                    }

                    extractedContent = if (message != null) {
                        extractContentText(message, "content")
                            .ifEmpty { extractContentText(message, "text") }
                    } else {
                        extractContentText(choice, "text")
                            .ifEmpty { extractContentText(choice, "content") }
                    }

                    val toolsArr = message?.optJSONArray("tool_calls") ?: choice.optJSONArray("tool_calls")
                    if (toolsArr != null) {
                        for (i in 0 until toolsArr.length()) {
                            val norm = normalizeToolCall(toolsArr.getJSONObject(i))
                            if (norm.optJSONObject("function")?.optString("name")?.isNotEmpty() == true) {
                                toolCallsDetected.add(norm)
                            }
                        }
                    } else {
                        val singleFunc = message?.optJSONObject("function_call") ?: choice.optJSONObject("function_call")
                        if (singleFunc != null) {
                            val norm = normalizeToolCall(singleFunc)
                            if (norm.optJSONObject("function")?.optString("name")?.isNotEmpty() == true) {
                                toolCallsDetected.add(norm)
                            }
                        }
                    }
                } else {
                    // 顶层提取 (兼容各类轻量 CLI 包装代理、Ollama、自定义反代)
                    extractedReasoning = extractContentText(rootJson, "reasoning_content")
                        .ifEmpty { extractContentText(rootJson, "reasoning") }
                        .ifEmpty { extractContentText(rootJson, "thought") }

                    extractedContent = extractContentText(rootJson, "content")
                        .ifEmpty { extractContentText(rootJson, "text") }
                        .ifEmpty { extractContentText(rootJson, "response") }
                        .ifEmpty { extractContentText(rootJson, "result") }
                        .ifEmpty { extractContentText(rootJson, "output") }
                        .ifEmpty { extractContentText(rootJson, "answer") }

                    val topTools = rootJson.optJSONArray("tool_calls")
                    if (topTools != null) {
                        for (i in 0 until topTools.length()) {
                            val norm = normalizeToolCall(topTools.getJSONObject(i))
                            if (norm.optJSONObject("function")?.optString("name")?.isNotEmpty() == true) {
                                toolCallsDetected.add(norm)
                            }
                        }
                    } else {
                        val topSingleFunc = rootJson.optJSONObject("function_call")
                        if (topSingleFunc != null) {
                            val norm = normalizeToolCall(topSingleFunc)
                            if (norm.optJSONObject("function")?.optString("name")?.isNotEmpty() == true) {
                                toolCallsDetected.add(norm)
                            }
                        }
                    }
                }

                if (extractedReasoning.isNotBlank() && extractedReasoning != "null") {
                    currentStepReasoning.append(extractedReasoning)
                    fullAccumulatedReasoning.append(extractedReasoning)
                    onChunk(extractedReasoning, true)
                }

                if (extractedContent.isNotBlank() && extractedContent != "null") {
                    currentStepContent.append(extractedContent)
                    fullAccumulatedContent.append(extractedContent)
                    onChunk(extractedContent, false)
                }
            } else if (!trimmed.startsWith("<")) {
                // 服务端以纯文本形式直接返回了大模型输出 (部分反代透传 stdout)
                currentStepContent.append(trimmed)
                fullAccumulatedContent.append(trimmed)
                onChunk(trimmed, false)
            }
        } catch (e: Exception) {
            Log.e(TAG, "解析整包 JSON 异常: ${e.message}", e)
            if (!trimmed.startsWith("<") && trimmed.isNotBlank()) {
                currentStepContent.append(trimmed)
                fullAccumulatedContent.append(trimmed)
                onChunk(trimmed, false)
            }
        }
    }

    /**
     * 解析 SSE 数据块 payload (兼容 choices[0].delta、choices[0].text 与顶层字段)
     */
    private fun parseSseChunk(
        payload: String,
        currentStepReasoning: StringBuilder,
        currentStepContent: StringBuilder,
        fullAccumulatedReasoning: StringBuilder,
        fullAccumulatedContent: StringBuilder,
        toolCallMap: MutableMap<Int, Triple<StringBuilder, StringBuilder, StringBuilder>>,
        toolCallsDetected: MutableList<JSONObject>,
        onChunk: (delta: String, isThinking: Boolean) -> Unit
    ) {
        try {
            val json = JSONObject(payload)
            if (json.has("error")) {
                val errObj = json.optJSONObject("error")
                val errMsg = errObj?.optString("message", "") ?: json.optString("error", "流式通信错误")
                Log.w(TAG, "SSE chunk 包含业务错误: $errMsg")
                return
            }

            val choices = json.optJSONArray("choices")
            if (choices != null && choices.length() > 0) {
                val choice = choices.getJSONObject(0)
                val delta = choice.optJSONObject("delta")
                val message = choice.optJSONObject("message")

                // 1. 思考链增量
                val reasoningDelta = when {
                    delta != null -> extractContentText(delta, "reasoning_content")
                        .ifEmpty { extractContentText(delta, "reasoning") }
                        .ifEmpty { extractContentText(delta, "thought") }
                    message != null -> extractContentText(message, "reasoning_content")
                        .ifEmpty { extractContentText(message, "reasoning") }
                        .ifEmpty { extractContentText(message, "thought") }
                    else -> extractContentText(choice, "reasoning_content")
                        .ifEmpty { extractContentText(choice, "reasoning") }
                }

                if (reasoningDelta.isNotEmpty() && reasoningDelta != "null") {
                    currentStepReasoning.append(reasoningDelta)
                    fullAccumulatedReasoning.append(reasoningDelta)
                    onChunk(reasoningDelta, true)
                }

                // 2. 正文打字机增量
                val contentDelta = when {
                    delta != null -> extractContentText(delta, "content")
                        .ifEmpty { extractContentText(delta, "text") }
                    message != null -> extractContentText(message, "content")
                        .ifEmpty { extractContentText(message, "text") }
                    else -> extractContentText(choice, "text")
                        .ifEmpty { extractContentText(choice, "content") }
                }

                if (contentDelta.isNotEmpty() && contentDelta != "null") {
                    currentStepContent.append(contentDelta)
                    fullAccumulatedContent.append(contentDelta)
                    onChunk(contentDelta, false)
                }

                // 3. 工具调用增量 (兼容 tool_calls 列表与单数 function_call)
                val toolsArr = delta?.optJSONArray("tool_calls")
                    ?: message?.optJSONArray("tool_calls")
                    ?: choice.optJSONArray("tool_calls")

                if (toolsArr != null) {
                    for (i in 0 until toolsArr.length()) {
                        val t = toolsArr.getJSONObject(i)
                        val idx = if (t.has("index")) t.optInt("index", i) else i
                        val id = if (!t.isNull("id")) t.optString("id", "").trim() else ""
                        val func = t.optJSONObject("function") ?: t.optJSONObject("function_call")
                        val name = resolveToolName(func, t, id)
                        val argsPart = extractToolArgumentsString(func, t)

                        val triple = toolCallMap.getOrPut(idx) {
                            Triple(StringBuilder(), StringBuilder(), StringBuilder())
                        }
                        if (id.isNotEmpty() && id != "null" && triple.first.isEmpty()) triple.first.append(id)
                        if (name.isNotEmpty() && triple.second.isEmpty()) triple.second.append(name)
                        if (argsPart.isNotEmpty()) {
                            val currentArgs = triple.third.toString().trim()
                            val isIncomingCompleteJson = argsPart.trim().startsWith("{") && argsPart.trim().endsWith("}")
                            val isCurrentCompleteJson = currentArgs.startsWith("{") && currentArgs.endsWith("}")
                            if (isIncomingCompleteJson && isCurrentCompleteJson) {
                                triple.third.setLength(0)
                                triple.third.append(argsPart.trim())
                            } else {
                                triple.third.append(argsPart)
                            }
                        }
                    }
                } else {
                    val singleFunc = delta?.optJSONObject("function_call")
                        ?: message?.optJSONObject("function_call")
                        ?: choice.optJSONObject("function_call")
                    if (singleFunc != null) {
                        val name = resolveToolName(singleFunc, null, "")
                        val argsPart = extractToolArgumentsString(singleFunc, null)
                        val triple = toolCallMap.getOrPut(0) {
                            Triple(StringBuilder(), StringBuilder(), StringBuilder())
                        }
                        if (name.isNotEmpty() && triple.second.isEmpty()) triple.second.append(name)
                        if (argsPart.isNotEmpty()) triple.third.append(argsPart)
                    }
                }
            } else {
                // 顶层提取 (兼容特殊 SSE 代理)
                val topReasoning = extractContentText(json, "reasoning_content")
                    .ifEmpty { extractContentText(json, "reasoning") }
                    .ifEmpty { extractContentText(json, "thought") }
                if (topReasoning.isNotEmpty() && topReasoning != "null") {
                    currentStepReasoning.append(topReasoning)
                    fullAccumulatedReasoning.append(topReasoning)
                    onChunk(topReasoning, true)
                }

                val topContent = extractContentText(json, "content")
                    .ifEmpty { extractContentText(json, "text") }
                    .ifEmpty { extractContentText(json, "response") }
                if (topContent.isNotEmpty() && topContent != "null") {
                    currentStepContent.append(topContent)
                    fullAccumulatedContent.append(topContent)
                    onChunk(topContent, false)
                }

                val topTools = json.optJSONArray("tool_calls")
                if (topTools != null) {
                    for (i in 0 until topTools.length()) {
                        val norm = normalizeToolCall(topTools.getJSONObject(i))
                        if (norm.optJSONObject("function")?.optString("name")?.isNotEmpty() == true) {
                            toolCallsDetected.add(norm)
                        }
                    }
                } else {
                    val topSingleFunc = json.optJSONObject("function_call")
                    if (topSingleFunc != null) {
                        val norm = normalizeToolCall(topSingleFunc)
                        if (norm.optJSONObject("function")?.optString("name")?.isNotEmpty() == true) {
                            toolCallsDetected.add(norm)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "解析 SSE payload 块异常: ${e.message}")
        }
    }

    /**
     * 判断模型返回的错误中是否包含拒绝原生 tools/function 参数的特征
     */
    private fun isToolParamRejected(errorBody: String): Boolean {
        val lower = errorBody.lowercase()
        return lower.contains("tools") ||
                lower.contains("tool_choice") ||
                lower.contains("function") ||
                lower.contains("unrecognized field") ||
                lower.contains("not supported") ||
                lower.contains("schema")
    }

    /**
     * 规范化工具调用对象（兼容顶层 name/arguments 与 function 嵌套层级，支持智能 ID 回溯与多态参数提取）
     */
    private fun normalizeToolCall(t: JSONObject): JSONObject {
        val id = if (!t.isNull("id")) t.optString("id", "").trim() else "call_${System.currentTimeMillis()}"
        val func = t.optJSONObject("function") ?: t.optJSONObject("function_call")
        val name = resolveToolName(func, t, id)
        val args = extractToolArgumentsString(func, t)

        return JSONObject().apply {
            put("id", id)
            put("type", "function")
            put("function", JSONObject().apply {
                put("name", name)
                put("arguments", if (args.isNotBlank()) args else "{}")
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
     * 从大模型纯文本回复中正则提取文本格式的工具调用（全面兼容 Pi-Agent、LangChain 及标准 JSON ReAct）
     */
    private fun extractTextualToolCalls(content: String): List<JSONObject> {
        val results = mutableListOf<JSONObject>()

        // 格式 1: ```tool:execute_shell_command\n{"command": "..."}\n``` 或 ```execute_shell_command\n{...}\n```
        val directCodeBlockRegex = Regex("```(?:tool:)?([a-zA-Z0-9_]+)\\s*\\n([\\s\\S]*?)```")
        for (match in directCodeBlockRegex.findAll(content)) {
            val label = match.groupValues[1].trim()
            val blockBody = match.groupValues[2].trim()

            if (VALID_TOOL_NAMES.contains(label) && blockBody.startsWith("{") && blockBody.endsWith("}")) {
                results.add(JSONObject().apply {
                    put("id", "call_txt_${System.currentTimeMillis()}_${results.size}")
                    put("type", "function")
                    put("function", JSONObject().apply {
                        put("name", label)
                        put("arguments", blockBody)
                    })
                })
            } else if ((label.equals("json", ignoreCase = true) || label.isEmpty()) && blockBody.startsWith("{") && blockBody.endsWith("}")) {
                // 格式 2: ```json\n{"name": "execute_shell_command", "arguments": {...}}\n```
                try {
                    val parsed = JSONObject(blockBody)
                    val toolName = parsed.optString("name", "").ifEmpty { parsed.optString("tool", "") }.ifEmpty { parsed.optString("function", "") }
                    if (VALID_TOOL_NAMES.contains(toolName)) {
                        val argsObj = parsed.opt("arguments") ?: parsed.opt("parameters") ?: parsed.opt("params")
                        val argsStr = when (argsObj) {
                            is JSONObject -> argsObj.toString()
                            is String -> argsObj
                            null -> blockBody
                            else -> argsObj.toString()
                        }
                        results.add(JSONObject().apply {
                            put("id", "call_txt_${System.currentTimeMillis()}_${results.size}")
                            put("type", "function")
                            put("function", JSONObject().apply {
                                put("name", toolName)
                                put("arguments", argsStr)
                            })
                        })
                    }
                } catch (_: Exception) {}
            }
        }

        // 格式 3: <tool_call>...</tool_call> 或 <tool>...</tool> 或 <action>...</action>
        val tagRegex = Regex("<(?:tool_call|tool|action)>\\s*([\\s\\S]*?)\\s*</(?:tool_call|tool|action)>")
        for (match in tagRegex.findAll(content)) {
            val rawTag = match.groupValues[1].trim()
            try {
                val parsed = JSONObject(rawTag)
                val toolName = parsed.optString("name", "").ifEmpty { parsed.optString("tool", "") }.ifEmpty { parsed.optString("function", "") }
                if (VALID_TOOL_NAMES.contains(toolName)) {
                    val argsObj = parsed.opt("arguments") ?: parsed.opt("parameters") ?: parsed.opt("params")
                    val argsStr = when (argsObj) {
                        is JSONObject -> argsObj.toString()
                        is String -> argsObj
                        else -> "{}"
                    }
                    results.add(JSONObject().apply {
                        put("id", "call_txt_${System.currentTimeMillis()}_${results.size}")
                        put("type", "function")
                        put("function", JSONObject().apply {
                            put("name", toolName)
                            put("arguments", argsStr)
                        })
                    })
                }
            } catch (_: Exception) {}
        }

        // 格式 4: 标准 LangChain / 原生 ReAct 格式:
        // Action: execute_shell_command
        // Action Input: {"command": "ps aux"} 或 Action Input: ps aux
        val reactRegex = Regex("Action:\\s*([a-zA-Z0-9_]+)\\s*\\n+Action Input:\\s*([\\s\\S]+?)(?:\\n*(?:Thought|Observation|Action:|$))")
        for (match in reactRegex.findAll(content)) {
            val toolName = match.groupValues[1].trim()
            val inputStr = match.groupValues[2].trim()
            if (VALID_TOOL_NAMES.contains(toolName)) {
                val finalArgs = if (inputStr.startsWith("{") && inputStr.endsWith("}")) {
                    inputStr
                } else {
                    // 若大模型直接输出了裸命令字符串，自动包装为该工具的标准 JSON 入参
                    when (toolName) {
                        "execute_shell_command" -> JSONObject().put("command", inputStr).toString()
                        "web_search" -> JSONObject().put("query", inputStr).toString()
                        "select_target_host" -> JSONObject().put("hostId", inputStr.toIntOrNull() ?: 0).toString()
                        else -> "{}"
                    }
                }
                results.add(JSONObject().apply {
                    put("id", "call_txt_${System.currentTimeMillis()}_${results.size}")
                    put("type", "function")
                    put("function", JSONObject().apply {
                        put("name", toolName)
                        put("arguments", finalArgs)
                    })
                })
            }
        }

        return results
    }
}
