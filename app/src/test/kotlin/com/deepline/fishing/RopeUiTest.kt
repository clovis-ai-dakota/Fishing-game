package com.deepline.fishing

import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.deepline.fishing.core.PlayerProgress
import com.deepline.fishing.core.Progression
import com.deepline.fishing.core.RopeStat
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The rope workshop (it used to be the Shop's Rope page): the Fish menu's rope numbers open it,
 * an upgrade costs points, and a level comes back for a full refund.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w400dp-h860dp-xxhdpi")
class RopeUiTest : BoatUiTestBase() {
    @Test
    fun upgradeAndRefundTheRopeInTheWorkshop() {
        val start = PlayerProgress(pointsBalance = 10_000, pointsEarned = 10_000)
        val vm = launch(start)
        vm.navigate(Screen.HOME)
        compose.waitForIdle()
        compose.onNodeWithTag("rope_open").performClick()
        assertEquals(Screen.ROPE, vm.screen)
        compose.shot("56_rope_workshop")

        val cost = Progression.upgradeCost(vm.progress, RopeStat.LENGTH)!!
        compose.onNodeWithTag("upgrade_length").performClick()
        assertEquals(1, vm.progress.level(RopeStat.LENGTH))
        assertEquals(start.pointsBalance - cost, vm.progress.pointsBalance)

        // Changed your mind: every point comes back.
        compose.onNodeWithTag("downgrade_length").performClick()
        assertEquals(0, vm.progress.level(RopeStat.LENGTH))
        assertEquals(start.pointsBalance, vm.progress.pointsBalance)

        vm.onBack()
        assertEquals(Screen.VILLAGE, vm.screen)
    }
}
