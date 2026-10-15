package com.noxxx.guard.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.noxxx.guard.data.BlockKind
import kotlinx.coroutines.delay

private val ENCOURAGEMENT = listOf(
    "Urges rise and fall like waves. This one will pass too.",
    "Stand up, drink some water, and step away for five minutes.",
    "You set this up for a reason. Future you is grateful.",
    "Text a friend, go for a walk, or do ten push-ups.",
    "One moment of discomfort beats hours of regret.",
)

/** Full-screen cover shown by the guard service when something is blocked. */
@Composable
fun BlockScreen(
    kind: BlockKind,
    detail: String,
    streakDays: Long,
    onOpenApp: (() -> Unit)?,
    onClose: () -> Unit,
) {
    var remaining by remember { mutableIntStateOf(if (kind == BlockKind.SETTINGS) 2 else 5) }
    LaunchedEffect(Unit) {
        while (remaining > 0) {
            delay(1_000)
            remaining--
        }
    }
    val encouragement = remember { ENCOURAGEMENT.random() }
    val (title, body) = blockCopy(kind, detail)

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Column(
            Modifier.fillMaxSize().systemBarsPadding().padding(horizontal = 32.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            IconBadge(kindIcon(kind), size = 96.dp)
            Text(title, style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
            Text(body, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (streakDays > 0) {
                AssistChip(
                    onClick = {},
                    label = { Text(if (streakDays == 1L) "1-day streak, keep it going" else "$streakDays-day streak, keep it going") },
                    leadingIcon = { Icon(Icons.Filled.LocalFireDepartment, contentDescription = null) },
                )
            }
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(encouragement, Modifier.padding(20.dp).fillMaxWidth(), textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyLarge)
            }
            if (onOpenApp != null) {
                FilledTonalButton(onClick = onOpenApp, modifier = Modifier.fillMaxWidth()) { Text("Open NoXXX") }
            }
            Button(onClick = onClose, enabled = remaining == 0, modifier = Modifier.fillMaxWidth()) {
                Text(if (remaining > 0) "Take a breath… $remaining" else "Close")
            }
        }
    }
}

private fun blockCopy(kind: BlockKind, detail: String): Pair<String, String> = when (kind) {
    BlockKind.WEBSITE -> "Site blocked" to "$detail is on your block list."
    BlockKind.SEARCH -> "Search blocked" to "That search contains a blocked word, so it was cleared."
    BlockKind.APP -> "App blocked" to "$detail is blocked by NoXXX."
    BlockKind.IMAGE -> "Screen covered" to "Image detection flagged explicit content in $detail."
    BlockKind.PRIVATE_BROWSING -> "Private browsing is blocked" to "Private or incognito tabs can't be checked. Use a normal tab."
    BlockKind.BROWSER -> "Use a supported browser" to
        "$detail can't be monitored. Use Chrome, Firefox, Samsung Internet, Edge, Brave, Vivaldi or DuckDuckGo."
    BlockKind.DNS_OFF -> "Web filter is off" to
        "Private DNS isn't set to a family filter, so browsers are paused. Open NoXXX to fix it."
    BlockKind.SETTINGS -> "Protected setting" to
        "This screen could switch off NoXXX. To change it, request the change in NoXXX and wait out the cool-off."
    BlockKind.GUARD_DISABLED -> "Guard was turned off" to
        "The guard service was disabled. Your streak has been reset and the event was recorded."
}
