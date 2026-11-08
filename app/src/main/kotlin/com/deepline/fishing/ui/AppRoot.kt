package com.deepline.fishing.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Snackbar
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.ui.platform.testTag
import com.deepline.fishing.billing.TestStore
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.deepline.fishing.GameViewModel
import com.deepline.fishing.R
import com.deepline.fishing.Screen
import com.deepline.fishing.ui.i18n.asString
import com.deepline.fishing.ui.i18n.label
import com.deepline.fishing.ui.dex.FishDetailScreen
import com.deepline.fishing.ui.secrets.FishingSecrets
import com.deepline.fishing.ui.secrets.SecretDiscoveredDialog
import com.deepline.fishing.ui.tour.LocalTourTargets
import com.deepline.fishing.ui.tour.TourOverlay
import com.deepline.fishing.ui.tour.TourTargets

@Composable
fun AppRoot(vm: GameViewModel) {
    val tourTargets = remember { TourTargets() }
    CompositionLocalProvider(LocalTourTargets provides tourTargets) {
        BackHandler(enabled = vm.screen != Screen.HOME) { vm.onBack() }
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Box(Modifier.fillMaxSize()) {
                // The theme's sea scene behind every menu (a cast draws its own water).
                if (vm.screen != Screen.CAST) {
                    com.deepline.fishing.ui.theme.ThemeBackdrop(com.deepline.fishing.ui.theme.LocalMenuPalette.current, Modifier.fillMaxSize())
                }
                when (vm.screen) {
                    // The four tabs share the bottom navigation bar.
                    Screen.HOME, Screen.DEX, Screen.SHOP, Screen.BATTLE_SETUP -> MainScaffold(vm)
                    // Full screens, without the bar.
                    Screen.CAST -> CastScreen(vm)
                    Screen.FISH_DETAIL -> FishDetailScreen(vm)
                    Screen.MILESTONES -> MilestonesScreen(vm)
                    Screen.BATTLE -> BattleScreen(vm)
                    Screen.ONLINE_LOBBY -> OnlineLobbyScreen(vm)
                    Screen.BOAT_EDITOR -> com.deepline.fishing.ui.boat.BoatEditorScreen(vm)
                    Screen.AQUARIUM -> com.deepline.fishing.ui.shop.AquariumScreen(vm)
                    Screen.THEMES -> com.deepline.fishing.ui.shop.ThemesScreen(vm)
                    Screen.SETTINGS -> com.deepline.fishing.ui.settings.SettingsScreen(vm)
                    // -- Daily loop screens below.
                    Screen.DAILY -> com.deepline.fishing.ui.daily.DailyScreen(vm)

                    // -- Social screens below.
                    Screen.SOCIAL -> com.deepline.fishing.ui.social.SocialHubScreen(vm)
                    Screen.FRIENDS -> com.deepline.fishing.ui.social.FriendsScreen(vm)
                    Screen.LEADERBOARDS -> com.deepline.fishing.ui.social.LeaderboardsScreen(vm)
                    Screen.TOURNAMENT -> com.deepline.fishing.ui.social.TournamentScreen(vm)
                    // Clubs ship in a later update: without the flag there is no club screen at all.
                    Screen.CLUBS -> if (com.deepline.fishing.FeatureFlags.clubs) com.deepline.fishing.ui.social.ClubsScreen(vm) else com.deepline.fishing.ui.social.SocialHubScreen(vm)

                }
                vm.tour?.let { TourOverlay(it, onNext = vm::tourNext, onBack = vm::tourBack, onSkip = vm::skipTour) }
                // What just happened in the shop (a purchase, a pending payment), above the tab bar.
                val message = vm.shopMessage
                if (message != null && isTabScreen(vm.screen) && vm.tour == null) {
                    Snackbar(
                        action = { TextButton(onClick = vm::dismissShopMessage) { Text(stringResource(R.string.common_ok)) } },
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .navigationBarsPadding()
                            .padding(start = 12.dp, end = 12.dp, bottom = 88.dp),
                    ) { Text(message.asString()) }
                }
            }
        }
        // Picking a gift for an online opponent.
        if (vm.giftTarget != null) com.deepline.fishing.ui.shop.GiftDialog(vm)
        // Debug builds pay through the test store: the tester decides how each payment goes.
        vm.testStore?.request?.let { item -> TestStoreDialog(item, onAnswer = { vm.testStore?.answer(it) }) }
        // Fishing secrets are explained wherever they happen: in a cast, after a battle, at home.
        vm.secretPopup?.let { id ->
            FishingSecrets.byId(id)?.let { SecretDiscoveredDialog(it, onAnswer = vm::answerSecretPopup) }
        }
        if (vm.askTourAgain) {
            AlertDialog(
                onDismissRequest = { vm.answerTourAgain(false) },
                title = { Text(stringResource(R.string.shell_tour_again_title)) },
                text = { Text(stringResource(R.string.shell_tour_again_body)) },
                confirmButton = { TextButton(onClick = { vm.answerTourAgain(true) }) { Text(stringResource(R.string.shell_tour_again_yes)) } },
                dismissButton = { TextButton(onClick = { vm.answerTourAgain(false) }) { Text(stringResource(R.string.common_no_thanks)) } },
            )
        }
        // Privacy: asked once before anything is collected; the tour waits for the answer.
        if (vm.privacy.askConsent) com.deepline.fishing.privacy.ConsentSheet(onAnswer = vm.privacy::choose)
    }
}

/** The test store's payment sheet: no real money, the tester picks the outcome. */
@Composable
private fun TestStoreDialog(item: com.deepline.fishing.core.ShopItem, onAnswer: (TestStore.TestOutcome) -> Unit) {
    AlertDialog(
        onDismissRequest = { onAnswer(TestStore.TestOutcome.CANCEL) },
        title = { Text(stringResource(R.string.shell_test_store_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.shell_test_store_body, formatEuros(item.euroCents), item.label()))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { onAnswer(TestStore.TestOutcome.PENDING) }, modifier = Modifier.testTag("test_store_pending")) {
                        Text(stringResource(R.string.shell_test_store_pending))
                    }
                    OutlinedButton(onClick = { onAnswer(TestStore.TestOutcome.FAIL) }, modifier = Modifier.testTag("test_store_fail")) {
                        Text(stringResource(R.string.shell_test_store_fail))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onAnswer(TestStore.TestOutcome.APPROVE) }, modifier = Modifier.testTag("test_store_approve")) {
                Text(stringResource(R.string.shell_test_store_approve))
            }
        },
        dismissButton = { TextButton(onClick = { onAnswer(TestStore.TestOutcome.CANCEL) }) { Text(stringResource(R.string.common_cancel)) } },
    )
}

/** Shared layout for the secondary screens: a back button, a title, then the content. */
@Composable
fun SubScreen(title: String, onBack: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            TextButton(onClick = onBack) { Text(stringResource(R.string.common_back)) }
            Text(
                title,
                style = MaterialTheme.typography.titleLarge.copy(brush = headlineBrush()),
                fontWeight = FontWeight.Black,
            )
        }
        content()
    }
}

/** The theme's gradient for big titles. */
@Composable
fun headlineBrush(): androidx.compose.ui.graphics.Brush {
    val colors = com.deepline.fishing.ui.theme.LocalMenuPalette.current.headline
    return androidx.compose.ui.graphics.Brush.horizontalGradient(if (colors.size > 1) colors else colors + colors)
}
