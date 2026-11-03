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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.deepline.fishing.GameViewModel
import com.deepline.fishing.Screen
import com.deepline.fishing.core.FishCatalog
import com.deepline.fishing.core.GameConfig
import com.deepline.fishing.core.ObstacleKind
import com.deepline.fishing.core.SecretRewards
import com.deepline.fishing.core.SecretRules
import com.deepline.fishing.core.Secrets
import com.deepline.fishing.ui.SparkleGold
import com.deepline.fishing.ui.SubScreen
import com.deepline.fishing.ui.drawFish
import com.deepline.fishing.ui.drawHook
import com.deepline.fishing.ui.drawObstacleIcon
import com.deepline.fishing.ui.formatPoints
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * A hidden mechanic, explained the first time the player triggers it and listed in the
 * Fishing secrets screen afterwards. Secrets are never spelled out in the tour.
 * [bonus] describes what it adds to a cast every time it happens.
 */
data class FishingSecret(
    val id: String,
    val title: String,
    val body: String,
    val bonus: String,
    val art: (DrawScope.() -> Unit)? = null,
)

private fun times(m: Double) = if (m == m.roundToInt().toDouble()) "×${m.roundToInt()}" else "×$m"

/**
 * All fishing secrets. To add one: give it an id, rules and rewards in `core` (`Secrets.kt`),
 * then describe it here.
 */
object FishingSecrets {
    private val snagList = ObstacleKind.entries.joinToString(", ") { "${it.displayName} ${it.snagPoints}" }
    private val bumps = GameConfig.DESCENT_TOUCH_LIMIT

    val all: List<FishingSecret> = listOf(
        FishingSecret(
            id = Secrets.LAST_BUMP,
            title = "The last bump hooks what it hits",
            body = "Your last bump (number $bumps) doesn't just send the line back up: it hooks whatever it hit. " +
                "If it was a fish's head, that fish is on your line, even a heavy one. And it's the only way to " +
                "land rocks, driftwood and jellies, which are worth big points ($snagList base points, multiplied " +
                "the deeper you go). Aim your last bump at something good!",
            bonus = "The hooked object or fish is yours.",
            art = { drawLastBumpArt() },
        ),
        FishingSecret(
            id = Secrets.SCHOOLING,
            title = "Schooling",
            body = "Fish swim in schools. Land ${SecretRules.SCHOOLING_COUNT} or more of the same species in a " +
                "single cast for a bonus.",
            bonus = "+${SecretRules.SCHOOLING_BONUS} points per cast.",
            art = { drawSchoolingArt() },
        ),
        FishingSecret(
            id = Secrets.SECOND_ENCOUNTER,
            title = "Second encounter",
            body = "A fish you bumped on the way down hasn't gone far. Catch it by the head on the way up and it's " +
                "worth double.",
            bonus = "That fish ${times(SecretRules.SECOND_ENCOUNTER_MULTIPLIER)}.",
            art = { drawSecondEncounterArt() },
        ),
        FishingSecret(
            id = Secrets.CLEAN_DROP,
            title = "Clean drop",
            body = "Reach the end of a rope of at least ${SecretRules.CLEAN_DROP_MIN_ROPE_M.roundToInt()} m without " +
                "a single bump. Dodging every head and object all the way down boosts the whole cast.",
            bonus = "Whole cast ${times(SecretRules.CLEAN_DROP_MULTIPLIER)}.",
            art = { drawCleanDropArt() },
        ),
        FishingSecret(
            id = Secrets.GRAND_TOUR,
            title = "Grand tour",
            body = "Make your $bumps bumps hit four different things: a fish's head, a rock, driftwood and a jelly. " +
                "They only share the water between about 120 and 260 m. Whatever the last bump hooks is worth double.",
            bonus = "The last bump's catch ${times(SecretRules.GRAND_TOUR_MULTIPLIER)}.",
            art = { drawGrandTourArt() },
        ),
        FishingSecret(
            id = Secrets.LADDER,
            title = "Ladder",
            body = "On the way up, catch ${SecretRules.LADDER_COUNT} or more fish in strictly increasing weight: " +
                "each one heavier than the one before. Deep fish tend to be heavy, so you have to pick your catches.",
            bonus = "Whole cast ${times(SecretRules.LADDER_MULTIPLIER)}.",
            art = { drawLadderArt() },
        ),
        FishingSecret(
            id = Secrets.ZONE_SWEEP,
            title = "Zone sweep",
            body = "Land a fish from every depth zone (Sunlit Shallows, Twilight Reef, Midnight Trench and Abyssal " +
                "Floor) in a single cast. You'll need a long rope and room on the line.",
            bonus = "Whole cast ${times(SecretRules.ZONE_SWEEP_MULTIPLIER)}.",
            art = { drawZoneSweepArt() },
        ),
        FishingSecret(
            id = Secrets.DAVID_AND_GOLIATH,
            title = "David and Goliath",
            body = "Hook the Pale Leviathan with your last bump while your rope is too weak to carry it. A legendary " +
                "catch: it unlocks the Giant-slayer skins.",
            bonus = "The leviathan ${times(SecretRules.DAVID_AND_GOLIATH_MULTIPLIER)}.",
            art = { drawGoliathArt() },
        ),
    )

    fun byId(id: String): FishingSecret? = all.firstOrNull { it.id == id }

    /** "+2,000 points · 2 Lanterns · Unlocks: …" for a secret's one-time discovery reward. */
    fun rewardText(id: String): String? {
        val r = SecretRewards.of(id)
        val parts = buildList {
            if (r.points > 0) add("+${formatPoints(r.points)} points")
            if (r.lanterns == 1) add("1 Lantern")
            if (r.lanterns > 1) add("${r.lanterns} Lanterns")
            if (r.cosmetics.isNotEmpty()) add("Skins: " + r.cosmetics.joinToString(", ") { it.displayName })
        }
        return parts.takeIf { it.isNotEmpty() }?.joinToString(" · ")
    }
}

private val ArtWater = Brush.verticalGradient(listOf(Color(0xFF0288D1), Color(0xFF01396B)))

private fun DrawScope.burst(c: Offset, inner: Float, outer: Float) {
    for (k in 0 until 8) {
        val a = (k / 8f) * 2f * PI.toFloat()
        val dir = Offset(cos(a), sin(a))
        drawLine(SparkleGold, c + dir * inner, c + dir * outer, strokeWidth = 3.dp.toPx(), cap = StrokeCap.Round)
    }
}

private fun DrawScope.drawLastBumpArt() {
    drawRect(ArtWater)
    val hook = Offset(size.width / 2f, size.height * 0.42f)
    drawLine(Color.White.copy(alpha = 0.85f), Offset(hook.x, 0f), Offset(hook.x, hook.y - 22.dp.toPx()), strokeWidth = 2f)
    val s = 15.dp.toPx()
    drawHook(hook, s, 0.3f, withBait = false)
    drawObstacleIcon(ObstacleKind.ROCK, Offset(hook.x, hook.y + s * 2.2f), 20.dp.toPx())
    burst(Offset(hook.x, hook.y + s), s * 2.6f, s * 3.4f)
}

private fun DrawScope.drawSchoolingArt() {
    drawRect(ArtWater)
    val sprat = FishCatalog.byId("silver_sprat")!!
    val len = size.width * 0.16f
    listOf(Offset(0.3f, 0.35f), Offset(0.5f, 0.55f), Offset(0.7f, 0.35f)).forEachIndexed { i, o ->
        drawFish(sprat, Offset(size.width * o.x, size.height * o.y), len, 1, false, i * 0.7f, phase = i.toFloat())
    }
}

private fun DrawScope.drawSecondEncounterArt() {
    drawRect(ArtWater)
    val sp = FishCatalog.byId("dusk_snapper")!!
    val c = Offset(size.width * 0.45f, size.height * 0.55f)
    drawFish(sp, c, size.width * 0.3f, 1, false, 0.3f)
    // Down then back up.
    val x = c.x + size.width * 0.13f
    drawLine(Color.White.copy(alpha = 0.8f), Offset(x - 10.dp.toPx(), 4.dp.toPx()), Offset(x - 10.dp.toPx(), c.y - 8.dp.toPx()), strokeWidth = 3f)
    drawLine(SparkleGold, Offset(x + 10.dp.toPx(), c.y - 8.dp.toPx()), Offset(x + 10.dp.toPx(), 4.dp.toPx()), strokeWidth = 3f)
    burst(Offset(x, c.y), 18.dp.toPx(), 26.dp.toPx())
}

private fun DrawScope.drawCleanDropArt() {
    drawRect(ArtWater)
    val x = size.width / 2f
    val end = size.height * 0.78f
    drawLine(Color.White.copy(alpha = 0.85f), Offset(x, 0f), Offset(x, end - 20.dp.toPx()), strokeWidth = 2f)
    drawLine(Color(0xFFFFB74D), Offset(0f, end + 14.dp.toPx()), Offset(size.width, end + 14.dp.toPx()), strokeWidth = 3f,
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(18f, 12f)))
    drawHook(Offset(x, end), 11.dp.toPx(), 0.2f)
    drawObstacleIcon(ObstacleKind.ROCK, Offset(x - size.width * 0.25f, size.height * 0.35f), 16.dp.toPx())
    drawFish(FishCatalog.byId("ghost_hake")!!, Offset(x + size.width * 0.22f, size.height * 0.5f), size.width * 0.22f, -1, false, 0.5f)
}

private fun DrawScope.drawGrandTourArt() {
    drawRect(ArtWater)
    val y = size.height / 2f
    val r = 16.dp.toPx()
    drawFish(FishCatalog.byId("reef_grouper")!!, Offset(size.width * 0.16f, y), size.width * 0.2f, 1, false, 0.2f)
    drawObstacleIcon(ObstacleKind.ROCK, Offset(size.width * 0.40f, y), r)
    drawObstacleIcon(ObstacleKind.DRIFTWOOD, Offset(size.width * 0.62f, y), r * 1.2f)
    drawObstacleIcon(ObstacleKind.JELLY, Offset(size.width * 0.84f, y + r * 0.3f), r)
    for (i in 1..4) {
        drawCircle(SparkleGold, radius = 5.dp.toPx(), center = Offset(size.width * (0.16f + (i - 1) * 0.227f), size.height * 0.85f))
    }
}

private fun DrawScope.drawLadderArt() {
    drawRect(ArtWater)
    val ids = listOf("silver_sprat", "coral_darter", "reed_perch", "sun_bream")
    ids.forEachIndexed { i, id ->
        val sp = FishCatalog.byId(id)!!
        val len = size.width * (0.10f + i * 0.05f)
        drawFish(sp, Offset(size.width * (0.15f + i * 0.23f), size.height * (0.78f - i * 0.17f)), len, 1, false, i * 0.5f)
    }
}

private fun DrawScope.drawZoneSweepArt() {
    val bands = listOf(Color(0xFF4FC3F7), Color(0xFF0277BD), Color(0xFF01396B), Color(0xFF061A3A))
    val ids = listOf("coral_darter", "dusk_snapper", "ghost_hake", "star_drifter")
    val h = size.height / 4f
    bands.forEachIndexed { i, c ->
        drawRect(c, topLeft = Offset(0f, i * h), size = Size(size.width, h))
        drawFish(FishCatalog.byId(ids[i])!!, Offset(size.width * (0.3f + 0.13f * i), i * h + h / 2f), h * 1.3f, if (i % 2 == 0) 1 else -1, false, i.toFloat())
    }
}

private fun DrawScope.drawGoliathArt() {
    drawRect(Brush.verticalGradient(listOf(Color(0xFF061A3A), Color(0xFF02060F))))
    val leviathan = FishCatalog.byId("pale_leviathan")!!
    val c = Offset(size.width * 0.55f, size.height * 0.58f)
    drawFish(leviathan, c, size.width * 0.75f, -1, false, 0.6f)
    val hook = Offset(c.x - size.width * 0.33f, c.y - 6.dp.toPx())
    drawLine(Color.White.copy(alpha = 0.6f), Offset(hook.x, 0f), Offset(hook.x, hook.y - 14.dp.toPx()), strokeWidth = 1.5f)
    drawHook(hook, 10.dp.toPx(), 0f, withBait = false)
    burst(hook, 16.dp.toPx(), 24.dp.toPx())
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

@Composable
private fun SecretDetails(secret: FishingSecret) {
    Text(secret.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    SecretArt(secret)
    Text(secret.body, style = MaterialTheme.typography.bodyMedium)
    Text("Each time: ${secret.bonus}", style = MaterialTheme.typography.bodyMedium, color = SparkleGold, fontWeight = FontWeight.SemiBold)
}

/** Shown the first time a secret happens (during the cast, or with the results). */
@Composable
fun SecretDiscoveredDialog(secret: FishingSecret, onAnswer: (showAgain: Boolean) -> Unit) {
    AlertDialog(
        // Dismissing without answering keeps the explanation coming, the safe default.
        onDismissRequest = { onAnswer(true) },
        title = { Text("Fishing secret discovered!") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                SecretDetails(secret)
                FishingSecrets.rewardText(secret.id)?.let {
                    Text("Discovery reward: $it", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                }
                Text(
                    "You can read it again any time with the Secrets button on the home screen.\n\n" +
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
            item {
                Text(
                    "Discovered ${discovered.size} of ${FishingSecrets.all.size}",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.secondary,
                    fontWeight = FontWeight.Bold,
                )
            }
            items(discovered, key = { it.id }) { secret ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        SecretDetails(secret)
                    }
                }
            }
            if (discovered.size < FishingSecrets.all.size) {
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
}
