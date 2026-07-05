package com.pocketcraft.app.ui.settings

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pocketcraft.app.AppPrefs
import com.pocketcraft.app.core.jre.JreInstaller
import com.pocketcraft.app.core.storage.StorageManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AppSettingsViewModel @Inject constructor(
    private val jreInstaller: JreInstaller,
    private val storageManager: StorageManager
) : ViewModel() {

    data class UiState(
        val isWorking: Boolean = false,
        /** null = progress unknown (querying API / extracting); 0f..1f = download progress */
        val downloadProgress: Float? = null,
        val downloadedMb: Float = 0f,
        val totalMb: Float = 0f,
        val workPhase: String = "",          // e.g. "Querying API…", "Downloading…", "Extracting…"
        val actionResult: String? = null,    // success / failure message shown in dialog
        val jrePresent: Boolean = false,
        val jreStatus: String = "Checking…"
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        checkJreStatus()
    }

    private fun checkJreStatus() {
        viewModelScope.launch(Dispatchers.IO) {
            val binary = jreInstaller.findExistingBinary()
            if (binary != null) {
                _uiState.update {
                    it.copy(
                        jrePresent = true,
                        jreStatus = "Installed — ${binary.absolutePath}"
                    )
                }
            } else {
                _uiState.update {
                    it.copy(
                        jrePresent = false,
                        jreStatus = "Not downloaded yet — will be fetched automatically when you start a server"
                    )
                }
            }
        }
    }

    fun setThemeMode(mode: AppPrefs.ThemeMode) = AppPrefs.setThemeMode(mode)

    fun storageDisplayPath(): String = storageManager.displayRootPath()

    /**
     * Deletes any existing JRE installation and triggers a fresh download from Adoptium.
     * Useful when the installation is corrupted or the user wants to update the JRE.
     */
    fun redownloadJre() {
        viewModelScope.launch {
            _uiState.update { it.copy(isWorking = true, actionResult = null, workPhase = "Starting…") }

            // Observe JreInstaller state to relay progress to the UI
            val progressJob = launch {
                jreInstaller.state.collect { state ->
                    when (state) {
                        is JreInstaller.State.Querying -> _uiState.update {
                            it.copy(workPhase = state.message, downloadProgress = null)
                        }
                        is JreInstaller.State.Downloading -> _uiState.update {
                            it.copy(
                                workPhase = "Downloading OpenJDK 21…",
                                downloadProgress = state.progress,
                                downloadedMb = state.downloadedMb,
                                totalMb = state.totalMb
                            )
                        }
                        is JreInstaller.State.Extracting -> _uiState.update {
                            it.copy(workPhase = "Extracting…", downloadProgress = null)
                        }
                        else -> { /* Ready / Failed / Idle handled after getJavaBinary() returns */ }
                    }
                }
            }

            try {
                // Wipe previous installation so getJavaBinary() re-downloads
                storageManager.jreDir.deleteRecursively()
                storageManager.jreDir.mkdirs()

                val binary = jreInstaller.getJavaBinary()
                progressJob.cancel()

                if (binary != null) {
                    _uiState.update {
                        it.copy(
                            isWorking = false,
                            actionResult = "OpenJDK 21 downloaded successfully.\nBinary: ${binary.absolutePath}",
                            jrePresent = true,
                            jreStatus = "Installed — ${binary.absolutePath}"
                        )
                    }
                } else {
                    _uiState.update {
                        it.copy(
                            isWorking = false,
                            actionResult = "Download failed — check your internet connection and try again.",
                            jrePresent = false,
                            jreStatus = "Not installed — tap 'Download JRE' to retry"
                        )
                    }
                }
            } catch (e: Exception) {
                progressJob.cancel()
                Log.e("AppSettingsVM", "JRE re-download failed", e)
                _uiState.update {
                    it.copy(
                        isWorking = false,
                        actionResult = "Error: ${e.message}"
                    )
                }
            }
        }
    }

    fun clearActionResult() = _uiState.update { it.copy(actionResult = null) }
}
