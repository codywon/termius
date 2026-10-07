package com.termius.clone.data.local

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.termius.clone.data.model.AiAgentConfig
import com.termius.clone.data.model.AiChatMessage
import com.termius.clone.data.model.AiChatSession

/**
 * AI 智能体配置与多会话历史记录持久化管理
 */
class AiConfigManager(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("termx_ai_prefs", Context.MODE_PRIVATE)
    private val gson = Gson()

    companion object {
        private const val KEY_CONFIG = "ai_agent_config"
        private const val KEY_SESSIONS = "ai_chat_sessions"
        private const val KEY_CURRENT_SESSION_ID = "ai_current_session_id"
        private const val KEY_LEGACY_HISTORY = "ai_chat_history"
        private const val PREFIX_SESSION_MESSAGES = "ai_messages_"
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

    // ==========================================
    // 多会话管理 (Multi-Session Management)
    // ==========================================

    fun loadSessions(): List<AiChatSession> {
        val json = prefs.getString(KEY_SESSIONS, null)
        val sessions: MutableList<AiChatSession> = if (json != null) {
            try {
                val type = object : TypeToken<List<AiChatSession>>() {}.type
                gson.fromJson<List<AiChatSession>>(json, type)?.toMutableList() ?: mutableListOf()
            } catch (_: Exception) {
                mutableListOf()
            }
        } else {
            mutableListOf()
        }

        // 迁移老旧单会话
        if (sessions.isEmpty()) {
            val legacyJson = prefs.getString(KEY_LEGACY_HISTORY, null)
            val defaultSession = AiChatSession(title = "默认运维会话")
            sessions.add(defaultSession)
            if (legacyJson != null) {
                prefs.edit().putString(PREFIX_SESSION_MESSAGES + defaultSession.id, legacyJson).apply()
            }
            saveSessions(sessions)
            setCurrentSessionId(defaultSession.id)
        }

        return sessions.sortedByDescending { it.updatedAt }
    }

    fun saveSessions(sessions: List<AiChatSession>) {
        prefs.edit().putString(KEY_SESSIONS, gson.toJson(sessions)).apply()
    }

    fun getCurrentSessionId(): String {
        val saved = prefs.getString(KEY_CURRENT_SESSION_ID, null)
        if (saved != null) return saved
        val list = loadSessions()
        val firstId = list.firstOrNull()?.id ?: createNewSession("新会话").id
        setCurrentSessionId(firstId)
        return firstId
    }

    fun setCurrentSessionId(sessionId: String) {
        prefs.edit().putString(KEY_CURRENT_SESSION_ID, sessionId).apply()
    }

    fun createNewSession(title: String = "新会话"): AiChatSession {
        val sessions = loadSessions().toMutableList()
        val newSession = AiChatSession(title = title)
        sessions.add(0, newSession)
        saveSessions(sessions)
        setCurrentSessionId(newSession.id)
        return newSession
    }

    fun renameSession(sessionId: String, newTitle: String) {
        val sessions = loadSessions().map {
            if (it.id == sessionId) it.copy(title = newTitle, updatedAt = System.currentTimeMillis()) else it
        }
        saveSessions(sessions)
    }

    fun deleteSession(sessionId: String): String {
        val sessions = loadSessions().filter { it.id != sessionId }.toMutableList()
        prefs.edit().remove(PREFIX_SESSION_MESSAGES + sessionId).apply()

        if (sessions.isEmpty()) {
            val fresh = AiChatSession(title = "新运维会话")
            sessions.add(fresh)
        }
        saveSessions(sessions)

        val nextId = sessions.first().id
        setCurrentSessionId(nextId)
        return nextId
    }

    // ==========================================
    // 消息读写 (按 SessionId 隔离)
    // ==========================================

    fun loadMessages(sessionId: String): List<AiChatMessage> {
        val json = prefs.getString(PREFIX_SESSION_MESSAGES + sessionId, null) ?: return emptyList()
        return try {
            val type = object : TypeToken<List<AiChatMessage>>() {}.type
            val rawList: List<AiChatMessage>? = gson.fromJson(json, type)
            rawList?.map { msg ->
                AiChatMessage(
                    id = msg.id ?: java.util.UUID.randomUUID().toString(),
                    role = msg.role ?: "user",
                    content = msg.safeContent,
                    reasoningContent = msg.safeReasoning,
                    toolCallsJson = msg.safeToolCalls,
                    toolCallId = msg.safeToolCallId,
                    images = msg.safeImages,
                    timestamp = msg.timestamp,
                    isThinking = msg.isThinking,
                    isError = msg.isError
                )
            } ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun saveMessages(sessionId: String, messages: List<AiChatMessage>) {
        val toSave = if (messages.size > 100) messages.takeLast(100) else messages
        prefs.edit().putString(PREFIX_SESSION_MESSAGES + sessionId, gson.toJson(toSave)).apply()

        // 自动更新会话标题（取首条用户输入作为标题）与更新时间
        if (messages.isNotEmpty()) {
            val sessions = loadSessions().toMutableList()
            val index = sessions.indexOfFirst { it.id == sessionId }
            if (index != -1) {
                val s = sessions[index]
                var title = s.title
                if (title == "新会话" || title == "默认运维会话") {
                    val firstUser = messages.firstOrNull { it.role == "user" }?.content?.trim()
                    if (!firstUser.isNullOrBlank()) {
                        title = firstUser.take(18)
                    }
                }
                sessions[index] = s.copy(title = title, updatedAt = System.currentTimeMillis())
                saveSessions(sessions)
            }
        }
    }

    fun clearMessages(sessionId: String) {
        prefs.edit().remove(PREFIX_SESSION_MESSAGES + sessionId).apply()
    }
}
