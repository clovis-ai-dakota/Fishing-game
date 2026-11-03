package com.deepline.fishing.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.deepline.fishing.GameViewModel
import com.deepline.fishing.Screen
import com.deepline.fishing.core.CastSession
import com.deepline.fishing.core.Cosmetic
import com.deepline.fishing.core.CosmeticSlot
import com.deepline.fishing.core.RopeSpecs
import com.deepline.fishing.core.World

/**
 * Lets the player choose which unlocked cosmetics to wear, one per slot. Cosmetics are purely
 * visual and never change how the game plays.
 */
@Composable
fun SkinsScreen(vm: GameViewModel) {
    val p = vm.progress
    val unlocked = p.unlockedCosmetics
    val skins = Skins.of(p.equippedSkins)
    SubScreen(title = "Skins", onBack = { vm.navigate(Screen.HOME) }) {
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { SkinPreview(skins) }
            vm.title?.let { t -> item { Row(verticalAlignment = Alignment.CenterVertically) { Text("Your title: "); TitleBadge(t) } } }
            item {
                Text(
                    "Skins only change how your gear looks: they never change how the game plays.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            items(CosmeticSlot.entries) { slot ->
                val options = unlocked.filter { it.slot == slot }
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(slot.displayName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        SkinOption("Classic", "The standard look.", selected = p.equippedSkins[slot] == null) {
                            vm.equipSkin(slot, null)
                        }
                        for (c in options) {
                            SkinOption(c.displayName, c.description, selected = p.equippedSkins[slot] == c) {
                                vm.equipSkin(slot, c)
                            }
                        }
                    }
                }
            }
            if (unlocked.size < Cosmetic.entries.size) {
                item {
                    Text(
                        "Legendary fishing secrets unlock more skins.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun SkinOption(name: String, description: String, selected: Boolean, onSelect: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onSelect, role = Role.RadioButton)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Column(Modifier.padding(start = 8.dp)) {
            Text(name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
            Text(description, style = MaterialTheme.typography.bodySmall)
        }
    }
}

/** A title won with a legendary secret, shown under the player's name. */
@Composable
fun TitleBadge(title: String, modifier: Modifier = Modifier) {
    Text(
        "★ $title",
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(Brush.horizontalGradient(listOf(Color(0xFFFFA000), Color(0xFFFFE082), Color(0xFFFFA000))))
            .padding(horizontal = 10.dp, vertical = 2.dp),
        color = Color(0xFF3E2723),
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Bold,
        maxLines = 1,
    )
}

/** The boat, rod, line and hook near the surface, wearing [skins]. */
@Composable
private fun SkinPreview(skins: Skins) {
    val measurer = rememberTextMeasurer()
    val session = remember {
        CastSession(RopeSpecs(120f, 10f, 9f), false, World(emptyList(), emptyList())).also {
            while (it.hookDepth < 5f) it.update(1f / 60f, null)
        }
    }
    // Skins are animated: the preview plays them (tests run with animations paused).
    val time by rememberInfiniteTransition(label = "skins").animateFloat(
        initialValue = 0f,
        targetValue = 60f,
        animationSpec = infiniteRepeatable(tween(60_000, easing = LinearEasing)),
        label = "time",
    )
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(360.dp)
            .clip(RoundedCornerShape(12.dp)),
    ) {
        // Framed on the boat, the line and the hook, with room for the masts.
        drawCastScene(session, 0.8f + time, measurer, skins = skins, cameraTop = -6.5f)
    }
}
