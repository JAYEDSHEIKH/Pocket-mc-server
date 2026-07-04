package com.pocketcraft.app.ui.serverdetail.console

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.pocketcraft.app.data.ServerStatus
import com.pocketcraft.app.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun ConsoleTab(
    lines: List<String>,
    onSendCommand: (String) -> Unit,
    serverStatus: ServerStatus,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var command by remember { mutableStateOf("") }
    val commandHistory = remember { mutableStateListOf<String>() }
    var historyIndex by remember { mutableIntStateOf(-1) }

    // Track whether user has scrolled up (to show "jump to latest" button)
    val isScrolledToBottom by remember {
        derivedStateOf {
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
            lastVisible >= lines.size - 2
        }
    }

    // Auto-scroll to bottom when new lines arrive (if already at bottom)
    LaunchedEffect(lines.size) {
        if (isScrolledToBottom && lines.isNotEmpty()) {
            listState.animateScrollToItem(lines.size - 1)
        }
    }

    Column(
        modifier = modifier.background(Color(0xFF060A10))  // slightly darker than app bg for terminal feel
    ) {
        // ── Console log area ───────────────────────────────────────────────
        Box(modifier = Modifier.weight(1f)) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(1.dp)
            ) {
                if (lines.isEmpty()) {
                    item {
                        Text(
                            text = when (serverStatus) {
                                ServerStatus.STOPPED -> "Server is not running. Press ▶ to start."
                                ServerStatus.STARTING -> "Server is starting…"
                                ServerStatus.DOWNLOADING -> "Downloading server jar…"
                                else -> "No output yet."
                            },
                            style = ConsoleTextStyle,
                            color = White40,
                            modifier = Modifier.padding(top = 16.dp)
                        )
                    }
                } else {
                    items(lines) { line ->
                        Text(
                            text = colorizeLogLine(line),
                            style = ConsoleTextStyle,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            // "Jump to latest" pill — visible when scrolled up
            if (!isScrolledToBottom && lines.isNotEmpty()) {
                SmallFloatingActionButton(
                    onClick = {
                        scope.launch { listState.animateScrollToItem(lines.size - 1) }
                    },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 8.dp),
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.primary
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.padding(horizontal = 8.dp)
                    ) {
                        Icon(Icons.Default.KeyboardArrowDown,
                            contentDescription = null, modifier = Modifier.size(16.dp))
                        Text("Jump to latest", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))

        // ── Command input bar ──────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = ">",
                style = ConsoleTextStyle,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            TextField(
                value = command,
                onValueChange = {
                    command = it
                    historyIndex = -1
                },
                placeholder = {
                    Text(
                        "Enter command…",
                        style = ConsoleTextStyle,
                        color = White40
                    )
                },
                singleLine = true,
                enabled = serverStatus == ServerStatus.RUNNING,
                textStyle = ConsoleTextStyle.copy(color = White95),
                colors = TextFieldDefaults.colors(
                    unfocusedContainerColor = Color.Transparent,
                    focusedContainerColor = Color.Transparent,
                    disabledContainerColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    disabledIndicatorColor = Color.Transparent
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = {
                    submitCommand(command, commandHistory) { cmd ->
                        onSendCommand(cmd)
                        command = ""
                        historyIndex = -1
                    }
                }),
                modifier = Modifier.weight(1f)
            )
            IconButton(
                onClick = {
                    submitCommand(command, commandHistory) { cmd ->
                        onSendCommand(cmd)
                        command = ""
                        historyIndex = -1
                    }
                },
                enabled = command.isNotBlank() && serverStatus == ServerStatus.RUNNING
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.Send,
                    contentDescription = "Send command",
                    tint = if (command.isNotBlank()) MaterialTheme.colorScheme.primary
                           else MaterialTheme.colorScheme.outline
                )
            }
        }
    }
}

private fun submitCommand(
    command: String,
    history: MutableList<String>,
    onSend: (String) -> Unit
) {
    val trimmed = command.trim()
    if (trimmed.isBlank()) return
    if (history.isEmpty() || history.last() != trimmed) {
        history.add(trimmed)
        if (history.size > 50) history.removeAt(0)
    }
    onSend(trimmed)
}

/**
 * Colorizes a console line based on its Minecraft log level.
 * Patterns recognized: [HH:MM:SS] [Thread/LEVEL]: message
 */
@Composable
private fun colorizeLogLine(line: String): AnnotatedString {
    return buildAnnotatedString {
        val color = when {
            line.contains("[ERROR]", ignoreCase = true) ||
            line.contains("Exception", ignoreCase = false) ||
            line.contains("FAILED", ignoreCase = false) -> LogError

            line.contains("[WARN]", ignoreCase = true) -> LogWarn

            line.contains("joined the game", ignoreCase = true) ||
            line.contains("left the game", ignoreCase = true) -> LogJoin

            line.contains("[INFO]", ignoreCase = true) -> LogInfo

            else -> LogDefault
        }
        withStyle(SpanStyle(color = color)) {
            append(line)
        }
    }
}
