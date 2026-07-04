package com.pocketcraft.app.core.process

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.pocketcraft.app.MainActivity
import com.pocketcraft.app.R
import com.pocketcraft.app.core.jre.JreInstaller
import com.pocketcraft.app.core.storage.StorageManager
import com.pocketcraft.app.data.ServerProfileDao
import com.pocketcraft.app.data.ServerStatus
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect
import java.io.File
import javax.inject.Inject

/**
 * Long-lived foreground service that hosts all running Minecraft server child processes.
 *
 * Notification actions:
 *   Lock / Unlock — acquires/releases a PARTIAL_WAKE_LOCK so the CPU stays up while backgrounded
 *   Close         — stops all servers, removes the service, and exits the process
 */
@AndroidEntryPoint
class ServerForegroundService : Service() {

    companion object {
        private const val TAG = "ServerFgService"
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "pocketcraft_server_channel"

        const val ACTION_START_SERVER   = "com.pocketcraft.app.START_SERVER"
        const val ACTION_STOP_SERVER    = "com.pocketcraft.app.STOP_SERVER"
        const val ACTION_STOP_SERVICE   = "com.pocketcraft.app.STOP_SERVICE"
        const val ACTION_TOGGLE_LOCK    = "com.pocketcraft.app.TOGGLE_LOCK"
        const val ACTION_CLOSE_APP      = "com.pocketcraft.app.CLOSE_APP"

        const val EXTRA_SERVER_ID           = "server_id"
        const val EXTRA_RAM_MB              = "ram_mb"
        const val EXTRA_SERVER_DIR          = "server_dir"
        const val EXTRA_CUSTOM_START_CMD    = "custom_start_cmd"

        fun startServerIntent(
            ctx: Context,
            serverId: String,
            ramMb: Int,
            serverDir: String,
            customStartCommand: String? = null
        ) = Intent(ctx, ServerForegroundService::class.java).apply {
            action = ACTION_START_SERVER
            putExtra(EXTRA_SERVER_ID, serverId)
            putExtra(EXTRA_RAM_MB, ramMb)
            putExtra(EXTRA_SERVER_DIR, serverDir)
            customStartCommand?.let { putExtra(EXTRA_CUSTOM_START_CMD, it) }
        }

        fun stopServerIntent(ctx: Context, serverId: String) =
            Intent(ctx, ServerForegroundService::class.java).apply {
                action = ACTION_STOP_SERVER
                putExtra(EXTRA_SERVER_ID, serverId)
            }
    }

    @Inject lateinit var jreInstaller: JreInstaller
    @Inject lateinit var serverProfileDao: ServerProfileDao
    @Inject lateinit var storageManager: StorageManager

    private val manager get() = ServerProcessManager.instance
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val binder = LocalBinder()

    private var isLocked = false
    private var wakeLock: PowerManager.WakeLock? = null

    inner class LocalBinder : Binder() {
        fun getService(): ServerForegroundService = this@ServerForegroundService
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                buildNotification("Server manager running"),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, buildNotification("Server manager running"))
        }
        collectStatusUpdates()
        Log.i(TAG, "Service created")
    }

    private fun collectStatusUpdates() {
        scope.launch {
            var previous: Map<String, ServerStatus> = emptyMap()
            manager.allStatuses.collect { current ->
                current.forEach { (id, status) ->
                    if (previous[id] != status) {
                        serverProfileDao.updateStatus(id, status)
                        Log.d(TAG, "[$id] DB status → $status")
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
                val serverId    = intent.getStringExtra(EXTRA_SERVER_ID)    ?: return START_NOT_STICKY
                val ramMb       = intent.getIntExtra(EXTRA_RAM_MB, 1024)
                val serverDir   = intent.getStringExtra(EXTRA_SERVER_DIR)   ?: return START_NOT_STICKY
                val customCmd   = intent.getStringExtra(EXTRA_CUSTOM_START_CMD)
                scope.launch { startServer(serverId, ramMb, File(serverDir), customCmd) }
            }
            ACTION_STOP_SERVER -> {
                val serverId = intent.getStringExtra(EXTRA_SERVER_ID) ?: return START_NOT_STICKY
                scope.launch { stopServer(serverId) }
            }
            ACTION_STOP_SERVICE -> {
                scope.launch {
                    manager.destroyAll()
                    serverProfileDao.resetStaleRunningStatuses()
                    withContext(Dispatchers.Main) { stopSelf() }
                }
            }
            ACTION_TOGGLE_LOCK -> handleToggleLock()
            ACTION_CLOSE_APP   -> handleCloseApp()
        }
        return START_NOT_STICKY
    }

    private suspend fun startServer(
        serverId: String,
        ramMb: Int,
        serverDir: File,
        customStartCommand: String?
    ) {
        Log.i(TAG, "[$serverId] Starting — serverDir=${serverDir.absolutePath}")

        // ── Pre-flight: JRE ───────────────────────────────────────────────────
        val javaBinary = jreInstaller.getJavaBinary()
        if (javaBinary == null) {
            val jrePath = storageManager.jreDir.absolutePath
            val msg = buildString {
                appendLine("Java Runtime (JRE) could not be found.")
                appendLine()
                appendLine("The bundled OpenJDK 21 (aarch64) could not be extracted from this APK.")
                appendLine()
                appendLine("Most likely causes:")
                appendLine("• This APK was built locally without the JRE asset inside it.")
                appendLine("• The GitHub Actions CI workflow has not run yet.")
                appendLine()
                appendLine("Fix: Download the debug APK from your GitHub repository's")
                appendLine("Actions tab → latest run → Artifacts → PocketCraft-debug.apk")
                appendLine()
                appendLine("Expected JRE path: $jrePath")
            }
            Log.e(TAG, "[$serverId] $msg")
            manager.emitError(serverId, msg)
            serverProfileDao.updateStatus(serverId, ServerStatus.CRASHED)
            return
        }

        // ── Pre-flight: server.jar ────────────────────────────────────────────
        val serverJar = File(serverDir, "server.jar")
        if (!serverJar.exists()) {
            val msg = buildString {
                appendLine("server.jar not found.")
                appendLine()
                appendLine("Path: ${serverJar.absolutePath}")
                appendLine()
                appendLine("The download may have failed or is still in progress.")
                appendLine("Check the server's status on the home screen.")
                appendLine("If it shows STOPPED (not DOWNLOADING), try deleting this server")
                appendLine("and creating it again.")
            }
            Log.e(TAG, "[$serverId] $msg")
            manager.emitError(serverId, msg)
            serverProfileDao.updateStatus(serverId, ServerStatus.CRASHED)
            return
        }

        // ── Ensure server directory exists before touching any files ─────────
        if (!serverDir.exists() && !serverDir.mkdirs()) {
            val msg = "Could not create server directory.\nPath: ${serverDir.absolutePath}\n\nCheck available storage space."
            Log.e(TAG, "[$serverId] $msg")
            manager.emitError(serverId, msg)
            serverProfileDao.updateStatus(serverId, ServerStatus.CRASHED)
            return
        }

        // ── Pre-flight: eula.txt ──────────────────────────────────────────────
        val eulaFile = File(serverDir, "eula.txt")
        if (!eulaFile.exists()) {
            eulaFile.writeText("# EULA accepted via PocketCraft\neula=true\n")
        }

        manager.startServer(serverId, javaBinary, serverDir, ramMb, customStartCommand)
    }

    private suspend fun stopServer(serverId: String) {
        manager.stopServer(serverId)
        if (!anyServerRunning()) withContext(Dispatchers.Main) { stopSelf() }
    }

    private fun anyServerRunning(): Boolean =
        manager.allStatuses.value.values.any {
            it == ServerStatus.RUNNING || it == ServerStatus.STARTING
        }

    // ── Lock / Unlock ─────────────────────────────────────────────────────────

    private fun handleToggleLock() {
        isLocked = !isLocked
        if (isLocked) {
            val pm = getSystemService(PowerManager::class.java)
            wakeLock = pm.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "PocketCraft::ServerLock"
            ).also { it.acquire() }
        } else {
            wakeLock?.let { if (it.isHeld) it.release() }
            wakeLock = null
        }
        updateNotification()
    }

    // ── Close app ─────────────────────────────────────────────────────────────

    private fun handleCloseApp() {
        scope.launch {
            manager.destroyAll()
            serverProfileDao.resetStaleRunningStatuses()
            wakeLock?.let { if (it.isHeld) it.release() }
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            delay(400)
            android.os.Process.killProcess(android.os.Process.myPid())
        }
    }

    // ── Notification ──────────────────────────────────────────────────────────

    private fun updateNotification() {
        val runningCount = manager.allStatuses.value.values.count {
            it == ServerStatus.RUNNING || it == ServerStatus.STARTING
        }
        val text = if (runningCount == 0) "No servers running"
                   else "$runningCount server${if (runningCount > 1) "s" else ""} running"
        getSystemService(NotificationManager::class.java)
            .notify(NOTIFICATION_ID, buildNotification(text))
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID, "Server Manager", NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Shown while a Minecraft server is running on this device"
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

        val lockPi = PendingIntent.getService(
            this, 1,
            Intent(this, ServerForegroundService::class.java).apply { action = ACTION_TOGGLE_LOCK },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val closePi = PendingIntent.getService(
            this, 2,
            Intent(this, ServerForegroundService::class.java).apply { action = ACTION_CLOSE_APP },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val lockLabel = if (isLocked) "Unlock" else "Lock"
        val title = if (isLocked) "PocketCraft 🔒" else "PocketCraft"

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_server_notification)
            .setContentTitle(title)
            .setContentText(contentText)
            .setContentIntent(openIntent)
            .setOngoing(true)
            .setSilent(true)
            .addAction(R.drawable.ic_server_notification, lockLabel, lockPi)
            .addAction(R.drawable.ic_server_notification, "Close", closePi)
            .build()
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onDestroy() {
        super.onDestroy()
        wakeLock?.let { if (it.isHeld) it.release() }
        scope.launch {
            manager.destroyAll()
            serverProfileDao.resetStaleRunningStatuses()
        }.invokeOnCompletion { scope.cancel() }
        Log.i(TAG, "Service destroyed")
    }
}
