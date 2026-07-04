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

    // ── Theme ─────────────────────────────────────────────────────────────────

    private val _themeMode = MutableStateFlow(ThemeMode.SYSTEM)
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    fun setThemeMode(mode: ThemeMode) {
        _themeMode.value = mode
        prefs.edit().putInt("theme_mode", mode.ordinal).apply()
    }

    // ── Server stop timeout ───────────────────────────────────────────────────
    // How long (seconds) to wait for a graceful "stop" command before force-kill

    private val _stopTimeoutSeconds = MutableStateFlow(30)
    val stopTimeoutSeconds: StateFlow<Int> = _stopTimeoutSeconds.asStateFlow()

    fun setStopTimeout(seconds: Int) {
        val clamped = seconds.coerceIn(5, 120)
        _stopTimeoutSeconds.value = clamped
        prefs.edit().putInt("stop_timeout_seconds", clamped).apply()
    }

    // ── Console max lines ─────────────────────────────────────────────────────

    private val _consoleMaxLines = MutableStateFlow(2000)
    val consoleMaxLines: StateFlow<Int> = _consoleMaxLines.asStateFlow()

    fun setConsoleMaxLines(lines: Int) {
        val clamped = lines.coerceIn(500, 10000)
        _consoleMaxLines.value = clamped
        prefs.edit().putInt("console_max_lines", clamped).apply()
    }

    // ── Auto-scroll console ───────────────────────────────────────────────────

    private val _autoScrollConsole = MutableStateFlow(true)
    val autoScrollConsole: StateFlow<Boolean> = _autoScrollConsole.asStateFlow()

    fun setAutoScrollConsole(enabled: Boolean) {
        _autoScrollConsole.value = enabled
        prefs.edit().putBoolean("auto_scroll_console", enabled).apply()
    }

    // ── Keep screen on while server runs ─────────────────────────────────────

    private val _keepScreenOn = MutableStateFlow(false)
    val keepScreenOn: StateFlow<Boolean> = _keepScreenOn.asStateFlow()

    fun setKeepScreenOn(enabled: Boolean) {
        _keepScreenOn.value = enabled
        prefs.edit().putBoolean("keep_screen_on", enabled).apply()
    }

    // ── Init ──────────────────────────────────────────────────────────────────

    fun init(context: Context) {
        prefs = context.getSharedPreferences("pocketcraft_prefs", Context.MODE_PRIVATE)
        _themeMode.value = ThemeMode.entries.getOrElse(prefs.getInt("theme_mode", 0)) { ThemeMode.SYSTEM }
        _stopTimeoutSeconds.value = prefs.getInt("stop_timeout_seconds", 30).coerceIn(5, 120)
        _consoleMaxLines.value = prefs.getInt("console_max_lines", 2000).coerceIn(500, 10000)
        _autoScrollConsole.value = prefs.getBoolean("auto_scroll_console", true)
        _keepScreenOn.value = prefs.getBoolean("keep_screen_on", false)
    }
}
