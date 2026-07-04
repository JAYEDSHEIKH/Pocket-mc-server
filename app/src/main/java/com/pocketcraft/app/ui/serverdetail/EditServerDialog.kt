package com.pocketcraft.app.ui.serverdetail

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.pocketcraft.app.data.ServerProfile
import kotlin.math.roundToInt

@Composable
fun EditServerDialog(
    profile: ServerProfile,
    maxRamMb: Int = 4096,
    onSave: (name: String, ramMb: Int, customStartCommand: String?, notes: String) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember(profile.id) { mutableStateOf(profile.name) }
    var ramMb by remember(profile.id) { mutableIntStateOf(profile.ramMb) }
    var customCmd by remember(profile.id) { mutableStateOf(profile.customStartCommand ?: "") }
    var notes by remember(profile.id) { mutableStateOf(profile.serverNotes) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .padding(vertical = 24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 20.dp, end = 8.dp, top = 12.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Edit Server",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Cancel")
                    }
                }
                HorizontalDivider(modifier = Modifier.padding(horizontal = 20.dp))

                // Body
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Server name
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Server name") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                        modifier = Modifier.fillMaxWidth()
                    )

                    // RAM slider
                    val totalPositions = ((maxRamMb - 512) / 512) + 1
                    val steps = (totalPositions - 2).coerceAtLeast(0)
                    Column {
                        Text(
                            "RAM: $ramMb MB",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Slider(
                            value = ramMb.toFloat(),
                            onValueChange = {
                                ramMb = ((it / 512f).roundToInt() * 512).coerceIn(512, maxRamMb)
                            },
                            valueRange = 512f..maxRamMb.toFloat(),
                            steps = steps,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("512 MB", style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("$maxRamMb MB", style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }

                    // Custom start command
                    OutlinedTextField(
                        value = customCmd,
                        onValueChange = { customCmd = it },
                        label = { Text("Custom JVM flags (optional)") },
                        placeholder = { Text("-Xmx${profile.ramMb}M -Xms${profile.ramMb / 2}M -XX:+UseG1GC …") },
                        supportingText = {
                            Text(
                                "Override default JVM arguments. Leave blank to use defaults.\n" +
                                "Do not include -jar server.jar or java itself.",
                                style = MaterialTheme.typography.labelSmall
                            )
                        },
                        minLines = 2,
                        maxLines = 4,
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Notes
                    OutlinedTextField(
                        value = notes,
                        onValueChange = { notes = it },
                        label = { Text("Notes (optional)") },
                        minLines = 2,
                        maxLines = 4,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                HorizontalDivider(modifier = Modifier.padding(horizontal = 20.dp))

                // Buttons
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(end = 16.dp, bottom = 12.dp, top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
                ) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    Button(
                        onClick = {
                            val cmd = customCmd.trim().takeIf { it.isNotBlank() }
                            onSave(name.trim(), ramMb, cmd, notes.trim())
                            onDismiss()
                        },
                        enabled = name.isNotBlank()
                    ) { Text("Save") }
                }
            }
        }
    }
}
