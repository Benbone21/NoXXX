package com.noxxx.guard.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.LockClock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Pin
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.noxxx.guard.NoXXXApp
import com.noxxx.guard.policy.PolicyEngine
import com.noxxx.guard.policy.UnlockRequest
import com.noxxx.guard.policy.formatDuration
import com.noxxx.guard.policy.formatMinutes

private val COOL_OFF_OPTIONS = listOf(60, 6 * 60, 12 * 60, 24 * 60, 48 * 60, 72 * 60, 7 * 24 * 60)
private val LOCKDOWN_DAYS = listOf(1, 3, 7, 14, 30, 90)

private fun mmss(ms: Long): String {
    val s = (ms + 999) / 1000
    return "%d:%02d".format(s / 60, s % 60)
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
fun LazyListScope.lockScreen(actions: PolicyActions) {
    item {
        if (guardState().committed) {
            LockStatusCard(actions, full = true)
        } else {
            SectionCard("Setup mode", "Settings lock once you start protection on the Home tab.", icon = Icons.Filled.LockOpen) {}
        }
    }

    item {
        val state = guardState()
        val p = state.policy
        SectionCard(
            "Cool-off length",
            "How long an unlock request waits before settings can be changed. Longer is stronger; " +
                "shortening it needs an unlock.",
            icon = Icons.Filled.Timer,
        ) {
            val editable = LocalSettingsEditable.current
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                COOL_OFF_OPTIONS.forEach { m ->
                    FilterChip(
                        selected = p.coolOffMinutes == m,
                        enabled = editable || m >= p.coolOffMinutes,
                        onClick = { actions.change { it.copy(coolOffMinutes = m) } },
                        label = { Text(formatMinutes(m)) },
                    )
                }
            }
        }
    }

    item { FullUnlockCard(actions) }
    item { LockdownCard(actions) }
    item { PartnerCard(actions) }
    item { PinCard(actions) }

    item {
        val p = guardState().policy
        SectionCard("Removing NoXXX", icon = Icons.Filled.DeleteForever) {
            Text(
                "1. Request an unlock at the top of this tab and wait out the cool-off.\n" +
                    "2. Open the unlock. For the next 15 minutes, system settings aren't guarded.\n" +
                    "3. Settings → Security → Device admin apps → turn off NoXXX.\n" +
                    "4. Uninstall as usual.",
                style = MaterialTheme.typography.bodyMedium,
            )
            ToggleRow("Tamper protection", "Closes system screens that could switch NoXXX off.", p.tamperProtection) { on ->
                actions.change { it.copy(tamperProtection = on) }
            }
        }
    }
}

@Composable
private fun FullUnlockCard(actions: PolicyActions) {
    val state = guardState()
    val store = NoXXXApp.instance.store
    val u = state.fullUnlock
    SectionCard(
        "Full unlock",
        "Required to deactivate device admin or accessibility. Has a 1-week wait.",
        icon = Icons.Filled.LockOpen,
    ) {
        if (u == null) {
            StatusLine(false, "No full unlock requested")
            Button(onClick = {
                if (store.requestFullUnlock()) actions.message("Full unlock requested. Ready in 1 week.")
            }) { Text("Request full unlock") }
        } else if (u.isWaiting) {
            Text("Full unlock in ${formatDuration(u.remainingWaitMs)}")
            LinearProgressIndicator(
                progress = { (u.elapsedMs.toFloat() / u.requiredMs).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth(),
            )
            TextButton(onClick = { store.relockFull(); actions.message("Full unlock cancelled") }) { Text("Cancel") }
        } else if (u.isReady) {
            Text("Full unlock ready. Opens a 15-minute window.")
            Row {
                Button(onClick = { store.openFullUnlock() }) { Text("Open full unlock") }
                TextButton(onClick = { store.relockFull() }) { Text("Cancel") }
            }
        } else if (u.isOpen) {
            Text("Full unlock active: ${mmss(u.windowRemainingMs)} left", color = MaterialTheme.colorScheme.primary)
            LinearProgressIndicator(
                progress = { (u.windowRemainingMs.toFloat() / UnlockRequest.WINDOW_MS).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth(),
            )
            TextButton(onClick = { store.relockFull(); actions.message("Full unlock closed") }) { Text("Close now") }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun LockdownCard(actions: PolicyActions) {
    val state = guardState()
    val store = NoXXXApp.instance.store
    var confirmDays by remember { mutableStateOf<Int?>(null) }
    SectionCard(
        "Lockdown",
        "Freezes every setting and pauses unlock requests until the time runs out. It can be extended but not shortened.",
        icon = Icons.Filled.LockClock,
    ) {
        if (state.lockdownRemainingMs > 0) {
            Text("Locked for ${formatDuration(state.lockdownRemainingMs)}", style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.primary)
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LOCKDOWN_DAYS.forEach { d ->
                FilterChip(selected = false, onClick = { confirmDays = d }, label = { Text(if (d == 1) "1 day" else "$d days") })
            }
        }
        if (state.lockdownRemainingMs > 0 && state.partnerHash != null) {
            PasswordAction("Partner password", "End") { pw ->
                actions.message(if (store.partnerEndLockdown(pw)) "Lockdown ended by partner" else "Wrong password")
            }
        }
    }
    confirmDays?.let { d ->
        AlertDialog(
            onDismissRequest = { confirmDays = null },
            icon = { Icon(Icons.Filled.LockClock, contentDescription = null) },
            title = { Text(if (d == 1) "Lock for 1 day?" else "Lock for $d days?") },
            text = { Text("You won't be able to loosen any setting until it ends. Making things stricter still works.") },
            confirmButton = {
                TextButton(onClick = {
                    store.startLockdown(d * 24L * 60 * 60_000)
                    confirmDays = null
                    actions.message("Lockdown started")
                }) { Text("Lock") }
            },
            dismissButton = { TextButton(onClick = { confirmDays = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun PartnerCard(actions: PolicyActions) {
    val state = guardState()
    val store = NoXXXApp.instance.store
    SectionCard(
        "Accountability partner",
        "Let someone you trust type a password that approves a change or ends lockdown immediately. Don't keep the password yourself.",
        icon = Icons.Filled.Groups,
    ) {
        if (state.partnerHash == null && !PolicyEngine.canAddPartner(state)) {
            StatusLine(false, "No partner password")
            Hint("A partner can only be added during setup, before protection starts. Otherwise you could add one yourself and skip the cool-off.")
        } else if (state.partnerHash == null) {
            PasswordAction("New partner password", "Set") { pw ->
                if (pw.length < 4) actions.message("Use at least 4 characters")
                else actions.message(if (store.setPartnerPassword(null, pw)) "Partner password set" else "Protection has started")
            }
        } else {
            StatusLine(true, "Partner password is set")
            PasswordAction("Current partner password", "Remove") { pw ->
                actions.message(if (store.setPartnerPassword(pw, null)) "Partner password removed" else "Wrong password")
            }
        }
    }
}

@Composable
private fun PinCard(actions: PolicyActions) {
    val state = guardState()
    val store = NoXXXApp.instance.store
    SectionCard("App PIN", "Asks for a PIN before NoXXX opens.", icon = Icons.Filled.Pin) {
        if (state.pinHash == null) {
            PasswordAction("New PIN", "Set", numeric = true) { pin ->
                if (pin.length < 4) actions.message("Use at least 4 digits")
                else { store.setPin(null, pin); actions.message("PIN set") }
            }
        } else {
            StatusLine(true, "PIN is set")
            PasswordAction("Current PIN", "Remove", numeric = true) { pin ->
                actions.message(if (store.setPin(pin, null)) "PIN removed" else "Wrong PIN")
            }
        }
    }
}
