package com.auralauncher.app.ui

import com.auralauncher.app.data.FolderEntity
import com.auralauncher.app.data.GridItemEntity
import com.auralauncher.app.data.HostedWidgetEntity
import com.auralauncher.app.data.IconOverrideEntity
import com.auralauncher.app.data.IconShape
import com.auralauncher.app.data.LauncherRepository

/** Every (page, row, col) taken by an icon, folder or any cell a widget spans. */
fun occupiedCells(
    gridItems: List<GridItemEntity>,
    widgets: List<HostedWidgetEntity>,
    folders: List<FolderEntity>
): Set<Triple<Int, Int, Int>> {
    val icons = gridItems.filter { it.page != LauncherRepository.DOCK_PAGE }.map { Triple(it.page, it.row, it.col) }
    val widgetCells = widgets.flatMap { w ->
        (w.row until w.row + w.spanRows).flatMap { r -> (w.col until w.col + w.spanCols).map { c -> Triple(w.page, r, c) } }
    }
    val folderCells = folders.map { Triple(it.page, it.row, it.col) }
    return (icons + widgetCells + folderCells).toSet()
}

/**
 * First empty cell scanning page by page, row by row — using the user's
 * real grid size and page count (the old version hard-coded 3 pages of
 * 5x4, so on a 6-column grid it never used columns 4-5, and on a 4-row
 * grid it could place apps on a row that doesn't exist).
 */
fun findFirstFreeCell(occupied: Set<Triple<Int, Int, Int>>, pages: Int, rows: Int, columns: Int, startPage: Int = 0): Triple<Int, Int, Int>? {
    val order = (startPage until pages) + (0 until startPage)
    for (page in order) for (row in 0 until rows) for (col in 0 until columns) {
        val cell = Triple(page, row, col)
        if (cell !in occupied) return cell
    }
    return null
}

/** Per-app override if set, otherwise the global shape from settings. */
fun shapeFor(packageName: String, overrides: List<IconOverrideEntity>, global: IconShape): IconShape =
    overrides.firstOrNull { it.packageName == packageName }?.shape ?: global
