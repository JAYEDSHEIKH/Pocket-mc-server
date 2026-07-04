package com.pocketcraft.app.ui.createserver

import android.content.Context
import android.app.ActivityManager
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.*
import com.pocketcraft.app.core.downloader.DownloadWorker
import com.pocketcraft.app.core.downloader.PaperApi
import com.pocketcraft.app.core.downloader.latestStable
import com.pocketcraft.app.core.downloader.serverJarUrl
import com.pocketcraft.app.core.downloader.stableVersionsSorted
import com.pocketcraft.app.core.storage.StorageManager
import com.pocketcraft.app.data.LoaderType
import com.pocketcraft.app.data.ServerProfile
import com.pocketcraft.app.data.ServerProfileDao
import com.pocketcraft.app.data.ServerStatus
import com.pocketcraft.app.ui.components.AppError
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

sealed class VersionLoadState {
    data object Loading : VersionLoadState()
    data class Success(val versions: List<String>) : VersionLoadState()
    data class Error(val message: String) : VersionLoadState()
}

data class CreateServerUiState(
    val step: Int = 1,
    val name: String = "",
    val nameError: String? = null,
    val loaderType: LoaderType = LoaderType.PAPER,
    val versionState: VersionLoadState = VersionLoadState.Loading,
    val selectedVersion: String = "",
    val ramMb: Int = 1024,
    val eulaAccepted: Boolean = false,
    val isCreating: Boolean = false,
    val creationError: AppError? = null
)

@HiltViewModel
class CreateServerViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val paperApi: PaperApi,
    private val serverProfileDao: ServerProfileDao,
    private val storageManager: StorageManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(CreateServerUiState())
    val uiState: StateFlow<CreateServerUiState> = _uiState.asStateFlow()

    /** Total device RAM in MB — used for the slider warning threshold. */
    val deviceRamMb: Int by lazy {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val info = ActivityManager.MemoryInfo()
        am.getMemoryInfo(info)
        (info.totalMem / 1024 / 1024).toInt()
    }

    init {
        fetchVersions()
    }

    // ── Step navigation ───────────────────────────────────────────────────────

    fun nextStep() {
        val state = _uiState.value
        when (state.step) {
            1 -> {
                val trimmed = state.name.trim()
                if (trimmed.isBlank()) {
                    _uiState.update { it.copy(nameError = "Server name can't be empty") }
                    return
                }
                if (trimmed.length > 32) {
                    _uiState.update { it.copy(nameError = "Name must be 32 characters or fewer") }
                    return
                }
                _uiState.update { it.copy(name = trimmed, nameError = null, step = 2) }
            }
            2 -> _uiState.update { it.copy(step = 3) }
            3 -> {
                if (state.selectedVersion.isBlank()) return
                _uiState.update { it.copy(step = 4) }
            }
            4 -> _uiState.update { it.copy(step = 5) }
            5 -> createServer()
        }
    }

    fun prevStep() {
        val current = _uiState.value.step
        if (current > 1) _uiState.update { it.copy(step = current - 1) }
    }

    // ── Field updates ─────────────────────────────────────────────────────────

    fun updateName(name: String) = _uiState.update { it.copy(name = name, nameError = null) }
    fun updateLoader(type: LoaderType) = _uiState.update { it.copy(loaderType = type) }
    fun updateVersion(v: String) = _uiState.update { it.copy(selectedVersion = v) }
    fun updateRam(mb: Int) = _uiState.update { it.copy(ramMb = mb) }
    fun updateEula(accepted: Boolean) = _uiState.update { it.copy(eulaAccepted = accepted) }
    fun retryFetchVersions() = fetchVersions()
    fun clearCreationError() = _uiState.update { it.copy(creationError = null) }

    // ── API calls ─────────────────────────────────────────────────────────────

    private fun fetchVersions() {
        viewModelScope.launch {
            _uiState.update { it.copy(versionState = VersionLoadState.Loading) }
            runCatching {
                val project = paperApi.getProject()
                project.stableVersionsSorted()
            }.onSuccess { versions ->
                _uiState.update {
                    it.copy(
                        versionState = VersionLoadState.Success(versions),
                        selectedVersion = versions.firstOrNull() ?: ""
                    )
                }
            }.onFailure { e ->
                Log.e("CreateServerVM", "Failed to fetch versions", e)
                _uiState.update {
                    it.copy(versionState = VersionLoadState.Error(
                        "Could not load Minecraft versions.\n${e.message ?: "Network error"}\n\nCheck your internet connection and try again."
                    ))
                }
            }
        }
    }

    // ── Create server ─────────────────────────────────────────────────────────

    private fun createServer() {
        val state = _uiState.value
        if (!state.eulaAccepted || state.isCreating) return

        viewModelScope.launch {
            _uiState.update { it.copy(isCreating = true, creationError = null) }

            runCatching {
                // Fetch the latest stable build for the chosen version
                val builds = runCatching { paperApi.getBuilds(state.selectedVersion) }
                    .getOrElse { e ->
                        throw IllegalStateException(
                            "Could not fetch build info for Minecraft ${state.selectedVersion}.\n\n${e.message}\n\nCheck your internet connection.",
                            e
                        )
                    }

                val build = builds.latestStable()
                    ?: throw IllegalStateException(
                        "No stable builds found for Minecraft ${state.selectedVersion}.\n\nTry a different version."
                    )
                val downloadUrl = runCatching { build.serverJarUrl() }
                    .getOrElse { e ->
                        throw IllegalStateException(
                            "Could not determine the download URL for the server jar.\n\n${e.message}",
                            e
                        )
                    }

                // Insert profile into DB first so it gets an ID
                val profile = ServerProfile(
                    name = state.name,
                    loaderType = state.loaderType,
                    minecraftVersion = state.selectedVersion,
                    ramMb = state.ramMb,
                    status = ServerStatus.DOWNLOADING,
                    eulaAccepted = true
                )
                serverProfileDao.insert(profile)

                // Prepare server directory using StorageManager (consistent with all other code paths)
                val serverDir = storageManager.createServerDirs(profile.id)

                // Write eula.txt (Mojang requires this before the server will start)
                File(serverDir, "eula.txt").writeText(
                    "# EULA accepted via PocketCraft on-device confirmation\n" +
                    "eula=true\n"
                )

                // Write default server.properties.
                // RCON is enabled with a randomly generated per-server password
                // so the Players tab (Phase 2) can connect without user configuration.
                val rconPassword = generateRconPassword()
                File(serverDir, "server.properties").writeText(
                    "# Minecraft server properties — generated by PocketCraft\n" +
                    "server-port=25565\n" +
                    "online-mode=true\n" +
                    "max-players=20\n" +
                    "view-distance=10\n" +
                    "simulation-distance=10\n" +
                    "difficulty=normal\n" +
                    "gamemode=survival\n" +
                    "enable-rcon=true\n" +
                    "rcon.port=25575\n" +
                    "rcon.password=$rconPassword\n" +
                    "enable-query=false\n" +
                    "motd=A PocketCraft Minecraft Server\n"
                )

                // Enqueue WorkManager download (survives app backgrounding)
                val work = OneTimeWorkRequestBuilder<DownloadWorker>()
                    .setInputData(
                        DownloadWorker.buildInputData(
                            serverId = profile.id,
                            downloadUrl = downloadUrl,
                            destPath = File(serverDir, "server.jar").absolutePath
                        )
                    )
                    .setConstraints(
                        Constraints.Builder()
                            .setRequiredNetworkType(NetworkType.CONNECTED)
                            .build()
                    )
                    .addTag("download_${profile.id}")
                    .build()

                WorkManager.getInstance(context).enqueue(work)
                profile.id

            }.onSuccess { serverId ->
                _uiState.update { it.copy(isCreating = false) }
                _createdServerId.value = serverId

            }.onFailure { e ->
                Log.e("CreateServerVM", "Failed to create server", e)
                _uiState.update {
                    it.copy(
                        isCreating = false,
                        creationError = AppError(
                            title = "Server Creation Failed",
                            message = e.message ?: "An unexpected error occurred while creating the server.",
                            detail = e.stackTraceToString()
                        )
                    )
                }
            }
        }
    }

    // One-shot event: consumed by the screen to navigate away after creation
    private val _createdServerId = MutableStateFlow<String?>(null)
    val createdServerId: StateFlow<String?> = _createdServerId.asStateFlow()
    fun consumeCreatedServerId() = _createdServerId.update { null }

    companion object {
        private val RCON_CHARS = ('a'..'z') + ('A'..'Z') + ('0'..'9')

        /** Generates a cryptographically random 24-character RCON password. */
        private fun generateRconPassword(): String {
            val random = java.security.SecureRandom()
            return (1..24).map { RCON_CHARS[random.nextInt(RCON_CHARS.size)] }.joinToString("")
        }
    }
}
