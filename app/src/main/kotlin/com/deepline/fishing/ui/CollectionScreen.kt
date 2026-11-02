package com.deepline.fishing.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.deepline.fishing.GameViewModel
import com.deepline.fishing.Screen
import com.deepline.fishing.core.DepthZone
import com.deepline.fishing.core.FishCatalog
import kotlin.math.roundToInt

private fun zoneRange(zone: DepthZone): String =
    if (zone.maxDepth == Float.MAX_VALUE) "${zone.minDepth.roundToInt()} m+"
    else "${zone.minDepth.roundToInt()}–${zone.maxDepth.roundToInt()} m"

@Composable
fun CollectionScreen(vm: GameViewModel) {
    val p = vm.progress
    SubScreen(title = "Fish log", onBack = { vm.navigate(Screen.HOME) }) {
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Text(
                    "Discovered ${p.discoveredCount} / ${FishCatalog.all.size}",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.secondary,
                    fontWeight = FontWeight.Bold,
                )
            }
            for (zone in DepthZone.entries) {
                val species = FishCatalog.all.filter { it.zone == zone }
                if (species.isEmpty()) continue
                item(key = "zone-${zone.name}") {
                    Text(
                        "${zone.displayName} · ${zoneRange(zone)}",
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                items(species, key = { it.id }) { s ->
                    val entry = p.collection[s.id]
                    val discovered = entry != null && entry.caught > 0
                    Card(Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Canvas(Modifier.size(width = 72.dp, height = 40.dp)) {
                                drawFish(
                                    species = s,
                                    center = Offset(size.width / 2f, size.height / 2f),
                                    lengthPx = size.width * 0.9f,
                                    direction = 1,
                                    sparkling = discovered && entry!!.sparklingCaught > 0,
                                    timeSeconds = 0f,
                                    silhouette = !discovered,
                                )
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    if (discovered) s.name else "???",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                )
                                if (discovered) {
                                    Text(
                                        "%.1f kg · %d base pts".format(s.weightKg, s.basePoints),
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                    Text(
                                        "Caught ${entry!!.caught} · Sparkling ${entry.sparklingCaught} · " +
                                            "Deepest ${entry.deepestM.roundToInt()} m · Best ${formatPoints(entry.bestPoints)} pts",
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                } else {
                                    Text(
                                        "Not caught yet. Lives around ${s.minDepth.roundToInt()} m and deeper.",
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
