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
import com.deepline.fishing.GameViewModel
import com.deepline.fishing.Screen
import com.deepline.fishing.core.Milestone
import com.deepline.fishing.core.Milestones
import com.deepline.fishing.core.Progression
import com.deepline.fishing.ui.tour.TourTarget
import com.deepline.fishing.ui.tour.tourTarget

fun milestoneRewardText(m: Milestone): String = listOfNotNull(
    if (m.rewardPoints > 0) "+${formatPoints(m.rewardPoints)} pts" else null,
    when {
        m.rewardLanterns == 1 -> "1 Lantern"
        m.rewardLanterns > 1 -> "${m.rewardLanterns} Lanterns"
        else -> null
    },
).joinToString(" · ")

@Composable
fun MilestonesScreen(vm: GameViewModel) {
    val p = vm.progress
    SubScreen(title = "Milestones", onBack = { vm.navigate(Screen.HOME) }) {
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Text(
                    "Earned in total: ${formatPoints(p.pointsEarned)} pts",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.secondary,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    "Milestones count every point you earn from fish, even after spending them on upgrades.",
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
                                    "${formatPoints(m.threshold)} pts",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                )
                                Text(milestoneRewardText(m), style = MaterialTheme.typography.bodyMedium)
                            }
                            when {
                                claimed -> Text("Claimed ✓", color = MaterialTheme.colorScheme.tertiary)
                                reached -> Button(onClick = { vm.claimMilestone(m) }) { Text("Claim") }
                                else -> Text(
                                    "${formatPoints(p.pointsEarned)} / ${formatPoints(m.threshold)}",
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
