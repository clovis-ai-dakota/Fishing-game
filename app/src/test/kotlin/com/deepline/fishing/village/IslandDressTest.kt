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
        // Only the boat counts: gear on the hook or the line doesn't dress the island.
        assertEquals(0, IslandDress.of(PlayerProgress(equippedSkins = mapOf(CosmeticSlot.LINE to Cosmetic.NEON_LINE))).level)
        assertEquals(0, IslandDress.of(PlayerProgress(equippedSkins = mapOf(CosmeticSlot.HOOK to Cosmetic.CROWN_HOOK))).level)
        // A legendary boat brings its set's monument, and takes over a design worn under it.
        val royal = IslandDress.of(
            wearing(PremiumSkins.byId(Seasons.boats.first())!!.design).copy(equippedSkins = mapOf(CosmeticSlot.BOAT to Cosmetic.ROYAL_BOAT)),
        )
        assertEquals(4, royal.level)
        assertEquals("crown", royal.legend)
        // Changing boats changes the island.
        val set = mapOf(CosmeticSlot.HOOK to Cosmetic.LEVIATHAN_BONE_HOOK, CosmeticSlot.LINE to Cosmetic.GIANTS_BRAID_LINE)
        assertEquals("leviathan", IslandDress.of(PlayerProgress(equippedSkins = set + (CosmeticSlot.BOAT to Cosmetic.GIANT_SLAYER_PENNANT))).legend)
        assertEquals(0, IslandDress.of(PlayerProgress(equippedSkins = set)).level)
        assertEquals(3, IslandDress.of(wearing(PremiumSkins.byId(Seasons.boats.first())!!.design).copy(equippedSkins = set)).level)
        // Every legendary boat has a monument.
        for (c in Cosmetic.entries.filter { it.slot == CosmeticSlot.BOAT }) {
            assertEquals(c.name, 4, IslandDress.of(PlayerProgress(equippedSkins = mapOf(c.slot to c))).level)
        }
    }
}
