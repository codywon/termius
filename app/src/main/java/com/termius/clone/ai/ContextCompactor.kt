package com.termius.clone.ai

import android.util.Log
import com.termius.clone.data.model.AiAgentConfig
import com.termius.clone.data.model.AiChatMessage
import org.json.JSONArray
import org.json.JSONObject

/**
 * 生产级运维上下文动态监控与自适应压缩器 (Context Compactor)：
 * 参考 Pi Agent 内存管理范式：
 * 1. 动态估算中英混合、Shell 脚本与长终端命令输出的 Token 水位；
 * 2. 当会话上下文占用达到设定阈值 (默认 70% contextWindow) 时，自动启动语义记忆提炼；
 * 3. 永久保留 System Prompt 与最近活跃轮次，将早期的历史对话及大体量命令输出 (Shell Observation) 压缩提炼为紧凑的结构化事实摘要；
 * 4. 彻底防止移动端 Context Window 溢出 (400 Bad Request)，实现无上限轮次的持久稳定运维会话。
 */
object ContextCompactor {

    private const val TAG = "ContextCompactor"

    fun estimateTokens(text: String): Int {
        if (text.isEmpty()) return 0
        var tokens = 0.0
        for (char in text) {
            val code = char.code
            tokens += when {
                code in 0x4E00..0x9FFF || code in 0x3400..0x4DBF -> 1.3
                char.isWhitespace() -> 0.3
                code in 33..47 || code in 58..64 || code in 91..96 || code in 123..126 -> 0.5
                code < 128 -> 0.28
                else -> 1.0
            }
        }
        return Math.ceil(tokens).toInt().coerceAtLeast(1)
    }

    fun calculateMessagesTokens(messages: JSONArray): Int {
        var total = 0
        for (i in 0 until messages.length()) {
            val item = messages.optJSONObject(i) ?: continue
            val role = item.optString("role", "")
            val toolCalls = item.optJSONArray("tool_calls")
            val reasoning = if (!item.isNull("reasoning_content")) item.optString("reasoning_content", "") else ""
            total += 4
            total += estimateTokens(role)
            total += estimateContentTokens(item.opt("content"))
            if (reasoning.isNotEmpty()) {
                total += estimateTokens(reasoning)
            }
            if (toolCalls != null) {
                total += estimateTokens(toolCalls.toString())
            }
        }
        return total
    }

    private fun estimateContentTokens(rawContent: Any?): Int {
        if (rawContent == null || rawContent == JSONObject.NULL) return 0
        return when (rawContent) {
            is String -> estimateTokens(rawContent)
            is JSONArray -> {
                var sum = 0
                for (j in 0 until rawContent.length()) {
                    val part = rawContent.optJSONObject(j)
                    if (part != null) {
                        val type = part.optString("type", "").lowercase()
                        if (type.contains("image") || part.has("image_url") || part.has("inlineData") || part.has("inline_data")) {
                            sum += 256
                        } else {
                            sum += estimateTokens(part.optString("text", ""))
                        }
                    } else {
                        sum += estimateTokens(rawContent.optString(j, ""))
                    }
                }
                sum
            }
            is JSONObject -> {
                val type = rawContent.optString("type", "").lowercase()
                if (type.contains("image") || rawContent.has("image_url")) 256
                else estimateTokens(rawContent.optString("text", ""))
            }
            else -> estimateTokens(rawContent.toString())
        }
    }

    fun isOverThreshold(estimatedTokens: Int, config: AiAgentConfig): Boolean {
        val window = config.contextWindow.coerceAtLeast(4096)
        val thresholdLimit = (window * config.compactionThreshold).toInt()
        val isOver = estimatedTokens >= thresholdLimit
        if (isOver) {
            Log.i(TAG, "上下文水位告警: 当前已消耗 ~$estimatedTokens tokens, 超过上限 $thresholdLimit (70% of $window)，触发自动压缩！")
        }
        return isOver
    }

    fun buildCompactedMessagesJson(
        systemContent: String,
        historyList: List<AiChatMessage>,
        config: AiAgentConfig
    ): JSONArray {
        val messagesJson = JSONArray()

        // 1. System Prompt (永久保留)
        messagesJson.put(
            JSONObject().apply {
                put("role", "system")
                put("content", systemContent)
            }
        )

        val validHistory = historyList.filter {
            it.role != "system" && (
                it.safeContent.isNotBlank() ||
                    it.safeReasoning.isNotBlank() ||
                    it.safeImages.isNotEmpty() ||
                    it.safeFileContextText.isNotBlank()
                )
        }
        if (validHistory.isEmpty()) {
            return messagesJson
        }

        val systemTokens = estimateTokens(systemContent) + 10
        val estimatedHistoryTokens = validHistory.sumOf {
            estimateTokens(it.safeContent) +
                estimateTokens(it.safeReasoning) +
                estimateTokens(it.safeFileContextText) +
                (it.safeImages.size * 256) +
                10
        }
        val totalEstimatedTokens = systemTokens + estimatedHistoryTokens

        val window = config.contextWindow.coerceAtLeast(4096)
        val thresholdLimit = (window * config.compactionThreshold).toInt()

        if (totalEstimatedTokens < thresholdLimit) {
            appendNormalizedMessages(messagesJson, validHistory)
            return mergeConsecutiveSameRoleMessages(messagesJson)
        }

        // 超标时保留最近 6 轮，且裁剪起点必须对齐到首条 role == "user" (绝不产生紧跟 system 的孤立 assistant)
        val preserveRecentCount = 6
        var splitIndex = (validHistory.size - preserveRecentCount).coerceAtLeast(0)
        if (splitIndex > 0 && validHistory[splitIndex].role != "user") {
            val forwardUserIdx = (splitIndex until validHistory.size).firstOrNull { validHistory[it].role == "user" }
            if (forwardUserIdx != null) {
                splitIndex = forwardUserIdx
            } else {
                val backwardUserIdx = (splitIndex - 1 downTo 0).firstOrNull { validHistory[it].role == "user" }
                if (backwardUserIdx != null) {
                    splitIndex = backwardUserIdx
                }
            }
        }

        val oldHistory = if (splitIndex > 0) validHistory.subList(0, splitIndex) else emptyList()
        val recentHistory = validHistory.subList(splitIndex, validHistory.size)

        if (oldHistory.isNotEmpty()) {
            val summarySb = StringBuilder("【历史早期运维会话记忆与已知事实摘要】:\n")
            val userQuestions = oldHistory.filter { it.role == "user" && it.safeContent.isNotBlank() }
                .map { it.safeContent.trim() }
            if (userQuestions.isNotEmpty()) {
                summarySb.append("- 用户曾关注/提问的问题: ")
                summarySb.append(userQuestions.takeLast(5).joinToString("；") { it.take(60) })
                summarySb.append("\n")
            }

            val assistantConclusions = oldHistory.filter { it.role == "assistant" && it.safeContent.isNotBlank() }
                .map { it.safeContent.trim() }
            if (assistantConclusions.isNotEmpty()) {
                summarySb.append("- 过去已得出的关键排障结论/诊断: ")
                summarySb.append(assistantConclusions.takeLast(3).joinToString("；") { it.take(80) })
                summarySb.append("\n")
            }

            messagesJson.put(
                JSONObject().apply {
                    put("role", "system")
                    put("content", summarySb.toString())
                }
            )
        }

        appendNormalizedMessages(messagesJson, recentHistory)
        return mergeConsecutiveSameRoleMessages(messagesJson)
    }

    /**
     * 规范化并填充历史消息：
     * 1. 过滤空内容；
     * 2. 安全处理多模态图文与附件文本；
     * 3. 避免紧跟 system 的孤立 assistant 造成严格网关 400。
     */
    private fun appendNormalizedMessages(messagesJson: JSONArray, list: List<AiChatMessage>) {
        val nonSystemList = list.filter {
            it.role != "system" && (
                it.safeContent.isNotBlank() ||
                    it.safeReasoning.isNotBlank() ||
                    it.safeImages.isNotEmpty() ||
                    it.safeFileContextText.isNotBlank()
                )
        }
        if (nonSystemList.isEmpty()) return

        // 防御：若过滤后首条非 system 消息仍是 assistant，尝试跳过前置孤立 assistant 直至首条 user
        val firstUserIdx = nonSystemList.indexOfFirst { it.role == "user" }
        val alignedList = if (firstUserIdx > 0) nonSystemList.subList(firstUserIdx, nonSystemList.size) else nonSystemList

        for (msg in alignedList) {
            val role = msg.role
            val rawText = if (msg.safeContent.isNotBlank()) msg.safeContent.trim() else msg.safeReasoning.trim()
            val textContent = if (msg.safeFileContextText.isNotBlank()) {
                val fileHeader = "【附加文件: ${if (msg.safeAttachedFileName.isNotBlank()) msg.safeAttachedFileName else "file"}】\n```\n${msg.safeFileContextText}\n```"
                if (rawText.isNotBlank()) {
                    "$fileHeader\n$rawText"
                } else {
                    "$fileHeader\n请帮我深入分析这份日志/配置文件的内容，排查异常或给出建议。"
                }
            } else if (rawText.isBlank() && msg.safeImages.isNotEmpty()) {
                "请帮我仔细观察分析这张图片中的架构拓扑、监控指标或异常报错，并给出详细的排障与处理方案。"
            } else {
                rawText
            }

            if (role == "user" && msg.safeImages.isNotEmpty()) {
                // 工业级标准 OpenAI Vision API 结构 (全面兼容 GPT-4o, Claude 3.5, Gemini, Qwen-VL 等)
                val contentArray = JSONArray()
                if (textContent.isNotEmpty()) {
                    contentArray.put(JSONObject().apply {
                        put("type", "text")
                        put("text", textContent)
                    })
                }
                for (imgData in msg.safeImages) {
                    val formattedUrl = if (imgData.startsWith("data:") || imgData.startsWith("http")) imgData else "data:image/jpeg;base64,$imgData"
                    contentArray.put(JSONObject().apply {
                        put("type", "image_url")
                        put("image_url", JSONObject().apply {
                            put("url", formattedUrl)
                            put("detail", "auto")
                        })
                    })
                }
                messagesJson.put(JSONObject().apply {
                    put("role", "user")
                    put("content", contentArray)
                })
            } else {
                messagesJson.put(JSONObject().apply {
                    put("role", role)
                    put("content", textContent)
                    // 思考模式模型 (DeepSeek V4 / deepseek-reasoner 等) 要求把 assistant 的历史思考链原样回传，
                    // 缺失该字段会直接被 400 拒绝。此处完整保留，发送前再由
                    // TermXAgentClient.applyReasoningPassthrough 按模型能力决定保留或剥离。
                    if (role == "assistant" && msg.safeReasoning.isNotBlank()) {
                        put("reasoning_content", msg.safeReasoning.trim())
                    }
                })
            }
        }
    }

    /**
     * 角色交替守卫 (Role Alternation Merger)：
     * 将连续相同的 user 或 assistant 消息安全合并为一条，
     * 保留 system 与并行 tool 消息的独立结构，防止 DeepSeek Reasoner / Claude / Gemini 400 报错。
     */
    fun mergeConsecutiveSameRoleMessages(source: JSONArray): JSONArray {
        val merged = JSONArray()
        for (i in 0 until source.length()) {
            val current = source.optJSONObject(i) ?: continue
            val role = current.optString("role", "")
            val last = if (merged.length() > 0) merged.optJSONObject(merged.length() - 1) else null

            if (last != null && role == last.optString("role", "") && (role == "user" || role == "assistant")) {
                mergeMessageInto(last, current)
            } else {
                merged.put(JSONObject(current.toString()))
            }
        }
        return merged
    }

    private fun mergeMessageInto(target: JSONObject, incoming: JSONObject) {
        val targetContent = target.opt("content")
        val incomingContent = incoming.opt("content")

        if (targetContent is JSONArray || incomingContent is JSONArray) {
            val combinedArray = JSONArray()
            appendContentPartsTo(combinedArray, targetContent)
            appendContentPartsTo(combinedArray, incomingContent)
            target.put("content", combinedArray)
        } else {
            val left = if (target.isNull("content")) "" else target.optString("content", "")
            val right = if (incoming.isNull("content")) "" else incoming.optString("content", "")
            val mergedText = when {
                left.isNotBlank() && right.isNotBlank() -> "$left\n\n$right"
                right.isNotBlank() -> right
                else -> left
            }
            if (mergedText.isNotEmpty() || (!target.isNull("content") || !incoming.isNull("content"))) {
                target.put("content", mergedText)
            }
        }

        // 合并 assistant 的 reasoning_content 与 tool_calls
        val leftReasoning = if (target.isNull("reasoning_content")) "" else target.optString("reasoning_content", "")
        val rightReasoning = if (incoming.isNull("reasoning_content")) "" else incoming.optString("reasoning_content", "")
        if (leftReasoning.isNotBlank() || rightReasoning.isNotBlank()) {
            val mergedReasoning = when {
                leftReasoning.isNotBlank() && rightReasoning.isNotBlank() -> "$leftReasoning\n\n$rightReasoning"
                rightReasoning.isNotBlank() -> rightReasoning
                else -> leftReasoning
            }
            target.put("reasoning_content", mergedReasoning)
        }

        val incomingToolCalls = incoming.optJSONArray("tool_calls")
        if (incomingToolCalls != null && incomingToolCalls.length() > 0) {
            val targetToolCalls = target.optJSONArray("tool_calls") ?: JSONArray().also { target.put("tool_calls", it) }
            for (j in 0 until incomingToolCalls.length()) {
                targetToolCalls.put(incomingToolCalls.get(j))
            }
        }
    }

    private fun appendContentPartsTo(dest: JSONArray, rawContent: Any?) {
        if (rawContent == null || rawContent == JSONObject.NULL) return
        when (rawContent) {
            is JSONArray -> {
                for (i in 0 until rawContent.length()) {
                    dest.put(rawContent.get(i))
                }
            }
            is String -> {
                if (rawContent.isNotBlank()) {
                    dest.put(JSONObject().apply {
                        put("type", "text")
                        put("text", rawContent)
                    })
                }
            }
            else -> {
                val text = rawContent.toString()
                if (text.isNotBlank()) {
                    dest.put(JSONObject().apply {
                        put("type", "text")
                        put("text", text)
                    })
                }
            }
        }
    }

    fun compactActiveMessages(messages: JSONArray, config: AiAgentConfig): JSONArray {
        val totalTokens = calculateMessagesTokens(messages)
        if (!isOverThreshold(totalTokens, config)) {
            return messages
        }

        // 查找体积最大的 tool observation 并安全裁剪
        val compacted = JSONArray()
        for (i in 0 until messages.length()) {
            val item = messages.getJSONObject(i)
            val role = item.optString("role", "")
            val rawContent = item.opt("content")

            if (role == "tool" && rawContent is String && rawContent.length > 3000) {
                val head = rawContent.take(1200)
                val tail = rawContent.takeLast(800)
                val trimmedContent = "$head\n\n... [中间超长日志输出由 TermX ContextCompactor 自动省略 ${rawContent.length - 2000} 字符] ...\n\n$tail"
                val newItem = JSONObject(item.toString())
                newItem.put("content", trimmedContent)
                compacted.put(newItem)
            } else {
                compacted.put(item)
            }
        }
        return compacted
    }
}
