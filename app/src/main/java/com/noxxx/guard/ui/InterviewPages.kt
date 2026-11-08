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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
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
    ) {
        Row(Modifier.padding(horizontal = 20.dp, vertical = 18.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(text, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
            AnimatedVisibility(selected, enter = scaleIn() + fadeIn(), exit = scaleOut() + fadeOut()) {
                Icon(Icons.Filled.CheckCircle, contentDescription = "Selected", tint = colors.primary)
            }
        }
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
fun SupportQuestion(next: () -> Unit) {
    val a = recoveryState().answers ?: Answers()
    Question(Icons.Filled.Person, "Is there someone you trust to hold a partner password?",
        "A parent, spouse, bishop or close friend. They can approve a change or end a lockdown early.") {
        SingleChoice(listOf(true, false), a.hasSupport, { if (it) "Yes, I have someone" else "Not right now" },
            { v -> setAnswers { it.copy(hasSupport = v) } }, next)
    }
}

@Composable
fun FaithQuestion() {
    val a = recoveryState().answers ?: Answers()
    rememberInstalledApps() // start loading, so suggested app blocks are ready on the next page
    Question(Icons.Filled.Favorite, "Which daily habits do you want to build?",
        "Recovery goes better with help from heaven. These become your daily goals.") {
        MultiChoice(Interview.FAITH, a.faith) { v -> setAnswers { it.copy(faith = v) } }
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
