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
 * 对话消息模型 (支持正文、深度思考链与工具调用状态)
 */
data class AiChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val role: String, // "user", "assistant", "system", "tool"
    val content: String,
    val reasoningContent: String = "",
    val toolCallsJson: String = "",
    val toolCallId: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val isThinking: Boolean = false,
    val isError: Boolean = false
)

/**
 * 命令风险级别
 */
enum class RiskLevel {
    LOW,       // 只读查询 (如 uptime, uname, df -h, cat)
    MEDIUM,    // 状态变更/服务控制 (如 systemctl restart, apt update)
    HIGH,      // 配置修改/高风险影响 (如 iptables -F, rm 目录, userdel)
    CRITICAL   // 灾难性破坏命令 (如 rm -rf /, mkfs, dd, shutdown, drop database)
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
