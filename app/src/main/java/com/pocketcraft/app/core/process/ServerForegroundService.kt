package com.pocketcraft.app.core.process

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.pocketcraft.app.MainActivity
import com.pocketcraft.app.R
import com.pocketcraft.app.core.jre.JreInstaller
import com.pocketcraft.app.data.ServerProfileDao
import com.pocketcraft.app.data.ServerStatus
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect
import java.io.File
import javax.inject.Inject

/**
 * Long-lived foreground service that hosts all running server child processes.
 * Without this, Android will kill the JVM child process when the app is backgrounded.
 *
 * Status persistence contract:
 * - All status transitions from [ServerProcessManager] are mirrored to Room by the
 *   [collectStatusUpdates] coroutine running for the service's lifetime.
 * - On service destroy, any server still in a running/starting state is reset to STOPPED.
 */
@AndroidEntryPoint
class ServerForegroundService : Service() {

    companion object {
        private const val TAG = "ServerFgService"
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "pocketcraft_server_channel"

        const val ACTION_START_SERVER = "com.pocketcraft.app.START_SERVER"
        const val ACTION_STOP_SERVER = "com.pocketcraft.app.STOP_SERVER"
        const val ACTION_STOP_SERVICE = "com.pocketcraft.app.STOP_SERVICE"
        const val EXTRA_SERVER_ID = "server_id"
        const val EXTRA_RAM_MB = "ram_mb"
        const val EXTRA_SERVER_DIR = "server_dir"

        fun startServerIntent(ctx: Context, serverId: String, ramMb: Int, serverDir: String) =
            Intent(ctx, ServerForegroundService::class.java).apply {
                action = ACTION_START_SERVER
                putExtra(EXTRA_SERVER_ID, serverId)
                putExtra(EXTRA_RAM_MB, ramMb)
                putExtra(EXTRA_SERVER_DIR, serverDir)
            }

        fun stopServerIntent(ctx: Context, serverId: String) =
            Intent(ctx, ServerForegroundService::class.java).apply {
                action = ACTION_STOP_SERVER
                putExtra(EXTRA_SERVER_ID, serverId)
            }
    }

    @Inject lateinit var jreInstaller: JreInstaller
    @Inject lateinit var serverProfileDao: ServerProfileDao

    private val manager get() = ServerProcessManager.instance
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val binder = LocalBinder()

    inner class LocalBinder : Binder() {
        fun getService(): ServerForegroundService = this@ServerForegroundService
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification("Server manager running"))

        // Mirror ALL live status changes from the process manager into Room.
        // This is the single authoritative path for DB status persistence.
        collectStatusUpdates()

        Log.i(TAG, "Service created")
    }

    /** Collects the process manager's status map and persists any change to Room. */
    private fun collectStatusUpdates() {
        scope.launch {
            var previous: Map<String, ServerStatus> = emptyMap()
            manager.allStatuses.collect { current ->
                // Detect entries that changed value
                current.forEach { (id, status) ->
                    if (previous[id] != status) {
                        serverProfileDao.updateStatus(id, status)
                        Log.d(TAG, "[$id] DB status → $status")
                    }
                }
                // Detect entries that disappeared (process finished/crashed and was removed)
                previous.forEach { (id, _) ->
                    if (!current.containsKey(id)) {
                        // ServerProcessManager removes entries on STOPPED/CRASHED — already written
                    }
                }
                previous = current
                updateNotification()
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_SERVER -> {
                val serverId = intent.getStringExtra(EXTRA_SERVER_ID) ?: return START_NOT_STICKY
                val ramMb = intent.getIntExtra(EXTRA_RAM_MB, 1024)
                val serverDir = intent.getStringExtra(EXTRA_SERVER_DIR) ?: return START_NOT_STICKY
                scope.launch { startServer(serverId, ramMb, File(serverDir)) }
            }
            ACTION_STOP_SERVER -> {
                val serverId = intent.getStringExtra(EXTRA_SERVER_ID) ?: return START_NOT_STICKY
                scope.launch { stopServer(serverId) }
            }
            ACTION_STOP_SERVICE -> {
                scope.launch {
                    manager.destroyAll()
                    serverProfileDao.resetStaleRunningStatuses()
                    stopSelf()
                }
            }
        }
        return START_NOT_STICKY
    }

    private suspend fun startServer(serverId: String, ramMb: Int, serverDir: File) {
        val javaBinary = jreInstaller.getJavaBinary()
        if (javaBinary == null) {
            Log.e(TAG, "[$serverId] Cannot start server — JRE not available")
            serverProfileDao.updateStatus(serverId, ServerStatus.CRASHED)
            return
        }
        manager.startServer(serverId, javaBinary, serverDir, ramMb)
        // Status will be persisted by collectStatusUpdates()
    }

    private suspend fun stopServer(serverId: String) {
        manager.stopServer(serverId)
        // Status persisted by collectStatusUpdates(); stop self if no more running
        if (!anyServerRunning()) {
            withContext(Dispatchers.Main) { stopSelf() }
        }
    }

    private fun anyServerRunning(): Boolean =
        manager.allStatuses.value.values.any {
            it == ServerStatus.RUNNING || it == ServerStatus.STARTING
        }

    private fun updateNotification() {
        val runningCount = manager.allStatuses.value.values.count {
            it == ServerStatus.RUNNING || it == ServerStatus.STARTING
        }
        val text = if (runningCount == 0) "No servers running"
                   else "$runningCount server${if (runningCount > 1) "s" else ""} running"
        getSystemService(NotificationManager::class.java)
            .notify(NOTIFICATION_ID, buildNotification(text))
    }

    // ── Notification boilerplate ──────────────────────────────────────────────

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Server Manager",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Shows while a Minecraft server is running on this device"
            setShowBadge(false)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(contentText: String): Notification {
        val openIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_server_notification)
            .setContentTitle("PocketCraft")
            .setContentText(contentText)
            .setContentIntent(openIntent)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onDestroy() {
        super.onDestroy()
        scope.launch {
            manager.destroyAll()
            serverProfileDao.resetStaleRunningStatuses()
        }.invokeOnCompletion { scope.cancel() }
        Log.i(TAG, "Service destroyed")
    }
}
