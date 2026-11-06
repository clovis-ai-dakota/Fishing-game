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
        // explained from the Dex, which opens it. The boat editor is explained from the Fish tab's
        // boat, which leads to it.
        // The aquarium is explained from the Shop's Aquarium page (and opened from the Dex).
        val explainedElsewhere = setOf(Screen.CAST, Screen.BATTLE, Screen.ONLINE_LOBBY, Screen.FISH_DETAIL, Screen.BOAT_EDITOR, Screen.AQUARIUM)
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
        // Version 7: the fast reel and the Lantern's reach.
        assertTrue("reel in fast" in text.lowercase())
        assertTrue("${GameConfig.VISIBLE_RADIUS_LANTERN.toInt()} m" in text)
        // Version 4: downgrades, the species count, titles and every drawn skin slot.
        assertTrue("downgrade" in text.lowercase())
        assertTrue("${com.deepline.fishing.core.FishCatalog.all.size} species" in text)
        assertTrue("title" in text.lowercase())
        // Version 6: boat customisation and the atmosphere.
        for (topic in listOf("Customise", "Atmosphere", com.deepline.fishing.core.Atmosphere.NIGHTMARE.displayName)) {
            assertTrue("Tour doesn't mention $topic", text.contains(topic, ignoreCase = true))
        }
        val boatStep = Tour.steps.first { it.id == "boat" }
        assertTrue("The boat step points at the Fish tab's boat", boatStep.screen == Screen.HOME && boatStep.target == com.deepline.fishing.ui.tour.TourTarget.HOME_BOAT)
        for (slot in com.deepline.fishing.core.CosmeticSlot.entries) {
            assertTrue("Tour doesn't mention the ${slot.displayName} skin slot", slot.displayName.lowercase() in text.lowercase())
        }
        // Version 5: every tab of the bottom bar, the Dex's seen/caught states, filters and pages.
        for (tab in com.deepline.fishing.MainTab.entries) assertTrue("Tour doesn't mention the ${tab.label} tab", tab.label in text)
        for (topic in listOf("Seen", "Catch it", "Filter", "swipe", "trophy", "Secrets section", "Settings")) {
            assertTrue("Tour doesn't mention $topic", text.contains(topic, ignoreCase = true))
        }
    }

    @Test
    fun everyTabIsPointedAt() {
        // The bar itself, and every tab the tour sends the player to, is highlighted at least once.
        val targets = Tour.steps.mapNotNull { it.target }.toSet()
        assertTrue(TourTarget.NAV_BAR in targets)
        for (t in listOf(TourTarget.HOME_CAST, TourTarget.HOME_POINTS, TourTarget.HOME_SETTINGS, TourTarget.SETTINGS_TOUR, TourTarget.DEX_HEADER, TourTarget.DEX_FILTERS)) {
            assertTrue("No step points at $t", t in targets)
        }
        // Changed in version 5, so returning players saw them in "What's new".
        val v5 = Tour.steps.filter { it.sinceVersion == 5 }.map { it.id }
        assertTrue(v5.containsAll(listOf("cast", "dex", "dex_filters", "milestones", "secrets")))
        // Version 11: Settings (the gear), where the tour can be replayed.
        val v11 = Tour.steps.filter { it.sinceVersion == 11 }.map { it.id }
        assertTrue(v11.containsAll(listOf("settings", "replay")))
        // Version 6: customising the boat and the atmosphere.
        val v6 = Tour.steps.filter { it.sinceVersion == 6 }.map { it.id }
        assertTrue(v6.containsAll(listOf("boat", "atmosphere")))
        // Version 7: the fast reel, the deeper bump-free zone and the brighter Lantern.
        val v7 = Tour.steps.filter { it.sinceVersion == 7 }.map { it.id }
        assertTrue(v7.containsAll(listOf("descent", "lantern")))
        // Version 8: the fast reel right after the last bump.
        assertTrue(Tour.VERSION >= 8)
        assertTrue(Tour.steps.single { it.id == "fast_reel" }.sinceVersion == 8)
        // ...and the new ways to spend points.
        for (item in com.deepline.fishing.core.Tackle.entries) assertTrue("Tour doesn't mention ${item.displayName}", item.displayName in text)
        assertTrue("wager" in text.lowercase())
        assertTrue(Tour.steps.filter { it.sinceVersion == 8 }.map { it.id }.containsAll(listOf("wager")))
        for (t in listOf(TourTarget.HOME_BOAT, TourTarget.SHOP_STYLE, TourTarget.SKINS_ATMOSPHERE)) {
            assertTrue("No step points at $t", t in targets)
        }
    }

    @Test
    fun theShopIsExplained() {
        // Version 9: the Shop tab replaced Upgrades, with two prices for everything, passes, the
        // detector and sight tackle in battles. Returning players see these as "What's new".
        val v9 = Tour.steps.filter { it.sinceVersion == 9 }.map { it.id }
        assertTrue(v9.containsAll(listOf("tabs", "upgrades", "tackle", "two_prices", "shop_style", "aquarium", "season", "passes", "badges", "gifts", "battle_tackle")))
        val targets = Tour.steps.mapNotNull { it.target }.toSet()
        for (t in listOf(TourTarget.SHOP_ROPE, TourTarget.SHOP_TACKLE, TourTarget.SHOP_PRICES, TourTarget.SHOP_STYLE, TourTarget.SHOP_SEASON, TourTarget.SHOP_PASSES, TourTarget.BATTLE_TACKLE)) {
            assertTrue("No step points at $t", t in targets)
        }
        // Every Shop page with something on sale is shown by a step.
        val shown = Tour.steps.filter { it.screen == Screen.SHOP }.map { it.shopPage ?: ShopPage.ROPE }.toSet()
        for (page in ShopPage.entries) {
            val hasItems = page.section == null || com.deepline.fishing.core.ShopCatalog.inSection(page.section).isNotEmpty()
            if (hasItems) assertTrue("The tour never shows the Shop's ${page.label} page", page in shown)
        }
        for (topic in listOf("euros", "points", "Captain's Club", "Starter pack", "battles", "${GameConfig.MINUTES_PER_EURO.toInt()} minutes", "aquarium", "gift", "recovery code", "Dex cards", "season pass", "weekend")) {
            assertTrue("Tour doesn't mention $topic", text.contains(topic, ignoreCase = true))
        }
        // Battles: which tackle is allowed.
        for (t in com.deepline.fishing.core.Tackle.entries.filter { it.usableInBattles }) {
            assertTrue(Tour.steps.single { it.id == "battle_tackle" }.body.contains(t.displayName))
        }
    }

    @Test
    fun themesAndNightModeAreExplained() {
        // Version 10: menu themes and the night mode.
        val v10 = Tour.steps.filter { it.sinceVersion == 10 }.map { it.id }
        assertTrue(v10.containsAll(listOf("themes", "night_mode")))
        val targets = Tour.steps.mapNotNull { it.target }.toSet()
        assertTrue(TourTarget.HOME_THEMES in targets && TourTarget.THEMES_MODE in targets)
        for (mode in com.deepline.fishing.core.NightMode.entries) {
            assertTrue("Tour doesn't mention ${mode.displayName}", text.contains(mode.displayName.removePrefix("Like my "), ignoreCase = true))
        }
        for (topic in listOf("theme", "Shop", "euros or points")) assertTrue("Tour doesn't mention $topic", text.contains(topic, ignoreCase = true))
    }

    @Test
    fun theTourNeverGivesAwayASecret() {
        // Secrets are discovered by playing and explained then (see FishingSecrets), not in the tour.
        val secretNames = com.deepline.fishing.ui.secrets.FishingSecrets.all.map { it.title }
        // Cosmetics are named after the secret that unlocks them, and the legendary fish is a surprise.
        val rewards = com.deepline.fishing.core.Cosmetic.entries.filterNot { it.fromShop }.flatMap { listOf(it.displayName, it.setName) }
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
