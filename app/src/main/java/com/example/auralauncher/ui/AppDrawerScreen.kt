package com.auralauncher.app.ui

import android.content.pm.ApplicationInfo
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.auralauncher.app.data.AppInfo
import com.auralauncher.app.data.AppListStore
import com.auralauncher.app.data.LauncherRepository
import com.auralauncher.app.iconpack.IconPackManager
import com.auralauncher.app.notifications.AuraNotificationListenerService
import com.auralauncher.app.prefs.AppUsageTracker
import com.auralauncher.app.prefs.HiddenAppsManager
import com.auralauncher.app.settings.DrawerSortMode
import com.auralauncher.app.settings.DrawerViewMode
import com.auralauncher.app.settings.LauncherSettingsManager
import com.auralauncher.app.shortcuts.AppShortcutsHelper
import com.auralauncher.app.ui.components.AppActionsSheet
import com.auralauncher.app.ui.components.AppIconView
import com.auralauncher.app.ui.components.BadgedIcon
import kotlinx.coroutines.launch

/** Android's raw ApplicationInfo.CATEGORY_* constants grouped into a small tab set. */
private enum class DrawerTab(val label: String) {
    ALL("All"), GAMES("Games"), SOCIAL("Social"), PRODUCTIVITY("Work"), MEDIA("Media"), OTHER("Other")
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
fun AppDrawerScreen(repository: LauncherRepository, focusSearch: Boolean, onOpenHiddenApps: () -> Unit, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val iconPackManager = remember { IconPackManager(context) }
    val hiddenAppsManager = remember { HiddenAppsManager(context) }
    val shortcutsHelper = remember { AppShortcutsHelper(context) }
    val usageTracker = remember { AppUsageTracker(context) }
    val settings = remember { LauncherSettingsManager(context) }
    val activeNotificationPackages by AuraNotificationListenerService.activePackages.collectAsState()
    val showBadges = settings.showNotificationBadges
    val globalShape = settings.iconShape

    val apps by AppListStore.get(context).apps.collectAsState()
    val allApps = apps.orEmpty()
    val gridItems by repository.observeAllGridItems().collectAsState(initial = emptyList())
    val hostedWidgets by repository.observeAllHostedWidgets().collectAsState(initial = emptyList())
    val folders by repository.observeAllFolders().collectAsState(initial = emptyList())
    val folderMembers by repository.observeAllFolderMembers().collectAsState(initial = emptyList())
    val iconOverrides by repository.observeIconOverrides().collectAsState(initial = emptyList())
    val onHome = remember(gridItems, folderMembers) {
        gridItems.filter { it.page != LauncherRepository.DOCK_PAGE }.map { it.packageName }.toSet() + folderMembers.map { it.packageName }
    }
    val dockItems = remember(gridItems) { gridItems.filter { it.page == LauncherRepository.DOCK_PAGE } }

    var query by remember { mutableStateOf("") }
    var selectedTab by remember { mutableStateOf(DrawerTab.ALL) }
    var sortMode by remember { mutableStateOf(settings.drawerSortMode) }
    var viewMode by remember { mutableStateOf(settings.drawerViewMode) }
    var hiddenSet by remember { mutableStateOf(hiddenAppsManager.getHiddenPackages()) }
    var actionsFor by remember { mutableStateOf<AppInfo?>(null) }
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(focusSearch) { if (focusSearch) focusRequester.requestFocus() }

    val availableTabs = remember(allApps) {
        val present = allApps.map { categoryToTab(it.category) }.toSet()
        listOf(DrawerTab.ALL) + DrawerTab.entries.filter { it != DrawerTab.ALL && it in present }
    }

    val filtered = remember(query, allApps, hiddenSet, selectedTab, sortMode) {
        val base = allApps.filter {
            it.packageName !in hiddenSet &&
                (query.isBlank() || it.label.contains(query.trim(), ignoreCase = true)) &&
                (selectedTab == DrawerTab.ALL || categoryToTab(it.category) == selectedTab)
        }
        val sorted = when (sortMode) {
            DrawerSortMode.ALPHABETICAL -> base.sortedBy { it.label.lowercase() }
            DrawerSortMode.MOST_USED -> base.sortedByDescending { usageTracker.launchCount(it.packageName) }
            DrawerSortMode.RECENTLY_INSTALLED -> base.sortedByDescending { repository.installTimeOf(it.packageName) }
        }
        // While searching, names that START with the query rank first.
        if (query.isBlank()) sorted else sorted.sortedByDescending { it.label.startsWith(query.trim(), ignoreCase = true) }
    }

    val shortcutResults = remember(query, filtered) {
        if (query.isBlank()) emptyList()
        else filtered.take(5).flatMap { app -> shortcutsHelper.shortcutsFor(app.packageName).map { app to it } }
    }

    fun launch(app: AppInfo) {
        repository.launchApp(app.packageName)
        onBack()
    }

    fun longPress(app: AppInfo) {
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        actionsFor = app
    }

    Surface(color = Color(0xF20E0E14), contentColor = Color.White, modifier = Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            // Search bar + actions
            Row(Modifier.padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Search apps", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Default.Close, "Clear") } },
                    singleLine = true,
                    shape = RoundedCornerShape(28.dp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(onGo = { filtered.firstOrNull()?.let { launch(it) } }),
                    modifier = Modifier.weight(1f).focusRequester(focusRequester)
                )
                IconButton(onClick = {
                    viewMode = if (viewMode == DrawerViewMode.LIST) DrawerViewMode.GRID else DrawerViewMode.LIST
                    settings.drawerViewMode = viewMode
                }) {
                    Icon(if (viewMode == DrawerViewMode.LIST) Icons.Default.GridView else Icons.AutoMirrored.Filled.ViewList, "Toggle view")
                }
                SortMenuButton(current = sortMode) { sortMode = it; settings.drawerSortMode = it }
                IconButton(onClick = onOpenHiddenApps) { Icon(Icons.Default.VisibilityOff, "Hidden apps") }
            }
            if (availableTabs.size > 1 && query.isBlank()) {
                ScrollableTabRow(
                    selectedTabIndex = availableTabs.indexOf(selectedTab).coerceAtLeast(0),
                    containerColor = Color.Transparent,
                    contentColor = Color.White,
                    edgePadding = 12.dp
                ) {
                    availableTabs.forEach { tab -> Tab(selected = selectedTab == tab, onClick = { selectedTab = tab }, text = { Text(tab.label) }) }
                }
            }

            when {
                apps == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                filtered.isEmpty() && shortcutResults.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(if (query.isBlank()) "No apps in this category" else "No apps match \"$query\"", color = Color.Gray)
                }
                viewMode == DrawerViewMode.GRID -> LazyVerticalGrid(
                    columns = GridCells.Adaptive(84.dp),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(12.dp)
                ) {
                    if (shortcutResults.isNotEmpty()) {
                        item(span = { GridItemSpan(maxLineSpan) }) { ShortcutResults(shortcutResults, shortcutsHelper) }
                    }
                    items(filtered, key = { it.packageName }) { app ->
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .padding(4.dp)
                                .combinedClickable(onClick = { launch(app) }, onLongClick = { longPress(app) })
                                .padding(vertical = 8.dp)
                        ) {
                            DrawerIcon(app, 52, iconPackManager, shapeFor(app.packageName, iconOverrides, globalShape), showBadges && app.packageName in activeNotificationPackages)
                            Spacer(Modifier.height(6.dp))
                            Text(app.label, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                        }
                    }
                }
                else -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
                    if (shortcutResults.isNotEmpty()) item { ShortcutResults(shortcutResults, shortcutsHelper) }
                    items(filtered, key = { it.packageName }) { app ->
                        ListItem(
                            leadingContent = {
                                DrawerIcon(app, 42, iconPackManager, shapeFor(app.packageName, iconOverrides, globalShape), showBadges && app.packageName in activeNotificationPackages)
                            },
                            headlineContent = { Text(app.label) },
                            supportingContent = { if (app.packageName in onHome) Text("On home screen", color = Color.Gray) },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent, headlineColor = Color.White),
                            modifier = Modifier.combinedClickable(onClick = { launch(app) }, onLongClick = { longPress(app) })
                        )
                    }
                }
            }
        }
    }

    actionsFor?.let { app ->
        val pkg = app.packageName
        val freeDockSlot = (0 until settings.dockSlots).firstOrNull { slot -> dockItems.none { it.col == slot } }
        AppActionsSheet(
            app = app,
            shape = shapeFor(pkg, iconOverrides, globalShape),
            onDismiss = { actionsFor = null },
            onAddToHome = if (pkg !in onHome) ({
                scope.launch {
                    val cell = findFirstFreeCell(occupiedCells(gridItems, hostedWidgets, folders), settings.pageCount, settings.rows, settings.columns)
                    if (cell != null) repository.placeOnGrid(cell.first, cell.second, cell.third, pkg)
                }
            }) else null,
            onAddToDock = if (dockItems.none { it.packageName == pkg } && freeDockSlot != null) ({ scope.launch { repository.placeInDock(freeDockSlot, pkg) } }) else null,
            onHide = {
                hiddenAppsManager.setHidden(pkg, true)
                hiddenSet = hiddenAppsManager.getHiddenPackages()
            }
        )
    }
}

@Composable
private fun DrawerIcon(app: AppInfo, sizeDp: Int, iconPackManager: IconPackManager, shape: com.auralauncher.app.data.IconShape, badge: Boolean) {
    val packIcon = remember(app.packageName) { iconPackManager.resolveIcon(app) }
    BadgedIcon(show = badge) {
        if (packIcon != null) AppIconView(icon = packIcon, shape = shape, sizeDp = sizeDp, isPreShaped = true)
        else AppIconView(icon = app.icon, shape = shape, sizeDp = sizeDp)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ShortcutResults(results: List<Pair<AppInfo, com.auralauncher.app.shortcuts.AppShortcut>>, helper: AppShortcutsHelper) {
    Column {
        Text("Actions", style = MaterialTheme.typography.labelMedium, color = Color.Gray, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
        results.forEach { (app, shortcut) ->
            ListItem(
                leadingContent = {
                    val bitmap = remember(shortcut.id) { shortcut.icon?.toBitmap(96, 96)?.asImageBitmap() }
                    if (bitmap != null) androidx.compose.foundation.Image(BitmapPainter(bitmap), null, Modifier.size(32.dp))
                },
                headlineContent = { Text(shortcut.label) },
                supportingContent = { Text(app.label, style = MaterialTheme.typography.bodySmall) },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent, headlineColor = Color.White),
                modifier = Modifier.combinedClickable(onClick = { helper.launch(shortcut) })
            )
        }
        HorizontalDivider(Modifier.padding(vertical = 8.dp), color = Color.White.copy(alpha = 0.1f))
    }
}

@Composable
private fun SortMenuButton(current: DrawerSortMode, onChange: (DrawerSortMode) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) { Icon(Icons.AutoMirrored.Filled.Sort, "Sort: ${current.label}") }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DrawerSortMode.entries.forEach { mode ->
                DropdownMenuItem(
                    text = { Text(mode.label) },
                    trailingIcon = { if (mode == current) Text("✓") },
                    onClick = { onChange(mode); expanded = false }
                )
            }
        }
    }
}
