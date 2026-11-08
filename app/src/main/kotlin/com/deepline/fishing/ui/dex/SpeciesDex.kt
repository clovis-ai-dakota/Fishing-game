package com.deepline.fishing.ui.dex

import com.deepline.fishing.ui.i18n.label
import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.runtime.getValue
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.deepline.fishing.GameViewModel
import com.deepline.fishing.core.DepthZone
import com.deepline.fishing.core.DexState
import com.deepline.fishing.core.FishCatalog
import com.deepline.fishing.core.FishSpecies
import com.deepline.fishing.core.PlayerProgress
import com.deepline.fishing.core.Progression
import com.deepline.fishing.ui.GameIcons
import com.deepline.fishing.ui.SparkleGold
import com.deepline.fishing.ui.tour.TourTarget
import com.deepline.fishing.ui.tour.tourTarget

/** The Species section: a header with the completion ring, zone filters, and a card per species. */
@Composable
fun SpeciesDex(vm: GameViewModel) {
    val p = vm.progress
    var zoneName by rememberSaveable { mutableStateOf<String?>(null) }
    var caughtOnly by rememberSaveable { mutableStateOf(false) }
    val zone = zoneName?.let { n -> DepthZone.entries.firstOrNull { it.name == n } }
    val shown = FishCatalog.all.withIndex().filter { (_, s) ->
        (zone == null || s.zone == zone) && (!caughtOnly || Progression.dexState(p, s.id) == DexState.CAUGHT)
    }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 104.dp),
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(key = "header", span = { GridItemSpan(maxLineSpan) }) { DexHeader(p) }
        // A migration under way (nothing between migrations).
        item(key = "migration", span = { GridItemSpan(maxLineSpan) }) { com.deepline.fishing.ui.daily.MigrationBanner(vm) }
        item(key = "filters", span = { GridItemSpan(maxLineSpan) }) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .tourTarget(TourTarget.DEX_FILTERS),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                FilterChip(selected = zone == null, onClick = { zoneName = null }, label = { Text("All") })
                for (z in DepthZone.entries) {
                    FilterChip(
                        selected = zone == z,
                        onClick = { zoneName = z.name },
                        label = { Text(z.label()) },
                        leadingIcon = { Box(Modifier.size(10.dp).clip(CircleShape).background(zoneColor(z))) },
                    )
                }
                FilterChip(selected = caughtOnly, onClick = { caughtOnly = !caughtOnly }, label = { Text("Caught only") })
            }
        }
        items(shown, key = { it.value.id }) { (index, species) ->
            FishCard(index + 1, species, p, vm.cardStyle) { vm.openFishDetail(index) }
        }
        if (shown.isEmpty()) {
            item(key = "empty", span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    "No fish caught here yet. Keep fishing!",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
            }
        }
    }
}

/** The completion ring (caught, plus seen in a lighter shade) and the counts per zone. */
@Composable
private fun DexHeader(p: PlayerProgress) {
    val total = FishCatalog.all.size
    val caught = p.discoveredCount
    val seen = p.seenCount
    Card(Modifier.fillMaxWidth().tourTarget(TourTarget.DEX_HEADER)) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(72.dp), contentAlignment = Alignment.Center) {
                val track = MaterialTheme.colorScheme.surfaceVariant
                val gold = MaterialTheme.colorScheme.secondary
                Canvas(Modifier.size(72.dp)) {
                    val stroke = 8.dp.toPx()
                    val inset = stroke / 2f
                    val arcSize = Size(size.width - stroke, size.height - stroke)
                    val tl = Offset(inset, inset)
                    drawArc(track, -90f, 360f, false, tl, arcSize, style = Stroke(stroke))
                    drawArc(gold.copy(alpha = 0.35f), -90f, 360f * seen / total, false, tl, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
                    drawArc(gold, -90f, 360f * caught / total, false, tl, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
                }
                Text("${caught * 100 / total}%", fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleMedium)
            }
            Spacer(Modifier.width(14.dp))
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    "Seen $seen · Caught $caught / $total",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                for (z in DepthZone.entries) {
                    val inZone = FishCatalog.all.filter { it.zone == z }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(8.dp).clip(CircleShape).background(zoneColor(z)))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "${z.label()}  ${inZone.count(p::isDiscovered)}/${inZone.size}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

/** One numbered card: zone-coloured frame and water, the portrait, the name (or ???). */
@Composable
private fun FishCard(number: Int, species: FishSpecies, p: PlayerProgress, style: com.deepline.fishing.core.CardStyle, onClick: () -> Unit) {
    val state = Progression.dexState(p, species.id)
    val entry = p.collection[species.id]
    val frame = zoneColor(species.zone)
    // Caught cards wear the player's card style (a frame and a sheen; the fish is unchanged).
    val styled = state == DexState.CAUGHT && style != com.deepline.fishing.core.CardStyle.CLASSIC
    val t = if (styled) {
        val anim by androidx.compose.animation.core.rememberInfiniteTransition(label = "card").animateFloat(
            0f, 1f, androidx.compose.animation.core.infiniteRepeatable(androidx.compose.animation.core.tween(4_000, easing = androidx.compose.animation.core.LinearEasing)), label = "card time",
        )
        anim
    } else {
        0f
    }
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        border = if (styled) BorderStroke(2.5.dp, cardFrame(style, species.zone, t)) else BorderStroke(if (state == DexState.CAUGHT) 2.dp else 1.dp, frame.copy(alpha = if (state == DexState.UNKNOWN) 0.35f else 0.9f)),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
    ) {
        Column(
            Modifier
                .background(zoneBackground(species.zone))
                .then(if (styled) Modifier.drawWithContent { drawContent(); drawCardSheen(style, t) } else Modifier)
                .padding(6.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    dexNumberText(number),
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    color = Color.White.copy(alpha = 0.85f),
                    modifier = Modifier.weight(1f),
                )
                if (state == DexState.SEEN) {
                    Icon(GameIcons.Eye, contentDescription = "Seen", tint = Color.White.copy(alpha = 0.8f), modifier = Modifier.size(12.dp))
                }
                if ((entry?.sparklingCaught ?: 0) > 0) {
                    Icon(GameIcons.Sparkle, contentDescription = "Sparkling caught", tint = SparkleGold, modifier = Modifier.size(12.dp))
                }
            }
            FishPortrait(species, state, Modifier.fillMaxWidth().aspectRatio(1.5f))
            Text(
                if (state == DexState.UNKNOWN) "???" else species.label(),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = if (state == DexState.CAUGHT) Color.White else Color.White.copy(alpha = 0.7f),
                textAlign = TextAlign.Center,
                minLines = 2,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
