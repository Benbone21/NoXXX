package com.noxxx.guard.ui

import android.os.Build
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ImageSearch
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import com.noxxx.guard.policy.Sensitivity

@OptIn(ExperimentalMaterial3Api::class)
fun LazyListScope.imagesScreen(actions: PolicyActions) {
    item { LockStatusCard(actions) }

    item {
        val p = guardState().policy
        val supported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
        SectionCard(
            "On-screen detection",
            "About every 1.5 s while the screen is on, NoXXX checks a shrunken copy of the screen with an on-device model. " +
                "Nothing is saved or sent anywhere. If explicit content is detected, the screen is covered and the app is sent back.",
            icon = Icons.Filled.ImageSearch,
        ) {
            if (!supported) StatusLine(false, "Needs Android 11 or newer")
            ToggleRow("Detect explicit images", null, p.imageScan && supported, enabled = supported) { on ->
                actions.change { it.copy(imageScan = on) }
            }
            Text("Sensitivity", style = MaterialTheme.typography.titleSmall)
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                val editable = LocalSettingsEditable.current
                Sensitivity.entries.forEachIndexed { i, s ->
                    SegmentedButton(
                        selected = p.sensitivity == s,
                        enabled = editable || s >= p.sensitivity,
                        onClick = { actions.change { it.copy(sensitivity = s) } },
                        shape = SegmentedButtonDefaults.itemShape(i, Sensitivity.entries.size),
                    ) { Text(s.name.lowercase().replaceFirstChar { c -> c.uppercase() }) }
                }
            }
            Hint(
                when (p.sensitivity) {
                    Sensitivity.RELAXED -> "Only clear-cut explicit images. Fewest false alarms."
                    Sensitivity.BALANCED -> "Explicit images plus very revealing ones. Recommended."
                    Sensitivity.STRICT -> "Also flags suggestive images, and reacts on the first frame. Expect some false alarms."
                }
            )
            ToggleRow(
                "Also scan messaging apps",
                "Off by default. Messaging and video-call apps like Signal and WhatsApp are skipped, since your " +
                    "own camera on a call can set off false alarms. Turning this on needs an unlock to turn back off.",
                p.scanMessagingApps,
            ) { on -> actions.change { it.copy(scanMessagingApps = on) } }
        }
    }

    item {
        val p = guardState().policy
        SectionCard(
            "Skip these apps",
            "Skipping saves battery in apps like games or maps. Adding an app here needs an unlock.",
            icon = Icons.Filled.SportsEsports,
        ) {
            AppPicker(p.imageScanExemptApps, checkingLoosens = true) { pkg, on ->
                actions.change {
                    if (on) it.copy(imageScanExemptApps = it.imageScanExemptApps + pkg)
                    else it.copy(imageScanExemptApps = it.imageScanExemptApps - pkg)
                }
            }
        }
    }
}
