package com.auralauncher.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class IconShape { CIRCLE, SQUIRCLE, ROUNDED_SQUARE, TEARDROP, SYSTEM_DEFAULT }

/** A named subset of apps — switching the active focus mode filters the
 *  drawer and hides home-grid icons for anything not in [allowedPackages]. */
@Entity(tableName = "focus_modes")
data class FocusModeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val allowedPackages: List<String>,
    val isActive: Boolean = false
)

/** Optional per-app icon shape override; anything not listed here falls
 *  back to the global default set in IconThemeScreen. */
@Entity(tableName = "icon_overrides")
data class IconOverrideEntity(
    @PrimaryKey val packageName: String,
    val shape: IconShape
)
