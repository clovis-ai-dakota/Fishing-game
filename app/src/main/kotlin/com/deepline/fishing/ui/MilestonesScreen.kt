package com.deepline.fishing.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.deepline.fishing.ui.kit.ActionRole
import com.deepline.fishing.ui.kit.CoinAmount
import com.deepline.fishing.ui.kit.GameButton
import com.deepline.fishing.ui.kit.GameColors
import com.deepline.fishing.ui.kit.InfoButton
import com.deepline.fishing.ui.kit.KitIcons
import com.deepline.fishing.ui.kit.ScenePill
import com.deepline.fishing.GameViewModel
import com.deepline.fishing.R
import com.deepline.fishing.ui.i18n.UiText
import com.deepline.fishing.ui.i18n.asString
import com.deepline.fishing.Screen
import com.deepline.fishing.core.Milestone
import com.deepline.fishing.core.Milestones
import com.deepline.fishing.core.Progression
import com.deepline.fishing.ui.tour.TourTarget
import com.deepline.fishing.ui.tour.tourTarget

/** "+500 pts · 1 Lantern": what a milestone gives. */
fun milestoneRewardText(m: Milestone): UiText = UiText.join(
    listOfNotNull(
        if (m.rewardPoints > 0) UiText.res(R.string.milestones_reward_points, UiText.number(m.rewardPoints)) else null,
        if (m.rewardLanterns > 0) UiText.plural(R.plurals.common_lanterns, m.rewardLanterns) else null,
    ),
    ITEM_SEPARATOR,
)

/**
 * The milestones: a ring for how close each one is, its goal and rewards as coins and pictograms,
 * and a green button when it's ready. What counts is behind the (i).
 */
@Composable
fun MilestonesScreen(vm: GameViewModel) {
    val p = vm.progress
    val scheme = MaterialTheme.colorScheme
    SubScreen(title = stringResource(R.string.milestones_title), onBack = { vm.navigate(Screen.HOME) }) {
        LazyColumn(contentPadding = PaddingValues(16.dp)) {
            item {
                val earned = stringResource(R.string.milestones_earned, formatPoints(p.pointsEarned))
                Row(Modifier.padding(bottom = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ScenePill(Modifier.semantics { contentDescription = earned }.testTag("milestones_earned")) {
                        Icon(GameIcons.Trophy, contentDescription = null, modifier = Modifier.size(16.dp))
                        CoinAmount(formatPoints(p.pointsEarned))
                    }
                    Spacer(Modifier.weight(1f))
                    ScenePill { InfoButton(stringResource(R.string.milestones_title), stringResource(R.string.milestones_explainer), Modifier.size(22.dp), tint = GameColors.OnDeep, tag = "milestones_info") }
                }
            }
            itemsIndexed(Milestones.all, key = { _, m -> m.index }) { i, m ->
                val claimed = m.index in p.claimedMilestones
                val reached = Progression.isMilestoneReached(p, m)
                val tagged = if (m.index == 0) Modifier.tourTarget(TourTarget.MILESTONES_FIRST) else Modifier
                val reward = milestoneRewardText(m).asString()
                val done = stringResource(R.string.milestones_claimed)
                // The road: one line down the list, filled as far as the points earned, with a
                // node at each milestone (how the most played games draw a reward track).
                val previous = Milestones.all.getOrNull(i - 1)?.threshold ?: 0
                val towards = ((p.pointsEarned - previous).toFloat() / (m.threshold - previous)).coerceIn(0f, 1f)
                val last = i == Milestones.all.lastIndex
                val line = scheme.primary
                val track = scheme.primary.copy(alpha = 0.22f)
                val nodeInk = if (reached) scheme.onPrimary else scheme.onSurfaceVariant
                Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(Modifier.width(34.dp).fillMaxHeight(), contentAlignment = Alignment.Center) {
                        Canvas(Modifier.fillMaxSize()) {
                            val x = size.width / 2f
                            val w = 6.dp.toPx()
                            val mid = size.height / 2f
                            drawLine(track, Offset(x, 0f), Offset(x, if (last) mid else size.height), strokeWidth = w)
                            // Up to this milestone: as far as the points go; past it: once it is reached.
                            if (towards > 0f) drawLine(line, Offset(x, 0f), Offset(x, mid * towards), strokeWidth = w)
                            if (reached && !last && p.pointsEarned > m.threshold) drawLine(line, Offset(x, mid), Offset(x, size.height), strokeWidth = w)
                            drawCircle(if (reached) line else lerp(track, Color.White, 0.75f), radius = 15.dp.toPx(), center = Offset(x, mid))
                            if (!reached) drawCircle(track, radius = 15.dp.toPx(), center = Offset(x, mid), style = Stroke(2.dp.toPx()))
                        }
                        if (claimed) {
                            Icon(KitIcons.Check, contentDescription = done, tint = nodeInk, modifier = Modifier.size(16.dp))
                        } else {
                            Icon(GameIcons.Trophy, contentDescription = null, tint = nodeInk, modifier = Modifier.size(16.dp))
                        }
                    }
                    Card(Modifier.weight(1f).padding(vertical = 5.dp).then(tagged)) {
                        Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                // The goal, then what it gives.
                                CoinAmount(formatPoints(m.threshold), Modifier.alpha(if (claimed) 0.6f else 1f), iconSize = 18.dp)
                                Row(
                                    Modifier.semantics { contentDescription = reward },
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    Icon(KitIcons.Gift, contentDescription = null, tint = scheme.onSurfaceVariant, modifier = Modifier.size(15.dp))
                                    ProvideTextStyle(MaterialTheme.typography.labelLarge) {
                                        if (m.rewardPoints > 0) CoinAmount("+" + formatPoints(m.rewardPoints), color = rewardTextColor(), iconSize = 14.dp)
                                        if (m.rewardLanterns > 0) {
                                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                                                Icon(GameIcons.Lantern, contentDescription = null, tint = scheme.primary, modifier = Modifier.size(15.dp))
                                                Text(stringResource(R.string.common_times, m.rewardLanterns), fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                }
                            }
                            if (reached && !claimed) {
                                GameButton(
                                    stringResource(R.string.milestones_claim),
                                    onClick = { vm.claimMilestone(m) },
                                    modifier = Modifier.testTag("milestone_claim_${m.index}"),
                                    role = ActionRole.CLAIM,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
