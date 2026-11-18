package com.termius.clone.data.local

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.termius.clone.data.model.AiAgentConfig
import com.termius.clone.data.model.AiChatMessage
import com.termius.clone.data.model.AiChatSession
import com.termius.clone.data.security.KeyVaultManager
import java.io.File
import java.security.MessageDigest

/**
 * AI 智能体配置与多会话历史记录持久化管理
 */
class AiConfigManager(context: Context) {

    private val appContext = context.applicationContext ?: context
    private val prefs: SharedPreferences = appContext.getSharedPreferences("termx_ai_prefs", Context.MODE_PRIVATE)
    private val blobDir: File = File(appContext.filesDir, "ai_chat_blobs")
    private val gson = Gson()

    companion object {
        private const val KEY_CONFIG = "ai_agent_config"
        private const val KEY_SESSIONS = "ai_chat_sessions"
        private const val KEY_CURRENT_SESSION_ID = "ai_current_session_id"
        private const val KEY_LEGACY_HISTORY = "ai_chat_history"
        private const val PREFIX_SESSION_MESSAGES = "ai_messages_"
        private const val BLOB_TOKEN_PREFIX = "txblob:"
        private const val BLOB_OFFLOAD_THRESHOLD = 256
        private val BLOB_TOKEN_REGEX = Regex("""txblob:([a-fA-F0-9]{40})""")
        private val SHA1_HEX_REGEX = Regex("""^[a-fA-F0-9]{40}$""")
    }

    fun loadConfig(): AiAgentConfig {
        val json = prefs.getString(KEY_CONFIG, null) ?: return AiAgentConfig()
        return try {
            val raw = gson.fromJson(json, AiAgentConfig::class.java) ?: return AiAgentConfig()
            val rawKey = (raw.apiKey as String?) ?: ""
            val plainApiKey = if (rawKey.isNotEmpty()) {
                try {
                    KeyVaultManager.decrypt(rawKey)
                } catch (_: Exception) {
                    ""
                }
            } else {
                ""
            }
            raw.copy(apiKey = plainApiKey)
        } catch (_: Exception) {
            AiAgentConfig()
        }
    }

    fun saveConfig(config: AiAgentConfig) {
        val encryptedKey = if (config.apiKey.isNotEmpty()) {
            KeyVaultManager.encrypt(config.apiKey)
        } else {
            ""
        }
        val toStore = config.copy(apiKey = encryptedKey)
        prefs.edit().putString(KEY_CONFIG, gson.toJson(toStore)).apply()
    }

    // ==========================================
    // 多会话管理 (Multi-Session Management)
    // ==========================================

    fun hasSession(sessionId: String): Boolean {
        if (sessionId.isBlank()) return false
        val json = prefs.getString(KEY_SESSIONS, null) ?: return false
        return try {
            val type = object : TypeToken<List<AiChatSession>>() {}.type
            val list: List<AiChatSession>? = gson.fromJson(json, type)
            list?.any { it.id == sessionId } == true
        } catch (_: Exception) {
            false
        }
    }

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
        cleanupUnusedBlobs()

        val nextId = sessions.first().id
        setCurrentSessionId(nextId)
        return nextId
    }

    // ==========================================
    // 消息读写 (按 SessionId 隔离，超长 Base64 图像载荷落盘卸载)
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
                    images = msg.safeImages.map { restoreImageBlob(it) },
                    attachedFileName = msg.safeAttachedFileName,
                    attachedFileSize = msg.attachedFileSize,
                    fileContextText = msg.safeFileContextText,
                    inlineImages = msg.safeInlineImages.map { restoreImageBlob(it) },
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
        val trimmed = if (messages.size > 100) {
            val tail = messages.takeLast(100)
            val firstUserIdx = tail.indexOfFirst { it.role == "user" }
            if (firstUserIdx > 0) tail.drop(firstUserIdx) else tail
        } else {
            messages
        }

        val offloaded = trimmed.map { msg ->
            val newImages = msg.safeImages.map { offloadImageBlob(it) }
            val newInlineImages = msg.safeInlineImages.map { offloadImageBlob(it) }
            if (newImages == msg.safeImages && newInlineImages == msg.safeInlineImages) {
                msg
            } else {
                msg.copy(images = newImages, inlineImages = newInlineImages)
            }
        }

        prefs.edit().putString(PREFIX_SESSION_MESSAGES + sessionId, gson.toJson(offloaded)).apply()
        if (messages.size > 100) {
            cleanupUnusedBlobs()
        }

        // 自动更新会话标题（取首条用户输入作为标题）与更新时间
        if (messages.isNotEmpty()) {
            val sessions = loadSessions().toMutableList()
            val index = sessions.indexOfFirst { it.id == sessionId }
            if (index != -1) {
                val s = sessions[index]
                var title = s.title
                if (title == "新会话" || title == "默认运维会话") {
                    val firstUser = messages.firstOrNull { it.role == "user" }?.safeContent?.trim()
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
        cleanupUnusedBlobs()
    }

    private fun offloadImageBlob(payload: String): String {
        if (payload.startsWith(BLOB_TOKEN_PREFIX)) return payload
        if (!payload.startsWith("data:image/", ignoreCase = true) || payload.length < BLOB_OFFLOAD_THRESHOLD) {
            return payload
        }
        return try {
            if (!blobDir.exists()) blobDir.mkdirs()
            val hash = sha1Hex(payload)
            val targetFile = File(blobDir, "$hash.dat")
            if (!targetFile.exists()) {
                val tmpFile = File(blobDir, "$hash.tmp.${System.nanoTime()}")
                tmpFile.writeText(payload, Charsets.UTF_8)
                if (!tmpFile.renameTo(targetFile)) {
                    targetFile.writeText(payload, Charsets.UTF_8)
                    tmpFile.delete()
                }
            }
            "$BLOB_TOKEN_PREFIX$hash"
        } catch (_: Exception) {
            payload
        }
    }

    private fun restoreImageBlob(tokenOrPayload: String): String {
        if (!tokenOrPayload.startsWith(BLOB_TOKEN_PREFIX)) return tokenOrPayload
        val hash = tokenOrPayload.removePrefix(BLOB_TOKEN_PREFIX).trim()
        if (!SHA1_HEX_REGEX.matches(hash)) return tokenOrPayload
        return try {
            val file = File(blobDir, "$hash.dat")
            if (file.exists()) file.readText(Charsets.UTF_8) else tokenOrPayload
        } catch (_: Exception) {
            tokenOrPayload
        }
    }

    private fun cleanupUnusedBlobs() {
        try {
            if (!blobDir.exists()) return
            val referenced = HashSet<String>()
            for ((key, value) in prefs.all) {
                if (key.startsWith(PREFIX_SESSION_MESSAGES) && value is String) {
                    for (match in BLOB_TOKEN_REGEX.findAll(value)) {
                        referenced.add(match.groupValues[1].lowercase())
                    }
                }
            }
            blobDir.listFiles()?.forEach { file ->
                if (file.isFile && file.extension == "dat") {
                    if (file.nameWithoutExtension.lowercase() !in referenced) {
                        file.delete()
                    }
                }
            }
        } catch (_: Exception) {
        }
    }

    private fun sha1Hex(text: String): String {
        val digest = MessageDigest.getInstance("SHA-1").digest(text.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }
}
