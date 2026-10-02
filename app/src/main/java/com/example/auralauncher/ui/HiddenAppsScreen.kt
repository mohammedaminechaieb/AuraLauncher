package com.auralauncher.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.auralauncher.app.data.AppListStore
import com.auralauncher.app.data.IconShape
import com.auralauncher.app.data.LauncherRepository
import com.auralauncher.app.prefs.HiddenAppsManager
import com.auralauncher.app.ui.components.AppIconView

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HiddenAppsScreen(@Suppress("UNUSED_PARAMETER") repository: LauncherRepository, onBack: () -> Unit) {
    val context = LocalContext.current
    val hiddenAppsManager = remember { HiddenAppsManager(context) }
    val apps by AppListStore.get(context).apps.collectAsState()
    var hiddenSet by remember { mutableStateOf(hiddenAppsManager.getHiddenPackages()) }
    // Hidden apps first, so it's easy to see (and undo) what's hidden.
    val sorted = remember(apps) { apps.orEmpty().sortedByDescending { it.packageName in hiddenSet } }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text("Hidden apps") },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } }
        )
    }) { padding ->
        LazyColumn(Modifier.padding(padding).fillMaxSize()) {
            item {
                Text(
                    "Hidden apps stay installed and keep working from the home screen — they're just left out of the drawer and search. ${hiddenSet.size} hidden.",
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodySmall
                )
            }
            items(sorted, key = { it.packageName }) { app ->
                val isHidden = app.packageName in hiddenSet
                fun toggle(checked: Boolean) {
                    hiddenAppsManager.setHidden(app.packageName, checked)
                    hiddenSet = hiddenAppsManager.getHiddenPackages()
                }
                ListItem(
                    modifier = Modifier.clickable { toggle(!isHidden) },
                    leadingContent = { AppIconView(icon = app.icon, shape = IconShape.SYSTEM_DEFAULT, sizeDp = 36) },
                    headlineContent = { Text(app.label) },
                    trailingContent = { Switch(checked = isHidden, onCheckedChange = ::toggle) }
                )
            }
        }
    }
}
