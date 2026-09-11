package com.auralauncher.app.shortcuts

import android.content.Context
import android.content.pm.LauncherApps
import android.content.pm.ShortcutInfo
import android.graphics.drawable.Drawable
import android.os.Process

data class AppShortcut(
    val id: String,
    val packageName: String,
    val label: String,
    val icon: Drawable?
)

/**
 * LauncherApps.getShortcuts() is how a launcher reads the "app actions"
 * apps publish — e.g. Gmail's "Compose", Phone's "Call [contact]". It
 * needs LauncherApps.hasShortcutHostPermission(context) to be true,
 * which Android grants automatically to whichever app currently holds
 * the default-launcher role (see MainActivity's isDefaultLauncher() —
 * the two features are linked: shortcuts only work once you're actually
 * set as default). No separate manifest permission declaration exists
 * for this; it's tied entirely to launcher-role status.
 */
class AppShortcutsHelper(private val context: Context) {

    private val launcherApps = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps

    fun shortcutsFor(packageName: String, maxCount: Int = 3): List<AppShortcut> {
        if (!launcherApps.hasShortcutHostPermission()) return emptyList() // not the default launcher yet

        val query = LauncherApps.ShortcutQuery().apply {
            setPackage(packageName)
            setQueryFlags(
                LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC or
                    LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST or
                    LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED
            )
        }

        val shortcuts = runCatching {
            launcherApps.getShortcuts(query, Process.myUserHandle())
        }.getOrNull() ?: return emptyList()

        return shortcuts.take(maxCount).map { info: ShortcutInfo ->
            AppShortcut(
                id = info.id,
                packageName = info.`package`,
                label = (info.shortLabel ?: info.longLabel ?: info.id).toString(),
                icon = runCatching { launcherApps.getShortcutIconDrawable(info, 0) }.getOrNull()
            )
        }
    }

    fun launch(shortcut: AppShortcut) {
        runCatching {
            launcherApps.startShortcut(shortcut.packageName, shortcut.id, null, null, Process.myUserHandle())
        }
    }
}
