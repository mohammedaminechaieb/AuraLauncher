package com.auralauncher.app.prefs

import android.content.Context

/** Plain SharedPreferences (per-package int counters) — same reasoning
 *  as the other small-scalar managers in this app: doesn't need Room. */
class AppUsageTracker(context: Context) {
    private val prefs = context.getSharedPreferences("auralauncher_usage_prefs", Context.MODE_PRIVATE)

    fun recordLaunch(packageName: String) {
        val current = prefs.getInt(packageName, 0)
        prefs.edit().putInt(packageName, current + 1).apply()
    }

    fun launchCount(packageName: String): Int = prefs.getInt(packageName, 0)
}
