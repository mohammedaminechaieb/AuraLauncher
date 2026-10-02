package com.auralauncher.app.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.auralauncher.app.data.AppInfo
import com.auralauncher.app.data.FolderEntity
import com.auralauncher.app.data.IconOverrideEntity
import com.auralauncher.app.data.IconShape
import com.auralauncher.app.iconpack.IconPackManager
import com.auralauncher.app.ui.shapeFor

/** Closed folder on the grid: a 2x2 preview of up to 4 member icons. */
@Composable
fun FolderPreviewIcon(members: List<AppInfo>, sizeDp: Int) {
    Box(
        Modifier
            .size(sizeDp.dp)
            .clip(RoundedCornerShape(percent = 30))
            .background(Color.White.copy(alpha = 0.22f))
            .padding((sizeDp * 0.1f).dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            members.take(4).chunked(2).forEach { rowApps ->
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    rowApps.forEach { app ->
                        val bitmap = remember(app.packageName) { app.icon.toBitmap(64, 64).asImageBitmap() }
                        Image(bitmap, null, Modifier.size((sizeDp * 0.38f).dp))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FolderContentsDialog(
    folder: FolderEntity,
    members: List<AppInfo>,
    iconOverrides: List<IconOverrideEntity>,
    globalShape: IconShape,
    iconPackManager: IconPackManager,
    onLaunch: (String) -> Unit,
    onRemoveMember: (String) -> Unit,
    onRename: (String) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    var renaming by remember { mutableStateOf(false) }
    var nameField by remember { mutableStateOf(folder.name) }
    var confirmingDelete by remember { mutableStateOf(false) }
    var memberMenu by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            if (renaming) {
                OutlinedTextField(value = nameField, onValueChange = { nameField = it }, singleLine = true, label = { Text("Folder name") })
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(folder.name, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    IconButton(onClick = { renaming = true }) { Icon(Icons.Default.Edit, "Rename") }
                    IconButton(onClick = { confirmingDelete = true }) { Icon(Icons.Default.Delete, "Delete folder") }
                }
            }
        },
        text = {
            Column {
                if (members.isEmpty()) {
                    Text("This folder is empty.")
                } else {
                    LazyVerticalGrid(columns = GridCells.Fixed(4), modifier = Modifier.heightIn(max = 280.dp)) {
                        items(members, key = { it.packageName }) { app ->
                            Box {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier
                                        .padding(4.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .combinedClickable(onClick = { onLaunch(app.packageName) }, onLongClick = { memberMenu = app.packageName })
                                        .padding(4.dp)
                                ) {
                                    val packIcon = remember(app.packageName) { iconPackManager.resolveIcon(app) }
                                    val shape = shapeFor(app.packageName, iconOverrides, globalShape)
                                    if (packIcon != null) AppIconView(icon = packIcon, shape = shape, sizeDp = 44, isPreShaped = true)
                                    else AppIconView(icon = app.icon, shape = shape, sizeDp = 44)
                                    Text(app.label, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                                }
                                DropdownMenu(expanded = memberMenu == app.packageName, onDismissRequest = { memberMenu = null }) {
                                    DropdownMenuItem(text = { Text("Move to home screen") }, onClick = { memberMenu = null; onRemoveMember(app.packageName) })
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Text("Hold an app to move it out of the folder", style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            if (renaming) {
                TextButton(onClick = { onRename(nameField.trim().ifEmpty { "Folder" }); renaming = false }) { Text("Save name") }
            } else {
                TextButton(onClick = onDismiss) { Text("Close") }
            }
        }
    )

    if (confirmingDelete) {
        AlertDialog(
            onDismissRequest = { confirmingDelete = false },
            title = { Text("Delete this folder?") },
            text = { Text("Its apps go back onto the home screen individually.") },
            confirmButton = { TextButton(onClick = { confirmingDelete = false; onDelete() }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { confirmingDelete = false }) { Text("Cancel") } }
        )
    }
}
