package com.auralauncher.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.auralauncher.app.data.FocusModeEntity
import com.auralauncher.app.data.LauncherRepository
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FocusModeScreen(repository: LauncherRepository, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val focusModes by repository.observeFocusModes().collectAsState(initial = emptyList())
    val allApps = remember { repository.loadAllApps() }

    var showCreateDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Focus modes") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) } }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showCreateDialog = true }) { Text("+") }
        }
    ) { padding ->
        LazyColumn(Modifier.padding(padding).fillMaxSize()) {
            items(focusModes, key = { it.id }) { mode ->
                ListItem(
                    headlineContent = { Text(mode.name) },
                    supportingContent = { Text("${mode.allowedPackages.size} apps${if (mode.isActive) " · active" else ""}") },
                    trailingContent = {
                        Row {
                            TextButton(onClick = {
                                scope.launch { repository.activateFocusMode(if (mode.isActive) null else mode.id) }
                            }) { Text(if (mode.isActive) "Deactivate" else "Activate") }
                            IconButton(onClick = { scope.launch { repository.deleteFocusMode(mode) } }) {
                                Icon(Icons.Default.ArrowBack, contentDescription = null) // placeholder icon, swap for a real delete glyph
                            }
                        }
                    }
                )
                Divider()
            }
        }
    }

    if (showCreateDialog) {
        CreateFocusModeDialog(
            allApps = allApps,
            onDismiss = { showCreateDialog = false },
            onCreate = { name, selectedPackages ->
                scope.launch {
                    repository.saveFocusMode(FocusModeEntity(name = name, allowedPackages = selectedPackages))
                }
                showCreateDialog = false
            }
        )
    }
}

@Composable
private fun CreateFocusModeDialog(
    allApps: List<com.auralauncher.app.data.AppInfo>,
    onDismiss: () -> Unit,
    onCreate: (String, List<String>) -> Unit
) {
    var name by remember { mutableStateOf("") }
    val selected = remember { mutableStateListOf<String>() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New focus mode") },
        text = {
            Column {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name (e.g. Work, Deep Focus)") })
                Spacer(Modifier.height(8.dp))
                Text("Allowed apps:", style = MaterialTheme.typography.labelMedium)
                LazyColumn(Modifier.height(240.dp)) {
                    items(allApps, key = { it.packageName }) { app ->
                        Row {
                            Checkbox(
                                checked = app.packageName in selected,
                                onCheckedChange = { checked ->
                                    if (checked) selected.add(app.packageName) else selected.remove(app.packageName)
                                }
                            )
                            Text(app.label, modifier = Modifier.align(androidx.compose.ui.Alignment.CenterVertically))
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onCreate(name.ifBlank { "Focus" }, selected.toList()) },
                enabled = selected.isNotEmpty()
            ) { Text("Create") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
