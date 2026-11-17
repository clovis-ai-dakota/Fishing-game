package com.deepline.fishing.village

import com.deepline.fishing.core.BoatDesign
import com.deepline.fishing.core.BoatPalette
import com.deepline.fishing.core.Cosmetic
import com.deepline.fishing.core.CosmeticSlot
import com.deepline.fishing.core.HullPattern
import com.deepline.fishing.core.PlayerProgress
import com.deepline.fishing.core.PremiumSkins
import com.deepline.fishing.core.Seasons
import com.deepline.fishing.ui.village.IslandDress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The island takes the colours of what the player wears, more so the harder it was to get. */
class IslandDressTest {
    private fun wearing(design: BoatDesign) = PlayerProgress(boatDesign = design, customBoatEquipped = true)

    @Test
    fun theHarderTheLookTheMoreTheIslandChanges() {
        assertEquals(IslandDress.CLASSIC, IslandDress.of(PlayerProgress()))
        // The default design, worn: nothing to show.
        assertEquals(0, IslandDress.of(wearing(BoatDesign.DEFAULT)).level)
        // A design of free options: the pennant only.
        val own = IslandDress.of(wearing(BoatDesign.DEFAULT.copy(flag = BoatPalette.indexOf("Lagoon"))))
        assertEquals(1, own.level)
        assertEquals(BoatPalette.color(BoatPalette.indexOf("Lagoon")), own.flag)
        assertNull(own.trim)
        assertNull(own.roof)
        // A shop paint: the awning too.
        assertEquals(2, IslandDress.of(wearing(BoatDesign.DEFAULT.copy(pattern = HullPattern.FLAMES))).level)
        // A €0.99 premade boat, then a season boat: the roofs and the bunting.
        assertEquals(2, IslandDress.of(wearing(PremiumSkins.byId("lagoon_explorer")!!.design)).level)
        val season = IslandDress.of(wearing(PremiumSkins.byId(Seasons.boats.first())!!.design))
        assertEquals(3, season.level)
        assertTrue(season.bunting)
        // Shop gear on the line: level 2 in its colours.
        assertEquals(2, IslandDress.of(PlayerProgress(equippedSkins = mapOf(CosmeticSlot.LINE to Cosmetic.NEON_LINE))).level)
        // A legendary piece beats everything, and brings its set's monument.
        val royal = IslandDress.of(
            wearing(PremiumSkins.byId(Seasons.boats.first())!!.design).copy(equippedSkins = mapOf(CosmeticSlot.HOOK to Cosmetic.CROWN_HOOK)),
        )
        assertEquals(4, royal.level)
        assertEquals("crown", royal.legend)
        // Every legendary set has a monument, and every shop piece colours the island.
        for (c in Cosmetic.entries) {
            val d = IslandDress.of(PlayerProgress(equippedSkins = mapOf(c.slot to c)))
            assertEquals(c.name, if (c.fromShop) 2 else 4, d.level)
        }
    }
}
