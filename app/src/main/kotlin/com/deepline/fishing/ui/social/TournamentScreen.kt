package com.deepline.fishing.ui.social

import android.icu.text.MessageFormat
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.deepline.fishing.GameViewModel
import com.deepline.fishing.R
import com.deepline.fishing.core.GameConfig
import com.deepline.fishing.core.ShopClock
import com.deepline.fishing.core.online.OnlineProtocol
import com.deepline.fishing.core.social.BoardKind
import com.deepline.fishing.core.social.TournamentTier
import com.deepline.fishing.ui.SightTackleChips
import com.deepline.fishing.ui.SubScreen
import com.deepline.fishing.ui.formatPoints
import com.deepline.fishing.ui.i18n.asString
import com.deepline.fishing.ui.i18n.label
import com.deepline.fishing.ui.pointsLabel
import com.deepline.fishing.ui.rewardTextColor
import com.deepline.fishing.ui.shop.ShopText
import com.deepline.fishing.ui.tour.TourTarget
import com.deepline.fishing.ui.tour.tourTarget
import java.util.Locale

/**
 * A rank as the language writes ordinal numbers: "1st", "23rd" in English, "1er", "2e" in French,
 * "1." in German and Turkish (ICU's ordinal rules, through MessageFormat's `ordinal` argument:
 * public since Android 7, the game's minimum).
 */
fun ordinal(n: Int, locale: Locale = Locale.getDefault()): String =
    runCatching { MessageFormat("{0,ordinal}", locale).format(arrayOf<Any>(n)) }.getOrElse { formatPoints(n) } // i18n-ignore: an ICU pattern, not text

/** [ordinal] in the language shown now. */
@Composable
@ReadOnlyComposable
fun ordinalLabel(n: Int): String = ordinal(n, LocalConfiguration.current.locales[0] ?: Locale.getDefault())

/** Who gets which band of a finished tournament ("The top 10%"). */
@StringRes
fun tierRule(tier: TournamentTier): Int = when (tier) {
    TournamentTier.CHAMPION -> R.string.social_tournament_tier_champion
    TournamentTier.GOLD -> R.string.social_tournament_tier_gold
    TournamentTier.SILVER -> R.string.social_tournament_tier_silver
    TournamentTier.BRONZE -> R.string.social_tournament_tier_bronze
    TournamentTier.TOOK_PART -> R.string.social_tournament_tier_took_part
}

/** The weekly tournament: enter, attempts left, your best, the standings and the rewards. */
@Composable
fun TournamentScreen(vm: GameViewModel) {
    val social = vm.social
    val t = social.tournament
    val now = ShopClock.now()
    SubScreen(stringResource(R.string.social_tournament_title), onBack = vm::onBack) {
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SocialStatus(vm) { social.refreshTournament() }

            for (r in t?.rewards.orEmpty()) {
                SocialCard(stringResource(R.string.social_tournament_reward_title), Modifier.testTag("tournament_reward"), highlighted = true) {
                    Text(
                        stringResource(
                            R.string.social_tournament_reward_body,
                            ordinalLabel(r.rank),
                            formatPoints(r.participants),
                            pointsLabel(r.best.toLong()),
                            r.tier.label(),
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        stringResource(R.string.social_tournament_reward_grants, ShopText.seasonGrants(r.tier.grants, vm.progress).asString()),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Button(onClick = { social.claimReward(r) }, modifier = Modifier.testTag("tournament_claim")) {
                        Text(stringResource(R.string.social_tournament_claim))
                    }
                }
            }

            SocialCard(stringResource(R.string.social_tournament_water_title), Modifier.tourTarget(TourTarget.TOURNAMENT_ENTER)) {
                Hint(
                    stringResource(
                        R.string.social_tournament_water_body,
                        OnlineProtocol.TOURNAMENT_ROPE_LEVEL,
                        GameConfig.DESCENT_TOUCH_LIMIT,
                        GameConfig.TOURNAMENT_ATTEMPTS,
                    ),
                )
                if (t != null) {
                    val left = t.attemptsMax - t.attemptsUsed
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Stat(
                            stringResource(R.string.social_tournament_attempts_left),
                            stringResource(R.string.social_tournament_attempts_value, left, t.attemptsMax),
                            Modifier.testTag("tournament_attempts"),
                        )
                        Stat(stringResource(R.string.social_tournament_ends_in), timeLeftLabel(t.endsAtMs, now))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Stat(
                            stringResource(R.string.social_tournament_your_best),
                            t.best?.let { pointsLabel(it.toLong()) } ?: stringResource(R.string.social_tournament_no_score),
                            Modifier.testTag("tournament_best"),
                        )
                        Stat(
                            stringResource(R.string.social_tournament_your_rank),
                            t.rank?.let { stringResource(R.string.social_tournament_rank_value, ordinalLabel(it), formatPoints(t.participants)) }
                                ?: stringResource(R.string.social_tournament_not_ranked),
                        )
                    }
                    SightTackleChips(vm)
                    Button(
                        onClick = vm::startTournamentAttempt,
                        enabled = left > 0 && !social.busy,
                        modifier = Modifier.fillMaxWidth().height(56.dp).testTag("tournament_start"),
                    ) {
                        Text(
                            if (left > 0) {
                                stringResource(R.string.social_tournament_start, t.attemptsUsed + 1, t.attemptsMax)
                            } else {
                                stringResource(R.string.social_tournament_no_attempts)
                            },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                } else {
                    Hint(stringResource(R.string.social_tournament_waiting))
                }
            }

            social.lastAttempt?.let { a ->
                SocialCard(stringResource(R.string.social_tournament_attempt_title, a.attempt), Modifier.testTag("tournament_last")) {
                    Text(
                        stringResource(R.string.social_tournament_attempt_points, pointsLabel(a.points.toLong())),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Hint(
                        stringResource(
                            R.string.social_tournament_attempt_best,
                            pointsLabel(a.best.toLong()),
                            ordinalLabel(a.rank),
                            formatPoints(a.participants),
                        ),
                    )
                }
            }

            if (t != null) {
                SocialCard(stringResource(R.string.social_tournament_standings_title)) {
                    if (t.top.isEmpty()) {
                        Hint(stringResource(R.string.social_tournament_standings_none))
                    } else {
                        t.top.forEachIndexed { i, e ->
                            if (i > 0) HorizontalDivider()
                            BoardRow(BoardKind.BEST_CAST, e)
                        }
                    }
                }
            }

            SocialCard(stringResource(R.string.social_tournament_rewards_title)) {
                Hint(stringResource(R.string.social_tournament_rewards_body))
                for (tier in TournamentTier.entries) {
                    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(tierRule(tier)), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        Text(
                            ShopText.seasonGrants(tier.grants, vm.progress).asString(),
                            style = MaterialTheme.typography.bodySmall,
                            color = rewardTextColor(),
                            modifier = Modifier.weight(1.4f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Stat(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Hint(label)
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    }
}
