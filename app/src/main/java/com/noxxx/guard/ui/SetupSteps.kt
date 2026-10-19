package com.noxxx.guard.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.noxxx.guard.NoXXXApp
import com.noxxx.guard.web.DnsCanary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.noxxx.guard.admin.GuardAdminReceiver
import com.noxxx.guard.monitor.GuardAccessibilityService
import com.noxxx.guard.monitor.TamperGuard
import com.noxxx.guard.policy.PrivateDnsProviders
import com.noxxx.guard.web.PrivateDns

/** Live status of everything setup needs. Cheap enough to rebuild every second. */
class SetupStatus(context: Context) {
    val accessibility = GuardAccessibilityService.isEnabled(context)
    val dns = PrivateDns.status(context)
    val canEnforceDns = PrivateDns.canEnforce(context)
    val admin = GuardAdminReceiver.isActive(context)
    val battery = context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName)
    val imageSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
}

@Composable
fun rememberSetupStatus(refresh: Int): SetupStatus {
    val ctx = LocalContext.current
    return remember(refresh) { SetupStatus(ctx) }
}

@Composable
private fun activity() = LocalContext.current as MainActivity

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AccessibilitySetup(status: SetupStatus) {
    val ctx = LocalContext.current
    val activity = activity()
    StatusLine(status.accessibility, if (status.accessibility) "The guard service is on" else "The guard service is off")
    if (status.accessibility) return
    Hint("Open Accessibility, find NoXXX (often under \"Downloaded apps\" or \"Installed apps\"), and switch it on.")
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = { activity.openSetupScreen(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS), TamperGuard.Grace.FULL) }) {
            Text("Open Accessibility")
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            OutlinedButton(onClick = {
                activity.openSetupScreen(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}")),
                    TamperGuard.Grace.FULL,
                )
            }) { Text("App info") }
        }
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        TipCard(
            "Switch greyed out?",
            "Android limits apps installed from outside the Play Store. Open App info, tap ⋮ in the top corner, " +
                "choose \"Allow restricted settings\", then try again.",
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PrivateDnsSetup(status: SetupStatus, actions: PolicyActions, showProviders: Boolean) {
    val ctx = LocalContext.current
    val activity = activity()
    val p = guardState().policy

    if (showProviders) {
        Column {
            PrivateDnsProviders.all.forEach { (host, description) ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .selectable(selected = p.privateDnsHost == host, role = Role.RadioButton) {
                            actions.change { it.copy(privateDnsHost = host) }
                        }
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = p.privateDnsHost == host, onClick = null)
                    Column(Modifier.padding(start = 12.dp)) {
                        Text(description.substringBefore(" ("), style = MaterialTheme.typography.bodyLarge)
                        Text(description.substringAfter("(", "").removeSuffix(")"),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            CustomDnsOption(status, actions)
        }
    }

    StatusLine(
        status.dns.filtering,
        when {
            !status.dns.online -> "Offline: can't check right now"
            status.dns.filtering -> "Filtering through ${status.dns.host}"
            status.dns.custom -> "Custom filter ${status.dns.host} isn't confirmed to block adult sites yet"
            status.dns.active -> "Private DNS is ${status.dns.host}, which isn't a family filter"
            else -> "Private DNS is off or automatic"
        },
    )
    if (status.dns.filtering) return

    Hint("Settings → Network & internet → Private DNS → \"Private DNS provider hostname\", then paste:")
    Surface(color = MaterialTheme.colorScheme.surfaceContainerHighest, shape = MaterialTheme.shapes.medium) {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                p.privateDnsHost,
                modifier = Modifier.weight(1f).padding(vertical = 12.dp),
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = FontFamily.Monospace,
            )
            androidx.compose.material3.IconButton(onClick = { copy(ctx, p.privateDnsHost); actions.message("Hostname copied") }) {
                Icon(Icons.Filled.ContentCopy, contentDescription = "Copy hostname", tint = MaterialTheme.colorScheme.primary)
            }
        }
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = { activity.openSetupScreen(Intent(Settings.ACTION_WIRELESS_SETTINGS), TamperGuard.Grace.PRIVATE_DNS) }) {
            Text("Open network settings")
        }
        if (status.canEnforceDns) {
            FilledTonalButton(onClick = { PrivateDns.enforce(ctx, p.privateDnsHost) }) { Text("Apply automatically") }
        }
    }
    TipCard(
        "Using a VPN?",
        "Some VPN apps send DNS through their own servers instead. If this stays red while your VPN is on, set the VPN's " +
            "DNS to a family filter (Cloudflare 1.1.1.3 or CleanBrowsing 185.228.168.168), or turn off \"Require Private DNS\".",
    )
}

@Composable
fun AutoRepairSetup(status: SetupStatus, actions: PolicyActions) {
    val ctx = LocalContext.current
    StatusLine(status.canEnforceDns, if (status.canEnforceDns) "Auto-repair is on" else "Auto-repair is off (optional)")
    if (status.canEnforceDns) return
    Hint("With a computer and USB debugging, run this once. NoXXX can then switch Private DNS back on by itself.")
    val cmd = "adb shell pm grant ${ctx.packageName} android.permission.WRITE_SECURE_SETTINGS"
    Surface(color = MaterialTheme.colorScheme.surfaceContainerHighest, shape = MaterialTheme.shapes.medium) {
        SelectionContainer {
            Text(cmd, Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
        }
    }
    OutlinedButton(onClick = { copy(ctx, cmd); actions.message("Command copied") }) { Text("Copy command") }
}

@Composable
fun AdminSetup(status: SetupStatus) {
    val ctx = LocalContext.current
    val activity = activity()
    StatusLine(status.admin, if (status.admin) "Uninstall protection is on" else "Uninstall protection is off")
    if (!status.admin) {
        Button(onClick = { activity.openSetupScreen(GuardAdminReceiver.activationIntent(ctx), TamperGuard.Grace.DEVICE_ADMIN) }) {
            Text("Turn on")
        }
    }
}

@Composable
fun BatterySetup(status: SetupStatus, committed: Boolean) {
    val ctx = LocalContext.current
    val activity = activity()
    StatusLine(status.battery, if (status.battery) "Battery optimization is off for NoXXX" else "Battery optimization may stop the guard")
    if (!status.battery) {
        Button(onClick = {
            activity.openSetupScreen(
                Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${ctx.packageName}")),
                TamperGuard.Grace.BATTERY,
            )
        }) { Text("Allow") }
    }
    if (!committed) {
        Hint("On Samsung, Xiaomi, OnePlus and similar phones, also set NoXXX's battery use to \"Unrestricted\" in App info.")
        OutlinedButton(onClick = {
            activity.openSetupScreen(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}")),
                TamperGuard.Grace.FULL,
            )
        }) { Text("Open App info") }
    }
}

@Composable
fun TipCard(title: String, body: String) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onTertiaryContainer)
            Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onTertiaryContainer)
        }
    }
}

fun copy(context: Context, text: String) {
    context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("NoXXX", text))
}

/**
 * A personal NextDNS / Control D filter, for people who need sites the family filters block (torrent
 * indexes, say). It only counts as filtering while the canary check confirms adult sites are blocked.
 */
@Composable
private fun CustomDnsOption(status: SetupStatus, actions: PolicyActions) {
    val p = guardState().policy
    val selected = PrivateDnsProviders.isCustom(p.privateDnsHost)
    var open by remember { mutableStateOf(selected) }
    var input by remember { mutableStateOf(if (selected) p.privateDnsHost else "") }
    val scope = rememberCoroutineScope()
    Row(
        Modifier.fillMaxWidth().selectable(selected = selected || open, role = Role.RadioButton) { open = true }
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Column(Modifier.padding(start = 12.dp)) {
            Text("Custom filter: NextDNS or Control D", style = MaterialTheme.typography.bodyLarge)
            Text("Your own blocklist and allowed sites. Must block adult sites to count.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    if (!open && !selected) return
    Column(Modifier.padding(start = 36.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Hint("1. Make a free profile at nextdns.io (or controld.com) and turn on porn blocking (NextDNS: Parental " +
            "Control → Porn). 2. Add sites you need to its Allowlist. 3. Paste the DNS-over-TLS hostname here, " +
            "like abc123.dns.nextdns.io. 4. Also add those sites to Allowed sites below, since NoXXX's own list may block them too.")
        OutlinedTextField(
            value = input,
            onValueChange = { input = it.trim().lowercase() },
            label = { Text("Hostname") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                if (PrivateDnsProviders.isCustom(input)) actions.change { it.copy(privateDnsHost = input) }
                else actions.message("Enter a hostname ending in .dns.nextdns.io or .dns.controld.com")
            }) { Text("Use this filter") }
            if (status.dns.custom) {
                TextButton(onClick = {
                    scope.launch(Dispatchers.IO) { NoXXXApp.instance.checkCustomDns(force = true) }
                    actions.message("Checking…")
                }) { Text("Check now") }
            }
        }
        if (status.dns.custom) {
            val last = DnsCanary.last?.takeIf { it.host == status.dns.host }
            when {
                last == null -> Hint("Not checked yet. It checks every few minutes, or tap Check now.")
                last.passed -> StatusLine(true, "Confirmed: adult test sites are blocked")
                last.inconclusive -> Hint("Couldn't check: DNS didn't answer. Try again on a working connection.")
                else -> StatusLine(false, "Not blocking adult sites (${last.leaked.joinToString()}). Turn on porn blocking in your profile.")
            }
        }
    }
}
