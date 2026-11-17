package com.deepline.fishing.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DecorSpotsTest {
    private val everything = PlayerProgress(ownedItems = Decor.entries.map { it.unlockKey }.toSet())

    @Test
    fun decorStaysWhereThePlayerPutsItAndNewPiecesNeverMoveIt() {
        // Every piece has a spot of its own: owning more never moves the others.
        val spots = Decor.entries.associateWith { Showcase.decorSpot(PlayerProgress(), it) }
        assertEquals(spots, Decor.entries.associateWith { Showcase.decorSpot(everything, it) })
        assertTrue(spots.values.all { it in 0f..1f })
        // Moved, saved, loaded.
        val moved = Showcase.placeDecor(everything, Decor.SUNKEN_SHIP, 0.8f)!!
        assertEquals(0.8f, Showcase.decorSpot(moved, Decor.SUNKEN_SHIP))
        assertEquals(spots[Decor.PEBBLES], Showcase.decorSpot(moved, Decor.PEBBLES))
        assertEquals(moved.decorSpots, ProgressCodec.decode(ProgressCodec.encode(moved)).decorSpots)
        // Clamped to the tank; only owned, movable pieces.
        assertEquals(1f, Showcase.decorSpot(Showcase.placeDecor(everything, Decor.PLANTS, 3f)!!, Decor.PLANTS))
        assertNull(Showcase.placeDecor(PlayerProgress(), Decor.SUNKEN_SHIP, 0.5f))
        assertNull(Showcase.placeDecor(everything, Decor.LANTERN_LIGHTS, 0.5f))
        assertNotNull(Showcase.placeDecor(PlayerProgress(), Decor.PLANTS, 0.5f))
    }
}
