package com.pocketcraft.app.ui.serverdetail

import android.app.ActivityManager
import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pocketcraft.app.AppPrefs
import com.pocketcraft.app.data.ServerStatus
import com.pocketcraft.app.ui.components.ErrorDialog
import com.pocketcraft.app.ui.components.ProgressDialog
import com.pocketcraft.app.ui.home.StatusDot
import com.pocketcraft.app.ui.serverdetail.console.ConsoleTab
import com.pocketcraft.app.ui.serverdetail.files.FilesTab
import com.pocketcraft.app.ui.serverdetail.settings.ServerSettingsTab

private val TAB_LABELS = listOf("Console", "Files", "Settings", "Players", "Plugins", "Backups")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServerDetailScreen(
    serverId: String,
    onNavigateBack: () -> Unit,
    viewModel: ServerDetailViewModel = hiltViewModel()
) {
    LaunchedEffect(serverId) { viewModel.init(serverId) }

    val profile       by viewModel.profile.collectAsStateWithLifecycle()
    val status        by viewModel.liveStatus.collectAsStateWithLifecycle()
    val consoleLines  by viewModel.consoleLines.collectAsStateWithLifecycle()
    val dlProgress    by viewModel.downloadProgress.collectAsStateWithLifecycle()
    val error         by viewModel.error.collectAsStateWithLifecycle()
    val loading       by viewModel.loading.collectAsStateWithLifecycle()

    val haptic       = LocalHapticFeedback.current
    val context      = LocalContext.current
    val view         = LocalView.current
    val keepScreenOn by AppPrefs.keepScreenOn.collectAsStateWithLifecycle()

    // Apply keep-screen-on when the pref is enabled AND a server is running
    DisposableEffect(keepScreenOn, status) {
        val shouldKeep = keepScreenOn && status == ServerStatus.RUNNING
        view.keepScreenOn = shouldKeep
        onDispose { view.keepScreenOn = false }
    }

    var selectedTab by remember { mutableIntStateOf(0) }
    var showEditDialog by remember { mutableStateOf(false) }

    // Device RAM for sliders
    val deviceRamMb = remember {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val info = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
        (info.totalMem / 1024 / 1024).toInt().coerceAtLeast(1024)
    }
    val maxRamMb = minOf(deviceRamMb, 8192)

    // Error / loading dialogs
    error?.let { ErrorDialog(error = it, onDismiss = viewModel::clearError) }
    loading?.let { ProgressDialog(title = it.title, message = it.message, progress = it.progress) }

    // Edit dialog
    if (showEditDialog && profile != null) {
        EditServerDialog(
            profile = profile!!,
            maxRamMb = maxRamMb,
            onSave = { name, ramMb, cmd, notes -> viewModel.saveSettings(name, ramMb, cmd, notes) },
            onDismiss = { showEditDialog = false }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        StatusDot(status = status)
                        Column {
                            Text(
                                text = profile?.name ?: "Server",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = status.label(),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    // Download progress bar replaces the start/stop indicator
                    if (status == ServerStatus.DOWNLOADING) {
                        val p = dlProgress
                        if (p != null) {
                            Column(
                                modifier = Modifier.padding(end = 12.dp).width(80.dp),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Text(
                                    "${(p * 100).toInt()}%",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                LinearProgressIndicator(
                                    progress = { p },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        } else {
                            CircularProgressIndicator(
                                modifier = Modifier.padding(end = 8.dp).size(24.dp),
                                strokeWidth = 2.dp
                            )
                        }
                    } else {
                        val isTransitioning = status in listOf(ServerStatus.STARTING, ServerStatus.STOPPING)
                        if (isTransitioning) {
                            CircularProgressIndicator(
                                modifier = Modifier.padding(end = 8.dp).size(24.dp), strokeWidth = 2.dp
                            )
                        } else {
                            val isRunning = status == ServerStatus.RUNNING
                            IconButton(onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                if (isRunning) viewModel.stopServer() else viewModel.startServer()
                            }) {
                                Icon(
                                    imageVector = if (isRunning) Icons.Default.Stop else Icons.Default.PlayArrow,
                                    contentDescription = if (isRunning) "Stop" else "Start",
                                    tint = if (isRunning) MaterialTheme.colorScheme.error
                                           else MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }

                    // Edit button
                    IconButton(onClick = { showEditDialog = true }) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit server")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            ScrollableTabRow(
                selectedTabIndex = selectedTab,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary,
                edgePadding = 0.dp
            ) {
                TAB_LABELS.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = {
                            Text(
                                title, style = MaterialTheme.typography.labelLarge,
                                color = if (selectedTab == index) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    )
                }
            }

            when (selectedTab) {
                0 -> ConsoleTab(
                    lines = consoleLines,
                    onSendCommand = viewModel::sendCommand,
                    serverStatus = status,
                    modifier = Modifier.fillMaxSize()
                )
                1 -> FilesTab(
                    viewModel = viewModel,
                    modifier = Modifier.fillMaxSize()
                )
                2 -> profile?.let { prof ->
                    ServerSettingsTab(
                        profile = prof,
                        liveStatus = status,
                        maxRamMb = maxRamMb,
                        viewModel = viewModel,
                        onServerDeleted = onNavigateBack,
                        modifier = Modifier.fillMaxSize()
                    )
                } ?: Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
                else -> ComingSoonTab(
                    tabName = TAB_LABELS[selectedTab],
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

@Composable
private fun ComingSoonTab(tabName: String, modifier: Modifier = Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Construction, contentDescription = null,
                modifier = Modifier.size(56.dp), tint = MaterialTheme.colorScheme.outline
            )
            Text("$tabName — coming soon", style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("This tab will be available in a future update.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.outline)
        }
    }
}

private fun ServerStatus.label(): String = when (this) {
    ServerStatus.STOPPED     -> "Stopped"
    ServerStatus.DOWNLOADING -> "Downloading…"
    ServerStatus.STARTING    -> "Starting…"
    ServerStatus.RUNNING     -> "Running"
    ServerStatus.STOPPING    -> "Stopping…"
    ServerStatus.CRASHED     -> "Crashed — tap ▶ to retry"
}
