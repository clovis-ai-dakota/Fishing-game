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
import com.deepline.fishing.core.DepthZone
import com.deepline.fishing.core.FishCatalog
import com.deepline.fishing.core.GameConfig
import com.deepline.fishing.core.ObstacleKind
import com.deepline.fishing.core.SecretRewards
import com.deepline.fishing.core.SecretRules
import com.deepline.fishing.core.Secrets
import com.deepline.fishing.ui.SparkleGold
import com.deepline.fishing.ui.SubScreen
import com.deepline.fishing.ui.drawFish
import com.deepline.fishing.ui.drawFishGlow
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

    private val R = SecretRules

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
        // Tier 1
        FishingSecret(
            Secrets.FREE_PASS, "Free pass",
            "Pass through ${R.FREE_PASS_COUNT} fish heads in the bump-free first ${GameConfig.BUMP_FREE_DEPTH.roundToInt()} m of one cast. They don't cost bumps up there.",
            "+${R.FREE_PASS_BONUS} points.", art = { drawFishRow(listOf("silver_sprat", "coral_darter", "silver_sprat"), hook = true) },
        ),
        FishingSecret(
            Secrets.SCHOOLING, "Schooling",
            "Fish swim in schools. Land ${R.SCHOOLING_COUNT} or more of the same species in a single cast.",
            "+${R.SCHOOLING_BONUS} points.", art = { drawSchoolingArt() },
        ),
        FishingSecret(
            Secrets.SURFACE_SKIM, "Surface skim",
            "Catch a fish shallower than ${R.SURFACE_SKIM_DEPTH.roundToInt()} m on the way up, right before you land.",
            "+${R.SURFACE_SKIM_BONUS} points.", art = { drawFishRow(listOf("coral_darter"), hook = true, top = true) },
        ),
        FishingSecret(
            Secrets.ZEN_CAST, "Zen cast",
            "Don't touch the screen for a whole cast, and still land ${R.ZEN_CAST_COUNT} or more fish.",
            "+${R.ZEN_CAST_BONUS} points.", art = { drawFishRow(listOf("reed_perch", "sun_bream", "silver_sprat")) },
        ),
        // Tier 2
        FishingSecret(
            Secrets.PINBALL, "Pinball",
            "Use all $bumps bumps within ${R.PINBALL_SECONDS.roundToInt()} seconds by steering into a cluster.",
            "+${R.PINBALL_BONUS} points.", art = { drawObstacleRow() },
        ),
        FishingSecret(
            Secrets.SECOND_ENCOUNTER, "Second encounter",
            "A fish you bumped on the way down hasn't gone far. Catch it by the head on the way up and it's worth double.",
            "That fish ${times(R.SECOND_ENCOUNTER_MULTIPLIER)}.", art = { drawSecondEncounterArt() },
        ),
        FishingSecret(
            Secrets.CLEAN_DROP, "Clean drop",
            "Reach the end of a rope of at least ${R.CLEAN_DROP_MIN_ROPE_M.roundToInt()} m without a single bump.",
            "Whole cast ${times(R.CLEAN_DROP_MULTIPLIER)}.", art = { drawCleanDropArt() },
        ),
        FishingSecret(
            Secrets.ROCK_BOTTOM, "Rock bottom",
            "Make your last bump snag an object within ${R.ROCK_BOTTOM_WITHIN_M.roundToInt()} m of the end of your rope.",
            "The snag ${times(R.ROCK_BOTTOM_MULTIPLIER)}.", art = { drawCleanDropArt(withRock = true) },
        ),
        FishingSecret(
            Secrets.BUZZER_BEATER, "Buzzer beater",
            "In a timed battle, start a cast with under ${R.BUZZER_BEATER_SECONDS.roundToInt()} seconds on the clock and still land a fish.",
            "+${R.BUZZER_BEATER_BONUS} points.", art = { drawFishRow(listOf("dusk_snapper"), hook = true) },
        ),
        // Tier 3
        FishingSecret(
            Secrets.CORNER_POCKET, "Corner pocket",
            "On the way up, hook a fish by the head right as it turns around at the edge of the water (within ${(GameConfig.WALL_TURN_WINDOW * 10).roundToInt() / 10.0} s).",
            "+${R.CORNER_POCKET_BONUS} points.", art = { drawCornerArt() },
        ),
        FishingSecret(
            Secrets.GRAND_TOUR, "Grand tour",
            "Make your $bumps bumps hit four different things: a fish's head, a rock, driftwood and a jelly. They only share " +
                "the water between about ${ObstacleKind.JELLY.minDepth.roundToInt()} and ${ObstacleKind.DRIFTWOOD.maxDepth.roundToInt()} m.",
            "The last bump's catch ${times(R.GRAND_TOUR_MULTIPLIER)}.", art = { drawGrandTourArt() },
        ),
        FishingSecret(
            Secrets.SPARKLE_SNAG, "Sparkle snag",
            "Make your last bump hook a Sparkling fish by the head, on the way down.",
            "That fish ${times(R.SPARKLE_SNAG_MULTIPLIER)} on top of Sparkling (${times(R.SPARKLE_SNAG_MULTIPLIER * GameConfig.SPARKLING_MULTIPLIER)} in all).", art = { drawFishRow(listOf("dusk_snapper"), hook = true, sparkling = true) },
        ),
        FishingSecret(
            Secrets.LADDER, "Ladder",
            "On the way up, catch ${R.LADDER_COUNT} or more fish in strictly increasing weight. Deep fish tend to be heavy, so pick your catches.",
            "Whole cast ${times(R.LADDER_MULTIPLIER)}.", art = { drawLadderArt() },
        ),
        FishingSecret(
            Secrets.PERFECT_LOAD, "Perfect load",
            "Surface with your line exactly at its weight limit, with at least ${R.PERFECT_LOAD_MIN_CATCHES} catches, and the fish that " +
                "fills it weighing ${R.PERFECT_LOAD_FINAL_CATCH_KG} kg or more. Leave just the right room!",
            "Whole cast ${times(R.PERFECT_LOAD_MULTIPLIER)}.", art = { drawFishRow(listOf("sun_bream", "reed_perch", "coral_darter"), hook = true) },
        ),
        FishingSecret(
            Secrets.MOONLIT_ANGLER, "Moonlit angler",
            "Below ${GameConfig.DARK_DEPTH.roundToInt()} m and without a Lantern, catch a fish that glows on the way up, aiming only by its light.",
            "That fish ${times(R.MOONLIT_ANGLER_MULTIPLIER)}.", art = { drawMoonlitArt() },
        ),
        FishingSecret(
            Secrets.SHUTOUT, "Shutout",
            "Win Best of 5 against the Hard rival without dropping a round (3-0).",
            "+${formatPoints(R.SHUTOUT_BONUS)} points.", art = { drawFishRow(listOf("reef_grouper", "reef_grouper", "reef_grouper")) },
        ),
        // Tier 4
        FishingSecret(
            Secrets.WHILE_IT_FLEES, "While it flees",
            "Bump a fish on the way down (it darts away for a moment), then hook it by the head on the way up while it is still fleeing. Tip: make that your last bump, so the hook turns back right away.",
            "That fish ${times(R.WHILE_IT_FLEES_MULTIPLIER)}.", art = { drawSecondEncounterArt() },
        ),
        FishingSecret(
            Secrets.BLIND_DROP, "Blind drop",
            "Without a Lantern, drop to at least ${R.BLIND_DROP_MIN_DEPTH_M.roundToInt()} m and reach the end of your rope without a single bump.",
            "Whole cast ${times(R.BLIND_DROP_MULTIPLIER)}.", art = { drawMoonlitArt(dark = true) },
        ),
        FishingSecret(
            Secrets.ZONE_SWEEP, "Zone sweep",
            "Land a fish from every depth zone (${DepthZone.entries.joinToString { it.displayName }}) in a single cast.",
            "Whole cast ${times(R.ZONE_SWEEP_MULTIPLIER)}.", art = { drawZoneSweepArt() },
        ),
        FishingSecret(
            Secrets.GRAND_SLAM, "Grand slam",
            "In one cast, snag an object with your last bump and catch ${R.GRAND_SLAM_COUNT} or more fish on the way up.",
            "Whole cast ${times(R.GRAND_SLAM_MULTIPLIER)}.", art = { drawLastBumpArt() },
        ),
        FishingSecret(
            Secrets.DOUBLE_HEADER, "Double header",
            "On the way up, hook two fish by the head at the very same instant.",
            "Both fish ${times(R.DOUBLE_HEADER_MULTIPLIER)}.", art = { drawFishRow(listOf("dusk_snapper", "dusk_snapper"), hook = true) },
        ),
        // Tier 5
        FishingSecret(
            Secrets.DAVID_AND_GOLIATH, "David and Goliath",
            "Hook the Pale Leviathan with your last bump while your rope is too weak to carry it. A legendary catch.",
            "The leviathan ${times(R.DAVID_AND_GOLIATH_MULTIPLIER)}.", art = { drawGoliathArt() },
        ),
        FishingSecret(
            Secrets.PERFECT_CAST, "Perfect cast",
            "Everything in one cast: your last bump snags an object within ${R.ROCK_BOTTOM_WITHIN_M.roundToInt()} m of the end of your " +
                "rope, you catch a Sparkling fish on the way up, and you surface with a perfect load.",
            "Whole cast ${times(R.PERFECT_CAST_MULTIPLIER)}.", art = { drawFishRow(listOf("star_drifter"), hook = true, sparkling = true) },
        ),
        FishingSecret(
            Secrets.MASTER_ANGLER, "Master angler",
            "Fill the whole fish log: all ${FishCatalog.all.size} species, including the one that lives at the very bottom and only " +
                "the last bump can land.",
            "A legend, forever.", art = { drawSovereignArt() },
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

private fun DrawScope.drawFishRow(ids: List<String>, hook: Boolean = false, top: Boolean = false, sparkling: Boolean = false) {
    drawRect(if (top) Brush.verticalGradient(listOf(Color(0xFFB3E5FC), Color(0xFF0288D1))) else ArtWater)
    val n = ids.size
    ids.forEachIndexed { i, id ->
        val sp = FishCatalog.byId(id) ?: return@forEachIndexed
        val c = Offset(size.width * (i + 1f) / (n + 1f), size.height * (if (top) 0.35f else 0.55f))
        drawFish(sp, c, size.width / (n + 2.2f), if (i % 2 == 0) 1 else -1, sparkling, i * 0.6f, phase = i.toFloat())
    }
    if (hook) {
        val h = Offset(size.width * 0.5f, size.height * 0.2f)
        drawLine(Color.White.copy(alpha = 0.8f), Offset(h.x, 0f), Offset(h.x, h.y - 10.dp.toPx()), strokeWidth = 2f)
        drawHook(h, 8.dp.toPx(), 0.2f, withBait = false)
    }
}

private fun DrawScope.drawObstacleRow() {
    drawRect(ArtWater)
    val r = 15.dp.toPx()
    listOf(ObstacleKind.ROCK, ObstacleKind.DRIFTWOOD, ObstacleKind.JELLY, ObstacleKind.ROCK).forEachIndexed { i, k ->
        drawObstacleIcon(k, Offset(size.width * (0.2f + i * 0.2f), size.height * (0.3f + (i % 2) * 0.35f)), r)
    }
    burst(Offset(size.width * 0.5f, size.height * 0.5f), 20.dp.toPx(), 28.dp.toPx())
}

private fun DrawScope.drawCornerArt() {
    drawRect(ArtWater)
    drawLine(Color.White.copy(alpha = 0.6f), Offset(size.width - 6.dp.toPx(), 0f), Offset(size.width - 6.dp.toPx(), size.height), strokeWidth = 3f)
    val sp = FishCatalog.byId("dusk_snapper")!!
    drawFish(sp, Offset(size.width * 0.78f, size.height * 0.55f), size.width * 0.25f, -1, false, 0.2f)
    val h = Offset(size.width * 0.62f, size.height * 0.5f)
    drawHook(h, 9.dp.toPx(), 0f, withBait = false)
    burst(h, 14.dp.toPx(), 20.dp.toPx())
}

private fun DrawScope.drawMoonlitArt(dark: Boolean = false) {
    drawRect(Color(0xFF02060F))
    val sp = FishCatalog.byId("glowfin_angler")!!
    val c = Offset(size.width * 0.5f, size.height * 0.55f)
    if (!dark) {
        drawFish(sp, c, size.width * 0.3f, 1, false, 0.4f, silhouette = true)
        drawFishGlow(sp, c, size.width * 0.3f, 1, 1f)
    }
    drawLine(Color.White.copy(alpha = 0.5f), Offset(size.width * 0.3f, 0f), Offset(size.width * 0.3f, size.height * 0.8f), strokeWidth = 1.5f)
    drawHook(Offset(size.width * 0.3f, size.height * 0.85f), 8.dp.toPx(), 0f, withBait = false)
}

private fun DrawScope.drawSovereignArt() {
    drawRect(Brush.verticalGradient(listOf(Color(0xFF1A0033), Color(0xFF02060F))))
    val sp = FishCatalog.sovereign
    val c = Offset(size.width * 0.5f, size.height * 0.55f)
    drawFish(sp, c, size.width * 0.7f, 1, sparkling = true, timeSeconds = 0.4f)
    drawFishGlow(sp, c, size.width * 0.7f, 1, 1f)
    burst(Offset(size.width * 0.86f, size.height * 0.25f), 8.dp.toPx(), 14.dp.toPx())
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

private fun DrawScope.drawCleanDropArt(withRock: Boolean = false) {
    drawRect(ArtWater)
    val x = size.width / 2f
    val end = size.height * 0.78f
    drawLine(Color.White.copy(alpha = 0.85f), Offset(x, 0f), Offset(x, end - 20.dp.toPx()), strokeWidth = 2f)
    drawLine(Color(0xFFFFB74D), Offset(0f, end + 14.dp.toPx()), Offset(size.width, end + 14.dp.toPx()), strokeWidth = 3f,
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(18f, 12f)))
    drawHook(Offset(x, end), 11.dp.toPx(), 0.2f)
    if (withRock) {
        drawObstacleIcon(ObstacleKind.ROCK, Offset(x, end + 4.dp.toPx()), 14.dp.toPx())
        burst(Offset(x, end), 22.dp.toPx(), 30.dp.toPx())
    }
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
