package com.deepline.fishing.analytics

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.deepline.fishing.DexSection
import com.deepline.fishing.GameViewModel
import com.deepline.fishing.Screen
import com.deepline.fishing.ShopPage

/**
 * Logs a screen view each time the player lands on a screen (the Shop's pages and the Dex's
 * sections count as screens). Screens the guided tour flips through aren't counted: the tour
 * logs its own events.
 */
@Composable
fun TrackScreens(vm: GameViewModel) {
    val name = screenName(vm.screen, vm.dexSection, vm.shopPage)
    val touring = vm.tour != null
    LaunchedEffect(name, touring) {
        if (!touring) Analytics.screen(name)
    }
}

/** "fish", "dex_species", "shop_tackle", "cast", "settings"... */
fun screenName(screen: Screen, dexSection: DexSection, shopPage: ShopPage): String = when (screen) {
    Screen.HOME -> "fish"
    Screen.DEX -> "dex_" + dexSection.name.lowercase()
    Screen.SHOP -> "shop_" + shopPage.name.lowercase()
    else -> screen.name.lowercase()
}
