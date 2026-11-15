package com.deepline.fishing.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.deepline.fishing.R
import com.deepline.fishing.core.FishCatalog
import com.deepline.fishing.core.GameConfig
import com.deepline.fishing.core.PlayerProgress
import com.deepline.fishing.core.Progression
import com.deepline.fishing.core.Seasons
import com.deepline.fishing.core.ShopClock
import com.deepline.fishing.ui.daily.DailyIcons
import com.deepline.fishing.ui.daily.DailyText
import com.deepline.fishing.ui.i18n.asString
import com.deepline.fishing.ui.i18n.label
import com.deepline.fishing.ui.kit.ActionRole
import com.deepline.fishing.ui.kit.GameColors
import com.deepline.fishing.ui.kit.LocalCalmMotion
import com.deepline.fishing.ui.kit.RingProgress
import com.deepline.fishing.ui.theme.LocalMenuPalette
import kotlinx.coroutines.delay

/**
 * What a solo cast moved forward, shown with its results before the points are collected: the
 * day's challenges it counted for, the season's track, new species for the Dex and milestones now
 * ready. Rings fill from where they were to where they are, so the player sees that one cast
 * feeds everything (the most played games show event progress after every level), without a
 * sentence. Nothing here when nothing moved. [before] is the save as it is, [after] what
 * collecting will make it (`Progression.applyCast`).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CastProgressStrip(before: PlayerProgress, after: PlayerProgress, modifier: Modifier = Modifier) {
    val now = ShopClock.now()
    val sameDay = before.challengeDay == after.challengeDay
    val moved = after.dailyChallenges.mapNotNull { c ->
        if (c.claimed) return@mapNotNull null
        val was = if (sameDay) before.dailyChallenges.firstOrNull { it.difficulty == c.difficulty && it.kind == c.kind && it.target == c.target }?.progress ?: 0 else 0
        if (c.progress > was) Triple(c, was.toFloat() / c.target, (c.progress.toFloat() / c.target).coerceAtMost(1f)) else null
    }
    val tierUp = Seasons.tier(after, now) > Seasons.tier(before, now)
    val seasonFrom = Seasons.xpIntoTier(before, now).toFloat() / GameConfig.SEASON_XP_PER_TIER
    val seasonTo = if (tierUp) 1f else Seasons.xpIntoTier(after, now).toFloat() / GameConfig.SEASON_XP_PER_TIER
    val newSpecies = after.discoveredCount - before.discoveredCount
    val milestones = Progression.claimableCount(after) - Progression.claimableCount(before)
    if (moved.isEmpty() && seasonTo <= seasonFrom && newSpecies <= 0 && milestones <= 0) return

    FlowRow(modifier.testTag("cast_progress"), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        for ((c, from, to) in moved) {
            MovingRing(DailyIcons.Flame, from, to, done = c.claimable, description = DailyText.challenge(c).asString())
        }
        if (seasonTo > seasonFrom) {
            val season = Seasons.at(now)
            MovingRing(GameIcons.Star, seasonFrom, seasonTo, done = tierUp, description = stringResource(R.string.shop_season_title, season.number, season.label()))
        }
        if (newSpecies > 0) {
            Gain(GameIcons.Dex, newSpecies, ready = false, description = stringResource(R.string.home_dex_caught, after.discoveredCount, FishCatalog.all.size))
        }
        if (milestones > 0) Gain(GameIcons.Trophy, milestones, ready = true, description = stringResource(R.string.home_cd_milestones))
    }
}

/** A ring that fills from [from] to [to] around its pictogram; green once [done] (ready to claim). */
@Composable
private fun MovingRing(icon: ImageVector, from: Float, to: Float, done: Boolean, description: String) {
    val calm = LocalCalmMotion.current
    val progress = remember { Animatable(if (calm) to else from) }
    LaunchedEffect(to) {
        if (!calm) {
            delay(250)
            progress.animateTo(to, tween(500))
        }
    }
    val color = if (done) GameColors.of(ActionRole.CLAIM, LocalMenuPalette.current).bottom else MaterialTheme.colorScheme.primary
    RingProgress(progress.value, Modifier.semantics { contentDescription = description }, diameter = 38.dp, color = color) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
    }
}

/** "+2" beside a pictogram: new species, milestones now ready (green when it can be claimed). */
@Composable
private fun Gain(icon: ImageVector, count: Int, ready: Boolean, description: String) {
    val color = if (ready) GameColors.of(ActionRole.CLAIM, LocalMenuPalette.current).bottom else MaterialTheme.colorScheme.primary
    Row(
        Modifier
            .background(color.copy(alpha = 0.14f), RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 8.dp)
            .semantics { contentDescription = description },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
        Text("+$count", color = color, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold) // i18n-ignore: digits
    }
}
