package com.pocketcraft.app.ui.home

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pocketcraft.app.core.process.ServerForegroundService
import com.pocketcraft.app.core.process.ServerProcessManager
import com.pocketcraft.app.data.ServerProfile
import com.pocketcraft.app.data.ServerProfileDao
import com.pocketcraft.app.data.ServerStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

data class ServerListItem(
    val profile: ServerProfile,
    val liveStatus: ServerStatus
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val serverProfileDao: ServerProfileDao
) : ViewModel() {

    private val processManager = ServerProcessManager.instance

    /** Combines DB profiles with live process status from ServerProcessManager. */
    val servers: StateFlow<List<ServerListItem>> = serverProfileDao.getAllServers()
        .combine(processManager.allStatuses) { profiles, liveStatuses ->
            profiles.map { profile ->
                ServerListItem(
                    profile = profile,
                    liveStatus = liveStatuses[profile.id] ?: profile.status
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun startServer(profile: ServerProfile) {
        if (!profile.eulaAccepted) return
        viewModelScope.launch {
            serverProfileDao.updateStatus(profile.id, ServerStatus.STARTING)
            val serverDir = serverDirectory(profile.id).absolutePath
            context.startForegroundService(
                ServerForegroundService.startServerIntent(context, profile.id, profile.ramMb, serverDir)
            )
        }
    }

    fun stopServer(serverId: String) {
        viewModelScope.launch {
            context.startService(ServerForegroundService.stopServerIntent(context, serverId))
        }
    }

    /**
     * Stops the server (if running), waits for it to reach a terminal state,
     * then deletes the DB record and all server files.
     * The wait is bounded to 35 s — matching the graceful-stop timeout in ServerProcess.
     */
    fun deleteServer(profile: ServerProfile) {
        viewModelScope.launch {
            val id = profile.id

            // Stop the server if it's currently live
            if (processManager.isRunning(id)) {
                context.startService(ServerForegroundService.stopServerIntent(context, id))

                // Poll until the process is gone or timeout (35 s)
                val deadline = System.currentTimeMillis() + 35_000L
                while (processManager.isRunning(id) && System.currentTimeMillis() < deadline) {
                    delay(500)
                }
            }

            // Safe to delete DB row and files now
            serverProfileDao.delete(profile)
            serverDirectory(id).deleteRecursively()
        }
    }

    private fun serverDirectory(serverId: String): File =
        File(context.filesDir, "servers/$serverId")
}
