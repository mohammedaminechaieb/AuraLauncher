package com.auralauncher.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.auralauncher.app.data.IconShape
import com.auralauncher.app.data.LauncherRepository
import com.auralauncher.app.prefs.HiddenAppsManager
import com.auralauncher.app.ui.components.AppIconView

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HiddenAppsScreen(repository: LauncherRepository, onBack: () -> Unit) {
    val context = LocalContext.current
    val hiddenAppsManager = remember { HiddenAppsManager(context) }
    val allApps = remember { repository.loadAllApps() }

    // Local mutable snapshot so toggling reflects immediately without
    // waiting on a Flow round-trip — HiddenAppsManager itself is the
    // source of truth on disk, this is just the UI's live view of it.
    var hiddenSet by remember { mutableStateOf(hiddenAppsManager.getHiddenPackages()) }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text("Hidden apps") },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) } }
        )
    }) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            Text(
                "Hidden apps stay installed and still work if already on your home screen — they're just left out of the drawer's list and search.",
                modifier = Modifier.padding(16.dp),
                style = MaterialTheme.typography.bodySmall
            )
            LazyColumn(Modifier.fillMaxSize()) {
                items(allApps, key = { it.packageName }) { app ->
                    val isHidden = app.packageName in hiddenSet
                    ListItem(
                        leadingContent = { AppIconView(icon = app.icon, shape = IconShape.SYSTEM_DEFAULT, sizeDp = 36) },
                        headlineContent = { Text(app.label) },
                        trailingContent = {
                            Switch(
                                checked = isHidden,
                                onCheckedChange = { checked ->
                                    hiddenAppsManager.setHidden(app.packageName, checked)
                                    hiddenSet = hiddenAppsManager.getHiddenPackages()
                                }
                            )
                        }
                    )
                    Divider()
                }
            }
        }
    }
}
