package com.termius.clone.data.model

import java.util.UUID

data class QuickCommand(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val subtitle: String = "",
    val command: String,
    val isCustom: Boolean = false
)
