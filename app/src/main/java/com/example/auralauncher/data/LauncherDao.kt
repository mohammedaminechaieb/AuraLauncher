package com.auralauncher.app.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface LauncherDao {

    // ---- Grid ----
    @Query("SELECT * FROM grid_items WHERE page = :page")
    fun observeGridItems(page: Int): Flow<List<GridItemEntity>>

    @Query("SELECT * FROM grid_items")
    fun observeAllGridItems(): Flow<List<GridItemEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertGridItem(item: GridItemEntity)

    @Query("DELETE FROM grid_items WHERE page = :page AND row = :row AND col = :col")
    suspend fun clearCell(page: Int, row: Int, col: Int)

    @Query("DELETE FROM grid_items WHERE packageName = :packageName")
    suspend fun removeFromGrid(packageName: String)

    // ---- Focus modes ----
    @Query("SELECT * FROM focus_modes")
    fun observeFocusModes(): Flow<List<FocusModeEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertFocusMode(mode: FocusModeEntity): Long

    @Delete
    suspend fun deleteFocusMode(mode: FocusModeEntity)

    @Query("UPDATE focus_modes SET isActive = 0")
    suspend fun deactivateAllFocusModes()

    @Query("UPDATE focus_modes SET isActive = 1 WHERE id = :id")
    suspend fun activateFocusMode(id: Long)

    // ---- Icon overrides ----
    @Query("SELECT * FROM icon_overrides")
    fun observeIconOverrides(): Flow<List<IconOverrideEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertIconOverride(override: IconOverrideEntity)

    @Query("DELETE FROM icon_overrides WHERE packageName = :packageName")
    suspend fun clearIconOverride(packageName: String)

    // ---- Hosted widgets ----
    @Query("SELECT * FROM hosted_widgets")
    fun observeAllHostedWidgets(): Flow<List<HostedWidgetEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertHostedWidget(widget: HostedWidgetEntity)

    @Query("DELETE FROM hosted_widgets WHERE appWidgetId = :appWidgetId")
    suspend fun deleteHostedWidget(appWidgetId: Int)

    // ---- Folders ----
    @Query("SELECT * FROM folders")
    fun observeAllFolders(): Flow<List<FolderEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertFolder(folder: FolderEntity): Long

    @Query("DELETE FROM folders WHERE id = :folderId")
    suspend fun deleteFolder(folderId: Long)

    @Update
    suspend fun updateFolder(folder: FolderEntity)

    @Query("SELECT * FROM folder_members")
    fun observeAllFolderMembers(): Flow<List<FolderMemberEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertFolderMember(member: FolderMemberEntity)

    @Query("DELETE FROM folder_members WHERE packageName = :packageName")
    suspend fun removeFolderMember(packageName: String)

    @Query("DELETE FROM folder_members WHERE folderId = :folderId")
    suspend fun removeAllMembersOfFolder(folderId: Long)

    // ---- One-shot reads for backup/restore (Flow versions above are for live UI observation) ----
    @Query("SELECT * FROM grid_items")
    suspend fun getAllGridItemsOnce(): List<GridItemEntity>

    @Query("SELECT * FROM folders")
    suspend fun getAllFoldersOnce(): List<FolderEntity>

    @Query("SELECT * FROM folder_members")
    suspend fun getAllFolderMembersOnce(): List<FolderMemberEntity>

    @Query("SELECT * FROM focus_modes")
    suspend fun getAllFocusModesOnce(): List<FocusModeEntity>

    @Query("SELECT * FROM icon_overrides")
    suspend fun getAllIconOverridesOnce(): List<IconOverrideEntity>

    @Query("SELECT * FROM hosted_widgets")
    suspend fun getAllHostedWidgetsOnce(): List<HostedWidgetEntity>

    // ---- Wipe-before-restore ----
    @Query("DELETE FROM grid_items")
    suspend fun clearAllGridItems()

    @Query("DELETE FROM folders")
    suspend fun clearAllFolders()

    @Query("DELETE FROM folder_members")
    suspend fun clearAllFolderMembers()

    @Query("DELETE FROM focus_modes")
    suspend fun clearAllFocusModes()

    @Query("DELETE FROM icon_overrides")
    suspend fun clearAllIconOverrides()
}
