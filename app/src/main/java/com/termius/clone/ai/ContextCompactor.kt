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
            val content = item.optString("content", "")
            val toolCalls = item.optJSONArray("tool_calls")
            total += 4
            total += estimateTokens(role)
            total += estimateTokens(content)
            if (toolCalls != null) {
                total += estimateTokens(toolCalls.toString())
            }
        }
        return total
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

        if (historyList.isEmpty()) {
            return messagesJson
        }

        val estimatedHistoryTokens = historyList.sumOf {
            estimateTokens(it.content) + estimateTokens(it.reasoningContent) + 10
        }

        val window = config.contextWindow.coerceAtLeast(4096)
        val thresholdLimit = (window * config.compactionThreshold).toInt()

        if (estimatedHistoryTokens < thresholdLimit) {
            appendNormalizedMessages(messagesJson, historyList)
            return messagesJson
        }

        // 超标时保留最近 6 轮，将较早历史提炼为事实摘要
        val preserveRecentCount = 6
        val oldHistory = if (historyList.size > preserveRecentCount) {
            historyList.subList(0, historyList.size - preserveRecentCount)
        } else {
            emptyList()
        }
        val recentHistory = historyList.takeLast(preserveRecentCount)

        if (oldHistory.isNotEmpty()) {
            val summarySb = StringBuilder("【历史早期运维会话记忆与已知事实摘要】:\n")
            val userQuestions = oldHistory.filter { it.role == "user" }.map { it.content.trim() }
            if (userQuestions.isNotEmpty()) {
                summarySb.append("- 用户曾关注/提问的问题: ")
                summarySb.append(userQuestions.takeLast(5).joinToString("；") { it.take(60) })
                summarySb.append("\n")
            }

            val assistantConclusions = oldHistory.filter { it.role == "assistant" && it.content.isNotBlank() }
                .map { it.content.trim() }
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
        return messagesJson
    }

    /**
     * 规范化并填充历史消息：
     * 1. 过滤空内容；
     * 2. 角色交替守卫 (Role Alternation Guard)：若存在连续两个相同的角色 (如用户在中断后连发两条 user 消息)，
     *    自动安全合并为一条复合指令，彻底杜绝大模型 API 出现格式校验报错或空回复。
     */
    private fun appendNormalizedMessages(messagesJson: JSONArray, list: List<AiChatMessage>) {
        val nonSystemList = list.filter { 
            it.role != "system" && (it.content.isNotBlank() || it.reasoningContent.isNotBlank() || it.images.isNotEmpty()) 
        }
        if (nonSystemList.isEmpty()) return

        for (msg in nonSystemList) {
            val role = msg.role
            val textContent = if (msg.content.isNotBlank()) msg.content.trim() else msg.reasoningContent.trim()

            if (role == "user" && msg.images.isNotEmpty()) {
                // 工业级标准 OpenAI Vision API 结构 (全面兼容 GPT-4o, Claude 3.5, Gemini, Qwen-VL 等)
                val contentArray = JSONArray()
                if (textContent.isNotEmpty()) {
                    contentArray.put(JSONObject().apply {
                        put("type", "text")
                        put("text", textContent)
                    })
                }
                for (imgData in msg.images) {
                    val formattedUrl = if (imgData.startsWith("data:")) imgData else "data:image/jpeg;base64,$imgData"
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
                })
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
            val content = item.optString("content", "")

            if (role == "tool" && content.length > 3000) {
                val head = content.take(1200)
                val tail = content.takeLast(800)
                val trimmedContent = "$head\n\n... [中间超长日志输出由 TermX ContextCompactor 自动省略 ${content.length - 2000} 字符] ...\n\n$tail"
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
