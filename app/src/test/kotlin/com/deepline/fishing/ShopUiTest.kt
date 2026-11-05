package com.deepline.fishing

import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import com.deepline.fishing.core.BotDifficulty
import com.deepline.fishing.core.Grant
import com.deepline.fishing.core.PlayerProgress
import com.deepline.fishing.core.RopeStat
import com.deepline.fishing.core.ShopCatalog
import com.deepline.fishing.core.ShopClock
import com.deepline.fishing.core.BattleMode
import com.deepline.fishing.billing.TestStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

private fun midGamePlayer(points: Long = 400_000) =
    PlayerProgress(pointsBalance = points, pointsEarned = points, ropeLevels = mapOf(RopeStat.LENGTH to 4), lanterns = 2)

/** Points purchases: the price is confirmed first, then the pack arrives. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w400dp-h860dp-xxhdpi")
class ShopPointsUiTest : BoatUiTestBase() {
    @Test
    fun buyingWithPointsAsksFirst() {
        val vm = launch(midGamePlayer())
        compose.onNodeWithTag("tab_shop").performClick()
        assertEquals(Screen.SHOP, vm.screen)
        compose.onNodeWithTag("shop_page_tackle").performClick()
        val crate = ShopCatalog.byId("lantern_crate")!!
        val price = vm.shopPointsPrice(crate)
        val count = ShopCatalog.packCount(crate, crate.grants.first() as Grant.TacklePack, vm.progress)
        compose.shot("62_shop_tackle")
        compose.onNodeWithTag("shop_list").performScrollToNode(hasTestTag("buy_pts_lantern_crate"))
        compose.onNodeWithTag("buy_pts_lantern_crate").performClick()
        // Nothing is spent until the player confirms.
        assertEquals(400_000L, vm.progress.pointsBalance)
        compose.shot("63_shop_confirm_points")
        compose.onNodeWithTag("confirm_points").performClick()
        assertEquals(400_000L - price, vm.progress.pointsBalance)
        assertEquals(2 + count, vm.progress.lanterns)
        assertTrue(vm.shopMessage!!.contains("$count Lanterns"))
    }
}

/** Euro purchases go through the test store: approved, pending then restored, failed. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w400dp-h860dp-xxhdpi")
class ShopStoreUiTest : BoatUiTestBase() {
    @Test
    fun payingThroughTheTestStore() {
        val vm = launch(midGamePlayer(points = 0))
        assertNotNull("debug builds use the test store", vm.testStore)
        compose.onNodeWithTag("tab_shop").performClick()
        compose.onNodeWithTag("shop_page_tackle").performClick()
        val pouch = ShopCatalog.byId("lantern_pouch")!!
        val count = ShopCatalog.packCount(pouch, pouch.grants.first() as Grant.TacklePack, vm.progress)

        // Approved: the pouch arrives straight away.
        compose.onNodeWithTag("shop_list").performScrollToNode(hasTestTag("buy_eur_lantern_pouch"))
        compose.onNodeWithTag("buy_eur_lantern_pouch").performClick()
        assertEquals(pouch, vm.testStore!!.request)
        compose.shot("64_test_store")
        compose.onNodeWithTag("test_store_approve").performClick()
        assertEquals(2 + count, vm.progress.lanterns)
        compose.onNodeWithText("Thank you!", substring = true).assertExists()

        // Pending: nothing yet, then it arrives when purchases are restored.
        compose.onNodeWithTag("buy_eur_lantern_pouch").performClick()
        compose.onNodeWithTag("test_store_pending").performClick()
        assertEquals(2 + count, vm.progress.lanterns)
        assertTrue(vm.shopMessage!!.contains("waiting for the payment"))
        vm.restorePurchases()
        compose.waitForIdle()
        assertEquals(2 + 2 * count, vm.progress.lanterns)

        // Failed: nothing changes.
        compose.onNodeWithTag("buy_eur_lantern_pouch").performClick()
        compose.onNodeWithTag("test_store_fail").performClick()
        assertEquals(2 + 2 * count, vm.progress.lanterns)
        assertTrue(vm.shopMessage!!.startsWith("The payment didn't go through"))
    }

}

/** A premade boat bought from the shop can be worn. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w400dp-h860dp-xxhdpi")
class PremadeBoatUiTest : BoatUiTestBase() {
    @Test
    fun aPremadeBoatIsWornOnceBought() {
        val vm = launch(midGamePlayer(points = 0))
        vm.openShop(ShopPage.STYLE)
        compose.waitForIdle()
        compose.shot("65_shop_style")
        val boat = ShopCatalog.byId("boat_lagoon_explorer")!!
        vm.buyWithMoney(boat, null)
        vm.testStore!!.answer(TestStore.TestOutcome.APPROVE)
        compose.waitForIdle()
        assertTrue(ShopCatalog.ownsPreset(vm.progress, "lagoon_explorer"))
        // Bought once: buying again isn't offered.
        assertFalse(vm.canBuy(boat))
        vm.wearPreset("lagoon_explorer")
        assertTrue(vm.isWearingPreset("lagoon_explorer"))
        assertTrue(vm.progress.customBoatEquipped)
        // Paints are previewed on the player's boat; gear tiles show the piece itself.
        compose.onNodeWithTag("shop_list").performScrollToNode(androidx.compose.ui.test.hasText("Paints, emblems and prints"))
        compose.shot("65b_shop_paints")
        compose.onNodeWithTag("shop_list").performScrollToNode(androidx.compose.ui.test.hasText("Hooks, lines and trails"))
        compose.shot("65c_shop_gear")
    }
}

/** The Captain's Club's monthly pouch, and the starter pack offer from the third session. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w400dp-h860dp-xxhdpi")
class ShopPassesUiTest : BoatUiTestBase() {
    @Test
    fun clubMembersClaimAFreePouchEachMonth() {
        val now = ShopClock.now()
        val vm = launch(midGamePlayer().copy(clubUntilMs = now + 10 * 86_400_000L))
        assertTrue(vm.isClubMember)
        compose.onNodeWithTag("tab_shop").performClick()
        compose.shot("66_shop_club_pouch")
        val before = vm.progress.lanterns
        compose.onNodeWithTag("claim_club_pouch").performClick()
        assertTrue(vm.progress.lanterns > before)
        assertFalse(vm.clubPouchReady)
    }
}

/** The starter pack offer opens on the third session (its own class: saved state is per class). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w400dp-h860dp-xxhdpi")
class StarterPackUiTest : BoatUiTestBase() {
    @Test
    fun theStarterPackIsOfferedFromTheThirdSession() {
        val vm = launch(midGamePlayer().copy(sessions = 2))
        assertEquals(3, vm.progress.sessions)
        assertTrue(vm.starterOfferOpen)
        vm.openShop(ShopPage.PASSES)
        compose.waitForIdle()
        compose.onNodeWithText("Starter pack").assertExists()
        compose.shot("67_shop_passes")
        vm.buyWithMoney(ShopCatalog.byId("starter_pack")!!, null)
        vm.testStore!!.answer(TestStore.TestOutcome.APPROVE)
        compose.waitForIdle()
        assertFalse(vm.starterOfferOpen)
        assertTrue(ShopCatalog.ownsPreset(vm.progress, "coral_cruiser"))
    }
}

/** Lanterns and detectors in battles: switched on from the battle tab, one used per cast. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w400dp-h860dp-xxhdpi")
class BattleTackleUiTest : BoatUiTestBase() {
    @Test
    fun sightTackleWorksInBattles() {
        val vm = launch(midGamePlayer().copy(lanterns = 3, detectors = 2))
        compose.onNodeWithTag("tab_battle").performClick()
        compose.onNodeWithTag("battle_lantern").performScrollTo().performClick()
        compose.onNodeWithTag("battle_detector").performScrollTo().performClick()
        assertTrue("lantern on", vm.useLantern)
        assertTrue("detector on (${vm.progress.detectors})", vm.useDetector)
        compose.shot("68_battle_tackle")
        vm.startBattle(BattleMode.BEST_OF_FIVE, Opponent.Computer(BotDifficulty.EASY))
        vm.playNextTurn()
        val s = vm.session
        assertNotNull(s)
        assertTrue(s!!.lanternActive)
        assertTrue(s.detectorActive)
        assertEquals(2, vm.progress.lanterns)
        assertEquals(1, vm.progress.detectors)
        assertNotNull(vm.detectorTargets)
        vm.abandonCast()
        assertNull(vm.session)
    }
}

/** The season track and a weekend event, on the Passes page (the clock is set to a Friday evening). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w400dp-h860dp-xxhdpi")
class SeasonUiTest : BoatUiTestBase() {
    @org.junit.After
    fun resetClock() {
        ShopClock.override = null
    }

    @Test
    fun claimingSeasonTiersAndAnEventReward() {
        // Season 2's first Friday, 20:00 UTC: a weekend event is on.
        val now = com.deepline.fishing.core.Seasons.EPOCH_MS + com.deepline.fishing.core.GameConfig.SEASON_WEEKS * 7 * 86_400_000L + 4 * 86_400_000L + 20 * 3_600_000L
        ShopClock.override = now
        val season = com.deepline.fishing.core.Seasons.at(now)
        val event = com.deepline.fishing.core.Seasons.eventAt(now)!!
        val vm = launch(
            midGamePlayer(points = 0).copy(
                seasonNumber = season.number, seasonXp = 4 * com.deepline.fishing.core.GameConfig.SEASON_XP_PER_TIER + 30,
                eventKey = event.key, eventProgress = event.goal.target,
            ),
        )
        assertTrue(vm.seasonRewardsReady)
        vm.openShop(ShopPage.PASSES)
        compose.waitForIdle()
        compose.shot("69_season")
        val before = vm.progress.lanterns + vm.progress.detectors + vm.progress.luckyCharms + vm.progress.sparkleLures + vm.progress.pointsBalance
        compose.onNodeWithTag("claim_season").performClick()
        assertTrue(com.deepline.fishing.core.Seasons.claimable(vm.progress, now).isEmpty())
        assertTrue(vm.progress.lanterns + vm.progress.detectors + vm.progress.luckyCharms + vm.progress.sparkleLures + vm.progress.pointsBalance > before)

        // The pass opens the premium track for the tiers already reached.
        vm.buyWithMoney(ShopCatalog.byId("season_pass")!!, null)
        vm.testStore!!.answer(TestStore.TestOutcome.APPROVE)
        compose.waitForIdle()
        assertTrue(com.deepline.fishing.core.Seasons.claimable(vm.progress, now).all { it.premium })
        vm.claimSeasonRewards()
        assertTrue("badge:SEASON" in vm.progress.ownedItems)

        compose.onNodeWithTag("shop_list").performScrollToNode(hasTestTag("claim_event"))
        compose.onNodeWithTag("claim_event").performClick()
        assertTrue("free" in vm.progress.eventClaims)
        assertFalse(vm.seasonRewardsReady)
    }
}
