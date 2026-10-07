package com.termius.clone.data.model

import java.util.UUID

/**
 * AI 智能体连接与大模型基础配置 (参考 Pi-Agent 极简范式)
 */
data class AiAgentConfig(
    val apiKey: String = "",
    val baseUrl: String = "https://api.deepseek.com",
    val modelName: String = "deepseek-chat",
    val customPrompt: String = "",
    val temperature: Double = 0.2,
    val maxTokens: Int = 4096,
    val contextWindow: Int = 65536,
    val compactionThreshold: Double = 0.7
)

/**
 * AI 对话会话模型 (支持历史多会话隔离与快速切换)
 */
data class AiChatSession(
    val id: String = UUID.randomUUID().toString(),
    val title: String = "新会话",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

/**
 * 对话消息模型 (支持正文、深度思考链与工具调用状态)
 */
data class AiChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val role: String = "user", // "user", "assistant", "system", "tool"
    val content: String = "",
    val reasoningContent: String = "",
    val toolCallsJson: String = "",
    val toolCallId: String = "",
    val images: List<String> = emptyList(), // 多模态视觉图片 (Base64 Data URI)
    val timestamp: Long = System.currentTimeMillis(),
    val isThinking: Boolean = false,
    val isError: Boolean = false
) {
    // 工业级防御：杜绝任何 Gson 反序列化破坏 Kotlin 非空契约导致的 NPE
    val safeContent: String get() = (content as String?) ?: ""
    val safeReasoning: String get() = (reasoningContent as String?) ?: ""
    val safeToolCalls: String get() = (toolCallsJson as String?) ?: ""
    val safeToolCallId: String get() = (toolCallId as String?) ?: ""
    val safeImages: List<String> get() = (images as List<String>?) ?: emptyList()
}

/**
 * 命令风险级别
 */
enum class RiskLevel {
    LOW,       // 只读查询 (如 uptime, uname, df -h, cat)
    MEDIUM,    // 状态变更/服务控制 (如 systemctl restart, apt update)
    HIGH,      // 配置修改/高风险影响 (如 iptables -F, rm 目录, userdel)
    CRITICAL   // 破坏性/不可逆操作 (如 rm -rf, mkfs, dd, shutdown, drop database)
}

/**
 * 危险操作人工审批请求 (Human-in-the-Loop)
 */
data class DangerousActionRequest(
    val approvalId: String = UUID.randomUUID().toString(),
    val command: String,
    val hostLabel: String,
    val riskLevel: RiskLevel,
    val reason: String,
    val impact: String
)

/**
 * 远程主机系统环境探测画像
 */
data class SystemEnvInfo(
    val osName: String = "Unknown",           // 如 Ubuntu, Debian, CentOS, Alpine, Arch
    val osVersion: String = "",              // 如 22.04 LTS
    val architecture: String = "x86_64",     // 如 x86_64, aarch64
    val kernelVersion: String = "",          // 如 5.15.0-generic
    val packageManager: String = "apt",      // 如 apt, yum, dnf, apk, pacman
    val isContainer: Boolean = false,        // 是否在 Docker/LXC 容器内
    val installedTools: List<String> = emptyList(), // 已探测到的工具: docker, nginx, systemd 等
    val rawSummary: String = ""              // 原始探测输出
)
