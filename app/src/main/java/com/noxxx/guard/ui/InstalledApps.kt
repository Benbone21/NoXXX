package com.noxxx.guard.ui

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class AppEntry(val pkg: String, val label: String)

object InstalledApps {
    @Volatile
    private var cache: List<AppEntry>? = null
    private val icons = HashMap<String, ImageBitmap>()

    fun cached() = cache ?: emptyList()

    fun load(context: Context): List<AppEntry> {
        val pm = context.packageManager
        @Suppress("DEPRECATION")
        val apps = pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
            .map { AppEntry(it.activityInfo.packageName, it.loadLabel(pm).toString()) }
            .filter { it.pkg != context.packageName }
            .distinctBy { it.pkg }
            .sortedBy { it.label.lowercase() }
        cache = apps
        return apps
    }

    fun icon(context: Context, pkg: String): ImageBitmap? = synchronized(icons) {
        icons[pkg] ?: runCatching {
            context.packageManager.getApplicationIcon(pkg).toBitmap(96, 96).asImageBitmap()
        }.getOrNull()?.also { icons[pkg] = it }
    }
}

@Composable
fun rememberInstalledApps(): List<AppEntry> {
    val ctx = LocalContext.current
    val apps by produceState(InstalledApps.cached()) {
        value = withContext(Dispatchers.IO) { InstalledApps.load(ctx) }
    }
    return apps
}

@Composable
private fun AppIcon(pkg: String) {
    val ctx = LocalContext.current
    val icon by produceState<ImageBitmap?>(null, pkg) { value = withContext(Dispatchers.IO) { InstalledApps.icon(ctx, pkg) } }
    Box(Modifier.size(40.dp)) {
        icon?.let { Image(it, contentDescription = null, modifier = Modifier.size(40.dp)) }
    }
}

/**
 * Searchable checklist of launchable apps. Checked entries come first. [checkingLoosens] marks
 * lists where checking an app weakens protection (the scan-exempt list); otherwise unchecking does.
 * That direction is disabled while settings are locked. Apps in [protectedPkgs] can't be toggled at
 * all and read as "Protected" (used so essential apps can never be added to the block list).
 */
@Composable
fun AppPicker(
    checked: Set<String>,
    checkingLoosens: Boolean = false,
    protectedPkgs: Set<String> = emptySet(),
    onToggle: (String, Boolean) -> Unit,
) {
    val editable = LocalSettingsEditable.current
    val apps = rememberInstalledApps()
    var query by remember { mutableStateOf("") }
    OutlinedTextField(
        value = query,
        onValueChange = { query = it },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
        placeholder = { Text("Search apps") },
        shape = MaterialTheme.shapes.extraLarge,
    )
    if (apps.isEmpty()) Text("Loading apps…", style = MaterialTheme.typography.bodySmall)
    val shown = remember(apps, query, checked) {
        apps
            .filter { query.isBlank() || it.label.contains(query, ignoreCase = true) || it.pkg.contains(query, ignoreCase = true) }
            .sortedByDescending { it.pkg in checked }
    }
    // A lazy list in its own scrolling box: only the visible rows get built. Building a row for each of
    // 100+ apps at once froze the screen for up to ~0.8 s.
    LazyColumn(Modifier.fillMaxWidth().heightIn(max = 440.dp)) {
        items(shown, key = { it.pkg }) { app ->
            val isChecked = app.pkg in checked
            val isProtected = app.pkg in protectedPkgs
            val loosening = if (checkingLoosens) !isChecked else isChecked
            val canToggle = !isProtected && (editable || !loosening)
            ListItem(
                headlineContent = { Text(app.label) },
                supportingContent = { Text(app.pkg, style = MaterialTheme.typography.bodySmall) },
                leadingContent = { AppIcon(app.pkg) },
                trailingContent = {
                    if (isProtected) {
                        Text("Protected", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    } else {
                        androidx.compose.foundation.layout.Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            if (!canToggle) LockedGlyph()
                            Checkbox(checked = isChecked, onCheckedChange = null, enabled = canToggle)
                        }
                    }
                },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                modifier = Modifier.toggleable(value = isChecked, enabled = canToggle, role = Role.Checkbox) { onToggle(app.pkg, it) },
            )
        }
    }
}
