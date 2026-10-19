package com.noxxx.guard.ui

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import java.io.File
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AppBlocking
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.ShieldMoon
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.noxxx.guard.NoXXXApp
import com.noxxx.guard.data.BlockKind
import com.noxxx.guard.policy.GuardState
import com.noxxx.guard.policy.formatMinutes
import java.text.DateFormat
import java.util.Date

@Composable
fun guardState(): GuardState = NoXXXApp.instance.store.state.collectAsStateWithLifecycle().value

fun LazyListScope.homeScreen(actions: PolicyActions, refresh: Int, openGuide: () -> Unit) {
    item { HeroCard(refresh, openGuide) }
    item { TamperAlertCard() }
    item { LockStatusCard(actions) }
    statsSection()
    item { SetupSummary(refresh, openGuide) }
    item { RecentBlocks() }
}

@Composable
private fun TamperAlertCard() {
    val state = guardState()
    val events = state.tamperEvents
    if (events.isEmpty()) return
    val fmt = remember { DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT) }
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
        ),
    ) {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Filled.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
            Text(
                if (events.size == 1) "Protection was turned off" else "Protection was turned off ${events.size} times",
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                "Last: ${fmt.format(Date(events.first().timeMs))}",
                style = MaterialTheme.typography.bodyMedium,
            )
            if (state.tamperCount > events.size) {
                Text(
                    "${state.tamperCount} total since protection started",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            if (events.size > 1) {
                TextButton(onClick = { expanded = !expanded }) {
                    Text(if (expanded) "Hide details" else "Show all ${events.size} events")
                }
                if (expanded) {
                    events.forEach { e ->
                        Text(
                            "• ${fmt.format(Date(e.timeMs))} — ${e.reason}",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
            TextButton(onClick = { NoXXXApp.instance.store.clearTamperEvents() }) { Text("Dismiss") }
        }
    }
}

@Composable
private fun HeroCard(refresh: Int, openGuide: () -> Unit) {
    val state = guardState()
    val status = rememberSetupStatus(refresh)
    var confirm by remember { mutableStateOf(false) }
    val p = state.policy
    val healthy = status.accessibility && (!p.requirePrivateDns || status.dns.filtering || !status.dns.online)

    val container = when {
        !state.committed -> MaterialTheme.colorScheme.secondaryContainer
        healthy -> MaterialTheme.colorScheme.primaryContainer
        else -> MaterialTheme.colorScheme.errorContainer
    }
    val onContainer = when {
        !state.committed -> MaterialTheme.colorScheme.onSecondaryContainer
        healthy -> MaterialTheme.colorScheme.onPrimaryContainer
        else -> MaterialTheme.colorScheme.onErrorContainer
    }

    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = container, contentColor = onContainer),
    ) {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            IconBadge(
                if (state.committed) Icons.Filled.Shield else Icons.Filled.ShieldMoon,
                size = 64.dp,
                container = onContainer.copy(alpha = 0.12f),
                tint = onContainer,
            )
            Text(
                when {
                    !state.committed -> "Setup mode"
                    healthy -> "Protection is on"
                    else -> "Protection needs attention"
                },
                style = MaterialTheme.typography.headlineSmall,
            )
            Text(
                when {
                    !state.committed -> "Changes apply instantly. When you start protection, settings lock and " +
                        "loosening anything needs an unlock after a ${formatMinutes(p.coolOffMinutes)} cool-off."
                    healthy -> "Everything is running. Your stats are below."
                    else -> "Something required is switched off. Fix it under Setup below."
                },
                style = MaterialTheme.typography.bodyMedium,
            )
            if (!state.committed) {
                val dnsReady = !p.requirePrivateDns || status.dns.filtering
                if (!status.accessibility) Text("• Turn on the guard service first", style = MaterialTheme.typography.bodyMedium)
                if (!dnsReady) Text("• Set Private DNS to a family filter", style = MaterialTheme.typography.bodyMedium)
                if (status.accessibility && dnsReady) {
                    Button(onClick = { confirm = true }) { Text("Start protection") }
                } else {
                    Button(onClick = openGuide) { Text("Finish setup") }
                }
            }
        }
    }

    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            icon = { Icon(Icons.Filled.Shield, contentDescription = null) },
            title = { Text("Start protection?") },
            text = {
                Text("Settings will lock. Home and your stats stay open, and you can always add protection. To loosen " +
                    "anything or uninstall, you'll request an unlock and wait ${formatMinutes(p.coolOffMinutes)}.")
            },
            confirmButton = { TextButton(onClick = { NoXXXApp.instance.store.commit(); confirm = false }) { Text("Start") } },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("Not yet") } },
        )
    }
}

@Composable
private fun SetupSummary(refresh: Int, openGuide: () -> Unit) {
    val status = rememberSetupStatus(refresh)
    val state = guardState()
    val p = state.policy
    SectionCard("Setup", icon = Icons.Outlined.Checklist) {
        // Each of these shows a fix button only while it's off, since the guide is gone once protection starts.
        AccessibilitySetup(status)
        if (p.requirePrivateDns) {
            StatusLine(status.dns.filtering || !status.dns.online,
                if (status.dns.filtering) "Private DNS: ${status.dns.host}" else "Private DNS family filter")
        }
        StatusLine(p.urlFilter, "Website list: %,d domains".format(NoXXXApp.instance.blocklist.domainCount))
        StatusLine(p.builtInKeywords, "Blocked words: %,d built in + %d of yours".format(NoXXXApp.instance.keywords.bundledCount, p.customKeywords.size))
        StatusLine(p.imageScan && status.imageSupported,
            "Image detection" + if (!status.imageSupported) " (needs Android 11+)" else "")
        AdminSetup(status)
        BatterySetup(status, state.committed)
        if (!state.committed) FilledTonalButton(onClick = openGuide) { Text("Open setup guide") }
    }
}

@Composable
private fun RecentBlocks() {
    val log = NoXXXApp.instance.blockLog
    val events = log.events.collectAsStateWithLifecycle().value
    val fmt = remember { DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT) }
    val ctx = LocalContext.current
    SectionCard("Recent blocks", if (events.isEmpty()) "Nothing blocked yet." else null, icon = Icons.Filled.History) {
        events.take(25).forEach { e ->
            ListItem(
                headlineContent = { Text(kindLabel(e.kind)) },
                supportingContent = {
                    Column {
                        Text("${e.detail} · ${fmt.format(Date(e.timeMs))}")
                        if (e.reason.isNotEmpty()) {
                            Text(e.reason, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                },
                leadingContent = { Icon(kindIcon(e.kind), contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            )
        }
        Hint("Something blocked that shouldn't be? Export diagnostics and share the file. It has the scores and " +
            "rules behind each block, including blocked words and addresses, so only send it to someone you trust.")
        Row {
            TextButton(onClick = { exportDiagnostics(ctx) }) { Text("Export diagnostics") }
            if (events.isNotEmpty()) TextButton(onClick = { log.clear() }) { Text("Clear history") }
        }
    }
}

fun kindLabel(k: BlockKind) = when (k) {
    BlockKind.WEBSITE -> "Website"
    BlockKind.SEARCH -> "Search"
    BlockKind.APP -> "App"
    BlockKind.IMAGE -> "Image"
    BlockKind.PRIVATE_BROWSING -> "Private tab"
    BlockKind.BROWSER -> "Unsupported browser"
    BlockKind.DNS_OFF -> "Filter was off"
    BlockKind.SETTINGS -> "Protected setting"
    BlockKind.GUARD_DISABLED -> "Guard turned off"
}

fun kindIcon(k: BlockKind): ImageVector = when (k) {
    BlockKind.WEBSITE -> Icons.Filled.Language
    BlockKind.SEARCH -> Icons.Filled.SearchOff
    BlockKind.APP -> Icons.Filled.AppBlocking
    BlockKind.IMAGE -> Icons.Filled.VisibilityOff
    BlockKind.PRIVATE_BROWSING -> Icons.Filled.PrivacyTip
    BlockKind.BROWSER -> Icons.Filled.Block
    BlockKind.DNS_OFF -> Icons.Filled.WifiOff
    BlockKind.SETTINGS -> Icons.Filled.Settings
    BlockKind.GUARD_DISABLED -> Icons.Filled.Warning
}

/** Copies the diagnostics log to a shareable file and opens the share sheet. */
private fun exportDiagnostics(context: Context) {
    val dir = File(context.cacheDir, "exports").apply { mkdirs() }
    val file = NoXXXApp.instance.diagnostics.export(File(dir, "noxxx-diagnostics.txt"))
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
    val send = Intent(Intent.ACTION_SEND)
        .setType("text/plain")
        .putExtra(Intent.EXTRA_STREAM, uri)
        .putExtra(Intent.EXTRA_SUBJECT, "NoXXX diagnostics")
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    context.startActivity(Intent.createChooser(send, "Share diagnostics"))
}
