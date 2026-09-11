package com.auralauncher.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * One embedded third-party widget (calendar, weather, whatever the user
 * picked via Android's system widget picker). appWidgetId is the ID
 * Android's AppWidgetHost assigned when the widget was bound — it's the
 * handle used to recreate the AppWidgetHostView and to release the ID
 * when the widget is removed (AppWidgetHost.deleteAppWidgetId).
 *
 * Unlike GridItemEntity (always exactly 1x1), a widget can span multiple
 * cells — spanRows/spanCols say how many, anchored at (row, col) as its
 * top-left corner.
 */
@Entity(tableName = "hosted_widgets")
data class HostedWidgetEntity(
    @PrimaryKey val appWidgetId: Int,
    val page: Int,
    val row: Int,
    val col: Int,
    val spanRows: Int,
    val spanCols: Int
)
