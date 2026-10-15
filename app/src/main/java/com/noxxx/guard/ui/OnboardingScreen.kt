package com.noxxx.guard.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Accessibility
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.noxxx.guard.NoXXXApp
import com.noxxx.guard.policy.Sensitivity
import com.noxxx.guard.policy.formatMinutes

private enum class Page { WELCOME, PROTECTIONS, ACCESSIBILITY, PRIVATE_DNS, UNINSTALL, BATTERY, APPS, COOL_OFF, FINISH }

private val COOL_OFF_CHOICES = listOf(60, 6 * 60, 24 * 60, 48 * 60, 72 * 60, 7 * 24 * 60)

/**
 * First-run setup guide: walks through every permission and choice one page at a time.
 * Only shown in setup mode; once protection starts it can't be opened again.
 */
@Composable
fun Onboarding(actions: PolicyActions, snackbar: SnackbarHostState, refresh: Int, onDone: () -> Unit) {
    val state = guardState()
    val pages = Page.entries.filter { it != Page.PRIVATE_DNS || state.policy.requirePrivateDns }
    var current by rememberSaveable { mutableStateOf(Page.WELCOME) }
    val index = pages.indexOf(current).coerceAtLeast(0)
    val status = rememberSetupStatus(refresh)

    fun go(delta: Int) {
        current = pages[(index + delta).coerceIn(0, pages.lastIndex)]
    }
    BackHandler(enabled = index > 0) { go(-1) }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            Column(Modifier.statusBarsPadding().padding(horizontal = 24.dp, vertical = 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Step ${index + 1} of ${pages.size}",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    if (current != Page.FINISH) {
                        TextButton(onClick = { current = Page.FINISH }) { Text("Skip to end") }
                    }
                }
                LinearProgressIndicator(
                    progress = { (index + 1f) / pages.size },
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                )
            }
        },
        bottomBar = {
            if (current != Page.FINISH) {
                Row(
                    Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 24.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (index > 0) TextButton(onClick = { go(-1) }) { Text("Back") }
                    Spacer(Modifier.weight(1f))
                    val required = when (current) {
                        Page.ACCESSIBILITY -> status.accessibility
                        Page.PRIVATE_DNS -> status.dns.filtering
                        Page.UNINSTALL -> status.admin
                        Page.BATTERY -> status.battery
                        else -> true
                    }
                    Button(onClick = { go(1) }) {
                        Text(
                            when {
                                current == Page.WELCOME -> "Get started"
                                !required -> "Skip for now"
                                else -> "Next"
                            }
                        )
                    }
                }
            }
        },
    ) { padding ->
        AnimatedContent(
            targetState = current,
            transitionSpec = {
                val forward = pages.indexOf(targetState) > pages.indexOf(initialState)
                (slideInHorizontally { w -> if (forward) w / 3 else -w / 3 } + fadeIn()) togetherWith
                    (slideOutHorizontally { w -> if (forward) -w / 3 else w / 3 } + fadeOut())
            },
            label = "onboarding",
            modifier = Modifier.padding(padding),
        ) { page ->
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                when (page) {
                    Page.WELCOME -> WelcomePage()
                    Page.PROTECTIONS -> ProtectionsPage(actions, status)
                    Page.ACCESSIBILITY -> PageHeader(Icons.Filled.Accessibility, "Turn on the guard",
                        "NoXXX uses Android's Accessibility service to read browser addresses, close blocked apps, check the " +
                            "screen for explicit images, and stop itself being switched off on impulse. It never reads passwords " +
                            "or sends anything off your phone.") { AccessibilitySetup(status) }
                    Page.PRIVATE_DNS -> PageHeader(Icons.Filled.Dns, "Filter every app",
                        "Private DNS is built into Android. Pointing it at a family filter blocks adult sites in every app and " +
                            "browser, with no VPN needed.") {
                        PrivateDnsSetup(status, actions, showProviders = true)
                        HorizontalDivider()
                        Text("Optional: auto-repair", style = MaterialTheme.typography.titleSmall)
                        AutoRepairSetup(status, actions)
                    }
                    Page.UNINSTALL -> PageHeader(Icons.Filled.AdminPanelSettings, "Prevent quick uninstalls",
                        "As a device admin, NoXXX can't be uninstalled until you switch this off, and that screen is guarded " +
                            "by the cool-off.") { AdminSetup(status) }
                    Page.BATTERY -> PageHeader(Icons.Filled.BatteryChargingFull, "Keep the guard running",
                        "Some phones stop background apps to save battery. Exempting NoXXX keeps protection on all day.") {
                        BatterySetup(status, committed = false)
                    }
                    Page.APPS -> PageHeader(Icons.Filled.Apps, "Block apps",
                        "Optional. Pick apps that are a problem for you; they'll close as soon as they open. You can change this " +
                            "later on the Apps tab.") {
                        val p = guardState().policy
                        AppPicker(p.blockedApps) { pkg, on ->
                            actions.change { if (on) it.copy(blockedApps = it.blockedApps + pkg) else it.copy(blockedApps = it.blockedApps - pkg) }
                        }
                    }
                    Page.COOL_OFF -> CoolOffPage(actions)
                    Page.FINISH -> FinishPage(status, onDone)
                }
                Spacer(Modifier.padding(bottom = 24.dp))
            }
        }
    }
}

@Composable
private fun PageHeader(icon: ImageVector, title: String, body: String, content: @Composable ColumnScope.() -> Unit) {
    IconBadge(icon, size = 72.dp)
    Text(title, style = MaterialTheme.typography.headlineMedium)
    Text(body, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Column(verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
}

@Composable
private fun WelcomePage() {
    PageHeader(Icons.Filled.Shield, "Welcome to NoXXX",
        "Let's set up your protection. It takes about two minutes, and everything runs on your phone.") {
        Feature(Icons.Filled.Language, "Websites", "Family-filter DNS plus a list of nearly 77,000 blocked sites")
        Feature(Icons.Filled.Image, "Images", "On-device detection covers explicit content in any app")
        Feature(Icons.Filled.Apps, "Apps", "Block the apps you choose")
        Feature(Icons.Filled.Timer, "Cool-off", "Loosening any setting waits out a delay, so urges pass first")
        TipCard("Nothing is locked yet", "While you set up, every change applies at once. Protection only locks in when you choose to start it.")
    }
}

@Composable
private fun Feature(icon: ImageVector, title: String, body: String) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(body) },
        leadingContent = { IconBadge(icon, size = 40.dp, container = MaterialTheme.colorScheme.secondaryContainer,
            tint = MaterialTheme.colorScheme.onSecondaryContainer) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProtectionsPage(actions: PolicyActions, status: SetupStatus) {
    val p = guardState().policy
    PageHeader(Icons.Filled.Tune, "Choose your protection",
        "Recommended settings are already on. Turn off anything you don't want.") {
        Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = MaterialTheme.shapes.large) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                ToggleRow("Block listed websites", "Checks the address bar of supported browsers.", p.urlFilter) { on ->
                    actions.change { it.copy(urlFilter = on) }
                }
                ToggleRow("Block explicit searches", "A built-in word list, matched as whole words so normal words aren't caught.",
                    p.builtInKeywords) { on -> actions.change { it.copy(builtInKeywords = on) } }
                ToggleRow("Check search boxes in other apps", "Search fields in apps, not messages or passwords.",
                    p.checkSearchBoxes) { on -> actions.change { it.copy(checkSearchBoxes = on) } }
                ToggleRow("Require Private DNS", "Pauses browsers if the system-wide filter is off. Turn off if your VPN can't work with it.",
                    p.requirePrivateDns) { on -> actions.change { it.copy(requirePrivateDns = on) } }
                ToggleRow(
                    "Detect explicit images",
                    if (status.imageSupported) "Checks the screen on-device every ~1.5 s." else "Needs Android 11 or newer.",
                    p.imageScan && status.imageSupported,
                    enabled = status.imageSupported,
                ) { on -> actions.change { it.copy(imageScan = on) } }
                ToggleRow("Block private tabs", "Incognito tabs hide their contents from checks.", p.blockPrivateBrowsing) { on ->
                    actions.change { it.copy(blockPrivateBrowsing = on) }
                }
                ToggleRow("Block unsupported browsers", "Closes browsers NoXXX can't monitor.", p.blockUnsupportedBrowsers) { on ->
                    actions.change { it.copy(blockUnsupportedBrowsers = on) }
                }
                ToggleRow("Tamper protection", "Closes system screens that could switch NoXXX off.", p.tamperProtection) { on ->
                    actions.change { it.copy(tamperProtection = on) }
                }
            }
        }
        if (p.imageScan && status.imageSupported) {
            Text("Image sensitivity", style = MaterialTheme.typography.titleSmall)
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
        }
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun CoolOffPage(actions: PolicyActions) {
    val state = guardState()
    val p = state.policy
    PageHeader(Icons.Filled.Timer, "Pick a cool-off",
        "Once protection starts, settings lock. You can always view your stats and add protection, but to loosen " +
            "anything you request an unlock, wait this long, then get 15 minutes to make changes.") {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val editable = LocalSettingsEditable.current
            COOL_OFF_CHOICES.forEach { m ->
                FilterChip(selected = p.coolOffMinutes == m, onClick = { actions.change { it.copy(coolOffMinutes = m) } },
                    enabled = editable || m >= p.coolOffMinutes, label = { Text(formatMinutes(m)) })
            }
        }
        Hint("24 hours is a good start. Longer is stronger.")
        HorizontalDivider()
        Text("Accountability partner (optional)", style = MaterialTheme.typography.titleSmall)
        if (state.partnerHash == null) {
            Hint("Hand your phone to someone you trust and let them type a password. It can approve a change or end lockdown early. " +
                "Don't keep it yourself. This is your only chance to add one: once protection starts, it can't be added.")
            PasswordAction("Partner password", "Set") { pw ->
                if (pw.length < 4) actions.message("Use at least 4 characters")
                else actions.message(if (NoXXXApp.instance.store.setPartnerPassword(null, pw)) "Partner password set" else "Protection has started")
            }
        } else {
            StatusLine(true, "Partner password is set")
        }
    }
}

@Composable
private fun FinishPage(status: SetupStatus, onDone: () -> Unit) {
    val state = guardState()
    val store = NoXXXApp.instance.store
    val p = state.policy
    val dnsReady = !p.requirePrivateDns || status.dns.filtering
    val ready = status.accessibility && dnsReady
    var confirm by remember { mutableStateOf(false) }

    PageHeader(Icons.Filled.VerifiedUser, if (ready) "You're all set" else "Almost there",
        if (ready) "Start protection now, or look around first. Nothing locks until you start."
        else "A couple of required steps are still open. You can finish them later from the Home screen.") {
        StatusLine(status.accessibility, "Guard service")
        if (p.requirePrivateDns) StatusLine(status.dns.filtering, "Private DNS family filter")
        StatusLine(status.admin, "Uninstall protection")
        StatusLine(status.battery, "Battery exemption")
        StatusLine(true, "Cool-off: ${formatMinutes(p.coolOffMinutes)}")

        Spacer(Modifier.padding(top = 8.dp))
        Button(onClick = { confirm = true }, enabled = ready, modifier = Modifier.fillMaxWidth()) { Text("Start protection") }
        OutlinedButton(onClick = { store.finishOnboarding(); onDone() }, modifier = Modifier.fillMaxWidth()) {
            Text("Explore first")
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
            confirmButton = {
                TextButton(onClick = {
                    store.commit()
                    store.finishOnboarding()
                    confirm = false
                    onDone()
                }) { Text("Start") }
            },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("Not yet") } },
        )
    }
}
