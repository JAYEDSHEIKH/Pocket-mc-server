package com.pocketcraft.app.ui.createserver

import android.content.Context
import android.app.ActivityManager
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.*
import com.pocketcraft.app.core.downloader.DownloadWorker
import com.pocketcraft.app.core.downloader.PaperApi
import com.pocketcraft.app.core.downloader.applicationDownloadUrl
import com.pocketcraft.app.core.downloader.latestStable
import com.pocketcraft.app.data.LoaderType
import com.pocketcraft.app.data.ServerProfile
import com.pocketcraft.app.data.ServerProfileDao
import com.pocketcraft.app.data.ServerStatus
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
    val isCreating: Boolean = false
)

@HiltViewModel
class CreateServerViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val paperApi: PaperApi,
    private val serverProfileDao: ServerProfileDao
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

    // ── API calls ─────────────────────────────────────────────────────────────

    private fun fetchVersions() {
        viewModelScope.launch {
            _uiState.update { it.copy(versionState = VersionLoadState.Loading) }
            runCatching {
                val project = paperApi.getProject()
                // Newest versions first
                val sorted = project.versions.sortedDescending()
                sorted
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
                    it.copy(versionState = VersionLoadState.Error(e.message ?: "Unknown error"))
                }
            }
        }
    }

    // ── Create server ─────────────────────────────────────────────────────────

    private fun createServer() {
        val state = _uiState.value
        if (!state.eulaAccepted || state.isCreating) return

        viewModelScope.launch {
            _uiState.update { it.copy(isCreating = true) }

            runCatching {
                // Fetch the latest build URL for the chosen version
                val builds = paperApi.getBuilds(state.selectedVersion)
                val build = builds.latestStable()
                    ?: throw IllegalStateException("No builds found for ${state.selectedVersion}")
                val downloadUrl = build.applicationDownloadUrl()

                // Insert profile into DB
                val profile = ServerProfile(
                    name = state.name,
                    loaderType = state.loaderType,
                    minecraftVersion = state.selectedVersion,
                    ramMb = state.ramMb,
                    status = ServerStatus.DOWNLOADING,
                    eulaAccepted = true
                )
                serverProfileDao.insert(profile)

                // Prepare server directory
                val serverDir = File(context.filesDir, "servers/${profile.id}")
                serverDir.mkdirs()

                // Write eula.txt (Mojang requires this before the server will start)
                File(serverDir, "eula.txt").writeText(
                    "# EULA accepted via PocketCraft on-device confirmation\n" +
                    "eula=true\n"
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
                // Navigation is handled by the screen observing a separate one-shot event
                _createdServerId.value = serverId
            }.onFailure { e ->
                Log.e("CreateServerVM", "Failed to create server", e)
                _uiState.update { it.copy(isCreating = false) }
            }
        }
    }

    // One-shot event: consumed by the screen to navigate away after creation
    private val _createdServerId = MutableStateFlow<String?>(null)
    val createdServerId: StateFlow<String?> = _createdServerId.asStateFlow()
    fun consumeCreatedServerId() = _createdServerId.update { null }
}
