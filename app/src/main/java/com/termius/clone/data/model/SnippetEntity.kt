package com.termius.clone.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "snippets")
data class SnippetEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,                 // 例如 "Docker 重启所有容器"
    val command: String,               // 例如 "docker restart $(docker ps -q)\n"
    val tag: String = "Docker",
    val description: String = ""
)
