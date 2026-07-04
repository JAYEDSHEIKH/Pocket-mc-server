package com.pocketcraft.app.ui.settings

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pocketcraft.app.AppPrefs
import com.pocketcraft.app.core.jre.JreInstaller
import com.pocketcraft.app.core.storage.StorageManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AppSettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val jreInstaller: JreInstaller,
    private val storageManager: StorageManager
) : ViewModel() {

    data class UiState(
        val isReextractingJre: Boolean = false,
        val reextractResult: String? = null,
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
                        jreStatus = "Not found — servers will not start until this APK includes the bundled JRE (built via GitHub Actions CI)"
                    )
                }
            }
        }
    }

    fun setThemeMode(mode: AppPrefs.ThemeMode) {
        AppPrefs.setThemeMode(mode)
    }

    fun storageDisplayPath(): String = storageManager.displayRootPath()

    /**
     * Deletes the extracted JRE directory and forces a full re-extraction.
     * Useful when extraction was corrupted or the JRE was partially extracted.
     */
    fun reextractJre() {
        viewModelScope.launch {
            _uiState.update { it.copy(isReextractingJre = true, reextractResult = null) }
            try {
                val jreDir = storageManager.jreDir
                jreDir.deleteRecursively()
                jreDir.mkdirs()
                val binary = jreInstaller.getJavaBinary()
                val result = if (binary != null) {
                    "JRE extracted successfully.\nBinary: ${binary.absolutePath}"
                } else {
                    "Extraction failed — this APK does not contain the JRE asset.\n\n" +
                    "The APK must be built via the GitHub Actions CI workflow which downloads " +
                    "and bundles the OpenJDK 21 aarch64 JRE automatically.\n\n" +
                    "Download a CI-built APK from your repository's Actions tab."
                }
                _uiState.update {
                    it.copy(
                        isReextractingJre = false,
                        reextractResult = result,
                        jrePresent = binary != null,
                        jreStatus = if (binary != null) "Installed — ${binary.absolutePath}"
                                    else "Not found — build via GitHub Actions CI"
                    )
                }
            } catch (e: Exception) {
                Log.e("AppSettingsVM", "JRE re-extract failed", e)
                _uiState.update {
                    it.copy(
                        isReextractingJre = false,
                        reextractResult = "Error: ${e.message}"
                    )
                }
            }
        }
    }

    fun clearReextractResult() = _uiState.update { it.copy(reextractResult = null) }
}
