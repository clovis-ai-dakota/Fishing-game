package com.deepline.fishing.ui.secrets

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.deepline.fishing.GameViewModel
import com.deepline.fishing.Screen
import com.deepline.fishing.core.GameConfig
import com.deepline.fishing.core.ObstacleKind
import com.deepline.fishing.core.Secrets
import com.deepline.fishing.ui.SparkleGold
import com.deepline.fishing.ui.SubScreen
import com.deepline.fishing.ui.drawHook
import com.deepline.fishing.ui.drawObstacleIcon
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * A hidden mechanic, explained the first time the player triggers it and listed in the
 * Fishing secrets screen afterwards. Secrets are never spelled out in the tour.
 */
data class FishingSecret(
    val id: String,
    val title: String,
    val body: String,
    val art: (DrawScope.() -> Unit)? = null,
)

/**
 * All fishing secrets. To add one: give it an id in `core` [Secrets], decide in
 * `Secrets.revealedBy` / the game code what triggers it, and describe it here.
 */
object FishingSecrets {
    private val snagList = ObstacleKind.entries.joinToString(", ") { "${it.displayName} ${it.snagPoints}" }

    val all: List<FishingSecret> = listOf(
        FishingSecret(
            id = Secrets.LAST_BUMP,
            title = "The last bump hooks what it hits",
            body = "Your last bump (number ${GameConfig.DESCENT_TOUCH_LIMIT}) doesn't just send the line back up: it " +
                "hooks whatever it hit. If it was a fish's head, that fish is on your line, even a heavy one. " +
                "And it's the only way to land rocks, driftwood and jellies, which are worth big points " +
                "($snagList base points, multiplied the deeper you go). Aim your last bump at something good!",
            art = { drawLastBumpArt() },
        ),
    )

    fun byId(id: String): FishingSecret? = all.firstOrNull { it.id == id }
}

private fun DrawScope.drawLastBumpArt() {
    drawRect(Brush.verticalGradient(listOf(Color(0xFF0288D1), Color(0xFF01396B))))
    val hook = Offset(size.width / 2f, size.height * 0.42f)
    drawLine(Color.White.copy(alpha = 0.85f), Offset(hook.x, 0f), Offset(hook.x, hook.y - 22.dp.toPx()), strokeWidth = 2f)
    val s = 15.dp.toPx()
    drawHook(hook, s, 0.3f, withBait = false)
    drawObstacleIcon(ObstacleKind.ROCK, Offset(hook.x, hook.y + s * 2.2f), 20.dp.toPx())
    for (k in 0 until 8) {
        val a = (k / 8f) * 2f * PI.toFloat()
        val dir = Offset(cos(a), sin(a))
        val c = Offset(hook.x, hook.y + s)
        drawLine(SparkleGold, c + dir * (s * 2.6f), c + dir * (s * 3.4f), strokeWidth = 3.dp.toPx(), cap = StrokeCap.Round)
    }
}

@Composable
private fun SecretArt(secret: FishingSecret) {
    val art = secret.art ?: return
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(120.dp)
            .clip(RoundedCornerShape(12.dp)),
    ) { art() }
}

/** Shown (with the cast paused) the first time a secret happens. */
@Composable
fun SecretDiscoveredDialog(secret: FishingSecret, onAnswer: (showAgain: Boolean) -> Unit) {
    AlertDialog(
        // Dismissing without answering keeps the explanation coming, the safe default.
        onDismissRequest = { onAnswer(true) },
        title = { Text("Fishing secret discovered!") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(secret.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                SecretArt(secret)
                Text(secret.body, style = MaterialTheme.typography.bodyMedium)
                Text(
                    "You can read it again any time in Fishing secrets on the home screen.\n\n" +
                        "Show this explanation again the next time it happens?",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        },
        confirmButton = { TextButton(onClick = { onAnswer(true) }) { Text("Yes, show again") } },
        dismissButton = { TextButton(onClick = { onAnswer(false) }) { Text("No thanks") } },
    )
}

/** The secrets the player has discovered so far. Undiscovered ones stay hidden. */
@Composable
fun SecretsScreen(vm: GameViewModel) {
    val discovered = FishingSecrets.all.filter { it.id in vm.progress.discoveredSecrets }
    SubScreen(title = "Fishing secrets", onBack = { vm.navigate(Screen.HOME) }) {
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(discovered, key = { it.id }) { secret ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(secret.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        SecretArt(secret)
                        Text(secret.body, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            item {
                Text(
                    "More secrets are waiting in the deep. Keep fishing!",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
