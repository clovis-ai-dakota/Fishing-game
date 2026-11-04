package com.deepline.fishing.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
    val box = modifier
        .fillMaxWidth()
        .height(height)
        .clip(RoundedCornerShape(12.dp))
        .let { m ->
            if (onClick == null) m
            else m.clickable(onClickLabel = clickLabel, role = Role.Button, onClick = onClick)
                .semantics { contentDescription = clickLabel ?: "Boat" }
        }
    // 3D where the phone supports it, otherwise (and in tests) the 2D scene.
    com.deepline.fishing.ui.render3d.gl.SkinPreview3d(skins, box) {
        Canvas(Modifier.matchParentSize()) {
            // Framed on the boat, the line and the hook, with room for the masts.
            drawCastScene(session, 0.8f + time, measurer, skins = skins, cameraTop = -6.5f)
        }
    }
}
