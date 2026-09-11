package com.auralauncher.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/** One occupied cell on the home grid. (page, row, col) together are unique. */
@Entity(tableName = "grid_items", primaryKeys = ["page", "row", "col"])
data class GridItemEntity(
    val page: Int,
    val row: Int,
    val col: Int,
    val packageName: String
)
