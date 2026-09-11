package com.auralauncher.app.ui

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.auralauncher.app.settings.DrawerSortMode
import com.auralauncher.app.settings.DrawerViewMode
import com.auralauncher.app.settings.GestureAction
import com.auralauncher.app.settings.LauncherSettingsManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    isDefaultLauncher: Boolean,
    hasNotificationAccess: Boolean,
    backupStatus: String?,
    onRequestDefaultLauncher: () -> Unit,
    onRequestNotificationAccess: () -> Unit,
    onOpenIconTheme: () -> Unit,
    onOpenFocusModes: () -> Unit,
    onOpenHiddenApps: () -> Unit,
    onExportBackup: () -> Unit,
    onImportBackup: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val settings = remember { LauncherSettingsManager(context) }

    var columns by remember { mutableStateOf(settings.columns) }
    var rows by remember { mutableStateOf(settings.rows) }
    var iconSize by remember { mutableStateOf(settings.iconSizeDp) }
    var showLabels by remember { mutableStateOf(settings.showLabels) }
    var swipeUp by remember { mutableStateOf(settings.swipeUpAction) }
    var swipeDown by remember { mutableStateOf(settings.swipeDownAction) }
    var dockSlots by remember { mutableStateOf(settings.dockSlots) }
    var drawerSort by remember { mutableStateOf(settings.drawerSortMode) }
    var drawerView by remember { mutableStateOf(settings.drawerViewMode) }
    var showHomeSearchBar by remember { mutableStateOf(settings.showHomeSearchBar) }
    var hideStatusBar by remember { mutableStateOf(settings.hideStatusBar) }
    var showBadges by remember { mutableStateOf(settings.showNotificationBadges) }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text("Home settings") },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) } }
        )
    }) { padding ->
        Column(Modifier.padding(padding).verticalScroll(rememberScrollState()).fillMaxSize()) {

            SettingsSection("Default launcher") {
                if (isDefaultLauncher) {
                    ListItem(headlineContent = { Text("AuraLauncher is your default home screen") }, supportingContent = { Text("\u2713 Active") })
                } else {
                    ListItem(
                        headlineContent = { Text("Not set as default yet") },
                        trailingContent = { TextButton(onClick = onRequestDefaultLauncher) { Text("Set as default") } }
                    )
                }
            }

            SettingsSection("Home screen grid") {
                SettingsStepper("Columns", columns, 3, 6) { columns = it; settings.columns = it }
                SettingsStepper("Rows", rows, 4, 7) { rows = it; settings.rows = it }
                ListItem(
                    headlineContent = { Text("Icon size") },
                    supportingContent = {
                        Slider(value = iconSize.toFloat(), valueRange = 40f..72f, steps = 7, onValueChange = { iconSize = it.toInt(); settings.iconSizeDp = it.toInt() })
                    }
                )
                ListItem(
                    headlineContent = { Text("Show labels under icons") },
                    trailingContent = { Switch(checked = showLabels, onCheckedChange = { showLabels = it; settings.showLabels = it }) }
                )
                ListItem(
                    headlineContent = { Text("Show search bar on home screen") },
                    supportingContent = { Text("Tap it to open the drawer with the keyboard ready") },
                    trailingContent = { Switch(checked = showHomeSearchBar, onCheckedChange = { showHomeSearchBar = it; settings.showHomeSearchBar = it }) }
                )
                ListItem(
                    headlineContent = { Text("Hide status bar") },
                    supportingContent = { Text("Swipe down from the top to reveal it temporarily") },
                    trailingContent = { Switch(checked = hideStatusBar, onCheckedChange = { hideStatusBar = it; settings.hideStatusBar = it }) }
                )
            }

            SettingsSection("Dock") {
                SettingsStepper("Dock slots", dockSlots, 3, 6) { dockSlots = it; settings.dockSlots = it }
                Text(
                    "Add apps to the dock by long-pressing them in the app drawer.",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }

            SettingsSection("Gestures") {
                GestureActionPicker("Swipe up", swipeUp) { swipeUp = it; settings.swipeUpAction = it }
                GestureActionPicker("Swipe down", swipeDown) { swipeDown = it; settings.swipeDownAction = it }
            }

            SettingsSection("App drawer") {
                DrawerSortPicker(drawerSort) { drawerSort = it; settings.drawerSortMode = it }
                DrawerViewPicker(drawerView) { drawerView = it; settings.drawerViewMode = it }
            }

            SettingsSection("Notifications") {
                ListItem(
                    headlineContent = { Text("Notification dot badges") },
                    supportingContent = { Text(if (hasNotificationAccess) "\u2713 Access granted" else "Needs Notification Access permission") },
                    trailingContent = {
                        Switch(
                            checked = showBadges,
                            onCheckedChange = {
                                showBadges = it; settings.showNotificationBadges = it
                                if (it && !hasNotificationAccess) onRequestNotificationAccess()
                            }
                        )
                    }
                )
                if (!hasNotificationAccess) {
                    ListItem(
                        headlineContent = { Text("Grant notification access") },
                        trailingContent = { TextButton(onClick = onRequestNotificationAccess) { Text("Open settings") } }
                    )
                }
            }

            SettingsSection("Personalization") {
                ListItem(headlineContent = { Text("Icon theme") }, supportingContent = { Text("Shapes, or import a real icon pack") }, modifier = Modifier.clickableRow(onOpenIconTheme))
                ListItem(
                    headlineContent = { Text("Wallpaper") },
                    supportingContent = { Text("Opens the system wallpaper picker") },
                    modifier = Modifier.clickableRow { runCatching { context.startActivity(Intent(Intent.ACTION_SET_WALLPAPER)) } }
                )
            }

            SettingsSection("Organization") {
                ListItem(headlineContent = { Text("Focus modes") }, supportingContent = { Text("Show only a chosen set of apps") }, modifier = Modifier.clickableRow(onOpenFocusModes))
                ListItem(headlineContent = { Text("Hidden apps") }, supportingContent = { Text("Keep apps out of the drawer") }, modifier = Modifier.clickableRow(onOpenHiddenApps))
            }

            SettingsSection("Backup & restore") {
                ListItem(
                    headlineContent = { Text("Export backup") },
                    supportingContent = { Text("Saves your layout, folders, focus modes, and settings to a file") },
                    modifier = Modifier.clickableRow(onExportBackup)
                )
                ListItem(
                    headlineContent = { Text("Restore from backup") },
                    supportingContent = { Text("Replaces your current layout with a backup file") },
                    modifier = Modifier.clickableRow(onImportBackup)
                )
                backupStatus?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                }
                Text(
                    "Note: hosted widgets aren't restored automatically — a widget's binding is tied to this specific install and can't be transferred. You'll need to re-add widgets after restoring.",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.padding(top = 12.dp)) {
        Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
        content()
        Divider(Modifier.padding(top = 8.dp))
    }
}

@Composable
private fun SettingsStepper(label: String, value: Int, min: Int, max: Int, onChange: (Int) -> Unit) {
    ListItem(
        headlineContent = { Text(label) },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { if (value > min) onChange(value - 1) }, enabled = value > min) { Text("\u2212") }
                Text("$value", modifier = Modifier.padding(horizontal = 8.dp))
                IconButton(onClick = { if (value < max) onChange(value + 1) }, enabled = value < max) { Text("+") }
            }
        }
    )
}

@Composable
private fun GestureActionPicker(label: String, current: GestureAction, onChange: (GestureAction) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ListItem(headlineContent = { Text(label) }, supportingContent = { Text(current.label) }, modifier = Modifier.clickableRow { expanded = true })
    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
        GestureAction.entries.forEach { action -> DropdownMenuItem(text = { Text(action.label) }, onClick = { onChange(action); expanded = false }) }
    }
}

@Composable
private fun DrawerSortPicker(current: DrawerSortMode, onChange: (DrawerSortMode) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ListItem(headlineContent = { Text("Sort apps by") }, supportingContent = { Text(current.label) }, modifier = Modifier.clickableRow { expanded = true })
    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
        DrawerSortMode.entries.forEach { mode -> DropdownMenuItem(text = { Text(mode.label) }, onClick = { onChange(mode); expanded = false }) }
    }
}

@Composable
private fun DrawerViewPicker(current: DrawerViewMode, onChange: (DrawerViewMode) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ListItem(headlineContent = { Text("Drawer layout") }, supportingContent = { Text(current.label) }, modifier = Modifier.clickableRow { expanded = true })
    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
        DrawerViewMode.entries.forEach { mode -> DropdownMenuItem(text = { Text(mode.label) }, onClick = { onChange(mode); expanded = false }) }
    }
}

private fun Modifier.clickableRow(onClick: () -> Unit): Modifier =
    this.clickable(onClick = onClick)
