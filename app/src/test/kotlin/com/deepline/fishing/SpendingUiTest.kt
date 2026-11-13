package com.deepline.fishing

import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.performScrollToNode
import com.deepline.fishing.core.BotDifficulty
import com.deepline.fishing.core.GameConfig
import com.deepline.fishing.core.PlayerProgress
import com.deepline.fishing.core.Progression
import com.deepline.fishing.core.RopeStat
import com.deepline.fishing.core.Tackle
import com.deepline.fishing.ui.formatPoints
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The tackle shop: buy a Lucky charm, switch it on, and the cast gets an extra bump. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w400dp-h860dp-xxhdpi")
class TackleShopUiTest : BoatUiTestBase() {
    @Test
    fun buyAndUseALuckyCharm() {
        val vm = launch(PlayerProgress(pointsBalance = 50_000, pointsEarned = 50_000, ropeLevels = mapOf(RopeStat.LENGTH to 4)))
        compose.onNodeWithTag("tab_shop").performClick()
        val price = Progression.tacklePrice(vm.progress, Tackle.LUCKY_CHARM)
        compose.onNodeWithTag("shop_page_tackle").performClick()
        compose.shot("60_tackle_shop")
        // The charm's card: buy just one with points.
        val before = vm.progress.pointsBalance
        compose.onNodeWithTag("shop_list").performScrollToNode(hasTestTag("buy_one_lucky_charm"))
        compose.onNodeWithTag("buy_one_lucky_charm").performClick()
        assertEquals(before - price, vm.progress.pointsBalance)
        assertEquals(1, vm.progress.luckyCharms)

        compose.onNodeWithTag("tab_fish").performClick()
        compose.click("Lucky charm ×1")
        assertTrue(vm.useCharm)
        compose.click("Cast line")
        val session = vm.session!!
        assertEquals(GameConfig.DESCENT_TOUCH_LIMIT + GameConfig.LUCKY_CHARM_EXTRA_BUMPS, session.touchLimit)
        assertEquals(0, vm.progress.luckyCharms)
    }
}

/** Wagers against the computer: the stake is taken, a forfeit loses it. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w400dp-h860dp-xxhdpi")
class WagerUiTest : BoatUiTestBase() {
    @Test
    fun stakeAndLoseAWager() {
        val vm = launch(PlayerProgress(pointsBalance = 10_000, pointsEarned = 10_000))
        compose.onNodeWithTag("tab_battle").performClick()
        compose.click("Wager (optional)")
        val stake = GameConfig.WAGER_STAKES.first { it <= 10_000 }
        compose.click(com.deepline.fishing.ui.compactPointsText(stake).text)
        compose.shot("61_wager")
        compose.click("Start battle")
        assertEquals(10_000 - stake, vm.progress.pointsBalance)
        assertEquals(stake, vm.battleStake)
        vm.forfeitBattle()
        compose.waitForIdle()
        assertEquals(false, vm.lastWagerResult?.won)
        assertEquals(10_000 - stake, vm.progress.pointsBalance)
        assertEquals(BotDifficulty.NORMAL, vm.lastWagerResult?.rival)
        compose.onNodeWithText(str(R.string.battle_wager_lost, str(R.string.common_points, formatPoints(stake)))).assertExists()
    }
}
