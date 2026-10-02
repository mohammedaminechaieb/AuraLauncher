package com.auralauncher.app.settings

import android.content.Context
import com.auralauncher.app.data.IconShape
import org.json.JSONObject

enum class GestureAction(val label: String) {
    OPEN_DRAWER("Open app drawer"),
    OPEN_NOTIFICATIONS("Open notifications"),
    OPEN_SETTINGS("Open home settings"),
    NONE("Do nothing")
}

enum class DrawerSortMode(val label: String) {
    ALPHABETICAL("A to Z"),
    MOST_USED("Most used"),
    RECENTLY_INSTALLED("Recently installed")
}

enum class DrawerViewMode(val label: String) {
    LIST("List"),
    GRID("Grid")
}

/**
 * Every dial a real launcher exposes for the home grid, drawer, and
 * general behavior — grid dimensions, icon size, label visibility,
 * gesture mapping, dock size, drawer sort/view, status bar visibility.
 * Plain SharedPreferences, same reasoning as IconPackManager/
 * HiddenAppsManager: small scalar values, no need for Room's query/Flow
 * machinery, and it avoids yet another destructive schema migration.
 */
class LauncherSettingsManager(context: Context) {
    private val prefs = context.getSharedPreferences("auralauncher_settings_prefs", Context.MODE_PRIVATE)

    var columns: Int
        get() = prefs.getInt("grid_columns", 4)
        set(value) = prefs.edit().putInt("grid_columns", value.coerceIn(3, 6)).apply()

    var rows: Int
        get() = prefs.getInt("grid_rows", 5)
        set(value) = prefs.edit().putInt("grid_rows", value.coerceIn(4, 7)).apply()

    var iconSizeDp: Int
        get() = prefs.getInt("icon_size_dp", 56)
        set(value) = prefs.edit().putInt("icon_size_dp", value.coerceIn(40, 72)).apply()

    var showLabels: Boolean
        get() = prefs.getBoolean("show_labels", true)
        set(value) = prefs.edit().putBoolean("show_labels", value).apply()

    var swipeUpAction: GestureAction
        get() = readAction("swipe_up_action", GestureAction.OPEN_DRAWER)
        set(value) = prefs.edit().putString("swipe_up_action", value.name).apply()

    var swipeDownAction: GestureAction
        get() = readAction("swipe_down_action", GestureAction.OPEN_NOTIFICATIONS)
        set(value) = prefs.edit().putString("swipe_down_action", value.name).apply()

    var dockSlots: Int
        get() = prefs.getInt("dock_slots", 4)
        set(value) = prefs.edit().putInt("dock_slots", value.coerceIn(3, 6)).apply()

    var drawerSortMode: DrawerSortMode
        get() = runCatching { DrawerSortMode.valueOf(prefs.getString("drawer_sort", null) ?: "") }.getOrDefault(DrawerSortMode.ALPHABETICAL)
        set(value) = prefs.edit().putString("drawer_sort", value.name).apply()

    var drawerViewMode: DrawerViewMode
        get() = runCatching { DrawerViewMode.valueOf(prefs.getString("drawer_view", null) ?: "") }.getOrDefault(DrawerViewMode.LIST)
        set(value) = prefs.edit().putString("drawer_view", value.name).apply()

    var showHomeSearchBar: Boolean
        get() = prefs.getBoolean("show_home_search_bar", false)
        set(value) = prefs.edit().putBoolean("show_home_search_bar", value).apply()

    var hideStatusBar: Boolean
        get() = prefs.getBoolean("hide_status_bar", false)
        set(value) = prefs.edit().putBoolean("hide_status_bar", value).apply()

    var showNotificationBadges: Boolean
        get() = prefs.getBoolean("show_notification_badges", true)
        set(value) = prefs.edit().putBoolean("show_notification_badges", value).apply()

    /** Number of swipeable home pages. */
    var pageCount: Int
        get() = prefs.getInt("page_count", 3)
        set(value) = prefs.edit().putInt("page_count", value.coerceIn(1, 5)).apply()

    /** Shape applied to every icon that has no per-app override. */
    var iconShape: IconShape
        get() = runCatching { IconShape.valueOf(prefs.getString("icon_shape", null) ?: "") }.getOrDefault(IconShape.SYSTEM_DEFAULT)
        set(value) = prefs.edit().putString("icon_shape", value.name).apply()

    /** Set once the first-run layout has been created, so emptying the grid
     *  on purpose doesn't make it refill itself on the next launch. */
    var gridSeeded: Boolean
        get() = prefs.getBoolean("grid_seeded", false)
        set(value) = prefs.edit().putBoolean("grid_seeded", value).apply()

    /** First-run "how to use" card on the home screen. */
    var homeHintDismissed: Boolean
        get() = prefs.getBoolean("home_hint_dismissed", false)
        set(value) = prefs.edit().putBoolean("home_hint_dismissed", value).apply()

    /** Auto-seed on a fresh grid fills at most this many cells, leaving
     *  real empty space for widgets and manual arrangement rather than
     *  packing every cell on every page — see HomeScreen's seeding call. */
    val autoSeedMaxApps: Int get() = 12

    private fun readAction(key: String, default: GestureAction): GestureAction {
        val stored = prefs.getString(key, null) ?: return default
        return runCatching { GestureAction.valueOf(stored) }.getOrDefault(default)
    }

    fun toJson(): JSONObject = JSONObject().apply {
        put("columns", columns); put("rows", rows); put("iconSizeDp", iconSizeDp)
        put("showLabels", showLabels); put("swipeUpAction", swipeUpAction.name); put("swipeDownAction", swipeDownAction.name)
        put("dockSlots", dockSlots); put("drawerSortMode", drawerSortMode.name); put("drawerViewMode", drawerViewMode.name)
        put("showHomeSearchBar", showHomeSearchBar); put("hideStatusBar", hideStatusBar); put("showNotificationBadges", showNotificationBadges)
        put("pageCount", pageCount); put("iconShape", iconShape.name)
    }

    fun applyFromJson(json: JSONObject) {
        columns = json.optInt("columns", columns)
        rows = json.optInt("rows", rows)
        iconSizeDp = json.optInt("iconSizeDp", iconSizeDp)
        showLabels = json.optBoolean("showLabels", showLabels)
        swipeUpAction = runCatching { GestureAction.valueOf(json.getString("swipeUpAction")) }.getOrDefault(swipeUpAction)
        swipeDownAction = runCatching { GestureAction.valueOf(json.getString("swipeDownAction")) }.getOrDefault(swipeDownAction)
        dockSlots = json.optInt("dockSlots", dockSlots)
        drawerSortMode = runCatching { DrawerSortMode.valueOf(json.getString("drawerSortMode")) }.getOrDefault(drawerSortMode)
        drawerViewMode = runCatching { DrawerViewMode.valueOf(json.getString("drawerViewMode")) }.getOrDefault(drawerViewMode)
        showHomeSearchBar = json.optBoolean("showHomeSearchBar", showHomeSearchBar)
        hideStatusBar = json.optBoolean("hideStatusBar", hideStatusBar)
        showNotificationBadges = json.optBoolean("showNotificationBadges", showNotificationBadges)
        pageCount = json.optInt("pageCount", pageCount)
        iconShape = runCatching { IconShape.valueOf(json.getString("iconShape")) }.getOrDefault(iconShape)
    }
}
