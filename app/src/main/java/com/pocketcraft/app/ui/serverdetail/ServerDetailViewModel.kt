package com.pocketcraft.app.ui.serverdetail

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
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@HiltViewModel
class ServerDetailViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val serverProfileDao: ServerProfileDao
) : ViewModel() {

    private val processManager = ServerProcessManager.instance

    private val _serverId = MutableStateFlow<String?>(null)

    val profile: StateFlow<ServerProfile?> = _serverId
        .filterNotNull()
        .flatMapLatest { id -> serverProfileDao.getServerById(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Live process status, falling back to DB value. */
    val liveStatus: StateFlow<ServerStatus> = _serverId
        .filterNotNull()
        .flatMapLatest { id ->
            combine(
                serverProfileDao.getServerById(id),
                processManager.allStatuses
            ) { dbProfile, statuses ->
                statuses[id] ?: dbProfile?.status ?: ServerStatus.STOPPED
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ServerStatus.STOPPED)

    /**
     * Console log lines from the running process (empty if server not running).
     *
     * Re-subscribes to the process's SharedFlow whenever [allStatuses] changes.
     * This is critical: stopping and restarting a server creates a new [ServerProcess]
     * with a brand-new SharedFlow.  Without this re-subscription, the console would
     * go silent after the first restart because we'd still be collecting the old
     * (now completed) flow.
     */
    val consoleLines: StateFlow<List<String>> = _serverId
        .filterNotNull()
        .flatMapLatest { id ->
            // Re-trigger the inner subscription every time the status map changes.
            // distinctUntilChanged() prevents redundant re-subscriptions for
            // unrelated server status updates.
            processManager.allStatuses
                .map { it[id] }
                .distinctUntilChanged()
                .flatMapLatest {
                    val flow = processManager.getConsoleFlow(id)
                    flow?.scan(emptyList<String>()) { acc, line ->
                        (acc + line).takeLast(2_000)
                    } ?: flowOf(emptyList())
                }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun init(serverId: String) {
        _serverId.value = serverId
    }

    fun startServer() {
        val prof = profile.value ?: return
        viewModelScope.launch {
            serverProfileDao.updateStatus(prof.id, ServerStatus.STARTING)
            val serverDir = File(context.filesDir, "servers/${prof.id}").absolutePath
            context.startForegroundService(
                ServerForegroundService.startServerIntent(
                    context, prof.id, prof.ramMb, serverDir
                )
            )
        }
    }

    fun stopServer() {
        val prof = profile.value ?: return
        viewModelScope.launch {
            context.startService(
                ServerForegroundService.stopServerIntent(context, prof.id)
            )
        }
    }

    fun sendCommand(command: String) {
        val id = _serverId.value ?: return
        processManager.sendCommand(id, command)
    }
}
