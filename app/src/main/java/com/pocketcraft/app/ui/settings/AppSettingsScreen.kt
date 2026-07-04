package com.pocketcraft.app.ui.settings

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
import com.pocketcraft.app.ui.components.ProgressDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppSettingsScreen(
    onNavigateBack: () -> Unit,
    viewModel: AppSettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val themeMode by AppPrefs.themeMode.collectAsStateWithLifecycle()

    if (uiState.isReextractingJre) {
        ProgressDialog(title = "Re-extracting JRE", message = "Extracting bundled Java 21 from APK assets…")
    }

    uiState.reextractResult?.let { result ->
        AlertDialog(
            onDismissRequest = viewModel::clearReextractResult,
            title = { Text("JRE Re-extraction") },
            text = { Text(result) },
            confirmButton = {
                TextButton(onClick = viewModel::clearReextractResult) { Text("OK") }
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

            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
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

            // ── Runtime ───────────────────────────────────────────────────────
            SectionHeader("Runtime")

            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                ListItem(
                    headlineContent = { Text("Re-extract Java Runtime") },
                    supportingContent = { Text("Fixes issues if the bundled JRE is corrupted") },
                    leadingContent = {
                        Icon(Icons.Default.Refresh, contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary)
                    },
                    trailingContent = {
                        OutlinedButton(
                            onClick = viewModel::reextractJre,
                            enabled = !uiState.isReextractingJre
                        ) { Text("Re-extract") }
                    }
                )
            }

            Spacer(Modifier.height(8.dp))

            // ── About ─────────────────────────────────────────────────────────
            SectionHeader("About")

            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
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
                    headlineContent = { Text("Minecraft Java Edition Server") },
                    supportingContent = { Text("Powered by PaperMC — running on-device via bundled OpenJDK 21") },
                    leadingContent = {
                        Icon(Icons.Default.Storage, contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary)
                    }
                )
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 4.dp, bottom = 4.dp, top = 8.dp)
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
        leadingContent = { Icon(icon, contentDescription = null) },
        trailingContent = {
            if (selected) {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = "Selected",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        },
        modifier = Modifier.clickableIf(true, onClick)
    )
}

// Extension to avoid importing clickable separately
private fun Modifier.clickableIf(enabled: Boolean, onClick: () -> Unit): Modifier =
    if (enabled) this.then(Modifier) else this
