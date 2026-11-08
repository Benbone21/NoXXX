package com.noxxx.guard.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.LockClock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import com.noxxx.guard.NoXXXApp
import com.noxxx.guard.recovery.Answers
import com.noxxx.guard.recovery.Attempts
import com.noxxx.guard.recovery.Duration
import com.noxxx.guard.recovery.Frequency
import com.noxxx.guard.recovery.Interview

/*
 * Setup interview: a few private questions, then recommended goals, lockdown and protection settings.
 * Answers stay on the phone.
 */

private fun answers() = NoXXXApp.instance.recovery.state.value.answers ?: Answers()

private fun setAnswers(block: (Answers) -> Answers) =
    NoXXXApp.instance.recovery.update { it.copy(answers = block(it.answers ?: Answers())) }

/** Fills in goals, the planned lockdown and protection settings from the answers. Setup mode only. */
fun applyInterview(actions: PolicyActions, installed: Set<String>) {
    val rec = Interview.recommend(answers(), installed)
    NoXXXApp.instance.recovery.update { it.copy(goals = rec.goals, plannedLockdownDays = rec.lockdownDays) }
    actions.change { Interview.applyTo(it, rec) }
}

/** Starts protection, then any lockdown chosen during setup. */
fun startProtection() {
    val app = NoXXXApp.instance
    app.store.commit()
    val days = app.recovery.state.value.plannedLockdownDays
    if (days > 0) app.store.startLockdown(days * 24L * 60 * 60_000)
    app.recovery.update { it.copy(plannedLockdownDays = 0) }
}

@Composable
private fun <T> Choices(options: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit) {
    Column {
        options.forEach { o ->
            Row(
                Modifier.fillMaxWidth().selectable(selected = o == selected, role = Role.RadioButton) { onSelect(o) }
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = o == selected, onClick = null)
                Text(label(o), Modifier.padding(start = 12.dp), style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun MultiChips(options: List<String>, selected: Set<String>, onChange: (Set<String>) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { o ->
            FilterChip(o in selected, { onChange(if (o in selected) selected - o else selected + o) }, label = { Text(o) })
        }
    }
}

@Composable
fun AboutYouPage() {
    val a = recoveryState().answers ?: Answers()
    PageHeader(Icons.Filled.Person, "A little about you",
        "A few private questions so NoXXX can suggest settings and goals that fit. Answers never leave your phone.") {
        Text("How long has this been a struggle?", style = MaterialTheme.typography.titleSmall)
        Choices(Duration.entries, a.duration, { it.label }) { v -> setAnswers { it.copy(duration = v) } }
        Text("In the last month, how often?", style = MaterialTheme.typography.titleSmall)
        Choices(Frequency.entries, a.frequency, { it.label }) { v -> setAnswers { it.copy(frequency = v) } }
        Text("How have past attempts gone?", style = MaterialTheme.typography.titleSmall)
        Choices(Attempts.entries, a.attempts, { it.label }) { v -> setAnswers { it.copy(attempts = v) } }
    }
}

@Composable
fun TriggersPage() {
    val a = recoveryState().answers ?: Answers()
    PageHeader(Icons.Filled.Psychology, "What sets it off",
        "Knowing your patterns is half the battle. Pick any that fit.") {
        Text("When is it hardest?", style = MaterialTheme.typography.titleSmall)
        MultiChips(Interview.HARD_TIMES, a.hardTimes) { v -> setAnswers { it.copy(hardTimes = v) } }
        Text("Where does it usually start?", style = MaterialTheme.typography.titleSmall)
        MultiChips(Interview.SOURCES, a.sources) { v -> setAnswers { it.copy(sources = v) } }
    }
}

@Composable
fun FaithPage() {
    val a = recoveryState().answers ?: Answers()
    rememberInstalledApps() // start loading, so suggested app blocks are ready on the next page
    PageHeader(Icons.Filled.Favorite, "Support and faith",
        "Recovery goes better with help from heaven and from people who love you.") {
        Text("Is there someone you trust to hold a partner password?", style = MaterialTheme.typography.titleSmall)
        Hint("A parent, spouse, bishop or close friend. They can approve a change or end a lockdown early.")
        Choices(listOf(true, false), a.hasSupport, { if (it) "Yes" else "Not right now" }) { v -> setAnswers { it.copy(hasSupport = v) } }
        Text("Which daily habits do you want to build?", style = MaterialTheme.typography.titleSmall)
        MultiChips(Interview.FAITH, a.faith) { v -> setAnswers { it.copy(faith = v) } }
    }
}

@Composable
fun GoalsPage() {
    val store = NoXXXApp.instance.recovery
    val state = recoveryState()
    var newGoal by remember { mutableStateOf("") }
    PageHeader(Icons.Filled.TaskAlt, "Your daily goals",
        "Suggested from your answers. Check them off on the Today tab each day. Remove any, or add your own.") {
        state.goals.forEach { g ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(g.title, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                IconButton(onClick = { store.update { it.removeGoal(g.id) } }) {
                    Icon(Icons.Filled.Delete, contentDescription = "Remove ${g.title}")
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(newGoal, { newGoal = it }, Modifier.weight(1f), singleLine = true,
                placeholder = { Text("Add a goal, like Go to the temple") })
            IconButton(onClick = {
                store.update { it.addGoal(newGoal, "goal-${System.currentTimeMillis()}") }
                newGoal = ""
            }) { Icon(Icons.Filled.Add, contentDescription = "Add goal") }
        }
    }
}

private val LOCKDOWN_OPTIONS = listOf(0, 7, 14, 30, 90)

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun LockdownPlanPage() {
    val store = NoXXXApp.instance.recovery
    val state = recoveryState()
    val rec = remember(state.answers) { Interview.recommend(state.answers ?: Answers(), emptySet()) }
    PageHeader(Icons.Filled.LockClock, "Start with a lockdown?",
        "A lockdown freezes every setting for a set time once you start protection, so nothing can be loosened " +
            "on a hard day. It can be extended but not shortened.") {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LOCKDOWN_OPTIONS.forEach { d ->
                FilterChip(
                    selected = state.plannedLockdownDays == d,
                    onClick = { store.update { it.copy(plannedLockdownDays = d) } },
                    label = { Text((if (d == 0) "No lockdown" else "$d days") + if (d == rec.lockdownDays) " · suggested" else "") },
                )
            }
        }
        Text("Why these suggestions", style = MaterialTheme.typography.titleSmall)
        rec.why.forEach { Text("•  $it", style = MaterialTheme.typography.bodyMedium) }
        Hint("Next, the recommended protection settings are already filled in. Look them over and change anything.")
    }
}
