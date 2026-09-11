package com.auralauncher.app.iconpack

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager

data class InstalledIconPack(
    val packageName: String,
    val label: String
)

/**
 * There's no single official "this is an icon pack" API — the convention
 * that Nova, ADW, GO Launcher, and most icon-pack apps on the Play Store
 * all follow is declaring an activity with one of a handful of
 * long-standing intent actions/categories, originally established by
 * ADW Launcher and later adopted as a de facto cross-launcher standard.
 * Checking all of them (not just one) is what makes detection actually
 * find real icon packs regardless of which launcher family they were
 * built for.
 */
object IconPackScanner {

    private val KNOWN_ICON_PACK_ACTIONS = listOf(
        "org.adw.launcher.THEMES",
        "com.novalauncher.THEME",
        "com.anddoes.launcher.THEME",
        "com.teslacoilsw.launcher.THEME",
        "com.gau.go.launcherex.theme"
    )

    fun findInstalledIconPacks(context: Context): List<InstalledIconPack> {
        val pm = context.packageManager
        val found = LinkedHashMap<String, InstalledIconPack>() // dedupe by package name

        for (action in KNOWN_ICON_PACK_ACTIONS) {
            val intent = Intent(action)
            val matches = runCatching { pm.queryIntentActivities(intent, PackageManager.MATCH_ALL) }.getOrDefault(emptyList())
            for (match in matches) {
                val pkg = match.activityInfo.packageName
                if (pkg !in found) {
                    found[pkg] = InstalledIconPack(pkg, match.loadLabel(pm).toString())
                }
            }
        }
        return found.values.toList()
    }
}
