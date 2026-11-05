package com.deepline.fishing.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BoatCustomisationTest {
    @Test
    fun theDefaultDesignIsFullyFree() {
        val p = PlayerProgress()
        for (part in BoatParts.partsOf(BoatDesign.DEFAULT)) {
            assertEquals(PartRequirement.Free, BoatParts.requirement(part), part.key)
            assertTrue(BoatParts.isAvailable(p, part))
        }
        assertNotNull(Progression.setBoatDesign(p, BoatDesign.DEFAULT))
    }

    @Test
    fun everyOptionHasARequirementAndARoundTripKey() {
        val expected = BoatPalette.swatches.size + HullPattern.entries.size + SailStyle.entries.size +
            FlagStyle.entries.size + Emblem.entries.size + ClothPattern.entries.size
        assertEquals(expected, BoatParts.all.size)
        assertEquals(expected, BoatParts.all.map { it.key }.toSet().size)
        for (part in BoatParts.all) assertEquals(part, BoatPart.fromKey(part.key))
        // A mix of ways to unlock things: free basics, points, progress, the shop and the club.
        val kinds = BoatParts.all.map { BoatParts.requirement(it)::class }.toSet()
        assertEquals(7, kinds.size)
    }

    @Test
    fun buyingAPartSpendsTheBalanceOnly() {
        val stripe = BoatPart.of(HullPattern.STRIPE)
        val cost = (BoatParts.requirement(stripe) as PartRequirement.Points).cost
        val poor = PlayerProgress(pointsBalance = cost - 1L, pointsEarned = 10_000)
        assertNull(Progression.unlockBoatPart(poor, stripe))
        val rich = poor.copy(pointsBalance = cost + 50L)
        assertFalse(BoatParts.isAvailable(rich, stripe))
        val after = Progression.unlockBoatPart(rich, stripe)!!
        assertEquals(50L, after.pointsBalance)
        assertEquals(10_000L, after.pointsEarned, "lifetime earnings unchanged")
        assertTrue(BoatParts.isAvailable(after, stripe))
        assertNull(Progression.unlockBoatPart(after.copy(pointsBalance = 99_999), stripe), "already owned")
        // Parts unlocked by progress can't be bought.
        assertNull(Progression.unlockBoatPart(rich.copy(pointsBalance = 99_999), BoatPart.of(HullPattern.CHECKER)))
    }

    @Test
    fun progressUnlocksPartsWithoutSpending() {
        val fish = BoatPart.of(Emblem.FISH)
        val count = (BoatParts.requirement(fish) as PartRequirement.SpeciesCaught).count
        val p = PlayerProgress(collection = FishCatalog.all.take(count).associate { it.id to CollectionEntry(caught = 1) })
        assertTrue(BoatParts.isAvailable(p, fish))
        assertFalse(BoatParts.isAvailable(p.copy(collection = p.collection.entries.drop(1).associate { it.key to it.value }), fish))

        val moon = BoatPart.of(Emblem.MOON) // a fish from the Midnight Trench
        val trenchFish = FishCatalog.all.first { it.zone == DepthZone.MIDNIGHT }
        assertFalse(BoatParts.isAvailable(PlayerProgress(), moon))
        assertTrue(BoatParts.isAvailable(PlayerProgress(collection = mapOf(trenchFish.id to CollectionEntry(caught = 1))), moon))

        val gold = BoatPart(BoatPartKind.COLOR, "Gold")
        val milestones = (BoatParts.requirement(gold) as PartRequirement.MilestonesClaimed).count
        assertTrue(BoatParts.isAvailable(PlayerProgress(claimedMilestones = (0 until milestones).toSet()), gold))
        assertFalse(BoatParts.isAvailable(PlayerProgress(claimedMilestones = (0 until milestones - 1).toSet()), gold))
    }

    @Test
    fun designsMayOnlyUseAvailableOptions() {
        val p = PlayerProgress(pointsBalance = 100_000)
        val withSail = BoatDesign.DEFAULT.copy(sailStyle = SailStyle.TRIANGLE)
        assertNull(Progression.setBoatDesign(p, withSail))
        val bought = Progression.unlockBoatPart(p, BoatPart.of(SailStyle.TRIANGLE))!!
        assertEquals(withSail, Progression.setBoatDesign(bought, withSail)!!.boatDesign)
        // Premium-only options never appear in a player's own design.
        val flames = BoatDesign.DEFAULT.copy(pattern = HullPattern.FLAMES)
        assertTrue(BoatParts.isPremiumOnly(BoatPart.of(HullPattern.FLAMES)))
        assertNull(Progression.unlockBoatPart(p, BoatPart.of(HullPattern.FLAMES)))
        assertNull(Progression.setBoatDesign(p, flames))
    }

    @Test
    fun theCustomBoatAndBoatCosmeticsReplaceEachOther() {
        val p = PlayerProgress(discoveredSecrets = setOf(Secrets.DAVID_AND_GOLIATH))
        val withPennant = Progression.equipSkin(p, CosmeticSlot.BOAT, Cosmetic.GIANT_SLAYER_PENNANT)
        val custom = Progression.equipCustomBoat(withPennant, true)
        assertTrue(custom.customBoatEquipped)
        assertNull(custom.equippedSkins[CosmeticSlot.BOAT])
        val back = Progression.equipSkin(custom, CosmeticSlot.BOAT, Cosmetic.GIANT_SLAYER_PENNANT)
        assertFalse(back.customBoatEquipped)
        assertEquals(Cosmetic.GIANT_SLAYER_PENNANT, back.equippedSkins[CosmeticSlot.BOAT])
        // Other slots are untouched.
        val withHook = Progression.equipSkin(back, CosmeticSlot.HOOK, Cosmetic.LEVIATHAN_BONE_HOOK)
        assertTrue(Progression.equipCustomBoat(withHook, true).equippedSkins[CosmeticSlot.HOOK] == Cosmetic.LEVIATHAN_BONE_HOOK)
    }

    @Test
    fun theNightmareAtmosphereUnlocksAtTheAbyssalFloor() {
        val p = PlayerProgress()
        assertNull(Progression.setAtmosphere(p, Atmosphere.NIGHTMARE))
        assertEquals(Atmosphere.FAMILY, Progression.setAtmosphere(p, Atmosphere.FAMILY)!!.atmosphere)
        fun cast(maxDepth: Float) = CastResult(emptyList(), maxDepth, 0, ReverseReason.ROPE_END, lanternUsed = false)
        val shallow = Progression.applyCast(p, cast(DepthZone.ABYSS.minDepth - 1f))
        assertFalse(shallow.atmosphereUnlocked)
        val deep = Progression.applyCast(shallow, cast(DepthZone.ABYSS.minDepth))
        assertTrue(deep.atmosphereUnlocked)
        assertEquals(Atmosphere.NIGHTMARE, Progression.setAtmosphere(deep, Atmosphere.NIGHTMARE)!!.atmosphere)
        // It stays unlocked.
        assertTrue(Progression.applyCast(deep, cast(10f)).atmosphereUnlocked)
    }

    @Test
    fun customisationIsSavedAndOldSavesStillLoad() {
        val design = BoatDesign(3, 7, 12, 1, 9, 14, HullPattern.DOTS, SailStyle.SQUARE, FlagStyle.SWALLOWTAIL, Emblem.ANCHOR)
        val p = PlayerProgress(
            boatDesign = design,
            customBoatEquipped = true,
            unlockedBoatParts = setOf(BoatPart.of(HullPattern.DOTS).key, BoatPart.of(SailStyle.SQUARE).key),
            atmosphereUnlocked = true,
            atmosphere = Atmosphere.NIGHTMARE,
        )
        val decoded = ProgressCodec.decode(ProgressCodec.encode(p))
        assertEquals(p.boatDesign, decoded.boatDesign)
        assertTrue(decoded.customBoatEquipped)
        assertEquals(p.unlockedBoatParts, decoded.unlockedBoatParts)
        assertTrue(decoded.atmosphereUnlocked)
        assertEquals(Atmosphere.NIGHTMARE, decoded.atmosphere)

        // A save from before customisation existed.
        val old = ProgressCodec.decode("v=1\nbalance=500\nearned=900\nskins=BOAT:GIANT_SLAYER_PENNANT\n")
        assertEquals(BoatDesign.DEFAULT, old.boatDesign)
        assertFalse(old.customBoatEquipped)
        assertTrue(old.unlockedBoatParts.isEmpty())
        assertFalse(old.atmosphereUnlocked)
        assertEquals(Atmosphere.FAMILY, old.atmosphere)
        assertEquals(500L, old.pointsBalance)

        // Garbage is ignored.
        val bad = ProgressCodec.decode("boat=1,2,x\nboatParts=NOPE:1,PATTERN:DOTS\natmosphere=NIGHTMARE\n")
        assertEquals(BoatDesign.DEFAULT, bad.boatDesign)
        assertEquals(setOf("PATTERN:DOTS"), bad.unlockedBoatParts)
        assertEquals(Atmosphere.FAMILY, bad.atmosphere, "Nightmare needs the unlock")
    }

    @Test
    fun premiumSkinsAreValidDesignsAndShowOffPremiumOptions() {
        assertTrue(PremiumSkins.all.size in 6..10)
        assertEquals(PremiumSkins.all.size, PremiumSkins.all.map { it.id }.toSet().size)
        for (s in PremiumSkins.all) {
            assertTrue(BoatParts.partsOf(s.design).all { it in BoatParts.all }, s.id)
        }
        assertTrue(PremiumSkins.all.any { s -> BoatParts.partsOf(s.design).any { BoatParts.isPremiumOnly(it) } })
    }
}
