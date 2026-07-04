package com.pocketcraft.app.core.process

import android.util.Log
import com.pocketcraft.app.data.ServerStatus
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.io.PrintWriter

/**
 * Wraps a single running Minecraft server child process.
 *
 * Lifecycle:
 *   start() → process is alive, console lines flow
 *   sendCommand(cmd) → writes to stdin
 *   stop() → sends "stop" to stdin, waits gracefully, then force-kills
 *   The process can also exit on its own (crash), which updates status to CRASHED.
 */
class ServerProcess(
    val serverId: String,
    private val javaBinary: File,
    private val serverDir: File,
    private val ramMb: Int,
    /** Callback invoked when the process exits (naturally or via stop()) */
    private val onStatusChange: (String, ServerStatus) -> Unit
) {
    companion object {
        private const val TAG = "ServerProcess"
        private const val CONSOLE_BUFFER = 2_000  // max lines held in memory
        private const val STOP_TIMEOUT_MS = 30_000L
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // Ring-buffer of recent console lines
    private val _consoleLines = MutableSharedFlow<String>(
        replay = CONSOLE_BUFFER,
        extraBufferCapacity = 200
    )
    val consoleLines: SharedFlow<String> = _consoleLines.asSharedFlow()

    private val _status = MutableStateFlow(ServerStatus.STOPPED)
    val status: StateFlow<ServerStatus> = _status.asStateFlow()

    private var process: Process? = null
    private var stdin: PrintWriter? = null

    fun start() {
        if (_status.value == ServerStatus.RUNNING || _status.value == ServerStatus.STARTING) return

        _status.value = ServerStatus.STARTING
        onStatusChange(serverId, ServerStatus.STARTING)

        scope.launch {
            runCatching {
                val heapG = maxOf(1, ramMb / 1024)
                val cmd = listOf(
                    javaBinary.absolutePath,
                    "-Xmx${ramMb}M",
                    "-Xms${ramMb / 2}M",
                    "-XX:+UseG1GC",
                    "-XX:+ParallelRefProcEnabled",
                    "-XX:MaxGCPauseMillis=200",
                    "-jar", "server.jar", "nogui"
                )
                Log.i(TAG, "[$serverId] Launching: ${cmd.joinToString(" ")}")

                val pb = ProcessBuilder(cmd)
                    .directory(serverDir)
                    .redirectErrorStream(true) // merge stderr into stdout

                process = pb.start()
                stdin = PrintWriter(process!!.outputStream, true)

                _status.value = ServerStatus.RUNNING
                onStatusChange(serverId, ServerStatus.RUNNING)

                // Pipe stdout/stderr into the SharedFlow
                val reader = BufferedReader(InputStreamReader(process!!.inputStream))
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    _consoleLines.emit(line!!)
                }

                // Process has exited
                val exitCode = process!!.waitFor()
                Log.i(TAG, "[$serverId] Process exited with code $exitCode")
                val finalStatus = if (exitCode == 0) ServerStatus.STOPPED else ServerStatus.CRASHED
                _status.value = finalStatus
                onStatusChange(serverId, finalStatus)

            }.onFailure { e ->
                Log.e(TAG, "[$serverId] Server process error", e)
                _status.value = ServerStatus.CRASHED
                onStatusChange(serverId, ServerStatus.CRASHED)
            }
        }
    }

    /** Sends a command to the server's stdin (e.g. "stop", "say Hello", "list"). */
    fun sendCommand(command: String) {
        if (_status.value != ServerStatus.RUNNING) return
        scope.launch {
            stdin?.println(command)
        }
    }

    /** Gracefully stops the server; force-kills after [STOP_TIMEOUT_MS]. */
    suspend fun stop() {
        if (_status.value == ServerStatus.STOPPED || _status.value == ServerStatus.STOPPING) return

        _status.value = ServerStatus.STOPPING
        onStatusChange(serverId, ServerStatus.STOPPING)

        sendCommand("stop")

        withContext(Dispatchers.IO) {
            val proc = process ?: return@withContext
            runCatching {
                val exited = proc.waitFor(STOP_TIMEOUT_MS, java.util.concurrent.TimeUnit.MILLISECONDS)
                if (!exited) {
                    Log.w(TAG, "[$serverId] Graceful stop timed out — force killing")
                    proc.destroyForcibly()
                }
            }
        }
    }

    fun destroy() {
        scope.cancel()
        process?.destroyForcibly()
    }
}
