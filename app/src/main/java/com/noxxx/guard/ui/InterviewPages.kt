package com.noxxx.guard.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Work
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import com.noxxx.guard.NoXXXApp
import com.noxxx.guard.monitor.EssentialApps
import com.noxxx.guard.recovery.Answers
import com.noxxx.guard.recovery.Attempts
import com.noxxx.guard.recovery.Duration
import com.noxxx.guard.recovery.Frequency
import com.noxxx.guard.recovery.GoalLibrary
import com.noxxx.guard.recovery.GoalSection
import com.noxxx.guard.recovery.Interview
import com.noxxx.guard.recovery.LastSlip
import com.noxxx.guard.recovery.LevelSettings
import com.noxxx.guard.recovery.ProtectionLevel
import com.noxxx.guard.recovery.Strictness

/*
 * Setup interview: a few private questions, then a suggested protection level, lockdown and goals.
 * Answers stay on the phone.
 */

private fun setAnswers(block: (Answers) -> Answers) =
    NoXXXApp.instance.recovery.update { it.copy(answers = block(it.answers ?: Answers())) }

/**
 * Fills in goals, the planned lockdown and protection settings from the answers. Safe to run again after
 * answers change: anything picked by hand on the goal and level pages is kept. Setup mode only.
 */
fun applyInterview(actions: PolicyActions, installed: Set<String>) {
    val recovery = NoXXXApp.instance.recovery
    val s = recovery.state.value
    val a = s.answers ?: Answers()
    val rec = Interview.recommend(a, installed)
    applyLevel(actions, Interview.bundle(s.setup.level ?: rec.level, a, installed).settings)
    recovery.update { Interview.recompute(it, rec) }
}

/**
 * Writes a level's settings into the policy. Unless [force]d, skips it when they match what was last written,
 * so fine-tuning on the protection page survives going back through the interview.
 */
private fun applyLevel(actions: PolicyActions, settings: LevelSettings, force: Boolean = false) {
    val recovery = NoXXXApp.instance.recovery
    val previous = recovery.state.value.setup.applied
    if (!force && settings == previous) return
    actions.change { Interview.applyTo(it, settings, previous) }
    recovery.update { it.copy(setup = it.setup.copy(applied = settings)) }
}

/** Starts protection, then any lockdown chosen during setup. */
fun startProtection() {
    val app = NoXXXApp.instance
    app.store.commit()
    val days = app.recovery.state.value.plannedLockdownDays
    if (days > 0) app.store.startLockdown(days * 24L * 60 * 60_000)
    app.recovery.update { it.copy(plannedLockdownDays = 0) }
}

/** One full-screen question: a large animated icon, the question, a hint, then big answer cards. */
@Composable
private fun Question(icon: ImageVector, question: String, hint: String?, content: @Composable ColumnScope.() -> Unit) {
    val pop = remember { Animatable(0.5f) }
    LaunchedEffect(Unit) { pop.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow)) }
    Column(Modifier.fillMaxWidth().padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Box(
            Modifier.size(84.dp).scale(pop.value).clip(CircleShape)
                .background(Brush.linearGradient(listOf(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.secondaryContainer))),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, contentDescription = null, Modifier.size(40.dp), tint = MaterialTheme.colorScheme.primary) }
        Text(question, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
        if (hint != null) Text(hint, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
    }
}

/** A large tappable answer that lights up when chosen. */
@Composable
private fun OptionCard(text: String, selected: Boolean, onClick: () -> Unit) {
    SelectableCard(selected, onClick) {
        Row(Modifier.padding(horizontal = 20.dp, vertical = 18.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(text, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
            SelectedMark(selected)
        }
    }
}

/** The animated surface behind answer and level cards. */
@Composable
private fun SelectableCard(selected: Boolean, onClick: () -> Unit, content: @Composable () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val container by animateColorAsState(if (selected) colors.primaryContainer else colors.surfaceContainerHigh, label = "option")
    val border by animateDpAsState(if (selected) 2.dp else 0.dp, label = "border")
    val scale by animateFloatAsState(if (selected) 1.02f else 1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy), label = "scale")
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().scale(scale),
        shape = MaterialTheme.shapes.large,
        color = container,
        border = if (selected) BorderStroke(border, colors.primary) else null,
        content = content,
    )
}

@Composable
private fun SelectedMark(selected: Boolean) {
    AnimatedVisibility(selected, enter = scaleIn() + fadeIn(), exit = scaleOut() + fadeOut()) {
        Icon(Icons.Filled.CheckCircle, contentDescription = "Selected", tint = MaterialTheme.colorScheme.primary)
    }
}

/** Single choice: picking an answer moves on to the next question after a beat. */
@Composable
private fun <T> SingleChoice(options: List<T>, selected: T?, label: (T) -> String, onSelect: (T) -> Unit, next: () -> Unit) {
    val scope = rememberCoroutineScope()
    options.forEach { o ->
        OptionCard(label(o), o == selected) {
            onSelect(o)
            scope.launch { delay(350); next() }
        }
    }
}

@Composable
private fun MultiChoice(options: List<String>, selected: Set<String>, onChange: (Set<String>) -> Unit) {
    options.forEach { o -> OptionCard(o, o in selected) { onChange(if (o in selected) selected - o else selected + o) } }
}

@Composable
fun DurationQuestion(next: () -> Unit) {
    val a = recoveryState().answers ?: Answers()
    Question(Icons.Filled.HourglassTop, "How long has this been a struggle?",
        "Private: answers never leave your phone. They help NoXXX suggest what fits you.") {
        SingleChoice(Duration.entries, a.duration, { it.label }, { v -> setAnswers { it.copy(duration = v) } }, next)
    }
}

@Composable
fun FrequencyQuestion(next: () -> Unit) {
    val a = recoveryState().answers ?: Answers()
    Question(Icons.Filled.CalendarMonth, "In the last month, how often?", "Honest answers get you the right amount of help.") {
        SingleChoice(Frequency.entries, a.frequency, { it.label }, { v -> setAnswers { it.copy(frequency = v) } }, next)
    }
}

@Composable
fun AttemptsQuestion(next: () -> Unit) {
    val a = recoveryState().answers ?: Answers()
    Question(Icons.Filled.Replay, "How have past attempts gone?", null) {
        SingleChoice(Attempts.entries, a.attempts, { it.label }, { v -> setAnswers { it.copy(attempts = v) } }, next)
    }
}

@Composable
fun LastSlipQuestion(next: () -> Unit) {
    val a = recoveryState().answers ?: Answers()
    Question(Icons.Filled.History, "When was the last time?", "No judgment here. It just helps pick a starting point.") {
        SingleChoice(LastSlip.entries, a.lastSlip, { it.label }, { v -> setAnswers { it.copy(lastSlip = v) } }, next)
    }
}

@Composable
fun HardTimesQuestion() {
    val a = recoveryState().answers ?: Answers()
    Question(Icons.Filled.Bedtime, "When is it hardest?", "Pick any that fit.") {
        MultiChoice(Interview.HARD_TIMES, a.hardTimes) { v -> setAnswers { it.copy(hardTimes = v) } }
    }
}

@Composable
fun SourcesQuestion() {
    val a = recoveryState().answers ?: Answers()
    Question(Icons.Filled.Psychology, "Where does it usually start?", "Pick any that fit.") {
        MultiChoice(Interview.SOURCES, a.sources) { v -> setAnswers { it.copy(sources = v) } }
    }
}

@Composable
fun SupportQuestion() {
    val a = recoveryState().answers ?: Answers()
    // Older answers only said yes or no; show what that was read as until it's answered again.
    val shown = a.support.ifEmpty { a.supporters }
    Question(Icons.Filled.Groups, "Who knows and supports you?",
        "Pick any that fit. Later you can let one of them hold a partner password.") {
        Interview.SUPPORT.forEach { o ->
            OptionCard(o, o in shown) {
                setAnswers { it.copy(support = Interview.toggleSupport(shown, o), legacyHasSupport = false) }
            }
        }
    }
}

@Composable
fun FaithQuestion() {
    val a = recoveryState().answers ?: Answers()
    Question(Icons.Filled.Favorite, "Which daily habits do you want to build?",
        "Recovery goes better with help from heaven. These shape your daily goals.") {
        MultiChoice(Interview.FAITH, a.faith) { v -> setAnswers { it.copy(faith = v) } }
    }
}

@Composable
fun StrictnessQuestion(next: () -> Unit) {
    val a = recoveryState().answers ?: Answers()
    Question(Icons.Filled.Lock, "How strict do you want it?", "This counts the most. You can change any setting before you start.") {
        SingleChoice(Strictness.entries, a.strictness, { it.label }, { v -> setAnswers { it.copy(strictness = v) } }, next)
    }
}

@Composable
fun PhoneQuestion() {
    val a = recoveryState().answers ?: Answers()
    Question(Icons.Filled.PhoneAndroid, "Anything about how you use your phone?",
        "So the suggested settings don't get in your way. Pick any that fit, or none.") {
        MultiChoice(Interview.PHONE, a.phone) { v -> setAnswers { it.copy(phone = v) } }
    }
}

@Composable
fun WorkAppsPage(actions: PolicyActions) {
    val ctx = LocalContext.current
    val essential = remember { EssentialApps.protectedSet(ctx) }
    val p = guardState().policy
    PageHeader(Icons.Filled.Work, "Apps you need",
        "Pick the apps you need for work or school. They can never be blocked, so protection can't lock you out of " +
            "them. You can change this later under Protection → Apps.") {
        AppPicker(p.neverBlockApps, checkingLoosens = true, protectedPkgs = essential, protectedLabel = "Always allowed") { pkg, on ->
            actions.change { if (on) it.copy(neverBlockApps = it.neverBlockApps + pkg) else it.copy(neverBlockApps = it.neverBlockApps - pkg) }
        }
    }
}

@Composable
fun GoalsPage() {
    val store = NoXXXApp.instance.recovery
    val state = recoveryState()
    val reasons = remember(state.answers) {
        Interview.suggestGoals(state.answers ?: Answers()).associate { it.goal.id to it.reason }
    }
    val checked = state.goals.map { it.id }.toSet()
    var newGoal by remember { mutableStateOf("") }
    PageHeader(Icons.Filled.TaskAlt, "Your daily goals",
        "A few are checked to fit your answers. Check off the ones you do on the Today tab each day. Change any, or add your own.") {
        Hint("${checked.size} checked. A handful is plenty to start; you can add more later.")
        GoalSection.entries.forEach { section ->
            Eyebrow(section.label)
            GoalLibrary.all.filter { it.section == section }.forEach { e ->
                GoalRow(e.goal.title, reasons[e.goal.id], e.goal.id in checked) { on ->
                    store.update { Interview.checkGoal(it, e.goal, on) }
                }
            }
        }
        val own = state.goals.filter { !GoalLibrary.isBuiltIn(it.id) }
        Eyebrow("Your own")
        own.forEach { g ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(g.title, Modifier.weight(1f).padding(start = 12.dp), style = MaterialTheme.typography.bodyLarge)
                IconButton(onClick = { store.update { it.removeGoal(g.id) } }) {
                    Icon(Icons.Filled.Delete, contentDescription = "Remove ${g.title}")
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(newGoal, { newGoal = it }, Modifier.weight(1f), singleLine = true,
                placeholder = { Text("Add your own, like Call my brother") })
            IconButton(onClick = {
                store.update { it.addGoal(newGoal, "goal-${System.currentTimeMillis()}") }
                newGoal = ""
            }) { Icon(Icons.Filled.Add, contentDescription = "Add goal") }
        }
    }
}

@Composable
private fun GoalRow(title: String, reason: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium)
            .toggleable(value = checked, role = Role.Checkbox, onValueChange = onChange)
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = null, modifier = Modifier.padding(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (reason != null) Hint(reason)
        }
    }
}

private val LOCKDOWN_OPTIONS = listOf(0, 7, 14, 30, 90)

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun LevelPage(actions: PolicyActions, installed: Set<String>) {
    val store = NoXXXApp.instance.recovery
    val state = recoveryState()
    val a = state.answers ?: Answers()
    val rec = remember(a, installed) { Interview.recommend(a, installed) }
    val chosen = state.setup.level ?: rec.level
    val notes = remember(chosen, a, installed) { Interview.bundle(chosen, a, installed).notes }
    PageHeader(Icons.Filled.Security, "Your protection level",
        "Suggested from your answers. Pick the one that feels right; you can fine-tune each setting next.") {
        ProtectionLevel.entries.forEach { level ->
            LevelCard(level, selected = level == chosen, suggested = level == rec.level, why = rec.why) {
                store.update { it.copy(setup = it.setup.copy(level = level, lockdownDays = null), plannedLockdownDays = level.lockdownDays) }
                applyLevel(actions, Interview.bundle(level, a, installed).settings, force = true)
            }
        }
        if (notes.isNotEmpty()) {
            Text("Fitted to your phone", style = MaterialTheme.typography.titleSmall)
            notes.forEach { Text("•  $it", style = MaterialTheme.typography.bodyMedium) }
        }
        HorizontalDivider()
        Text("Lockdown", style = MaterialTheme.typography.titleSmall)
        Hint("A lockdown freezes every setting for a set time once you start protection, so nothing can be loosened " +
            "on a hard day. It can be extended but not shortened.")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LOCKDOWN_OPTIONS.forEach { d ->
                FilterChip(
                    selected = state.plannedLockdownDays == d,
                    onClick = { store.update { it.copy(setup = it.setup.copy(lockdownDays = d), plannedLockdownDays = d) } },
                    label = { Text((if (d == 0) "No lockdown" else "$d days") + if (d == chosen.lockdownDays) " · suggested" else "") },
                )
            }
        }
    }
}

@Composable
private fun LevelCard(level: ProtectionLevel, selected: Boolean, suggested: Boolean, why: List<String>, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    SelectableCard(selected, onClick) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (suggested) {
                Text("SUGGESTED FOR YOU", style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 1.8.sp), color = colors.primary)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(level.label, Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                SelectedMark(selected)
            }
            Text(level.description, style = MaterialTheme.typography.bodyMedium)
            Text(level.summary, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
            if (suggested && why.isNotEmpty()) {
                Column(Modifier.padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    why.forEach { Text("•  $it", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant) }
                }
            }
        }
    }
}
