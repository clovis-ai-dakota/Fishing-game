package com.deepline.fishing.ui.tour

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.deepline.fishing.core.FishCatalog
import com.deepline.fishing.ui.SparkleGold
import com.deepline.fishing.ui.drawFish
import com.deepline.fishing.ui.drawHook
import kotlinx.coroutines.delay

/** Where tour targets are on screen, and how to scroll them into view. */
class TourTargets {
    val bounds = mutableStateMapOf<TourTarget, Rect>()
    @OptIn(ExperimentalFoundationApi::class)
    val requesters = HashMap<TourTarget, BringIntoViewRequester>()
}

val LocalTourTargets = staticCompositionLocalOf { TourTargets() }

/** Marks this element as something a tour step can highlight. */
@OptIn(ExperimentalFoundationApi::class)
fun Modifier.tourTarget(target: TourTarget): Modifier = composed {
    val targets = LocalTourTargets.current
    val requester = remember { BringIntoViewRequester() }
    DisposableEffect(targets, target) {
        targets.requesters[target] = requester
        onDispose {
            if (targets.requesters[target] === requester) {
                targets.requesters.remove(target)
                targets.bounds.remove(target)
            }
        }
    }
    this
        .bringIntoViewRequester(requester)
        .onGloballyPositioned { targets.bounds[target] = it.boundsInRoot() }
}

/** Dims the app, highlights the current step's target and shows the step card. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TourOverlay(session: TourSession, onNext: () -> Unit, onBack: () -> Unit, onSkip: () -> Unit) {
    val step = session.current
    val targets = LocalTourTargets.current

    LaunchedEffect(step.id) {
        val target = step.target ?: return@LaunchedEffect
        // The screen may have just changed: wait for the target to be laid out, then scroll to it.
        var requester = targets.requesters[target]
        var tries = 0
        while (requester == null && tries < 30) {
            delay(16)
            requester = targets.requesters[target]
            tries++
        }
        requester?.bringIntoView()
    }

    BackHandler { if (session.isFirst) onSkip() else onBack() }

    val rect = step.target?.let { targets.bounds[it] }
    val density = LocalDensity.current
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            // Swallow touches so the app underneath can't be used during the tour.
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) awaitPointerEvent().changes.forEach { it.consume() }
                }
            },
    ) {
        val heightPx = with(density) { maxHeight.toPx() }
        Canvas(Modifier.fillMaxSize()) {
            val dim = Color(0xD9000A14)
            if (rect == null) {
                drawRect(dim)
            } else {
                val pad = 8.dp.toPx()
                val radius = CornerRadius(16.dp.toPx())
                // Kept inside the screen (the bottom bar's target reaches the very edge).
                val edge = 2.dp.toPx()
                val hole = rect.inflate(pad).let {
                    Rect(maxOf(it.left, edge), maxOf(it.top, edge), minOf(it.right, size.width - edge), minOf(it.bottom, size.height - edge))
                }
                val path = Path().apply {
                    fillType = PathFillType.EvenOdd
                    addRect(Rect(Offset.Zero, size))
                    addRoundRect(RoundRect(hole, radius))
                }
                drawPath(path, dim)
                drawRoundRect(
                    SparkleGold,
                    topLeft = hole.topLeft,
                    size = hole.size,
                    cornerRadius = radius,
                    style = Stroke(width = 3.dp.toPx()),
                )
            }
        }
        val alignment = when {
            rect == null -> Alignment.Center
            rect.center.y < heightPx / 2f -> Alignment.BottomCenter
            else -> Alignment.TopCenter
        }
        TourCard(
            session = session,
            onNext = onNext,
            onBack = onBack,
            onSkip = onSkip,
            modifier = Modifier
                .align(alignment)
                .systemBarsPadding()
                .padding(16.dp)
                .widthIn(max = 520.dp),
        )
    }
}

@Composable
private fun TourCard(session: TourSession, onNext: () -> Unit, onBack: () -> Unit, onSkip: () -> Unit, modifier: Modifier) {
    val step = session.current
    Card(modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    (if (session.whatsNew) "What's new · " else "") + "${session.index + 1} / ${session.steps.size}",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onSkip) { Text("Skip tour") }
            }
            Text(step.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            step.art?.let { TourArtwork(it) }
            Text(step.body, style = MaterialTheme.typography.bodyMedium)
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (!session.isFirst) TextButton(onClick = onBack) { Text("Back") }
                Spacer(Modifier.weight(1f))
                Button(onClick = onNext) { Text(if (session.isLast) "Finish" else "Next") }
            }
        }
    }
}

private val ArtWater = Brush.verticalGradient(listOf(Color(0xFF4FC3F7), Color(0xFF0277BD)))
private val Good = Color(0xFF69F0AE)
private val Bad = Color(0xFFFF5252)

@Composable
private fun TourArtwork(art: TourArt) {
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(120.dp)
            .clip(RoundedCornerShape(12.dp)),
    ) {
        when (art) {
            TourArt.STEER -> drawSteer()
            TourArt.BUMPS -> drawBumps()
            TourArt.HEADS -> drawHeads()
            TourArt.WEIGHT -> drawWeight()
            TourArt.SPARKLING -> drawSparkling()
            TourArt.DARKNESS -> drawDarkness()
        }
    }
}

private fun DrawScope.arrow(from: Offset, to: Offset, color: Color) {
    val w = 4.dp.toPx()
    drawLine(color, from, to, strokeWidth = w, cap = StrokeCap.Round)
    val dir = (to - from) / (to - from).getDistance()
    val normal = Offset(-dir.y, dir.x)
    val head = 10.dp.toPx()
    drawLine(color, to, to - dir * head + normal * head * 0.6f, strokeWidth = w, cap = StrokeCap.Round)
    drawLine(color, to, to - dir * head - normal * head * 0.6f, strokeWidth = w, cap = StrokeCap.Round)
}

private fun DrawScope.check(at: Offset, s: Float) {
    val p = Path().apply {
        moveTo(at.x - s, at.y)
        lineTo(at.x - s * 0.3f, at.y + s * 0.7f)
        lineTo(at.x + s, at.y - s * 0.8f)
    }
    drawPath(p, Good, style = Stroke(width = s * 0.35f, cap = StrokeCap.Round))
}

private fun DrawScope.cross(at: Offset, s: Float) {
    drawLine(Bad, at + Offset(-s, -s), at + Offset(s, s), strokeWidth = s * 0.35f, cap = StrokeCap.Round)
    drawLine(Bad, at + Offset(-s, s), at + Offset(s, -s), strokeWidth = s * 0.35f, cap = StrokeCap.Round)
}

private fun DrawScope.drawSteer() {
    drawRect(ArtWater)
    val hook = Offset(size.width / 2f, size.height * 0.55f)
    drawLine(Color.White.copy(alpha = 0.85f), Offset(hook.x, 0f), Offset(hook.x, hook.y - 22.dp.toPx()), strokeWidth = 2f)
    drawHook(hook, 16.dp.toPx(), 0.3f)
    arrow(Offset(hook.x - 34.dp.toPx(), hook.y), Offset(hook.x - 90.dp.toPx(), hook.y), Color.White)
    arrow(Offset(hook.x + 34.dp.toPx(), hook.y), Offset(hook.x + 90.dp.toPx(), hook.y), Color.White)
    // A fingertip dragging.
    val finger = Offset(hook.x + 110.dp.toPx(), hook.y + 26.dp.toPx())
    drawCircle(Color.White.copy(alpha = 0.35f), radius = 22.dp.toPx(), center = finger)
    drawCircle(Color.White.copy(alpha = 0.85f), radius = 12.dp.toPx(), center = finger)
}

private fun DrawScope.drawBumps() {
    drawRect(ArtWater)
    // Bump-free band at the top.
    val band = size.height * 0.32f
    drawRect(Good.copy(alpha = 0.18f), size = androidx.compose.ui.geometry.Size(size.width, band))
    drawLine(Good, Offset(0f, band), Offset(size.width, band), strokeWidth = 2f)
    val hook = Offset(size.width * 0.45f, size.height * 0.62f)
    drawLine(Color.White.copy(alpha = 0.85f), Offset(hook.x, 0f), Offset(hook.x, hook.y - 20.dp.toPx()), strokeWidth = 2f)
    drawHook(hook, 14.dp.toPx(), 0.2f)
    drawCircle(Bad.copy(alpha = 0.8f), radius = 26.dp.toPx(), center = hook, style = Stroke(width = 3f))
    drawCircle(Color(0xFF546E7A), radius = 16.dp.toPx(), center = hook + Offset(26.dp.toPx(), 12.dp.toPx()))
    drawFish(FishCatalog.all[1], Offset(size.width * 0.18f, size.height * 0.16f), 54.dp.toPx(), 1, sparkling = false, timeSeconds = 0.5f)
    // Bumps left: 3 of 4.
    for (i in 0 until 4) {
        drawCircle(
            if (i < 3) Color(0xFF80DEEA) else Color.White.copy(alpha = 0.25f),
            radius = 7.dp.toPx(),
            center = Offset(size.width - (16 + i * 20).dp.toPx(), size.height - 18.dp.toPx()),
        )
    }
}

private fun DrawScope.drawHeads() {
    drawRect(ArtWater)
    val sp = FishCatalog.byId("dusk_snapper")!!
    val len = size.width * 0.34f
    val left = Offset(size.width * 0.25f, size.height * 0.55f)
    val right = Offset(size.width * 0.75f, size.height * 0.55f)
    drawFish(sp, left, len, 1, sparkling = false, timeSeconds = 0.2f)
    drawFish(sp, right, len, 1, sparkling = false, timeSeconds = 0.9f)
    val s = 11.dp.toPx()
    drawHook(left + Offset(len * 0.42f, -s * 0.2f), s, 0.1f, withBait = false)
    drawHook(right + Offset(-len * 0.45f, -s * 0.2f), s, 0.1f, withBait = false)
    check(Offset(left.x, size.height * 0.16f), 12.dp.toPx())
    cross(Offset(right.x, size.height * 0.16f), 10.dp.toPx())
}

private fun DrawScope.drawWeight() {
    drawRect(ArtWater)
    val hook = Offset(size.width * 0.35f, size.height * 0.22f)
    drawLine(Color.White.copy(alpha = 0.85f), Offset(hook.x, 0f), Offset(hook.x, hook.y - 18.dp.toPx()), strokeWidth = 2f)
    drawHook(hook, 12.dp.toPx(), 0f, withBait = false)
    val fish = listOf("sun_bream", "reed_perch", "coral_darter")
    fish.forEachIndexed { i, id ->
        val sp = FishCatalog.byId(id)!!
        val len = (54 - i * 8).dp.toPx()
        drawFish(sp, Offset(hook.x + (i - 1) * 14.dp.toPx(), hook.y + 10.dp.toPx() + len / 2f), len, 1, false, 0.4f + i, rotationDegrees = -90f)
    }
    // Load bar, nearly full.
    val barLeft = size.width * 0.58f
    val barTop = size.height * 0.45f
    val barW = size.width * 0.34f
    val barH = 12.dp.toPx()
    drawRoundRect(Color.White.copy(alpha = 0.25f), topLeft = Offset(barLeft, barTop), size = androidx.compose.ui.geometry.Size(barW, barH), cornerRadius = CornerRadius(barH / 2))
    drawRoundRect(Color(0xFFFFCA28), topLeft = Offset(barLeft, barTop), size = androidx.compose.ui.geometry.Size(barW * 0.9f, barH), cornerRadius = CornerRadius(barH / 2))
}

private fun DrawScope.drawSparkling() {
    drawRect(ArtWater)
    val sp = FishCatalog.byId("reef_grouper")!!
    drawFish(sp, Offset(size.width * 0.30f, size.height * 0.52f), size.width * 0.34f, 1, sparkling = true, timeSeconds = 0.3f)
    // "× 3" drawn as shapes: a cross and a stylised three.
    val c = Offset(size.width * 0.70f, size.height * 0.5f)
    val s = 12.dp.toPx()
    drawLine(SparkleGold, c + Offset(-s, -s), c + Offset(s, s), strokeWidth = 5.dp.toPx(), cap = StrokeCap.Round)
    drawLine(SparkleGold, c + Offset(-s, s), c + Offset(s, -s), strokeWidth = 5.dp.toPx(), cap = StrokeCap.Round)
    val t = c + Offset(3.2f * s, 0f)
    val three = Path().apply {
        moveTo(t.x - s, t.y - 1.6f * s)
        quadraticTo(t.x + 1.4f * s, t.y - 2.0f * s, t.x + 0.9f * s, t.y - 0.6f * s)
        quadraticTo(t.x + 0.4f * s, t.y, t.x - 0.2f * s, t.y)
        moveTo(t.x - 0.2f * s, t.y)
        quadraticTo(t.x + 1.6f * s, t.y + 0.2f * s, t.x + 0.7f * s, t.y + 1.4f * s)
        quadraticTo(t.x, t.y + 2.0f * s, t.x - s, t.y + 1.4f * s)
    }
    drawPath(three, SparkleGold, style = Stroke(width = 5.dp.toPx(), cap = StrokeCap.Round))
}

private fun DrawScope.drawDarkness() {
    val half = size.width / 2f
    for (side in 0..1) {
        val left = side * half
        val center = Offset(left + half / 2f, size.height / 2f)
        val radius = if (side == 0) 22.dp.toPx() else 52.dp.toPx()
        drawRect(Color(0xFF061A3A), topLeft = Offset(left, 0f), size = androidx.compose.ui.geometry.Size(half, size.height))
        val sp = FishCatalog.byId("ghost_hake")!!
        drawFish(sp, center + Offset(-30.dp.toPx(), -22.dp.toPx()), 50.dp.toPx(), 1, false, 0.4f)
        drawFish(FishCatalog.byId("needle_viper")!!, center + Offset(32.dp.toPx(), 26.dp.toPx()), 46.dp.toPx(), -1, false, 1f)
        drawRect(
            Brush.radialGradient(
                0f to Color.Transparent,
                0.6f to Color.Black.copy(alpha = 0.45f),
                1f to Color.Black.copy(alpha = 0.97f),
                center = center,
                radius = radius,
            ),
            topLeft = Offset(left, 0f),
            size = androidx.compose.ui.geometry.Size(half, size.height),
        )
        if (side == 1) {
            drawCircle(Brush.radialGradient(listOf(Color(0x50FFE082), Color.Transparent), center = center, radius = radius), radius = radius, center = center)
        }
        drawHook(center, 9.dp.toPx(), 0f)
    }
    drawLine(Color.White.copy(alpha = 0.4f), Offset(half, 0f), Offset(half, size.height), strokeWidth = 2f)
}
