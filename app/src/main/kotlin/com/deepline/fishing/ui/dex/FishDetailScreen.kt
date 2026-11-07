package com.deepline.fishing.ui.dex

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.deepline.fishing.GameViewModel
import com.deepline.fishing.core.CollectionEntry
import com.deepline.fishing.core.DexState
import com.deepline.fishing.core.FishCatalog
import com.deepline.fishing.core.FishSpecies
import com.deepline.fishing.core.Progression
import com.deepline.fishing.ui.GameIcons
import com.deepline.fishing.ui.SparkleGold
import com.deepline.fishing.ui.SubScreen
import com.deepline.fishing.ui.formatPoints
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * A species' Dex page, full screen. Swipe (or use the arrows) to flip through the whole Dex.
 * Unknown species show only their depths; seen ones add their name; caught ones show everything.
 */
@Composable
fun FishDetailScreen(vm: GameViewModel) {
    val all = FishCatalog.all
    val pager = rememberPagerState(initialPage = vm.dexFishIndex.coerceIn(0, all.lastIndex)) { all.size }
    val scope = rememberCoroutineScope()
    LaunchedEffect(pager.currentPage) { vm.showFishDetail(pager.currentPage) }
    SubScreen(title = "Dex ${dexNumberText(pager.currentPage + 1)}", onBack = vm::onBack) {
        HorizontalPager(state = pager, modifier = Modifier.weight(1f)) { page ->
            FishPage(all[page], page + 1, Progression.dexState(vm.progress, all[page].id), vm.progress.collection[all[page].id])
        }
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = { scope.launch { pager.animateScrollToPage(pager.currentPage - 1) } },
                enabled = pager.currentPage > 0,
            ) { Icon(GameIcons.ChevronLeft, contentDescription = "Previous fish") }
            Text(
                "${pager.currentPage + 1} / ${all.size}",
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.weight(1f),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            IconButton(
                onClick = { scope.launch { pager.animateScrollToPage(pager.currentPage + 1) } },
                enabled = pager.currentPage < all.lastIndex,
            ) { Icon(GameIcons.ChevronRight, contentDescription = "Next fish") }
        }
    }
}

@Composable
private fun FishPage(species: FishSpecies, number: Int, state: DexState, entry: CollectionEntry?) {
    val frame = zoneColor(species.zone)
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(220.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(zoneBackground(species.zone))
                .border(BorderStroke(2.dp, frame), RoundedCornerShape(20.dp)),
        ) {
            FishPortrait(species, state, Modifier.fillMaxSize().padding(12.dp), large = true)
            Text(
                dexNumberText(number),
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                color = Color.White.copy(alpha = 0.9f),
                modifier = Modifier.align(Alignment.TopStart).padding(12.dp),
            )
            if ((entry?.sparklingCaught ?: 0) > 0) {
                Icon(
                    GameIcons.Sparkle,
                    contentDescription = "Sparkling caught",
                    tint = SparkleGold,
                    modifier = Modifier.align(Alignment.TopEnd).padding(12.dp).size(20.dp),
                )
            }
            Surface(
                color = Color.Black.copy(alpha = 0.35f),
                shape = RoundedCornerShape(50),
                modifier = Modifier.align(Alignment.BottomStart).padding(12.dp),
            ) {
                Text(
                    species.zone.displayName,
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                )
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                if (state == DexState.UNKNOWN) "???" else species.name,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Black,
            )
            Text(
                when (state) {
                    DexState.UNKNOWN -> "Not seen yet"
                    DexState.SEEN -> "Seen, not caught yet"
                    DexState.CAUGHT -> "Caught"
                },
                style = MaterialTheme.typography.labelLarge,
                color = if (state == DexState.CAUGHT) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        com.deepline.fishing.ui.daily.MigratingNote(species)
        val caught = state == DexState.CAUGHT
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatTile("Depth", "${species.minDepth.roundToInt()}–${species.maxDepth.roundToInt()} m", Modifier.weight(1f))
            StatTile("Weight", if (caught) "%.1f kg".format(species.weightKg) else "???", Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatTile("Base points", if (caught) formatPoints(species.basePoints) else "???", Modifier.weight(1f))
            Card(Modifier.weight(1f)) {
                Column(Modifier.padding(12.dp)) {
                    Text("Rarity", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (caught) {
                        Stars(Progression.rarityStars(species))
                    } else {
                        Text("???", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        if (caught && entry != null) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Your records", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Record("Caught", "${entry.caught}", Modifier.weight(1f))
                        Record("Sparkling", "${entry.sparklingCaught}", Modifier.weight(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Record("Deepest", "${entry.deepestM.roundToInt()} m", Modifier.weight(1f))
                        Record("Best", "${formatPoints(entry.bestPoints)} pts", Modifier.weight(1f))
                    }
                }
            }
        } else {
            Text(
                if (state == DexState.UNKNOWN) {
                    "Fish near these depths to spot it: anything that swims close to your hook is added to the Dex."
                } else {
                    "Catch one to fill in its weight, points, rarity and your records."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun StatTile(label: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier) {
        Column(Modifier.padding(12.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun Record(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
    }
}

/** Rarity as 1–5 gold stars. */
@Composable
fun Stars(count: Int, max: Int = 5) {
    Row(Modifier.padding(top = 2.dp)) {
        for (i in 1..max) {
            Icon(
                GameIcons.Star,
                contentDescription = null,
                tint = if (i <= count) SparkleGold else Color.White.copy(alpha = 0.15f),
                modifier = Modifier.size(18.dp),
            )
            if (i < max) Spacer(Modifier.width(1.dp))
        }
    }
}
