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
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.deepline.fishing.GameViewModel
import com.deepline.fishing.R
import com.deepline.fishing.ui.i18n.asString
import com.deepline.fishing.ui.i18n.label
import com.deepline.fishing.ui.i18n.shortLabel
import com.deepline.fishing.DexSection
import com.deepline.fishing.ShopPage
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
        title = stringResource(R.string.app_name),
        subtitle = {
            val t = vm.title
            val frame = vm.titleFrame
            if (t != null) {
                TitleBadge(t, Modifier.padding(top = 2.dp), frame = frame)
            } else if (frame != com.deepline.fishing.core.TitleFrame.GOLD) {
                // A badge frame without a title yet: the player's name, framed.
                TitleBadge(vm.playerName, Modifier.padding(top = 2.dp), frame = frame)
            } else {
                Text(
                    stringResource(R.string.home_tagline),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        actions = {
            PointsPill(p.pointsBalance, Modifier.tourTarget(TourTarget.HOME_POINTS))
            val claimable = Progression.claimableCount(p)
            BadgedBox(
                badge = { if (claimable > 0) Badge { Text(formatPoints(claimable)) } },
                modifier = Modifier.tourTarget(TourTarget.HOME_MILESTONES),
            ) {
                IconButton(onClick = { vm.navigate(Screen.MILESTONES) }) {
                    Icon(GameIcons.Trophy, contentDescription = stringResource(R.string.home_cd_milestones), tint = MaterialTheme.colorScheme.secondary)
                }
            }
            IconButton(onClick = vm::openThemes, modifier = Modifier.tourTarget(TourTarget.HOME_THEMES)) {
                Icon(GameIcons.Palette, contentDescription = stringResource(R.string.home_cd_themes), tint = MaterialTheme.colorScheme.primary)
            }
            IconButton(onClick = vm::openSettings, modifier = Modifier.tourTarget(TourTarget.HOME_SETTINGS)) {
                Icon(GameIcons.Settings, contentDescription = stringResource(R.string.home_cd_settings))
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
            // Once per machine-translated language: the translation may be approximate.
            com.deepline.fishing.ui.i18n.MachineTranslationNotice()
            // Your boat at the surface, wearing your skins (live 3D where available). Tap it to
            // customise it in the Dex's Skins section.
            SkinPreview(
                vm.skins,
                height = 190.dp,
                modifier = Modifier.tourTarget(TourTarget.HOME_BOAT),
                onClick = { vm.openDex(DexSection.SKINS) },
                clickLabel = stringResource(R.string.home_boat_action),
            )
            // The daily loop: a migration under way, then the daily reward and challenges.
            com.deepline.fishing.ui.daily.MigrationBanner(vm)
            com.deepline.fishing.ui.daily.DailyCard(vm, Modifier.tourTarget(TourTarget.HOME_DAILY))
            DepthCard(vm)
            DexProgressCard(vm)
            MilestoneStrip(vm)
            TextButton(onClick = { showRules = true }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                Text(stringResource(R.string.home_rules_title))
            }
        }
        CastArea(vm)
    }

    if (showRules) {
        AlertDialog(
            onDismissRequest = { showRules = false },
            title = { Text(stringResource(R.string.home_rules_title)) },
            text = { Text(rulesText(), style = MaterialTheme.typography.bodyMedium) },
            confirmButton = { TextButton(onClick = { showRules = false }) { Text(stringResource(R.string.common_got_it)) } },
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
            // From a million on, "1.2M": the exact balance is on the Shop tab, and the game's name keeps its room.
            Text(if (points >= 1_000_000) compactPoints(points) else formatPoints(points), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary)
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
    Card(onClick = { vm.openShop(ShopPage.ROPE) }, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.home_rope_title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Text(
                    stringResource(R.string.home_rope_reach, metersText(specs.lengthM).asString(), reachZone.label()),
                    style = MaterialTheme.typography.labelLarge,
                    color = com.deepline.fishing.ui.dex.zoneTextColor(reachZone),
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
                        Text(stat.shortLabel(), style = MaterialTheme.typography.labelSmall)
                        Text(
                            formatStat(stat, stat.valueAt(p.level(stat))),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
            Text(
                if (deepest > 0f) {
                    stringResource(R.string.home_rope_stats_deepest, formatPoints(p.castsMade), formatPoints(p.pointsEarned), metersText(deepest).asString())
                } else {
                    stringResource(R.string.home_rope_stats, formatPoints(p.castsMade), formatPoints(p.pointsEarned))
                },
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
                Text(stringResource(R.string.home_dex_title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Text(
                    stringResource(R.string.home_dex_caught, p.discoveredCount, total),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.secondary,
                    fontWeight = FontWeight.Bold,
                )
            }
            LinearProgressIndicator(progress = { p.discoveredCount.toFloat() / total }, modifier = Modifier.fillMaxWidth())
            val zones = DepthZone.entries.map { zone ->
                val species = FishCatalog.all.filter { it.zone == zone }
                stringResource(R.string.home_dex_zone, zone.shortLabel(), species.count(p::isDiscovered), species.size)
            }
            Text(
                stringResource(R.string.home_dex_seen, p.seenCount) + ITEM_SEPARATOR + zones.joinToString(ITEM_SEPARATOR),
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
                Text(stringResource(R.string.home_milestones_title), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                val claimable = Progression.claimableCount(p)
                if (claimable > 0) {
                    Text(pluralStringResource(R.plurals.home_milestones_to_claim, claimable, claimable), color = MaterialTheme.colorScheme.secondary, fontWeight = FontWeight.Bold)
                }
            }
            if (next != null) {
                LinearProgressIndicator(
                    progress = { (p.pointsEarned.toFloat() / next.threshold).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    stringResource(R.string.home_milestones_next, formatPoints(p.pointsEarned), formatPoints(next.threshold), milestoneRewardText(next).asString()),
                    style = MaterialTheme.typography.bodySmall,
                )
            } else {
                Text(stringResource(R.string.home_milestones_done), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

/** The thumb zone: the Lantern switch and the big Cast line button. */
@Composable
private fun CastArea(vm: GameViewModel) {
    val p = vm.progress
    // The cast area sits on the same frosted bar as the tabs below it.
    Surface(color = com.deepline.fishing.ui.theme.LocalMenuPalette.current.bar, modifier = Modifier.fillMaxWidth()) {
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
                    Text(stringResource(R.string.home_lanterns, p.lanterns), style = MaterialTheme.typography.titleSmall)
                    Text(
                        stringResource(R.string.home_lanterns_hint, GameConfig.DARK_DEPTH.roundToInt()),
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
            // The other tackle, once the player owns some (bought in the Shop).
            if (p.luckyCharms > 0 || p.sparkleLures > 0 || p.detectors > 0) {
                // Wraps onto a second line rather than hiding a chip past the edge (French and Spanish names are long).
                androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (p.detectors > 0) {
                        FilterChip(
                            selected = vm.useDetector,
                            onClick = { vm.toggleDetector(!vm.useDetector) },
                            label = { Text(stringResource(R.string.home_tackle_detector, p.detectors)) },
                        )
                    }
                    if (p.luckyCharms > 0) {
                        FilterChip(
                            selected = vm.useCharm,
                            onClick = { vm.toggleCharm(!vm.useCharm) },
                            label = { Text(stringResource(R.string.home_tackle_charm, p.luckyCharms)) },
                        )
                    }
                    if (p.sparkleLures > 0) {
                        FilterChip(
                            selected = vm.useLure,
                            onClick = { vm.toggleLure(!vm.useLure) },
                            label = { Text(stringResource(R.string.home_tackle_lure, p.sparkleLures)) },
                        )
                    }
                }
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
                Text(stringResource(R.string.home_cast_button), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun rulesText(): String = stringResource(
    R.string.home_rules_body,
    GameConfig.DESCENT_TOUCH_LIMIT,
    GameConfig.BUMP_FREE_DEPTH.toInt(),
    GameConfig.SPARKLING_MULTIPLIER,
    GameConfig.DARK_DEPTH.roundToInt(),
)
