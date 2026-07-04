package com.pocketcraft.app.ui.serverdetail.files

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pocketcraft.app.ui.serverdetail.FileEntry
import com.pocketcraft.app.ui.serverdetail.FileManagerState
import com.pocketcraft.app.ui.serverdetail.ServerDetailViewModel
import java.io.File
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun FilesTab(
    viewModel: ServerDetailViewModel,
    modifier: Modifier = Modifier
) {
    val fileState by viewModel.fileState.collectAsStateWithLifecycle()

    val filePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        val destDir = fileState.currentDir ?: return@rememberLauncherForActivityResult
        viewModel.uploadFiles(uris, destDir)
    }

    var deleteTarget by remember { mutableStateOf<FileEntry?>(null) }
    var renameTarget by remember { mutableStateOf<FileEntry?>(null) }
    var moveTarget by remember { mutableStateOf<FileEntry?>(null) }
    var infoTarget by remember { mutableStateOf<FileEntry?>(null) }

    // ── Delete confirmation dialog ─────────────────────────────────────────────
    deleteTarget?.let { entry ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            icon = { Icon(Icons.Default.DeleteForever, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Delete ${if (entry.isDirectory) "folder" else "file"}?") },
            text = {
                Text(
                    if (entry.isDirectory)
                        "\"${entry.file.name}\" and all its contents will be permanently deleted."
                    else
                        "\"${entry.file.name}\" will be permanently deleted."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteFile(entry.file)
                    deleteTarget = null
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("Cancel") }
            }
        )
    }

    // ── Rename dialog ──────────────────────────────────────────────────────────
    renameTarget?.let { entry ->
        var newName by remember(entry.file.name) { mutableStateOf(entry.file.name) }
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text("Rename") },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text("New name") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = {
                        viewModel.renameFile(entry.file, newName)
                        renameTarget = null
                    }),
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.renameFile(entry.file, newName)
                    renameTarget = null
                }, enabled = newName.isNotBlank() && newName != entry.file.name) {
                    Text("Rename")
                }
            },
            dismissButton = {
                TextButton(onClick = { renameTarget = null }) { Text("Cancel") }
            }
        )
    }

    // ── Move dialog ────────────────────────────────────────────────────────────
    moveTarget?.let { entry ->
        MoveDialog(
            file = entry.file,
            rootDir = fileState.rootDir ?: entry.file.parentFile ?: entry.file,
            onMove = { targetDir ->
                viewModel.moveFile(entry.file, targetDir)
                moveTarget = null
            },
            onDismiss = { moveTarget = null }
        )
    }

    // ── Info dialog ────────────────────────────────────────────────────────────
    infoTarget?.let { entry ->
        val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        AlertDialog(
            onDismissRequest = { infoTarget = null },
            title = { Text(entry.file.name) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    InfoRow("Type", if (entry.isDirectory) "Folder" else "File")
                    InfoRow("Size", if (entry.isDirectory) "—" else formatSize(entry.sizeBytes))
                    InfoRow("Modified", fmt.format(Date(entry.lastModified)))
                    InfoRow("Path", entry.file.absolutePath)
                }
            },
            confirmButton = {
                TextButton(onClick = { infoTarget = null }) { Text("Close") }
            }
        )
    }

    // ── Main content ──────────────────────────────────────────────────────────
    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = { filePicker.launch(arrayOf("*/*")) },
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(Icons.Default.Upload, contentDescription = "Upload file")
            }
        },
        modifier = modifier
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {

            // Breadcrumb bar
            BreadcrumbBar(
                rootDir = fileState.rootDir,
                currentDir = fileState.currentDir,
                canGoUp = viewModel.canNavigateUp(),
                onNavigateUp = viewModel::navigateUp
            )

            // Upload progress indicators
            fileState.uploadProgress.forEach { (name, progress) ->
                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            "Uploading $name",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            "${(progress * 100).toInt()}%",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            if (fileState.isLoading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            if (fileState.entries.isEmpty() && !fileState.isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Default.FolderOpen, contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.outline
                        )
                        Text(
                            "Empty folder",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 80.dp)
                ) {
                    items(fileState.entries, key = { it.file.absolutePath }) { entry ->
                        FileRow(
                            entry = entry,
                            onClick = {
                                if (entry.isDirectory) viewModel.navigateTo(entry.file)
                            },
                            onRename = { renameTarget = entry },
                            onMove   = { moveTarget = entry },
                            onDelete = { deleteTarget = entry },
                            onInfo   = { infoTarget = entry }
                        )
                        HorizontalDivider(
                            modifier = Modifier.padding(start = 56.dp),
                            color = MaterialTheme.colorScheme.outlineVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BreadcrumbBar(
    rootDir: File?,
    currentDir: File?,
    canGoUp: Boolean,
    onNavigateUp: () -> Unit
) {
    if (rootDir == null || currentDir == null) return
    val relativePath = try {
        currentDir.canonicalPath.removePrefix(rootDir.canonicalPath).trimStart('/')
    } catch (e: Exception) { currentDir.name }

    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onNavigateUp,
                enabled = canGoUp
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Up",
                    tint = if (canGoUp) MaterialTheme.colorScheme.primary
                           else MaterialTheme.colorScheme.outline
                )
            }
            Text(
                text = "/ ${relativePath.ifEmpty { "server root" }}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun FileRow(
    entry: FileEntry,
    onClick: () -> Unit,
    onRename: () -> Unit,
    onMove: () -> Unit,
    onDelete: () -> Unit,
    onInfo: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            imageVector = if (entry.isDirectory) Icons.Default.Folder else fileIcon(entry.file.extension),
            contentDescription = null,
            tint = if (entry.isDirectory) MaterialTheme.colorScheme.tertiary
                   else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(28.dp)
        )

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = entry.file.name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = if (entry.isDirectory) "Folder"
                       else formatSize(entry.sizeBytes),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Box {
            IconButton(onClick = { menuExpanded = true }) {
                Icon(
                    Icons.Default.MoreVert,
                    contentDescription = "File options",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false }
            ) {
                DropdownMenuItem(
                    text = { Text("Rename") },
                    leadingIcon = { Icon(Icons.Default.Edit, null) },
                    onClick = { menuExpanded = false; onRename() }
                )
                DropdownMenuItem(
                    text = { Text("Move to…") },
                    leadingIcon = { Icon(Icons.Default.DriveFileMove, null) },
                    onClick = { menuExpanded = false; onMove() }
                )
                DropdownMenuItem(
                    text = { Text("Properties") },
                    leadingIcon = { Icon(Icons.Default.Info, null) },
                    onClick = { menuExpanded = false; onInfo() }
                )
                HorizontalDivider()
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

@Composable
private fun MoveDialog(
    file: File,
    rootDir: File,
    onMove: (File) -> Unit,
    onDismiss: () -> Unit
) {
    val dirs = remember(rootDir) {
        buildList {
            add(rootDir) // root
            rootDir.walkTopDown()
                .filter { it.isDirectory && it != rootDir && it != file && !it.canonicalPath.startsWith(file.canonicalPath) }
                .take(100)
                .forEach { add(it) }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Move \"${file.name}\" to…") },
        text = {
            LazyColumn(modifier = Modifier.heightIn(max = 300.dp)) {
                items(dirs) { dir ->
                    val label = try {
                        "/ " + dir.canonicalPath.removePrefix(rootDir.canonicalPath).trimStart('/')
                            .ifEmpty { "server root" }
                    } catch (e: Exception) { dir.name }
                    ListItem(
                        headlineContent = { Text(label, style = MaterialTheme.typography.bodyMedium) },
                        leadingContent = { Icon(Icons.Default.Folder, null, tint = MaterialTheme.colorScheme.tertiary) },
                        modifier = Modifier.clickable { onMove(dir) }
                    )
                    HorizontalDivider()
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(72.dp))
        Text(value, style = MaterialTheme.typography.bodySmall)
    }
}

private fun fileIcon(ext: String): ImageVector = when (ext.lowercase()) {
    "jar"   -> Icons.Default.Archive
    "json"  -> Icons.Default.DataObject
    "yml", "yaml", "toml", "properties", "conf", "cfg", "ini" -> Icons.Default.Settings
    "txt", "log", "md" -> Icons.Default.Description
    "zip", "tar", "gz", "7z" -> Icons.Default.FolderZip
    "png", "jpg", "jpeg", "webp", "gif" -> Icons.Default.Image
    else    -> Icons.Default.InsertDriveFile
}

private fun formatSize(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024) return DecimalFormat("0.#").format(kb) + " KB"
    val mb = kb / 1024.0
    if (mb < 1024) return DecimalFormat("0.#").format(mb) + " MB"
    return DecimalFormat("0.##").format(mb / 1024.0) + " GB"
}
