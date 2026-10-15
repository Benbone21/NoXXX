package com.noxxx.guard.ui

import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AppBlocking
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.noxxx.guard.monitor.BrowserRegistry
import com.noxxx.guard.monitor.EssentialApps

fun LazyListScope.appsScreen(actions: PolicyActions) {
    item { LockStatusCard(actions) }

    item {
        val p = guardState().policy
        SectionCard("Browsers", icon = Icons.Filled.Public) {
            ToggleRow(
                "Block unsupported browsers",
                "Browsers NoXXX can't read the address bar of are closed. Supported: Chrome, Firefox, Samsung Internet, Edge, Brave, Opera, Vivaldi, DuckDuckGo.",
                p.blockUnsupportedBrowsers,
            ) { on -> actions.change { it.copy(blockUnsupportedBrowsers = on) } }
            ToggleRow(
                "Block private tabs",
                "Private/incognito tabs hide their contents from screen checks, so they're closed (Android 11+).",
                p.blockPrivateBrowsing,
            ) { on -> actions.change { it.copy(blockPrivateBrowsing = on) } }
            InstalledBrowsers(p.blockUnsupportedBrowsers)
        }
    }

    item {
        val ctx = LocalContext.current
        val essential = remember { EssentialApps.protectedSet(ctx) }
        val p = guardState().policy
        SectionCard("Blocked apps", "Checked apps are closed as soon as they open. Blocking is always allowed; unblocking needs an unlock.",
            icon = Icons.Filled.AppBlocking) {
            AppPicker(p.blockedApps, protectedPkgs = essential + p.neverBlockApps) { pkg, on ->
                actions.change { if (on) it.copy(blockedApps = it.blockedApps + pkg) else it.copy(blockedApps = it.blockedApps - pkg) }
            }
        }
    }

    item {
        val ctx = LocalContext.current
        val essential = remember { EssentialApps.protectedSet(ctx) }
        val p = guardState().policy
        SectionCard(
            "Never block",
            "Apps here can never be blocked, so protection can't lock you out of them. Good for banking, password " +
                "managers, maps, and the like. Phone, texts, settings, the launcher and keyboard are always protected. " +
                "Adding an app needs an unlock.",
            icon = Icons.Filled.VerifiedUser,
        ) {
            AppPicker(p.neverBlockApps, checkingLoosens = true, protectedPkgs = essential) { pkg, on ->
                actions.change {
                    if (on) it.copy(neverBlockApps = it.neverBlockApps + pkg)
                    else it.copy(neverBlockApps = it.neverBlockApps - pkg)
                }
            }
        }
    }
}

@Composable
private fun InstalledBrowsers(blockUnsupported: Boolean) {
    val ctx = LocalContext.current
    val registry = remember { BrowserRegistry(ctx) }
    val apps = rememberInstalledApps()
    val browsers = registry.installedBrowsers()
    if (browsers.isEmpty()) return
    Text("Installed browsers", style = MaterialTheme.typography.titleSmall)
    browsers.sortedBy { pkg -> apps.firstOrNull { it.pkg == pkg }?.label ?: pkg }.forEach { pkg ->
        val name = apps.firstOrNull { it.pkg == pkg }?.label ?: pkg
        val supported = registry.isSupported(pkg)
        StatusLine(
            supported || !blockUnsupported,
            name + when {
                supported -> " (monitored)"
                blockUnsupported -> " (will be blocked)"
                else -> " (not monitored)"
            },
        )
    }
}
