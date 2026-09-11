package com.auralauncher.app.data

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager

class LauncherRepository(private val context: Context) {
    private val dao = AppDatabase.get(context).dao()
    private val pm: PackageManager = context.packageManager

    // ---- Installed apps (not persisted — PackageManager is the source of truth) ----
    fun loadAllApps(): List<AppInfo> {
        val intent = Intent(Intent.ACTION_MAIN, null).apply { addCategory(Intent.CATEGORY_LAUNCHER) }
        return pm.queryIntentActivities(intent, PackageManager.MATCH_ALL)
            .mapNotNull {
                if (it.activityInfo == null) return@mapNotNull null
                AppInfo(
                    packageName = it.activityInfo.packageName,
                    label = it.loadLabel(pm).toString(),
                    icon = it.loadIcon(pm),
                    componentName = "${it.activityInfo.packageName}/${it.activityInfo.name}",
                    category = it.activityInfo.applicationInfo?.category ?: android.content.pm.ApplicationInfo.CATEGORY_UNDEFINED
                )
            }
            .distinctBy { it.packageName }
            .filter { it.packageName != context.packageName } // don't list AuraLauncher itself
            .sortedBy { it.label.lowercase() }
    }

    fun launchApp(packageName: String) {
        pm.getLaunchIntentForPackage(packageName)?.let { intent ->
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            com.auralauncher.app.prefs.AppUsageTracker(context).recordLaunch(packageName)
        }
    }

    /** Install date, for the drawer's "Recently installed" sort mode. */
    fun installTimeOf(packageName: String): Long =
        runCatching { pm.getPackageInfo(packageName, 0).firstInstallTime }.getOrDefault(0L)

    /** Real launchers never start with a blank grid — first run should
     *  already look like a home screen. Fills the START of page 0 only,
     *  up to [maxApps] apps, leaving the rest of page 0 and all of pages
     *  1-2 genuinely empty — packing every cell on every page would leave
     *  no room to actually add widgets afterward. Called from HomeScreen
     *  only when the current grid is confirmed empty, so this never
     *  overwrites an arrangement the user already made. */
    suspend fun seedGrid(apps: List<AppInfo>, maxApps: Int, columns: Int) {
        apps.take(maxApps).forEachIndexed { index, app ->
            val row = index / columns
            val col = index % columns
            dao.upsertGridItem(GridItemEntity(0, row, col, app.packageName))
        }
    }

    // ---- Grid ----
    fun observeGridItems(page: Int) = dao.observeGridItems(page)
    fun observeAllGridItems() = dao.observeAllGridItems()
    suspend fun placeOnGrid(page: Int, row: Int, col: Int, packageName: String) =
        dao.upsertGridItem(GridItemEntity(page, row, col, packageName))
    suspend fun clearCell(page: Int, row: Int, col: Int) = dao.clearCell(page, row, col)
    suspend fun removeFromGrid(packageName: String) = dao.removeFromGrid(packageName)

    // ---- Dock ----
    // Reuses GridItemEntity with page = DOCK_PAGE as a reserved sentinel —
    // avoids yet another table/migration for what's structurally identical
    // to a grid row (position -> package name), just always visible instead
    // of tied to a swipeable page.
    fun observeDockItems() = dao.observeGridItems(DOCK_PAGE)
    suspend fun placeInDock(slot: Int, packageName: String) =
        dao.upsertGridItem(GridItemEntity(DOCK_PAGE, 0, slot, packageName))
    suspend fun removeFromDock(packageName: String) = dao.removeFromGrid(packageName)

    companion object {
        const val DOCK_PAGE = -1
    }

    // ---- Focus modes ----
    fun observeFocusModes() = dao.observeFocusModes()
    suspend fun saveFocusMode(mode: FocusModeEntity) = dao.upsertFocusMode(mode)
    suspend fun deleteFocusMode(mode: FocusModeEntity) = dao.deleteFocusMode(mode)
    suspend fun activateFocusMode(id: Long?) {
        dao.deactivateAllFocusModes()
        if (id != null) dao.activateFocusMode(id)
    }

    // ---- Icon overrides ----
    fun observeIconOverrides() = dao.observeIconOverrides()
    suspend fun setIconOverride(packageName: String, shape: IconShape) =
        dao.upsertIconOverride(IconOverrideEntity(packageName, shape))
    suspend fun clearIconOverride(packageName: String) = dao.clearIconOverride(packageName)

    // ---- Hosted widgets ----
    fun observeAllHostedWidgets() = dao.observeAllHostedWidgets()
    suspend fun saveHostedWidget(widget: HostedWidgetEntity) = dao.upsertHostedWidget(widget)
    suspend fun removeHostedWidget(appWidgetId: Int) = dao.deleteHostedWidget(appWidgetId)

    // ---- Folders ----
    fun observeAllFolders() = dao.observeAllFolders()
    fun observeAllFolderMembers() = dao.observeAllFolderMembers()

    /** Merges two standalone app icons into a brand-new folder at the
     *  dropped-on cell — this is what dragging app A onto app B does. */
    suspend fun createFolderFromApps(page: Int, row: Int, col: Int, draggedPackage: String, targetPackage: String): Long {
        val folderId = dao.upsertFolder(FolderEntity(page = page, row = row, col = col, name = "Folder"))
        dao.removeFromGrid(draggedPackage)
        dao.removeFromGrid(targetPackage)
        dao.upsertFolderMember(FolderMemberEntity(draggedPackage, folderId))
        dao.upsertFolderMember(FolderMemberEntity(targetPackage, folderId))
        return folderId
    }

    /** Drops an app into an EXISTING folder (dragged onto an already-formed folder icon). */
    suspend fun addAppToFolder(folderId: Long, packageName: String) {
        dao.removeFromGrid(packageName)
        dao.upsertFolderMember(FolderMemberEntity(packageName, folderId))
    }

    /** Takes an app back out of a folder and places it on the open grid. */
    suspend fun removeAppFromFolder(packageName: String, page: Int, row: Int, col: Int) {
        dao.removeFolderMember(packageName)
        dao.upsertGridItem(GridItemEntity(page, row, col, packageName))
    }

    suspend fun renameFolder(folder: FolderEntity, newName: String) = dao.updateFolder(folder.copy(name = newName))

    /** Deletes the folder and scatters its members back onto the grid at
     *  whichever free cells the caller found for them (see HomeScreen —
     *  reuses the same "first free cell across pages" search AppDrawerScreen uses). */
    suspend fun deleteFolder(folder: FolderEntity, memberPlacements: List<Pair<String, Triple<Int, Int, Int>>>) {
        memberPlacements.forEach { (pkg, placement) ->
            dao.removeFolderMember(pkg)
            dao.upsertGridItem(GridItemEntity(placement.first, placement.second, placement.third, pkg))
        }
        dao.deleteFolder(folder.id)
    }

    // ---- Backup / restore ----
    suspend fun exportBackup(settingsJson: org.json.JSONObject): String =
        com.auralauncher.app.backup.BackupService.export(dao, settingsJson)

    /** Returns the settings JSON block from the backup so the caller can
     *  apply it to LauncherSettingsManager — this repository doesn't own
     *  settings, so it hands that part back rather than reaching into it. */
    suspend fun importBackup(json: String): org.json.JSONObject =
        com.auralauncher.app.backup.BackupService.import(json, dao)
}
