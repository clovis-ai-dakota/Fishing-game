package com.deepline.fishing.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.deepline.fishing.core.MenuTheme

/** What the scene behind the menus shows on top of its water (see [ThemeBackdrop]). */
enum class SceneDecor { LAGOON, KELP, SAND, CORAL, ICE, HARBOR, KOI, TREASURE, GLOW, AURORA }

/**
 * Every colour of a menu theme, for day or night. The scene colours paint [ThemeBackdrop]; the
 * UI colours become the Material colour scheme (see `DeepLineTheme`). Text sits on [card]s or
 * straight on the water, so [onCard] and [onCardMuted] keep at least 4.5:1 contrast with both.
 */
data class MenuPalette(
    val theme: MenuTheme,
    val night: Boolean,
    /** Light UI (dark text): the day versions, except the themes that are dusky by nature. */
    val light: Boolean,
    val decor: SceneDecor,
    // Scene
    val sky: List<Color>,
    val sea: List<Color>,
    /** Sun rays by day, moonlight by night. */
    val glow: Color,
    /** Bubbles by day, plankton by night. */
    val particle: Color,
    val silhouette: Color,
    val accentA: Color,
    val accentB: Color,
    // UI
    val primary: Color,
    val onPrimary: Color,
    val selection: Color,
    val onSelection: Color,
    /** Highlighted text (points, tiers, "in use"). */
    val highlight: Color,
    val reward: Color,
    val card: Color,
    val cardStrong: Color,
    val onCard: Color,
    val onCardMuted: Color,
    val outline: Color,
    val bar: Color,
    /** The big titles' gradient. */
    val headline: List<Color>,
)

val LocalMenuPalette = staticCompositionLocalOf { MenuPalettes.of(MenuTheme.LAGOON, night = false) }

object MenuPalettes {
    private fun c(argb: Long) = Color(argb)

    // Glass for light and dark themes: see-through enough to show the sea, opaque enough for text.
    private val glassLight = c(0xE0FFFFFF)
    private val glassLightStrong = c(0xF5FFFFFF)
    private val inkLight = c(0xFF0B2540)
    private val inkLightMuted = c(0xFF34506A)

    private fun lightUi(
        theme: MenuTheme, decor: SceneDecor, sky: List<Long>, sea: List<Long>, silhouette: Long, accentA: Long, accentB: Long,
        primary: Long, selection: Long, onSelection: Long, highlight: Long, headline: List<Long>,
        glow: Long = 0xFFFFFFFF, particle: Long = 0xFFFFFFFF, ink: Color = inkLight, inkMuted: Color = inkLightMuted,
        card: Color = glassLight, bar: Long = 0xE6F4FCFF, reward: Long = 0xFF8A5A00,
    ) = MenuPalette(
        theme, night = false, light = true, decor = decor,
        sky = sky.map(::c), sea = sea.map(::c), glow = c(glow), particle = c(particle), silhouette = c(silhouette), accentA = c(accentA), accentB = c(accentB),
        primary = c(primary), onPrimary = Color.White, selection = c(selection), onSelection = c(onSelection), highlight = c(highlight), reward = c(reward),
        card = card, cardStrong = glassLightStrong, onCard = ink, onCardMuted = inkMuted, outline = ink.copy(alpha = 0.35f), bar = c(bar),
        headline = headline.map(::c),
    )

    private fun darkUi(
        theme: MenuTheme, night: Boolean, decor: SceneDecor, sky: List<Long>, sea: List<Long>, silhouette: Long, accentA: Long, accentB: Long,
        primary: Long, onPrimary: Long, selection: Long, onSelection: Long, highlight: Long, headline: List<Long>,
        glow: Long = 0xFFFFF4D6, particle: Long = 0xFF9FE7FF, card: Long = 0xC70E2034, cardStrong: Long = 0xF2132A40, bar: Long = 0xE60A1828,
        onCard: Long = 0xFFE6EFF7, onCardMuted: Long = 0xFFB3C6D8, reward: Long = 0xFFFFD580,
    ) = MenuPalette(
        theme, night = night, light = false, decor = decor,
        sky = sky.map(::c), sea = sea.map(::c), glow = c(glow), particle = c(particle), silhouette = c(silhouette), accentA = c(accentA), accentB = c(accentB),
        primary = c(primary), onPrimary = c(onPrimary), selection = c(selection), onSelection = c(onSelection), highlight = c(highlight), reward = c(reward),
        card = c(card), cardStrong = c(cardStrong), onCard = c(onCard), onCardMuted = c(onCardMuted), outline = c(onCardMuted).copy(alpha = 0.45f), bar = c(bar),
        headline = headline.map(::c),
    )

    fun of(theme: MenuTheme, night: Boolean): MenuPalette = when (theme) {
        MenuTheme.LAGOON -> if (!night) {
            lightUi(
                theme, SceneDecor.LAGOON, sky = listOf(0xFF9EDCFA, 0xFFDDF4FF), sea = listOf(0xFFC2F1F0, 0xFF92DDE5, 0xFF63C6D5),
                silhouette = 0xFF2B8FA6, accentA = 0xFFFFD54F, accentB = 0xFFFF8A65,
                primary = 0xFF006878, selection = 0xFFBFEAF0, onSelection = 0xFF00333B, highlight = 0xFF155A8A,
                headline = listOf(0xFF0B3550, 0xFF00748A),
            )
        } else {
            darkUi(
                theme, true, SceneDecor.LAGOON, sky = listOf(0xFF07142A, 0xFF14294B), sea = listOf(0xFF103049, 0xFF0B2238, 0xFF061424),
                silhouette = 0xFF1C4763, accentA = 0xFFFFF1C1, accentB = 0xFF7FDBEA,
                primary = 0xFF7FDBEA, onPrimary = 0xFF00363D, selection = 0xFF2B4170, onSelection = 0xFFDCE4FF, highlight = 0xFFB6C8FF,
                headline = listOf(0xFFEAF6FF, 0xFF7FDBEA),
            )
        }
        MenuTheme.KELP_FOREST -> if (!night) {
            lightUi(
                theme, SceneDecor.KELP, sky = listOf(0xFFCDEBB8, 0xFFEFF8E4), sea = listOf(0xFFD3F0CC, 0xFFA9DDB2, 0xFF7CC594),
                silhouette = 0xFF2F7A4A, accentA = 0xFFE7C35A, accentB = 0xFF5FA85A,
                primary = 0xFF2E6B3A, selection = 0xFFCDEBC8, onSelection = 0xFF113A1A, highlight = 0xFF2F5E1C,
                headline = listOf(0xFF1C3A18, 0xFF3D7A2C), glow = 0xFFFFF6C8, bar = 0xE6F3FBEE,
            )
        } else {
            darkUi(
                theme, true, SceneDecor.KELP, sky = listOf(0xFF0B1A12, 0xFF16301F), sea = listOf(0xFF132E21, 0xFF0C2117, 0xFF06120C),
                silhouette = 0xFF1E4A31, accentA = 0xFFCFF7A6, accentB = 0xFF6FBF73,
                primary = 0xFF9BDCA4, onPrimary = 0xFF0B3A16, selection = 0xFF24452D, onSelection = 0xFFD6F2D8, highlight = 0xFFC5E89A,
                headline = listOf(0xFFEAF7E3, 0xFF9BDCA4), particle = 0xFFCFF7A6, card = 0xC70E2018, cardStrong = 0xF2142A1E, bar = 0xE60A1810,
            )
        }
        MenuTheme.SANDBAR -> if (!night) {
            lightUi(
                theme, SceneDecor.SAND, sky = listOf(0xFFFFE2B8, 0xFFFFF5E6), sea = listOf(0xFFCFF2EC, 0xFFA7E3DC, 0xFFE6D3A6),
                silhouette = 0xFFC2A066, accentA = 0xFFF2C98A, accentB = 0xFF6FCFC6,
                primary = 0xFF7A5414, selection = 0xFFF5E3C4, onSelection = 0xFF3F2A05, highlight = 0xFF6A4A10,
                headline = listOf(0xFF4A3410, 0xFF8A6524), glow = 0xFFFFF8E1, bar = 0xE6FFF8EC, ink = c(0xFF2A1E0A), inkMuted = c(0xFF5A4628),
            )
        } else {
            darkUi(
                theme, true, SceneDecor.SAND, sky = listOf(0xFF151520, 0xFF2A2A3A), sea = listOf(0xFF17303A, 0xFF13262E, 0xFF2A2418),
                silhouette = 0xFF5A4A30, accentA = 0xFFF2C98A, accentB = 0xFF4FA8A0,
                primary = 0xFFF2C98A, onPrimary = 0xFF3F2A05, selection = 0xFF4A3B22, onSelection = 0xFFF8E8CC, highlight = 0xFFF2D49A,
                headline = listOf(0xFFFFF1DA, 0xFFF2C98A), particle = 0xFFFFE9B8, card = 0xC71A1A16, cardStrong = 0xF2262420, bar = 0xE6141410,
            )
        }
        MenuTheme.CORAL_REEF -> if (!night) {
            lightUi(
                theme, SceneDecor.CORAL, sky = listOf(0xFFFFCDD6, 0xFFFFEEF1), sea = listOf(0xFFCFF4F2, 0xFFA2E2E6, 0xFF7ECFDB),
                silhouette = 0xFFFF7F6B, accentA = 0xFFFFB74D, accentB = 0xFFF48FB1,
                primary = 0xFFB23A1A, selection = 0xFFFFDCD2, onSelection = 0xFF4A1408, highlight = 0xFF9A2E3E,
                headline = listOf(0xFF7A1F2B, 0xFFC2410C), bar = 0xE6FFF3F1,
            )
        } else {
            darkUi(
                theme, true, SceneDecor.CORAL, sky = listOf(0xFF1A0E26, 0xFF2E1840), sea = listOf(0xFF2A1636, 0xFF1A0F2C, 0xFF0B0716),
                silhouette = 0xFFFF6B8A, accentA = 0xFFFFB74D, accentB = 0xFFB388FF,
                primary = 0xFFFF9E8A, onPrimary = 0xFF4A1408, selection = 0xFF4A2340, onSelection = 0xFFFFDCE6, highlight = 0xFFFFB3C6,
                headline = listOf(0xFFFFE6EC, 0xFFFF9E8A), particle = 0xFFFFC2D6, card = 0xC71C0E24, cardStrong = 0xF2281632, bar = 0xE6120A1A,
            )
        }
        MenuTheme.POLAR -> if (!night) {
            lightUi(
                theme, SceneDecor.ICE, sky = listOf(0xFFD6E9FF, 0xFFF4F9FF), sea = listOf(0xFFDDF0FB, 0xFFB5DDF4, 0xFF8BC4EA),
                silhouette = 0xFF5D8FC2, accentA = 0xFFFFFFFF, accentB = 0xFFA8D8FF,
                primary = 0xFF1F5FA8, selection = 0xFFD6E6FA, onSelection = 0xFF0A2A52, highlight = 0xFF1F4F8C,
                headline = listOf(0xFF0D2A4A, 0xFF2D6FB8), bar = 0xE6F4F9FF,
            )
        } else {
            darkUi(
                theme, true, SceneDecor.ICE, sky = listOf(0xFF081228, 0xFF15274A), sea = listOf(0xFF11284A, 0xFF0B1B34, 0xFF050E1E),
                silhouette = 0xFF2F4E78, accentA = 0xFFDDEBFF, accentB = 0xFF64FFDA,
                primary = 0xFFA8CCFF, onPrimary = 0xFF0A2A52, selection = 0xFF243C66, onSelection = 0xFFDCE8FF, highlight = 0xFFC8DCFF,
                headline = listOf(0xFFF2F7FF, 0xFFA8CCFF), particle = 0xFFE6F0FF,
            )
        }
        MenuTheme.SUNSET_HARBOR -> if (!night) {
            lightUi(
                theme, SceneDecor.HARBOR, sky = listOf(0xFFFF9E6B, 0xFFFFD8A8), sea = listOf(0xFFF8CBA8, 0xFFE0B2C4, 0xFFB8AEDB),
                silhouette = 0xFF5A3458, accentA = 0xFFFFE08A, accentB = 0xFFFF7A59,
                primary = 0xFF9C2F4E, selection = 0xFFFFDDD0, onSelection = 0xFF4A1020, highlight = 0xFF8A2E2E,
                headline = listOf(0xFF4A1430, 0xFFB4363A), glow = 0xFFFFE6B0, bar = 0xE6FFF1E8, ink = c(0xFF2A1226), inkMuted = c(0xFF553248),
            )
        } else {
            darkUi(
                theme, true, SceneDecor.HARBOR, sky = listOf(0xFF140C2A, 0xFF3A1F4A), sea = listOf(0xFF2A1A40, 0xFF180F2C, 0xFF0A0716),
                silhouette = 0xFF0A0614, accentA = 0xFFFFC46B, accentB = 0xFFFF7A59,
                primary = 0xFFFFB38A, onPrimary = 0xFF4A1A08, selection = 0xFF4A2A50, onSelection = 0xFFFFE3D6, highlight = 0xFFFFC9A8,
                headline = listOf(0xFFFFEBD6, 0xFFFFB38A), particle = 0xFFFFD9A8, card = 0xC71A1028, cardStrong = 0xF2261838, bar = 0xE6100A1A,
            )
        }
        MenuTheme.KOI_POND -> if (!night) {
            lightUi(
                theme, SceneDecor.KOI, sky = listOf(0xFFCDEBD8, 0xFFF0F9F3), sea = listOf(0xFFCFEFE0, 0xFFA2D9C3, 0xFF79C4A8),
                silhouette = 0xFF3F8C5C, accentA = 0xFFFF8A3D, accentB = 0xFFF8BBD0,
                primary = 0xFF2F6E57, selection = 0xFFCDEDDF, onSelection = 0xFF0F3A2A, highlight = 0xFF2A5E48,
                headline = listOf(0xFF173D30, 0xFF2F7A60), bar = 0xE6F1FBF6,
            )
        } else {
            darkUi(
                theme, true, SceneDecor.KOI, sky = listOf(0xFF081812, 0xFF14302A), sea = listOf(0xFF102C25, 0xFF0B1F1A, 0xFF05110E),
                silhouette = 0xFF1F4A38, accentA = 0xFFFF9A52, accentB = 0xFFFFD27A,
                primary = 0xFF8FD8BC, onPrimary = 0xFF0B3A2A, selection = 0xFF234A3C, onSelection = 0xFFD2F2E6, highlight = 0xFFB8EBD6,
                headline = listOf(0xFFE6F8F0, 0xFF8FD8BC), particle = 0xFFFFE3A8, card = 0xC70C201A, cardStrong = 0xF2122C24, bar = 0xE6081610,
            )
        }
        MenuTheme.TREASURE_COVE -> if (!night) {
            lightUi(
                theme, SceneDecor.TREASURE, sky = listOf(0xFFC9DDFA, 0xFFEEF4FF), sea = listOf(0xFFCFE4F6, 0xFF9DC4E6, 0xFF73A3D2),
                silhouette = 0xFF2C4F80, accentA = 0xFFFFC93C, accentB = 0xFFFFE9A0,
                primary = 0xFF7A4F00, selection = 0xFFF8E6BC, onSelection = 0xFF3A2600, highlight = 0xFF6E4800,
                headline = listOf(0xFF1A2E5A, 0xFF8A5A00), bar = 0xE6F4F7FF, ink = c(0xFF0D1F3C),
            )
        } else {
            darkUi(
                theme, true, SceneDecor.TREASURE, sky = listOf(0xFF060C1E, 0xFF101C3A), sea = listOf(0xFF0E1C3A, 0xFF09132A, 0xFF040816),
                silhouette = 0xFF1A2A4A, accentA = 0xFFFFC93C, accentB = 0xFFFFE9A0,
                primary = 0xFFFFD27A, onPrimary = 0xFF3F2E00, selection = 0xFF3A3218, onSelection = 0xFFFFEFC8, highlight = 0xFFFFDE94,
                headline = listOf(0xFFFFF4D6, 0xFFFFC93C), particle = 0xFFFFE9A0,
            )
        }
        // Dusky by nature: dark UI by day too, darker at night, the glows brighter.
        MenuTheme.GLOW_TIDE -> darkUi(
            theme, night, SceneDecor.GLOW,
            sky = if (night) listOf(0xFF02040C, 0xFF060B1E) else listOf(0xFF1A2560, 0xFF2E3F84),
            sea = if (night) listOf(0xFF061026, 0xFF040A1A, 0xFF02050C) else listOf(0xFF143A6E, 0xFF0D2650, 0xFF07142E),
            silhouette = 0xFF0F2A4A, accentA = 0xFF6EF5E0, accentB = 0xFFE59CFF,
            primary = 0xFF7FF0E0, onPrimary = 0xFF003A35, selection = 0xFF213E66, onSelection = 0xFFD6F8FF, highlight = 0xFFA8F5EA,
            headline = listOf(0xFFE8FFFB, 0xFF7FF0E0, 0xFFE59CFF), particle = 0xFFB8FFF4, glow = 0xFF8CF0FF,
            card = 0xC2081A30, cardStrong = 0xF20C2440,
        )
        MenuTheme.AURORA -> if (!night) {
            lightUi(
                theme, SceneDecor.AURORA, sky = listOf(0xFFCFE3FF, 0xFFF0F6FF), sea = listOf(0xFFD8ECF8, 0xFFAED3EE, 0xFF86B8E2),
                silhouette = 0xFF4A6E9C, accentA = 0xFF64FFDA, accentB = 0xFFB388FF,
                primary = 0xFF00696A, selection = 0xFFD8E2FA, onSelection = 0xFF132E5C, highlight = 0xFF4A3A9C,
                headline = listOf(0xFF102E5A, 0xFF5A3FB0), bar = 0xE6F4F8FF,
            )
        } else {
            darkUi(
                theme, true, SceneDecor.AURORA, sky = listOf(0xFF040A1A, 0xFF0C1A36), sea = listOf(0xFF0C1C38, 0xFF08142A, 0xFF040A16),
                silhouette = 0xFF1A2E4E, accentA = 0xFF64FFDA, accentB = 0xFFB388FF,
                primary = 0xFF80F0D8, onPrimary = 0xFF00382E, selection = 0xFF2A2F6A, onSelection = 0xFFE2E0FF, highlight = 0xFFC8B8FF,
                headline = listOf(0xFFE6FFF8, 0xFF64FFDA, 0xFFB388FF), particle = 0xFFCDE8FF,
            )
        }
    }
}
