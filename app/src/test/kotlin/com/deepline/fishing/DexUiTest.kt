package com.deepline.fishing

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.deepline.fishing.core.CollectionEntry
import com.deepline.fishing.core.Cosmetic
import com.deepline.fishing.core.FishCatalog
import com.deepline.fishing.core.PlayerProgress
import com.deepline.fishing.core.ProgressRepository
import com.deepline.fishing.core.RopeStat
import com.deepline.fishing.core.Secrets
import com.deepline.fishing.ui.secrets.FishingSecrets
import com.deepline.fishing.ui.tour.Tour
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** The bottom tabs and the Dex: species cards, a species page, locked secrets and the skins library. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w400dp-h860dp-xxhdpi")
class DexUiTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    private var scenario: ActivityScenario<MainActivity>? = null

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

    private fun launch(progress: PlayerProgress): GameViewModel {
        val store = SharedPrefsStore(ApplicationProvider.getApplicationContext())
        ProgressRepository(store).save(progress)
        store.putString("tour.showAgain", "false")
        store.putString("tour.seenVersion", Tour.VERSION.toString())
        scenario = ActivityScenario.launch(MainActivity::class.java)
        lateinit var vm: GameViewModel
        scenario!!.onActivity { vm = ViewModelProvider(it)[GameViewModel::class.java] }
        return vm
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

    private fun ComposeTestRule.tab(name: String) = onNodeWithTag("tab_$name").performClick()

    private fun isShowing(text: String, substring: Boolean = false) =
        compose.onAllNodes(hasText(text, substring = substring)).fetchSemanticsNodes().isNotEmpty()

    private fun ComposeTestRule.shot(name: String) {
        waitForIdle()
        lateinit var bitmap: Bitmap
        scenario!!.onActivity { a ->
            val v = a.window.decorView
            bitmap = Bitmap.createBitmap(v.width, v.height, Bitmap.Config.ARGB_8888)
            v.draw(Canvas(bitmap))
        }
        val dir = File("build/screenshots").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    /** Some fish caught (a few Sparkling), more only seen, four secrets found including a legendary one. */
    private fun midGame(): PlayerProgress {
        val caught = FishCatalog.all.filterIndexed { i, _ -> i % 4 == 0 && i < 120 }
        val seen = FishCatalog.all.filterIndexed { i, _ -> i % 4 == 1 && i < 120 }.map { it.id }.toSet()
        return PlayerProgress(
            pointsBalance = 48_210,
            pointsEarned = 96_000,
            ropeLevels = mapOf(RopeStat.LENGTH to 6, RopeStat.STRENGTH to 5, RopeStat.AGILITY to 4),
            lanterns = 4,
            collection = caught.withIndex().associate { (k, s) ->
                s.id to CollectionEntry(2 + k % 5, if (k % 3 == 0) 1 else 0, s.minDepth + 12f, s.basePoints * 4)
            },
            seenSpecies = seen,
            castsMade = 87,
            discoveredSecrets = setOf(Secrets.LAST_BUMP, Secrets.SCHOOLING, Secrets.PINBALL, Secrets.DAVID_AND_GOLIATH),
            equippedSkins = mapOf(com.deepline.fishing.core.CosmeticSlot.HOOK to Cosmetic.LEVIATHAN_BONE_HOOK),
        )
    }

    @Test
    fun theFishTabIsTheHubAndBackReturnsToIt() {
        val vm = launch(midGame())
        compose.onNodeWithText("Cast line").assertExists()
        compose.onNodeWithContentDescription("Milestones").assertExists()
        compose.shot("49_fish_tab")
        compose.tab("shop")
        assertEquals(Screen.SHOP, vm.screen)
        // System back from any tab goes to the Fish tab.
        compose.waitForIdle()
        scenario!!.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
        assertEquals(Screen.HOME, vm.screen)
        // Milestones are pushed on top of the Fish tab, with a back button.
        compose.onNodeWithContentDescription("Milestones").performClick()
        assertEquals(Screen.MILESTONES, vm.screen)
        compose.click("‹ Back")
        assertEquals(Screen.HOME, vm.screen)
    }

    @Test
    fun speciesGridFiltersAndPages() {
        val vm = launch(midGame())
        val p = vm.progress
        compose.tab("dex")
        compose.onNodeWithText("Seen ${p.seenCount} · Caught ${p.discoveredCount} / ${FishCatalog.all.size}").assertExists()
        // Unknown species show ???, seen ones their name, caught ones everything.
        assertTrue(isShowing("???"))
        assertTrue(isShowing(FishCatalog.all[1].name)) // seen
        compose.shot("50_dex_grid")

        compose.click("Twilight Reef")
        compose.shot("50b_dex_filtered")
        compose.click("All")

        // Tap the first card (caught): its page, then flip to the next one (seen only).
        compose.click(FishCatalog.all[0].name)
        assertEquals(Screen.FISH_DETAIL, vm.screen)
        compose.onNodeWithText("Your records").assertExists()
        compose.shot("51_dex_detail")
        compose.onNodeWithContentDescription("Next fish").performClick()
        compose.waitForIdle()
        assertEquals(1, vm.dexFishIndex)
        compose.onNodeWithText("Seen, not caught yet").assertExists()
        compose.shot("51b_dex_detail_seen")

        // Back returns to the Dex.
        compose.waitForIdle()
        scenario!!.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
        assertEquals(Screen.DEX, vm.screen)

        // An unknown species: depths only.
        scenario!!.onActivity { vm.openFishDetail(FishCatalog.all.lastIndex) }
        compose.waitForIdle()
        compose.onNodeWithText("Not seen yet").assertExists()
        assertTrue(!isShowing(FishCatalog.sovereign.name))
        compose.shot("51c_dex_detail_unknown")
    }

    @Test
    fun lockedSecretsShowTierAndRewardOnly() {
        val vm = launch(midGame())
        compose.tab("dex")
        compose.click("Secrets")
        compose.onNodeWithText("Discovered 4 of ${Secrets.all.size}").assertExists()
        // Found: titles. Locked: never the title, only the tier and the reward.
        assertTrue(isShowing(FishingSecrets.byId(Secrets.SCHOOLING)!!.title))
        for (id in Secrets.all - vm.progress.discoveredSecrets) {
            assertTrue("locked secret $id shows its title", !isShowing(FishingSecrets.byId(id)!!.title))
        }
        compose.shot("52_dex_secrets")
        compose.onAllNodes(hasScrollAction()).onFirst().performScrollToNode(hasText("a legendary skin set", substring = true))
        assertTrue(isShowing("a legendary skin set", substring = true))
        assertTrue("set names hint at secrets", !isShowing("Prismatic", substring = true))
        compose.shot("52b_dex_secrets_locked")

        // A found secret opens its explanation.
        compose.onAllNodes(hasScrollAction()).onFirst().performScrollToNode(hasText(FishingSecrets.byId(Secrets.SCHOOLING)!!.title))
        compose.click(FishingSecrets.byId(Secrets.SCHOOLING)!!.title)
        compose.onNodeWithText("Close").assertExists()
        compose.click("Close")
    }

    @Test
    fun skinsLibraryEquipsAndHidesLockedNames() {
        val vm = launch(midGame())
        compose.tab("dex")
        compose.click("Skins")
        compose.onAllNodes(hasScrollAction()).onFirst().performScrollToNode(hasText("Giant-slayer set"))
        compose.onNodeWithText("Giant-slayer set").assertExists()
        compose.shot("53_dex_skins")
        // Locked sets show their slots only.
        compose.onAllNodes(hasScrollAction()).onFirst().performScrollToNode(hasText("Locked set"))
        assertTrue(isShowing("Locked set"))
        for (c in Cosmetic.entries.filter { it.unlockedBy != Secrets.DAVID_AND_GOLIATH }) {
            assertTrue("locked ${c.name} shows its name", !isShowing(c.displayName))
        }
        compose.shot("53b_dex_skins_locked")
        // Wear the braid, then take it off again.
        compose.click("Giant's braid")
        assertEquals(Cosmetic.GIANTS_BRAID_LINE, vm.progress.equippedSkins[com.deepline.fishing.core.CosmeticSlot.LINE])
        compose.click("Giant's braid")
        assertEquals(null, vm.progress.equippedSkins[com.deepline.fishing.core.CosmeticSlot.LINE])
    }
}
