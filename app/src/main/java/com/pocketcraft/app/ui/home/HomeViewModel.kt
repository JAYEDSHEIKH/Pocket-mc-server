package com.pocketcraft.app.ui.home

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pocketcraft.app.core.process.ServerForegroundService
import com.pocketcraft.app.core.process.ServerProcessManager
import com.pocketcraft.app.core.storage.StorageManager
import com.pocketcraft.app.data.ServerProfile
import com.pocketcraft.app.data.ServerProfileDao
import com.pocketcraft.app.data.ServerStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ServerListItem(
    val profile: ServerProfile,
    val liveStatus: ServerStatus
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val serverProfileDao: ServerProfileDao,
    private val storageManager: StorageManager
) : ViewModel() {

    private val processManager = ServerProcessManager.instance

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
            val serverDir = storageManager.resolveServerDir(profile.id).absolutePath
            context.startForegroundService(
                ServerForegroundService.startServerIntent(
                    context, profile.id, profile.ramMb, serverDir, profile.customStartCommand
                )
            )
        }
    }

    fun stopServer(serverId: String) {
        viewModelScope.launch {
            context.startService(ServerForegroundService.stopServerIntent(context, serverId))
        }
    }

    fun updateServer(profile: ServerProfile, name: String, ramMb: Int, customStartCommand: String?, notes: String) {
        viewModelScope.launch {
            serverProfileDao.updateSettings(
                id = profile.id,
                name = name.trim(),
                ramMb = ramMb,
                customStartCommand = customStartCommand?.trim()?.takeIf { it.isNotBlank() },
                notes = notes.trim()
            )
        }
    }

    fun deleteServer(profile: ServerProfile) {
        viewModelScope.launch {
            val id = profile.id
            if (processManager.isRunning(id)) {
                context.startService(ServerForegroundService.stopServerIntent(context, id))
                val deadline = System.currentTimeMillis() + 35_000L
                while (processManager.isRunning(id) && System.currentTimeMillis() < deadline) {
                    delay(500)
                }
            }
            serverProfileDao.delete(profile)
            storageManager.resolveServerDir(id).deleteRecursively()
        }
    }
}
