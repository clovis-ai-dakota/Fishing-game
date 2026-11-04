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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.deepline.fishing.GameViewModel
import com.deepline.fishing.MainTab
import com.deepline.fishing.Screen
import com.deepline.fishing.ui.dex.DexScreen
import com.deepline.fishing.ui.tour.TourTarget
import com.deepline.fishing.ui.tour.tourTarget

private val MainTab.icon: ImageVector
    get() = when (this) {
        MainTab.FISH -> GameIcons.Fish
        MainTab.DEX -> GameIcons.Dex
        MainTab.UPGRADES -> GameIcons.Upgrades
        MainTab.BATTLE -> GameIcons.Battle
    }

private val MainTab.tourTarget: TourTarget?
    get() = when (this) {
        MainTab.DEX -> TourTarget.TAB_DEX
        MainTab.UPGRADES -> TourTarget.TAB_UPGRADES
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
    val current = MainTab.of(vm.screen) ?: MainTab.FISH
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (current) {
                MainTab.FISH -> HomeScreen(vm)
                MainTab.DEX -> DexScreen(vm)
                MainTab.UPGRADES -> UpgradesScreen(vm)
                MainTab.BATTLE -> BattleSetupScreen(vm)
            }
        }
        NavigationBar(Modifier.tourTarget(TourTarget.NAV_BAR)) {
            for (tab in MainTab.entries) {
                val target = tab.tourTarget
                NavigationBarItem(
                    selected = tab == current,
                    onClick = { vm.selectTab(tab) },
                    icon = { Icon(tab.icon, contentDescription = null) },
                    label = { Text(tab.label, maxLines = 1) },
                    alwaysShowLabel = true,
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.onSecondary,
                        indicatorColor = MaterialTheme.colorScheme.secondary,
                    ),
                    modifier = Modifier
                        .testTag("tab_${tab.name.lowercase()}")
                        .then(if (target != null) Modifier.tourTarget(target) else Modifier),
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
                Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
                subtitle?.invoke()
            }
            actions()
        }
        content()
    }
}
