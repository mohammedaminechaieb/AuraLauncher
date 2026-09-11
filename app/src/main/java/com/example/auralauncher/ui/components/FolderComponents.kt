package com.auralauncher.app.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.auralauncher.app.data.AppInfo
import com.auralauncher.app.data.FolderEntity
import com.auralauncher.app.data.IconOverrideEntity
import com.auralauncher.app.data.IconShape
import com.auralauncher.app.iconpack.IconPackManager
import androidx.core.graphics.drawable.toBitmap
import androidx.compose.ui.graphics.asImageBitmap

/** The closed folder icon on the grid — a small 2x2 grid of up to 4 member app icons,
 *  the same visual language stock Android/most launchers use for folder previews. */
@Composable
fun FolderPreviewIcon(members: List<AppInfo>, cellSize: Dp) {
    val previewSize = cellSize * 0.85f
    Box(
        Modifier
            .size(previewSize)
            .clip(RoundedCornerShape(16.dp))
            .background(androidx.compose.ui.graphics.Color.DarkGray.copy(alpha = 0.4f)),
        contentAlignment = Alignment.Center
    ) {
        if (members.isEmpty()) return@Box
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.padding(4.dp),
            userScrollEnabled = false
        ) {
            items(members.take(4)) { app ->
                val bitmap = remember(app.packageName) {
                    app.icon.toBitmap(width = 48, height = 48).asImageBitmap()
                }
                androidx.compose.foundation.Image(
                    painter = androidx.compose.ui.graphics.painter.BitmapPainter(bitmap),
                    contentDescription = null,
                    modifier = Modifier.padding(1.dp).size(previewSize / 2 - 4.dp)
                )
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

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            if (renaming) {
                OutlinedTextField(
                    value = nameField,
                    onValueChange = { nameField = it },
                    singleLine = true,
                    label = { Text("Folder name") }
                )
            } else {
                Text(folder.name, modifier = Modifier.combinedClickable(onClick = {}, onLongClick = { renaming = true }))
            }
        },
        text = {
            Column {
                if (members.isEmpty()) {
                    Text("This folder is empty.")
                } else {
                    LazyVerticalGrid(columns = GridCells.Fixed(4), modifier = Modifier.height(200.dp)) {
                        items(members, key = { it.packageName }) { app ->
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .padding(6.dp)
                                    .combinedClickable(
                                        onClick = { onLaunch(app.packageName) },
                                        onLongClick = { onRemoveMember(app.packageName) }
                                    )
                            ) {
                                val packIcon = remember(app.packageName) { iconPackManager.resolveIcon(app) }
                                val shape = iconOverrides.firstOrNull { it.packageName == app.packageName }?.shape ?: IconShape.SYSTEM_DEFAULT
                                if (packIcon != null) {
                                    AppIconView(icon = packIcon, shape = shape, sizeDp = 40, isPreShaped = true)
                                } else {
                                    AppIconView(icon = app.icon, shape = shape, sizeDp = 40)
                                }
                                Text(app.label, style = MaterialTheme.typography.labelSmall, maxLines = 1, textAlign = TextAlign.Center)
                            }
                        }
                    }
                    Text(
                        "Tap to open · long-press to take out of the folder",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            if (renaming) {
                TextButton(onClick = { onRename(nameField); renaming = false }) { Text("Save name") }
            } else {
                TextButton(onClick = { confirmingDelete = true }) { Text("Delete folder") }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )

    if (confirmingDelete) {
        AlertDialog(
            onDismissRequest = { confirmingDelete = false },
            title = { Text("Delete this folder?") },
            text = { Text("Its apps go back onto the home screen individually.") },
            confirmButton = {
                TextButton(onClick = { confirmingDelete = false; onDelete() }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirmingDelete = false }) { Text("Cancel") } }
        )
    }
}
