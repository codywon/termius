package com.termius.clone.terminal.session

import android.content.Context
import android.content.Intent
import com.termius.clone.data.model.HostEntity
import com.termius.clone.data.model.IdentityEntity
import com.termius.clone.service.SshForegroundService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object SessionManager {

    private val _sessions = MutableStateFlow<List<SshSession>>(emptyList())
    val sessions: StateFlow<List<SshSession>> = _sessions.asStateFlow()

    private val _currentSessionId = MutableStateFlow<String?>(null)
    val currentSessionId: StateFlow<String?> = _currentSessionId.asStateFlow()

    val currentSession: SshSession?
        get() = _sessions.value.find { it.id == _currentSessionId.value }

    fun openSession(
        context: Context,
        host: HostEntity,
        identity: IdentityEntity? = null
    ): SshSession {
        val session = SshSession(host = host, identity = identity)
        _sessions.value = _sessions.value + session
        _currentSessionId.value = session.id
        session.connect()

        updateForegroundService(context)
        return session
    }

    fun selectSession(id: String) {
        if (_sessions.value.any { it.id == id }) {
            _currentSessionId.value = id
        }
    }

    fun closeSession(context: Context, id: String) {
        val session = _sessions.value.find { it.id == id }
        session?.disconnect()

        val remaining = _sessions.value.filter { it.id != id }
        _sessions.value = remaining

        if (_currentSessionId.value == id) {
            _currentSessionId.value = remaining.lastOrNull()?.id
        }

        updateForegroundService(context)
    }

    private fun updateForegroundService(context: Context) {
        try {
            val count = _sessions.value.size
            val intent = Intent(context, SshForegroundService::class.java).apply {
                putExtra(SshForegroundService.EXTRA_SESSION_COUNT, count)
            }
            if (count > 0) {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } else {
                context.stopService(intent)
            }
        } catch (e: Throwable) {
            e.printStackTrace()
            // 避免因任何后台限制或厂商系统拦截导致的崩溃
        }
    }
}
