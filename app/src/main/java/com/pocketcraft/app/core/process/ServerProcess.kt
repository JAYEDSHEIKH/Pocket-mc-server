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
 * @param customJvmArgs  If non-null these JVM flags replace the built-in defaults.
 *                       Do NOT include the java binary path, -jar, server.jar, or nogui.
 */
class ServerProcess(
    val serverId: String,
    private val javaBinary: File,
    private val serverDir: File,
    private val ramMb: Int,
    private val customJvmArgs: List<String>? = null,
    private val onStatusChange: (String, ServerStatus) -> Unit
) {
    companion object {
        private const val TAG = "ServerProcess"
        private const val CONSOLE_BUFFER = 2_000
    }

    private val stopTimeoutMs: Long
        get() = com.pocketcraft.app.AppPrefs.stopTimeoutSeconds.value * 1000L

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _consoleLines = MutableSharedFlow<String>(
        replay = CONSOLE_BUFFER,
        extraBufferCapacity = 200
    )
    val consoleLines: SharedFlow<String> = _consoleLines.asSharedFlow()

    private val _status = MutableStateFlow(ServerStatus.STOPPED)
    val status: StateFlow<ServerStatus> = _status.asStateFlow()

    private var process: Process? = null
    private var stdin: PrintWriter? = null

    /** Human-readable description of the last failure, for display in error dialogs. */
    var lastError: String? = null
        private set

    fun start() {
        if (_status.value == ServerStatus.RUNNING || _status.value == ServerStatus.STARTING) return

        _status.value = ServerStatus.STARTING
        onStatusChange(serverId, ServerStatus.STARTING)

        scope.launch {
            runCatching {
                // ── Pre-flight checks ──────────────────────────────────────────
                val serverJar = File(serverDir, "server.jar")
                if (!serverJar.exists()) {
                    throw IllegalStateException(
                        "server.jar not found.\n" +
                        "Path: ${serverJar.absolutePath}\n" +
                        "Make sure the download completed successfully."
                    )
                }
                if (!javaBinary.exists() || !javaBinary.canExecute()) {
                    throw IllegalStateException(
                        "Java binary not found or not executable.\n" +
                        "Path: ${javaBinary.absolutePath}\n" +
                        "Try reinstalling the JRE from App Settings."
                    )
                }

                // ── Build command ──────────────────────────────────────────────
                val jvmArgs = customJvmArgs ?: listOf(
                    "-Xmx${ramMb}M",
                    "-Xms${ramMb / 2}M",
                    "-XX:+UseG1GC",
                    "-XX:+ParallelRefProcEnabled",
                    "-XX:MaxGCPauseMillis=200"
                )
                val cmd = listOf(javaBinary.absolutePath) + jvmArgs + listOf("-jar", "server.jar", "nogui")
                Log.i(TAG, "[$serverId] Launching: ${cmd.joinToString(" ")}")

                val pb = ProcessBuilder(cmd)
                    .directory(serverDir)
                    .redirectErrorStream(true)

                process = pb.start()
                stdin = PrintWriter(process!!.outputStream, true)

                _status.value = ServerStatus.RUNNING
                onStatusChange(serverId, ServerStatus.RUNNING)

                // Pipe stdout+stderr into the SharedFlow
                val reader = BufferedReader(InputStreamReader(process!!.inputStream))
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    _consoleLines.emit(line!!)
                }

                val exitCode = process!!.waitFor()
                Log.i(TAG, "[$serverId] Process exited with code $exitCode")

                if (exitCode != 0) {
                    lastError = "Process exited with code $exitCode. Check the Console tab for details."
                }
                val finalStatus = if (exitCode == 0) ServerStatus.STOPPED else ServerStatus.CRASHED
                _status.value = finalStatus
                onStatusChange(serverId, finalStatus)

            }.onFailure { e ->
                Log.e(TAG, "[$serverId] Server process error", e)
                lastError = e.message ?: e.javaClass.simpleName
                _status.value = ServerStatus.CRASHED
                onStatusChange(serverId, ServerStatus.CRASHED)
            }
        }
    }

    /** Sends a command to the server's stdin while it is running. */
    fun sendCommand(command: String) {
        if (_status.value != ServerStatus.RUNNING) return
        scope.launch { stdin?.println(command) }
    }

    /** Gracefully stops the server; force-kills after [STOP_TIMEOUT_MS]. */
    suspend fun stop() {
        if (_status.value == ServerStatus.STOPPED || _status.value == ServerStatus.STOPPING) return

        _status.value = ServerStatus.STOPPING
        onStatusChange(serverId, ServerStatus.STOPPING)

        // Write "stop" DIRECTLY to stdin — bypasses the sendCommand() RUNNING-only guard.
        withContext(Dispatchers.IO) {
            try {
                stdin?.println("stop")
                stdin?.flush()
            } catch (e: Exception) {
                Log.w(TAG, "[$serverId] Could not write stop to stdin: ${e.message}")
            }
        }

        withContext(Dispatchers.IO) {
            val proc = process ?: return@withContext
            runCatching {
                val exited = proc.waitFor(stopTimeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS)
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
