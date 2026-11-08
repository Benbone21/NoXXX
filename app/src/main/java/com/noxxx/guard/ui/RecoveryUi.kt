package com.noxxx.guard.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.SelfImprovement
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.noxxx.guard.NoXXXApp
import com.noxxx.guard.recovery.JournalEntry
import com.noxxx.guard.recovery.Milestones
import com.noxxx.guard.recovery.Passage
import com.noxxx.guard.recovery.RecoveryState
import com.noxxx.guard.recovery.Scriptures
import kotlinx.coroutines.delay
import java.text.DateFormat
import java.time.LocalDate
import java.util.Date

/** Opens a scripture or web link (Gospel Library opens scripture links when it's installed). */
fun openLink(context: Context, url: String) {
    runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

@Composable
fun recoveryState(): RecoveryState = NoXXXApp.instance.recovery.state.collectAsStateWithLifecycle().value

/** A passage with its reference and a link to read it in context. */
@Composable
fun PassageView(passage: Passage, onOpen: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("“${passage.text}”", style = MaterialTheme.typography.bodyLarge, fontStyle = FontStyle.Italic)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("— ${passage.ref}", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
            TextButton(onClick = { onOpen(passage.url) }) { Text("Read in Gospel Library") }
        }
    }
}

@Composable
fun DailyScriptureCard() {
    val ctx = LocalContext.current
    val list = Scriptures.passages.collectAsStateWithLifecycle().value
    val passage = Scriptures.forDay(LocalDate.now(), list) ?: return
    SectionCard("Today's scripture", icon = Icons.Filled.AutoStories) {
        PassageView(passage) { openLink(ctx, it) }
    }
}

/** A clear, always-visible way to get help in the moment. */
@Composable
fun StruggleCard() {
    var open by remember { mutableStateOf(false) }
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        ),
    ) {
        Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Icon(Icons.Filled.SelfImprovement, contentDescription = null, modifier = Modifier.size(36.dp))
            Column(Modifier.weight(1f)) {
                Text("Having a hard moment?", style = MaterialTheme.typography.titleMedium)
                Text("A verse, a minute of breathing and a few things to do instead.",
                    style = MaterialTheme.typography.bodyMedium)
            }
            Button(onClick = { open = true }) { Text("Help") }
        }
    }
    if (open) StruggleDialog { open = false }
}

/** The streak at a glance, for Today: days clean and progress to the next milestone. */
@Composable
fun StreakHero() {
    val state = guardState()
    val days = ((System.currentTimeMillis() - state.streakStartWallMs) / 86_400_000L).coerceAtLeast(0)
    val next = Milestones.next(days)
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
    ) {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("$days", style = MaterialTheme.typography.displayLarge)
                Text(if (days == 1L) "day clean" else "days clean", style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(bottom = 12.dp))
            }
            Milestones.reached(days)?.let { Text(it.title, style = MaterialTheme.typography.titleMedium) }
            if (next != null) {
                LinearProgressIndicator(progress = { Milestones.progress(days) }, modifier = Modifier.fillMaxWidth())
                val left = next.days - days
                Text("$left ${if (left == 1L) "day" else "days"} to ${next.title.lowercase()}", style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

/** Streak milestones, adapted from Quitter: the next one to aim for, and the whole timeline. */
@Composable
fun MilestoneSection(days: Long) {
    var showAll by remember { mutableStateOf(false) }
    val next = Milestones.next(days)
    val reached = Milestones.reached(days)
    if (reached != null) {
        Text(reached.title, style = MaterialTheme.typography.titleSmall)
        Text(reached.description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    if (next != null) {
        val left = next.days - days
        LinearProgressIndicator(progress = { Milestones.progress(days) }, modifier = Modifier.fillMaxWidth())
        Text("Next: ${next.title} in $left ${if (left == 1L) "day" else "days"}", style = MaterialTheme.typography.bodySmall)
    }
    TextButton(onClick = { showAll = !showAll }) { Text(if (showAll) "Hide milestones" else "All milestones") }
    if (showAll) {
        Milestones.ALL.forEach { m ->
            val done = days >= m.days
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(if (done) Icons.Filled.TaskAlt else Icons.Filled.Flag, contentDescription = if (done) "Reached" else null,
                    tint = if (done) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline)
                Column {
                    Text("Day ${m.days} · ${m.title}", style = MaterialTheme.typography.labelLarge)
                    Text(m.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
fun GoalsCard() {
    val store = NoXXXApp.instance.recovery
    val state = recoveryState()
    val today = LocalDate.now()
    var editing by remember { mutableStateOf(false) }
    var newGoal by remember { mutableStateOf("") }
    val doneCount = state.goals.count { state.isDone(it.id, today) }
    SectionCard("Today's goals", if (state.goals.isEmpty()) "Add something small you can do every day." else
        "$doneCount of ${state.goals.size} done", icon = Icons.Filled.TaskAlt) {
        state.goals.forEach { goal ->
            val streak = state.streak(goal.id, today)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = state.isDone(goal.id, today), onCheckedChange = { store.update { it.toggle(goal.id, today) } })
                Column(Modifier.weight(1f)) {
                    Text(goal.title, style = MaterialTheme.typography.bodyLarge)
                    if (streak > 1) Text("$streak days in a row", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary)
                }
                if (editing) IconButton(onClick = { store.update { it.removeGoal(goal.id) } }) {
                    Icon(Icons.Filled.Delete, contentDescription = "Remove ${goal.title}")
                }
            }
        }
        if (editing) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(newGoal, { newGoal = it }, Modifier.weight(1f), singleLine = true,
                    placeholder = { Text("New goal, like Go to the temple") })
                IconButton(onClick = {
                    store.update { it.addGoal(newGoal, "goal-${System.currentTimeMillis()}") }
                    newGoal = ""
                }) { Icon(Icons.Filled.Add, contentDescription = "Add goal") }
            }
        }
        TextButton(onClick = { editing = !editing }) { Text(if (editing) "Done editing" else "Edit goals") }
    }
}

@Composable
fun JournalCard() {
    val state = recoveryState()
    var writing by remember { mutableStateOf(false) }
    var showAll by remember { mutableStateOf(false) }
    val fmt = remember { DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT) }
    SectionCard("Journal", if (state.journal.isEmpty()) "Write how today went: what helped, what was hard." else null,
        icon = Icons.Filled.EditNote) {
        FilledTonalButton(onClick = { writing = true }) { Text("Write an entry") }
        state.journal.take(if (showAll) 50 else 3).forEach { e ->
            HorizontalDivider()
            Text("${fmt.format(Date(e.timeMs))} · ${MOODS[e.mood - 1]}" + (if (e.slipped) " · Slipped" else "") +
                (if (e.urge > 0) " · Urge ${e.urge}/5" else ""), style = MaterialTheme.typography.labelLarge)
            if (e.triggers.isNotEmpty()) Text(e.triggers.joinToString(), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (e.note.isNotBlank()) Text(e.note, style = MaterialTheme.typography.bodyMedium)
        }
        if (state.journal.size > 3) TextButton(onClick = { showAll = !showAll }) { Text(if (showAll) "Show less" else "Show more") }
    }
    if (writing) JournalDialog(slipped = false) { writing = false }
}

private val MOODS = listOf("Rough", "Low", "Okay", "Good", "Great")

/** Write a journal entry. After a slip, [slipped] frames it as noting what happened, not as failure. */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun JournalDialog(slipped: Boolean, onDone: () -> Unit) {
    var mood by remember { mutableIntStateOf(3) }
    var urge by remember { mutableIntStateOf(if (slipped) 4 else 0) }
    var triggers by remember { mutableStateOf(emptySet<String>()) }
    var note by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDone,
        title = { Text(if (slipped) "What happened?" else "Journal") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (slipped) Text("A slip isn't the end. Noting what led to it helps you see it coming next time.",
                    style = MaterialTheme.typography.bodyMedium)
                Text("Mood", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    MOODS.forEachIndexed { i, m -> FilterChip(mood == i + 1, { mood = i + 1 }, label = { Text(m) }) }
                }
                Text("Urge: ${if (urge == 0) "none" else "$urge of 5"}", style = MaterialTheme.typography.labelLarge)
                Slider(value = urge.toFloat(), onValueChange = { urge = it.toInt() }, valueRange = 0f..5f, steps = 4)
                Text("What was going on?", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    RecoveryState.TRIGGERS.forEach { t ->
                        FilterChip(t in triggers, { triggers = if (t in triggers) triggers - t else triggers + t }, label = { Text(t) })
                    }
                }
                OutlinedTextField(note, { note = it }, Modifier.fillMaxWidth(), minLines = 3,
                    placeholder = { Text(if (slipped) "What could you do differently next time?" else "What helped? What was hard?") })
            }
        },
        confirmButton = {
            TextButton(onClick = {
                NoXXXApp.instance.recovery.update {
                    it.addEntry(JournalEntry(System.currentTimeMillis(), mood, urge, triggers, note.trim(), slipped))
                }
                onDone()
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDone) { Text("Cancel") } },
    )
}

/** The Church Addiction Recovery Program's twelve key principles. */
private val ARP_PRINCIPLES = listOf(
    "Honesty", "Hope", "Trust in God", "Truth", "Confession", "Change of heart", "Humility",
    "Seeking forgiveness", "Restitution and reconciliation", "Daily accountability", "Personal revelation", "Service",
)

private const val ARP_URL = "https://addictionrecovery.churchofjesuschrist.org/"

private val INSTEAD = listOf(
    "Say a prayer, even a short one, right now.",
    "Leave the room: go outside, or somewhere with other people.",
    "Text or call someone you trust.",
    "Put the phone down and do something with your hands for ten minutes.",
    "Read the verse above slowly, then read the chapter it's from.",
)

/** Full-screen help for an urge: a verse, a breathing exercise, things to do, and the recovery principles. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StruggleDialog(onClose: () -> Unit) {
    val ctx = LocalContext.current
    var passage by remember { mutableStateOf(Scriptures.forStruggle()) }
    var logging by remember { mutableStateOf(false) }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            Column(
                Modifier.fillMaxSize().systemBarsPadding().verticalScroll(rememberScrollState()).padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("This will pass", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
                    IconButton(onClick = onClose) { Icon(Icons.Filled.Close, contentDescription = "Close") }
                }
                passage?.let { p ->
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                        Column(Modifier.padding(20.dp)) {
                            PassageView(p) { openLink(ctx, it) }
                            TextButton(onClick = { passage = Scriptures.forStruggle(p) }) { Text("Another verse") }
                        }
                    }
                }
                Breathing()
                Text("Instead, right now", style = MaterialTheme.typography.titleMedium)
                INSTEAD.forEach { Text("•  $it", style = MaterialTheme.typography.bodyLarge) }
                Text("Recovery principles", style = MaterialTheme.typography.titleMedium)
                Text("The twelve key principles of the Church's Addiction Recovery Program:",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    ARP_PRINCIPLES.forEachIndexed { i, s ->
                        Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                            Text("${i + 1}. $s", Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
                OutlinedButton(onClick = { openLink(ctx, ARP_URL) }, modifier = Modifier.fillMaxWidth()) {
                    Text("Addiction Recovery Program and meetings")
                }
                FilledTonalButton(onClick = { logging = true }, modifier = Modifier.fillMaxWidth()) { Text("Write about this urge") }
                Button(onClick = onClose, modifier = Modifier.fillMaxWidth()) { Text("I'm okay now") }
                Spacer(Modifier.size(8.dp))
            }
        }
    }
    if (logging) JournalDialog(slipped = false) { logging = false }
}

private enum class Breath(val label: String, val seconds: Int, val scale: Float) {
    IN("Breathe in", 4, 1f), HOLD("Hold", 4, 1f), OUT("Breathe out", 6, 0.55f)
}

/** A slow breathing guide: in for 4, hold for 4, out for 6. */
@Composable
private fun Breathing() {
    var phase by remember { mutableStateOf(Breath.OUT) }
    var running by remember { mutableStateOf(false) }
    var left by remember { mutableIntStateOf(0) }
    LaunchedEffect(running) {
        while (running) {
            for (p in Breath.entries) {
                phase = p
                for (s in p.seconds downTo 1) {
                    left = s
                    delay(1_000)
                }
            }
        }
    }
    val size by animateFloatAsState(
        targetValue = if (running) phase.scale else 0.55f,
        animationSpec = tween(durationMillis = if (running) phase.seconds * 1_000 else 300),
        label = "breath",
    )
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.size(160.dp), contentAlignment = Alignment.Center) {
            Box(Modifier.size(160.dp).scale(size).background(MaterialTheme.colorScheme.primaryContainer, CircleShape))
            Text(if (running) "${phase.label}\n$left" else "Breathe", textAlign = TextAlign.Center,
                style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
        }
        TextButton(onClick = { running = !running }) { Text(if (running) "Stop" else "Start a minute of breathing") }
    }
}
