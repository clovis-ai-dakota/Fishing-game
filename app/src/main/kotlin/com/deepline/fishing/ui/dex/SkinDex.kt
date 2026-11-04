package com.deepline.fishing.ui.dex

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.TextButton
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.deepline.fishing.GameViewModel
import com.deepline.fishing.core.Atmosphere
import com.deepline.fishing.core.Cosmetic
import com.deepline.fishing.core.CosmeticSlot
import com.deepline.fishing.core.DepthZone
import com.deepline.fishing.ui.GameIcons
import com.deepline.fishing.ui.SkinPreview
import com.deepline.fishing.ui.TitleBadge
import com.deepline.fishing.ui.drawBoat
import com.deepline.fishing.ui.drawBoatTile
import com.deepline.fishing.ui.drawHook
import com.deepline.fishing.ui.drawHookAura
import com.deepline.fishing.ui.drawHookTrail
import com.deepline.fishing.ui.drawSkinnedLine
import com.deepline.fishing.ui.tour.TourTarget
import com.deepline.fishing.ui.tour.tourTarget
import kotlin.math.min

/**
 * The Skins section: a live preview of what you wear, then every legendary set as a row of slot
 * tiles. Tap a tile to wear it, tap it again to go back to the classic look. Locked tiles only
 * show their slot: the set and item names would hint at the secret that unlocks them.
 */
@Composable
fun SkinDex(vm: GameViewModel) {
    val p = vm.progress
    val unlocked = p.unlockedCosmetics.toSet()
    val sets = Cosmetic.entries.map { it.setName }.distinct()
    val time by rememberInfiniteTransition(label = "skin tiles").animateFloat(
        initialValue = 0f,
        targetValue = 60f,
        animationSpec = infiniteRepeatable(tween(60_000, easing = LinearEasing)),
        label = "time",
    )
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 100.dp),
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(key = "preview", span = { GridItemSpan(maxLineSpan) }) { SkinPreview(vm.skins, height = 240.dp) }
        item(key = "boat", span = { GridItemSpan(maxLineSpan) }) { YourBoatCard(vm, time) }
        item(key = "atmosphere", span = { GridItemSpan(maxLineSpan) }) { AtmosphereCard(vm) }
        item(key = "legendary", span = { GridItemSpan(maxLineSpan) }) {
            Text(
                "Legendary skins",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        item(key = "note", span = { GridItemSpan(maxLineSpan) }) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                vm.title?.let { t ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Your title: ")
                        TitleBadge(t)
                    }
                }
                Text(
                    "Skins only change how your gear looks: they never change how the game plays. " +
                        "Unlocked ${unlocked.size} of ${Cosmetic.entries.size}.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        for (set in sets) {
            val items = Cosmetic.entries.filter { it.setName == set }.sortedBy { it.slot.ordinal }
            val open = items.any { it in unlocked }
            item(key = "set-$set", span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    if (open) "$set set" else "Locked set",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (open) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            items(items, key = { it.name }) { c ->
                val equipped = p.equippedSkins[c.slot] == c
                SkinTile(c, c in unlocked, equipped, time) {
                    vm.equipSkin(c.slot, if (equipped) null else c)
                }
            }
        }
    }
}

@Composable
private fun SkinTile(c: Cosmetic, unlocked: Boolean, equipped: Boolean, time: Float, onClick: () -> Unit) {
    val border = when {
        equipped -> BorderStroke(2.dp, MaterialTheme.colorScheme.secondary)
        unlocked -> BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        else -> BorderStroke(1.dp, Color.White.copy(alpha = 0.12f))
    }
    val content: @Composable () -> Unit = {
        Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(1.2f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(TileWater),
                contentAlignment = Alignment.Center,
            ) {
                if (c.slot == CosmeticSlot.TITLE) {
                    if (unlocked) TitleBadge(c.displayName)
                } else if (unlocked) {
                    Canvas(Modifier.fillMaxSize()) { drawSlotArt(c.slot, c, time) }
                } else {
                    // The classic item's outline: nothing about the locked one shows.
                    Canvas(Modifier.fillMaxSize()) { drawSilhouette { drawSlotArt(c.slot, null, 0f) } }
                }
                if (!unlocked) {
                    Icon(GameIcons.Lock, contentDescription = "Locked", tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(22.dp))
                }
            }
            Text(
                if (unlocked) c.displayName else c.slot.displayName,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                minLines = 2,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                when {
                    !unlocked -> "Locked"
                    equipped -> "Equipped ✓"
                    else -> c.slot.displayName
                },
                style = MaterialTheme.typography.labelSmall,
                color = if (equipped) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
    if (unlocked) {
        Card(onClick = onClick, shape = RoundedCornerShape(14.dp), border = border) { content() }
    } else {
        Card(
            shape = RoundedCornerShape(14.dp),
            border = border,
            colors = CardDefaults.cardColors(containerColor = Color(0xFF071726)),
        ) { content() }
    }
}

private val TileWater = Brush.verticalGradient(listOf(Color(0xFF0288D1), Color(0xFF01396B)))

/** The hand-made boat: wear it, design it, or browse the shop's premade boats. */
@Composable
private fun YourBoatCard(vm: GameViewModel, time: Float) {
    val p = vm.progress
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Your boat", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Canvas(
                    Modifier
                        .size(width = 120.dp, height = 92.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .semantics { contentDescription = "Your hand-made boat" },
                ) { drawBoatTile(null, p.boatDesign, time) }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        "Paint and rig it yourself. Basic options are free; more unlock with points and as you progress.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (p.customBoatEquipped) {
                        TextButton(onClick = { vm.equipCustomBoat(false) }) { Text("Worn ✓ · use the classic boat") }
                    } else {
                        TextButton(onClick = { vm.equipCustomBoat(true) }) { Text("Wear my boat") }
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = vm::openBoatEditor, modifier = Modifier.weight(1f).tourTarget(TourTarget.SKINS_CUSTOMISE)) { Text("Customise boat") }
                OutlinedButton(onClick = vm::openShop, modifier = Modifier.weight(1f)) { Text("Premium skins") }
            }
        }
    }
}

/** Family (default) or Nightmare deep water: locked until the Abyssal Floor is reached. */
@Composable
private fun AtmosphereCard(vm: GameViewModel) {
    val p = vm.progress
    Card(Modifier.fillMaxWidth().tourTarget(TourTarget.SKINS_ATMOSPHERE), shape = RoundedCornerShape(14.dp)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Atmosphere", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                if (!p.atmosphereUnlocked) Icon(GameIcons.Lock, contentDescription = "Locked", modifier = Modifier.size(18.dp))
            }
            if (!p.atmosphereUnlocked) {
                Text(
                    "Reach the ${DepthZone.ABYSS.displayName} to unlock ${Atmosphere.NIGHTMARE.displayName}: a frightening " +
                        "look for the deep water.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    Atmosphere.entries.forEachIndexed { i, a ->
                        SegmentedButton(
                            selected = p.atmosphere == a,
                            onClick = { vm.setAtmosphere(a) },
                            shape = SegmentedButtonDefaults.itemShape(i, Atmosphere.entries.size),
                        ) { Text(a.displayName) }
                    }
                }
                Text(
                    if (p.atmosphere == Atmosphere.NIGHTMARE) {
                        "The deep is properly frightening. You still see exactly as much as before: only the mood changes."
                    } else {
                        "Harmless at the surface, spookier the deeper you go."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Draws [block] as a flat dark silhouette. */
private fun DrawScope.drawSilhouette(block: DrawScope.() -> Unit) {
    drawIntoCanvas { canvas ->
        val paint = Paint().apply { colorFilter = ColorFilter.tint(Color(0xFF0B1E33), BlendMode.SrcIn) }
        canvas.saveLayer(Rect(Offset.Zero, size), paint)
        block()
        canvas.restore()
    }
}

/** A small picture of [slot] wearing [c] (null = the classic look). */
private fun DrawScope.drawSlotArt(slot: CosmeticSlot, c: Cosmetic?, time: Float) {
    val w = size.width
    val h = size.height
    when (slot) {
        CosmeticSlot.HOOK -> {
            val s = min(w, h) * 0.2f
            val bend = Offset(w / 2f - 0.2f * s, h / 2f + 0.6f * s)
            drawHookAura(bend, s, time, c)
            drawHook(bend, s, time, withBait = false, skin = c)
        }
        CosmeticSlot.LINE -> {
            val a = Offset(w * 0.15f, h * 0.1f)
            val ctrl = Offset(w * 0.2f, h * 0.85f)
            val b = Offset(w * 0.85f, h * 0.9f)
            val points = (0..24).map { i ->
                val t = i / 24f
                val u = 1f - t
                Offset(u * u * a.x + 2 * u * t * ctrl.x + t * t * b.x, u * u * a.y + 2 * u * t * ctrl.y + t * t * b.y)
            }
            drawSkinnedLine(points, time, 0f, c)
        }
        CosmeticSlot.BOAT -> {
            drawRect(Brush.verticalGradient(listOf(Color(0xFF8FD3F4), Color(0xFFD7F1FF))), size = size.copy(height = h * 0.8f))
            drawBoat(w / 2f, h * 0.8f, min(w / 6f, h / 6.6f), time, c)
        }
        CosmeticSlot.TRAIL -> {
            val scale = h / 4.2f
            val s = min(w, h) * 0.12f
            val bend = Offset(w / 2f, h * 0.86f)
            drawHookTrail(bend.x, bend.y - 1.4f * s, scale, time, c)
            drawHook(bend, s, time, withBait = false)
        }
        CosmeticSlot.TITLE -> Unit
    }
}
