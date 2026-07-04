package com.pocketcraft.app.ui.home

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pocketcraft.app.data.LoaderType
import com.pocketcraft.app.data.ServerProfile
import com.pocketcraft.app.data.ServerStatus
import com.pocketcraft.app.ui.serverdetail.EditServerDialog
import com.pocketcraft.app.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onCreateServer: () -> Unit,
    onOpenServer: (String) -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val servers by viewModel.servers.collectAsStateWithLifecycle()
    var serverToDelete by remember { mutableStateOf<ServerListItem?>(null) }
    var serverToEdit by remember { mutableStateOf<ServerListItem?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "PocketCraft",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(
                            Icons.Default.Settings,
                            contentDescription = "App Settings",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onCreateServer,
                icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
                text = { Text("New Server") },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->

        if (servers.isEmpty()) {
            EmptyState(modifier = Modifier.fillMaxSize().padding(paddingValues))
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(paddingValues),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(servers, key = { it.profile.id }) { item ->
                    ServerCard(
                        item = item,
                        onClick = { onOpenServer(item.profile.id) },
                        onStartStop = {
                            when (item.liveStatus) {
                                ServerStatus.STOPPED, ServerStatus.CRASHED -> viewModel.startServer(item.profile)
                                ServerStatus.RUNNING -> viewModel.stopServer(item.profile.id)
                                else -> Unit
                            }
                        },
                        onEdit   = { serverToEdit = item },
                        onDelete = { serverToDelete = item }
                    )
                }
                item { Spacer(Modifier.height(80.dp)) }
            }
        }
    }

    // Delete confirmation
    serverToDelete?.let { item ->
        AlertDialog(
            onDismissRequest = { serverToDelete = null },
            icon = { Icon(Icons.Default.DeleteForever, null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Delete \"${item.profile.name}\"?") },
            text = { Text("This will permanently delete the server and all its world data. This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteServer(item.profile)
                    serverToDelete = null
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { serverToDelete = null }) { Text("Cancel") }
            }
        )
    }

    // Edit dialog
    serverToEdit?.let { item ->
        EditServerDialog(
            profile = item.profile,
            onSave = { name, ramMb, cmd, notes ->
                viewModel.updateServer(item.profile, name, ramMb, cmd, notes)
            },
            onDismiss = { serverToEdit = null }
        )
    }
}

@Composable
private fun ServerCard(
    item: ServerListItem,
    onClick: () -> Unit,
    onStartStop: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    var menuExpanded by remember { mutableStateOf(false) }

    Card(
        modifier = modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 16.dp, bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            StatusDot(status = item.liveStatus)

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.profile.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(4.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    LoaderBadge(loaderType = item.profile.loaderType)
                    Text(
                        text = item.profile.minecraftVersion,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "• ${item.profile.ramMb} MB",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (item.liveStatus == ServerStatus.DOWNLOADING) {
                    Spacer(Modifier.height(6.dp))
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth(0.6f), color = StatusDownload)
                }
            }

            // Start/Stop button
            val isTransitioning = item.liveStatus in listOf(
                ServerStatus.STARTING, ServerStatus.STOPPING, ServerStatus.DOWNLOADING
            )
            if (isTransitioning) {
                CircularProgressIndicator(modifier = Modifier.size(36.dp), strokeWidth = 3.dp)
            } else {
                val isRunning = item.liveStatus == ServerStatus.RUNNING
                FilledTonalIconButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onStartStop()
                    },
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = if (isRunning) MaterialTheme.colorScheme.errorContainer
                                        else MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Icon(
                        imageVector = if (isRunning) Icons.Default.Stop else Icons.Default.PlayArrow,
                        contentDescription = if (isRunning) "Stop server" else "Start server",
                        tint = if (isRunning) MaterialTheme.colorScheme.error
                               else MaterialTheme.colorScheme.primary
                    )
                }
            }

            // 3-dot overflow menu
            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(
                        Icons.Default.MoreVert,
                        contentDescription = "Server options",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                    DropdownMenuItem(
                        text = { Text("Edit") },
                        leadingIcon = { Icon(Icons.Default.Edit, null) },
                        onClick = { menuExpanded = false; onEdit() }
                    )
                    DropdownMenuItem(
                        text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                        leadingIcon = {
                            Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error)
                        },
                        onClick = { menuExpanded = false; onDelete() }
                    )
                }
            }
        }
    }
}

@Composable
fun StatusDot(status: ServerStatus, modifier: Modifier = Modifier) {
    val color = statusColor(status)
    val shouldPulse = status == ServerStatus.RUNNING
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.85f, targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ), label = "pulseDot"
    )
    Box(
        modifier = modifier.size(12.dp)
            .scale(if (shouldPulse) pulseScale else 1f)
            .clip(CircleShape).background(color)
    )
}

@Composable
fun statusColor(status: ServerStatus): Color = when (status) {
    ServerStatus.RUNNING    -> StatusRunning
    ServerStatus.STOPPED    -> StatusStopped
    ServerStatus.STARTING,
    ServerStatus.STOPPING   -> StatusStarting
    ServerStatus.CRASHED    -> StatusCrashed
    ServerStatus.DOWNLOADING -> StatusDownload
}

@Composable
fun LoaderBadge(loaderType: LoaderType, modifier: Modifier = Modifier) {
    val (text, color) = when (loaderType) {
        LoaderType.PAPER  -> "Paper"  to Color(0xFF69B3EC)
        LoaderType.FABRIC -> "Fabric" to Color(0xFFB2A8E0)
    }
    Surface(
        modifier = modifier, shape = RoundedCornerShape(4.dp),
        color = color.copy(alpha = 0.18f), contentColor = color
    ) {
        Text(
            text = text, style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

@Composable
private fun EmptyState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Rounded.Dns, contentDescription = null,
            modifier = Modifier.size(80.dp), tint = MaterialTheme.colorScheme.outline
        )
        Spacer(Modifier.height(24.dp))
        Text("No servers yet", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Text(
            "Tap the button below to set up your first\nMinecraft Java server — right on this device.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}
