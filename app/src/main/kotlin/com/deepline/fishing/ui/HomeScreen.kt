package com.deepline.fishing.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.deepline.fishing.GameViewModel
import com.deepline.fishing.DexSection
import com.deepline.fishing.MainTab
import com.deepline.fishing.Screen
import com.deepline.fishing.core.DepthZone
import com.deepline.fishing.core.FishCatalog
import com.deepline.fishing.core.GameConfig
import com.deepline.fishing.core.Progression
import com.deepline.fishing.core.RopeStat
import com.deepline.fishing.ui.dex.zoneColor
import com.deepline.fishing.ui.tour.TourTarget
import com.deepline.fishing.ui.tour.tourTarget
import kotlin.math.roundToInt

/**
 * The Fish tab: the game's hub. One dominant action, Cast line, sits at the bottom in the thumb
 * zone with the Lantern switch next to it; the top bar holds points, milestones and the tour.
 */
@Composable
fun HomeScreen(vm: GameViewModel) {
    val p = vm.progress
    var showRules by remember { mutableStateOf(false) }

    TabScreen(
        title = "Deep Line",
        subtitle = {
            val t = vm.title
            if (t != null) {
                TitleBadge(t, Modifier.padding(top = 2.dp))
            } else {
                Text(
                    "Catch every fish in the deep",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        actions = {
            PointsPill(p.pointsBalance, Modifier.tourTarget(TourTarget.HOME_POINTS))
            val claimable = Progression.claimableCount(p)
            BadgedBox(
                badge = { if (claimable > 0) Badge { Text("$claimable") } },
                modifier = Modifier.tourTarget(TourTarget.HOME_MILESTONES),
            ) {
                IconButton(onClick = { vm.navigate(Screen.MILESTONES) }) {
                    Icon(GameIcons.Trophy, contentDescription = "Milestones", tint = MaterialTheme.colorScheme.secondary)
                }
            }
            IconButton(onClick = vm::startTour, modifier = Modifier.tourTarget(TourTarget.HOME_TOUR)) {
                Icon(GameIcons.Help, contentDescription = "Take the tour")
            }
        },
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Your boat at the surface, wearing your skins (live 3D where available). Tap it to
            // customise it in the Dex's Skins section.
            SkinPreview(
                vm.skins,
                height = 190.dp,
                modifier = Modifier.tourTarget(TourTarget.HOME_BOAT),
                onClick = { vm.openDex(DexSection.SKINS) },
                clickLabel = "Customise your boat",
            )
            DepthCard(vm)
            DexProgressCard(vm)
            MilestoneStrip(vm)
            TextButton(onClick = { showRules = true }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                Text("How to fish")
            }
        }
        CastArea(vm)
    }

    if (showRules) {
        AlertDialog(
            onDismissRequest = { showRules = false },
            title = { Text("How to fish") },
            text = { Text(rulesText(), style = MaterialTheme.typography.bodyMedium) },
            confirmButton = { TextButton(onClick = { showRules = false }) { Text("Got it") } },
        )
    }
}

@Composable
private fun PointsPill(points: Long, modifier: Modifier = Modifier) {
    Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.surfaceVariant, modifier = modifier) {
        Row(
            Modifier.padding(start = 8.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(GameIcons.Coin, contentDescription = null, tint = SparkleGold, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(formatPoints(points), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary)
        }
    }
}

/** How deep the rope reaches, drawn over the depth zones, with the rope stats underneath. */
@Composable
private fun DepthCard(vm: GameViewModel) {
    val p = vm.progress
    val specs = p.specs
    val maxDepth = RopeStat.LENGTH.valueAt(RopeStat.LENGTH.maxLevel)
    val reachZone = DepthZone.of(specs.lengthM - 0.01f)
    val deepest = p.collection.values.maxOfOrNull { it.deepestM } ?: 0f
    Card(onClick = { vm.selectTab(MainTab.UPGRADES) }, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Your rope", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Text(
                    "Reaches ${specs.lengthM.roundToInt()} m · ${reachZone.displayName}",
                    style = MaterialTheme.typography.labelLarge,
                    color = zoneColor(reachZone),
                    fontWeight = FontWeight.Bold,
                )
            }
            Canvas(Modifier.fillMaxWidth().height(28.dp)) {
                val barTop = 4.dp.toPx()
                val barH = size.height - 8.dp.toPx()
                fun x(depth: Float) = (depth / maxDepth).coerceIn(0f, 1f) * size.width
                for (zone in DepthZone.entries) {
                    val from = x(zone.minDepth)
                    val to = x(minOf(zone.maxDepth, maxDepth))
                    val reached = zone.minDepth < specs.lengthM
                    drawRect(
                        zoneColor(zone).copy(alpha = if (reached) 0.9f else 0.25f),
                        topLeft = Offset(from, barTop),
                        size = Size(to - from, barH),
                    )
                }
                // Dashed: water the rope can't reach yet.
                drawLine(
                    Color.White.copy(alpha = 0.35f),
                    Offset(x(specs.lengthM), barTop + barH / 2f),
                    Offset(size.width, barTop + barH / 2f),
                    strokeWidth = 2f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f)),
                )
                if (deepest > 0f) {
                    drawCircle(SparkleGold, radius = 4.dp.toPx(), center = Offset(x(deepest), barTop + barH / 2f))
                }
                drawRoundRect(
                    Color.White,
                    topLeft = Offset(x(specs.lengthM) - 1.5.dp.toPx(), 0f),
                    size = Size(3.dp.toPx(), size.height),
                    cornerRadius = CornerRadius(2.dp.toPx()),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (stat in RopeStat.entries) {
                    Column(Modifier.weight(1f)) {
                        Text(stat.displayName.removePrefix("Rope "), style = MaterialTheme.typography.labelSmall)
                        Text(
                            formatStat(stat, stat.valueAt(p.level(stat))),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
            Text(
                "Casts: ${p.castsMade} · Earned in total: ${formatPoints(p.pointsEarned)} pts" +
                    if (deepest > 0f) " · Deepest catch ${deepest.roundToInt()} m" else "",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun DexProgressCard(vm: GameViewModel) {
    val p = vm.progress
    val total = FishCatalog.all.size
    Card(onClick = { vm.openDex() }, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(GameIcons.Dex, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Dex", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Text(
                    "Caught ${p.discoveredCount} / $total",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.secondary,
                    fontWeight = FontWeight.Bold,
                )
            }
            LinearProgressIndicator(progress = { p.discoveredCount.toFloat() / total }, modifier = Modifier.fillMaxWidth())
            Text(
                "Seen ${p.seenCount} · " + DepthZone.entries.joinToString("  ·  ") { zone ->
                    val species = FishCatalog.all.filter { it.zone == zone }
                    "${zone.displayName.substringAfterLast(' ')} ${species.count(p::isDiscovered)}/${species.size}"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun MilestoneStrip(vm: GameViewModel) {
    val p = vm.progress
    val next = Progression.nextMilestone(p)
    Card(onClick = { vm.navigate(Screen.MILESTONES) }, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(GameIcons.Trophy, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.secondary)
                Spacer(Modifier.width(8.dp))
                Text("Milestones", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                val claimable = Progression.claimableCount(p)
                if (claimable > 0) {
                    Text("$claimable to claim!", color = MaterialTheme.colorScheme.secondary, fontWeight = FontWeight.Bold)
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
}

/** The thumb zone: the Lantern switch and the big Cast line button. */
@Composable
private fun CastArea(vm: GameViewModel) {
    val p = vm.progress
    Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 3.dp, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().tourTarget(TourTarget.HOME_LANTERN),
            ) {
                Icon(
                    GameIcons.Lantern,
                    contentDescription = null,
                    tint = if (vm.useLantern && p.lanterns > 0) SparkleGold else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("Lanterns: ${p.lanterns}", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "Light up the dark below ${GameConfig.DARK_DEPTH.roundToInt()} m on the next cast.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = vm.useLantern && p.lanterns > 0,
                    onCheckedChange = vm::toggleLantern,
                    enabled = p.lanterns > 0,
                )
            }
            Button(
                onClick = vm::startCast,
                shape = RoundedCornerShape(20.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .tourTarget(TourTarget.HOME_CAST),
            ) {
                Icon(GameIcons.Fish, contentDescription = null)
                Spacer(Modifier.width(10.dp))
                Text("Cast line", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }
        }
    }
}

private fun rulesText(): String =
    "• Drag left and right to steer the hook.\n" +
        "• Going down: the hook sinks until it has bumped ${GameConfig.DESCENT_TOUCH_LIMIT} times " +
        "or runs out of rope. A bump is touching a fish's head, or a rock, driftwood or jelly; " +
        "brushing a fish's body or tail is free. The first ${GameConfig.BUMP_FREE_DEPTH.toInt()} m are bump-free.\n" +
        "• Coming up: nudge a fish on its head (the pale face by the eye) to hook it. " +
        "Touching the tail does nothing. The rope can only carry so much weight.\n" +
        "• Deeper catches are worth more points. Sparkling fish are worth " +
        "${GameConfig.SPARKLING_MULTIPLIER}× more.\n" +
        "• Below ${GameConfig.DARK_DEPTH.roundToInt()} m it gets dark. A lantern lights up more of the water."
