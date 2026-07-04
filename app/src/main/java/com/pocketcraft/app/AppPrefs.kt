package com.pocketcraft.app

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Lightweight preferences singleton backed by SharedPreferences.
 * Initialized once in [PocketCraftApplication.onCreate].
 *
 * Exposes [StateFlow]s so Compose can observe changes reactively via
 * [androidx.lifecycle.compose.collectAsStateWithLifecycle].
 */
object AppPrefs {

    enum class ThemeMode { SYSTEM, DARK, LIGHT }

    private lateinit var prefs: android.content.SharedPreferences

    private val _themeMode = MutableStateFlow(ThemeMode.SYSTEM)
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    fun init(context: Context) {
        prefs = context.getSharedPreferences("pocketcraft_prefs", Context.MODE_PRIVATE)
        _themeMode.value = ThemeMode.entries.getOrElse(prefs.getInt("theme_mode", 0)) { ThemeMode.SYSTEM }
    }

    fun setThemeMode(mode: ThemeMode) {
        _themeMode.value = mode
        prefs.edit().putInt("theme_mode", mode.ordinal).apply()
    }
}
