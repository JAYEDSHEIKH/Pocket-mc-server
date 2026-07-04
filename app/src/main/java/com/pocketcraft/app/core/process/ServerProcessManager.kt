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
 * Access via [ServerProcessManager.instance].
 */
class ServerProcessManager private constructor() {

    companion object {
        private const val TAG = "ServerProcessManager"

        @Volatile private var _instance: ServerProcessManager? = null

        val instance: ServerProcessManager
            get() = _instance ?: synchronized(this) {
                _instance ?: ServerProcessManager().also { _instance = it }
            }
    }

    private val processes = ConcurrentHashMap<String, ServerProcess>()

    private val _allStatuses = MutableStateFlow<Map<String, ServerStatus>>(emptyMap())
    val allStatuses: StateFlow<Map<String, ServerStatus>> = _allStatuses.asStateFlow()

    fun startServer(
        serverId: String,
        javaBinary: File,
        serverDir: File,
        ramMb: Int,
        customStartCommand: String? = null
    ) {
        if (processes.containsKey(serverId)) {
            Log.w(TAG, "[$serverId] Already has an active process — ignoring start request")
            return
        }

        // Parse the custom command string into a JVM-args list.
        // Strip any trailing "-jar server.jar nogui" the user might have included.
        val customJvmArgs: List<String>? = customStartCommand
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?.split("\\s+".toRegex())
            ?.filter { it.isNotBlank() }
            ?.let { args ->
                val jarIdx = args.indexOf("-jar")
                if (jarIdx >= 0) args.take(jarIdx) else args
            }

        val proc = ServerProcess(
            serverId = serverId,
            javaBinary = javaBinary,
            serverDir = serverDir,
            ramMb = ramMb,
            customJvmArgs = customJvmArgs,
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
        processes.remove(serverId)
    }

    fun sendCommand(serverId: String, command: String) {
        processes[serverId]?.sendCommand(command)
    }

    fun getConsoleFlow(serverId: String) = processes[serverId]?.consoleLines

    fun getStatus(serverId: String): ServerStatus =
        processes[serverId]?.status?.value ?: ServerStatus.STOPPED

    /** Returns the last error description from a crashed process, or null. */
    fun getLastError(serverId: String): String? = processes[serverId]?.lastError

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

    fun destroyAll() {
        processes.values.forEach { it.destroy() }
        processes.clear()
        _allStatuses.value = emptyMap()
    }
}
