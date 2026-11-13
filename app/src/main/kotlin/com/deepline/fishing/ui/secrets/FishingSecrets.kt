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
import androidx.compose.ui.res.stringResource
import com.deepline.fishing.R as Res
import com.deepline.fishing.core.DepthZone
import com.deepline.fishing.core.FishCatalog
import com.deepline.fishing.core.GameConfig
import com.deepline.fishing.core.ObstacleKind
import com.deepline.fishing.core.SecretReward
import com.deepline.fishing.core.SecretRewards
import com.deepline.fishing.core.SecretRules
import com.deepline.fishing.core.Secrets
import com.deepline.fishing.ui.SparkleGold
import com.deepline.fishing.ui.drawFish
import com.deepline.fishing.ui.drawFishGlow
import com.deepline.fishing.ui.drawHook
import com.deepline.fishing.ui.drawObstacleIcon
import com.deepline.fishing.ui.formatPoints
import com.deepline.fishing.ui.i18n.UiText
import com.deepline.fishing.ui.i18n.asString
import com.deepline.fishing.ui.i18n.labelText
import com.deepline.fishing.ui.i18n.nameText
import com.deepline.fishing.ui.i18n.secretTitleText
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
    val body: UiText,
    val bonus: UiText,
    val art: (DrawScope.() -> Unit)? = null,
) {
    /** The secret's name (strings_core.xml): only shown once the player has found it. */
    val title: UiText get() = secretTitleText(id)
}

/** "2", "1.5": a multiplier, shown after the × sign. */
private fun times(m: Double): UiText = UiText.decimal(m, 2)

/** "+50 points." */
private fun plusPoints(points: Int): UiText = UiText.plural(Res.plurals.secrets_bonus_points, points, formatPoints(points))

private fun thatFish(m: Double) = UiText.res(Res.string.secrets_bonus_that_fish, times(m))

private fun wholeCast(m: Double) = UiText.res(Res.string.secrets_bonus_whole_cast, times(m))

/**
 * All fishing secrets. To add one: give it an id, rules and rewards in `core` (`Secrets.kt`),
 * then describe it here (texts in strings_secrets.xml).
 */
object FishingSecrets {
    /** "Driftwood 100, Rock 200, Jelly cluster 150": the objects' base points. */
    private val snagList = UiText.join(
        ObstacleKind.entries.map { UiText.res(Res.string.secrets_snag_points, it.labelText(), formatPoints(it.snagPoints)) },
        ", ",
    )
    private val bumps = GameConfig.DESCENT_TOUCH_LIMIT

    private val R = SecretRules

    val all: List<FishingSecret> = listOf(
        FishingSecret(
            id = Secrets.LAST_BUMP,
            body = UiText.res(Res.string.secrets_last_bump_body, bumps, snagList),
            bonus = UiText.res(Res.string.secrets_bonus_yours),
            art = { drawLastBumpArt() },
        ),
        // Tier 1
        FishingSecret(
            Secrets.FREE_PASS,
            UiText.res(Res.string.secrets_free_pass_body, R.FREE_PASS_COUNT, GameConfig.BUMP_FREE_DEPTH.roundToInt()),
            plusPoints(R.FREE_PASS_BONUS), art = { drawFishRow(listOf("silver_sprat", "coral_darter", "silver_sprat"), hook = true) },
        ),
        FishingSecret(
            Secrets.SCHOOLING,
            UiText.res(Res.string.secrets_schooling_body, R.SCHOOLING_COUNT),
            plusPoints(R.SCHOOLING_BONUS), art = { drawSchoolingArt() },
        ),
        FishingSecret(
            Secrets.SURFACE_SKIM,
            UiText.res(Res.string.secrets_surface_skim_body, R.SURFACE_SKIM_DEPTH.roundToInt()),
            plusPoints(R.SURFACE_SKIM_BONUS), art = { drawFishRow(listOf("coral_darter"), hook = true, top = true) },
        ),
        FishingSecret(
            Secrets.ZEN_CAST,
            UiText.res(Res.string.secrets_zen_cast_body, R.ZEN_CAST_COUNT),
            plusPoints(R.ZEN_CAST_BONUS), art = { drawFishRow(listOf("reed_perch", "sun_bream", "silver_sprat")) },
        ),
        // Tier 2
        FishingSecret(
            Secrets.PINBALL,
            UiText.res(Res.string.secrets_pinball_body, bumps, R.PINBALL_SECONDS.roundToInt()),
            plusPoints(R.PINBALL_BONUS), art = { drawObstacleRow() },
        ),
        FishingSecret(
            Secrets.SECOND_ENCOUNTER,
            UiText.res(Res.string.secrets_second_encounter_body),
            thatFish(R.SECOND_ENCOUNTER_MULTIPLIER), art = { drawSecondEncounterArt() },
        ),
        FishingSecret(
            Secrets.CLEAN_DROP,
            UiText.res(Res.string.secrets_clean_drop_body, R.CLEAN_DROP_MIN_ROPE_M.roundToInt()),
            wholeCast(R.CLEAN_DROP_MULTIPLIER), art = { drawCleanDropArt() },
        ),
        FishingSecret(
            Secrets.ROCK_BOTTOM,
            UiText.res(Res.string.secrets_rock_bottom_body, R.ROCK_BOTTOM_WITHIN_M.roundToInt()),
            UiText.res(Res.string.secrets_bonus_snag, times(R.ROCK_BOTTOM_MULTIPLIER)), art = { drawCleanDropArt(withRock = true) },
        ),
        FishingSecret(
            Secrets.BUZZER_BEATER,
            UiText.res(Res.string.secrets_buzzer_beater_body, R.BUZZER_BEATER_SECONDS.roundToInt()),
            plusPoints(R.BUZZER_BEATER_BONUS), art = { drawFishRow(listOf("dusk_snapper"), hook = true) },
        ),
        // Tier 3
        FishingSecret(
            Secrets.CORNER_POCKET,
            UiText.res(Res.string.secrets_corner_pocket_body, UiText.decimal((GameConfig.WALL_TURN_WINDOW * 10).roundToInt() / 10.0)),
            plusPoints(R.CORNER_POCKET_BONUS), art = { drawCornerArt() },
        ),
        FishingSecret(
            Secrets.GRAND_TOUR,
            UiText.res(
                Res.string.secrets_grand_tour_body,
                bumps,
                ObstacleKind.JELLY.minDepth.roundToInt(),
                ObstacleKind.DRIFTWOOD.maxDepth.roundToInt(),
            ),
            UiText.res(Res.string.secrets_bonus_last_catch, times(R.GRAND_TOUR_MULTIPLIER)), art = { drawGrandTourArt() },
        ),
        FishingSecret(
            Secrets.SPARKLE_SNAG,
            UiText.res(Res.string.secrets_sparkle_snag_body),
            UiText.res(
                Res.string.secrets_bonus_sparkle_snag,
                times(R.SPARKLE_SNAG_MULTIPLIER),
                times(R.SPARKLE_SNAG_MULTIPLIER * GameConfig.SPARKLING_MULTIPLIER),
            ),
            art = { drawFishRow(listOf("dusk_snapper"), hook = true, sparkling = true) },
        ),
        FishingSecret(
            Secrets.LADDER,
            UiText.res(Res.string.secrets_ladder_body, R.LADDER_COUNT),
            wholeCast(R.LADDER_MULTIPLIER), art = { drawLadderArt() },
        ),
        FishingSecret(
            Secrets.PERFECT_LOAD,
            UiText.res(Res.string.secrets_perfect_load_body, R.PERFECT_LOAD_MIN_CATCHES, UiText.decimal(R.PERFECT_LOAD_FINAL_CATCH_KG, 2)),
            wholeCast(R.PERFECT_LOAD_MULTIPLIER), art = { drawFishRow(listOf("sun_bream", "reed_perch", "coral_darter"), hook = true) },
        ),
        FishingSecret(
            Secrets.MOONLIT_ANGLER,
            UiText.res(Res.string.secrets_moonlit_angler_body, GameConfig.DARK_DEPTH.roundToInt()),
            thatFish(R.MOONLIT_ANGLER_MULTIPLIER), art = { drawMoonlitArt() },
        ),
        FishingSecret(
            Secrets.SHUTOUT,
            UiText.res(Res.string.secrets_shutout_body),
            plusPoints(R.SHUTOUT_BONUS), art = { drawFishRow(listOf("reef_grouper", "reef_grouper", "reef_grouper")) },
        ),
        // Tier 4
        FishingSecret(
            Secrets.WHILE_IT_FLEES,
            UiText.res(Res.string.secrets_while_it_flees_body),
            thatFish(R.WHILE_IT_FLEES_MULTIPLIER), art = { drawSecondEncounterArt() },
        ),
        FishingSecret(
            Secrets.BLIND_DROP,
            UiText.res(Res.string.secrets_blind_drop_body, R.BLIND_DROP_MIN_DEPTH_M.roundToInt()),
            wholeCast(R.BLIND_DROP_MULTIPLIER), art = { drawMoonlitArt(dark = true) },
        ),
        FishingSecret(
            Secrets.ZONE_SWEEP,
            UiText.res(Res.string.secrets_zone_sweep_body, UiText.join(DepthZone.entries.map { it.labelText() }, ", ")),
            wholeCast(R.ZONE_SWEEP_MULTIPLIER), art = { drawZoneSweepArt() },
        ),
        FishingSecret(
            Secrets.GRAND_SLAM,
            UiText.res(Res.string.secrets_grand_slam_body, R.GRAND_SLAM_COUNT),
            wholeCast(R.GRAND_SLAM_MULTIPLIER), art = { drawLastBumpArt() },
        ),
        FishingSecret(
            Secrets.DOUBLE_HEADER,
            UiText.res(Res.string.secrets_double_header_body),
            UiText.res(Res.string.secrets_bonus_both_fish, times(R.DOUBLE_HEADER_MULTIPLIER)),
            art = { drawFishRow(listOf("dusk_snapper", "dusk_snapper"), hook = true) },
        ),
        FishingSecret(
            Secrets.CROWD_SURFER,
            UiText.res(Res.string.secrets_crowd_surfer_body, R.CROWD_SURFER_COUNT),
            wholeCast(R.CROWD_SURFER_MULTIPLIER),
            art = { drawFishRow(listOf("silver_sprat", "coral_darter", "reed_perch", "silver_sprat"), hook = true) },
        ),
        // Tier 5
        FishingSecret(
            Secrets.DAVID_AND_GOLIATH,
            UiText.res(
                Res.string.secrets_david_and_goliath_body,
                FishCatalog.byId(R.GOLIATH_SPECIES_ID)?.nameText() ?: UiText.raw(R.GOLIATH_SPECIES_ID),
            ),
            UiText.res(Res.string.secrets_bonus_leviathan, times(R.DAVID_AND_GOLIATH_MULTIPLIER)), art = { drawGoliathArt() },
        ),
        FishingSecret(
            Secrets.PERFECT_CAST,
            UiText.res(Res.string.secrets_perfect_cast_body, R.ROCK_BOTTOM_WITHIN_M.roundToInt()),
            wholeCast(R.PERFECT_CAST_MULTIPLIER), art = { drawFishRow(listOf("star_drifter"), hook = true, sparkling = true) },
        ),
        FishingSecret(
            Secrets.MASTER_ANGLER,
            UiText.res(Res.string.secrets_master_angler_body, FishCatalog.all.size),
            UiText.res(Res.string.secrets_bonus_legend), art = { drawSovereignArt() },
        ),
    )

    fun byId(id: String): FishingSecret? = all.firstOrNull { it.id == id }

    /** "+2,000 points · 2 Lanterns · Skins: …" for a secret's one-time discovery reward. */
    fun rewardText(id: String): UiText? {
        val r = SecretRewards.of(id)
        return rewardParts(r, if (r.cosmetics.isEmpty()) null else UiText.res(Res.string.secrets_reward_skins, UiText.join(r.cosmetics.map { it.labelText() }, ", ")))
    }

    /**
     * The reward shown on a locked Dex card: points and Lanterns, and only "a legendary skin set"
     * for cosmetics (set names would hint at the secret).
     */
    fun lockedRewardText(id: String): UiText? {
        val r = SecretRewards.of(id)
        return rewardParts(r, if (r.cosmetics.isEmpty()) null else UiText.res(Res.string.secrets_reward_legendary_set))
    }

    private fun rewardParts(r: SecretReward, looks: UiText?): UiText? {
        val parts = listOfNotNull(
            if (r.points > 0) UiText.plural(Res.plurals.secrets_reward_points, r.points, formatPoints(r.points)) else null,
            if (r.lanterns > 0) UiText.plural(Res.plurals.common_lanterns, r.lanterns) else null,
            looks,
        )
        return parts.takeIf { it.isNotEmpty() }?.let { UiText.join(it, com.deepline.fishing.ui.ITEM_SEPARATOR) }
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
internal fun SecretArt(secret: FishingSecret, height: androidx.compose.ui.unit.Dp = 120.dp) {
    val art = secret.art ?: return
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(12.dp)),
    ) { art() }
}

@Composable
internal fun SecretDetails(secret: FishingSecret) {
    Text(secret.title.asString(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    SecretArt(secret)
    Text(secret.body.asString(), style = MaterialTheme.typography.bodyMedium)
    Text(
        stringResource(Res.string.secrets_each_time, secret.bonus.asString()),
        style = MaterialTheme.typography.bodyMedium,
        color = com.deepline.fishing.ui.rewardTextColor(),
        fontWeight = FontWeight.SemiBold,
    )
}

/** "Discovery reward: +2,000 points · 2 Lanterns", under a found secret's explanation. */
@Composable
internal fun SecretDiscoveryReward(secretId: String) {
    FishingSecrets.rewardText(secretId)?.let {
        Text(
            stringResource(Res.string.secrets_discovery_reward, it.asString()),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

/** Shown the first time a secret happens (during the cast, or with the results). */
@Composable
fun SecretDiscoveredDialog(secret: FishingSecret, onAnswer: (showAgain: Boolean) -> Unit) {
    AlertDialog(
        // Dismissing without answering keeps the explanation coming, the safe default.
        onDismissRequest = { onAnswer(true) },
        title = { Text(stringResource(Res.string.secrets_discovered_title)) },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                SecretDetails(secret)
                SecretDiscoveryReward(secret.id)
                Text(stringResource(Res.string.secrets_discovered_footer), style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = { TextButton(onClick = { onAnswer(true) }) { Text(stringResource(Res.string.secrets_show_again)) } },
        dismissButton = { TextButton(onClick = { onAnswer(false) }) { Text(stringResource(Res.string.common_no_thanks)) } },
    )
}
