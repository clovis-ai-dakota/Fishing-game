package com.deepline.fishing.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ShopTest {
    private val now = 1_800_000_000_000L

    @Test
    fun pricesAreMostlyLowWithAFewAtFiveAndSpecialsAtTen() {
        val cents = ShopCatalog.all.map { it.euroCents }
        assertTrue(cents.all { it in 50..199 || it == 499 || it == 999 }, "prices: ${cents.toSortedSet()}")
        assertTrue(cents.count { it <= 199 } > cents.size * 0.7)
        assertTrue(ShopCatalog.all.map { it.id }.toSet().size == ShopCatalog.all.size, "unique product ids")
    }

    @Test
    fun pointsPricesAreFishingTimeAtTheRopesReach() {
        val item = ShopCatalog.byId("paint_flames")!!
        // €0.50 at 45 min per euro: 22.5 minutes of fishing.
        assertEquals(22.5f, ShopCatalog.minutes(item))
        val early = ShopCatalog.pointsPrice(item, PlayerProgress())
        val late = ShopCatalog.pointsPrice(item, PlayerProgress(ropeLevels = mapOf(RopeStat.LENGTH to 10)))
        assertEquals(ShopCatalog.niceRound(22.5 * GameConfig.POINTS_PER_MINUTE_BY_LENGTH_LEVEL[0]), early)
        assertTrue(late > early * 20)
        // Lanterns: a full hour per euro. Bigger tiers give more per euro.
        assertEquals(30f, ShopCatalog.minutes(ShopCatalog.byId("lantern_pouch")!!))
        assertTrue(ShopCatalog.minutes(ShopCatalog.byId("founders_pack")!!) > 9.99f * 45f * 1.3f)
    }

    @Test
    fun buyingWithPointsGrantsAndOnlyOnceForPermanentItems() {
        var p = PlayerProgress(pointsBalance = 10_000_000)
        val stripes = ShopCatalog.byId("cloth_stripes")!!
        p = ShopCatalog.buyWithPoints(p, stripes, now)!!
        assertTrue(BoatParts.isAvailable(p, BoatPart.of(ClothPattern.STRIPES)))
        assertNull(ShopCatalog.buyWithPoints(p, stripes, now), "owned already")
        // A pack is sized to its price in single items, and can be bought again.
        val crate = ShopCatalog.byId("lantern_crate")!!
        val before = p.lanterns
        p = ShopCatalog.buyWithPoints(p, crate, now)!!
        val got = p.lanterns - before
        assertEquals((ShopCatalog.pointsPrice(crate, p) / Progression.tacklePrice(p, Tackle.LANTERN)).toInt(), got)
        assertNotNull(ShopCatalog.buyWithPoints(p, crate, now))
        assertNull(ShopCatalog.buyWithPoints(PlayerProgress(pointsBalance = 10), crate, now), "too expensive")
    }

    @Test
    fun presetsSetsAndGearCosmetics() {
        var p = PlayerProgress(pointsBalance = 100_000_000)
        p = ShopCatalog.buyWithPoints(p, ShopCatalog.byId("set_neon_nights")!!, now)!!
        assertTrue(ShopCatalog.ownsPreset(p, "neon_nights"))
        assertTrue(Cosmetic.NEON_HOOK in p.unlockedCosmetics && Cosmetic.STAR_TRAIL in p.unlockedCosmetics)
        val design = PremiumSkins.byId("neon_nights")!!.design
        assertNotNull(Progression.setBoatDesign(p, design), "a bought preset's options are all available")
        val worn = ShopCatalog.wearPreset(p, "neon_nights")!!
        assertTrue(worn.customBoatEquipped)
        assertEquals(design, worn.boatDesign)
        assertNull(ShopCatalog.wearPreset(PlayerProgress(), "neon_nights"))
    }

    @Test
    fun theStarterPackOpensOnTheThirdSessionForThreeDays() {
        var p = PlayerProgress()
        val starter = ShopCatalog.byId("starter_pack")!!
        repeat(GameConfig.STARTER_OFFER_FROM_SESSION - 1) { p = ShopCatalog.onSessionStart(p, now) }
        assertFalse(ShopCatalog.canBuy(starter, p, now))
        p = ShopCatalog.onSessionStart(p, now)
        assertTrue(ShopCatalog.canBuy(starter, p, now))
        assertFalse(ShopCatalog.canBuy(starter, p, now + GameConfig.STARTER_OFFER_HOURS * 3_600_000L + 1))
        p = ShopCatalog.grant(p, starter, now)
        assertFalse(ShopCatalog.canBuy(starter, p, now))
        assertEquals(20, p.lanterns - GameConfig.STARTING_LANTERNS)
    }

    @Test
    fun theClubLastsAMonthAndStacks() {
        val club = ShopCatalog.byId("captains_club")!!
        var p = ShopCatalog.grant(PlayerProgress(), club, now)
        assertTrue(ShopCatalog.isClubMember(p, now + 29 * 86_400_000L))
        assertFalse(ShopCatalog.isClubMember(p, now + 31 * 86_400_000L))
        p = ShopCatalog.grant(p, club, now)
        assertTrue(ShopCatalog.isClubMember(p, now + 59 * 86_400_000L))
        ShopClock.override = now
        try {
            assertTrue(BoatParts.isAvailable(p, BoatPart.of(HullPattern.AURORA)))
            assertFalse(BoatParts.isAvailable(PlayerProgress(), BoatPart.of(HullPattern.AURORA)))
        } finally {
            ShopClock.override = null
        }
    }

    @Test
    fun ownershipIsSaved() {
        val p = PlayerProgress(ownedItems = setOf("paint_neon", "cosmetic:NEON_HOOK"), clubUntilMs = now, sessions = 4, starterOfferEndsMs = now + 5, detectors = 7,
            boatDesign = BoatDesign(cloth = ClothPattern.WAVES))
        val back = ProgressCodec.decode(ProgressCodec.encode(p))
        assertEquals(p.ownedItems, back.ownedItems)
        assertEquals(now, back.clubUntilMs)
        assertEquals(4, back.sessions)
        assertEquals(7, back.detectors)
        assertEquals(ClothPattern.WAVES, back.boatDesign.cloth)
    }
}
