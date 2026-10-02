package com.auralauncher.app

import android.app.Activity
import android.app.role.RoleManager
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.text.TextUtils
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.lifecycle.lifecycleScope
import com.auralauncher.app.data.AppListStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.auralauncher.app.data.HostedWidgetEntity
import com.auralauncher.app.data.LauncherRepository
import com.auralauncher.app.settings.LauncherSettingsManager
import com.auralauncher.app.ui.AppDrawerScreen
import com.auralauncher.app.ui.FocusModeScreen
import com.auralauncher.app.ui.HiddenAppsScreen
import com.auralauncher.app.ui.HomeScreen
import com.auralauncher.app.ui.IconThemeScreen
import com.auralauncher.app.ui.SettingsScreen
import com.auralauncher.app.widgethost.AuraWidgetHost
import kotlinx.coroutines.launch
import org.json.JSONObject

private enum class Screen { HOME, DRAWER, ICON_THEME, FOCUS_MODES, HIDDEN_APPS, SETTINGS }

/** Where Back goes from each screen (HOME has nowhere to go — a launcher's root). */
private fun Screen.parent(hiddenAppsFrom: Screen): Screen? = when (this) {
    Screen.HOME -> null
    Screen.DRAWER, Screen.SETTINGS -> Screen.HOME
    Screen.ICON_THEME, Screen.FOCUS_MODES -> Screen.SETTINGS
    Screen.HIDDEN_APPS -> hiddenAppsFrom
}

/** What cell a widget-in-progress should land on once the pick/configure
 *  flow finishes — captured right before launching the system picker. */
private data class PendingWidgetPlacement(val page: Int, val row: Int, val col: Int)

class MainActivity : ComponentActivity() {

    private lateinit var widgetHost: AuraWidgetHost
    private lateinit var settings: LauncherSettingsManager

    // Hoisted out of Compose so onNewIntent (Home button) can reset it.
    private var screen by mutableStateOf(Screen.HOME)
    private var hiddenAppsFrom by mutableStateOf(Screen.DRAWER)
    private var drawerFocusSearch by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
        super.onCreate(savedInstanceState)
        val repository = LauncherRepository(applicationContext)
        widgetHost = AuraWidgetHost(applicationContext)
        settings = LauncherSettingsManager(applicationContext)
        AppListStore.get(applicationContext) // start loading the app list right away
        val scope = lifecycleScope

        setContent {
            val colorScheme = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                dynamicDarkColorScheme(this)
            } else {
                androidx.compose.material3.darkColorScheme()
            }
            MaterialTheme(colorScheme = colorScheme) {
                // Transparent so the home screen shows the real wallpaper the
                // window draws behind it; other screens paint their own background.
                Surface(modifier = Modifier.fillMaxSize(), color = androidx.compose.ui.graphics.Color.Transparent) {
                    val parent = screen.parent(hiddenAppsFrom)
                    BackHandler(enabled = true) { parent?.let { screen = it } }
                    var isDefaultLauncher by remember { mutableStateOf(isDefaultLauncher()) }
                    var hasNotificationAccess by remember { mutableStateOf(hasNotificationAccess()) }
                    var pendingPlacement by remember { mutableStateOf<PendingWidgetPlacement?>(null) }
                    var pendingAppWidgetId by remember { mutableStateOf<Int?>(null) }
                    var backupStatus by remember { mutableStateOf<String?>(null) }

                    // Applies/removes immersive status-bar hiding whenever the
                    // setting changes — re-checked each time Settings could
                    // plausibly have changed it (screen switches back to HOME).
                    LaunchedEffect(screen) {
                        applyStatusBarVisibility(settings.hideStatusBar)
                    }

                    val roleLauncher = rememberLauncherForActivityResult(
                        ActivityResultContracts.StartActivityForResult()
                    ) { isDefaultLauncher = isDefaultLauncher() }

                    val notificationAccessLauncher = rememberLauncherForActivityResult(
                        ActivityResultContracts.StartActivityForResult()
                    ) { hasNotificationAccess = hasNotificationAccess() }

                    val configureLauncher = rememberLauncherForActivityResult(
                        ActivityResultContracts.StartActivityForResult()
                    ) { result ->
                        val appWidgetId = pendingAppWidgetId
                        val placement = pendingPlacement
                        if (result.resultCode == Activity.RESULT_OK && appWidgetId != null && placement != null) {
                            scope.launch {
                                repository.saveHostedWidget(
                                    HostedWidgetEntity(appWidgetId, placement.page, placement.row, placement.col, 2, 2)
                                )
                            }
                        } else if (appWidgetId != null) {
                            widgetHost.deleteAppWidgetId(appWidgetId)
                        }
                        pendingAppWidgetId = null
                        pendingPlacement = null
                    }

                    val pickLauncher = rememberLauncherForActivityResult(
                        ActivityResultContracts.StartActivityForResult()
                    ) { result ->
                        val appWidgetId = result.data?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1) ?: -1

                        if (result.resultCode != Activity.RESULT_OK || appWidgetId == -1) {
                            pendingAppWidgetId?.let { widgetHost.deleteAppWidgetId(it) }
                            pendingAppWidgetId = null
                            pendingPlacement = null
                            return@rememberLauncherForActivityResult
                        }

                        val info = widgetHost.getAppWidgetInfo(appWidgetId)
                        val configureIntent = info?.let { widgetHost.buildConfigureIntent(appWidgetId, it) }
                        pendingAppWidgetId = appWidgetId

                        if (configureIntent != null) {
                            configureLauncher.launch(configureIntent)
                        } else {
                            val placement = pendingPlacement
                            if (placement != null) {
                                scope.launch {
                                    repository.saveHostedWidget(
                                        HostedWidgetEntity(appWidgetId, placement.page, placement.row, placement.col, 2, 2)
                                    )
                                }
                            }
                            pendingAppWidgetId = null
                            pendingPlacement = null
                        }
                    }

                    fun startAddWidgetFlow(page: Int, row: Int, col: Int) {
                        val newId = widgetHost.allocateAppWidgetId()
                        pendingPlacement = PendingWidgetPlacement(page, row, col)
                        pendingAppWidgetId = newId
                        pickLauncher.launch(widgetHost.buildPickIntent(newId))
                    }

                    // Backup export: system "Save As" dialog, same technique HaptiKit uses.
                    val exportLauncher = rememberLauncherForActivityResult(
                        ActivityResultContracts.CreateDocument("application/json")
                    ) { uri: Uri? ->
                        if (uri == null) return@rememberLauncherForActivityResult
                        scope.launch {
                            val json = repository.exportBackup(settings.toJson())
                            contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray()) }
                            backupStatus = "Backup exported."
                        }
                    }

                    // Backup import: system file picker.
                    val importLauncher = rememberLauncherForActivityResult(
                        ActivityResultContracts.OpenDocument()
                    ) { uri: Uri? ->
                        if (uri == null) return@rememberLauncherForActivityResult
                        scope.launch {
                            val json = contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                            if (json != null) {
                                val settingsJson = repository.importBackup(json)
                                settings.applyFromJson(settingsJson)
                                backupStatus = "Backup restored. Widgets need to be re-added manually (see note)."
                            } else {
                                backupStatus = "Couldn't read that file."
                            }
                        }
                    }

                    when (screen) {
                        Screen.HOME -> HomeScreen(
                            repository = repository,
                            widgetHost = widgetHost,
                            isDefaultLauncher = isDefaultLauncher,
                            onRequestDefaultLauncher = { requestDefaultLauncher(roleLauncher) },
                            onAddWidgetRequested = { page, row, col -> startAddWidgetFlow(page, row, col) },
                            onOpenDrawer = { focus -> drawerFocusSearch = focus; screen = Screen.DRAWER },
                            onOpenSettings = { screen = Screen.SETTINGS }
                        )
                        Screen.DRAWER -> AppDrawerScreen(
                            repository = repository,
                            focusSearch = drawerFocusSearch,
                            onOpenHiddenApps = { hiddenAppsFrom = Screen.DRAWER; screen = Screen.HIDDEN_APPS },
                            onBack = { screen = Screen.HOME }
                        )
                        Screen.HIDDEN_APPS -> HiddenAppsScreen(repository = repository, onBack = { screen = hiddenAppsFrom })
                        Screen.ICON_THEME -> IconThemeScreen(repository = repository, onBack = { screen = Screen.SETTINGS })
                        Screen.FOCUS_MODES -> FocusModeScreen(repository = repository, onBack = { screen = Screen.SETTINGS })
                        Screen.SETTINGS -> SettingsScreen(
                            isDefaultLauncher = isDefaultLauncher,
                            hasNotificationAccess = hasNotificationAccess,
                            backupStatus = backupStatus,
                            onRequestDefaultLauncher = { requestDefaultLauncher(roleLauncher) },
                            onRequestNotificationAccess = { notificationAccessLauncher.launch(Intent(android.provider.Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) },
                            onOpenIconTheme = { screen = Screen.ICON_THEME },
                            onOpenFocusModes = { screen = Screen.FOCUS_MODES },
                            onOpenHiddenApps = { hiddenAppsFrom = Screen.SETTINGS; screen = Screen.HIDDEN_APPS },
                            onExportBackup = { exportLauncher.launch("auralauncher_backup.json") },
                            onImportBackup = { importLauncher.launch(arrayOf("application/json")) },
                            onBack = { screen = Screen.HOME }
                        )
                    }
                }
            }
        }
    }

    /** Pressing Home while AuraLauncher is already the foreground launcher
     *  delivers a new HOME intent here — go back to the home screen, like
     *  every launcher does (the old version stayed stuck in the drawer). */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.action == Intent.ACTION_MAIN && intent.hasCategory(Intent.CATEGORY_HOME)) {
            screen = Screen.HOME
        }
    }

    override fun onResume() {
        super.onResume()
        widgetHost.startListening()
    }

    override fun onPause() {
        widgetHost.stopListening()
        super.onPause()
    }

    private fun applyStatusBarVisibility(hide: Boolean) {
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        if (hide) {
            controller.hide(WindowInsetsCompat.Type.statusBars())
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        } else {
            controller.show(WindowInsetsCompat.Type.statusBars())
        }
    }

    private fun isDefaultLauncher(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = getSystemService(RoleManager::class.java)
            if (roleManager?.isRoleAvailable(RoleManager.ROLE_HOME) == true) {
                return roleManager.isRoleHeld(RoleManager.ROLE_HOME)
            }
        }
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val resolved = packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
        return resolved?.activityInfo?.packageName == packageName
    }

    private fun requestDefaultLauncher(launcher: androidx.activity.result.ActivityResultLauncher<Intent>) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = getSystemService(RoleManager::class.java)
            if (roleManager?.isRoleAvailable(RoleManager.ROLE_HOME) == true) {
                launcher.launch(roleManager.createRequestRoleIntent(RoleManager.ROLE_HOME))
                return
            }
        }
        launcher.launch(Intent(android.provider.Settings.ACTION_HOME_SETTINGS))
    }

    private fun hasNotificationAccess(): Boolean {
        val expected = "$packageName/.notifications.AuraNotificationListenerService"
        val enabled = android.provider.Settings.Secure.getString(contentResolver, "enabled_notification_listeners") ?: return false
        val splitter = TextUtils.SimpleStringSplitter(':')
        splitter.setString(enabled)
        while (splitter.hasNext()) {
            if (splitter.next().equals(expected, ignoreCase = true)) return true
        }
        return false
    }
}
