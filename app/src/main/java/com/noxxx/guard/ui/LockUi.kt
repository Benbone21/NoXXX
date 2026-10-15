package com.noxxx.guard.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockClock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.noxxx.guard.NoXXXApp
import com.noxxx.guard.policy.GuardState
import com.noxxx.guard.policy.UnlockRequest
import com.noxxx.guard.policy.formatDuration
import com.noxxx.guard.policy.formatMinutes

/** False while protection is on and settings are locked. Controls use it to disable loosening. */
val LocalSettingsEditable = compositionLocalOf { true }

private enum class LockPhase { LOCKDOWN, LOCKED, WAITING, READY, OPEN, FULL_OPEN }

private fun phaseOf(s: GuardState): LockPhase? {
    if (!s.committed) return null
    val u = s.unlock
    val fu = s.fullUnlock
    return when {
        s.lockdownRemainingMs > 0 -> LockPhase.LOCKDOWN
        fu?.isOpen == true -> LockPhase.FULL_OPEN
        u == null -> LockPhase.LOCKED
        u.isOpen -> LockPhase.OPEN
        u.isReady -> LockPhase.READY
        else -> LockPhase.WAITING
    }
}

private fun mmss(ms: Long): String {
    val s = (ms + 999) / 1000
    return "%d:%02d".format(s / 60, s % 60)
}

/**
 * Shows whether settings are locked and what to do about it. [full] adds the explanation and the
 * partner option (used on the Lock tab); the compact form sits at the top of other tabs.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LockStatusCard(actions: PolicyActions, full: Boolean = false) {
    val state = guardState()
    val store = NoXXXApp.instance.store
    val phase = phaseOf(state) ?: return
    val u = state.unlock
    val coolOff = formatMinutes(state.policy.coolOffMinutes)
    val window = formatDuration(UnlockRequest.WINDOW_MS)

    val (icon: ImageVector, title: String, body: String) = when (phase) {
        LockPhase.LOCKDOWN -> Triple(Icons.Filled.LockClock, "Lockdown: ${formatDuration(state.lockdownRemainingMs)} left",
            "Settings are frozen and unlocks are paused until it ends. You can still add protection.")
        LockPhase.LOCKED -> Triple(Icons.Filled.Lock, "Settings are locked",
            "You can still add protection. To loosen anything, request an unlock: it opens after your $coolOff cool-off " +
                "and gives you $window to make changes.")
        LockPhase.WAITING -> Triple(Icons.Filled.HourglassTop, "Unlock in ${formatDuration(u!!.remainingWaitMs)}",
            "Your unlock request is waiting out the $coolOff cool-off. If the urge has passed, cancel it.")
        LockPhase.READY -> Triple(Icons.Filled.LockOpen, "Unlock is ready",
            "Open it when you're ready to make changes. You'll have $window. It expires in ${formatDuration(u!!.readyRemainingMs)}.")
        LockPhase.OPEN -> Triple(Icons.Filled.LockOpen, "Settings unlocked: ${mmss(u!!.windowRemainingMs)} left",
            "Settings can be changed. TamperShield is still active; use Full Unlock to deactivate device admin.")
        LockPhase.FULL_OPEN -> Triple(Icons.Filled.LockOpen, "Full unlock: ${mmss(state.fullUnlock!!.windowRemainingMs)} left",
            "TamperShield is off. You can deactivate device admin and uninstall. Locks again automatically.")
    }
    val open = phase == LockPhase.OPEN
    val container = if (open) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.tertiaryContainer
    val content = if (open) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onTertiaryContainer

    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = container, contentColor = content),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                IconBadge(icon, size = 40.dp, container = content.copy(alpha = 0.12f), tint = content)
                Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            }
            if (full || phase != LockPhase.LOCKED) Text(body, style = MaterialTheme.typography.bodyMedium)

            if (phase == LockPhase.WAITING) {
                LinearProgressIndicator(
                    progress = { (u!!.elapsedMs.toFloat() / u.requiredMs.coerceAtLeast(1)).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth(),
                    color = content,
                    trackColor = content.copy(alpha = 0.2f),
                )
            }
            if (phase == LockPhase.OPEN) {
                LinearProgressIndicator(
                    progress = { (u!!.windowRemainingMs.toFloat() / UnlockRequest.WINDOW_MS).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth(),
                    color = content,
                    trackColor = content.copy(alpha = 0.2f),
                )
            }
            if (phase == LockPhase.FULL_OPEN) {
                LinearProgressIndicator(
                    progress = { (state.fullUnlock!!.windowRemainingMs.toFloat() / UnlockRequest.WINDOW_MS).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth(),
                    color = content,
                    trackColor = content.copy(alpha = 0.2f),
                )
            }

            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                when (phase) {
                    LockPhase.LOCKED -> Button(onClick = {
                        if (store.requestUnlock()) actions.message("Unlock requested. It'll be ready in $coolOff.")
                    }) { Text("Request unlock") }
                    LockPhase.WAITING -> OutlinedButton(onClick = { store.relock(); actions.message("Unlock cancelled. Nice.") }) {
                        Text("Cancel request", color = content)
                    }
                    LockPhase.READY -> {
                        Button(onClick = { store.openUnlock() }) { Text("Unlock for $window") }
                        TextButton(onClick = { store.relock() }) { Text("Never mind", color = content) }
                    }
                    LockPhase.OPEN -> Button(onClick = { store.relock(); actions.message("Settings locked") }) { Text("Lock now") }
                    LockPhase.FULL_OPEN -> Button(onClick = { store.relockFull(); actions.message("Full unlock closed") }) { Text("Close full unlock") }
                    LockPhase.LOCKDOWN -> Unit
                }
            }

            if (full && state.partnerHash != null && phase != LockPhase.OPEN) {
                PasswordAction("Partner password", "Unlock now") { pw ->
                    actions.message(if (store.partnerUnlock(pw)) "Unlocked by your partner" else "Wrong password")
                }
            }
        }
    }
}

/** Small lock glyph shown next to a control that's disabled because settings are locked. */
@Composable
fun LockedGlyph() {
    Icon(
        Icons.Filled.Lock,
        contentDescription = "Locked",
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(end = 8.dp).size(18.dp),
    )
}
