package com.noxxx.guard.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.geometry.Offset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.fadeOut
import androidx.compose.animation.fadeIn
import androidx.compose.animation.togetherWith
import androidx.compose.animation.AnimatedContent
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
import androidx.compose.foundation.layout.height
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
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
import androidx.compose.ui.text.font.FontWeight
import com.noxxx.guard.recovery.StreakTime
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

/** A clear, always-visible way to get help in the moment. */
@Composable
fun StruggleCard() {
    var open by remember { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme
    Button(
        onClick = { open = true },
        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp).height(54.dp),
        shape = CircleShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = colors.secondary,
            contentColor = colors.onSecondary,
        ),
    ) {
        Icon(Icons.Filled.SelfImprovement, contentDescription = null)
        Spacer(Modifier.size(10.dp))
        Text("Having a hard moment?", style = MaterialTheme.typography.titleMedium)
    }
    if (open) StruggleDialog { open = false }
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
        if (doneCount == state.goals.size) "All done today. Well done!" else "$doneCount of ${state.goals.size} done",
        icon = Icons.Filled.TaskAlt) {
        if (state.goals.isNotEmpty()) {
            val goalProgress by animateFloatAsState(doneCount.toFloat() / state.goals.size, tween(500), label = "goals")
            LinearProgressIndicator(
                progress = { goalProgress },
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.secondary,
                trackColor = MaterialTheme.colorScheme.secondaryContainer,
            )
        }
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
