package com.pocketcraft.app.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pocketcraft.app.AppPrefs
import com.pocketcraft.app.BuildConfig

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppSettingsScreen(
    onNavigateBack: () -> Unit,
    viewModel: AppSettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val themeMode by AppPrefs.themeMode.collectAsStateWithLifecycle()
    val stopTimeout by AppPrefs.stopTimeoutSeconds.collectAsStateWithLifecycle()
    val consoleMaxLines by AppPrefs.consoleMaxLines.collectAsStateWithLifecycle()
    val autoScroll by AppPrefs.autoScrollConsole.collectAsStateWithLifecycle()
    val keepScreenOn by AppPrefs.keepScreenOn.collectAsStateWithLifecycle()

    // ── Working dialog (download / extract) ───────────────────────────────────
    if (uiState.isWorking) {
        AlertDialog(
            onDismissRequest = { /* not dismissable during work */ },
            title = { Text("Java Runtime") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(uiState.workPhase, style = MaterialTheme.typography.bodyMedium)
                    val progress = uiState.downloadProgress
                    if (progress != null) {
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier.fillMaxWidth()
                        )
                        if (uiState.totalMb > 0f) {
                            Text(
                                "%.1f / %.1f MB".format(uiState.downloadedMb, uiState.totalMb),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                }
            },
            confirmButton = {}
        )
    }

    // ── Result dialog ─────────────────────────────────────────────────────────
    uiState.actionResult?.let { result ->
        AlertDialog(
            onDismissRequest = viewModel::clearActionResult,
            title = { Text("Java Runtime") },
            text = { Text(result) },
            confirmButton = {
                TextButton(onClick = viewModel::clearActionResult) { Text("OK") }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("App Settings", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {

            // ── Appearance ────────────────────────────────────────────────────
            SectionHeader("Appearance")

            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(modifier = Modifier.fillMaxWidth().padding(4.dp)) {
                    ThemeModeItem(
                        label = "Follow system",
                        icon = Icons.Default.PhoneAndroid,
                        selected = themeMode == AppPrefs.ThemeMode.SYSTEM,
                        onClick = { viewModel.setThemeMode(AppPrefs.ThemeMode.SYSTEM) }
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    ThemeModeItem(
                        label = "Dark",
                        icon = Icons.Default.DarkMode,
                        selected = themeMode == AppPrefs.ThemeMode.DARK,
                        onClick = { viewModel.setThemeMode(AppPrefs.ThemeMode.DARK) }
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    ThemeModeItem(
                        label = "Light",
                        icon = Icons.Default.LightMode,
                        selected = themeMode == AppPrefs.ThemeMode.LIGHT,
                        onClick = { viewModel.setThemeMode(AppPrefs.ThemeMode.LIGHT) }
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            // ── Console ───────────────────────────────────────────────────────
            SectionHeader("Console")

            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    ListItem(
                        headlineContent = { Text("Auto-scroll to latest") },
                        supportingContent = { Text("Automatically scroll to the newest log line") },
                        leadingContent = {
                            Icon(Icons.Default.VerticalAlignBottom, null,
                                tint = MaterialTheme.colorScheme.primary)
                        },
                        trailingContent = {
                            Switch(
                                checked = autoScroll,
                                onCheckedChange = { AppPrefs.setAutoScrollConsole(it) }
                            )
                        }
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Max console lines", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                "$consoleMaxLines lines",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Slider(
                            value = consoleMaxLines.toFloat(),
                            onValueChange = { AppPrefs.setConsoleMaxLines(it.toInt()) },
                            valueRange = 500f..10000f,
                            steps = 18,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text(
                            "Higher values use more memory but preserve more history.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // ── Server behaviour ──────────────────────────────────────────────
            SectionHeader("Server Behaviour")

            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    ListItem(
                        headlineContent = { Text("Keep screen on while running") },
                        supportingContent = { Text("Prevents the screen from sleeping when a server is active") },
                        leadingContent = {
                            Icon(Icons.Default.Brightness7, null,
                                tint = MaterialTheme.colorScheme.primary)
                        },
                        trailingContent = {
                            Switch(
                                checked = keepScreenOn,
                                onCheckedChange = { AppPrefs.setKeepScreenOn(it) }
                            )
                        }
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Graceful stop timeout", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                "${stopTimeout}s",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Slider(
                            value = stopTimeout.toFloat(),
                            onValueChange = { AppPrefs.setStopTimeout(it.toInt()) },
                            valueRange = 5f..120f,
                            steps = 22,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text(
                            "How long to wait for the server to save and stop cleanly before force-killing it.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // ── Storage ───────────────────────────────────────────────────────
            SectionHeader("Storage")

            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                ListItem(
                    headlineContent = { Text("Server data location") },
                    supportingContent = { Text(viewModel.storageDisplayPath()) },
                    leadingContent = {
                        Icon(Icons.Default.FolderOpen, null,
                            tint = MaterialTheme.colorScheme.primary)
                    }
                )
            }

            Spacer(Modifier.height(8.dp))

            // ── Java Runtime ──────────────────────────────────────────────────
            SectionHeader("Java Runtime (JRE)")

            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    ListItem(
                        headlineContent = { Text("JRE Status") },
                        supportingContent = { Text(uiState.jreStatus) },
                        leadingContent = {
                            Icon(
                                if (uiState.jrePresent) Icons.Default.CheckCircle else Icons.Default.CloudDownload,
                                contentDescription = null,
                                tint = if (uiState.jrePresent) MaterialTheme.colorScheme.primary
                                       else MaterialTheme.colorScheme.secondary
                            )
                        }
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    ListItem(
                        headlineContent = {
                            Text(if (uiState.jrePresent) "Re-download Java Runtime" else "Download Java Runtime")
                        },
                        supportingContent = {
                            Text(
                                if (uiState.jrePresent)
                                    "Re-downloads OpenJDK 21 (aarch64) from adoptium.net (~50 MB)"
                                else
                                    "Downloads OpenJDK 21 (aarch64) from adoptium.net — required to run servers (~50 MB)"
                            )
                        },
                        leadingContent = {
                            Icon(Icons.Default.Download, contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary)
                        },
                        trailingContent = {
                            OutlinedButton(
                                onClick = viewModel::redownloadJre,
                                enabled = !uiState.isWorking
                            ) {
                                Text(if (uiState.jrePresent) "Re-download" else "Download")
                            }
                        }
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            // ── About ─────────────────────────────────────────────────────────
            SectionHeader("About")

            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    ListItem(
                        headlineContent = { Text("PocketCraft") },
                        supportingContent = { Text("Version ${BuildConfig.VERSION_NAME}") },
                        leadingContent = {
                            Icon(Icons.Default.Info, contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary)
                        }
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    ListItem(
                        headlineContent = { Text("Powered by PaperMC") },
                        supportingContent = { Text("Running on-device via OpenJDK 21 (aarch64) — downloaded at first use") },
                        leadingContent = {
                            Icon(Icons.Default.Storage, contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary)
                        }
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    ListItem(
                        headlineContent = { Text("Distribution") },
                        supportingContent = { Text("APK via GitHub Releases (not Play Store — this app runs a live JVM)") },
                        leadingContent = {
                            Icon(Icons.Default.OpenInNew, contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary)
                        }
                    )
                }
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}

// ── Small reusable composables ────────────────────────────────────────────────

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 4.dp, bottom = 2.dp)
    )
}

@Composable
private fun ThemeModeItem(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    onClick: () -> Unit
) {
    ListItem(
        headlineContent = { Text(label) },
        leadingContent = {
            Icon(icon, contentDescription = null,
                tint = if (selected) MaterialTheme.colorScheme.primary
                       else MaterialTheme.colorScheme.onSurfaceVariant)
        },
        trailingContent = {
            if (selected) Icon(Icons.Default.Check, contentDescription = "Selected",
                tint = MaterialTheme.colorScheme.primary)
        },
        modifier = Modifier.clickable(onClick = onClick)
    )
}
