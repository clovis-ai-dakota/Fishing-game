package com.deepline.fishing.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The Dex: fish seen during casts, caught fish, rarity stars and secret tiers. */
class DexTest {
    private val center = GameConfig.WORLD_WIDTH / 2f

    /** A cast that goes straight down to [ropeM] and back up, past [fish] that don't move. */
    private fun castPast(ropeM: Float, lantern: Boolean, vararg fish: Pair<String, Pair<Float, Float>>): CastResult {
        val entities = fish.mapIndexed { i, (id, pos) ->
            FishEntity(i, FishCatalog.byId(id)!!, false, pos.first, pos.second, 1, baseSpeed = 0f)
        }
        val session = CastSession(RopeSpecs(ropeM, 50f, 9f), lantern, World(entities, emptyList()))
        while (session.phase != CastPhase.SURFACED) session.update(1f / 60f, null)
        return session.result!!
    }

    @Test
    fun fishNearTheHookAreSeenFarOnesAreNot() {
        val near = "silver_sprat" to (center + 6f to 40f)
        val far = "coral_darter" to (center + 6f to 80f) // the rope ends at 60 m: never within reach
        val r = castPast(60f, lantern = false, near, far)
        assertTrue("silver_sprat" in r.seenSpeciesIds)
        assertFalse("coral_darter" in r.seenSpeciesIds)
    }

    @Test
    fun inDarkWaterOnlyTheLitCircleCounts() {
        // Below the dark depth the hook only lights a small circle, a Lantern a bigger one.
        val inside = Lighting.visibleRadius(false) - 1f
        val outside = Lighting.visibleRadius(false) + 1f
        assertTrue(outside < GameConfig.SEEN_DISTANCE_M && outside < Lighting.visibleRadius(true))
        val depth = GameConfig.DARK_DEPTH + GameConfig.DARK_FADE_RANGE + 40f
        val close = "ghost_hake" to (center + inside to depth)
        val wide = "needle_viper" to (center + outside to depth)
        val dark = castPast(depth + 10f, lantern = false, close, wide)
        assertTrue("ghost_hake" in dark.seenSpeciesIds)
        assertFalse("needle_viper" in dark.seenSpeciesIds)
        val lit = castPast(depth + 10f, lantern = true, close, wide)
        assertTrue("needle_viper" in lit.seenSpeciesIds)
    }

    @Test
    fun dexStatesGoFromUnknownToSeenToCaught() {
        val id = "reed_perch"
        var p = PlayerProgress()
        assertEquals(DexState.UNKNOWN, Progression.dexState(p, id))
        p = Progression.applyCast(p, CastResult(emptyList(), 50f, 0, ReverseReason.ROPE_END, false, seenSpeciesIds = setOf(id)))
        assertEquals(DexState.SEEN, Progression.dexState(p, id))
        assertEquals(1, p.seenCount)
        assertEquals(0, p.discoveredCount)
        val catch = CatchRecord(FishCatalog.byId(id)!!, false, 40f, 10)
        p = Progression.applyCast(p, CastResult(listOf(catch), 50f, 0, ReverseReason.ROPE_END, false))
        assertEquals(DexState.CAUGHT, Progression.dexState(p, id))
        // A catch also counts as seen, even if the cast didn't report it.
        val other = CatchRecord(FishCatalog.byId("sun_bream")!!, false, 40f, 10)
        p = Progression.applyCast(p, CastResult(listOf(other), 50f, 0, ReverseReason.ROPE_END, false))
        assertTrue("sun_bream" in p.seenSpecies)
    }

    @Test
    fun seenSpeciesAreSavedAndOldSavesStillLoad() {
        val p = PlayerProgress(seenSpecies = setOf("reed_perch", "deep_sovereign"))
        assertEquals(p.seenSpecies, ProgressCodec.decode(ProgressCodec.encode(p)).seenSpecies)
        // A save from before the Dex had no "seen" line.
        val old = ProgressCodec.encode(PlayerProgress(pointsBalance = 42)).lines().filterNot { it.startsWith("seen=") }.joinToString("\n")
        val loaded = ProgressCodec.decode(old)
        assertEquals(42, loaded.pointsBalance)
        assertEquals(emptySet(), loaded.seenSpecies)
    }

    @Test
    fun rarityStarsAndNumbers() {
        assertEquals(1, Progression.rarityStars(FishCatalog.byId("silver_sprat")!!))
        assertEquals(5, Progression.rarityStars(FishCatalog.sovereign))
        assertTrue(FishCatalog.all.all { Progression.rarityStars(it) in 1..5 })
        assertEquals(1, Progression.dexNumber(FishCatalog.all.first()))
        assertEquals(FishCatalog.all.size, Progression.dexNumber(FishCatalog.sovereign))
    }

    @Test
    fun everySecretHasATierAndTheLegendaryOnesAreTopTier() {
        assertTrue(Secrets.all.all { Secrets.tierOf(it) in 1..Secrets.MAX_TIER })
        val legendary = Cosmetic.entries.filter { !it.fromShop }.map { it.unlockedBy }.toSet()
        assertEquals(legendary, Secrets.all.filter { Secrets.tierOf(it) == Secrets.MAX_TIER }.toSet())
        // Tiers never go down along the list.
        assertEquals(Secrets.all.map(Secrets::tierOf).sorted(), Secrets.all.map(Secrets::tierOf))
    }
}
