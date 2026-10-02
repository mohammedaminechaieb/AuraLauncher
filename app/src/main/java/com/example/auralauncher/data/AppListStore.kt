package com.auralauncher.app.data

import android.content.Context
import android.content.pm.LauncherApps
import android.os.Handler
import android.os.Looper
import android.os.UserHandle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * The one shared, live list of launchable apps.
 *
 * A launcher process stays alive for days, so loading the list once (the
 * old `remember { loadAllApps() }` in every screen) meant newly installed
 * apps never appeared and uninstalled ones lingered until a restart. This
 * listens to LauncherApps package callbacks and reloads off the main thread
 * (loading every icon synchronously also janked opening the drawer).
 *
 * [apps] is null until the first load finishes, so callers can tell
 * "still loading" apart from "no apps".
 */
class AppListStore private constructor(context: Context) {

    private val appContext = context.applicationContext
    private val repository = LauncherRepository(appContext)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var loadJob: Job? = null

    private val _apps = MutableStateFlow<List<AppInfo>?>(null)
    val apps: StateFlow<List<AppInfo>?> = _apps.asStateFlow()

    private val callback = object : LauncherApps.Callback() {
        override fun onPackageRemoved(packageName: String, user: UserHandle) {
            // Free the cells it occupied — otherwise an invisible ghost
            // keeps that spot "occupied" forever.
            scope.launch { repository.forgetPackage(packageName) }
            reload()
        }
        override fun onPackageAdded(packageName: String, user: UserHandle) = reload()
        override fun onPackageChanged(packageName: String, user: UserHandle) = reload()
        override fun onPackagesAvailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) = reload()
        override fun onPackagesUnavailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) = reload()
    }

    init {
        appContext.getSystemService(LauncherApps::class.java)?.registerCallback(callback, Handler(Looper.getMainLooper()))
        reload()
    }

    fun reload() {
        loadJob?.cancel()
        loadJob = scope.launch {
            val apps = repository.loadAllApps()
            apps.forEach { com.auralauncher.app.ui.components.IconBitmapCache.prewarm(it.icon) }
            _apps.value = apps
        }
    }

    companion object {
        @Volatile private var instance: AppListStore? = null
        fun get(context: Context): AppListStore =
            instance ?: synchronized(this) { instance ?: AppListStore(context).also { instance = it } }
    }
}
