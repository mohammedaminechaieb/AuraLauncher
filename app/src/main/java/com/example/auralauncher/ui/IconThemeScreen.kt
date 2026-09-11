package com.auralauncher.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.auralauncher.app.data.IconShape
import com.auralauncher.app.data.LauncherRepository
import com.auralauncher.app.iconpack.IconPackManager
import com.auralauncher.app.iconpack.IconPackScanner
import com.auralauncher.app.iconpack.InstalledIconPack
import com.auralauncher.app.ui.components.AppIconView
import kotlinx.coroutines.launch

private val shapeLabels = mapOf(
    IconShape.SYSTEM_DEFAULT to "System default (each app's own adaptive icon)",
    IconShape.CIRCLE to "Circle",
    IconShape.SQUIRCLE to "Squircle",
    IconShape.ROUNDED_SQUARE to "Rounded square",
    IconShape.TEARDROP to "Teardrop"
)

/**
 * Two independent theming layers, in the order most icon-pack-aware
 * launchers present them:
 *   1. Icon pack — real third-party artwork, read from an installed
 *      pack's own appfilter.xml (see iconpack/IconPackParser.kt). Only
 *      covers apps the pack's designer specifically included icons for.
 *   2. Shape theming — the fallback for everything the pack didn't
 *      cover, or the whole-device look if no pack is selected at all.
 * Both apply live on the home grid; picking a pack doesn't require
 * "Apply to all apps" the way shape does, since the icon-pack lookup
 * happens per-app at render time rather than being baked into the DB.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IconThemeScreen(repository: LauncherRepository, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val iconPackManager = remember { IconPackManager(context) }

    var selectedShape by remember { mutableStateOf(IconShape.SYSTEM_DEFAULT) }
    var selectedPack by remember { mutableStateOf(iconPackManager.selectedPackage) }
    val installedPacks = remember { IconPackScanner.findInstalledIconPacks(context) }
    val previewApps = remember { repository.loadAllApps().take(4) }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text("Icon theme") },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) } }
        )
    }) { padding ->
        Column(Modifier.padding(padding).padding(16.dp).fillMaxSize().verticalScroll(rememberScrollState())) {
            Text("Preview", style = MaterialTheme.typography.titleSmall)
            Row(Modifier.padding(vertical = 12.dp)) {
                previewApps.forEach { app ->
                    Box(Modifier.padding(end = 12.dp)) {
                        val packIcon = remember(selectedPack, app.packageName) { iconPackManager.resolveIcon(app) }
                        if (packIcon != null) {
                            AppIconView(icon = packIcon, shape = selectedShape, sizeDp = 48, isPreShaped = true)
                        } else {
                            AppIconView(icon = app.icon, shape = selectedShape, sizeDp = 48)
                        }
                    }
                }
            }

            Divider()
            Text("Icon pack", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(vertical = 8.dp))

            if (installedPacks.isEmpty()) {
                Text(
                    "No icon pack apps detected on this device. Install one from the Play Store " +
                        "(e.g. search \"icon pack\") and it'll show up here automatically.",
                    style = MaterialTheme.typography.bodySmall
                )
            } else {
                Row(
                    Modifier.fillMaxWidth().selectable(selected = selectedPack == null) {
                        selectedPack = null
                        iconPackManager.selectedPackage = null
                    }.padding(vertical = 8.dp)
                ) {
                    RadioButton(selected = selectedPack == null, onClick = {
                        selectedPack = null
                        iconPackManager.selectedPackage = null
                    })
                    Spacer(Modifier.width(12.dp))
                    Text("None — use shape theming only")
                }

                installedPacks.forEach { pack: InstalledIconPack ->
                    Row(
                        Modifier.fillMaxWidth().selectable(selected = selectedPack == pack.packageName) {
                            selectedPack = pack.packageName
                            iconPackManager.selectedPackage = pack.packageName
                        }.padding(vertical = 8.dp)
                    ) {
                        RadioButton(selected = selectedPack == pack.packageName, onClick = {
                            selectedPack = pack.packageName
                            iconPackManager.selectedPackage = pack.packageName
                        })
                        Spacer(Modifier.width(12.dp))
                        Text(pack.label)
                    }
                }

                Text(
                    "Only apps the pack's designer specifically included icons for will use its artwork " +
                        "— everything else falls back to shape theming below.",
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Divider(Modifier.padding(top = 16.dp))
            Text("Shape (fallback / whole-device if no pack selected)", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(vertical = 8.dp))

            shapeLabels.forEach { (shape, label) ->
                Row(
                    Modifier.fillMaxWidth().selectable(selected = selectedShape == shape) {
                        selectedShape = shape
                    }.padding(vertical = 12.dp)
                ) {
                    RadioButton(selected = selectedShape == shape, onClick = { selectedShape = shape })
                    Spacer(Modifier.width(12.dp))
                    Text(label)
                }
            }

            Spacer(Modifier.height(16.dp))

            Button(
                onClick = {
                    scope.launch {
                        repository.loadAllApps().forEach { app ->
                            repository.setIconOverride(app.packageName, selectedShape)
                        }
                    }
                    onBack()
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Apply shape to all apps") }

            Spacer(Modifier.height(8.dp))
        }
    }
}
