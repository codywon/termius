package com.termius.clone.service

import android.app.*
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.termius.clone.MainActivity
import com.termius.clone.terminal.session.SessionManager
import com.termius.clone.terminal.session.SessionState
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.coroutineContext

/**
 * 生产级 SSH 前台保活与连接常驻服务 (参考 mqtt-assistant-app 工业级保活机制):
 * 1. 启动为 Foreground Service (通知栏常驻)，防止系统在应用最小化或息屏时挂起进程；
 * 2. 组合持有 CPU WakeLock 与 Wi-Fi Lock，防止 Wi-Fi 芯片和 Socket 进入省电休眠导致 Broken Pipe；
 * 3. 动态监听息屏/亮屏事件：息屏时维持长连接心跳，亮屏时快速探活自愈；
 * 4. 监听网络切换（Wi-Fi/移动网络）：实时响应网络环境迁移；
 * 5. 安全释放与异常熔断保护（含 Android 15 dataSync 超时处理）。
 */
class SshForegroundService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null
    private var screenReceiver: BroadcastReceiver? = null
    private var connectivityManager: ConnectivityManager? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private var tickerJob: Job? = null
    private var networkProbeJob: Job? = null

    private val lastScreenProbeAt = AtomicLong(0L)
    private val lastNetworkProbeAt = AtomicLong(0L)

    private var startTimestamp = System.currentTimeMillis()

    companion object {
        private const val TAG = "SshFgService"
        private const val PROBE_THROTTLE_MS = 2_000L
        const val CHANNEL_ID = "teamx_ssh_session_channel"
        const val NOTIFICATION_ID = 2001
        const val ACTION_START = "com.termius.clone.service.ACTION_START"
        const val ACTION_STOP = "com.termius.clone.service.ACTION_STOP"
        const val EXTRA_SESSION_COUNT = "extra_session_count"

        private val _runningFlow = MutableStateFlow(false)
        val runningFlow: StateFlow<Boolean> = _runningFlow.asStateFlow()

        private val activeTaskCount = AtomicInteger(0)

        var isRunning: Boolean = false
            private set

        private fun updateRunningState(running: Boolean) {
            isRunning = running
            _runningFlow.value = running
        }

        fun hasActiveTasks(): Boolean = activeTaskCount.get() > 0

        fun acquireTask(context: Context) {
            activeTaskCount.incrementAndGet()
            val count = SessionManager.sessions.value.size.coerceAtLeast(1)
            start(context, count)
        }

        fun releaseTask(context: Context) {
            val remaining = activeTaskCount.updateAndGet { current -> (current - 1).coerceAtLeast(0) }
            if (remaining <= 0 && SessionManager.sessions.value.isEmpty()) {
                stop(context)
            }
        }

        fun start(context: Context, sessionCount: Int = 1) {
            try {
                val intent = Intent(context, SshForegroundService::class.java).apply {
                    action = ACTION_START
                    putExtra(EXTRA_SESSION_COUNT, sessionCount)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to start SshForegroundService: ${e.message}")
            }
        }

        fun stop(context: Context) {
            try {
                context.stopService(Intent(context, SshForegroundService::class.java))
            } catch (e: Exception) {
                Log.w(TAG, "Failed to stop SshForegroundService: ${e.message}")
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        try {
            createNotificationChannel()
            acquireWakeAndWifiLocks()
            registerScreenReceiver()
            registerNetworkCallback()
            startNotificationTicker()
            updateRunningState(true)
            startTimestamp = System.currentTimeMillis()
        } catch (e: Throwable) {
            Log.e(TAG, "Error in onCreate", e)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            try {
                ensureForegroundNotification(SessionManager.sessions.value.size)
                ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            } catch (e: Throwable) {
                Log.w(TAG, "Error during ACTION_STOP foreground transition: ${e.message}")
            }
            stopSelf()
            return START_NOT_STICKY
        }

        try {
            val count = intent?.getIntExtra(EXTRA_SESSION_COUNT, 1) ?: 1
            ensureForegroundNotification(count)
        } catch (e: Throwable) {
            Log.e(TAG, "Error in onStartCommand", e)
            stopSelf()
        }
        return START_NOT_STICKY
    }

    override fun onTimeout(startId: Int) {
        Log.w(TAG, "Foreground service timeout reached (startId=$startId), stopping gracefully")
        handleForegroundTimeout(startId)
    }

    override fun onTimeout(startId: Int, fgsType: Int) {
        Log.w(TAG, "Foreground service timeout reached (startId=$startId, fgsType=$fgsType), stopping gracefully")
        handleForegroundTimeout(startId)
    }

    private fun handleForegroundTimeout(startId: Int) {
        activeTaskCount.set(0)
        try {
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        } catch (e: Throwable) {
            Log.w(TAG, "stopForeground on timeout failed: ${e.message}")
        }
        stopSelf(startId)
    }

    private fun ensureForegroundNotification(sessionCount: Int) {
        val notification = buildNotification(sessionCount)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        updateRunningState(false)
        tickerJob?.cancel()
        networkProbeJob?.cancel()
        serviceScope.cancel()
        unregisterScreenReceiver()
        unregisterNetworkCallback()
        releaseWakeAndWifiLocks()
        super.onDestroy()
    }

    private fun acquireWakeAndWifiLocks() {
        try {
            val pm = getSystemService(Context.POWER_SERVICE) as? PowerManager
            wakeLock = pm?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "TermX:SshKeepAliveLock")?.apply {
                setReferenceCounted(false)
                acquire(24 * 60 * 60 * 1000L) // 24小时超长租约
            }

            val wm = applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            wifiLock = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                wm?.createWifiLock(WifiManager.WIFI_MODE_FULL_LOW_LATENCY, "TermX:SshWifiLock")
            } else {
                @Suppress("DEPRECATION")
                wm?.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "TermX:SshWifiLock")
            }?.apply {
                setReferenceCounted(false)
                acquire()
            }
            Log.d(TAG, "WakeLock and WifiLock acquired")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to acquire locks: ${e.message}")
        }
    }

    private fun releaseWakeAndWifiLocks() {
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
            wakeLock = null

            if (wifiLock?.isHeld == true) {
                wifiLock?.release()
            }
            wifiLock = null
            Log.d(TAG, "WakeLock and WifiLock released")
        } catch (e: Exception) {
            Log.w(TAG, "Error releasing locks: ${e.message}")
        }
    }

    private fun registerScreenReceiver() {
        screenReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                when (intent?.action) {
                    Intent.ACTION_SCREEN_ON -> {
                        val now = System.currentTimeMillis()
                        val prev = lastScreenProbeAt.get()
                        if (now - prev < PROBE_THROTTLE_MS || !lastScreenProbeAt.compareAndSet(prev, now)) {
                            return
                        }
                        Log.d(TAG, "Screen ON detected, verifying active SSH sessions")
                        serviceScope.launch {
                            probeAndReconnectSessions("SCREEN_ON")
                        }
                    }
                    Intent.ACTION_SCREEN_OFF -> {
                        Log.d(TAG, "Screen OFF detected, holding active socket keepalive")
                    }
                }
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
        }
        registerReceiver(screenReceiver, filter)
    }

    private fun unregisterScreenReceiver() {
        screenReceiver?.let {
            try {
                unregisterReceiver(it)
            } catch (_: Exception) {}
            screenReceiver = null
        }
    }

    private fun registerNetworkCallback() {
        try {
            connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            val request = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()

            networkCallback = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    Log.d(TAG, "Network became available")
                    val now = System.currentTimeMillis()
                    val prev = lastNetworkProbeAt.get()
                    if (now - prev < PROBE_THROTTLE_MS) {
                        return
                    }
                    networkProbeJob?.cancel()
                    networkProbeJob = serviceScope.launch {
                        delay(400L) // 等待网络切换路由稳定
                        val fireTime = System.currentTimeMillis()
                        lastNetworkProbeAt.set(fireTime)
                        probeAndReconnectSessions("NETWORK_AVAILABLE")
                    }
                }

                override fun onLost(network: Network) {
                    Log.w(TAG, "Network lost, active sessions might disconnect")
                }
            }
            connectivityManager?.registerNetworkCallback(request, networkCallback!!)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to register network callback: ${e.message}")
        }
    }

    private fun unregisterNetworkCallback() {
        networkCallback?.let {
            try {
                connectivityManager?.unregisterNetworkCallback(it)
            } catch (_: Exception) {}
            networkCallback = null
        }
    }

    private suspend fun probeAndReconnectSessions(trigger: String) {
        val sessions = SessionManager.sessions.value
        for (session in sessions) {
            coroutineContext.ensureActive()
            if (session.isManuallyDisconnected) continue
            val state = session.sessionState.value
            val needsReconnect = when (state) {
                SessionState.ERROR, SessionState.DISCONNECTED -> true
                SessionState.CONNECTED -> {
                    val isAlive = try {
                        session.getClient()?.isConnected == true
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        false
                    }
                    !isAlive
                }
                SessionState.CONNECTING, SessionState.AUTHENTICATING -> false
            }
            if (needsReconnect) {
                Log.i(TAG, "[$trigger] Reconnecting session ${session.host.label} (state=$state)")
                try {
                    session.reconnect()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(TAG, "[$trigger] Reconnect trigger failed for ${session.host.label}: ${e.message}")
                }
            }
        }
    }

    private fun startNotificationTicker() {
        tickerJob = serviceScope.launch {
            while (isActive) {
                delay(30_000L) // 每30秒轻量刷新通知栏时长
                val sessions = SessionManager.sessions.value
                val tasks = activeTaskCount.get()
                if (sessions.isEmpty() && tasks <= 0) {
                    // 若已无活动会话且无后台任务，自动优雅停止常驻服务
                    stopSelf()
                    break
                }
                val notification = buildNotification(sessions.size)
                val manager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                manager?.notify(NOTIFICATION_ID, notification)
            }
        }
    }

    private fun buildNotification(sessionCount: Int): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val sessions = SessionManager.sessions.value
        val activeHostLabels = sessions.joinToString(", ") { it.host.label }.ifBlank { "活跃会话" }
        val elapsedMinutes = ((System.currentTimeMillis() - startTimestamp) / 60000L).coerceAtLeast(0)
        val timeText = if (elapsedMinutes >= 60) "${elapsedMinutes / 60}小时${elapsedMinutes % 60}分" else "${elapsedMinutes}分钟"

        val contentText = if (sessions.isEmpty() && activeTaskCount.get() > 0) {
            "${activeTaskCount.get()} 个后台任务运行中 • 在线 $timeText"
        } else {
            "$sessionCount 个活动连接 ($activeHostLabels) • 在线 $timeText"
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("TermX Mobile 连接保活中")
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.stat_notify_sync_noanim)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "TermX SSH 会话保活",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "保持后台 SSH 会话和端口转发连接不断开"
                setShowBadge(false)
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }
}
