package com.termius.clone.data.model

import java.util.UUID

enum class TunnelType {
    LOCAL,    // 本地端口转发: 本机端口 -> 远程目标
    REMOTE,   // 远程端口转发: 远程端口 -> 本地目标
    DYNAMIC   // 动态 SOCKS5 代理
}

data class TunnelRule(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val type: TunnelType = TunnelType.LOCAL,
    val localPort: Int,
    val remoteHost: String = "127.0.0.1",
    val remotePort: Int,
    val hostId: Long? = null,
    val isRunning: Boolean = false,
    val description: String = ""
)
