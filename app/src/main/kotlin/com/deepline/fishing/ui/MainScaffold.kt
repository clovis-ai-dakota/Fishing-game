package com.deepline.fishing.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.getValue
import androidx.compose.ui.semantics.Role
import androidx.compose.animation.AnimatedContent
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.deepline.fishing.GameViewModel
import com.deepline.fishing.MainTab
import com.deepline.fishing.Screen
import com.deepline.fishing.ui.dex.DexScreen
import com.deepline.fishing.ui.tour.TourTarget
import com.deepline.fishing.ui.tour.tourTarget
import com.deepline.fishing.ui.theme.atRest

private val MainTab.icon: ImageVector
    get() = when (this) {
        MainTab.FISH -> GameIcons.Fish
        MainTab.DEX -> GameIcons.Dex
        MainTab.SHOP -> GameIcons.Shop
        MainTab.BATTLE -> GameIcons.Battle
    }

private val MainTab.tourTarget: TourTarget?
    get() = when (this) {
        MainTab.DEX -> TourTarget.TAB_DEX
        MainTab.SHOP -> TourTarget.TAB_SHOP
        MainTab.BATTLE -> TourTarget.TAB_BATTLE
        MainTab.FISH -> null
    }

/**
 * The four top-level tabs (Material 3 navigation bar: 3–5 destinations, always labelled, in the
 * thumb zone at the bottom). Casts, battles, the online lobby and detail pages are full screens
 * drawn without it.
 */
@Composable
fun MainScaffold(vm: GameViewModel) {
    // While a pushed screen covers the tabs (and as it comes and goes), the tab it came from stays.
    var last by rememberSaveable { mutableStateOf(MainTab.FISH) }
    MainTab.of(vm.screen)?.let { last = it }
    val current = last
    Column(Modifier.fillMaxSize()) {
        // The content moves the way the camera travels between the two places (see ScreenMotion).
        AnimatedContent(
            targetState = current,
            transitionSpec = { ScreenMotion.betweenTabs(initialState, targetState, vm.feel.settings.reduceMotion) },
            label = "tab",
            modifier = Modifier.weight(1f).fillMaxWidth(),
        ) { tab ->
            // At rest: the tab has arrived and isn't leaving, and neither is the tab layer itself.
            val settled = com.deepline.fishing.ui.theme.LocalScreenSettled.current && atRest
            androidx.compose.runtime.CompositionLocalProvider(com.deepline.fishing.ui.theme.LocalScreenSettled provides settled) {
                when (tab) {
                    MainTab.FISH -> HomeScreen(vm)
                    MainTab.DEX -> DexScreen(vm)
                    MainTab.SHOP -> com.deepline.fishing.ui.shop.ShopScreen(vm)
                    MainTab.BATTLE -> BattleSetupScreen(vm)
                }
            }
        }
        TabBar(vm, current)
    }
}

/**
 * The tab bar, on the same deep water as the dock above it: the current tab is lit in the main
 * action's amber and raised a little, the others are quiet. Always labelled, in the thumb zone.
 */
@Composable
private fun TabBar(vm: GameViewModel, current: MainTab) {
    val amber = com.deepline.fishing.ui.kit.GameColors.of(com.deepline.fishing.ui.kit.ActionRole.PLAY, com.deepline.fishing.ui.theme.LocalMenuPalette.current).top
    val quiet = com.deepline.fishing.ui.kit.GameColors.OnDeepMuted
    Row(
        Modifier
            .fillMaxWidth()
            .background(com.deepline.fishing.ui.kit.deepColor())
            .navigationBarsPadding()
            .height(64.dp)
            .selectableGroup()
            .tourTarget(TourTarget.NAV_BAR),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        for (tab in MainTab.entries) {
            val target = tab.tourTarget
            val selected = tab == current
            // A dot on the Shop tab when there's something for the player there (the Club's
            // monthly pouch, or the starter pack offer while it's open).
            val dot = tab == MainTab.SHOP && (vm.clubPouchReady || vm.starterOfferOpen || vm.seasonRewardsReady)
            val lift by animateDpAsState(if (selected) (-3).dp else 0.dp, tween(160), label = "tab")
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .selectable(selected = selected, role = Role.Tab, onClick = { vm.selectTab(tab) })
                    .testTag("tab_${tab.name.lowercase()}")
                    .then(if (target != null) Modifier.tourTarget(target) else Modifier),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Box(Modifier.offset(y = lift)) {
                    Icon(tab.icon, contentDescription = null, tint = if (selected) amber else quiet, modifier = Modifier.size(if (selected) 28.dp else 24.dp))
                    if (dot) Box(Modifier.align(Alignment.TopEnd).offset(x = 4.dp, y = (-2).dp).size(9.dp).background(com.deepline.fishing.ui.kit.GameColors.Badge, CircleShape))
                }
                Text(
                    stringResource(tab.labelRes),
                    maxLines = 1,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    color = if (selected) amber else quiet,
                )
            }
        }
    }
}

/** Whether [screen] is shown inside the tab scaffold. */
fun isTabScreen(screen: Screen): Boolean = MainTab.of(screen) != null

/** The header of a tab: a big title, an optional subtitle line, and actions on the right. */
@Composable
fun TabScreen(
    title: String,
    actions: @Composable RowScope.() -> Unit = {},
    subtitle: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.headlineSmall.copy(brush = headlineBrush()),
                    fontWeight = FontWeight.Black,
                    maxLines = 1,
                )
                subtitle?.invoke()
            }
            actions()
        }
        content()
    }
}
