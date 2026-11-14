package com.deepline.fishing.ui.kit

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.deepline.fishing.R
import com.deepline.fishing.ui.GameIcons
import com.deepline.fishing.ui.SparkleGold
import com.deepline.fishing.ui.theme.LocalMenuPalette

/** A red count on a button: something to collect or look at. Nothing at zero. */
@Composable
fun CountBadge(count: Int, modifier: Modifier = Modifier) {
    if (count <= 0) return
    Box(
        modifier
            .defaultMinSize(minWidth = 18.dp, minHeight = 18.dp)
            .background(GameColors.Badge, CircleShape)
            .border(1.5.dp, Color.White, CircleShape)
            .padding(horizontal = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(if (count > 99) "99+" else "$count", color = Color.White, fontSize = 11.sp, lineHeight = 12.sp, fontWeight = FontWeight.Bold) // i18n-ignore: digits
    }
}

/**
 * A round, raised pictogram button, for the top bar and the rails over a scene. [badge] is a
 * count in red; [label] is a short caption underneath (a countdown, a streak), drawn on a dark
 * pill so it reads on any scene. A [ActionRole.CLAIM] role says "something is ready here".
 */
@Composable
fun RoundIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    role: ActionRole = ActionRole.NAV,
    badge: Int = 0,
    label: String? = null,
    diameter: Dp = 42.dp,
) {
    val deep = deepColor()
    val colors = if (role == ActionRole.NAV) RoleColors(lerp(deep, Color.White, 0.14f), deep, lerp(deep, Color.Black, 0.5f), GameColors.OnDeep) else roleColors(role)
    val interaction = remember { MutableInteractionSource() }
    val isPressed by interaction.collectIsPressedAsState()
    val pressed by animateFloatAsState(if (isPressed) 1f else 0f, tween(PRESS_MS), label = "press")
    val feedback = LocalPressFeedback.current
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Box {
            Box(
                Modifier
                    .size(diameter, diameter + 3.dp)
                    .drawBehind { drawRaised(colors, size.width / 2f, 3.dp.toPx(), pressed) }
                    .clip(CircleShape)
                    .clickable(interactionSource = interaction, indication = null, role = Role.Button) {
                        feedback(role)
                        onClick()
                    }
                    .semantics { this.contentDescription = contentDescription },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = colors.content,
                    modifier = Modifier.padding(bottom = 3.dp).size(diameter * 0.5f).graphicsLayer { translationY = 3.dp.toPx() * pressed },
                )
            }
            CountBadge(badge, Modifier.align(Alignment.TopEnd).offset(x = 5.dp, y = (-5).dp))
        }
        if (label != null) ScenePill { Text(label, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, maxLines = 1) }
    }
}

/** A dark pill for a number or a few words over a scene (white text in every theme). */
@Composable
fun ScenePill(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, content: @Composable RowScope.() -> Unit) {
    val shape = RoundedCornerShape(50)
    Row(
        modifier
            .clip(shape)
            .background(deepColor().copy(alpha = 0.82f))
            .let { if (onClick != null) it.clickable(role = Role.Button, onClick = onClick) else it }
            .padding(horizontal = 9.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        androidx.compose.runtime.CompositionLocalProvider(LocalContentColor provides GameColors.OnDeep) { content() }
    }
}

/** A pictogram and its number ("420 m" with the depth arrow), over a scene. */
@Composable
fun StatPill(icon: ImageVector, text: String, modifier: Modifier = Modifier, tint: Color = Color(0xFF8FE3F5)) {
    ScenePill(modifier) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(15.dp))
        Text(text, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

/** The points coin and an amount, in the reward gold: prices and rewards in points. */
@Composable
fun CoinAmount(text: String, modifier: Modifier = Modifier, color: Color = LocalContentColor.current, iconSize: Dp = 16.dp) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Icon(GameIcons.Coin, contentDescription = null, tint = SparkleGold, modifier = Modifier.size(iconSize))
        Text(text, color = color, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

/**
 * One kind of tackle for the next cast: its pictogram, how many are left, lit when it's in use.
 * Replaces a switch and its sentence; [description] is read by screen readers and tests.
 */
@Composable
fun TackleTile(
    icon: ImageVector,
    count: Int,
    selected: Boolean,
    onToggle: (Boolean) -> Unit,
    description: String,
    modifier: Modifier = Modifier,
    /** With none left, the tile leads somewhere to get more (the Shop). */
    onEmpty: (() -> Unit)? = null,
) {
    val deep = deepColor()
    val on = selected && count > 0
    val colors = if (on) {
        RoleColors(Color(0xFFFFF6CF), Color(0xFFFFE38A), Color(0xFFB98300), Color(0xFF6A4100))
    } else {
        RoleColors(lerp(deep, Color.White, 0.2f), lerp(deep, Color.White, 0.08f), lerp(deep, Color.Black, 0.5f), if (count > 0) GameColors.OnDeep else GameColors.OnDeepMuted.copy(alpha = 0.6f))
    }
    val pressed by animateFloatAsState(if (on) 0.5f else 0f, tween(PRESS_MS), label = "tile")
    val feedback = LocalPressFeedback.current
    Box(modifier) {
        Box(
            Modifier
                .size(52.dp, 56.dp)
                .drawBehind { drawRaised(colors, 14.dp.toPx(), 4.dp.toPx(), pressed) }
                .clip(RoundedCornerShape(14.dp))
                .let { m ->
                    if (count > 0 || onEmpty == null) {
                        m.toggleable(value = on, enabled = count > 0, role = Role.Switch) {
                            feedback(ActionRole.NAV)
                            onToggle(it)
                        }
                    } else {
                        m.clickable(role = Role.Button, onClick = onEmpty)
                    }
                }
                .semantics { contentDescription = description },
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = colors.content, modifier = Modifier.padding(bottom = 4.dp).size(26.dp).graphicsLayer { translationY = 4.dp.toPx() * pressed })
        }
        Box(
            Modifier
                .align(Alignment.BottomEnd)
                .offset(x = 6.dp, y = 4.dp)
                .defaultMinSize(minWidth = 20.dp)
                .background(lerp(deep, Color.Black, 0.35f), RoundedCornerShape(50))
                .border(1.dp, GameColors.OnDeepMuted.copy(alpha = 0.6f), RoundedCornerShape(50))
                .padding(horizontal = 5.dp, vertical = 1.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text("$count", color = GameColors.OnDeep, fontSize = 11.sp, lineHeight = 13.sp, fontWeight = FontWeight.Bold) // i18n-ignore: digits
        }
    }
}

/**
 * The (i) that holds what a screen no longer spells out: tapping it shows [body] under [title].
 * Explanations live here (and in the tour), never as permanent paragraphs (see docs/ui-design.md).
 */
@Composable
fun InfoButton(title: String, body: String, modifier: Modifier = Modifier, tint: Color = LocalContentColor.current.copy(alpha = 0.75f), tag: String? = null) {
    var open by remember { mutableStateOf(false) }
    val label = stringResource(R.string.common_info_about, title)
    Box(
        modifier
            .size(36.dp)
            .clip(CircleShape)
            .clickable(role = Role.Button) { open = true }
            .semantics { contentDescription = label }
            .let { if (tag != null) it.testTag(tag) else it },
        contentAlignment = Alignment.Center,
    ) {
        Icon(KitIcons.Info, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
    }
    if (open) {
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text(title) },
            text = { Text(body, style = MaterialTheme.typography.bodyMedium) },
            confirmButton = { TextButton(onClick = { open = false }) { Text(stringResource(R.string.common_got_it)) } },
        )
    }
}

/** A section's title with its (i): what replaces "title + paragraph" at the top of a list. */
@Composable
fun SectionTitle(title: String, info: String?, modifier: Modifier = Modifier, icon: ImageVector? = null, trailing: (@Composable RowScope.() -> Unit)? = null) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (icon != null) Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        trailing?.invoke(this)
        if (info != null) InfoButton(title, info)
    }
}

/** Levels as squares: [filled] of [total] lit. */
@Composable
fun Pips(filled: Int, total: Int, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.primary) {
    val off = color.copy(alpha = 0.22f)
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        repeat(total) { i -> Box(Modifier.size(width = 10.dp, height = 8.dp).background(if (i < filled) color else off, RoundedCornerShape(2.dp))) }
    }
}

/** Progress as a ring around [content] (a count, a pictogram). */
@Composable
fun RingProgress(progress: Float, modifier: Modifier = Modifier, diameter: Dp = 44.dp, color: Color = MaterialTheme.colorScheme.primary, content: @Composable () -> Unit) {
    val track = color.copy(alpha = 0.2f)
    Box(modifier.size(diameter), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(diameter)) {
            val w = 4.dp.toPx()
            val inset = Offset(w / 2f, w / 2f)
            val arc = Size(size.width - w, size.height - w)
            drawArc(track, 0f, 360f, false, inset, arc, style = Stroke(w))
            drawArc(color, -90f, 360f * progress.coerceIn(0f, 1f), false, inset, arc, style = Stroke(w, cap = StrokeCap.Round))
        }
        content()
    }
}

/** Paints the dock: [deep] water with a lighter line where it meets the scene. */
fun Modifier.drawBehindDock(deep: Color): Modifier = drawBehind {
    drawRect(deep)
    drawRect(lerp(deep, Color.White, 0.28f), size = Size(size.width, 2.dp.toPx()))
}

@Composable
internal fun onDeepMuted(): Color = GameColors.OnDeepMuted

@Composable
internal fun isLightTheme(): Boolean = LocalMenuPalette.current.light
