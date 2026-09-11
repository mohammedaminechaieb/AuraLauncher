package com.auralauncher.app.widgethost

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.content.Intent

/**
 * Thin wrapper so the rest of the app never touches AppWidgetHost/
 * AppWidgetManager directly. This is the actual mechanism that lets
 * AuraLauncher embed OTHER apps' real home-screen widgets — the same
 * system API every real launcher (Nova, Smart Launcher, stock Pixel
 * Launcher) uses. There's no special permission needed beyond what's
 * already implicit in being a launcher; the system's own widget-picker
 * Activity (ACTION_APPWIDGET_PICK) handles the bind confirmation.
 *
 * HOST_ID just needs to be a stable constant unique to this app — it's
 * how Android tells "AuraLauncher's hosted widgets" apart from any other
 * app on the device that also happens to host widgets.
 */
class AuraWidgetHost(context: Context) {
    private val appContext = context.applicationContext
    val appWidgetManager: AppWidgetManager = AppWidgetManager.getInstance(appContext)
    val appWidgetHost: AppWidgetHost = AppWidgetHost(appContext, HOST_ID)

    fun startListening() = appWidgetHost.startListening()
    fun stopListening() = appWidgetHost.stopListening()

    fun allocateAppWidgetId(): Int = appWidgetHost.allocateAppWidgetId()

    fun deleteAppWidgetId(appWidgetId: Int) {
        runCatching { appWidgetHost.deleteAppWidgetId(appWidgetId) }
    }

    fun getAppWidgetInfo(appWidgetId: Int): AppWidgetProviderInfo? =
        appWidgetManager.getAppWidgetInfo(appWidgetId)

    /** Builds the intent that launches Android's own system widget-picker UI —
     *  shows every widget from every installed app, exactly like long-pressing
     *  the home screen on a stock launcher does. */
    fun buildPickIntent(appWidgetId: Int): Intent {
        return Intent(AppWidgetManager.ACTION_APPWIDGET_PICK).apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        }
    }

    /** Some widgets (most calendar/weather widgets) require a one-time
     *  configuration step right after being picked — e.g. "which calendar?"
     *  This builds that intent; caller only needs it if
     *  AppWidgetProviderInfo.configure is non-null. */
    fun buildConfigureIntent(appWidgetId: Int, info: AppWidgetProviderInfo): Intent? {
        if (info.configure == null) return null
        return Intent(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE).apply {
            component = info.configure
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        }
    }

    /** Creates the actual embeddable View for a bound+configured widget.
     *  This IS the other app's real widget UI, rendered live — not a
     *  static preview. */
    fun createView(context: Context, appWidgetId: Int, info: AppWidgetProviderInfo): AppWidgetHostView {
        return appWidgetHost.createView(context, appWidgetId, info)
    }

    companion object {
        private const val HOST_ID = 4224 // arbitrary but must stay stable across app versions
    }
}
