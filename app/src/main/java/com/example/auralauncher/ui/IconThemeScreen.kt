package com.auralauncher.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.auralauncher.app.data.AppListStore
import com.auralauncher.app.data.IconShape
import com.auralauncher.app.data.LauncherRepository
import com.auralauncher.app.iconpack.IconPackManager
import com.auralauncher.app.iconpack.IconPackScanner
import com.auralauncher.app.settings.LauncherSettingsManager
import com.auralauncher.app.ui.components.AppIconView
import kotlinx.coroutines.launch

private val shapeLabels = linkedMapOf(
    IconShape.SYSTEM_DEFAULT to "System default",
    IconShape.CIRCLE to "Circle",
    IconShape.SQUIRCLE to "Squircle",
    IconShape.ROUNDED_SQUARE to "Rounded square",
    IconShape.TEARDROP to "Teardrop"
)

/**
 * Two theming layers: an icon pack (real third-party artwork from the
 * pack's appfilter.xml), and a shape that applies to every icon the pack
 * doesn't cover. Both apply instantly — no "Apply" step.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun IconThemeScreen(repository: LauncherRepository, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val iconPackManager = remember { IconPackManager(context) }
    val settings = remember { LauncherSettingsManager(context) }

    var selectedShape by remember { mutableStateOf(settings.iconShape) }
    var selectedPack by remember { mutableStateOf(iconPackManager.selectedPackage) }
    val installedPacks = remember { IconPackScanner.findInstalledIconPacks(context) }
    val apps by AppListStore.get(context).apps.collectAsState()
    val previewApps = remember(apps) { apps.orEmpty().take(5) }
    val overrides by repository.observeIconOverrides().collectAsState(initial = emptyList())

    Scaffold(topBar = {
        TopAppBar(
            title = { Text("Icon theme") },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } }
        )
    }) { padding ->
        Column(Modifier.padding(padding).padding(16.dp).fillMaxSize().verticalScroll(rememberScrollState())) {
            Card {
                Row(Modifier.fillMaxWidth().padding(vertical = 20.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                    previewApps.forEach { app ->
                        val packIcon = remember(selectedPack, app.packageName) { iconPackManager.resolveIcon(app) }
                        if (packIcon != null) AppIconView(icon = packIcon, shape = selectedShape, sizeDp = 48, isPreShaped = true)
                        else AppIconView(icon = app.icon, shape = selectedShape, sizeDp = 48)
                    }
                }
            }

            Text("Shape", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 20.dp, bottom = 8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                shapeLabels.forEach { (shape, label) ->
                    FilterChip(
                        selected = selectedShape == shape,
                        onClick = { selectedShape = shape; settings.iconShape = shape },
                        label = { Text(label) }
                    )
                }
            }
            if (overrides.isNotEmpty()) {
                TextButton(onClick = { scope.launch { repository.clearAllIconOverrides() } }) {
                    Text("Reset ${overrides.size} app-specific shape${if (overrides.size == 1) "" else "s"}")
                }
            }

            Text("Icon pack", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 20.dp, bottom = 8.dp))
            if (installedPacks.isEmpty()) {
                Text(
                    "No icon packs installed. Install one from the Play Store (search \"icon pack\") and it'll appear here automatically.",
                    style = MaterialTheme.typography.bodySmall
                )
            } else {
                (listOf<Pair<String?, String>>(null to "None — use shapes only") + installedPacks.map { it.packageName to it.label }).forEach { (pkg, label) ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .selectable(selected = selectedPack == pkg) { selectedPack = pkg; iconPackManager.selectedPackage = pkg }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = selectedPack == pkg, onClick = null)
                        Spacer(Modifier.width(12.dp))
                        Text(label)
                    }
                }
                Text(
                    "Apps the pack doesn't include an icon for use the shape above.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}
