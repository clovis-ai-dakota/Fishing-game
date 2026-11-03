package com.deepline.fishing

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performScrollToNode
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.deepline.fishing.core.CastSession
import com.deepline.fishing.core.GameConfig
import com.deepline.fishing.core.Obstacle
import com.deepline.fishing.core.ObstacleKind
import com.deepline.fishing.core.Secrets
import com.deepline.fishing.core.World
import com.deepline.fishing.ui.tour.Tour
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * The legendary David and Goliath secret: its rewards and the skins it unlocks. In its own class
 * (so its own JVM) because saved progress can leak between tests in one class.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w400dp-h860dp-xxhdpi")
class LegendarySecretUiTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    private var scenario: ActivityScenario<MainActivity>? = null

    /** Saved settings can outlive a test in the same class: every test starts from a clean slate. */
    @org.junit.Before
    fun clearSavedState() {
        ApplicationProvider.getApplicationContext<android.content.Context>()
            .getSharedPreferences("deepline", android.content.Context.MODE_PRIVATE)
            .edit().clear().commit()
    }

    @After
    fun tearDown() {
        scenario?.close()
    }

    private fun ComposeTestRule.click(text: String) {
        val matcher = hasText(text)
        if (onAllNodes(matcher).fetchSemanticsNodes().isEmpty()) {
            onAllNodes(hasScrollAction()).onFirst().performScrollToNode(matcher)
        }
        val node = onNode(matcher)
        runCatching { node.performScrollTo() }
        node.performClick()
    }

    private fun ComposeTestRule.advanceSeconds(seconds: Float) {
        mainClock.autoAdvance = false
        repeat((seconds * 60).toInt()) { mainClock.advanceTimeBy(16) }
        mainClock.autoAdvance = true
    }

    private fun ComposeTestRule.shot(name: String) {
        // While a cast is running the clock is advanced by hand (waiting for idle would play it out).
        if (mainClock.autoAdvance) waitForIdle() else mainClock.advanceTimeByFrame()
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

    private fun isShowing(text: String, substring: Boolean = false) =
        compose.onAllNodes(hasText(text, substring = substring)).fetchSemanticsNodes().isNotEmpty()

    /** Three rocks, then the Pale Leviathan's head: on a 6 kg rope the 4th bump makes David and Goliath. */
    private fun goliathWater(specs: com.deepline.fishing.core.RopeSpecs, lantern: Boolean): CastSession {
        val c = GameConfig.WORLD_WIDTH / 2f
        val leviathan = com.deepline.fishing.core.FishCatalog.byId("pale_leviathan")!!
        val head = leviathan.bodyLengthM / 2f * 0.8f
        // Spaced out so the bumps take longer than "Pinball" allows: only the legendary secret here.
        val fish = com.deepline.fishing.core.FishEntity(0, leviathan, false, c - head, 80f, 1, 0f)
        val rocks = listOf(35f, 50f, 65f).mapIndexed { i, d -> Obstacle(i, ObstacleKind.ROCK, c, d, 1f) }
        return CastSession(specs, lantern, World(listOf(fish), rocks))
    }

    @Test
    fun legendarySecretRewardsAndSkins() {
        val store = SharedPrefsStore(ApplicationProvider.getApplicationContext<Context>())
        store.putString("tour.showAgain", "false")
        store.putString("tour.seenVersion", Tour.VERSION.toString())
        scenario = ActivityScenario.launch(MainActivity::class.java)
        lateinit var vm: GameViewModel
        scenario!!.onActivity { vm = ViewModelProvider(it)[GameViewModel::class.java] }
        vm.castFactory = ::goliathWater
        assertTrue(!isShowing("Skins"))
        val pointsBefore = vm.progress.pointsBalance
        val lanternsBefore = vm.progress.lanterns

        compose.click("Cast line")
        compose.advanceSeconds(6f)
        // The last bump hooked the leviathan: that secret first, mid-cast.
        compose.onNodeWithText("The last bump hooks what it hits").assertExists()
        compose.click("No thanks")
        compose.advanceSeconds(12f)
        // With the results: the legendary secret.
        compose.onNodeWithText("Fishing secret discovered!").assertExists()
        assertTrue(isShowing("Skins: ", substring = true))
        compose.shot("30_goliath_discovered")
        compose.click("Yes, show again")
        assertTrue(isShowing("✦ Secret: David and Goliath"))
        compose.shot("31_goliath_results")
        compose.click("Collect")

        val reward = com.deepline.fishing.core.SecretRewards.of(Secrets.DAVID_AND_GOLIATH)
        assertTrue(vm.progress.pointsBalance >= pointsBefore + reward.points)
        assertEquals(lanternsBefore + reward.lanterns, vm.progress.lanterns)
        assertTrue(vm.progress.unlockedCosmetics.isNotEmpty())

        // Skins: wear the whole set (hook, line, boat, trail and title), then fish with them.
        compose.click("Skins")
        val set = com.deepline.fishing.core.Cosmetic.entries.filter { it.unlockedBy == Secrets.DAVID_AND_GOLIATH }
        assertEquals(com.deepline.fishing.core.CosmeticSlot.entries.size, set.size)
        for (c in set) compose.click(c.displayName)
        compose.onAllNodes(hasScrollAction()).onFirst().performScrollToIndex(0)
        compose.shot("32_skins")
        assertEquals(set.size, vm.progress.equippedSkins.size)
        assertEquals("Giant-slayer", vm.title)
        compose.click("‹ Back")
        // The title is shown off on the home screen.
        compose.onNodeWithText("★ Giant-slayer").assertExists()

        compose.click("Cast line")
        compose.mainClock.autoAdvance = false
        repeat(72) { compose.mainClock.advanceTimeBy(16) }
        compose.shot("33_cast_with_skins")
    }
}
