package com.deepline.fishing.ui.kit

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import com.deepline.fishing.ui.theme.LocalMenuPalette
import com.deepline.fishing.ui.theme.MenuPalette

/**
 * What an action is, which decides its colour: the same in every menu theme, by day and by night,
 * so a colour always means the same thing (see docs/ui-design.md).
 *
 * - [PLAY]: the one main action of a screen (Cast, Start the battle, Collect the cast): amber,
 *   the opposite of the sea's blue. At most one per screen.
 * - [CLAIM]: something free and ready (daily reward, milestone, season tier, gift): green.
 * - [NAV]: open, choose, go back, and every secondary action: the theme's own colour.
 * - [POINTS]: a price in points, or a points reward: gold, with the coin.
 * - [PREMIUM]: euros, passes, the Captain's Club: violet.
 * - [ALERT]: no way back (delete), and notification badges: red.
 */
enum class ActionRole { PLAY, CLAIM, NAV, POINTS, PREMIUM, ALERT }

/** The colours of a raised button: its face (a top-to-bottom gradient), its edge below, its text. */
data class RoleColors(val top: Color, val bottom: Color, val edge: Color, val content: Color)

object GameColors {
    private val play = RoleColors(Color(0xFFFFD54A), Color(0xFFFFAE00), Color(0xFFA86400), Color(0xFF3A2400))
    private val claim = RoleColors(Color(0xFF6BE391), Color(0xFF1FAE57), Color(0xFF0C6B31), Color(0xFF05311A))
    private val points = RoleColors(Color(0xFFFFE27A), Color(0xFFF5B400), Color(0xFFA06500), Color(0xFF3A2400))
    private val premium = RoleColors(Color(0xFFB896FF), Color(0xFF7A4BE0), Color(0xFF482895), Color.White)
    private val alert = RoleColors(Color(0xFFFF8E7E), Color(0xFFE2412F), Color(0xFF96200F), Color.White)
    private val disabled = RoleColors(Color(0xFFB9C4CC), Color(0xFFA3B0BA), Color(0xFF7C8A95), Color(0xFF4B5964))

    /** Notification badges and dots. */
    val Badge = Color(0xFFFF4B3E)

    fun of(role: ActionRole, palette: MenuPalette, enabled: Boolean = true): RoleColors = when {
        !enabled -> disabled
        role == ActionRole.PLAY -> play
        role == ActionRole.CLAIM -> claim
        role == ActionRole.POINTS -> points
        role == ActionRole.PREMIUM -> premium
        role == ActionRole.ALERT -> alert
        else -> RoleColors(
            top = lerp(palette.primary, Color.White, 0.16f),
            bottom = palette.primary,
            edge = lerp(palette.primary, Color.Black, 0.42f),
            content = palette.onPrimary,
        )
    }

    /**
     * The deep water the action zones sit on (the dock under the tabs, pills over a scene): dark
     * in every theme, so warm buttons stand out and white text reads on it.
     */
    fun deep(palette: MenuPalette): Color = lerp(if (palette.light) palette.primary else palette.sea.last(), Color(0xFF02131F), if (palette.light) 0.62f else 0.5f)

    /** Text and icons on [deep]. */
    val OnDeep = Color(0xFFF2FAFF)
    val OnDeepMuted = Color(0xFFA9C7D8)
}

@Composable
@ReadOnlyComposable
fun roleColors(role: ActionRole, enabled: Boolean = true): RoleColors = GameColors.of(role, LocalMenuPalette.current, enabled)

@Composable
@ReadOnlyComposable
fun deepColor(): Color = GameColors.deep(LocalMenuPalette.current)
