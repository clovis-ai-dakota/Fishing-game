package com.deepline.fishing

import com.deepline.fishing.core.BattleMode
import com.deepline.fishing.core.GameConfig
import com.deepline.fishing.core.RopeStat
import com.deepline.fishing.ui.tour.Tour
import com.deepline.fishing.ui.tour.TourTarget
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
        // explained from the screens that lead to them. A species' page (FISH_DETAIL) is
        // explained from the Dex, which opens it.
        val explainedElsewhere = setOf(Screen.CAST, Screen.BATTLE, Screen.ONLINE_LOBBY, Screen.FISH_DETAIL)
        val visited = Tour.steps.map { it.screen }.toSet()
        for (screen in Screen.entries - explainedElsewhere) {
            assertTrue("The tour never shows the $screen screen", screen in visited)
        }
    }

    @Test
    fun everyBattleModeRopeUpgradeAndCoreRuleIsExplained() {
        for (mode in BattleMode.entries) assertTrue("Tour doesn't mention ${mode.title}", mode.title in text)
        for (stat in RopeStat.entries) assertTrue("Tour doesn't mention ${stat.displayName}", stat.displayName in text)
        for (topic in listOf("Lantern", "Sparkling", "head", "Load", "Dex", "Milestone", "online", "Quick match", "Fishing secrets", "Skins")) {
            assertTrue("Tour doesn't mention $topic", text.contains(topic, ignoreCase = true))
        }
        assertTrue("${GameConfig.BUMP_FREE_DEPTH.toInt()} m" in text)
        assertTrue("${GameConfig.DESCENT_TOUCH_LIMIT}" in text)
        // Version 4: downgrades, the species count, titles and every drawn skin slot.
        assertTrue("downgrade" in text.lowercase())
        assertTrue("${com.deepline.fishing.core.FishCatalog.all.size} species" in text)
        assertTrue("title" in text.lowercase())
        for (slot in com.deepline.fishing.core.CosmeticSlot.entries) {
            assertTrue("Tour doesn't mention the ${slot.displayName} skin slot", slot.displayName.lowercase() in text.lowercase())
        }
        // Version 5: every tab of the bottom bar, the Dex's seen/caught states, filters and pages.
        for (tab in com.deepline.fishing.MainTab.entries) assertTrue("Tour doesn't mention the ${tab.label} tab", tab.label in text)
        for (topic in listOf("Seen", "Catch it", "Filter", "swipe", "trophy", "Secrets section", "? button")) {
            assertTrue("Tour doesn't mention $topic", text.contains(topic, ignoreCase = true))
        }
    }

    @Test
    fun everyTabIsPointedAt() {
        // The bar itself, and every tab the tour sends the player to, is highlighted at least once.
        val targets = Tour.steps.mapNotNull { it.target }.toSet()
        assertTrue(TourTarget.NAV_BAR in targets)
        for (t in listOf(TourTarget.HOME_CAST, TourTarget.HOME_POINTS, TourTarget.HOME_TOUR, TourTarget.DEX_HEADER, TourTarget.DEX_FILTERS)) {
            assertTrue("No step points at $t", t in targets)
        }
        // Changed in version 5, so returning players see them in "What's new".
        val whatsNew = Tour.steps.filter { it.sinceVersion == Tour.VERSION }.map { it.id }
        assertTrue(whatsNew.containsAll(listOf("tabs", "cast", "upgrades", "dex", "dex_filters", "milestones", "secrets", "replay")))
    }

    @Test
    fun theTourNeverGivesAwayASecret() {
        // Secrets are discovered by playing and explained then (see FishingSecrets), not in the tour.
        val secretNames = com.deepline.fishing.ui.secrets.FishingSecrets.all.map { it.title }
        // Cosmetics are named after the secret that unlocks them, and the legendary fish is a surprise.
        val rewards = com.deepline.fishing.core.Cosmetic.entries.flatMap { listOf(it.displayName, it.setName) }
        val legendary = listOf(com.deepline.fishing.core.FishCatalog.sovereign.name, "sovereign", "only be caught")
        for (giveaway in listOf("last bump", "snag", "hooks what", "hooks whatever", "leviathan") + secretNames + rewards + legendary) {
            assertTrue("The tour reveals a secret: '$giveaway'", !text.contains(giveaway, ignoreCase = true))
        }
    }

    @Test
    fun everySecretHasAnExplanation() {
        val ids = com.deepline.fishing.core.Secrets.all
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
