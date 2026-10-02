package com.termius.clone.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "identities")
data class IdentityEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,                  // 例如 "AWS EC2 Key"
    val username: String = "ubuntu",
    val password: String = "",
    val privateKey: String = "",       // OpenSSH / PEM 格式私钥内容
    val passphrase: String = "",       // 私钥密码
    val keyType: String = "RSA"        // RSA, ED25519, ECDSA
)
