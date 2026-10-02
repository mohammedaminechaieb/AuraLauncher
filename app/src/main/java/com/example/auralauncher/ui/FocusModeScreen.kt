package com.auralauncher.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SelfImprovement
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.auralauncher.app.data.AppInfo
import com.auralauncher.app.data.AppListStore
import com.auralauncher.app.data.FocusModeEntity
import com.auralauncher.app.data.IconShape
import com.auralauncher.app.data.LauncherRepository
import com.auralauncher.app.ui.components.AppIconView
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FocusModeScreen(repository: LauncherRepository, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val focusModes by repository.observeFocusModes().collectAsState(initial = emptyList())
    val apps by AppListStore.get(context).apps.collectAsState()

    // null = closed, id 0 = creating new, otherwise editing that mode
    var editing by remember { mutableStateOf<FocusModeEntity?>(null) }
    var deleteCandidate by remember { mutableStateOf<FocusModeEntity?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Focus modes") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { editing = FocusModeEntity(name = "", allowedPackages = emptyList()) },
                icon = { Icon(Icons.Default.Add, null) },
                text = { Text("New focus mode") }
            )
        }
    ) { padding ->
        if (focusModes.isEmpty()) {
            Column(
                Modifier.padding(padding).fillMaxSize().padding(32.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(Icons.Default.SelfImprovement, null, Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(16.dp))
                Text("Focus modes hide everything except the apps you choose — great for work hours or winding down.", textAlign = TextAlign.Center)
            }
            return@Scaffold
        }
        LazyColumn(Modifier.padding(padding).fillMaxSize(), contentPadding = PaddingValues(bottom = 96.dp)) {
            items(focusModes, key = { it.id }) { mode ->
                ListItem(
                    headlineContent = { Text(mode.name) },
                    supportingContent = { Text("${mode.allowedPackages.size} apps" + if (mode.isActive) " · active now" else "") },
                    leadingContent = {
                        Switch(checked = mode.isActive, onCheckedChange = { on -> scope.launch { repository.activateFocusMode(if (on) mode.id else null) } })
                    },
                    trailingContent = {
                        Row {
                            IconButton(onClick = { editing = mode }) { Icon(Icons.Default.Edit, "Edit") }
                            IconButton(onClick = { deleteCandidate = mode }) { Icon(Icons.Default.Delete, "Delete") }
                        }
                    }
                )
                HorizontalDivider()
            }
        }
    }

    editing?.let { mode ->
        FocusModeDialog(
            initial = mode,
            allApps = apps.orEmpty(),
            onDismiss = { editing = null },
            onSave = { name, selected ->
                scope.launch { repository.saveFocusMode(mode.copy(name = name, allowedPackages = selected)) }
                editing = null
            }
        )
    }

    deleteCandidate?.let { mode ->
        AlertDialog(
            onDismissRequest = { deleteCandidate = null },
            title = { Text("Delete \"${mode.name}\"?") },
            confirmButton = { TextButton(onClick = { scope.launch { repository.deleteFocusMode(mode) }; deleteCandidate = null }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { deleteCandidate = null }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun FocusModeDialog(
    initial: FocusModeEntity,
    allApps: List<AppInfo>,
    onDismiss: () -> Unit,
    onSave: (String, List<String>) -> Unit
) {
    var name by remember { mutableStateOf(initial.name) }
    var query by remember { mutableStateOf("") }
    val selected = remember { mutableStateListOf<String>().apply { addAll(initial.allowedPackages) } }
    val visible = remember(query, allApps) {
        allApps.filter { query.isBlank() || it.label.contains(query, ignoreCase = true) }
            .sortedByDescending { it.packageName in initial.allowedPackages }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial.id == 0L) "New focus mode" else "Edit focus mode") },
        text = {
            Column {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name (e.g. Work, Deep focus)") }, singleLine = true)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = query, onValueChange = { query = it }, placeholder = { Text("Search apps") },
                    leadingIcon = { Icon(Icons.Default.Search, null) }, singleLine = true
                )
                Spacer(Modifier.height(4.dp))
                Text("${selected.size} selected", style = MaterialTheme.typography.labelMedium)
                LazyColumn(Modifier.height(280.dp)) {
                    items(visible, key = { it.packageName }) { app ->
                        val checked = app.packageName in selected
                        Row(
                            Modifier.fillMaxWidth().clickable {
                                if (checked) selected.remove(app.packageName) else selected.add(app.packageName)
                            }.padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(checked = checked, onCheckedChange = null)
                            Spacer(Modifier.width(8.dp))
                            AppIconView(icon = app.icon, shape = IconShape.SYSTEM_DEFAULT, sizeDp = 28)
                            Spacer(Modifier.width(10.dp))
                            Text(app.label)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(name.ifBlank { "Focus" }, selected.toList()) }, enabled = selected.isNotEmpty()) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
