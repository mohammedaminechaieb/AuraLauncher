package com.auralauncher.app.prefs

import android.content.Context

/**
 * Plain SharedPreferences, same reasoning as IconPackManager — a small
 * set of package names doesn't need Room's query/Flow machinery, and
 * keeping it separate avoids yet another schema migration.
 */
class HiddenAppsManager(context: Context) {
    private val prefs = context.getSharedPreferences("auralauncher_hidden_apps_prefs", Context.MODE_PRIVATE)

    fun getHiddenPackages(): Set<String> = prefs.getStringSet("hidden_packages", emptySet()) ?: emptySet()

    fun setHidden(packageName: String, hidden: Boolean) {
        val current = getHiddenPackages().toMutableSet()
        if (hidden) current.add(packageName) else current.remove(packageName)
        prefs.edit().putStringSet("hidden_packages", current).apply()
    }

    fun isHidden(packageName: String): Boolean = packageName in getHiddenPackages()
}
