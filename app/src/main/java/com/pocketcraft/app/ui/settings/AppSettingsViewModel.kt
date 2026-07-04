package com.pocketcraft.app.ui.settings

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pocketcraft.app.AppPrefs
import com.pocketcraft.app.core.jre.JreInstaller
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AppSettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val jreInstaller: JreInstaller
) : ViewModel() {

    data class UiState(
        val isReextractingJre: Boolean = false,
        val reextractResult: String? = null
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    fun setThemeMode(mode: AppPrefs.ThemeMode) {
        AppPrefs.setThemeMode(mode)
    }

    /**
     * Deletes the extracted JRE directory and forces a full re-extraction
     * on the next server start.  Useful when extraction was corrupted.
     */
    fun reextractJre() {
        viewModelScope.launch {
            _uiState.update { it.copy(isReextractingJre = true, reextractResult = null) }
            try {
                val jreDir = context.filesDir.resolve("jre")
                jreDir.deleteRecursively()
                jreDir.mkdirs()
                val binary = jreInstaller.getJavaBinary()
                _uiState.update {
                    it.copy(
                        isReextractingJre = false,
                        reextractResult = if (binary != null)
                            "JRE extracted successfully.\nBinary: ${binary.absolutePath}"
                        else
                            "Extraction failed — check that the APK contains the JRE asset."
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
