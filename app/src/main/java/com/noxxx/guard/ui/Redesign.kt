package com.noxxx.guard.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.runtime.key
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.noxxx.guard.NoXXXApp
import com.noxxx.guard.policy.formatDuration
import com.noxxx.guard.policy.formatMinutes
import com.noxxx.guard.recovery.Milestones
import com.noxxx.guard.recovery.Scriptures
import com.noxxx.guard.recovery.StreakTime
import kotlinx.coroutines.delay
import java.text.DateFormat
import java.time.LocalDate
import java.util.Date
import kotlin.random.Random

/* The Temple screens: Today's ring clock, Progress, and Protection's overview. */

private val MOODS = listOf("Rough", "Low", "Okay", "Good", "Great")

/** Small uppercase section label, like "MILESTONES". */
@Composable
private fun Eyebrow(text: String) {
    Text(text.uppercase(), style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 1.8.sp),
        color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** A quiet bordered panel, the base card of the Temple look. */
@Composable
fun Panel(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Card(
        modifier = modifier.fillMaxWidth().padding(vertical = 6.dp),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) { content() }
}

/**
 * Digits that roll down when they change, each one on its own, so only the digits that change move.
 * Clipped to their own box, which reads like a mechanical counter.
 */
@Composable
private fun RollingDigits(text: String, style: androidx.compose.ui.text.TextStyle, color: Color = Color.Unspecified) {
    Row {
        text.forEachIndexed { i, ch ->
            key(i) {
                AnimatedContent(
                    targetState = ch,
                    transitionSpec = {
                        (slideInVertically(tween(520, easing = FastOutSlowInEasing)) { -it } + fadeIn(tween(380))) togetherWith
                            (slideOutVertically(tween(520, easing = FastOutSlowInEasing)) { it } + fadeOut(tween(380))) using
                            SizeTransform(clip = true)
                    },
                    label = "digit",
                ) { c -> Text(c.toString(), style = style.copy(fontFeatureSettings = "lnum, tnum"), color = color) }
            }
        }
    }
}

@Composable
private fun rememberNow(): Long {
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1_000 - now % 1_000)
        }
    }
    return now
}

/** A progress ring: faint track, gold arc from the top. */
@Composable
private fun Ring(progress: Float, size: Dp, stroke: Dp, content: @Composable () -> Unit) {
    val track = MaterialTheme.colorScheme.outlineVariant
    val arc = MaterialTheme.colorScheme.primary
    val animated by animateFloatAsState(progress, tween(900), label = "ring")
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val w = stroke.toPx()
            val inset = w / 2
            val arcSize = Size(this.size.width - w, this.size.height - w)
            drawArc(track, 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(w))
            drawArc(arc, -90f, 360f * animated, false, Offset(inset, inset), arcSize, style = Stroke(w, cap = StrokeCap.Round))
        }
        content()
    }
}

/** Today's streak: a gold ring toward the next milestone, a live clock, bigger stats, and reset. */
@Composable
fun RingClock() {
    val state = guardState()
    val now = rememberNow()
    val t = StreakTime.between(state.streakStartWallMs, now)
    val next = Milestones.next(t.days)
    val colors = MaterialTheme.colorScheme
    val started = remember(state.streakStartWallMs) {
        DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(state.streakStartWallMs)).substringBeforeLast(',')
    }
    val digits = MaterialTheme.typography.headlineLarge.copy(
        fontFamily = MaterialTheme.typography.bodyLarge.fontFamily, fontWeight = FontWeight.Medium, fontSize = 30.sp)
    val unit = MaterialTheme.typography.titleMedium.copy(fontFamily = MaterialTheme.typography.bodyLarge.fontFamily)
    Panel {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                Ring(Milestones.progress(t.days), 120.dp, 5.dp) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        RollingDigits(t.days.toString(), MaterialTheme.typography.displayMedium.copy(fontWeight = FontWeight.SemiBold), colors.primary)
                        Text(if (t.days == 1L) "DAY" else "DAYS", style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.8.sp),
                            color = colors.onSurfaceVariant)
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        listOf("%02d".format(t.hours) to "h", "%02d".format(t.minutes) to "m", "%02d".format(t.seconds) to "s")
                            .forEachIndexed { i, (n, u) ->
                                if (i > 0) Spacer(Modifier.width(6.dp))
                                RollingDigits(n, digits)
                                Text(u, style = unit, color = colors.onSurfaceVariant, modifier = Modifier.padding(start = 1.dp, bottom = 4.dp))
                            }
                    }
                    next?.let {
                        val left = it.days - t.days
                        Text("$left ${if (left == 1L) "day" else "days"} to ${it.title.lowercase()}",
                            style = MaterialTheme.typography.bodyMedium, color = colors.secondary)
                    }
                }
            }
            HorizontalDivider(color = colors.outlineVariant)
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                listOf(
                    "${t.weeks}" to "${if (t.weeks == 1L) "week" else "weeks"} + ${t.extraDays} ${if (t.extraDays == 1) "day" else "days"}",
                    "%,d".format(t.totalHours) to "hours total",
                    started to "started",
                ).forEachIndexed { i, (v, l) ->
                    if (i > 0) VerticalDivider(Modifier.fillMaxHeight(), color = colors.outlineVariant)
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(v, style = MaterialTheme.typography.headlineMedium, maxLines = 1)
                        Text(l, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant, textAlign = TextAlign.Center)
                    }
                }
            }
            ResetStreakButton(Modifier.align(Alignment.End))
        }
    }
}

/** "Reset streak", which asks first, then offers a journal entry about what happened. */
@Composable
fun ResetStreakButton(modifier: Modifier = Modifier) {
    var confirm by remember { mutableStateOf(false) }
    var journal by remember { mutableStateOf(false) }
    TextButton(onClick = { confirm = true }, modifier = modifier) { Text("Reset streak") }
    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("Reset your streak?") },
            text = { Text("Your streak goes back to zero. A slip doesn't erase your progress, and your best streak is kept.") },
            confirmButton = {
                TextButton(onClick = { NoXXXApp.instance.store.resetStreak(); confirm = false; journal = true }) { Text("Reset") }
            },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("Keep my streak") } },
        )
    }
    if (journal) JournalDialog(slipped = true) { journal = false }
}

/** Today's verse: a large gold quote mark and italic serif, open on the page. */
@Composable
fun TodayScripture() {
    val ctx = LocalContext.current
    val list = Scriptures.passages.collectAsStateWithLifecycle().value
    val passage = Scriptures.forDay(LocalDate.now(), list) ?: return
    val colors = MaterialTheme.colorScheme
    Box(Modifier.padding(vertical = 6.dp)) {
        Column(Modifier.padding(horizontal = 4.dp, vertical = 8.dp)) {
            Text("“", style = MaterialTheme.typography.displayMedium, color = colors.primary.copy(alpha = 0.55f),
                modifier = Modifier.height(36.dp))
            Text(passage.text, style = MaterialTheme.typography.headlineSmall.copy(fontStyle = FontStyle.Italic,
                fontWeight = FontWeight.Medium, lineHeight = 30.sp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(passage.ref, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant, modifier = Modifier.weight(1f))
                TextButton(onClick = { openLink(ctx, passage.url) }) { Text("Read in Gospel Library", color = colors.secondary) }
            }
        }
    }
}

// --- Progress: Ledger numbers, the weekly chart, a milestone timeline and the journal ---

/** Three numbers across: the current streak, the best, and everything blocked. Plus reset. */
@Composable
fun ProgressSummary() {
    val state = guardState()
    val stats = NoXXXApp.instance.blockLog.stats.collectAsStateWithLifecycle().value
    val now = rememberNow()
    val days = StreakTime.between(state.streakStartWallMs, now).days
    val best = maxOf(state.bestStreakMs / 86_400_000L, days)
    Column(Modifier.padding(vertical = 6.dp)) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min).padding(vertical = 14.dp)) {
            listOf(Triple("$days", "days now", true), Triple("$best", "best", false), Triple("%,d".format(stats.total), "blocked", false))
                .forEachIndexed { i, (v, l, gold) ->
                    if (i > 0) VerticalDivider(Modifier.fillMaxHeight(), color = MaterialTheme.colorScheme.outlineVariant)
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(v, style = MaterialTheme.typography.displaySmall,
                            color = if (gold) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                        Text(l, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        ResetStreakButton(Modifier.align(Alignment.End))
    }
}

/** Blocks per day this week, with a chip per kind. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WeekBlocksCard() {
    val stats = NoXXXApp.instance.blockLog.stats.collectAsStateWithLifecycle().value
    val events = NoXXXApp.instance.blockLog.events.collectAsStateWithLifecycle().value
    val days = stats.lastDays(7)
    val since = System.currentTimeMillis() - 7 * 86_400_000L
    val kinds = events.filter { it.timeMs >= since }.groupingBy { it.kind }.eachCount().entries.sortedByDescending { it.value }
    Panel {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text("Blocked this week", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Text("${days.sumOf { it.second }}", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
            }
            WeekChart(days)
            if (kinds.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    kinds.forEach { (k, n) ->
                        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                            Text("${kindLabel(k)} $n", Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }
        }
    }
}

/** Milestones as a vertical timeline: the last reached, the next, and the one after; or all of them. */
@Composable
fun MilestoneTimeline() {
    val state = guardState()
    val now = rememberNow()
    val days = StreakTime.between(state.streakStartWallMs, now).days
    var all by remember { mutableStateOf(false) }
    val list = Milestones.ALL
    val nextIndex = list.indexOfFirst { it.days > days }.let { if (it < 0) list.size else it }
    val shown = if (all) list else list.subList((nextIndex - 1).coerceAtLeast(0), (nextIndex + 2).coerceAtMost(list.size))
    val colors = MaterialTheme.colorScheme
    Column(Modifier.padding(vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) { Eyebrow("Milestones") }
            TextButton(onClick = { all = !all }) { Text(if (all) "Show less" else "All") }
        }
        shown.forEachIndexed { i, m ->
            val reached = days >= m.days
            val isNext = list.indexOf(m) == nextIndex
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Column(Modifier.width(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Spacer(Modifier.height(6.dp))
                    Box(
                        Modifier.size(12.dp).clip(CircleShape)
                            .then(if (reached) Modifier.background(colors.primary) else Modifier)
                            .border(if (isNext) 2.dp else 1.dp, if (reached || isNext) colors.primary else colors.outline, CircleShape)
                    )
                    if (i < shown.lastIndex) Box(Modifier.width(1.dp).height(40.dp).background(if (reached) colors.primary else colors.outlineVariant))
                }
                Column(Modifier.padding(bottom = 8.dp)) {
                    Text(m.title, style = if (isNext) MaterialTheme.typography.titleLarge else MaterialTheme.typography.bodyLarge,
                        color = when { isNext -> colors.primary; reached -> colors.onSurface; else -> colors.onSurfaceVariant })
                    Text(when {
                        reached -> "Day ${m.days} · reached"
                        isNext -> "Day ${m.days} · in ${m.days - days} ${if (m.days - days == 1L) "day" else "days"}"
                        else -> "Day ${m.days}"
                    }, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                }
            }
        }
    }
}

/** The journal as a ledger: date and mood above an italic note, newest first. */
@Composable
fun JournalLedger() {
    val state = recoveryState()
    var writing by remember { mutableStateOf(false) }
    var more by remember { mutableStateOf(false) }
    val fmt = remember { DateFormat.getDateInstance(DateFormat.MEDIUM) }
    Column(Modifier.padding(vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) { Eyebrow("Journal") }
            TextButton(onClick = { writing = true }) { Text("Write") }
        }
        if (state.journal.isEmpty()) {
            Text("Write how today went: what helped, what was hard.", style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        state.journal.take(if (more) 50 else 3).forEach { e ->
            Column(Modifier.padding(vertical = 10.dp)) {
                Text(listOfNotNull(fmt.format(Date(e.timeMs)), MOODS[e.mood - 1], "Slipped".takeIf { e.slipped },
                    "urge ${e.urge} of 5".takeIf { e.urge > 0 }).joinToString(" · ") +
                    (if (e.triggers.isNotEmpty()) " · " + e.triggers.joinToString() else ""),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (e.note.isNotBlank()) {
                    Text(e.note, style = MaterialTheme.typography.titleLarge.copy(fontStyle = FontStyle.Italic, fontWeight = FontWeight.Medium))
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
        if (state.journal.size > 3) TextButton(onClick = { more = !more }) { Text(if (more) "Show less" else "Show more") }
    }
    if (writing) JournalDialog(slipped = false) { writing = false }
}

// --- Protection: a status list ---

/** "You're protected" with each protection as a row. Only shown when everything is running. */
@Composable
fun ProtectedSeal(refresh: Int) {
    val state = guardState()
    val status = rememberSetupStatus(refresh)
    val p = state.policy
    val colors = MaterialTheme.colorScheme
    Column(Modifier.padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(Modifier.size(52.dp).border(1.dp, colors.primary, CircleShape), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.VerifiedUser, contentDescription = null, tint = colors.primary)
            }
            Column {
                Text("You're protected", style = MaterialTheme.typography.headlineSmall)
                Text("Everything is running", style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
            }
        }
        val rows = listOf(
            "Guard service" to if (status.accessibility) "On" else "Off",
            "Family filter" to when {
                !p.requirePrivateDns -> "Not required"
                status.dns.filtering -> status.dns.host?.substringBefore('.')?.replaceFirstChar { it.uppercase() } ?: "On"
                else -> "Off"
            },
            "Uninstall protection" to if (status.admin) "On" else "Off",
            "Image detection" to if (p.imageScan) p.sensitivity.name.lowercase().replaceFirstChar { it.uppercase() } else "Off",
            "Battery exemption" to if (status.battery) "On" else "Off",
        )
        Column {
            rows.forEachIndexed { i, (label, value) ->
                Row(Modifier.fillMaxWidth().padding(vertical = 11.dp)) {
                    Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    Text(value, style = MaterialTheme.typography.bodyLarge,
                        color = if (value == "Off") colors.error else colors.primary)
                }
                if (i < rows.lastIndex) HorizontalDivider(color = colors.outlineVariant)
            }
        }
        // Optional protections that are off get their turn-on button right here.
        if (!status.admin) AdminSetup(status)
        if (!status.battery) BatterySetup(status, state.committed)
    }
}

/** Websites, Apps, Images and Lock as rows that open each section. */
@Composable
fun SectionRows(onSection: (ProtectionSection) -> Unit) {
    val state = guardState()
    val p = state.policy
    val summaries = mapOf(
        ProtectionSection.WEB to "%,d sites".format(NoXXXApp.instance.blocklist.domainCount),
        ProtectionSection.APPS to "${p.blockedApps.size} blocked" + if (p.ignoredApps.isEmpty()) "" else ", ${p.ignoredApps.size} ignored",
        ProtectionSection.IMAGES to if (!p.imageScan) "Off" else {
            val level = p.sensitivity.name.lowercase().replaceFirstChar { it.uppercase() }
            val skipped = p.imageScanExemptApps.size
            if (skipped == 0) level else "$level, $skipped ${if (skipped == 1) "app" else "apps"} skipped"
        },
        ProtectionSection.LOCK to when {
            state.lockdownRemainingMs > 0 -> "Lockdown, ${formatDuration(state.lockdownRemainingMs)} left"
            state.committed -> "${formatMinutes(p.coolOffMinutes)} cool-off"
            else -> "Setup mode"
        },
    )
    Panel {
        Column(Modifier.padding(horizontal = 18.dp)) {
            summaries.entries.forEachIndexed { i, (s, summary) ->
                Row(
                    Modifier.fillMaxWidth().clickable { onSection(s) }.padding(vertical = 15.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(if (s == ProtectionSection.LOCK) "Lock and lockdown" else s.label, style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f))
                    Text(summary, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (i < summaries.size - 1) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
    }
}

/** Back to the Protection overview from a section. */
@Composable
fun BackRow(onBack: () -> Unit) {
    TextButton(onClick = onBack) {
        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text("Protection")
    }
}
