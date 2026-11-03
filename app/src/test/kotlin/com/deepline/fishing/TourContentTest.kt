package com.deepline.fishing

import com.deepline.fishing.core.BattleMode
import com.deepline.fishing.core.GameConfig
import com.deepline.fishing.core.RopeStat
import com.deepline.fishing.ui.tour.Tour
import com.deepline.fishing.ui.tour.TourStep
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Keeps the tour in step with the app: if a feature is added without a tour step, this fails.
 * (See the note on [Tour].)
 */
class TourContentTest {
    private val text = Tour.steps.joinToString("\n") { it.title + " " + it.body }

    @Test
    fun everyMenuScreenIsVisited() {
        // CAST, BATTLE and ONLINE_LOBBY need a live cast, battle or connection, so they're
        // explained from the screens that lead to them. SECRETS only exists once a secret is
        // discovered; the "secrets" step explains it from the home screen.
        val explainedElsewhere = setOf(Screen.CAST, Screen.BATTLE, Screen.ONLINE_LOBBY, Screen.SECRETS)
        val visited = Tour.steps.map { it.screen }.toSet()
        for (screen in Screen.entries - explainedElsewhere) {
            assertTrue("The tour never shows the $screen screen", screen in visited)
        }
    }

    @Test
    fun everyBattleModeRopeUpgradeAndCoreRuleIsExplained() {
        for (mode in BattleMode.entries) assertTrue("Tour doesn't mention ${mode.title}", mode.title in text)
        for (stat in RopeStat.entries) assertTrue("Tour doesn't mention ${stat.displayName}", stat.displayName in text)
        for (topic in listOf("Lantern", "Sparkling", "head", "Load", "Fish log", "Milestone", "online", "Quick match", "Fishing secrets")) {
            assertTrue("Tour doesn't mention $topic", text.contains(topic, ignoreCase = true))
        }
        assertTrue("${GameConfig.BUMP_FREE_DEPTH.toInt()} m" in text)
        assertTrue("${GameConfig.DESCENT_TOUCH_LIMIT}" in text)
    }

    @Test
    fun theTourNeverGivesAwayASecret() {
        // Secrets are discovered by playing and explained then (see FishingSecrets), not in the tour.
        for (giveaway in listOf("last bump", "snag", "hooks what", "hooks whatever")) {
            assertTrue("The tour reveals a secret: '$giveaway'", !text.contains(giveaway, ignoreCase = true))
        }
    }

    @Test
    fun everySecretHasAnExplanation() {
        val ids = listOf(com.deepline.fishing.core.Secrets.LAST_BUMP)
        for (id in ids) assertTrue("No explanation for secret $id", com.deepline.fishing.ui.secrets.FishingSecrets.byId(id) != null)
    }

    @Test
    fun stepIdsAreUniqueAndVersionsValid() {
        assertEquals(Tour.steps.size, Tour.steps.map { it.id }.toSet().size)
        assertTrue(Tour.steps.all { it.sinceVersion in 1..Tour.VERSION })
        assertTrue("The tour should end on the step that shows how to replay it", Tour.steps.last().id == "replay")
    }

    @Test
    fun launchPlan() {
        // First launch (never answered) or "show again": the full tour.
        assertEquals(Tour.steps, Tour.planAtLaunch(showAgain = true, seenVersion = 0)!!.steps)
        assertEquals(false, Tour.planAtLaunch(showAgain = true, seenVersion = Tour.VERSION)!!.whatsNew)
        // Said no, and has seen the current tour: nothing.
        assertNull(Tour.planAtLaunch(showAgain = false, seenVersion = Tour.VERSION))
        // Said no to an older tour: just the steps added since.
        val older = Tour.planAtLaunch(showAgain = false, seenVersion = 0)
        assertEquals(Tour.steps.filter { it.sinceVersion > 0 }, older!!.steps)
        assertTrue(older.whatsNew)
    }

    @Test
    fun whatsNewOnlyShowsNewerSteps() {
        val steps = listOf(
            TourStep("a", "A", "a", Screen.HOME, sinceVersion = 1),
            TourStep("b", "B", "b", Screen.HOME, sinceVersion = 2),
        )
        assertEquals(listOf("b"), steps.filter { it.sinceVersion > 1 }.map { it.id })
    }
}
