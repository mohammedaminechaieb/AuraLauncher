package com.auralauncher.app.ui

import android.content.pm.ApplicationInfo
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.auralauncher.app.data.AppInfo
import com.auralauncher.app.data.IconShape
import com.auralauncher.app.data.LauncherRepository
import com.auralauncher.app.iconpack.IconPackManager
import com.auralauncher.app.notifications.AuraNotificationListenerService
import com.auralauncher.app.prefs.AppUsageTracker
import com.auralauncher.app.prefs.HiddenAppsManager
import com.auralauncher.app.settings.DrawerSortMode
import com.auralauncher.app.settings.DrawerViewMode
import com.auralauncher.app.settings.LauncherSettingsManager
import com.auralauncher.app.shortcuts.AppShortcutsHelper
import com.auralauncher.app.ui.components.AppIconView
import com.auralauncher.app.ui.components.BadgedIcon
import kotlinx.coroutines.launch

/** Groups Android's raw ApplicationInfo.CATEGORY_* constants into a small,
 *  human-friendly tab set — matching the level of grouping Smart Launcher's
 *  category tabs use, rather than exposing all ~10 raw platform categories
 *  (most of which are rare in practice and would make for mostly-empty tabs). */
private enum class DrawerTab(val label: String) {
    ALL("All"),
    GAMES("Games"),
    SOCIAL("Social"),
    PRODUCTIVITY("Work"),
    MEDIA("Media"),
    OTHER("Other")
}

private fun categoryToTab(category: Int): DrawerTab = when (category) {
    ApplicationInfo.CATEGORY_GAME -> DrawerTab.GAMES
    ApplicationInfo.CATEGORY_SOCIAL -> DrawerTab.SOCIAL
    ApplicationInfo.CATEGORY_PRODUCTIVITY -> DrawerTab.PRODUCTIVITY
    ApplicationInfo.CATEGORY_AUDIO, ApplicationInfo.CATEGORY_VIDEO, ApplicationInfo.CATEGORY_IMAGE -> DrawerTab.MEDIA
    else -> DrawerTab.OTHER
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun AppDrawerScreen(repository: LauncherRepository, onOpenHiddenApps: () -> Unit, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current
    val iconPackManager = remember { IconPackManager(context) }
    val hiddenAppsManager = remember { HiddenAppsManager(context) }
    val shortcutsHelper = remember { AppShortcutsHelper(context) }
    val usageTracker = remember { AppUsageTracker(context) }
    val settings = remember { LauncherSettingsManager(context) }
    val activeNotificationPackages by AuraNotificationListenerService.activePackages.collectAsState()
    val showBadges = settings.showNotificationBadges

    val allApps = remember { repository.loadAllApps() }
    val gridItems by repository.observeAllGridItems().collectAsState(initial = emptyList())
    val dockItems by repository.observeDockItems().collectAsState(initial = emptyList())
    val hostedWidgets by repository.observeAllHostedWidgets().collectAsState(initial = emptyList())
    val onGrid = remember(gridItems) { gridItems.map { it.packageName }.toSet() }

    var query by remember { mutableStateOf("") }
    var selectedTab by remember { mutableStateOf(DrawerTab.ALL) }
    var sortMode by remember { mutableStateOf(settings.drawerSortMode) }
    var viewMode by remember { mutableStateOf(settings.drawerViewMode) }
    val hiddenSet = remember(query) { hiddenAppsManager.getHiddenPackages() }

    val availableTabs = remember(allApps) {
        val present = allApps.map { categoryToTab(it.category) }.toSet()
        listOf(DrawerTab.ALL) + DrawerTab.entries.filter { it != DrawerTab.ALL && it in present }
    }

    val filtered = remember(query, allApps, hiddenSet, selectedTab, sortMode) {
        val base = allApps.filter {
            it.packageName !in hiddenSet &&
                (query.isBlank() || it.label.contains(query, ignoreCase = true)) &&
                (selectedTab == DrawerTab.ALL || categoryToTab(it.category) == selectedTab)
        }
        when (sortMode) {
            DrawerSortMode.ALPHABETICAL -> base.sortedBy { it.label.lowercase() }
            DrawerSortMode.MOST_USED -> base.sortedByDescending { usageTracker.launchCount(it.packageName) }
            DrawerSortMode.RECENTLY_INSTALLED -> base.sortedByDescending { repository.installTimeOf(it.packageName) }
        }
    }

    val shortcutResults = remember(query, filtered) {
        if (query.isBlank()) emptyList()
        else filtered.take(5).flatMap { app -> shortcutsHelper.shortcutsFor(app.packageName).map { app to it } }
    }

    var addCandidate by remember { mutableStateOf<String?>(null) }

    Surface(color = Color(0xFF0E0E14), modifier = Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                Column {
                    TopAppBar(
                        title = {
                            OutlinedTextField(
                                value = query,
                                onValueChange = { query = it },
                                placeholder = { Text("Search apps") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        },
                        navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) } },
                        actions = {
                            IconButton(onClick = { viewMode = if (viewMode == DrawerViewMode.LIST) DrawerViewMode.GRID else DrawerViewMode.LIST; settings.drawerViewMode = viewMode }) {
                                Icon(
                                    if (viewMode == DrawerViewMode.LIST) androidx.compose.material.icons.Icons.Default.GridView else androidx.compose.material.icons.Icons.Default.ViewList,
                                    contentDescription = "Toggle view"
                                )
                            }
                            SortMenuButton(current = sortMode) { sortMode = it; settings.drawerSortMode = it }
                            IconButton(onClick = onOpenHiddenApps) { Icon(Icons.Default.VisibilityOff, contentDescription = "Hidden apps") }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
                    )
                    if (availableTabs.size > 1) {
                        ScrollableTabRow(
                            selectedTabIndex = availableTabs.indexOf(selectedTab).coerceAtLeast(0),
                            containerColor = Color.Transparent,
                            edgePadding = 12.dp
                        ) {
                            availableTabs.forEach { tab ->
                                Tab(selected = selectedTab == tab, onClick = { selectedTab = tab }, text = { Text(tab.label) })
                            }
                        }
                    }
                }
            }
        ) { padding ->
            if (filtered.isEmpty() && shortcutResults.isEmpty()) {
                Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(if (query.isBlank()) "No apps in this category" else "No apps match \"$query\"", color = Color.Gray)
                }
                return@Scaffold
            }

            if (viewMode == DrawerViewMode.GRID) {
                LazyVerticalGrid(columns = GridCells.Fixed(4), modifier = Modifier.padding(padding).fillMaxSize(), contentPadding = PaddingValues(12.dp)) {
                    items(filtered, key = { it.packageName }) { app ->
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .padding(8.dp)
                                .combinedClickable(
                                    onClick = { repository.launchApp(app.packageName) },
                                    onLongClick = { addCandidate = app.packageName }
                                )
                        ) {
                            val packIcon = remember(app.packageName) { iconPackManager.resolveIcon(app) }
                            BadgedIcon(show = showBadges && app.packageName in activeNotificationPackages) {
                                if (packIcon != null) {
                                    AppIconView(icon = packIcon, shape = IconShape.SYSTEM_DEFAULT, sizeDp = 48, isPreShaped = true)
                                } else {
                                    AppIconView(icon = app.icon, shape = IconShape.SYSTEM_DEFAULT, sizeDp = 48)
                                }
                            }
                            Text(app.label, style = MaterialTheme.typography.labelSmall, color = Color.White, maxLines = 1, textAlign = TextAlign.Center)
                        }
                    }
                }
            } else {
                LazyColumn(Modifier.padding(padding).fillMaxSize()) {
                    if (shortcutResults.isNotEmpty()) {
                        item {
                            Text("Actions", style = MaterialTheme.typography.labelMedium, color = Color.Gray, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                        }
                        items(shortcutResults, key = { (app, shortcut) -> "${app.packageName}/${shortcut.id}" }) { (app, shortcut) ->
                            ListItem(
                                leadingContent = {
                                    val bitmap = remember(shortcut.id) { shortcut.icon?.toBitmap(width = 96, height = 96)?.asImageBitmap() }
                                    if (bitmap != null) {
                                        androidx.compose.foundation.Image(painter = BitmapPainter(bitmap), contentDescription = null, modifier = Modifier.size(32.dp))
                                    }
                                },
                                headlineContent = { Text(shortcut.label) },
                                supportingContent = { Text(app.label, style = MaterialTheme.typography.bodySmall) },
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                                modifier = Modifier.combinedClickable(onClick = { shortcutsHelper.launch(shortcut) })
                            )
                        }
                        item { Divider(Modifier.padding(vertical = 8.dp), color = Color.White.copy(alpha = 0.1f)) }
                    }

                    items(filtered, key = { it.packageName }) { app ->
                        ListItem(
                            leadingContent = {
                                val packIcon = remember(app.packageName) { iconPackManager.resolveIcon(app) }
                                BadgedIcon(show = showBadges && app.packageName in activeNotificationPackages) {
                                    if (packIcon != null) {
                                        AppIconView(icon = packIcon, shape = IconShape.SYSTEM_DEFAULT, sizeDp = 40, isPreShaped = true)
                                    } else {
                                        AppIconView(icon = app.icon, shape = IconShape.SYSTEM_DEFAULT, sizeDp = 40)
                                    }
                                }
                            },
                            headlineContent = { Text(app.label) },
                            supportingContent = { if (app.packageName in onGrid) Text("On home screen", color = Color.Gray) },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            modifier = Modifier.combinedClickable(
                                onClick = { repository.launchApp(app.packageName) },
                                onLongClick = { addCandidate = app.packageName }
                            )
                        )
                        Divider(color = Color.White.copy(alpha = 0.08f))
                    }
                }
            }
        }
    }

    addCandidate?.let { pkg ->
        val app = allApps.first { it.packageName == pkg }
        val dockFull = dockItems.size >= settings.dockSlots
        AlertDialog(
            onDismissRequest = { addCandidate = null },
            title = { Text(app.label) },
            text = {
                Column {
                    ListItem(
                        headlineContent = { Text("Add to home screen") },
                        modifier = Modifier.combinedClickable(onClick = {
                            scope.launch {
                                val widgetCells = hostedWidgets.flatMap { w ->
                                    (w.row until w.row + w.spanRows).flatMap { r -> (w.col until w.col + w.spanCols).map { c -> Triple(w.page, r, c) } }
                                }
                                val occupied = gridItems.map { Triple(it.page, it.row, it.col) } + widgetCells
                                val placement = findFirstFreeCell(occupied)
                                if (placement != null) repository.placeOnGrid(placement.first, placement.second, placement.third, pkg)
                            }
                            addCandidate = null
                        })
                    )
                    ListItem(
                        headlineContent = { Text(if (dockFull) "Add to dock (dock is full)" else "Add to dock") },
                        modifier = Modifier.combinedClickable(onClick = {
                            if (!dockFull) {
                                val usedSlots = dockItems.map { it.col }.toSet()
                                val freeSlot = (0 until settings.dockSlots).firstOrNull { it !in usedSlots }
                                if (freeSlot != null) scope.launch { repository.placeInDock(freeSlot, pkg) }
                            }
                            addCandidate = null
                        })
                    )
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { addCandidate = null }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun SortMenuButton(current: DrawerSortMode, onChange: (DrawerSortMode) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    IconButton(onClick = { expanded = true }) {
        Icon(androidx.compose.material.icons.Icons.Default.Sort, contentDescription = "Sort: ${current.label}")
    }
    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
        DrawerSortMode.entries.forEach { mode ->
            DropdownMenuItem(text = { Text(mode.label) }, onClick = { onChange(mode); expanded = false })
        }
    }
}

/** Searches page 0, then page 1, then page 2 (matching HomeScreen's PAGE_COUNT = 3)
 *  for the first empty cell, so newly added apps land wherever there's room across
 *  every page rather than only ever considering page 0. */
fun findFirstFreeCell(occupied: List<Triple<Int, Int, Int>>): Triple<Int, Int, Int>? {
    val occupiedSet = occupied.toSet()
    for (page in 0 until 3) {
        for (row in 0 until 5) {
            for (col in 0 until 4) {
                if (Triple(page, row, col) !in occupiedSet) return Triple(page, row, col)
            }
        }
    }
    return null // every page full
}
