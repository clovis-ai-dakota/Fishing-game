package com.deepline.fishing.ui.dex

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.lerp
import com.deepline.fishing.core.DexState
import com.deepline.fishing.core.FishSpecies
import com.deepline.fishing.ui.drawFish
import com.deepline.fishing.ui.drawFishGlow

/**
 * A species' picture in the Dex. Every Dex picture goes through here, so the art can change in
 * one place. [state] decides how much is shown: a dark silhouette when unknown, a silhouette
 * tinted with the fish's colour when seen, the fish itself once caught. [large] is the detail
 * page's portrait, which swims gently.
 */
@Composable
fun FishPortrait(species: FishSpecies, state: DexState, modifier: Modifier = Modifier, large: Boolean = false) {
    // A 3D render where the phone supports it; the 2D art while it renders, and otherwise.
    val look = when (state) {
        DexState.UNKNOWN -> com.deepline.fishing.ui.render3d.PortraitLook.UNKNOWN
        DexState.SEEN -> com.deepline.fishing.ui.render3d.PortraitLook.SEEN
        DexState.CAUGHT -> com.deepline.fishing.ui.render3d.PortraitLook.CAUGHT
    }
    com.deepline.fishing.ui.render3d.gl.FishPortrait3d(species, look, modifier, large = large) {
        FishPortrait2d(species, state, androidx.compose.ui.Modifier.matchParentSize(), large)
    }
}

@Composable
private fun androidx.compose.foundation.layout.BoxScope.FishPortrait2d(species: FishSpecies, state: DexState, modifier: Modifier, large: Boolean) {
    val time = if (large) {
        val t by rememberInfiniteTransition(label = "portrait").animateFloat(
            initialValue = 0f,
            targetValue = 20f,
            animationSpec = infiniteRepeatable(tween(20_000, easing = LinearEasing)),
            label = "swim",
        )
        0.4f + t
    } else {
        0.4f
    }
    Canvas(modifier) { drawPortrait(species, state, time) }
}

/** Seen fish are shown as a silhouette in a muted version of their own colour. */
fun seenTint(species: FishSpecies): Color = lerp(Color(species.bodyColor), Color(0xFF0B1E33), 0.45f)

private fun DrawScope.drawPortrait(species: FishSpecies, state: DexState, time: Float) {
    // Fit the whole fish (fins and lure included) inside the box.
    val byWidth = size.width * 0.82f
    val byHeight = size.height * 0.8f / (species.heightRatio * 1.7f + 0.1f)
    val length = minOf(byWidth, byHeight)
    val center = Offset(size.width / 2f, size.height / 2f)
    when (state) {
        DexState.UNKNOWN -> drawFish(species, center, length, 1, sparkling = false, timeSeconds = time, silhouette = true)
        DexState.SEEN -> drawIntoCanvas { canvas ->
            val paint = Paint().apply { colorFilter = ColorFilter.tint(seenTint(species), BlendMode.SrcIn) }
            canvas.saveLayer(Rect(Offset.Zero, size), paint)
            drawFish(species, center, length, 1, sparkling = false, timeSeconds = time)
            canvas.restore()
        }
        DexState.CAUGHT -> {
            drawFish(species, center, length, 1, sparkling = false, timeSeconds = time)
            if (species.glowing) drawFishGlow(species, center, length, 1, 0.7f)
        }
    }
}
