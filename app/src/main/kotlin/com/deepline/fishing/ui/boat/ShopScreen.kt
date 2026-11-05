package com.deepline.fishing.ui.boat

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.deepline.fishing.GameViewModel
import com.deepline.fishing.core.PremiumSkins
import com.deepline.fishing.ui.SkinPreview
import com.deepline.fishing.ui.SubScreen
import com.deepline.fishing.ui.drawBoatTile
import com.deepline.fishing.ui.tour.TourTarget
import com.deepline.fishing.ui.tour.tourTarget

/**
 * Premium skins: premade boats with prices. Payments aren't wired yet, so nothing can be bought
 * or worn: every button says "Coming soon". Tap a boat to preview it at the top.
 */
@Composable
fun ShopScreen(vm: GameViewModel) {
    var selected by remember { mutableStateOf(PremiumSkins.all.first()) }
    val time by rememberInfiniteTransition(label = "shop tiles").animateFloat(
        initialValue = 0f,
        targetValue = 60f,
        animationSpec = infiniteRepeatable(tween(60_000, easing = LinearEasing)),
        label = "time",
    )
    SubScreen(title = "Premium skins", onBack = vm::onBack) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 150.dp),
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.tourTarget(TourTarget.SHOP_ITEMS),
        ) {
            item(key = "preview", span = { GridItemSpan(maxLineSpan) }) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SkinPreview(vm.skins.copy(boat = null, customBoat = selected.design), height = 190.dp)
                    Text(selected.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        "Coming soon: premade boats you'll be able to buy as a shortcut to a great-looking boat. " +
                            "Payments aren't available yet, so these can't be bought or worn for now. Like every skin, " +
                            "they only change how your boat looks.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(PremiumSkins.all, key = { it.id }) { skin ->
                val isSelected = skin == selected
                Card(
                    onClick = { selected = skin },
                    shape = RoundedCornerShape(14.dp),
                    border = if (isSelected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                ) {
                    Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Canvas(
                            Modifier
                                .fillMaxWidth()
                                .aspectRatio(1.3f)
                                .clip(RoundedCornerShape(10.dp)),
                        ) { drawBoatTile(null, skin.design, time) }
                        Text(skin.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            skin.tagline,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            minLines = 2,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(com.deepline.fishing.core.ShopCatalog.byId("boat_${skin.id}")?.euroLabel ?: "", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.secondary, fontWeight = FontWeight.Bold)
                        Button(onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth()) { Text("Coming soon", maxLines = 1) }
                    }
                }
            }
        }
    }
}
