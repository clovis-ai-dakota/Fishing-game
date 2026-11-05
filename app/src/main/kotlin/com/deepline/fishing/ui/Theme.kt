package com.deepline.fishing.ui

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import com.deepline.fishing.core.MenuTheme
import com.deepline.fishing.ui.theme.LocalMenuPalette
import com.deepline.fishing.ui.theme.MenuPalette
import com.deepline.fishing.ui.theme.MenuPalettes

/**
 * The app's colours come from the menu theme (see `MenuPalette`): the scene behind the menus and
 * a Material colour scheme in its colours. Roles stay the same in every theme: [primary] for
 * actions, [secondaryContainer] for selection, [secondary] for highlighted text, [tertiary] and
 * [SparkleGold] for rewards. Cards are glass over the scene ("surfaceContainer" roles) with text
 * that keeps 4.5:1 contrast. Dark versions follow Material's dark-theme guidance (no pure black,
 * desaturated accents, off-white text).
 */
fun colorSchemeOf(p: MenuPalette): ColorScheme {
    val deep = p.sea.last()
    return if (p.light) {
        lightColorScheme(
            primary = p.primary,
            onPrimary = p.onPrimary,
            primaryContainer = lerp(p.primary, Color.White, 0.8f),
            onPrimaryContainer = lerp(p.primary, Color.Black, 0.5f),
            secondary = p.highlight,
            onSecondary = Color.White,
            secondaryContainer = p.selection,
            onSecondaryContainer = p.onSelection,
            tertiary = p.reward,
            onTertiary = Color.White,
            tertiaryContainer = lerp(p.reward, Color.White, 0.8f),
            onTertiaryContainer = lerp(p.reward, Color.Black, 0.5f),
            background = p.sea.first(),
            onBackground = p.onCard,
            surface = p.cardStrong,
            onSurface = p.onCard,
            surfaceVariant = p.card,
            onSurfaceVariant = p.onCardMuted,
            surfaceTint = Color.Transparent,
            inverseSurface = p.onCard,
            inverseOnSurface = Color.White,
            inversePrimary = lerp(p.primary, Color.White, 0.5f),
            surfaceContainerLowest = p.card,
            surfaceContainerLow = p.card,
            surfaceContainer = p.bar,
            surfaceContainerHigh = p.cardStrong,
            surfaceContainerHighest = p.card,
            surfaceBright = p.cardStrong,
            surfaceDim = p.card,
            outline = p.outline,
            outlineVariant = p.outline.copy(alpha = p.outline.alpha * 0.5f),
            error = Color(0xFFB3261E),
            onError = Color.White,
            scrim = Color.Black,
        )
    } else {
        darkColorScheme(
            primary = p.primary,
            onPrimary = p.onPrimary,
            primaryContainer = lerp(p.primary, deep, 0.7f),
            onPrimaryContainer = lerp(p.primary, Color.White, 0.6f),
            secondary = p.highlight,
            onSecondary = deep,
            secondaryContainer = p.selection,
            onSecondaryContainer = p.onSelection,
            tertiary = p.reward,
            onTertiary = Color(0xFF3F2E00),
            tertiaryContainer = Color(0xFF5B4300),
            onTertiaryContainer = Color(0xFFFFE9B8),
            background = deep,
            onBackground = p.onCard,
            surface = p.cardStrong,
            onSurface = p.onCard,
            surfaceVariant = p.card,
            onSurfaceVariant = p.onCardMuted,
            surfaceTint = Color.Transparent,
            inverseSurface = p.onCard,
            inverseOnSurface = deep,
            inversePrimary = lerp(p.primary, Color.Black, 0.5f),
            surfaceContainerLowest = p.card,
            surfaceContainerLow = p.card,
            surfaceContainer = p.bar,
            surfaceContainerHigh = p.cardStrong,
            surfaceContainerHighest = p.card,
            surfaceBright = p.cardStrong,
            surfaceDim = p.card,
            outline = p.outline,
            outlineVariant = p.outline.copy(alpha = p.outline.alpha * 0.5f),
            error = Color(0xFFFFB4AB),
            onError = Color(0xFF690005),
        )
    }
}

val SparkleGold = Color(0xFFFFE082)

/** Gold for reward text: bright on dark themes, a deeper gold on light ones (4.5:1 on cards). */
@Composable
fun rewardTextColor(): Color = if (LocalMenuPalette.current.light) MaterialTheme.colorScheme.tertiary else SparkleGold

/** The menus in [theme], by day or at [night]. The game's own water never changes with it. */
@Composable
fun DeepLineTheme(theme: MenuTheme = MenuTheme.LAGOON, night: Boolean = false, content: @Composable () -> Unit) {
    val palette = remember(theme, night) { MenuPalettes.of(theme, night) }
    val scheme = remember(palette) { colorSchemeOf(palette) }
    CompositionLocalProvider(LocalMenuPalette provides palette) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}
