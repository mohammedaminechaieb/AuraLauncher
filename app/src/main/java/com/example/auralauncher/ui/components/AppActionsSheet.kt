package com.auralauncher.app.ui.components

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.auralauncher.app.data.AppInfo
import com.auralauncher.app.data.IconShape
import com.auralauncher.app.shortcuts.AppShortcutsHelper

/**
 * What long-pressing an app shows, everywhere (home grid, dock, drawer):
 * the app's own shortcuts first (e.g. "New message"), then placement
 * actions, then App info / Uninstall.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppActionsSheet(
    app: AppInfo,
    shape: IconShape,
    onDismiss: () -> Unit,
    onAddToHome: (() -> Unit)? = null,
    onRemoveFromHome: (() -> Unit)? = null,
    onAddToDock: (() -> Unit)? = null,
    onRemoveFromDock: (() -> Unit)? = null,
    onHide: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val shortcutsHelper = remember { AppShortcutsHelper(context) }
    val shortcuts = remember(app.packageName) { shortcutsHelper.shortcutsFor(app.packageName, maxCount = 4) }
    val isSystemApp = remember(app.packageName) {
        runCatching {
            context.packageManager.getApplicationInfo(app.packageName, 0).flags and ApplicationInfo.FLAG_SYSTEM != 0
        }.getOrDefault(true)
    }

    fun act(block: () -> Unit) { block(); onDismiss() }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(bottom = 24.dp)) {
            Row(Modifier.padding(horizontal = 24.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                AppIconView(icon = app.icon, shape = shape, sizeDp = 44)
                Spacer(Modifier.width(16.dp))
                Text(app.label, style = MaterialTheme.typography.titleLarge)
            }

            if (shortcuts.isNotEmpty()) {
                shortcuts.forEach { shortcut ->
                    ListItem(
                        leadingContent = {
                            val bmp = remember(shortcut.id) { shortcut.icon?.toBitmap(72, 72)?.asImageBitmap() }
                            if (bmp != null) Image(bmp, null, Modifier.size(28.dp)) else Icon(Icons.Default.Bolt, null)
                        },
                        headlineContent = { Text(shortcut.label) },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        modifier = Modifier.clickable { act { shortcutsHelper.launch(shortcut) } }
                    )
                }
                HorizontalDivider(Modifier.padding(vertical = 4.dp, horizontal = 24.dp))
            }

            onAddToHome?.let { Action(Icons.Default.AddToHomeScreen, "Add to home screen") { act(it) } }
            onRemoveFromHome?.let { Action(Icons.Default.Close, "Remove from home screen") { act(it) } }
            onAddToDock?.let { Action(Icons.Default.Dock, "Add to dock") { act(it) } }
            onRemoveFromDock?.let { Action(Icons.Default.Close, "Remove from dock") { act(it) } }
            onHide?.let { Action(Icons.Default.VisibilityOff, "Hide from app drawer") { act(it) } }
            Action(Icons.Outlined.Info, "App info") {
                act {
                    runCatching {
                        context.startActivity(
                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${app.packageName}"))
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    }
                }
            }
            if (!isSystemApp) {
                Action(Icons.Default.Delete, "Uninstall") {
                    act {
                        runCatching {
                            context.startActivity(
                                Intent(Intent.ACTION_DELETE, Uri.parse("package:${app.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Action(icon: ImageVector, label: String, onClick: () -> Unit) {
    ListItem(
        leadingContent = { Icon(icon, null) },
        headlineContent = { Text(label) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.clickable(onClick = onClick)
    )
}
