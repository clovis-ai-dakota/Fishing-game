package com.deepline.fishing

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.ui.test.assertAll
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isNotEnabled
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.deepline.fishing.core.Atmosphere
import com.deepline.fishing.core.BoatPalette
import com.deepline.fishing.core.BoatPart
import com.deepline.fishing.core.BoatParts
import com.deepline.fishing.core.CastResult
import com.deepline.fishing.core.DepthZone
import com.deepline.fishing.core.HullPattern
import com.deepline.fishing.core.PartRequirement
import com.deepline.fishing.core.PlayerProgress
import com.deepline.fishing.core.Progression
import com.deepline.fishing.core.ProgressRepository
import com.deepline.fishing.core.ReverseReason
import com.deepline.fishing.ui.tour.Tour
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Shared setup for the boat customisation UI tests. */
abstract class BoatUiTestBase {
    @get:Rule
    val compose = createEmptyComposeRule()

    protected var scenario: ActivityScenario<MainActivity>? = null

    @Before
    fun clearSavedState() {
        ApplicationProvider.getApplicationContext<Context>()
            .getSharedPreferences("deepline", Context.MODE_PRIVATE)
            .edit().clear().commit()
    }

    @After
    fun tearDown() {
        scenario?.close()
    }

    protected fun launch(progress: PlayerProgress): GameViewModel {
        val store = SharedPrefsStore(ApplicationProvider.getApplicationContext())
        ProgressRepository(store).save(progress)
        store.putString("tour.showAgain", "false")
        store.putString("tour.seenVersion", Tour.VERSION.toString())
        scenario = ActivityScenario.launch(MainActivity::class.java)
        lateinit var vm: GameViewModel
        scenario!!.onActivity { vm = ViewModelProvider(it)[GameViewModel::class.java] }
        return vm
    }

    protected fun ComposeTestRule.click(text: String) {
        val matcher = hasText(text)
        if (onAllNodes(matcher).fetchSemanticsNodes().isEmpty()) {
            onAllNodes(hasScrollAction()).onFirst().performScrollToNode(matcher)
        }
        val node = onNode(matcher)
        runCatching { node.performScrollTo() }
        node.performClick()
    }

    protected fun ComposeTestRule.clickDescription(description: String) {
        val node = onNodeWithContentDescription(description)
        runCatching { node.performScrollTo() }
        node.performClick()
    }

    protected fun isShowing(text: String, substring: Boolean = false) =
        compose.onAllNodes(hasText(text, substring = substring)).fetchSemanticsNodes().isNotEmpty()

    protected fun ComposeTestRule.shot(name: String) {
        waitForIdle()
        lateinit var bitmap: Bitmap
        scenario!!.onActivity { a ->
            val v = a.window.decorView
            bitmap = Bitmap.createBitmap(v.width, v.height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            v.draw(canvas)
            // Dialogs live in their own window: draw the open one on top, dimmed and centered.
            val dialog = org.robolectric.shadows.ShadowDialog.getLatestDialog()
            if (dialog != null && dialog.isShowing) {
                val d = dialog.window!!.decorView
                canvas.drawColor(android.graphics.Color.argb(150, 0, 0, 0))
                canvas.save()
                canvas.translate((v.width - d.width) / 2f, (v.height - d.height) / 2f)
                d.draw(canvas)
                canvas.restore()
            }
        }
        val dir = File("build/screenshots").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

}

/** Tap the boat on the Fish tab, design a boat (buying a part with points), then the shop. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w400dp-h860dp-xxhdpi")
class BoatCustomisationUiTest : BoatUiTestBase() {
    @Test
    fun customiseABoatFromTheFishTab() {
        val stripe = BoatPart.of(HullPattern.STRIPE)
        val stripeCost = (BoatParts.requirement(stripe) as PartRequirement.Points).cost
        val vm = launch(PlayerProgress(pointsBalance = 5_000, pointsEarned = 5_000))

        // The boat at the top of the Fish tab opens the Dex's Skins section.
        compose.clickDescription("Customise your boat")
        assertEquals(Screen.DEX, vm.screen)
        compose.onNodeWithText("Your boat").assertExists()
        compose.shot("54_skins_section")
        val locked = hasText("Reach the ${DepthZone.ABYSS.displayName}", substring = true)
        compose.onAllNodes(hasScrollAction()).onFirst().performScrollToNode(locked)
        assertTrue(isShowing("Reach the ${DepthZone.ABYSS.displayName}", substring = true))
        compose.shot("54b_skins_atmosphere_locked")
        compose.onAllNodes(hasScrollAction()).onFirst().performScrollToNode(hasText("Customise boat"))

        compose.click("Customise boat")
        assertEquals(Screen.BOAT_EDITOR, vm.screen)
        // A free colour applies straight away.
        compose.clickDescription("Hull colour: Ocean")
        compose.shot("55_boat_editor")

        // A points option asks first, then spends the points.
        compose.click("Racing stripe")
        compose.onNodeWithText("Unlock Racing stripe?").assertExists()
        compose.shot("56_boat_editor_unlock")
        compose.click("Unlock for $stripeCost pts")
        assertEquals(5_000L - stripeCost, vm.progress.pointsBalance)
        assertEquals(5_000L, vm.progress.pointsEarned)
        assertTrue(stripe.key in vm.progress.unlockedBoatParts)

        // A progress option explains how to unlock it.
        compose.click("Checkers")
        compose.onNodeWithText("Checkers is locked").assertExists()
        assertTrue(isShowing("species to unlock it", substring = true))
        compose.click("OK")

        compose.click("Save and wear")
        assertEquals(Screen.DEX, vm.screen)
        assertTrue(vm.progress.customBoatEquipped)
        assertEquals(BoatPalette.indexOf("Ocean"), vm.progress.boatDesign.hull)
        assertEquals(HullPattern.STRIPE, vm.progress.boatDesign.pattern)
        assertEquals(vm.progress.boatDesign, vm.skins.customBoat)

        // The shop: premade boats with prices, nothing can be bought yet.
        compose.click("Premium skins")
        assertEquals(Screen.SHOP, vm.screen)
        val buttons = compose.onAllNodesWithText("Coming soon")
        assertTrue(buttons.fetchSemanticsNodes().isNotEmpty())
        buttons.assertAll(isNotEnabled())
        compose.shot("57_shop")
        vm.onBack()
        assertEquals(Screen.DEX, vm.screen)

        // Wearing the classic boat again.
        compose.click("Worn ✓ · use the classic boat")
        assertFalse(vm.progress.customBoatEquipped)
        assertEquals(null, vm.skins.customBoat)
    }

}

/** A separate class: saved settings can leak between tests of one class under Robolectric. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w400dp-h860dp-xxhdpi")
class AtmosphereUiTest : BoatUiTestBase() {
    @Test
    fun theNightmareAtmosphereUnlocksAtTheAbyssalFloor() {
        val deepCast = CastResult(emptyList(), DepthZone.ABYSS.minDepth + 5f, 0, ReverseReason.ROPE_END, lanternUsed = false)
        val vm = launch(Progression.applyCast(PlayerProgress(), deepCast))
        assertTrue(vm.progress.atmosphereUnlocked)
        compose.clickDescription("Customise your boat")
        compose.onAllNodes(hasScrollAction()).onFirst().performScrollToNode(hasText(Atmosphere.NIGHTMARE.displayName))
        compose.click(Atmosphere.NIGHTMARE.displayName)
        assertEquals(Atmosphere.NIGHTMARE, vm.progress.atmosphere)
        assertEquals(Atmosphere.NIGHTMARE, vm.skins.atmosphere)
        compose.shot("58_atmosphere_nightmare")
        compose.click(Atmosphere.FAMILY.displayName)
        assertEquals(Atmosphere.FAMILY, vm.progress.atmosphere)
    }
}
