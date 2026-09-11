package com.auralauncher.app.util

import android.app.StatusBarManager
import android.content.Context
import android.util.Log

/**
 * There is no public, stable API for a launcher to open the notification
 * shade — StatusBarManager.expandNotificationsPanel() exists but is marked
 * @hide, reachable only via reflection. This works on stock AOSP-based
 * ROMs (and most Pixel/OnePlus/etc. builds) but is routinely blocked by
 * heavily customized OEM skins (some Xiaomi/Samsung builds in particular)
 * since it's not part of the guaranteed public API surface. Fails silently
 * if unavailable — swipe-down just does nothing rather than crashing.
 */
object NotificationShadeOpener {
    private const val TAG = "NotificationShadeOpener"

    fun tryExpand(context: Context) {
        runCatching {
            val statusBarService = context.getSystemService(Context.STATUS_BAR_SERVICE)
            val method = statusBarService?.javaClass?.getMethod("expandNotificationsPanel")
            method?.invoke(statusBarService)
        }.onFailure {
            Log.w(TAG, "Could not expand notification shade via reflection on this OEM build: ${it.message}")
        }
    }
}
