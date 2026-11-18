package com.termius.clone.terminal.session

import android.content.Context
import com.termius.clone.data.model.AuthType
import com.termius.clone.data.model.HostEntity
import com.termius.clone.data.model.IdentityEntity
import com.termius.clone.service.SshForegroundService
import com.termius.clone.terminal.tunnel.TunnelManager
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

    private fun effectiveUsername(host: HostEntity, identity: IdentityEntity?): String {
        return if (host.authType == AuthType.IDENTITY_REF && identity != null) {
            identity.username
        } else {
            host.username
        }
    }

    private fun hasEndpointChanged(
        oldHost: HostEntity,
        oldIdentity: IdentityEntity?,
        newHost: HostEntity,
        newIdentity: IdentityEntity?
    ): Boolean {
        return oldHost.hostname != newHost.hostname ||
            oldHost.port != newHost.port ||
            oldHost.authType != newHost.authType ||
            effectiveUsername(oldHost, oldIdentity) != effectiveUsername(newHost, newIdentity)
    }

    fun openSession(
        context: Context,
        host: HostEntity,
        identity: IdentityEntity? = null,
        forceNew: Boolean = false
    ): SshSession {
        var existingToReconnect: SshSession? = null
        var newSessionToConnect: SshSession? = null
        val resultSession: SshSession

        synchronized(this) {
            // 如果已经存在该主机的会话标签，直接切过去，杜绝重复生成多个同名标签
            if (!forceNew) {
                val existing = _sessions.value.find { it.host.id == host.id }
                if (existing != null) {
                    val endpointChanged = hasEndpointChanged(existing.host, existing.identity, host, identity)
                    existing.updateCredentials(host, identity)
                    _currentSessionId.value = existing.id
                    val st = existing.sessionState.value
                    if (endpointChanged || st == SessionState.DISCONNECTED || st == SessionState.ERROR) {
                        existingToReconnect = existing
                    }
                    resultSession = existing
                } else {
                    val session = SshSession(host = host, identity = identity)
                    _sessions.value = _sessions.value + session
                    _currentSessionId.value = session.id
                    newSessionToConnect = session
                    resultSession = session
                }
            } else {
                val session = SshSession(host = host, identity = identity)
                _sessions.value = _sessions.value + session
                _currentSessionId.value = session.id
                newSessionToConnect = session
                resultSession = session
            }
        }

        existingToReconnect?.reconnect()
        newSessionToConnect?.connect()
        updateForegroundService(context)
        return resultSession
    }

    fun selectSession(id: String) {
        synchronized(this) {
            if (_sessions.value.any { it.id == id }) {
                _currentSessionId.value = id
            }
        }
    }

    fun updateHostSessions(host: HostEntity, identity: IdentityEntity? = null) {
        val toReconnect = mutableListOf<SshSession>()
        synchronized(this) {
            for (session in _sessions.value) {
                if (session.host.id == host.id) {
                    val endpointChanged = hasEndpointChanged(session.host, session.identity, host, identity)
                    session.updateCredentials(host, identity)
                    if (endpointChanged && !session.isManuallyDisconnected) {
                        toReconnect.add(session)
                    }
                }
            }
        }
        for (session in toReconnect) {
            session.reconnect()
        }
    }

    fun closeSession(context: Context, id: String) {
        var closedSession: SshSession? = null
        var shouldStopHostTunnels = false

        synchronized(this) {
            val session = _sessions.value.find { it.id == id }
            closedSession = session

            val remaining = _sessions.value.filter { it.id != id }
            _sessions.value = remaining

            if (_currentSessionId.value == id) {
                _currentSessionId.value = remaining.lastOrNull()?.id
            }

            if (session != null && remaining.none { it.host.id == session.host.id }) {
                shouldStopHostTunnels = true
            }
        }

        closedSession?.destroy()
        if (shouldStopHostTunnels) {
            closedSession?.let { TunnelManager.stopTunnelsForHost(it.host.id) }
        }
        updateForegroundService(context)
    }

    fun closeSessionsForHost(context: Context, hostId: Long) {
        val toDestroy: List<SshSession>
        synchronized(this) {
            val current = _sessions.value
            toDestroy = current.filter { it.host.id == hostId }
            if (toDestroy.isNotEmpty()) {
                val remaining = current.filter { it.host.id != hostId }
                _sessions.value = remaining
                if (_currentSessionId.value != null && toDestroy.any { it.id == _currentSessionId.value }) {
                    _currentSessionId.value = remaining.lastOrNull()?.id
                }
            }
        }

        for (session in toDestroy) {
            session.destroy()
        }
        TunnelManager.stopTunnelsForHost(hostId)
        updateForegroundService(context)
    }

    private fun updateForegroundService(context: Context) {
        try {
            val count = _sessions.value.size
            if (count > 0) {
                SshForegroundService.start(context, count)
            } else if (!SshForegroundService.hasActiveTasks()) {
                SshForegroundService.stop(context)
            }
        } catch (e: Throwable) {
            e.printStackTrace()
            // 避免因任何后台限制或厂商系统拦截导致的崩溃
        }
    }
}
