package com.deepline.fishing.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.background
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
import androidx.compose.animation.AnimatedContent
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
import com.deepline.fishing.ui.theme.atRest

@Composable
fun AppRoot(vm: GameViewModel) {
    val tourTargets = remember { TourTargets() }
    // Kit buttons click: navigation and the main action play the tap; claims and purchases
    // play their own sound when they succeed.
    val pressFeedback = remember(vm) {
        { role: com.deepline.fishing.ui.kit.ActionRole ->
            if (role == com.deepline.fishing.ui.kit.ActionRole.NAV || role == com.deepline.fishing.ui.kit.ActionRole.PLAY) {
                vm.feel.cue(com.deepline.fishing.feel.Cue.UI_TAP)
            }
        }
    }
    val rewardFx = remember { com.deepline.fishing.ui.kit.RewardFx() }
    val backdropCover = remember { com.deepline.fishing.ui.theme.BackdropCover() }
    val calmMotion = vm.feel.settings.reduceMotion
    // Points in motion: when the balance goes down coins leave it for what was bought, and when a
    // cast's points arrive they fly to it (once the menu is back on screen).
    val balance = vm.progress.pointsBalance
    val seenBalance = remember { longArrayOf(balance) }
    androidx.compose.runtime.LaunchedEffect(balance) {
        val before = seenBalance[0]
        seenBalance[0] = balance
        if (calmMotion || balance == before) return@LaunchedEffect
        if (balance < before) {
            rewardFx.spent()
        } else {
            kotlinx.coroutines.delay(ScreenMotion.SURFACE_MS.toLong())
            rewardFx.earned()
        }
    }
    CompositionLocalProvider(
        LocalTourTargets provides tourTargets,
        com.deepline.fishing.ui.kit.LocalPressFeedback provides pressFeedback,
        com.deepline.fishing.ui.kit.LocalCalmMotion provides calmMotion,
        com.deepline.fishing.ui.kit.LocalRewardFx provides rewardFx.takeIf { !calmMotion },
        com.deepline.fishing.ui.theme.LocalBackdropCover provides backdropCover,
    ) {
        BackHandler(enabled = vm.screen != Screen.VILLAGE) { vm.onBack() }
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Box(Modifier.fillMaxSize()) {
                // The theme's sea scene behind every menu (a cast draws its own water): the camera
                // travels to the menu's place in the world.
                val calm = vm.feel.settings.reduceMotion
                if (vm.screen != Screen.CAST) {
                    com.deepline.fishing.ui.theme.ThemeBackdrop(
                        com.deepline.fishing.ui.theme.LocalMenuPalette.current,
                        Modifier.fillMaxSize(),
                        place = com.deepline.fishing.ui.theme.Place.of(vm.screen),
                        travelMs = if (calm || ScreenMotion.instant) 0 else com.deepline.fishing.ui.theme.CAMERA_MS,
                        // The scene's layers shift as the phone tilts: depth without a 3D view.
                        tilt = com.deepline.fishing.ui.kit.rememberDeviceTilt(enabled = !calm),
                        // Not drawn while the Fish tab's own scene covers all of it.
                        hidden = { backdropCover.covered },
                    )
                }
                // One screen at a time, each full-screen: the village, a building's menu, or what
                // opens from it. A move says where the player goes (see ScreenMotion).
                AnimatedContent(
                    targetState = vm.screen,
                    transitionSpec = { ScreenMotion.between(initialState, targetState, calm, vm.openOrigin) },
                    label = "screen",
                    modifier = Modifier.fillMaxSize(),
                ) { screen ->
                    CompositionLocalProvider(com.deepline.fishing.ui.theme.LocalScreenSettled provides atRest) {
                    when (screen) {
                        Screen.VILLAGE -> com.deepline.fishing.ui.village.VillageScreen(vm)
                        Screen.HOME -> HomeScreen(vm)
                        Screen.DEX -> com.deepline.fishing.ui.dex.DexScreen(vm)
                        Screen.SHOP -> com.deepline.fishing.ui.shop.ShopScreen(vm)
                        Screen.BATTLE_SETUP -> BattleSetupScreen(vm)
                        Screen.CAST -> CastScreen(vm)
                        Screen.FISH_DETAIL -> FishDetailScreen(vm)
                        Screen.MILESTONES -> MilestonesScreen(vm)
                        Screen.BATTLE -> BattleScreen(vm)
                        Screen.ONLINE_LOBBY -> OnlineLobbyScreen(vm)
                        Screen.BOAT_EDITOR -> com.deepline.fishing.ui.boat.BoatEditorScreen(vm)
                        Screen.AQUARIUM -> com.deepline.fishing.ui.shop.AquariumScreen(vm)
                        Screen.THEMES -> com.deepline.fishing.ui.shop.ThemesScreen(vm)
                        Screen.SETTINGS -> com.deepline.fishing.ui.settings.SettingsScreen(vm)
                        Screen.ROPE -> com.deepline.fishing.ui.rope.RopeScreen(vm)
                        Screen.DAILY -> com.deepline.fishing.ui.daily.DailyScreen(vm)
                        Screen.SOCIAL -> com.deepline.fishing.ui.social.SocialHubScreen(vm)
                        Screen.FRIENDS -> com.deepline.fishing.ui.social.FriendsScreen(vm)
                        Screen.LEADERBOARDS -> com.deepline.fishing.ui.social.LeaderboardsScreen(vm)
                        Screen.TOURNAMENT -> com.deepline.fishing.ui.social.TournamentScreen(vm)
                        // Clubs ship in a later update: without the flag there is no club screen at all.
                        Screen.CLUBS -> if (com.deepline.fishing.FeatureFlags.clubs) com.deepline.fishing.ui.social.ClubsScreen(vm) else com.deepline.fishing.ui.social.SocialHubScreen(vm)
                    }
                    }
                }
                // Coins burst out of claimed rewards, above the screens.
                com.deepline.fishing.ui.kit.RewardFxLayer(rewardFx, Modifier.fillMaxSize())
                vm.tour?.let { TourOverlay(it, onNext = vm::tourNext, onBack = vm::tourBack, onSkip = vm::skipTour) }
                // What just happened in the shop (a purchase, a pending payment), above the tab bar.
                val message = vm.shopMessage
                if (message != null && (vm.screen == Screen.VILLAGE || vm.screen == Screen.SHOP) && vm.tour == null) {
                    Snackbar(
                        action = { TextButton(onClick = vm::dismissShopMessage) { Text(stringResource(R.string.common_ok)) } },
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .navigationBarsPadding()
                            .padding(start = 12.dp, end = 12.dp, bottom = 16.dp),
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
fun SubScreen(
    title: String,
    onBack: () -> Unit,
    /** Buttons at the right of the header (a balance, a shortcut). */
    actions: @Composable RowScope.() -> Unit = {},
    /** A line under the title. */
    subtitle: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(Modifier.fillMaxSize()) {
        // The phone's navigation bar stands on the same deep water as the tab bar and a screen's
        // dock: without it the scene's seabed shows under the system buttons, cut off by the
        // content above it (seen on a phone with three-button navigation).
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .windowInsetsBottomHeight(WindowInsets.navigationBars)
                .background(com.deepline.fishing.ui.kit.deepColor()),
        )
        SubScreenContent(title, onBack, actions, subtitle, content)
    }
}

@Composable
private fun SubScreenContent(
    title: String,
    onBack: () -> Unit,
    actions: @Composable RowScope.() -> Unit,
    subtitle: (@Composable () -> Unit)?,
    content: @Composable ColumnScope.() -> Unit,
) {
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
            TextButton(onClick = onBack, modifier = Modifier.testTag("back")) { Text(stringResource(R.string.common_back)) }
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleLarge.copy(brush = headlineBrush()),
                    fontWeight = FontWeight.Black,
                )
                subtitle?.invoke()
            }
            actions()
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
