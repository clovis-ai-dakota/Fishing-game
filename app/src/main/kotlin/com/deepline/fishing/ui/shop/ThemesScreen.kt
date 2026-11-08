package com.deepline.fishing.ui.shop

import com.deepline.fishing.ui.i18n.description
import com.deepline.fishing.ui.i18n.label
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.deepline.fishing.GameViewModel
import com.deepline.fishing.R
import com.deepline.fishing.ShopPage
import com.deepline.fishing.core.MenuTheme
import com.deepline.fishing.core.NightMode
import com.deepline.fishing.core.ShopCatalog
import com.deepline.fishing.core.ShopClock
import com.deepline.fishing.core.ShopItem
import com.deepline.fishing.ui.SubScreen
import com.deepline.fishing.ui.i18n.asString
import com.deepline.fishing.ui.metersText
import com.deepline.fishing.ui.theme.MenuPalettes
import com.deepline.fishing.ui.theme.ThemeBackdrop
import com.deepline.fishing.ui.tour.TourTarget
import com.deepline.fishing.ui.tour.tourTarget

/**
 * The menus' look: day or night, and the theme. Lagoon is free; the others are on sale here and
 * on the Shop's Style page (euros or points), and the Club's Aurora comes with the membership.
 */
@Composable
fun ThemesScreen(vm: GameViewModel) {
    val night = vm.nightMode.isNight(isSystemInDarkTheme())
    var confirm by remember { mutableStateOf<ShopItem?>(null) }
    val actions = ShopActions(vm, LocalContext.current.findActivity(), onPoints = { confirm = it })
    SubScreen(title = stringResource(R.string.shop_themes_title), onBack = vm::onBack) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 156.dp),
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxSize().testTag("themes_list"),
        ) {
            item(key = "mode", span = { GridItemSpan(maxLineSpan) }) {
                Card(Modifier.fillMaxWidth().tourTarget(TourTarget.THEMES_MODE), shape = RoundedCornerShape(16.dp)) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.shop_themes_mode_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                            NightMode.entries.forEachIndexed { i, mode ->
                                SegmentedButton(
                                    selected = vm.nightMode == mode,
                                    onClick = { vm.setNightMode(mode) },
                                    shape = SegmentedButtonDefaults.itemShape(i, NightMode.entries.size),
                                    modifier = Modifier.testTag("mode_${mode.name.lowercase()}"),
                                ) { Text(mode.label(), maxLines = 1) }
                            }
                        }
                        Text(
                            stringResource(R.string.shop_themes_mode_note),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            items(MenuTheme.entries, key = { it.name }) { theme -> ThemeCard(vm, theme, night, actions) }
            ShopCatalog.byId("theme_collection")?.takeIf { ShopCatalog.canBuy(it, vm.progress, ShopClock.now()) }?.let { bundle ->
                item(key = "collection", span = { GridItemSpan(maxLineSpan) }) { ItemCard(vm, bundle, actions) }
            }
        }
    }
    confirm?.let { PointsConfirmDialog(vm, it, onDone = { confirm = null }) }
}

@Composable
private fun ThemeCard(vm: GameViewModel, theme: MenuTheme, night: Boolean, actions: ShopActions) {
    val p = vm.progress
    val item = ShopCatalog.byId("theme_${theme.name.lowercase()}")
    Card(shape = RoundedCornerShape(16.dp), modifier = Modifier.testTag("theme_${theme.name.lowercase()}")) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            ThemePreview(theme, night, Modifier.fillMaxWidth().height(150.dp).clip(RoundedCornerShape(12.dp)))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(theme.label(), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                if (theme.tier > 0) Text("★".repeat(theme.tier), color = MaterialTheme.colorScheme.tertiary, style = MaterialTheme.typography.labelMedium)
            }
            Text(theme.description(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, minLines = 2, maxLines = 2, overflow = TextOverflow.Ellipsis)
            when {
                vm.menuTheme == theme -> OwnedRow(stringResource(R.string.shop_in_use))
                theme.isAvailable(p, ShopClock.now()) -> OwnedRow {
                    TextButton(onClick = { vm.setMenuTheme(theme) }, modifier = Modifier.testTag("use_${theme.name.lowercase()}")) {
                        Text(stringResource(R.string.shop_use))
                    }
                }
                theme.clubOnly -> OwnedRow(stringResource(R.string.core_shop_item_captains_club)) {
                    TextButton(onClick = { vm.openShop(ShopPage.PASSES) }) { Text(stringResource(R.string.shop_themes_join)) }
                }
                item != null -> PriceButtons(item, actions)
            }
        }
    }
}

/**
 * A small picture of [theme]: its scene, and a title, a card and a button in its colours, so the
 * look of the menus can be judged before buying. Still (not animated), to keep the grid light.
 */
@Composable
fun ThemePreview(theme: MenuTheme, night: Boolean, modifier: Modifier = Modifier) {
    val palette = remember(theme, night) { MenuPalettes.of(theme, night) }
    Box(modifier) {
        ThemeBackdrop(palette, Modifier.fillMaxSize(), animate = false)
        Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(
                stringResource(R.string.app_name),
                style = TextStyle(brush = Brush.horizontalGradient(palette.headline + palette.headline.last()), fontSize = 15.sp, fontWeight = FontWeight.Black),
            )
            Spacer(Modifier.height(10.dp))
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(palette.card)
                    .padding(6.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                // The Fish tab in miniature: its rope card and Cast line button, in the home screen's words.
                Text(stringResource(R.string.home_rope_title), color = palette.onCard, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                Text(stringResource(R.string.shop_themes_preview_reach, metersText(240f).asString()), color = palette.onCardMuted, fontSize = 9.sp)
                Box(
                    Modifier
                        .clip(RoundedCornerShape(50))
                        .background(palette.primary)
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                ) { Text(stringResource(R.string.home_cast_button), color = palette.onPrimary, fontSize = 9.sp, fontWeight = FontWeight.Bold) }
            }
        }
        Spacer(Modifier.width(1.dp))
    }
}
