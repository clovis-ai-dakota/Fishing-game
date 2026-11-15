package com.deepline.fishing.ui

import com.deepline.fishing.ui.kit.rewardBalance
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.deepline.fishing.DexSection
import com.deepline.fishing.GameViewModel
import com.deepline.fishing.R
import com.deepline.fishing.Screen
import com.deepline.fishing.ShopPage
import com.deepline.fishing.core.Daily
import com.deepline.fishing.core.DepthZone
import com.deepline.fishing.core.FishCatalog
import com.deepline.fishing.core.Progression
import com.deepline.fishing.core.RopeStat
import com.deepline.fishing.core.Seasons
import com.deepline.fishing.core.ShopClock
import com.deepline.fishing.ui.daily.DailyIcons
import com.deepline.fishing.ui.i18n.asString
import com.deepline.fishing.ui.i18n.label
import com.deepline.fishing.ui.kit.ActionRole
import com.deepline.fishing.ui.kit.ButtonSize
import com.deepline.fishing.ui.kit.GameButton
import com.deepline.fishing.ui.kit.GameColors
import com.deepline.fishing.ui.kit.InfoButton
import com.deepline.fishing.ui.kit.KitIcons
import com.deepline.fishing.ui.kit.RoundIconButton
import com.deepline.fishing.ui.kit.ScenePill
import com.deepline.fishing.ui.kit.StatPill
import com.deepline.fishing.ui.kit.TackleTile
import com.deepline.fishing.ui.kit.deepColor
import com.deepline.fishing.ui.kit.drawBehindDock
import com.deepline.fishing.ui.shop.ShopText
import com.deepline.fishing.ui.tour.TourTarget
import com.deepline.fishing.ui.tour.tourTarget

/**
 * The Fish tab: the game's hub, as a scene. The boat floats in the middle (tap it to customise
 * it); around it, round buttons with counts and countdowns stand in for cards of text: points and
 * milestones at the top, today's reward, a migration and the season on the left, the Dex on the
 * right, the rope's three numbers above the dock. The dock holds the tackle for the next cast and
 * the one main action, Cast line. Explanations are behind (i) buttons and in the tour.
 */
@Composable
fun HomeScreen(vm: GameViewModel) {
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            // Your boat at the surface, wearing your skins (live 3D where available).
            SkinPreview(
                vm.skins,
                height = null,
                modifier = Modifier.fillMaxSize().tourTarget(TourTarget.HOME_BOAT),
                cameraTop = -11.5f,
                onClick = { vm.openDex(DexSection.SKINS) },
                clickLabel = stringResource(R.string.home_boat_action),
            )
            TopBar(vm, Modifier.align(Alignment.TopCenter))
            EventRail(vm, Modifier.align(Alignment.CenterStart).padding(start = 12.dp))
            DexButton(vm, Modifier.align(Alignment.CenterEnd).padding(end = 12.dp))
            RopeStrip(vm, Modifier.align(Alignment.BottomCenter).padding(bottom = 10.dp))
        }
        CastDock(vm)
    }
}

/** Points on the left (with the title worn, if any); milestones, themes and settings on the right. */
@Composable
private fun TopBar(vm: GameViewModel, modifier: Modifier = Modifier) {
    val p = vm.progress
    Column(modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 12.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                ScenePill(Modifier.tourTarget(TourTarget.HOME_POINTS).rewardBalance()) {
                    Icon(GameIcons.Coin, contentDescription = null, tint = SparkleGold, modifier = Modifier.size(20.dp))
                    // From a million on, "1.2M": the exact balance is on the Shop tab.
                    Text(
                        if (p.pointsBalance >= 1_000_000) compactPoints(p.pointsBalance) else formatPoints(p.pointsBalance),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                    )
                }
                val title = vm.title
                val frame = vm.titleFrame
                if (title != null) {
                    TitleBadge(title, frame = frame)
                } else if (frame != com.deepline.fishing.core.TitleFrame.GOLD) {
                    // A badge frame without a title yet: the player's name, framed.
                    TitleBadge(vm.playerName, frame = frame)
                }
            }
            RoundIconButton(
                GameIcons.Trophy,
                stringResource(R.string.home_cd_milestones),
                onClick = { vm.navigate(Screen.MILESTONES) },
                modifier = Modifier.tourTarget(TourTarget.HOME_MILESTONES),
                role = if (Progression.claimableCount(p) > 0) ActionRole.CLAIM else ActionRole.NAV,
                badge = Progression.claimableCount(p),
                caption = stringResource(R.string.home_cd_milestones),
            )
            RoundIconButton(
                GameIcons.Palette, stringResource(R.string.home_cd_themes), onClick = vm::openThemes,
                modifier = Modifier.tourTarget(TourTarget.HOME_THEMES), caption = stringResource(R.string.home_cd_themes),
            )
            RoundIconButton(
                GameIcons.Settings, stringResource(R.string.home_cd_settings), onClick = vm::openSettings,
                modifier = Modifier.tourTarget(TourTarget.HOME_SETTINGS), caption = stringResource(R.string.home_cd_settings),
            )
        }
        // Once per machine-translated language: the translation may be approximate.
        com.deepline.fishing.ui.i18n.MachineTranslationNotice()
    }
}

/** What's on now, each with its count or countdown: today's reward and challenges, a migration, the season. */
@Composable
private fun EventRail(vm: GameViewModel, modifier: Modifier = Modifier) {
    val p = vm.progress
    val now = ShopClock.now()
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        val status = Daily.status(p, vm.daily.today)
        val ready = (if (status.canClaim) 1 else 0) + vm.daily.challenges.count { it.claimable }
        RoundIconButton(
            DailyIcons.Flame,
            stringResource(R.string.daily_title),
            onClick = vm::openDaily,
            modifier = Modifier.tourTarget(TourTarget.HOME_DAILY).testTag("daily_button"),
            role = if (ready > 0) ActionRole.CLAIM else ActionRole.NAV,
            badge = ready,
            // The streak, in days in a row.
            label = if (status.streak > 0) formatPoints(status.streak) else null,
            caption = stringResource(R.string.daily_title),
        )
        val migration = vm.daily.migration
        if (migration != null) {
            RoundIconButton(
                DailyIcons.School,
                stringResource(R.string.daily_migration_banner_title),
                onClick = vm::openDaily,
                modifier = Modifier.testTag("migration_banner"),
                label = ShopText.timeLeft(migration.msLeft(now)).asString(),
                caption = stringResource(R.string.home_caption_migration),
            )
        }
        val season = Seasons.at(now)
        RoundIconButton(
            GameIcons.Star,
            stringResource(R.string.shop_season_title, season.number, season.label()),
            onClick = { vm.openShop(ShopPage.PASSES) },
            modifier = Modifier.testTag("season_button"),
            role = if (vm.seasonRewardsReady) ActionRole.CLAIM else ActionRole.NAV,
            label = ShopText.timeLeft(season.msLeft(now)).asString(),
            caption = stringResource(R.string.home_caption_season),
        )
    }
}

/** The collection so far, on the way to the Dex. */
@Composable
private fun DexButton(vm: GameViewModel, modifier: Modifier = Modifier) {
    val p = vm.progress
    RoundIconButton(
        GameIcons.Dex,
        stringResource(R.string.home_dex_caught, p.discoveredCount, FishCatalog.all.size),
        onClick = { vm.openDex() },
        modifier = modifier.testTag("dex_button"),
        label = stringResource(R.string.common_fraction, p.discoveredCount, FishCatalog.all.size),
        caption = stringResource(R.string.home_dex_title),
    )
}

/** The rope in three numbers (how deep, how heavy, how fast); the rest is behind the (i) and in the Shop. */
@Composable
private fun RopeStrip(vm: GameViewModel, modifier: Modifier = Modifier) {
    val p = vm.progress
    val specs = p.specs
    val reachZone = DepthZone.of(specs.lengthM - 0.01f)
    val deepest = p.collection.values.maxOfOrNull { it.deepestM } ?: 0f
    val details = stringResource(R.string.home_rope_reach, metersText(specs.lengthM).asString(), reachZone.label()) + "\n\n" +
        if (deepest > 0f) {
            stringResource(R.string.home_rope_stats_deepest, formatPoints(p.castsMade), formatPoints(p.pointsEarned), metersText(deepest).asString())
        } else {
            stringResource(R.string.home_rope_stats, formatPoints(p.castsMade), formatPoints(p.pointsEarned))
        }
    Row(modifier.testTag("rope_strip"), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        StatPill(KitIcons.Depth, formatStat(RopeStat.LENGTH, RopeStat.LENGTH.valueAt(p.level(RopeStat.LENGTH))))
        StatPill(KitIcons.Weight, formatStat(RopeStat.STRENGTH, RopeStat.STRENGTH.valueAt(p.level(RopeStat.STRENGTH))))
        StatPill(KitIcons.Speed, formatStat(RopeStat.AGILITY, RopeStat.AGILITY.valueAt(p.level(RopeStat.AGILITY))))
        ScenePill { InfoButton(stringResource(R.string.home_rope_title), details, Modifier.size(22.dp), tint = GameColors.OnDeep, tag = "rope_info") }
    }
}

/** The thumb zone: the tackle for the next cast, and the one main action. */
@Composable
private fun CastDock(vm: GameViewModel) {
    val p = vm.progress
    Column(Modifier.fillMaxWidth().testTag("cast_dock")) {
        Box(Modifier.fillMaxWidth().dockBackground()) {
            Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 10.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally)) {
                    TackleTile(
                        GameIcons.Lantern, p.lanterns, vm.useLantern, vm::toggleLantern,
                        stringResource(R.string.home_lanterns, p.lanterns),
                        Modifier.tourTarget(TourTarget.HOME_LANTERN).testTag("tackle_lantern"),
                        onEmpty = { vm.openShop(ShopPage.TACKLE) },
                    )
                    TackleTile(
                        KitIcons.Radar, p.detectors, vm.useDetector, vm::toggleDetector,
                        stringResource(R.string.home_tackle_detector, p.detectors),
                        Modifier.testTag("tackle_detector"),
                        onEmpty = { vm.openShop(ShopPage.TACKLE) },
                    )
                    TackleTile(
                        KitIcons.Clover, p.luckyCharms, vm.useCharm, vm::toggleCharm,
                        stringResource(R.string.home_tackle_charm, p.luckyCharms),
                        Modifier.testTag("tackle_charm"),
                        onEmpty = { vm.openShop(ShopPage.TACKLE) },
                    )
                    TackleTile(
                        GameIcons.Sparkle, p.sparkleLures, vm.useLure, vm::toggleLure,
                        stringResource(R.string.home_tackle_lure, p.sparkleLures),
                        Modifier.testTag("tackle_lure"),
                        onEmpty = { vm.openShop(ShopPage.TACKLE) },
                    )
                }
                GameButton(
                    onClick = vm::startCast,
                    modifier = Modifier.fillMaxWidth().tourTarget(TourTarget.HOME_CAST),
                    role = ActionRole.PLAY,
                    size = ButtonSize.HERO,
                ) {
                    Icon(KitIcons.Hook, contentDescription = null, modifier = Modifier.size(28.dp))
                    Spacer(Modifier.size(2.dp))
                    Text(stringResource(R.string.home_cast_button))
                }
            }
        }
    }
}

/** The deep water under the action zone and the tabs. */
@Composable
fun Modifier.dockBackground(): Modifier = drawBehindDock(deepColor())
