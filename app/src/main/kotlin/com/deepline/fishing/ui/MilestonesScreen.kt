package com.deepline.fishing.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
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

@Composable
fun MilestonesScreen(vm: GameViewModel) {
    val p = vm.progress
    SubScreen(title = stringResource(R.string.milestones_title), onBack = { vm.navigate(Screen.HOME) }) {
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Text(
                    stringResource(R.string.milestones_earned, formatPoints(p.pointsEarned)),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.secondary,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    stringResource(R.string.milestones_explainer),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            items(Milestones.all, key = { it.index }) { m ->
                val claimed = m.index in p.claimedMilestones
                val reached = Progression.isMilestoneReached(p, m)
                val tagged = if (m.index == 0) Modifier.tourTarget(TourTarget.MILESTONES_FIRST) else Modifier
                Card(Modifier.fillMaxWidth().then(tagged)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    stringResource(R.string.milestones_threshold, formatPoints(m.threshold)),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                )
                                Text(milestoneRewardText(m).asString(), style = MaterialTheme.typography.bodyMedium)
                            }
                            when {
                                claimed -> Text(stringResource(R.string.milestones_claimed), color = MaterialTheme.colorScheme.tertiary)
                                reached -> Button(onClick = { vm.claimMilestone(m) }) { Text(stringResource(R.string.milestones_claim)) }
                                else -> Text(
                                    stringResource(R.string.common_progress_of, formatPoints(p.pointsEarned), formatPoints(m.threshold)),
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                        if (!reached) {
                            LinearProgressIndicator(
                                progress = { (p.pointsEarned.toFloat() / m.threshold).coerceIn(0f, 1f) },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            }
        }
    }
}
