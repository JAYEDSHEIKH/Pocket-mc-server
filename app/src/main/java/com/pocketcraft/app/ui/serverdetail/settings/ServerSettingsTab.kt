package com.pocketcraft.app.ui.serverdetail.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.pocketcraft.app.data.ServerProfile
import com.pocketcraft.app.data.ServerStatus
import com.pocketcraft.app.ui.serverdetail.ServerDetailViewModel
import kotlin.math.roundToInt

@Composable
fun ServerSettingsTab(
    profile: ServerProfile,
    liveStatus: ServerStatus,
    maxRamMb: Int,
    viewModel: ServerDetailViewModel,
    onServerDeleted: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Editable fields — reset when profile changes
    var name by remember(profile.id) { mutableStateOf(profile.name) }
    var ramMb by remember(profile.id) { mutableIntStateOf(profile.ramMb) }
    var customCmd by remember(profile.id) { mutableStateOf(profile.customStartCommand ?: "") }
    var notes by remember(profile.id) { mutableStateOf(profile.serverNotes) }

    val isDirty = name.trim() != profile.name ||
                  ramMb != profile.ramMb ||
                  customCmd.trim().takeIf { it.isNotBlank() } != profile.customStartCommand ||
                  notes.trim() != profile.serverNotes

    var showDeleteConfirm by remember { mutableStateOf(false) }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            icon = { Icon(Icons.Default.DeleteForever, null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Delete \"${profile.name}\"?") },
            text = {
                Text(
                    "This will permanently delete the server and all its world data, configs, and plugins. " +
                    "This cannot be undone."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    viewModel.deleteServer(onServerDeleted)
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") }
            }
        )
    }

    val isRunning = liveStatus == ServerStatus.RUNNING || liveStatus == ServerStatus.STARTING

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {

        // ── General ───────────────────────────────────────────────────────────
        SectionLabel("General")

        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Server name") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            modifier = Modifier.fillMaxWidth()
        )

        // ── RAM ───────────────────────────────────────────────────────────────
        SectionLabel("Memory")

        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "RAM allocation",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = MaterialTheme.shapes.small
                    ) {
                        Text(
                            "$ramMb MB",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
                val totalPositions = ((maxRamMb - 512) / 512) + 1
                val steps = (totalPositions - 2).coerceAtLeast(0)
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
                if (isRunning) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "RAM changes take effect after the next server restart.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                }
            }
        }

        // ── Start command ─────────────────────────────────────────────────────
        SectionLabel("Java Launch Options")

        OutlinedTextField(
            value = customCmd,
            onValueChange = { customCmd = it },
            label = { Text("Custom JVM flags (optional)") },
            placeholder = {
                Text(
                    "-Xmx${profile.ramMb}M -Xms${profile.ramMb / 2}M -XX:+UseG1GC",
                    style = MaterialTheme.typography.bodySmall
                )
            },
            supportingText = {
                Column {
                    Text(
                        "Override the default JVM arguments. Leave blank to use defaults.",
                        style = MaterialTheme.typography.labelSmall
                    )
                    Text(
                        "Do NOT include: java path, -jar server.jar, or nogui",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            minLines = 2,
            maxLines = 5,
            modifier = Modifier.fillMaxWidth()
        )

        // ── Notes ─────────────────────────────────────────────────────────────
        OutlinedTextField(
            value = notes,
            onValueChange = { notes = it },
            label = { Text("Notes (optional)") },
            minLines = 2,
            maxLines = 4,
            modifier = Modifier.fillMaxWidth()
        )

        // ── Save button ───────────────────────────────────────────────────────
        Button(
            onClick = {
                val cmd = customCmd.trim().takeIf { it.isNotBlank() }
                viewModel.saveSettings(name, ramMb, cmd, notes)
            },
            enabled = isDirty && name.isNotBlank(),
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Save changes")
        }

        // ── Server info (read-only) ───────────────────────────────────────────
        SectionLabel("Server Info")

        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(modifier = Modifier.fillMaxWidth()) {
                InfoItem("Minecraft version", profile.minecraftVersion, Icons.Default.SportsEsports)
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                InfoItem("Server type", profile.loaderType.name, Icons.Default.Storage)
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                InfoItem("Server ID", profile.id, Icons.Default.Fingerprint)
            }
        }

        // ── Danger zone ───────────────────────────────────────────────────────
        SectionLabel("Danger Zone")

        OutlinedButton(
            onClick = { showDeleteConfirm = true },
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
            border = ButtonDefaults.outlinedButtonBorder.copy(
                brush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.error.copy(alpha = 0.6f))
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.DeleteForever, null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Delete server & all data")
        }

        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
    )
}

@Composable
private fun InfoItem(label: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    ListItem(
        headlineContent = { Text(label, style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant) },
        supportingContent = { Text(value, style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium) },
        leadingContent = { Icon(icon, null, tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp)) }
    )
}
