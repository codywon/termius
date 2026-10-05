package com.termius.clone.data.local

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.termius.clone.data.model.AiAgentConfig
import com.termius.clone.data.model.AiChatMessage

/**
 * AI 智能体配置与对话历史持久化管理
 */
class AiConfigManager(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("termx_ai_prefs", Context.MODE_PRIVATE)
    private val gson = Gson()

    companion object {
        private const val KEY_CONFIG = "ai_agent_config"
        private const val KEY_HISTORY = "ai_chat_history"
    }

    fun loadConfig(): AiAgentConfig {
        val json = prefs.getString(KEY_CONFIG, null) ?: return AiAgentConfig()
        return try {
            gson.fromJson(json, AiAgentConfig::class.java) ?: AiAgentConfig()
        } catch (_: Exception) {
            AiAgentConfig()
        }
    }

    fun saveConfig(config: AiAgentConfig) {
        prefs.edit().putString(KEY_CONFIG, gson.toJson(config)).apply()
    }

    fun loadHistory(): List<AiChatMessage> {
        val json = prefs.getString(KEY_HISTORY, null) ?: return emptyList()
        return try {
            val type = object : TypeToken<List<AiChatMessage>>() {}.type
            gson.fromJson(json, type) ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun saveHistory(messages: List<AiChatMessage>) {
        // 最多保留最近 100 条
        val toSave = if (messages.size > 100) messages.takeLast(100) else messages
        prefs.edit().putString(KEY_HISTORY, gson.toJson(toSave)).apply()
    }

    fun clearHistory() {
        prefs.edit().remove(KEY_HISTORY).apply()
    }
}
