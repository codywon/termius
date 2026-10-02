package com.termius.clone.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "hosts")
data class HostEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val label: String,                 // 别名，例如 "Production Server"
    val hostname: String,              // IP 或域名
    val port: Int = 22,
    val username: String = "root",
    val authType: AuthType = AuthType.PASSWORD,
    val identityId: Long? = null,      // 关联的 Keychain Identity
    val password: String = "",         // 独立密码（若未关联 Identity）
    val privateKey: String = "",       // 独立私钥（若未关联 Identity）
    val passphrase: String = "",       // 私钥密码
    val groupName: String = "Default", // 分组目录
    val colorTag: String = "#7952FF",  // 标签颜色
    val jumpHostId: Long? = null,      // 跳板机 ID
    val terminalTheme: String = "Dracula",
    val lastConnected: Long = 0L
)

enum class AuthType {
    PASSWORD,
    KEY,
    IDENTITY_REF
}
