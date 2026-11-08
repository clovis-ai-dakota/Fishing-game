package com.deepline.fishing.ui.shop

import com.deepline.fishing.ui.i18n.label
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.deepline.fishing.GameViewModel
import com.deepline.fishing.ShopPage
import com.deepline.fishing.core.Decor
import com.deepline.fishing.core.FishCatalog
import com.deepline.fishing.core.FishSpecies
import com.deepline.fishing.core.PlayerProgress
import com.deepline.fishing.core.Showcase
import com.deepline.fishing.ui.SubScreen
import com.deepline.fishing.ui.drawFish
import com.deepline.fishing.ui.waterColor
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * The aquarium: caught fish swimming among the decor the player owns. Pick which species swim
 * here (the tank holds [Showcase.tankSize]); decor and a bigger tank are in the Shop. Looks only.
 */
@Composable
fun AquariumScreen(vm: GameViewModel) {
    val p = vm.progress
    val caught = FishCatalog.all.filter { p.isDiscovered(it) }
    val inTank = Showcase.tankFish(p).map { it.id }.toSet()
    SubScreen(title = "Aquarium", onBack = vm::onBack) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 110.dp),
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.testTag("aquarium_list"),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) { AquariumTank(p, height = 260.dp) }
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    "In the tank: ${inTank.size} / ${Showcase.tankSize(p)}" +
                        if (caught.isEmpty()) ". Catch fish to fill it." else ". Tap a species to add it or take it out.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text("Decor", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp))
            }
            items(Decor.entries.filter { Showcase.ownsDecor(p, it) }, key = { "d_" + it.name }) { d ->
                FilterChip(
                    selected = d.name !in p.hiddenDecor,
                    onClick = { vm.toggleDecor(d) },
                    label = { Text(d.label(), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                )
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                TextButton(onClick = { vm.openShop(ShopPage.AQUARIUM) }) { Text("More decor and a bigger tank in the Shop") }
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text("Your catches", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            items(caught, key = { it.id }) { s ->
                FilterChip(
                    selected = s.id in inTank,
                    onClick = { vm.toggleTankFish(s.id) },
                    label = { Text(s.label(), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    modifier = Modifier.testTag("tank_${s.id}"),
                )
            }
        }
    }
}

/** The tank itself, animated: shown on the aquarium screen and in the Shop's Aquarium page. */
@Composable
fun AquariumTank(p: PlayerProgress, height: Dp, modifier: Modifier = Modifier, extraDecor: Decor? = null, big: Boolean = Showcase.hasBigTank(p)) {
    val t by rememberInfiniteTransition(label = "tank").animateFloat(
        initialValue = 0f,
        targetValue = 120f,
        animationSpec = infiniteRepeatable(tween(120_000, easing = LinearEasing)),
        label = "tank time",
    )
    val fish = Showcase.tankFish(p)
    val decor = (Showcase.shownDecor(p) + listOfNotNull(extraDecor)).distinct()
    Canvas(
        modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(16.dp))
            .semantics { contentDescription = "Aquarium with ${fish.size} fish" },
    ) { drawTank(fish, decor, t, big) }
}

private fun DrawScope.drawTank(fish: List<FishSpecies>, decor: List<Decor>, t: Float, big: Boolean) {
    val w = size.width
    val h = size.height
    // Sunlit water from the colour script, a sandy floor and the glass's frame.
    drawRect(Brush.verticalGradient(listOf(waterColor(5f), waterColor(if (big) 60f else 45f))))
    val floor = h * 0.84f
    drawRect(Brush.verticalGradient(listOf(Color(0xFFE6CF9A), Color(0xFFC9A96E)), startY = floor, endY = h), topLeft = Offset(0f, floor), size = Size(w, h - floor))
    for (k in 0 until 5) {
        val x = w * (0.1f + 0.2f * k) + sin(t * 0.4f + k) * w * 0.02f
        val ray = Path().apply { moveTo(x - w * 0.03f, 0f); lineTo(x + w * 0.03f, 0f); lineTo(x + w * 0.1f, floor); lineTo(x - w * 0.02f, floor); close() }
        drawPath(ray, Color.White.copy(alpha = 0.06f))
    }
    for (d in decor) drawDecor(d, w, h, floor, t)
    // Fish swim back and forth at their own heights and speeds.
    val n = fish.size.coerceAtLeast(1)
    fish.forEachIndexed { i, s ->
        val lane = (i + 0.5f) / n
        val y = h * (0.12f + 0.62f * lane)
        val speed = 0.05f + 0.03f * ((i * 37) % 5)
        val phase = (t * speed + i * 0.37f) % 2f
        val forward = phase < 1f
        val u = if (forward) phase else 2f - phase
        val len = (h * 0.11f * (0.7f + 0.6f * ((s.bodyLengthM.coerceIn(0.2f, 3f)) / 3f))).coerceAtMost(w * 0.22f)
        val x = len + u * (w - 2f * len)
        drawFish(s, Offset(x, y + sin(t * 1.3f + i) * h * 0.02f), len, if (forward) 1 else -1, sparkling = false, timeSeconds = t, phase = i * 1.3f)
    }
    // Bubbles rising.
    for (k in 0 until 10) {
        val p = (t * 0.12f + k / 10f) % 1f
        val x = w * (0.08f + 0.09f * k) + sin(p * 8f + k) * 6f
        drawCircle(Color.White.copy(alpha = 0.5f * (1f - p)), radius = 3f + 4f * p, center = Offset(x, floor - p * floor), style = Stroke(width = 1.5f))
    }
    drawRect(Color.White.copy(alpha = 0.25f), style = Stroke(width = 6f))
}

private fun DrawScope.drawDecor(d: Decor, w: Float, h: Float, floor: Float, t: Float) {
    when (d) {
        Decor.PLANTS -> for (k in 0 until 4) {
            val x = w * (0.06f + 0.29f * k)
            val tall = h * (0.25f + 0.08f * (k % 2))
            val sway = sin(t * 1.2f + k) * w * 0.015f
            val leaf = Path().apply {
                moveTo(x, floor)
                quadraticTo(x - w * 0.03f + sway, floor - tall * 0.5f, x + sway * 2f, floor - tall)
                quadraticTo(x + w * 0.03f + sway, floor - tall * 0.5f, x + w * 0.012f, floor)
                close()
            }
            drawPath(leaf, Color(0xFF43A047).copy(alpha = 0.9f))
        }
        Decor.PEBBLES -> for (k in 0 until 12) {
            val x = w * ((k * 0.083f + 0.03f) % 1f)
            drawOval(listOf(Color(0xFF9E9E9E), Color(0xFFBCAAA4), Color(0xFF8D6E63))[k % 3], topLeft = Offset(x, floor + h * 0.03f + (k % 3) * 3f), size = Size(w * 0.035f, h * 0.025f))
        }
        Decor.SAND_CASTLE -> {
            val x = w * 0.70f
            val bw = w * 0.14f
            val bh = h * 0.14f
            drawRect(Color(0xFFE0C07E), topLeft = Offset(x, floor - bh), size = Size(bw, bh))
            for (k in 0 until 3) drawRect(Color(0xFFD7B46A), topLeft = Offset(x + k * bw * 0.4f, floor - bh * 1.45f), size = Size(bw * 0.22f, bh * 0.45f))
            drawRect(Color(0xFF6D4C41), topLeft = Offset(x + bw * 0.4f, floor - bh * 0.45f), size = Size(bw * 0.2f, bh * 0.45f))
        }
        Decor.BUBBLER -> {
            val c = Offset(w * 0.18f, floor - h * 0.07f)
            drawCircle(Color(0xFFB0BEC5), radius = h * 0.06f, center = c)
            drawCircle(Color(0xFF4FC3F7), radius = h * 0.035f, center = c + Offset(h * 0.01f, -h * 0.005f))
            for (k in 0 until 6) {
                val p = (t * 0.5f + k / 6f) % 1f
                drawCircle(Color.White.copy(alpha = 0.7f * (1f - p)), radius = 3f + 5f * p, center = c + Offset(sin(p * 9f) * 5f, -h * 0.07f - p * floor * 0.9f), style = Stroke(width = 1.5f))
            }
        }
        Decor.TREASURE_CHEST -> {
            val x = w * 0.42f
            val cw = w * 0.13f
            val ch = h * 0.08f
            val open = (0.5f + 0.5f * sin(t * 0.8f)).coerceIn(0f, 1f)
            drawRect(Color(0xFF6D4C41), topLeft = Offset(x, floor - ch), size = Size(cw, ch))
            drawRect(Color(0xFFFFD54F), topLeft = Offset(x + cw * 0.05f, floor - ch * 1.05f), size = Size(cw * 0.9f, ch * 0.2f * open))
            drawRect(Color(0xFF8D6E63), topLeft = Offset(x, floor - ch - ch * 0.5f * open - ch * 0.3f), size = Size(cw, ch * 0.3f))
            drawRect(Color(0xFFFFC107), topLeft = Offset(x + cw * 0.45f, floor - ch * 0.75f), size = Size(cw * 0.1f, ch * 0.25f))
        }
        Decor.CORAL_GARDEN -> for (k in 0 until 3) {
            val base = Offset(w * (0.52f + 0.1f * k), floor)
            val col = listOf(Color(0xFFFF8A80), Color(0xFFFFAB40), Color(0xFFF48FB1))[k]
            for (b in 0 until 4) {
                val a = -PI.toFloat() / 2f + (b - 1.5f) * 0.4f
                val end = base + Offset(cos(a), sin(a)) * (h * (0.1f + 0.03f * b))
                drawLine(col, base, end, strokeWidth = w * 0.012f, cap = StrokeCap.Round)
                drawCircle(col, radius = w * 0.01f, center = end)
            }
        }
        Decor.LANTERN_LIGHTS -> for (k in 0 until 7) {
            val x = w * (0.08f + 0.14f * k)
            val y = h * 0.05f + sin(k * 1.1f) * h * 0.015f
            val glow = 0.6f + 0.4f * sin(t * 2f + k)
            drawCircle(Color(0xFFFFE082).copy(alpha = 0.25f * glow), radius = h * 0.04f, center = Offset(x, y))
            drawCircle(Color(0xFFFFCA28), radius = h * 0.015f, center = Offset(x, y))
        }
        Decor.SUNKEN_SHIP -> {
            val x = w * 0.05f
            val hull = Path().apply {
                moveTo(x, floor - h * 0.12f)
                lineTo(x + w * 0.3f, floor - h * 0.17f)
                lineTo(x + w * 0.27f, floor)
                lineTo(x + w * 0.03f, floor)
                close()
            }
            drawPath(hull, Color(0xFF4E342E))
            drawLine(Color(0xFF3E2723), Offset(x + w * 0.15f, floor - h * 0.15f), Offset(x + w * 0.2f, floor - h * 0.42f), strokeWidth = w * 0.012f)
            for (k in 0 until 3) drawCircle(Color(0xFF1B0F0B), radius = h * 0.015f, center = Offset(x + w * (0.08f + 0.07f * k), floor - h * 0.08f))
        }
    }
}
