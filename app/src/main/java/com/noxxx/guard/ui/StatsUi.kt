package com.noxxx.guard.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.noxxx.guard.NoXXXApp
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.min

private const val DAY_MS = 86_400_000L

fun LazyListScope.statsSection() {
    item { StreakCard() }
    item { TotalsRow() }
    item { WeekCard() }
    item { ByTypeCard() }
}

private fun days(ms: Long) = (ms / DAY_MS).coerceAtLeast(0)

private fun dayWord(n: Long) = if (n == 1L) "day" else "days"

@Composable
private fun StreakCard() {
    val state = guardState()
    var confirm by remember { mutableStateOf(false) }
    val current = System.currentTimeMillis() - state.streakStartWallMs
    val currentDays = days(current)
    val bestDays = days(maxOf(state.bestStreakMs, current))

    SectionCard("Streak", icon = Icons.Filled.LocalFireDepartment) {
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(currentDays.toString(), style = MaterialTheme.typography.displayMedium, color = MaterialTheme.colorScheme.primary)
            Text("${dayWord(currentDays)} clean", style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 10.dp))
        }
        Text("Best: $bestDays ${dayWord(bestDays)}", style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        TextButton(onClick = { confirm = true }) { Text("I slipped, reset streak") }
    }

    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("Reset your streak?") },
            text = { Text("A slip doesn't erase your progress, and your best streak is kept. Note what triggered it, then start again.") },
            confirmButton = { TextButton(onClick = { NoXXXApp.instance.store.resetStreak(); confirm = false }) { Text("Reset") } },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun TotalsRow() {
    val stats = NoXXXApp.instance.blockLog.stats.collectAsStateWithLifecycle().value
    val week = stats.lastDays(7).sumOf { it.second }
    Row(
        Modifier.fillMaxWidth().height(IntrinsicSize.Min).padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        StatTile("Today", stats.on(LocalDate.now()).toString(), Modifier.weight(1f))
        StatTile("This week", "%,d".format(week), Modifier.weight(1f))
        StatTile("All time", "%,d".format(stats.total), Modifier.weight(1f))
    }
}

@Composable
private fun StatTile(label: String, value: String, modifier: Modifier) {
    Card(modifier = modifier.fillMaxHeight(), shape = MaterialTheme.shapes.large) {
        Column(Modifier.padding(16.dp)) {
            Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.headlineMedium)
            Text("blocked", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun WeekCard() {
    val stats = NoXXXApp.instance.blockLog.stats.collectAsStateWithLifecycle().value
    val days = stats.lastDays(7)
    SectionCard("Blocks, last 7 days", "Tap a day to see its count.", icon = Icons.Filled.BarChart) {
        if (days.all { it.second == 0 }) {
            Hint("Nothing blocked this week.")
        }
        WeekChart(days)
    }
}

/** Single-series column chart: one hue, rounded data-ends on a hairline baseline, one value label at a time. */
@Composable
private fun WeekChart(days: List<Pair<LocalDate, Int>>) {
    val barColor = MaterialTheme.colorScheme.primary
    val baselineColor = MaterialTheme.colorScheme.outlineVariant
    val selectedTrack = MaterialTheme.colorScheme.surfaceVariant
    val labelStyle = MaterialTheme.typography.labelMedium.copy(color = MaterialTheme.colorScheme.onSurface)
    val measurer = rememberTextMeasurer()
    var selected by remember(days.size) { mutableIntStateOf(days.lastIndex) }
    val locale = Locale.getDefault()
    val maxValue = days.maxOf { it.second }.coerceAtLeast(1)
    val summary = days.joinToString { (d, v) -> "${d.dayOfWeek.getDisplayName(TextStyle.FULL, locale)}: $v" }

    Column {
        Box(
            Modifier
                .fillMaxWidth()
                .height(140.dp)
                .semantics { contentDescription = "Blocks per day. $summary" }
                .pointerInput(days) {
                    detectTapGestures { offset ->
                        selected = (offset.x / (size.width / days.size)).toInt().coerceIn(0, days.lastIndex)
                    }
                },
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val slot = size.width / days.size
                val barWidth = min(24.dp.toPx(), slot * 0.6f)
                val labelRoom = 22.dp.toPx()
                val plotHeight = size.height - labelRoom
                val radius = 4.dp.toPx()

                // Soft column behind the selected day so the tap target reads.
                drawRoundRect(
                    color = selectedTrack.copy(alpha = 0.5f),
                    topLeft = Offset(slot * selected + (slot - barWidth) / 2 - 6.dp.toPx(), 0f),
                    size = androidx.compose.ui.geometry.Size(barWidth + 12.dp.toPx(), size.height),
                    cornerRadius = CornerRadius(radius * 2),
                )

                days.forEachIndexed { i, (_, value) ->
                    if (value == 0) return@forEachIndexed
                    val h = value / maxValue.toFloat() * plotHeight
                    val x = slot * i + (slot - barWidth) / 2
                    val r = min(radius, h)
                    val path = Path().apply {
                        addRoundRect(
                            RoundRect(
                                rect = Rect(x, size.height - h, x + barWidth, size.height),
                                topLeft = CornerRadius(r), topRight = CornerRadius(r),
                                bottomRight = CornerRadius.Zero, bottomLeft = CornerRadius.Zero,
                            )
                        )
                    }
                    drawPath(path, barColor)
                }

                drawLine(baselineColor, Offset(0f, size.height), Offset(size.width, size.height), strokeWidth = 1.dp.toPx())

                val value = days[selected].second
                val text = measurer.measure(value.toString(), labelStyle)
                val h = value / maxValue.toFloat() * plotHeight
                val cx = slot * selected + slot / 2
                drawText(text, topLeft = Offset(cx - text.size.width / 2f, size.height - h - text.size.height - 4.dp.toPx()))
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
            days.forEachIndexed { i, (d, _) ->
                Text(
                    if (i == days.lastIndex) "Today" else d.dayOfWeek.getDisplayName(TextStyle.SHORT, locale),
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (i == selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ByTypeCard() {
    val events = NoXXXApp.instance.blockLog.events.collectAsStateWithLifecycle().value
    val since = System.currentTimeMillis() - 7 * DAY_MS
    val counts = events.filter { it.timeMs >= since }.groupingBy { it.kind }.eachCount()
        .entries.sortedByDescending { it.value }
    if (counts.isEmpty()) return
    SectionCard("By type, last 7 days") {
        counts.forEach { (kind, n) ->
            ListItem(
                headlineContent = { Text(kindLabel(kind)) },
                leadingContent = { Icon(kindIcon(kind), contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                trailingContent = { Text("%,d".format(n), style = MaterialTheme.typography.titleMedium) },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            )
        }
    }
}
