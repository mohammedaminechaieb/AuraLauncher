package com.auralauncher.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/** A folder occupies exactly one grid cell, same as a single app icon would. */
@Entity(tableName = "folders")
data class FolderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val page: Int,
    val row: Int,
    val col: Int,
    val name: String
)

/**
 * Deliberately a separate table from GridItemEntity rather than adding a
 * nullable folderId column to it. GridItemEntity's primary key is
 * (page, row, col) — real on-screen position. An app inside a folder has
 * no real position of its own, so forcing it to still occupy a unique
 * (page, row, col) triple just to satisfy that primary key would mean
 * inventing fake coordinates for every folder member. Keeping membership
 * entirely separate avoids that: joining a folder deletes the app's
 * GridItemEntity row outright and adds one row here instead; leaving a
 * folder does the reverse.
 */
@Entity(tableName = "folder_members")
data class FolderMemberEntity(
    @PrimaryKey val packageName: String,
    val folderId: Long
)
