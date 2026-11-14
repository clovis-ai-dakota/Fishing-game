package com.deepline.fishing.ui.kit

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** How big a [GameButton] is. [HERO] is the screen's main action, in the thumb zone. */
enum class ButtonSize(val height: Dp, val edge: Dp, val radius: Dp, val icon: Dp, val padding: Dp) {
    SMALL(36.dp, 3.dp, 10.dp, 16.dp, 12.dp),
    MEDIUM(44.dp, 4.dp, 13.dp, 20.dp, 18.dp),
    HERO(60.dp, 6.dp, 18.dp, 26.dp, 24.dp),
}

/** Called when a kit button is pressed (the app plays its tap sound and haptic there). */
val LocalPressFeedback = staticCompositionLocalOf<(ActionRole) -> Unit> { {} }

/** How long a button takes to sink under the finger (see docs/ui-design.md). */
const val PRESS_MS = 90

/**
 * Draws a raised face on its edge: the edge is the full shape, the face sits [lift] above its
 * bottom and sinks by [pressed] (0 to 1) when held.
 */
internal fun DrawScope.drawRaised(c: RoleColors, radius: Float, lift: Float, pressed: Float) {
    val r = CornerRadius(radius)
    val down = lift * pressed
    // The edge (and a soft ground shadow that shrinks as the button sinks).
    drawRoundRect(Color.Black.copy(alpha = 0.22f * (1f - pressed)), topLeft = Offset(0f, lift * 0.6f), size = size, cornerRadius = r)
    drawRoundRect(c.edge, topLeft = Offset(0f, down), size = Size(size.width, size.height - down), cornerRadius = r)
    val face = Size(size.width, size.height - lift)
    drawRoundRect(Brush.verticalGradient(listOf(c.top, c.bottom), startY = down, endY = down + face.height), topLeft = Offset(0f, down), size = face, cornerRadius = r)
    // A soft gloss on the upper half, and a darker outline so the shape holds on any background.
    val inset = 2.dp.toPx()
    drawRoundRect(
        Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.34f), Color.White.copy(alpha = 0f)), startY = down + inset, endY = down + face.height * 0.55f),
        topLeft = Offset(inset, down + inset),
        size = Size(size.width - inset * 2f, face.height * 0.55f),
        cornerRadius = CornerRadius((radius - inset).coerceAtLeast(0f)),
    )
    drawRoundRect(c.edge.copy(alpha = 0.55f), topLeft = Offset(0f, down), size = face, cornerRadius = r, style = Stroke(width = 1.dp.toPx()))
}

/**
 * The game's button: a raised, coloured face that sinks when pressed. Its [role] decides its
 * colour (see [ActionRole]); a screen has at most one [ActionRole.PLAY] button.
 */
@Composable
fun GameButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    role: ActionRole = ActionRole.NAV,
    size: ButtonSize = ButtonSize.MEDIUM,
    enabled: Boolean = true,
    onClickLabel: String? = null,
    contentPadding: PaddingValues? = null,
    content: @Composable RowScope.() -> Unit,
) {
    val colors = roleColors(role, enabled)
    val interaction = remember { MutableInteractionSource() }
    val isPressed by interaction.collectIsPressedAsState()
    val pressed by animateFloatAsState(if (isPressed && enabled) 1f else 0f, tween(PRESS_MS), label = "press")
    val feedback = LocalPressFeedback.current
    // A claimed reward bursts out of its button.
    val rewards = if (role == ActionRole.CLAIM) LocalRewardFx.current else null
    val centre = remember { arrayOf(Offset.Unspecified) }
    Box(
        modifier
            .defaultMinSize(minWidth = size.height, minHeight = size.height + size.edge)
            .let { m -> if (rewards != null) m.onGloballyPositioned { centre[0] = it.boundsInRoot().center } else m }
            .drawBehind { drawRaised(colors, size.radius.toPx(), size.edge.toPx(), pressed) }
            .clickable(interactionSource = interaction, indication = null, enabled = enabled, onClickLabel = onClickLabel, role = Role.Button) {
                feedback(role)
                if (rewards != null && centre[0] != Offset.Unspecified) rewards.burst(centre[0])
                onClick()
            },
        contentAlignment = Alignment.Center,
    ) {
        Row(
            Modifier
                .padding(bottom = size.edge)
                .graphicsLayer { translationY = size.edge.toPx() * pressed }
                .padding(contentPadding ?: PaddingValues(horizontal = size.padding, vertical = 6.dp)),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(if (size == ButtonSize.SMALL) 5.dp else 8.dp, Alignment.CenterHorizontally),
        ) {
            val style = when (size) {
                ButtonSize.SMALL -> MaterialTheme.typography.labelLarge
                ButtonSize.MEDIUM -> MaterialTheme.typography.titleSmall
                ButtonSize.HERO -> MaterialTheme.typography.titleLarge
            }
            CompositionLocalProvider(LocalContentColor provides colors.content) {
                ProvideTextStyle(style.copy(fontWeight = if (size == ButtonSize.HERO) FontWeight.Black else FontWeight.Bold, textAlign = TextAlign.Center)) {
                    content()
                }
            }
        }
    }
}

/** A [GameButton] with an optional pictogram and a label. */
@Composable
fun GameButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    role: ActionRole = ActionRole.NAV,
    size: ButtonSize = ButtonSize.MEDIUM,
    icon: ImageVector? = null,
    enabled: Boolean = true,
) {
    GameButton(onClick, modifier, role, size, enabled) {
        if (icon != null) Icon(icon, contentDescription = null, modifier = Modifier.size(size.icon))
        Text(text)
    }
}
