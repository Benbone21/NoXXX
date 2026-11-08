package com.noxxx.guard.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.RadioButton
import androidx.compose.material3.TextButton
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.togetherWith
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Today
import androidx.compose.material.icons.outlined.Today
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.noxxx.guard.NoXXXApp
import com.noxxx.guard.monitor.TamperGuard
import com.noxxx.guard.policy.ChangeResult
import com.noxxx.guard.policy.Policy
import com.noxxx.guard.policy.formatDuration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { NoXXXTheme { Root() } }
    }

    override fun onResume() {
        super.onResume()
        // Back in the app: any grace period for system setup screens is over.
        TamperGuard.endGrace()
    }

    /** Opens a system setup screen for NoXXX without TamperGuard closing it. */
    fun openSetupScreen(intent: Intent, grace: TamperGuard.Grace) {
        TamperGuard.grantGrace(grace)
        runCatching { startActivity(intent) }
    }
}

private enum class Tab(val label: String, val title: String, val selectedIcon: ImageVector, val icon: ImageVector) {
    TODAY("Today", "Today", Icons.Filled.Today, Icons.Outlined.Today),
    PROGRESS("Progress", "Progress", Icons.Filled.Insights, Icons.Outlined.Insights),
    PROTECTION("Protection", "Protection", Icons.Filled.Shield, Icons.Outlined.Shield),
}

/** Saves a policy change and reports what happened. */
class PolicyActions(private val snackbar: SnackbarHostState, private val scope: CoroutineScope) {
    private val store get() = NoXXXApp.instance.store

    fun change(transform: (Policy) -> Policy) {
        when (val r = store.request(transform)) {
            ChangeResult.NoChange -> Unit
            ChangeResult.Applied -> if (store.state.value.committed) message("Saved")
            ChangeResult.NeedsUnlock -> message("Settings are locked. Request an unlock to loosen this.", "Request unlock") {
                if (store.requestUnlock()) message("Unlock requested")
            }
            is ChangeResult.Locked -> message("Lockdown is on for another ${formatDuration(r.remainingMs)}. Nothing can be loosened until then.")
        }
    }

    fun message(text: String, actionLabel: String? = null, onAction: () -> Unit = {}) {
        scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            if (snackbar.showSnackbar(text, actionLabel) == SnackbarResult.ActionPerformed) onAction()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Root() {
    val store = NoXXXApp.instance.store
    val state = guardState()
    var unlocked by rememberSaveable { mutableStateOf(state.pinHash == null) }
    if (!unlocked) {
        PinGate { unlocked = true }
        return
    }

    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val actions = remember { PolicyActions(snackbar, scope) }

    // Keep timers and live status fresh while the app is open (e.g. after returning from Settings).
    var refresh by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            store.tick()
            refresh++
            delay(1_000)
        }
    }

    // The setup guide is only for setup mode: once protection starts it can't be opened again.
    var showGuide by rememberSaveable { mutableStateOf(false) }
    CompositionLocalProvider(LocalSettingsEditable provides state.settingsEditable) {
        if (!state.committed && (!state.onboardingDone || showGuide)) {
            Onboarding(actions, snackbar, refresh) { showGuide = false }
        } else {
            MainTabs(actions, snackbar, refresh) { showGuide = true }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainTabs(actions: PolicyActions, snackbar: SnackbarHostState, refresh: Int, openGuide: () -> Unit) {
    var tab by rememberSaveable { mutableStateOf(Tab.TODAY) }
    var section by rememberSaveable { mutableStateOf(ProtectionSection.OVERVIEW) }
    var pickPalette by remember { mutableStateOf(false) }
    if (pickPalette) PaletteDialog { pickPalette = false }
    val scrollBehavior = key(tab) { TopAppBarDefaults.exitUntilCollapsedScrollBehavior() }

    val night = LocalPalette.current == Palette.NIGHT
    val colors = MaterialTheme.colorScheme
    if (night) NightBackdrop(Modifier.fillMaxSize())
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = if (night) Color.Transparent else colors.background,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            LargeTopAppBar(
                title = { Text(if (tab == Tab.TODAY) greeting() else tab.title) },
                actions = {
                    IconButton(onClick = { pickPalette = true }) {
                        Icon(Icons.Outlined.Palette, contentDescription = "Colors")
                    }
                    if (tab == Tab.PROTECTION && !guardState().committed) {
                        IconButton(onClick = openGuide) {
                            Icon(Icons.Outlined.Checklist, contentDescription = "Setup guide")
                        }
                    }
                },
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.largeTopAppBarColors(
                    containerColor = if (night) Color.Transparent else colors.background,
                    scrolledContainerColor = if (night) Color(0xE60B0F26) else colors.background,
                ),
            )
        },
        bottomBar = {
            NavigationBar(containerColor = if (night) Color(0xF00D0C22) else colors.background, tonalElevation = 0.dp) {
                Tab.entries.forEach { t ->
                    NavigationBarItem(
                        selected = tab == t,
                        onClick = { tab = t },
                        icon = { Icon(if (tab == t) t.selectedIcon else t.icon, contentDescription = null) },
                        label = { Text(t.label) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = colors.primary, selectedTextColor = colors.primary,
                            indicatorColor = colors.primary.copy(alpha = 0.14f),
                            unselectedIconColor = colors.onSurfaceVariant, unselectedTextColor = colors.onSurfaceVariant,
                        ),
                    )
                }
            }
        },
    ) { padding ->
        AnimatedContent(
            targetState = tab to section,
            transitionSpec = { (fadeIn(tween(220, delayMillis = 60)) + scaleIn(tween(220), initialScale = 0.97f)) togetherWith fadeOut(tween(120)) },
            label = "tab",
        ) { (shownTab, shownSection) ->
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = padding.calculateTopPadding(),
                    bottom = padding.calculateBottomPadding() + 16.dp,
                ),
            ) {
                when (shownTab) {
                    Tab.TODAY -> todayScreen(refresh) { tab = Tab.PROTECTION; section = ProtectionSection.OVERVIEW }
                    Tab.PROGRESS -> progressScreen()
                    Tab.PROTECTION -> protectionScreen(actions, refresh, openGuide, shownSection) { section = it }
                }
            }
        }
    }
}

@Composable
private fun PinGate(onUnlock: () -> Unit) {
    val store = NoXXXApp.instance.store
    var pin by remember { mutableStateOf("") }
    var wrong by remember { mutableStateOf(false) }
    Scaffold { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(32.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            IconBadge(Icons.Filled.Lock, size = 72.dp)
            Text("NoXXX is locked", style = MaterialTheme.typography.headlineSmall)
            OutlinedTextField(
                value = pin,
                onValueChange = { pin = it; wrong = false },
                label = { Text("PIN") },
                singleLine = true,
                isError = wrong,
                supportingText = if (wrong) ({ Text("Wrong PIN") }) else null,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                modifier = Modifier.fillMaxWidth(),
            )
            Button(onClick = { if (store.checkPin(pin)) onUnlock() else wrong = true }, modifier = Modifier.fillMaxWidth()) {
                Text("Unlock")
            }
        }
    }
}

/** "Good morning" and so on, for the Today tab. */
private fun greeting(): String = when (java.time.LocalTime.now().hour) {
    in 4..11 -> "Good morning"
    in 12..16 -> "Good afternoon"
    else -> "Good evening"
}

@Composable
private fun PaletteDialog(onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val current = Appearance.palette.collectAsStateWithLifecycle().value
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Colors") },
        text = {
            Column {
                Palette.entries.forEach { p ->
                    Row(
                        Modifier.fillMaxWidth().selectable(selected = p == current, role = Role.RadioButton) {
                            Appearance.set(ctx, p)
                        }.padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = p == current, onClick = null)
                        Column(Modifier.padding(start = 12.dp)) {
                            Text(p.label, style = MaterialTheme.typography.bodyLarge)
                            Text(p.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
    )
}
