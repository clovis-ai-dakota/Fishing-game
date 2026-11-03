package com.deepline.fishing.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.deepline.fishing.GameViewModel
import com.deepline.fishing.Screen
import com.deepline.fishing.core.DepthZone
import com.deepline.fishing.core.FishCatalog
import com.deepline.fishing.core.GameConfig
import com.deepline.fishing.core.Progression
import com.deepline.fishing.ui.tour.TourTarget
import com.deepline.fishing.ui.tour.tourTarget
import kotlin.math.roundToInt

@Composable
fun HomeScreen(vm: GameViewModel) {
    val p = vm.progress
    val total = FishCatalog.all.size

    Column(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column {
            Text("Deep Line", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Black)
            Text(
                "Catch every fish in the deep",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Card(onClick = { vm.navigate(Screen.COLLECTION) }, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Fish log", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    Text(
                        "${p.discoveredCount} / $total",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.secondary,
                        fontWeight = FontWeight.Bold,
                    )
                }
                LinearProgressIndicator(
                    progress = { p.discoveredCount.toFloat() / total },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    DepthZone.entries.joinToString("  ·  ") { zone ->
                        val species = FishCatalog.all.filter { it.zone == zone }
                        "${zone.displayName} ${species.count(p::isDiscovered)}/${species.size}"
                    },
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }

        Card(Modifier.fillMaxWidth().tourTarget(TourTarget.HOME_POINTS)) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Points", style = MaterialTheme.typography.labelLarge)
                    Text(
                        formatPoints(p.pointsBalance),
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.secondary,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("Earned in total: ${formatPoints(p.pointsEarned)}", style = MaterialTheme.typography.bodySmall)
                    Text("Casts: ${p.castsMade}", style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        val next = Progression.nextMilestone(p)
        val claimable = Progression.claimableCount(p)
        Card(onClick = { vm.navigate(Screen.MILESTONES) }, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row {
                    Text("Milestones", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    if (claimable > 0) {
                        Text(
                            "$claimable to claim!",
                            color = MaterialTheme.colorScheme.secondary,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
                if (next != null) {
                    LinearProgressIndicator(
                        progress = { (p.pointsEarned.toFloat() / next.threshold).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        "${formatPoints(p.pointsEarned)} / ${formatPoints(next.threshold)} · next: ${milestoneRewardText(next)}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                } else {
                    Text("All milestones reached!", style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        val specs = p.specs
        Card(onClick = { vm.navigate(Screen.UPGRADES) }, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Your rope", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Length ${specs.lengthM.roundToInt()} m · Strength ${specs.maxWeightKg.roundToInt()} kg · " +
                        "Agility %.1f m/s".format(specs.steerSpeed),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }

        Card(Modifier.fillMaxWidth().tourTarget(TourTarget.HOME_LANTERN)) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Lanterns: ${p.lanterns}", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Use one on the next cast to see further below ${GameConfig.DARK_DEPTH.roundToInt()} m.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Switch(
                    checked = vm.useLantern && p.lanterns > 0,
                    onCheckedChange = vm::toggleLantern,
                    enabled = p.lanterns > 0,
                )
            }
        }

        Button(
            onClick = vm::startCast,
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .tourTarget(TourTarget.HOME_CAST),
        ) {
            Text("Cast line", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }

        FilledTonalButton(
            onClick = { vm.navigate(Screen.BATTLE_SETUP) },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .tourTarget(TourTarget.HOME_BATTLE),
        ) {
            Text("1v1 Battle", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { vm.navigate(Screen.UPGRADES) }, modifier = Modifier.weight(1f)) {
                Text("Upgrades")
            }
            OutlinedButton(onClick = { vm.navigate(Screen.COLLECTION) }, modifier = Modifier.weight(1f)) {
                Text("Log ${p.discoveredCount}/${FishCatalog.all.size}")
            }
            OutlinedButton(onClick = { vm.navigate(Screen.MILESTONES) }, modifier = Modifier.weight(1f)) {
                Text("Rewards")
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = vm::startTour,
                modifier = Modifier
                    .weight(1f)
                    .tourTarget(TourTarget.HOME_TOUR),
            ) {
                Text("Take the tour")
            }
            // Appears once the player has discovered a hidden mechanic.
            if (p.discoveredSecrets.isNotEmpty()) {
                OutlinedButton(onClick = { vm.navigate(Screen.SECRETS) }, modifier = Modifier.weight(1f)) {
                    Text("Fishing secrets")
                }
            }
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("How to fish", style = MaterialTheme.typography.titleMedium)
                Text(
                    "• Drag left and right to steer the hook.\n" +
                        "• Going down: the hook sinks until it has bumped ${GameConfig.DESCENT_TOUCH_LIMIT} times " +
                        "or runs out of rope. A bump is touching a fish's head, or a rock, driftwood or jelly; " +
                        "brushing a fish's body or tail is free. The first ${GameConfig.BUMP_FREE_DEPTH.toInt()} m are bump-free.\n" +
                        "• Coming up: nudge a fish on its head (the pale face by the eye) to hook it. " +
                        "Touching the tail does nothing. The rope can only carry so much weight.\n" +
                        "• Deeper catches are worth more points. Sparkling fish are worth " +
                        "${GameConfig.SPARKLING_MULTIPLIER}× more.\n" +
                        "• Below ${GameConfig.DARK_DEPTH.roundToInt()} m it gets dark. A lantern lights up more of the water.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}
