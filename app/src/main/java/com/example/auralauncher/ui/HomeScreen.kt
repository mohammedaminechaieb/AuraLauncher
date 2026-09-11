package com.auralauncher.app.ui

import android.annotation.SuppressLint
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import com.auralauncher.app.data.*
import com.auralauncher.app.iconpack.IconPackManager
import com.auralauncher.app.notifications.AuraNotificationListenerService
import com.auralauncher.app.settings.GestureAction
import com.auralauncher.app.settings.LauncherSettingsManager
import com.auralauncher.app.ui.components.AppIconView
import com.auralauncher.app.ui.components.BadgedIcon
import com.auralauncher.app.ui.components.FolderContentsDialog
import com.auralauncher.app.ui.components.FolderPreviewIcon
import com.auralauncher.app.util.NotificationShadeOpener
import com.auralauncher.app.util.rememberDeviceWallpaper
import com.auralauncher.app.widgethost.AuraWidgetHost
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private const val PAGE_COUNT = 3

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    repository: LauncherRepository,
    widgetHost: AuraWidgetHost,
    isDefaultLauncher: Boolean,
    onRequestDefaultLauncher: () -> Unit,
    onAddWidgetRequested: (page: Int, row: Int, col: Int) -> Unit,
    onOpenDrawer: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState(pageCount = { PAGE_COUNT })
    val iconPackManager = remember { IconPackManager(context) }
    val settings = remember { LauncherSettingsManager(context) }

    // Re-read on every recomposition of this screen (cheap SharedPreferences
    // reads) so coming back from Settings picks up new values immediately
    // without needing a more elaborate state-sync mechanism.
    val columns = settings.columns
    val rows = settings.rows
    val iconSizeDp = settings.iconSizeDp
    val showLabels = settings.showLabels
    val dockSlots = settings.dockSlots
    val showHomeSearchBar = settings.showHomeSearchBar
    val showBadges = settings.showNotificationBadges
    val activeNotificationPackages by AuraNotificationListenerService.activePackages.collectAsState()

    val allDockItems by repository.observeDockItems().collectAsState(initial = emptyList())

    val allGridItems by repository.observeAllGridItems().collectAsState(initial = emptyList())
    val allWidgets by repository.observeAllHostedWidgets().collectAsState(initial = emptyList())
    val allFolders by repository.observeAllFolders().collectAsState(initial = emptyList())
    val allFolderMembers by repository.observeAllFolderMembers().collectAsState(initial = emptyList())
    val focusModes by repository.observeFocusModes().collectAsState(initial = emptyList())
    val iconOverrides by repository.observeIconOverrides().collectAsState(initial = emptyList())
    val activeFocusMode = focusModes.firstOrNull { it.isActive }

    val allApps = remember { repository.loadAllApps() }
    val appsByPackage = remember(allApps) { allApps.associateBy { it.packageName } }
    val wallpaper = rememberDeviceWallpaper(context)

    // First-run seeding: fills only the START of page 0 (capped, see
    // LauncherSettingsManager.autoSeedMaxApps) so there's real empty space
    // left over on page 0 and all of pages 1-2 for widgets and your own
    // arrangement — packing every cell was the earlier mistake that made
    // "add a widget" impossible to reach.
    LaunchedEffect(Unit) {
        val currentGrid = repository.observeAllGridItems().first()
        if (currentGrid.isEmpty() && allApps.isNotEmpty()) {
            repository.seedGrid(allApps, settings.autoSeedMaxApps, columns)
        }
    }

    fun runGestureAction(action: GestureAction) {
        when (action) {
            GestureAction.OPEN_DRAWER -> onOpenDrawer()
            GestureAction.OPEN_NOTIFICATIONS -> NotificationShadeOpener.tryExpand(context)
            GestureAction.OPEN_SETTINGS -> onOpenSettings()
            GestureAction.NONE -> Unit
        }
    }

    Box(Modifier.fillMaxSize()) {
        if (wallpaper != null) {
            Image(bitmap = wallpaper, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.25f)))
        } else {
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF1B1B2F), Color(0xFF0A0A14)))))
        }

        Scaffold(
            containerColor = Color.Transparent,
            floatingActionButton = {
                FloatingActionButton(onClick = onOpenSettings) {
                    Icon(androidx.compose.material.icons.Icons.Default.Settings, contentDescription = "Home settings")
                }
            }
        ) { padding ->
            Column(
                Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .pointerInput(settings.swipeUpAction, settings.swipeDownAction) {
                        detectVerticalDragGestures { change, dragAmount ->
                            change.consume()
                            if (dragAmount < -40) runGestureAction(settings.swipeUpAction)
                            if (dragAmount > 40) runGestureAction(settings.swipeDownAction)
                        }
                    }
            ) {
                if (!isDefaultLauncher) {
                    Surface(color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(12.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text("Not your default home screen yet", modifier = Modifier.weight(1f))
                            TextButton(onClick = onRequestDefaultLauncher) { Text("Set as default") }
                        }
                    }
                }

                if (showHomeSearchBar) {
                    Surface(
                        color = Color.White.copy(alpha = 0.12f),
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(24.dp))
                    ) {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)
                                .pointerInput(Unit) { detectTapGestures(onTap = { onOpenDrawer() }) },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(androidx.compose.material.icons.Icons.Default.Search, contentDescription = null, tint = Color.White.copy(alpha = 0.8f))
                            Spacer(Modifier.width(8.dp))
                            Text("Search apps", color = Color.White.copy(alpha = 0.8f))
                        }
                    }
                }

                activeFocusMode?.let {
                    AssistChip(
                        onClick = { scope.launch { repository.activateFocusMode(null) } },
                        label = { Text("Focus: ${it.name} · tap to exit") },
                        modifier = Modifier.padding(8.dp)
                    )
                }

                HorizontalPager(state = pagerState, modifier = Modifier.weight(1f).fillMaxWidth()) { page ->
                    val pageIcons = remember(allGridItems, page) { allGridItems.filter { it.page == page } }
                    val visibleIcons = remember(pageIcons, activeFocusMode) {
                        if (activeFocusMode == null) pageIcons
                        else pageIcons.filter { it.packageName in activeFocusMode.allowedPackages }
                    }
                    val pageWidgets = remember(allWidgets, page) { allWidgets.filter { it.page == page } }
                    val pageFolders = remember(allFolders, page) { allFolders.filter { it.page == page } }

                    GridPage(
                        page = page,
                        columns = columns,
                        rows = rows,
                        iconSizeDp = iconSizeDp,
                        showLabels = showLabels,
                        icons = visibleIcons,
                        widgets = pageWidgets,
                        folders = pageFolders,
                        folderMembers = allFolderMembers,
                        allOccupiedCells = remember(allGridItems, allWidgets, allFolders) {
                            val iconCells = allGridItems.map { Triple(it.page, it.row, it.col) }
                            val widgetCells = allWidgets.flatMap { w ->
                                (w.row until w.row + w.spanRows).flatMap { r ->
                                    (w.col until w.col + w.spanCols).map { c -> Triple(w.page, r, c) }
                                }
                            }
                            val folderCells = allFolders.map { Triple(it.page, it.row, it.col) }
                            (iconCells + widgetCells + folderCells).toSet()
                        },
                        appsByPackage = appsByPackage,
                        iconOverrides = iconOverrides,
                        iconPackManager = iconPackManager,
                        repository = repository,
                        widgetHost = widgetHost,
                        onAddWidgetRequested = onAddWidgetRequested,
                        onOpenSettings = onOpenSettings,
                        showBadges = showBadges,
                        activeNotificationPackages = activeNotificationPackages
                    )
                }

                // Dock — persistent row of apps, always visible regardless of
                // which page is showing. Reuses the grid table under a
                // reserved page number (see LauncherRepository.DOCK_PAGE);
                // apps are added to it from the drawer's long-press menu.
                Dock(
                    slots = dockSlots,
                    items = allDockItems,
                    appsByPackage = appsByPackage,
                    iconOverrides = iconOverrides,
                    iconSizeDp = iconSizeDp,
                    iconPackManager = iconPackManager,
                    showBadges = showBadges,
                    activeNotificationPackages = activeNotificationPackages,
                    repository = repository
                )

                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.Center) {
                    repeat(PAGE_COUNT) { i ->
                        val selected = pagerState.currentPage == i
                        Box(
                            Modifier
                                .padding(4.dp)
                                .size(if (selected) 8.dp else 6.dp)
                                .background(
                                    if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                                    shape = androidx.compose.foundation.shape.CircleShape
                                )
                        )
                    }
                }
            }
        }
    }
}

private data class DragState(
    val fromRow: Int,
    val fromCol: Int,
    val app: AppInfo,
    val shape: IconShape,
    val resolvedIcon: android.graphics.drawable.Drawable?,
    val pointerOffset: Offset
)

@SuppressLint("UnusedBoxWithConstraintsScope")
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GridPage(
    page: Int,
    columns: Int,
    rows: Int,
    iconSizeDp: Int,
    showLabels: Boolean,
    icons: List<GridItemEntity>,
    widgets: List<HostedWidgetEntity>,
    folders: List<FolderEntity>,
    folderMembers: List<FolderMemberEntity>,
    allOccupiedCells: Set<Triple<Int, Int, Int>>,
    appsByPackage: Map<String, AppInfo>,
    iconOverrides: List<IconOverrideEntity>,
    iconPackManager: IconPackManager,
    repository: LauncherRepository,
    widgetHost: AuraWidgetHost,
    onAddWidgetRequested: (page: Int, row: Int, col: Int) -> Unit,
    onOpenSettings: () -> Unit,
    showBadges: Boolean,
    activeNotificationPackages: Set<String>
) {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val context = LocalContext.current

    val iconsByCell = remember(icons) { icons.associateBy { it.row to it.col } }
    val foldersByCell = remember(folders) { folders.associateBy { it.row to it.col } }
    val widgetOccupiedCells = remember(widgets) {
        widgets.flatMap { w ->
            (w.row until w.row + w.spanRows).flatMap { r -> (w.col until w.col + w.spanCols).map { c -> r to c } }
        }.toSet()
    }

    var dragState by remember { mutableStateOf<DragState?>(null) }
    var removeAppCandidate by remember { mutableStateOf<String?>(null) }
    var removeWidgetCandidate by remember { mutableStateOf<Int?>(null) }
    var emptyCellMenu by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var openFolder by remember { mutableStateOf<FolderEntity?>(null) }

    BoxWithConstraints(Modifier.fillMaxSize().padding(16.dp)) {
        val cellWidth = maxWidth / columns
        val cellHeight = maxHeight / rows

        fun rowColFromOffset(offset: Offset): Pair<Int, Int> {
            val row = (with(density) { offset.y.toDp() } / cellHeight).toInt().coerceIn(0, rows - 1)
            val col = (with(density) { offset.x.toDp() } / cellWidth).toInt().coerceIn(0, columns - 1)
            return row to col
        }

        for (row in 0 until rows) {
            for (col in 0 until columns) {
                val occupied = (row to col) in iconsByCell || (row to col) in widgetOccupiedCells || (row to col) in foldersByCell
                if (!occupied) {
                    Box(
                        Modifier
                            .offset(x = cellWidth * col, y = cellHeight * row)
                            .size(cellWidth, cellHeight)
                            .pointerInput(row, col) {
                                detectTapGestures(onLongPress = { emptyCellMenu = row to col })
                            }
                    )
                }
            }
        }

        for (folder in folders) {
            val members = remember(folderMembers, folder.id) {
                folderMembers.filter { it.folderId == folder.id }.mapNotNull { appsByPackage[it.packageName] }
            }
            Box(
                Modifier
                    .offset(x = cellWidth * folder.col, y = cellHeight * folder.row)
                    .size(cellWidth, cellHeight)
                    .combinedClickable(onClick = { openFolder = folder }, onLongClick = { openFolder = folder }),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    FolderPreviewIcon(members = members.take(4), cellSize = cellWidth)
                    if (showLabels) {
                        Text(folder.name, style = MaterialTheme.typography.labelSmall, color = Color.White, maxLines = 1)
                    }
                }
            }
        }

        for ((cell, item) in iconsByCell) {
            val (row, col) = cell
            val app = appsByPackage[item.packageName] ?: continue
            val shape = iconOverrides.firstOrNull { it.packageName == app.packageName }?.shape ?: IconShape.SYSTEM_DEFAULT
            val isDragging = dragState?.let { it.fromRow == row && it.fromCol == col } == true

            Box(
                Modifier
                    .offset(x = cellWidth * col, y = cellHeight * row)
                    .size(cellWidth, cellHeight)
                    .combinedClickable(
                        onClick = { repository.launchApp(app.packageName) },
                        onLongClick = { removeAppCandidate = app.packageName }
                    )
                    .pointerInput(app.packageName) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = { localOffset ->
                                val originGlobal = Offset(
                                    with(density) { (cellWidth * col).toPx() },
                                    with(density) { (cellHeight * row).toPx() }
                                )
                                dragState = DragState(row, col, app, shape, iconPackManager.resolveIcon(app), originGlobal + localOffset)
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                dragState = dragState?.copy(pointerOffset = (dragState?.pointerOffset ?: Offset.Zero) + dragAmount)
                            },
                            onDragEnd = {
                                val current = dragState
                                if (current != null) {
                                    val (targetRow, targetCol) = rowColFromOffset(current.pointerOffset)
                                    val isWidgetCell = (targetRow to targetCol) in widgetOccupiedCells
                                    val targetFolder = foldersByCell[targetRow to targetCol]
                                    val targetOccupant = iconsByCell[targetRow to targetCol]
                                    val droppedOnSelf = targetRow == current.fromRow && targetCol == current.fromCol

                                    scope.launch {
                                        when {
                                            isWidgetCell || droppedOnSelf -> Unit
                                            targetFolder != null -> repository.addAppToFolder(targetFolder.id, current.app.packageName)
                                            targetOccupant != null && targetOccupant.packageName != current.app.packageName -> {
                                                repository.createFolderFromApps(page, targetRow, targetCol, current.app.packageName, targetOccupant.packageName)
                                            }
                                            else -> {
                                                repository.placeOnGrid(page, targetRow, targetCol, current.app.packageName)
                                                repository.clearCell(page, current.fromRow, current.fromCol)
                                            }
                                        }
                                    }
                                }
                                dragState = null
                            },
                            onDragCancel = { dragState = null }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.graphicsLayer(alpha = if (isDragging) 0.25f else 1f)
                ) {
                    val packIcon = remember(app.packageName) { iconPackManager.resolveIcon(app) }
                    BadgedIcon(show = showBadges && app.packageName in activeNotificationPackages) {
                        if (packIcon != null) {
                            AppIconView(icon = packIcon, shape = shape, sizeDp = iconSizeDp, isPreShaped = true)
                        } else {
                            AppIconView(icon = app.icon, shape = shape, sizeDp = iconSizeDp)
                        }
                    }
                    if (showLabels) {
                        Text(
                            app.label,
                            style = MaterialTheme.typography.labelSmall.copy(
                                shadow = Shadow(color = Color.Black, offset = Offset(0f, 1f), blurRadius = 4f)
                            ),
                            color = Color.White,
                            textAlign = TextAlign.Center,
                            maxLines = 1
                        )
                    }
                }
            }
        }

        for (widget in widgets) {
            val info = widgetHost.getAppWidgetInfo(widget.appWidgetId)
            if (info == null) {
                Box(
                    Modifier
                        .offset(x = cellWidth * widget.col, y = cellHeight * widget.row)
                        .size(cellWidth * widget.spanCols, cellHeight * widget.spanRows)
                        .padding(4.dp)
                        .clip(androidx.compose.foundation.shape.RoundedCornerShape(20.dp))
                        .background(Color.Black.copy(alpha = 0.4f))
                        .combinedClickable(onClick = {}, onLongClick = { removeWidgetCandidate = widget.appWidgetId }),
                    contentAlignment = Alignment.Center
                ) { Text("Widget unavailable", style = MaterialTheme.typography.labelSmall, color = Color.White) }
                continue
            }

            Box(
                Modifier
                    .offset(x = cellWidth * widget.col, y = cellHeight * widget.row)
                    .size(cellWidth * widget.spanCols, cellHeight * widget.spanRows)
                    .padding(4.dp)
                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(20.dp))
                    .background(Color.Black.copy(alpha = 0.25f))
                    .pointerInput(widget.appWidgetId) {
                        detectTapGestures(onLongPress = { removeWidgetCandidate = widget.appWidgetId })
                    }
            ) {
                AndroidView(
                    factory = { ctx -> widgetHost.createView(ctx, widget.appWidgetId, info) },
                    modifier = Modifier.fillMaxSize().clip(androidx.compose.foundation.shape.RoundedCornerShape(20.dp))
                )
            }
        }

        dragState?.let { drag ->
            Box(
                Modifier
                    .zIndex(10f)
                    .graphicsLayer {
                        translationX = drag.pointerOffset.x - with(density) { (cellWidth / 2).toPx() }
                        translationY = drag.pointerOffset.y - with(density) { (cellHeight / 2).toPx() }
                        scaleX = 1.15f; scaleY = 1.15f; alpha = 0.9f
                    }
                    .size(cellWidth, cellHeight),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    if (drag.resolvedIcon != null) {
                        AppIconView(icon = drag.resolvedIcon, shape = drag.shape, sizeDp = iconSizeDp, isPreShaped = true)
                    } else {
                        AppIconView(icon = drag.app.icon, shape = drag.shape, sizeDp = iconSizeDp)
                    }
                    Text(drag.app.label, style = MaterialTheme.typography.labelSmall, color = Color.White, maxLines = 1)
                }
            }
        }
    }

    // The long-press-on-empty-space menu — this is now the GUARANTEED way
    // to add a widget or reach settings, independent of whether the grid
    // happens to have obvious empty space nearby (the previous version's
    // real bug: a fully-packed grid made this unreachable entirely).
    emptyCellMenu?.let { (row, col) ->
        AlertDialog(
            onDismissRequest = { emptyCellMenu = null },
            title = { Text("Home screen") },
            text = {
                Column {
                    ListItem(
                        headlineContent = { Text("Add widget") },
                        supportingContent = { Text("Opens Android's widget picker") },
                        modifier = Modifier.combinedClickable(onClick = {
                            val clampedRow = row.coerceAtMost(rows - 2)
                            val clampedCol = col.coerceAtMost(columns - 2)
                            onAddWidgetRequested(page, clampedRow, clampedCol)
                            emptyCellMenu = null
                        })
                    )
                    ListItem(
                        headlineContent = { Text("Wallpaper") },
                        supportingContent = { Text("Opens the system wallpaper picker") },
                        modifier = Modifier.combinedClickable(onClick = {
                            runCatching { context.startActivity(android.content.Intent(android.content.Intent.ACTION_SET_WALLPAPER)) }
                            emptyCellMenu = null
                        })
                    )
                    ListItem(
                        headlineContent = { Text("Home settings") },
                        supportingContent = { Text("Grid size, gestures, icon theme, and more") },
                        modifier = Modifier.combinedClickable(onClick = {
                            onOpenSettings()
                            emptyCellMenu = null
                        })
                    )
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { emptyCellMenu = null }) { Text("Cancel") } }
        )
    }

    removeAppCandidate?.let { pkg ->
        val label = appsByPackage[pkg]?.label ?: pkg
        AlertDialog(
            onDismissRequest = { removeAppCandidate = null },
            title = { Text(label) },
            text = { Text("Remove from home screen? (Still installed — find it again in the app drawer.)") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch { repository.removeFromGrid(pkg) }
                    removeAppCandidate = null
                }) { Text("Remove") }
            },
            dismissButton = { TextButton(onClick = { removeAppCandidate = null }) { Text("Cancel") } }
        )
    }

    removeWidgetCandidate?.let { appWidgetId ->
        AlertDialog(
            onDismissRequest = { removeWidgetCandidate = null },
            title = { Text("Remove widget?") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch { repository.removeHostedWidget(appWidgetId) }
                    widgetHost.deleteAppWidgetId(appWidgetId)
                    removeWidgetCandidate = null
                }) { Text("Remove") }
            },
            dismissButton = { TextButton(onClick = { removeWidgetCandidate = null }) { Text("Cancel") } }
        )
    }

    openFolder?.let { folder ->
        FolderContentsDialog(
            folder = folder,
            members = folderMembers.filter { it.folderId == folder.id }.mapNotNull { appsByPackage[it.packageName] },
            iconOverrides = iconOverrides,
            iconPackManager = iconPackManager,
            onLaunch = { pkg -> repository.launchApp(pkg); openFolder = null },
            onRemoveMember = { pkg ->
                scope.launch {
                    val placement = findFirstFreeCell(allOccupiedCells.toList()) ?: Triple(page, 0, 0)
                    repository.removeAppFromFolder(pkg, placement.first, placement.second, placement.third)
                }
            },
            onRename = { newName -> scope.launch { repository.renameFolder(folder, newName) } },
            onDelete = {
                scope.launch {
                    val members = folderMembers.filter { it.folderId == folder.id }
                    val consumed = mutableSetOf<Triple<Int, Int, Int>>()
                    val placements = members.mapNotNull { member ->
                        val placement = findFirstFreeCell((allOccupiedCells + consumed).toList())
                        if (placement != null) { consumed.add(placement); member.packageName to placement } else null
                    }
                    repository.deleteFolder(folder, placements)
                }
                openFolder = null
            },
            onDismiss = { openFolder = null }
        )
    }
}

/** Persistent row of apps rendered below the pager — always visible
 *  regardless of which page is currently showing, same role a dock plays
 *  in every real launcher. Backed by the same grid table as the pages,
 *  just under a reserved page number (LauncherRepository.DOCK_PAGE), so
 *  no separate table/migration was needed for it. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Dock(
    slots: Int,
    items: List<GridItemEntity>,
    appsByPackage: Map<String, AppInfo>,
    iconOverrides: List<IconOverrideEntity>,
    iconSizeDp: Int,
    iconPackManager: IconPackManager,
    showBadges: Boolean,
    activeNotificationPackages: Set<String>,
    repository: LauncherRepository
) {
    val scope = rememberCoroutineScope()
    val itemsBySlot = remember(items) { items.associateBy { it.col } }
    var removeCandidate by remember { mutableStateOf<String?>(null) }

    Surface(color = Color.Black.copy(alpha = 0.2f), shape = androidx.compose.foundation.shape.RoundedCornerShape(28.dp), modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(28.dp))) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
            repeat(slots) { slot ->
                val item = itemsBySlot[slot]
                val app = item?.let { appsByPackage[it.packageName] }
                Box(
                    Modifier
                        .size(iconSizeDp.dp + 8.dp)
                        .then(
                            if (app != null) Modifier.combinedClickable(
                                onClick = { repository.launchApp(app.packageName) },
                                onLongClick = { removeCandidate = app.packageName }
                            ) else Modifier
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (app != null) {
                        val shape = iconOverrides.firstOrNull { it.packageName == app.packageName }?.shape ?: IconShape.SYSTEM_DEFAULT
                        val packIcon = remember(app.packageName) { iconPackManager.resolveIcon(app) }
                        BadgedIcon(show = showBadges && app.packageName in activeNotificationPackages) {
                            if (packIcon != null) {
                                AppIconView(icon = packIcon, shape = shape, sizeDp = iconSizeDp, isPreShaped = true)
                            } else {
                                AppIconView(icon = app.icon, shape = shape, sizeDp = iconSizeDp)
                            }
                        }
                    }
                }
            }
        }
    }

    removeCandidate?.let { pkg ->
        val label = appsByPackage[pkg]?.label ?: pkg
        AlertDialog(
            onDismissRequest = { removeCandidate = null },
            title = { Text(label) },
            text = { Text("Remove from the dock?") },
            confirmButton = {
                TextButton(onClick = { scope.launch { repository.removeFromDock(pkg) }; removeCandidate = null }) { Text("Remove") }
            },
            dismissButton = { TextButton(onClick = { removeCandidate = null }) { Text("Cancel") } }
        )
    }
}

