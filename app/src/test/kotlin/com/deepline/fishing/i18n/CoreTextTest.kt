package com.deepline.fishing.i18n

import com.deepline.fishing.core.Atmosphere
import com.deepline.fishing.core.BattleMode
import com.deepline.fishing.core.BoatPalette
import com.deepline.fishing.core.BotDifficulty
import com.deepline.fishing.core.CardStyle
import com.deepline.fishing.core.ChallengeDifficulty
import com.deepline.fishing.core.ClothPattern
import com.deepline.fishing.core.Cosmetic
import com.deepline.fishing.core.CosmeticSlot
import com.deepline.fishing.core.Decor
import com.deepline.fishing.core.DepthZone
import com.deepline.fishing.core.Emblem
import com.deepline.fishing.core.EventGoal
import com.deepline.fishing.core.FishCatalog
import com.deepline.fishing.core.FlagStyle
import com.deepline.fishing.core.GameConfig
import com.deepline.fishing.core.HullPattern
import com.deepline.fishing.core.MenuTheme
import com.deepline.fishing.core.NightMode
import com.deepline.fishing.core.ObstacleKind
import com.deepline.fishing.core.PremiumSkins
import com.deepline.fishing.core.RopeStat
import com.deepline.fishing.core.SailStyle
import com.deepline.fishing.core.Seasons
import com.deepline.fishing.core.Secrets
import com.deepline.fishing.core.ShopCatalog
import com.deepline.fishing.core.ShopSection
import com.deepline.fishing.core.Tackle
import com.deepline.fishing.core.TitleFrame
import com.deepline.fishing.core.social.BoardKind
import com.deepline.fishing.core.social.BoardView
import com.deepline.fishing.core.social.ClubRole
import com.deepline.fishing.core.social.TournamentTier
import com.deepline.fishing.text
import com.deepline.fishing.ui.i18n.UiText
import com.deepline.fishing.ui.i18n.badgeText
import com.deepline.fishing.ui.i18n.descriptionText
import com.deepline.fishing.ui.i18n.labelText
import com.deepline.fishing.ui.i18n.nameText
import com.deepline.fishing.ui.i18n.rulesText
import com.deepline.fishing.ui.i18n.secretTitleText
import com.deepline.fishing.ui.i18n.skinSetText
import com.deepline.fishing.ui.i18n.swatchText
import com.deepline.fishing.ui.i18n.titleText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Every name and description core defines has a string resource (strings_core.xml,
 * strings_species.xml), and the English one says exactly what core says. Core's English stays the
 * reference (the server and the tests use it): when it changes, update the English resource.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class CoreTextTest {
    private fun same(expected: String, actual: UiText, what: String) {
        assertTrue("$what has no string resource", actual !is UiText.Raw)
        assertEquals(what, expected, actual.text)
    }

    @Test
    fun everyEnumNameMatchesCore() {
        DepthZone.entries.forEach { same(it.displayName, it.labelText(), "zone $it") }
        ObstacleKind.entries.forEach { same(it.displayName, it.labelText(), "obstacle $it") }
        RopeStat.entries.forEach {
            same(it.displayName, it.labelText(), "rope $it")
            same(it.description, it.descriptionText(), "rope $it description")
        }
        Tackle.entries.forEach {
            same(it.displayName, it.labelText(), "tackle $it")
            same(it.description, it.descriptionText(), "tackle $it description")
        }
        BattleMode.entries.forEach {
            same(it.title, it.labelText(), "mode $it")
            same(it.summary, it.rulesText(), "mode $it rules")
        }
        BotDifficulty.entries.forEach { same(it.displayName, it.labelText(), "bot $it") }
        ChallengeDifficulty.entries.forEach { same(it.label, it.labelText(), "challenge $it") }
        CosmeticSlot.entries.forEach { same(it.displayName, it.labelText(), "slot $it") }
        Cosmetic.entries.forEach {
            same(it.displayName, it.labelText(), "cosmetic $it")
            same(it.description, it.descriptionText(), "cosmetic $it description")
            if (it.setName.isNotEmpty()) same(it.setName, skinSetText(it.setName), "set ${it.setName}")
            if (it.slot == CosmeticSlot.TITLE) same(it.displayName, titleText(it.displayName), "title $it")
        }
        ShopSection.entries.forEach { same(it.displayName, it.labelText(), "shop section $it") }
        HullPattern.entries.forEach { same(it.displayName, it.labelText(), "pattern $it") }
        ClothPattern.entries.forEach { same(it.displayName, it.labelText(), "cloth $it") }
        SailStyle.entries.forEach { same(it.displayName, it.labelText(), "sail $it") }
        FlagStyle.entries.forEach { same(it.displayName, it.labelText(), "flag $it") }
        Emblem.entries.forEach { same(it.displayName, it.labelText(), "emblem $it") }
        Atmosphere.entries.forEach { same(it.displayName, it.labelText(), "atmosphere $it") }
        TitleFrame.entries.forEach { same(it.displayName, it.labelText(), "frame $it") }
        CardStyle.entries.forEach { same(it.displayName, it.labelText(), "card style $it") }
        Decor.entries.forEach {
            same(it.displayName, it.labelText(), "decor $it")
            same(it.description, it.descriptionText(), "decor $it description")
        }
        MenuTheme.entries.forEach {
            same(it.displayName, it.labelText(), "theme $it")
            same(it.description, it.descriptionText(), "theme $it description")
        }
        NightMode.entries.forEach { same(it.displayName, it.labelText(), "night mode $it") }
        EventGoal.entries.forEach { same(it.displayName, it.labelText(), "event goal $it") }
        TournamentTier.entries.forEach { same(it.displayName, it.labelText(), "tournament tier $it") }
        ClubRole.entries.forEach { same(it.displayName, it.labelText(), "club role $it") }
        BoardKind.entries.forEach {
            same(it.displayName, it.labelText(), "board $it")
            same(it.description, it.descriptionText(), "board $it description")
        }
        BoardView.entries.forEach { same(it.displayName, it.labelText(), "board view $it") }
        BoatPalette.swatches.forEach { same(it.name, swatchText(it.name), "colour ${it.name}") }
    }

    @Test
    fun everyShopItemBoatAndSeasonMatchesCore() {
        for (item in ShopCatalog.all) {
            same(item.name, item.nameText(), "shop item ${item.id}")
            same(item.description, item.descriptionText(), "shop item ${item.id} description")
            item.badge?.let { badge -> assertEquals("badge of ${item.id}", badge, item.badgeText()!!.text) }
        }
        for (boat in PremiumSkins.all) {
            same(boat.name, boat.nameText(), "boat ${boat.id}")
            same(boat.tagline, boat.descriptionText(), "boat ${boat.id} description")
        }
        val week = GameConfig.SEASON_WEEKS * 7L * 86_400_000L
        for (n in 0 until 12) {
            val season = Seasons.at(Seasons.EPOCH_MS + n * week + 1_000)
            same(season.name, season.nameText(), "season ${season.number}")
        }
    }

    @Test
    fun everySpeciesHasItsName() {
        // Ids are saved in players' logs and never change; the English names equal core's.
        for (species in FishCatalog.all) same(species.name, species.nameText(), "species ${species.id}")
        val names = FishCatalog.all.map { it.nameText().text }
        assertEquals("species names must stay unique", names.size, names.toSet().size)
    }

    @Test
    fun everySecretHasATitle() {
        for (id in Secrets.all) assertTrue("secret $id has no title", secretTitleText(id) !is UiText.Raw)
        // The explanations use the same titles.
        for (secret in com.deepline.fishing.ui.secrets.FishingSecrets.all) {
            assertEquals(secretTitleText(secret.id), secret.title)
        }
    }
}
