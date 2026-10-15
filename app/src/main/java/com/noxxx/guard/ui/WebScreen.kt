package com.noxxx.guard.ui

import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CheckCircleOutline
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.text.input.KeyboardType
import com.noxxx.guard.NoXXXApp
import com.noxxx.guard.web.Domains

fun LazyListScope.webScreen(actions: PolicyActions, refresh: Int) {
    item { LockStatusCard(actions) }

    item {
        val p = guardState().policy
        SectionCard(
            "Address-bar checking",
            "Supported browsers are checked against %,d bundled domains plus your own list.".format(NoXXXApp.instance.blocklist.domainCount),
            icon = Icons.Filled.Language,
        ) {
            ToggleRow("Block listed websites", null, p.urlFilter) { on -> actions.change { it.copy(urlFilter = on) } }
        }
    }

    item {
        val p = guardState().policy
        val status = rememberSetupStatus(refresh)
        SectionCard(
            "Private DNS filter",
            "Android's Private DNS sends every app's lookups to a family-filter resolver. No VPN is needed, so your own VPN keeps working.",
            icon = Icons.Filled.Dns,
        ) {
            ToggleRow(
                "Require Private DNS",
                "Browsers are paused while Private DNS isn't set to one of these filters.",
                p.requirePrivateDns,
            ) { on -> actions.change { it.copy(requirePrivateDns = on) } }
            PrivateDnsSetup(status, actions, showProviders = true)
            Hint("Picking a provider here doesn't change your phone's setting by itself. Update it in Settings too (or use auto-repair).")
        }
    }

    item {
        val p = guardState().policy
        SectionCard("Your blocked sites", "Blocks the site and all its subdomains. Adding is always allowed; removing needs an unlock.", icon = Icons.Filled.Block) {
            EditableList(
                items = p.customBlockedDomains,
                placeholder = "example.com",
                normalize = Domains::normalize,
                onAdd = { d -> actions.change { it.copy(customBlockedDomains = it.customBlockedDomains + d) } },
                onRemove = { d -> actions.change { it.copy(customBlockedDomains = it.customBlockedDomains - d) } },
            )
        }
    }

    item {
        val p = guardState().policy
        SectionCard("Always allow", "For sites blocked by mistake. Adding one needs an unlock; removing is always allowed.",
            icon = Icons.Filled.CheckCircleOutline) {
            EditableList(
                items = p.allowedDomains,
                placeholder = "example.com",
                addLoosens = true,
                normalize = Domains::normalize,
                onAdd = { d -> actions.change { it.copy(allowedDomains = it.allowedDomains + d) } },
                onRemove = { d -> actions.change { it.copy(allowedDomains = it.allowedDomains - d) } },
            )
        }
    }

    item {
        val p = guardState().policy
        SectionCard(
            "Blocked words",
            "Searches and addresses are split into words and compared whole, so a blocked word inside a longer, " +
                "normal word never triggers a block.",
            icon = Icons.Filled.TextFields,
        ) {
            ToggleRow(
                "Built-in word list",
                "%,d words and phrases. The list isn't shown, so it can't become a prompt.".format(NoXXXApp.instance.keywords.bundledCount),
                p.builtInKeywords,
            ) { on -> actions.change { it.copy(builtInKeywords = on) } }
            ToggleRow(
                "Check search boxes in other apps",
                "Also checks search fields in apps like Reddit, X and the Play Store. Message boxes and passwords are skipped.",
                p.checkSearchBoxes,
            ) { on -> actions.change { it.copy(checkSearchBoxes = on) } }
            Text("Your own words", style = MaterialTheme.typography.titleSmall)
            EditableList(
                items = p.customKeywords,
                placeholder = "word or phrase",
                keyboardType = KeyboardType.Text,
                normalize = { it.trim().lowercase().takeIf { w -> w.length >= 3 } },
                onAdd = { w -> actions.change { it.copy(customKeywords = it.customKeywords + w) } },
                onRemove = { w -> actions.change { it.copy(customKeywords = it.customKeywords - w) } },
            )
        }
    }

    item {
        val p = guardState().policy
        SectionCard(
            "Allowed words",
            "If a normal word or phrase gets blocked by mistake, add it here. Adding needs an unlock; removing is always allowed.",
            icon = Icons.Filled.CheckCircleOutline,
        ) {
            EditableList(
                items = p.allowedWords,
                placeholder = "word or phrase",
                keyboardType = KeyboardType.Text,
                addLoosens = true,
                normalize = { it.trim().lowercase().takeIf { w -> w.length >= 2 } },
                onAdd = { w -> actions.change { it.copy(allowedWords = it.allowedWords + w) } },
                onRemove = { w -> actions.change { it.copy(allowedWords = it.allowedWords - w) } },
            )
        }
    }
}
