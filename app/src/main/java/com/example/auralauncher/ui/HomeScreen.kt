package com.auralauncher.app.ui

import android.annotation.SuppressLint
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material.icons.filled.Widgets
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import com.auralauncher.app.data.*
import com.auralauncher.app.iconpack.IconPackManager
import com.auralauncher.app.notifications.AuraNotificationListenerService
import com.auralauncher.app.settings.GestureAction
import com.auralauncher.app.settings.LauncherSettingsManager
import com.auralauncher.app.ui.components.*
import com.auralauncher.app.util.NotificationShadeOpener
import com.auralauncher.app.widgethost.AuraWidgetHost
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Which app the actions sheet is open for, and where it was long-pressed. */
private data class ActionsTarget(val app: AppInfo, val inDock: Boolean)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    repository: LauncherRepository,
    widgetHost: AuraWidgetHost,
    isDefaultLauncher: Boolean,
    onRequestDefaultLauncher: () -> Unit,
    onAddWidgetRequested: (page: Int, row: Int, col: Int) -> Unit,
    onOpenDrawer: (focusSearch: Boolean) -> Unit,
    onOpenSettings: () -> Unit
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val iconPackManager = remember { IconPackManager(context) }
    val settings = remember { LauncherSettingsManager(context) }

    // Cheap SharedPreferences reads; HomeScreen is recomposed fresh whenever
    // you come back from Settings, so new values apply immediately.
    val columns = settings.columns
    val rows = settings.rows
    val pageCount = settings.pageCount
    val iconSizeDp = settings.iconSizeDp
    val showLabels = settings.showLabels
    val dockSlots = settings.dockSlots
    val globalShape = settings.iconShape
    val showBadges = settings.showNotificationBadges
    val pagerState = rememberPagerState(pageCount = { pageCount })

    val activeNotificationPackages by AuraNotificationListenerService.activePackages.collectAsState()
    val apps by AppListStore.get(context).apps.collectAsState()
    val allApps = apps.orEmpty()
    val appsByPackage = remember(allApps) { allApps.associateBy { it.packageName } }

    val allGridItems by repository.observeAllGridItems().collectAsState(initial = emptyList())
    val allDockItems = remember(allGridItems) { allGridItems.filter { it.page == LauncherRepository.DOCK_PAGE } }
    val allWidgets by repository.observeAllHostedWidgets().collectAsState(initial = emptyList())
    val allFolders by repository.observeAllFolders().collectAsState(initial = emptyList())
    val allFolderMembers by repository.observeAllFolderMembers().collectAsState(initial = emptyList())
    val focusModes by repository.observeFocusModes().collectAsState(initial = emptyList())
    val iconOverrides by repository.observeIconOverrides().collectAsState(initial = emptyList())
    val activeFocusMode = focusModes.firstOrNull { it.isActive }
    val occupied = remember(allGridItems, allWidgets, allFolders) { occupiedCells(allGridItems, allWidgets, allFolders) }

    var actionsTarget by remember { mutableStateOf<ActionsTarget?>(null) }
    var hintVisible by remember { mutableStateOf(!settings.homeHintDismissed) }
    var defaultBannerVisible by remember { mutableStateOf(true) }

    // First run: fill the start of page 0 only, leaving room for widgets.
    LaunchedEffect(allApps.isNotEmpty()) {
        if (allApps.isEmpty() || settings.gridSeeded) return@LaunchedEffect
        if (repository.observeAllGridItems().first().isEmpty()) {
            repository.seedGrid(allApps, settings.autoSeedMaxApps, columns)
        }
        settings.gridSeeded = true
    }

    // Drop anything uninstalled while the launcher wasn't running. Runs once
    // per launch — later uninstalls arrive via AppListStore's package
    // callback, and re-checking on every list change could wrongly remove an
    // app that's briefly missing while it updates.
    LaunchedEffect(apps != null) {
        val installed = apps?.map { it.packageName }?.toSet() ?: return@LaunchedEffect
        (repository.observeAllGridItems().first().map { it.packageName } +
            repository.observeAllFolderMembers().first().map { it.packageName })
            .filter { it !in installed }
            .distinct()
            .forEach { repository.forgetPackage(it) }
    }

    // Shrinking the grid (or page count) in Settings would leave icons outside
    // the visible area — move them into free cells instead of losing them.
    LaunchedEffect(columns, rows, pageCount, allGridItems, allFolders) {
        fun outside(p: Int, r: Int, c: Int) = p >= pageCount || r >= rows || c >= columns
        val strayIcons = allGridItems.filter { it.page != LauncherRepository.DOCK_PAGE && outside(it.page, it.row, it.col) }
        val strayFolders = allFolders.filter { outside(it.page, it.row, it.col) }
        if (strayIcons.isEmpty() && strayFolders.isEmpty()) return@LaunchedEffect
        val taken = occupied.filter { (p, r, c) -> !outside(p, r, c) }.toMutableSet()
        strayIcons.forEach { item ->
            val cell = findFirstFreeCell(taken, pageCount, rows, columns) ?: return@forEach
            taken += cell
            repository.clearCell(item.page, item.row, item.col)
            repository.placeOnGrid(cell.first, cell.second, cell.third, item.packageName)
        }
        strayFolders.forEach { folder ->
            val cell = findFirstFreeCell(taken, pageCount, rows, columns) ?: return@forEach
            taken += cell
            repository.moveFolder(folder, cell.first, cell.second, cell.third)
        }
    }

    fun runGestureAction(action: GestureAction) {
        when (action) {
            GestureAction.OPEN_DRAWER -> onOpenDrawer(false)
            GestureAction.OPEN_NOTIFICATIONS -> NotificationShadeOpener.tryExpand(context)
            GestureAction.OPEN_SETTINGS -> onOpenSettings()
            GestureAction.NONE -> Unit
        }
    }

    val swipeThresholdPx = with(density) { 80.dp.toPx() }

    // The window itself shows the real wallpaper (windowShowWallpaper), so
    // the home screen only adds soft scrims to keep labels readable.
    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(0f to Color.Black.copy(alpha = 0.25f), 0.25f to Color.Transparent, 0.75f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.35f)))
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .pointerInput(settings.swipeUpAction, settings.swipeDownAction) {
                    // Accumulate the whole drag and decide once at the end — the
                    // old per-frame 40px check rarely fired and could fire twice.
                    // A long drag OR a quick flick both count.
                    var total = 0f
                    val velocity = VelocityTracker()
                    val flickVelocity = 1200.dp.toPx()
                    detectVerticalDragGestures(
                        onDragStart = { total = 0f; velocity.resetTracking() },
                        onVerticalDrag = { change, amount ->
                            total += amount
                            velocity.addPosition(change.uptimeMillis, change.position)
                            change.consume()
                        },
                        onDragEnd = {
                            val vy = velocity.calculateVelocity().y
                            if (total < -swipeThresholdPx || (total < 0 && vy < -flickVelocity)) runGestureAction(settings.swipeUpAction)
                            else if (total > swipeThresholdPx || (total > 0 && vy > flickVelocity)) runGestureAction(settings.swipeDownAction)
                        }
                    )
                }
        ) {
            if (!isDefaultLauncher && defaultBannerVisible) {
                Banner(
                    text = "Make AuraLauncher your home screen so the Home button opens it.",
                    action = "Set as default",
                    onAction = onRequestDefaultLauncher,
                    onClose = { defaultBannerVisible = false }
                )
            }

            AnimatedVisibility(hintVisible) {
                Banner(
                    text = "Swipe up for all apps · hold an app for options or to move it · hold empty space for widgets & settings",
                    action = "Got it",
                    onAction = { hintVisible = false; settings.homeHintDismissed = true },
                    onClose = null
                )
            }

            if (settings.showHomeSearchBar) {
                Surface(
                    color = Color.White.copy(alpha = 0.16f),
                    shape = RoundedCornerShape(28.dp),
                    onClick = { onOpenDrawer(true) },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)
                ) {
                    Row(Modifier.padding(horizontal = 18.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Search, contentDescription = null, tint = Color.White.copy(alpha = 0.85f))
                        Spacer(Modifier.width(10.dp))
                        Text("Search apps", color = Color.White.copy(alpha = 0.85f))
                    }
                }
            }

            activeFocusMode?.let {
                AssistChip(
                    onClick = { scope.launch { repository.activateFocusMode(null) } },
                    label = { Text("Focus: ${it.name} · tap to exit") },
                    colors = AssistChipDefaults.assistChipColors(containerColor = Color.Black.copy(alpha = 0.35f), labelColor = Color.White),
                    modifier = Modifier.padding(horizontal = 16.dp).align(Alignment.CenterHorizontally)
                )
            }

            HorizontalPager(state = pagerState, modifier = Modifier.weight(1f).fillMaxWidth()) { page ->
                val pageIcons = remember(allGridItems, page, activeFocusMode) {
                    allGridItems.filter { it.page == page && (activeFocusMode == null || it.packageName in activeFocusMode.allowedPackages) }
                }
                GridPage(
                    page = page,
                    columns = columns,
                    rows = rows,
                    iconSizeDp = iconSizeDp,
                    showLabels = showLabels,
                    globalShape = globalShape,
                    icons = pageIcons,
                    widgets = remember(allWidgets, page) { allWidgets.filter { it.page == page } },
                    folders = remember(allFolders, page) { allFolders.filter { it.page == page } },
                    folderMembers = allFolderMembers,
                    findFreeCell = { extra -> findFirstFreeCell(occupied + extra, pageCount, rows, columns, startPage = page) },
                    appsByPackage = appsByPackage,
                    iconOverrides = iconOverrides,
                    iconPackManager = iconPackManager,
                    repository = repository,
                    widgetHost = widgetHost,
                    onAddWidgetRequested = onAddWidgetRequested,
                    onOpenSettings = onOpenSettings,
                    onShowActions = { app -> actionsTarget = ActionsTarget(app, inDock = false) },
                    showBadges = showBadges,
                    activeNotificationPackages = activeNotificationPackages
                )
            }

            if (pageCount > 1) {
                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.Center) {
                    repeat(pageCount) { i ->
                        val selected = pagerState.currentPage == i
                        Box(
                            Modifier
                                .padding(3.dp)
                                .size(width = if (selected) 18.dp else 6.dp, height = 6.dp)
                                .background(Color.White.copy(alpha = if (selected) 0.95f else 0.45f), CircleShape)
                        )
                    }
                }
            }

            Dock(
                slots = dockSlots,
                items = allDockItems,
                appsByPackage = appsByPackage,
                iconOverrides = iconOverrides,
                globalShape = globalShape,
                iconSizeDp = iconSizeDp,
                iconPackManager = iconPackManager,
                showBadges = showBadges,
                activeNotificationPackages = activeNotificationPackages,
                repository = repository,
                onShowActions = { app -> actionsTarget = ActionsTarget(app, inDock = true) }
            )
        }
    }

    actionsTarget?.let { target ->
        val pkg = target.app.packageName
        val onHome = allGridItems.any { it.packageName == pkg && it.page != LauncherRepository.DOCK_PAGE }
        val inDock = allDockItems.any { it.packageName == pkg }
        val freeDockSlot = (0 until dockSlots).firstOrNull { slot -> allDockItems.none { it.col == slot } }
        AppActionsSheet(
            app = target.app,
            shape = shapeFor(pkg, iconOverrides, globalShape),
            onDismiss = { actionsTarget = null },
            onRemoveFromHome = if (onHome && !target.inDock) ({ scope.launch { repository.removeFromGrid(pkg) } }) else null,
            onAddToDock = if (!inDock && freeDockSlot != null) ({ scope.launch { repository.placeInDock(freeDockSlot, pkg) } }) else null,
            onRemoveFromDock = if (target.inDock) ({ scope.launch { repository.removeDockSlot(pkg) } }) else null,
        )
    }
}

@Composable
private fun Banner(text: String, action: String, onAction: () -> Unit, onClose: (() -> Unit)?) {
    Surface(
        color = Color.Black.copy(alpha = 0.55f),
        contentColor = Color.White,
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)
    ) {
        Row(Modifier.padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(text, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
            TextButton(onClick = onAction) { Text(action) }
            if (onClose != null) IconButton(onClick = onClose) { Icon(Icons.Default.Close, "Dismiss", Modifier.size(18.dp)) }
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
    globalShape: IconShape,
    icons: List<GridItemEntity>,
    widgets: List<HostedWidgetEntity>,
    folders: List<FolderEntity>,
    folderMembers: List<FolderMemberEntity>,
    findFreeCell: (extra: Set<Triple<Int, Int, Int>>) -> Triple<Int, Int, Int>?,
    appsByPackage: Map<String, AppInfo>,
    iconOverrides: List<IconOverrideEntity>,
    iconPackManager: IconPackManager,
    repository: LauncherRepository,
    widgetHost: AuraWidgetHost,
    onAddWidgetRequested: (page: Int, row: Int, col: Int) -> Unit,
    onOpenSettings: () -> Unit,
    onShowActions: (AppInfo) -> Unit,
    showBadges: Boolean,
    activeNotificationPackages: Set<String>
) {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current

    val iconsByCell = remember(icons) { icons.associateBy { it.row to it.col } }
    val foldersByCell = remember(folders) { folders.associateBy { it.row to it.col } }
    val widgetOccupiedCells = remember(widgets) {
        widgets.flatMap { w ->
            (w.row until w.row + w.spanRows).flatMap { r -> (w.col until w.col + w.spanCols).map { c -> r to c } }
        }.toSet()
    }

    var dragState by remember { mutableStateOf<DragState?>(null) }
    var pressedCell by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var removeWidgetCandidate by remember { mutableStateOf<Int?>(null) }
    var emptyCellMenu by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var openFolder by remember { mutableStateOf<FolderEntity?>(null) }

    BoxWithConstraints(Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp)) {
        val cellWidth = maxWidth / columns
        val cellHeight = maxHeight / rows

        fun rowColFromOffset(offset: Offset): Pair<Int, Int> {
            val row = (with(density) { offset.y.toDp() } / cellHeight).toInt().coerceIn(0, rows - 1)
            val col = (with(density) { offset.x.toDp() } / cellWidth).toInt().coerceIn(0, columns - 1)
            return row to col
        }

        // Empty cells: long-press opens the home-screen menu.
        for (row in 0 until rows) {
            for (col in 0 until columns) {
                val occupied = (row to col) in iconsByCell || (row to col) in widgetOccupiedCells || (row to col) in foldersByCell
                if (!occupied) {
                    Box(
                        Modifier
                            .offset(x = cellWidth * col, y = cellHeight * row)
                            .size(cellWidth, cellHeight)
                            .pointerInput(row, col) { detectTapGestures(onLongPress = { haptics.performHapticFeedback(HapticFeedbackType.LongPress); emptyCellMenu = row to col }) }
                    )
                }
            }
        }

        // Drop-target highlight while dragging.
        dragState?.let { drag ->
            val (r, c) = rowColFromOffset(drag.pointerOffset)
            Box(
                Modifier
                    .offset(x = cellWidth * c, y = cellHeight * r)
                    .size(cellWidth, cellHeight)
                    .padding(4.dp)
                    .border(2.dp, Color.White.copy(alpha = 0.7f), RoundedCornerShape(18.dp))
                    .background(Color.White.copy(alpha = 0.12f), RoundedCornerShape(18.dp))
            )
        }

        for (folder in folders) {
            val members = remember(folderMembers, folder.id, appsByPackage) {
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
                    FolderPreviewIcon(members = members.take(4), sizeDp = iconSizeDp)
                    if (showLabels) IconLabel(folder.name)
                }
            }
        }

        for ((cell, item) in iconsByCell) {
            val (row, col) = cell
            val app = appsByPackage[item.packageName] ?: continue
            val shape = shapeFor(app.packageName, iconOverrides, globalShape)
            val isDragging = dragState?.let { it.fromRow == row && it.fromCol == col } == true
            val isPressed = pressedCell == (row to col) && dragState == null

            Box(
                Modifier
                    .offset(x = cellWidth * col, y = cellHeight * row)
                    .size(cellWidth, cellHeight)
                    .appIconGestures(
                        key = Triple(app.packageName, row, col),
                        onTap = { repository.launchApp(app.packageName) },
                        onLongPressStart = {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            pressedCell = row to col
                        },
                        onLongPress = {
                            pressedCell = null
                            onShowActions(app)
                        },
                        onDragStart = { local ->
                            pressedCell = null
                            val origin = with(density) { Offset((cellWidth * col).toPx(), (cellHeight * row).toPx()) }
                            dragState = DragState(row, col, app, shape, iconPackManager.resolveIcon(app), origin + local)
                        },
                        onDrag = { delta -> dragState = dragState?.let { it.copy(pointerOffset = it.pointerOffset + delta) } },
                        onDragEnd = { cancelled ->
                            pressedCell = null
                            val current = dragState
                            dragState = null
                            if (current == null || cancelled) return@appIconGestures
                            val (targetRow, targetCol) = rowColFromOffset(current.pointerOffset)
                            val targetFolder = foldersByCell[targetRow to targetCol]
                            val targetOccupant = iconsByCell[targetRow to targetCol]
                            val droppedOnSelf = targetRow == current.fromRow && targetCol == current.fromCol
                            scope.launch {
                                when {
                                    (targetRow to targetCol) in widgetOccupiedCells || droppedOnSelf -> Unit
                                    targetFolder != null -> repository.addAppToFolder(targetFolder.id, current.app.packageName)
                                    targetOccupant != null -> repository.createFolderFromApps(page, targetRow, targetCol, current.app.packageName, targetOccupant.packageName)
                                    else -> {
                                        repository.clearCell(page, current.fromRow, current.fromCol)
                                        repository.placeOnGrid(page, targetRow, targetCol, current.app.packageName)
                                    }
                                }
                            }
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.graphicsLayer {
                        alpha = if (isDragging) 0.25f else 1f
                        val s = if (isPressed) 0.9f else 1f
                        scaleX = s; scaleY = s
                    }
                ) {
                    val packIcon = remember(app.packageName) { iconPackManager.resolveIcon(app) }
                    BadgedIcon(show = showBadges && app.packageName in activeNotificationPackages) {
                        if (packIcon != null) AppIconView(icon = packIcon, shape = shape, sizeDp = iconSizeDp, isPreShaped = true)
                        else AppIconView(icon = app.icon, shape = shape, sizeDp = iconSizeDp)
                    }
                    if (showLabels) IconLabel(app.label)
                }
            }
        }

        for (widget in widgets) {
            val info = widgetHost.getAppWidgetInfo(widget.appWidgetId)
            val base = Modifier
                .offset(x = cellWidth * widget.col, y = cellHeight * widget.row)
                .size(cellWidth * widget.spanCols, cellHeight * widget.spanRows)
                .padding(4.dp)
                .clip(RoundedCornerShape(20.dp))
            if (info == null) {
                Box(
                    base.background(Color.Black.copy(alpha = 0.4f))
                        .combinedClickable(onClick = {}, onLongClick = { removeWidgetCandidate = widget.appWidgetId }),
                    contentAlignment = Alignment.Center
                ) { Text("Widget unavailable\nhold to remove", style = MaterialTheme.typography.labelSmall, color = Color.White, textAlign = TextAlign.Center) }
                continue
            }
            Box(base.pointerInput(widget.appWidgetId) { detectTapGestures(onLongPress = { removeWidgetCandidate = widget.appWidgetId }) }) {
                AndroidView(
                    factory = { ctx -> widgetHost.createView(ctx, widget.appWidgetId, info) },
                    modifier = Modifier.fillMaxSize()
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
                        scaleX = 1.15f; scaleY = 1.15f; alpha = 0.95f
                    }
                    .size(cellWidth, cellHeight),
                contentAlignment = Alignment.Center
            ) {
                if (drag.resolvedIcon != null) AppIconView(icon = drag.resolvedIcon, shape = drag.shape, sizeDp = iconSizeDp, isPreShaped = true)
                else AppIconView(icon = drag.app.icon, shape = drag.shape, sizeDp = iconSizeDp)
            }
        }
    }

    // Long-press on empty space: the guaranteed way to add a widget or reach settings.
    emptyCellMenu?.let { (row, col) ->
        HomeMenuSheet(
            onDismiss = { emptyCellMenu = null },
            onAddWidget = { onAddWidgetRequested(page, row.coerceAtMost(rows - 2).coerceAtLeast(0), col.coerceAtMost(columns - 2).coerceAtLeast(0)) },
            onWallpaper = { runCatching { context.startActivity(android.content.Intent(android.content.Intent.ACTION_SET_WALLPAPER)) } },
            onSettings = onOpenSettings
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
            globalShape = globalShape,
            iconPackManager = iconPackManager,
            onLaunch = { pkg -> repository.launchApp(pkg); openFolder = null },
            onRemoveMember = { pkg ->
                scope.launch {
                    val placement = findFreeCell(emptySet()) ?: return@launch
                    repository.removeAppFromFolder(pkg, placement.first, placement.second, placement.third)
                }
            },
            onRename = { newName -> scope.launch { repository.renameFolder(folder, newName) } },
            onDelete = {
                scope.launch {
                    val consumed = mutableSetOf<Triple<Int, Int, Int>>()
                    val placements = folderMembers.filter { it.folderId == folder.id }.mapNotNull { member ->
                        findFreeCell(consumed)?.let { consumed.add(it); member.packageName to it }
                    }
                    repository.deleteFolder(folder, placements)
                }
                openFolder = null
            },
            onDismiss = { openFolder = null }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeMenuSheet(onDismiss: () -> Unit, onAddWidget: () -> Unit, onWallpaper: () -> Unit, onSettings: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(bottom = 24.dp)) {
            listOf(
                Triple(Icons.Default.Widgets, "Add widget", onAddWidget),
                Triple(Icons.Default.Wallpaper, "Wallpaper", onWallpaper),
                Triple(Icons.Default.Settings, "Home settings", onSettings),
            ).forEach { (icon, label, action) ->
                ListItem(
                    leadingContent = { Icon(icon, null) },
                    headlineContent = { Text(label) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    modifier = Modifier.combinedClickableCompat { onDismiss(); action() }
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
private fun Modifier.combinedClickableCompat(onClick: () -> Unit) = this.combinedClickable(onClick = onClick)

@Composable
private fun IconLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall.copy(shadow = Shadow(color = Color.Black, offset = Offset(0f, 1f), blurRadius = 4f)),
        color = Color.White,
        textAlign = TextAlign.Center,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(top = 4.dp, start = 2.dp, end = 2.dp)
    )
}

/** Persistent row of apps below the pager, visible on every page. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Dock(
    slots: Int,
    items: List<GridItemEntity>,
    appsByPackage: Map<String, AppInfo>,
    iconOverrides: List<IconOverrideEntity>,
    globalShape: IconShape,
    iconSizeDp: Int,
    iconPackManager: IconPackManager,
    showBadges: Boolean,
    activeNotificationPackages: Set<String>,
    repository: LauncherRepository,
    onShowActions: (AppInfo) -> Unit
) {
    val itemsBySlot = remember(items) { items.associateBy { it.col } }
    val haptics = LocalHapticFeedback.current
    Surface(
        color = Color.White.copy(alpha = 0.14f),
        shape = RoundedCornerShape(28.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Row(Modifier.padding(horizontal = 8.dp, vertical = 10.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
            repeat(slots) { slot ->
                val app = itemsBySlot[slot]?.let { appsByPackage[it.packageName] }
                Box(
                    Modifier
                        .size(iconSizeDp.dp + 8.dp)
                        .then(
                            if (app != null) Modifier.combinedClickable(
                                onClick = { repository.launchApp(app.packageName) },
                                onLongClick = { haptics.performHapticFeedback(HapticFeedbackType.LongPress); onShowActions(app) }
                            ) else Modifier
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (app != null) {
                        val shape = shapeFor(app.packageName, iconOverrides, globalShape)
                        val packIcon = remember(app.packageName) { iconPackManager.resolveIcon(app) }
                        BadgedIcon(show = showBadges && app.packageName in activeNotificationPackages) {
                            if (packIcon != null) AppIconView(icon = packIcon, shape = shape, sizeDp = iconSizeDp, isPreShaped = true)
                            else AppIconView(icon = app.icon, shape = shape, sizeDp = iconSizeDp)
                        }
                    } else {
                        // Faint placeholder so empty dock slots are discoverable.
                        Box(Modifier.size((iconSizeDp * 0.5f).dp).border(1.dp, Color.White.copy(alpha = 0.25f), CircleShape))
                    }
                }
            }
        }
    }
}
