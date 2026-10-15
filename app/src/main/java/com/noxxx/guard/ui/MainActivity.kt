package com.noxxx.guard.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
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
    HOME("Home", "NoXXX", Icons.Filled.Shield, Icons.Outlined.Shield),
    WEB("Websites", "Websites", Icons.Filled.Language, Icons.Outlined.Language),
    APPS("Apps", "Apps", Icons.Filled.Apps, Icons.Outlined.Apps),
    IMAGES("Images", "Image detection", Icons.Filled.Image, Icons.Outlined.Image),
    LOCK("Lock", "Lock & cool-off", Icons.Filled.Lock, Icons.Outlined.Lock),
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
    var tab by rememberSaveable { mutableStateOf(Tab.HOME) }
    val scrollBehavior = key(tab) { TopAppBarDefaults.exitUntilCollapsedScrollBehavior() }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            LargeTopAppBar(
                title = { Text(tab.title) },
                actions = {
                    if (tab == Tab.HOME && !guardState().committed) {
                        IconButton(onClick = openGuide) {
                            Icon(Icons.Outlined.Checklist, contentDescription = "Setup guide")
                        }
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
        bottomBar = {
            NavigationBar {
                Tab.entries.forEach { t ->
                    NavigationBarItem(
                        selected = tab == t,
                        onClick = { tab = t },
                        icon = { Icon(if (tab == t) t.selectedIcon else t.icon, contentDescription = null) },
                        label = { Text(t.label) },
                    )
                }
            }
        },
    ) { padding ->
        key(tab) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = padding.calculateTopPadding(),
                    bottom = padding.calculateBottomPadding() + 16.dp,
                ),
            ) {
                when (tab) {
                    Tab.HOME -> homeScreen(actions, refresh, openGuide)
                    Tab.WEB -> webScreen(actions, refresh)
                    Tab.APPS -> appsScreen(actions)
                    Tab.IMAGES -> imagesScreen(actions)
                    Tab.LOCK -> lockScreen(actions)
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
