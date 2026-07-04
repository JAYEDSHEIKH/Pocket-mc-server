package com.pocketcraft.app.ui.serverdetail

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pocketcraft.app.data.ServerStatus
import com.pocketcraft.app.ui.home.StatusDot
import com.pocketcraft.app.ui.serverdetail.console.ConsoleTab

private val tabs = listOf("Console", "Players", "Files", "Plugins", "Properties", "Backups", "Network")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServerDetailScreen(
    serverId: String,
    onNavigateBack: () -> Unit,
    viewModel: ServerDetailViewModel = hiltViewModel()
) {
    LaunchedEffect(serverId) { viewModel.init(serverId) }

    val profile by viewModel.profile.collectAsStateWithLifecycle()
    val status by viewModel.liveStatus.collectAsStateWithLifecycle()
    val consoleLines by viewModel.consoleLines.collectAsStateWithLifecycle()

    var selectedTab by remember { mutableIntStateOf(0) }
    val haptic = LocalHapticFeedback.current

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
                    val isTransitioning = status in listOf(
                        ServerStatus.STARTING, ServerStatus.STOPPING, ServerStatus.DOWNLOADING
                    )
                    if (isTransitioning) {
                        CircularProgressIndicator(
                            modifier = Modifier.padding(end = 8.dp).size(24.dp),
                            strokeWidth = 2.dp
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
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = {
                            Text(
                                title,
                                style = MaterialTheme.typography.labelLarge,
                                color = if (selectedTab == index)
                                    MaterialTheme.colorScheme.primary
                                else
                                    MaterialTheme.colorScheme.onSurfaceVariant
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
                else -> ComingSoonTab(tabName = tabs[selectedTab], modifier = Modifier.fillMaxSize())
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
                imageVector = Icons.Default.Construction,
                contentDescription = null,
                modifier = Modifier.size(56.dp),
                tint = MaterialTheme.colorScheme.outline
            )
            Text(
                text = "$tabName — coming soon",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "This tab will be available in a future update.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.outline
            )
        }
    }
}

private fun ServerStatus.label(): String = when (this) {
    ServerStatus.STOPPED     -> "Stopped"
    ServerStatus.DOWNLOADING -> "Downloading…"
    ServerStatus.STARTING    -> "Starting…"
    ServerStatus.RUNNING     -> "Running"
    ServerStatus.STOPPING    -> "Stopping…"
    ServerStatus.CRASHED     -> "Crashed"
}
