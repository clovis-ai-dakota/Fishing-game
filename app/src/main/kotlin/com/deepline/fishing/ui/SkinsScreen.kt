package com.deepline.fishing.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
 * A title (won with a legendary secret, or bought), shown under the player's name, in its
 * [frame] (see `TitleFrame`): looks only. Animated frames move slowly.
 */
@Composable
fun TitleBadge(title: String, modifier: Modifier = Modifier, frame: com.deepline.fishing.core.TitleFrame = com.deepline.fishing.core.TitleFrame.GOLD) {
    val t by rememberInfiniteTransition(label = "badge").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(6_000, easing = LinearEasing)),
        label = "badge time",
    )
    val look = badgeLook(frame, t)
    Text(
        // Titles travel by their English name (online too): shown in the game's language.
        look.icon + " " + com.deepline.fishing.ui.i18n.titleLabel(title),
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(look.fill)
            .then(if (look.border != null) Modifier.border(1.5.dp, look.border, RoundedCornerShape(50)) else Modifier)
            .padding(horizontal = 10.dp, vertical = 2.dp),
        color = look.ink,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Bold,
        maxLines = 1,
    )
}

private class BadgeLook(val fill: Brush, val ink: Color, val icon: String = "★", val border: Color? = null)

/** Each frame's colours; [t] (0..1, looping) moves the animated ones. */
private fun badgeLook(frame: com.deepline.fishing.core.TitleFrame, t: Float): BadgeLook {
    fun shift(colors: List<Color>): Brush {
        // A gradient that slides along the badge.
        val n = colors.size
        val k = (t * n).toInt() % n
        return Brush.horizontalGradient(List(n + 1) { colors[(it + k) % n] })
    }
    return when (frame) {
        com.deepline.fishing.core.TitleFrame.GOLD -> BadgeLook(Brush.horizontalGradient(listOf(Color(0xFFFFA000), Color(0xFFFFE082), Color(0xFFFFA000))), Color(0xFF3E2723))
        com.deepline.fishing.core.TitleFrame.RAINBOW -> BadgeLook(
            shift(listOf(Color(0xFFFF8A80), Color(0xFFFFD180), Color(0xFFFFFF8D), Color(0xFFB9F6CA), Color(0xFF80D8FF), Color(0xFFB388FF))),
            Color(0xFF1A1A2E), "✦",
        )
        com.deepline.fishing.core.TitleFrame.OCEAN -> BadgeLook(shift(listOf(Color(0xFF00838F), Color(0xFF26C6DA), Color(0xFF1565C0))), Color.White, "≈")
        com.deepline.fishing.core.TitleFrame.EMBER -> BadgeLook(shift(listOf(Color(0xFFBF360C), Color(0xFFFF7043), Color(0xFFFFB74D))), Color.White, "♨")
        com.deepline.fishing.core.TitleFrame.CLUB -> BadgeLook(shift(listOf(Color(0xFF64FFDA), Color(0xFFB388FF), Color(0xFF80D8FF))), Color(0xFF0D1B2A), "⚓")
        com.deepline.fishing.core.TitleFrame.CLUB_FOREVER -> BadgeLook(shift(listOf(Color(0xFF64FFDA), Color(0xFFB388FF), Color(0xFF80D8FF))), Color(0xFF0D1B2A), "⚓", Color(0xFFFFD54F))
        com.deepline.fishing.core.TitleFrame.SEASON -> BadgeLook(shift(listOf(Color(0xFFB39DDB), Color(0xFFE1BEE7), Color(0xFF9FA8DA))), Color(0xFF1A1033), "❖", Color(0xFFECEFF1))
        com.deepline.fishing.core.TitleFrame.COLLECTOR -> BadgeLook(Brush.horizontalGradient(listOf(Color(0xFF111111), Color(0xFF2E2E2E), Color(0xFF111111))), Color(0xFFFFD54F), "♛", Color(0xFFFFD54F))
        com.deepline.fishing.core.TitleFrame.EVENT -> BadgeLook(shift(listOf(Color(0xFFFF4081), Color(0xFFFF80AB), Color(0xFFFFAB40))), Color.White, "✧")
        // Won in the weekly tournament: polished silver with a gold rim.
        com.deepline.fishing.core.TitleFrame.TOURNAMENT -> BadgeLook(shift(listOf(Color(0xFF90A4AE), Color(0xFFECEFF1), Color(0xFFB0BEC5))), Color(0xFF102027), "♜", Color(0xFFFFC107))
    }
}

/** The boat, rod, line and hook near the surface, wearing [skins] (the Dex's Skins section). */
@Composable
fun SkinPreview(
    skins: Skins,
    height: androidx.compose.ui.unit.Dp = 360.dp,
    modifier: Modifier = Modifier,
    /** When set, the preview is a button (the Fish tab's boat opens the Skins section). */
    onClick: (() -> Unit)? = null,
    clickLabel: String? = null,
) {
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
    val description = clickLabel ?: androidx.compose.ui.res.stringResource(com.deepline.fishing.R.string.core_slot_boat)
    val box = modifier
        .fillMaxWidth()
        .height(height)
        .clip(RoundedCornerShape(12.dp))
        .let { m ->
            if (onClick == null) m
            else m.clickable(onClickLabel = clickLabel, role = Role.Button, onClick = onClick)
                .semantics { contentDescription = description }
        }
    // 3D where the phone supports it, otherwise (and in tests) the 2D scene.
    com.deepline.fishing.ui.render3d.gl.SkinPreview3d(skins, box) {
        Canvas(Modifier.matchParentSize()) {
            // Framed on the boat, the line and the hook, with room for the masts.
            drawCastScene(session, 0.8f + time, measurer, skins = skins, cameraTop = -6.5f)
        }
    }
}
