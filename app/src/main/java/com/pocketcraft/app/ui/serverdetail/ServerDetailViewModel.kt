package com.pocketcraft.app.ui.serverdetail

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.pocketcraft.app.core.downloader.DownloadWorker
import com.pocketcraft.app.core.process.ServerForegroundService
import com.pocketcraft.app.core.process.ServerProcessManager
import com.pocketcraft.app.core.storage.StorageManager
import com.pocketcraft.app.data.ServerProfile
import com.pocketcraft.app.data.ServerProfileDao
import com.pocketcraft.app.data.ServerStatus
import com.pocketcraft.app.ui.components.AppError
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

data class FileEntry(
    val file: File,
    val isDirectory: Boolean = file.isDirectory,
    val sizeBytes: Long = if (file.isFile) file.length() else 0L,
    val lastModified: Long = file.lastModified()
)

data class FileManagerState(
    val rootDir: File? = null,
    val currentDir: File? = null,
    val entries: List<FileEntry> = emptyList(),
    val isLoading: Boolean = false,
    val uploadProgress: Map<String, Float> = emptyMap()
)

data class LoadingState(
    val title: String,
    val message: String,
    val progress: Float? = null
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ServerDetailViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val serverProfileDao: ServerProfileDao,
    private val storageManager: StorageManager
) : ViewModel() {

    private val processManager = ServerProcessManager.instance
    private val _serverId = MutableStateFlow<String?>(null)

    // ── Profile ────────────────────────────────────────────────────────────────
    val profile: StateFlow<ServerProfile?> = _serverId
        .filterNotNull()
        .flatMapLatest { id -> serverProfileDao.getServerById(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    // ── Live status ────────────────────────────────────────────────────────────
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

    // ── Console lines ──────────────────────────────────────────────────────────
    val consoleLines: StateFlow<List<String>> = _serverId
        .filterNotNull()
        .flatMapLatest { id ->
            processManager.allStatuses
                .map { it[id] }
                .distinctUntilChanged()
                .flatMapLatest {
                    processManager.getConsoleFlow(id)
                        ?.scan(emptyList<String>()) { acc, line -> (acc + line).takeLast(2_000) }
                        ?: flowOf(emptyList())
                }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // ── Download progress (from WorkManager) ───────────────────────────────────
    private val _downloadProgress = MutableStateFlow<Float?>(null)
    val downloadProgress: StateFlow<Float?> = _downloadProgress.asStateFlow()

    // ── Error events ───────────────────────────────────────────────────────────
    private val _error = MutableStateFlow<AppError?>(null)
    val error: StateFlow<AppError?> = _error.asStateFlow()
    fun clearError() = _error.update { null }

    // ── Loading overlay ────────────────────────────────────────────────────────
    private val _loading = MutableStateFlow<LoadingState?>(null)
    val loading: StateFlow<LoadingState?> = _loading.asStateFlow()

    // ── File manager ───────────────────────────────────────────────────────────
    private val _fileState = MutableStateFlow(FileManagerState())
    val fileState: StateFlow<FileManagerState> = _fileState.asStateFlow()

    // ── Initialization ─────────────────────────────────────────────────────────
    fun init(serverId: String) {
        if (_serverId.value == serverId) return
        _serverId.value = serverId

        val serverDir = storageManager.resolveServerDir(serverId)
        _fileState.update { it.copy(rootDir = serverDir, currentDir = serverDir) }
        loadFiles(serverDir)

        // Observe download progress from WorkManager
        viewModelScope.launch {
            WorkManager.getInstance(context)
                .getWorkInfosByTagFlow("download_$serverId")
                .collect { infos ->
                    val running = infos.firstOrNull { it.state == WorkInfo.State.RUNNING }
                    val progress = running?.let { info ->
                        val dl = info.progress.getLong(DownloadWorker.PROGRESS_BYTES_DOWNLOADED, 0L)
                        val total = info.progress.getLong(DownloadWorker.PROGRESS_BYTES_TOTAL, -1L)
                        if (total > 0L) dl.toFloat() / total.toFloat() else null
                    }
                    _downloadProgress.update { progress }
                }
        }
    }

    // ── Server control ─────────────────────────────────────────────────────────
    fun startServer() {
        val prof = profile.value ?: return
        viewModelScope.launch {
            try {
                serverProfileDao.updateStatus(prof.id, ServerStatus.STARTING)
                val serverDir = storageManager.resolveServerDir(prof.id)
                if (!serverDir.exists()) serverDir.mkdirs()
                context.startForegroundService(
                    ServerForegroundService.startServerIntent(
                        context, prof.id, prof.ramMb,
                        serverDir.absolutePath, prof.customStartCommand
                    )
                )
            } catch (e: Exception) {
                serverProfileDao.updateStatus(prof.id, ServerStatus.CRASHED)
                _error.update {
                    AppError(
                        title = "Failed to start server",
                        message = "The server could not be launched.\n${e.message}",
                        detail = e.stackTraceToString()
                    )
                }
            }
        }
    }

    fun stopServer() {
        val prof = profile.value ?: return
        viewModelScope.launch {
            context.startService(ServerForegroundService.stopServerIntent(context, prof.id))
        }
    }

    fun sendCommand(command: String) {
        val id = _serverId.value ?: return
        processManager.sendCommand(id, command)
    }

    // ── Server settings ────────────────────────────────────────────────────────
    fun saveSettings(name: String, ramMb: Int, customStartCommand: String?, notes: String) {
        val id = _serverId.value ?: return
        viewModelScope.launch {
            serverProfileDao.updateSettings(
                id = id,
                name = name.trim(),
                ramMb = ramMb,
                customStartCommand = customStartCommand?.trim()?.takeIf { it.isNotBlank() },
                notes = notes.trim()
            )
        }
    }

    fun deleteServer(onDeleted: () -> Unit) {
        val prof = profile.value ?: return
        viewModelScope.launch {
            _loading.update { LoadingState("Deleting server", "Stopping server and removing all files…") }
            try {
                if (processManager.isRunning(prof.id)) {
                    context.startService(ServerForegroundService.stopServerIntent(context, prof.id))
                    var waited = 0
                    while (processManager.isRunning(prof.id) && waited < 20) {
                        delay(500); waited++
                    }
                }
                serverProfileDao.delete(prof)
                withContext(Dispatchers.IO) {
                    storageManager.resolveServerDir(prof.id).deleteRecursively()
                }
                _loading.update { null }
                withContext(Dispatchers.Main) { onDeleted() }
            } catch (e: Exception) {
                _loading.update { null }
                _error.update {
                    AppError("Delete failed", "Could not delete server: ${e.message}", e.stackTraceToString())
                }
            }
        }
    }

    // ── File manager operations ────────────────────────────────────────────────
    fun loadFiles(dir: File? = _fileState.value.currentDir) {
        val target = dir ?: return
        viewModelScope.launch(Dispatchers.IO) {
            _fileState.update { it.copy(isLoading = true) }
            val entries = runCatching {
                target.listFiles()
                    ?.map { FileEntry(it) }
                    ?.sortedWith(compareBy<FileEntry> { !it.isDirectory }.thenBy { it.file.name.lowercase() })
                    ?: emptyList()
            }.getOrDefault(emptyList())
            _fileState.update { it.copy(currentDir = target, entries = entries, isLoading = false) }
        }
    }

    fun navigateTo(dir: File) = loadFiles(dir)

    fun navigateUp() {
        val current = _fileState.value.currentDir ?: return
        val root = _fileState.value.rootDir ?: return
        if (current == root) return
        loadFiles(current.parentFile?.takeIf { it.exists() } ?: root)
    }

    fun canNavigateUp(): Boolean {
        val current = _fileState.value.currentDir ?: return false
        val root = _fileState.value.rootDir ?: return false
        return current.canonicalPath != root.canonicalPath
    }

    fun deleteFile(file: File) {
        viewModelScope.launch {
            _loading.update { LoadingState("Deleting", "Removing ${file.name}…") }
            withContext(Dispatchers.IO) {
                if (file.isDirectory) file.deleteRecursively() else file.delete()
            }
            _loading.update { null }
            loadFiles()
        }
    }

    fun renameFile(file: File, newName: String) {
        val trimmed = newName.trim()
        if (trimmed.isBlank() || trimmed == file.name) return
        viewModelScope.launch(Dispatchers.IO) {
            val dest = File(file.parentFile, trimmed)
            if (dest.exists()) {
                _error.update { AppError("Rename failed", "A file named '$trimmed' already exists in this folder.") }
                return@launch
            }
            val ok = file.renameTo(dest)
            if (!ok) {
                _error.update { AppError("Rename failed", "Could not rename '${file.name}'. The file may be in use.") }
            }
            loadFiles()
        }
    }

    fun moveFile(file: File, targetDir: File) {
        if (file.parentFile?.canonicalPath == targetDir.canonicalPath) return
        viewModelScope.launch(Dispatchers.IO) {
            val dest = File(targetDir, file.name)
            if (dest.exists()) {
                _error.update { AppError("Move failed", "A file named '${file.name}' already exists in the target folder.") }
                return@launch
            }
            val moved = file.renameTo(dest)
            if (!moved) {
                runCatching {
                    if (file.isDirectory) { file.copyRecursively(dest); file.deleteRecursively() }
                    else { file.copyTo(dest); file.delete() }
                }.onFailure { e ->
                    _error.update { AppError("Move failed", "Could not move '${file.name}': ${e.message}", e.stackTraceToString()) }
                }
            }
            loadFiles()
        }
    }

    fun uploadFiles(uris: List<Uri>, destDir: File) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            uris.forEach { uri ->
                val name = resolveFileName(uri) ?: "upload_${System.currentTimeMillis()}"
                val destFile = File(destDir, name)
                _fileState.update { it.copy(uploadProgress = it.uploadProgress + (name to 0f)) }
                withContext(Dispatchers.IO) {
                    try {
                        val size = context.contentResolver
                            .openFileDescriptor(uri, "r")?.use { it.statSize } ?: -1L
                        var written = 0L
                        context.contentResolver.openInputStream(uri)?.use { input ->
                            destFile.outputStream().use { output ->
                                val buf = ByteArray(64 * 1024)
                                var read: Int
                                while (input.read(buf).also { read = it } != -1) {
                                    output.write(buf, 0, read)
                                    written += read
                                    if (size > 0) {
                                        val p = (written.toFloat() / size.toFloat()).coerceIn(0f, 1f)
                                        _fileState.update { it.copy(uploadProgress = it.uploadProgress + (name to p)) }
                                    }
                                }
                            }
                        }
                    } catch (e: Exception) {
                        destFile.delete()
                        _error.update {
                            AppError("Upload failed", "Could not upload '$name': ${e.message}", e.stackTraceToString())
                        }
                    }
                }
                _fileState.update { it.copy(uploadProgress = it.uploadProgress - name) }
            }
            loadFiles(destDir)
        }
    }

    private fun resolveFileName(uri: Uri): String? {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (cursor.moveToFirst() && idx >= 0) return cursor.getString(idx)
        }
        return uri.lastPathSegment
    }
}
