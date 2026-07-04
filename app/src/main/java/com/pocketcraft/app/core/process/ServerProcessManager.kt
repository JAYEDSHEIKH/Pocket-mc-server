package com.pocketcraft.app.core.process

import android.util.Log
import com.pocketcraft.app.data.ServerStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Singleton registry of all running server processes.
 *
 * Lives outside of any DI scope so that the ServerForegroundService and
 * ViewModels can both observe the same live state without a binding
 * back-and-forth.
 *
 * Access via [ServerProcessManager.instance].
 */
class ServerProcessManager private constructor() {

    companion object {
        private const val TAG = "ServerProcessManager"

        @Volatile
        private var _instance: ServerProcessManager? = null

        val instance: ServerProcessManager
            get() = _instance ?: synchronized(this) {
                _instance ?: ServerProcessManager().also { _instance = it }
            }
    }

    private val processes = ConcurrentHashMap<String, ServerProcess>()

    // Global status map: serverId → ServerStatus (observed by ViewModels)
    private val _allStatuses = MutableStateFlow<Map<String, ServerStatus>>(emptyMap())
    val allStatuses: StateFlow<Map<String, ServerStatus>> = _allStatuses.asStateFlow()

    fun startServer(
        serverId: String,
        javaBinary: File,
        serverDir: File,
        ramMb: Int
    ) {
        if (processes.containsKey(serverId)) {
            Log.w(TAG, "[$serverId] Already has an active process — ignoring start request")
            return
        }

        val proc = ServerProcess(
            serverId = serverId,
            javaBinary = javaBinary,
            serverDir = serverDir,
            ramMb = ramMb,
            onStatusChange = { id, status -> updateStatus(id, status) }
        )
        processes[serverId] = proc
        proc.start()
    }

    suspend fun stopServer(serverId: String) {
        val proc = processes[serverId] ?: run {
            Log.w(TAG, "[$serverId] No active process to stop")
            return
        }
        proc.stop()
        // Process removal happens via onStatusChange when it reaches STOPPED/CRASHED
        processes.remove(serverId)
    }

    fun sendCommand(serverId: String, command: String) {
        processes[serverId]?.sendCommand(command)
    }

    fun getConsoleFlow(serverId: String) = processes[serverId]?.consoleLines

    fun getStatus(serverId: String): ServerStatus =
        processes[serverId]?.status?.value ?: ServerStatus.STOPPED

    fun isRunning(serverId: String): Boolean =
        processes[serverId]?.status?.value in listOf(
            ServerStatus.RUNNING, ServerStatus.STARTING, ServerStatus.STOPPING
        )

    private fun updateStatus(serverId: String, status: ServerStatus) {
        if (status == ServerStatus.STOPPED || status == ServerStatus.CRASHED) {
            processes.remove(serverId)
        }
        _allStatuses.value = _allStatuses.value.toMutableMap().also { it[serverId] = status }
        Log.d(TAG, "[$serverId] Status → $status")
    }

    /** Called when the ForegroundService is destroyed — forcibly kills all processes. */
    fun destroyAll() {
        processes.values.forEach { it.destroy() }
        processes.clear()
        _allStatuses.value = emptyMap()
    }
}
