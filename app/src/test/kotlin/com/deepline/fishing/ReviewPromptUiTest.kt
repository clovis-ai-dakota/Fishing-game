package com.deepline.fishing

import android.app.Activity
import android.content.Context
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.deepline.fishing.analytics.RecordingTelemetry
import com.deepline.fishing.analytics.Telemetry
import com.deepline.fishing.billing.TestStore
import com.deepline.fishing.core.CastSession
import com.deepline.fishing.core.FishCatalog
import com.deepline.fishing.core.FishEntity
import com.deepline.fishing.core.GameConfig
import com.deepline.fishing.core.LocalDays
import com.deepline.fishing.core.Obstacle
import com.deepline.fishing.core.ObstacleKind
import com.deepline.fishing.core.PlayerProgress
import com.deepline.fishing.core.ProgressRepository
import com.deepline.fishing.core.RopeSpecs
import com.deepline.fishing.core.ShopCatalog
import com.deepline.fishing.core.ShopClock
import com.deepline.fishing.core.World
import com.deepline.fishing.privacy.ConsentStore
import com.deepline.fishing.privacy.PrivacyChoices
import com.deepline.fishing.review.ReviewPrompter
import com.deepline.fishing.review.ReviewPrompters
import com.deepline.fishing.ui.tour.Tour
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The Play review card is asked for right after a happy moment, once the screen is calm: never
 * during a cast, over the results or over a secret's explanation, never right after a setback,
 * and only once in a long while.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w400dp-h860dp-xxhdpi")
class ReviewPromptUiTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    private var scenario: ActivityScenario<MainActivity>? = null

    /** Stands in for Google Play: records each time the game asks for the card. */
    private class RecordingPrompter : ReviewPrompter {
        val launches = mutableListOf<String>()
        override suspend fun launch(activity: Activity) {
            launches += activity.javaClass.simpleName
        }
    }

    private val prompter = RecordingPrompter()
    private val recorder = RecordingTelemetry()

    @Before
    fun setUp() {
        ApplicationProvider.getApplicationContext<Context>()
            .getSharedPreferences("deepline", Context.MODE_PRIVATE).edit().clear().commit()
        ReviewPrompters.factory = { prompter }
        Telemetry.install(recorder)
    }

    @After
    fun tearDown() {
        scenario?.close()
        ReviewPrompters.factory = null
        ShopClock.override = null
        Telemetry.uninstall()
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

    private fun isShowing(text: String) = compose.onAllNodes(hasText(text)).fetchSemanticsNodes().isNotEmpty()

    /** Three rocks, then a Sparkling fish's head: the 4th bump hooks it (a rare catch). */
    @Suppress("UNUSED_PARAMETER")
    private fun sparklingWater(specs: RopeSpecs, lantern: Boolean): CastSession {
        val c = GameConfig.WORLD_WIDTH / 2f
        val species = FishCatalog.all.first { !it.lastBumpOnly && it.minDepth <= 100f && it.maxDepth >= 100f }
        val fish = FishEntity(0, species, true, c - species.bodyLengthM / 2f * 0.8f, 100f, 1, 0f)
        val rocks = listOf(60f, 72f, 84f).mapIndexed { i, d -> Obstacle(i, ObstacleKind.ROCK, c, d, 1f) }
        return CastSession(RopeSpecs(200f, 30f, 9f), false, World(listOf(fish), rocks))
    }

    /** A returning player: tour taken, statistics allowed, enough casts and days to be asked. */
    private fun launchSeasonedPlayer(): GameViewModel {
        val store = SharedPrefsStore(ApplicationProvider.getApplicationContext<Context>())
        store.putString("tour.showAgain", "false")
        store.putString("tour.seenVersion", Tour.VERSION.toString())
        ConsentStore(store).save(PrivacyChoices(analytics = true, crashReports = false))
        ProgressRepository(store).save(PlayerProgress(castsMade = GameConfig.Review.MIN_CASTS + 5))
        store.putString("review.playDays", GameConfig.Review.MIN_PLAY_DAYS.toString())
        scenario = ActivityScenario.launch(MainActivity::class.java)
        compose.waitForIdle()
        lateinit var vm: GameViewModel
        scenario!!.onActivity { vm = ViewModelProvider(it)[GameViewModel::class.java] }
        vm.castFactory = ::sparklingWater
        return vm
    }

    /** Answers every "show this again?" secret explanation that is open. */
    private fun answerSecretExplanations() {
        while (isShowing("No thanks")) {
            compose.click("No thanks")
            compose.advanceSeconds(0.2f)
        }
    }

    /** One cast in [sparklingWater] (about 19 s), explanations answered, results left showing. */
    private fun castToResults() {
        compose.click("Cast line")
        compose.advanceSeconds(7f)
        answerSecretExplanations()
        compose.advanceSeconds(12f)
        assertTrue(isShowing("Cast complete"))
    }

    private fun collect() {
        compose.click("Collect")
        compose.waitForIdle()
    }

    // One test only: from a class's second test on, Robolectric hands the view model the first
    // test's preferences (see CLAUDE.md), so the scenarios run in order here.
    @Test
    fun theCardComesAfterAHappyCalmMomentAndRarely() {
        val start = System.currentTimeMillis()
        ShopClock.override = start
        val vm = launchSeasonedPlayer()

        // 1. A rare catch (the 4th bump hooks a Sparkling fish). Its secret is explained mid-cast:
        //    no card during the cast, nor while the results are showing.
        compose.click("Cast line")
        compose.advanceSeconds(7f)
        assertTrue(isShowing("The last bump hooks what it hits"))
        assertNull(vm.reviewToAsk)
        answerSecretExplanations()
        compose.advanceSeconds(12f)
        assertTrue(isShowing("Cast complete"))
        answerSecretExplanations()
        compose.waitForIdle()
        assertTrue(prompter.launches.isEmpty())
        collect()
        // The happy moment was noted when the results were collected...
        if (vm.secretPopup != null) {
            // ...and waits for any explanation still open over the Fish tab.
            assertNotNull(vm.review.pending)
            assertTrue(prompter.launches.isEmpty())
            answerSecretExplanations()
            compose.waitForIdle()
        }
        // Calm at last: Play's card is asked for, exactly once.
        assertEquals(listOf("MainActivity"), prompter.launches)
        assertEquals(1, vm.review.state.asks)
        assertNull(vm.review.pending)
        val event = recorder.last("review_prompt")!!
        assertEquals("rare_catch", event.params["moment"])
        assertEquals(1, event.params["ask"])
        assertEquals(emptyList<String>(), recorder.problems())

        // 2. Another rare catch right away: asked recently, nothing.
        castToResults()
        collect()
        assertEquals(1, prompter.launches.size)

        // 3. Months later, a payment fails just before a rare catch: a setback, nothing.
        val later = start + (GameConfig.Review.COOLDOWN_DAYS + 1) * LocalDays.DAY_MS
        ShopClock.override = later
        val item = ShopCatalog.all.first { vm.canBuy(it) }
        vm.buyWithMoney(item, null)
        vm.testStore!!.answer(TestStore.TestOutcome.FAIL)
        compose.waitForIdle()
        // The failure's message covers the Cast button until it's dismissed.
        compose.click("OK")
        compose.waitForIdle()
        castToResults()
        collect()
        assertEquals(1, prompter.launches.size)
        assertEquals(1, vm.review.state.asks)

        // 4. An hour after the setback, a rare catch may ask again.
        ShopClock.override = later + GameConfig.Review.SETBACK_QUIET_MINUTES * 60_000L
        castToResults()
        collect()
        assertEquals(2, prompter.launches.size)
        assertEquals(2, recorder.events.count { it.name == "review_prompt" })
    }
}
