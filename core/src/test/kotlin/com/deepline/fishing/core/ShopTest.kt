package com.deepline.fishing.core

import com.deepline.fishing.core.online.toDto
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
        // Mostly €0.50 to €2; complete sets at €2.99 (cheaper than their pieces); a few at €5, specials at €10.
        assertTrue(cents.all { it in 50..199 || it == 299 || it == 499 || it == 999 }, "prices: ${cents.toSortedSet()}")
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

    @Test
    fun bundlesAreAlwaysCheaperThanTheirPieces() {
        val p = PlayerProgress(ropeLevels = mapOf(RopeStat.LENGTH to 4))
        // Bundles of things also sold one by one (the founder's pack is exclusive, not a discount).
        for (item in listOf("starter_pack", "set_coral_reef", "set_neon_nights").map { ShopCatalog.byId(it)!! }) {
            val discount = ShopCatalog.discountPercent(item, p)
            assertNotNull(discount, "${item.id} costs ${item.euroCents} but its pieces only ${ShopCatalog.separateCents(item, p)}")
            assertTrue(discount in 1..90)
        }
    }

    @Test
    fun clubMembersGetAMonthlyPouch() {
        val member = ShopCatalog.grant(PlayerProgress(lanterns = 0), ShopCatalog.byId("captains_club")!!, now)
        assertTrue(ShopCatalog.clubPouchReady(member, now))
        val claimed = ShopCatalog.claimClubPouch(member, now)!!
        assertTrue(claimed.lanterns > 0)
        assertNull(ShopCatalog.claimClubPouch(claimed, now + 86_400_000L), "once a month")
        assertNotNull(ShopCatalog.claimClubPouch(claimed.copy(clubUntilMs = now + 60 * 86_400_000L), now + 31 * 86_400_000L))
        assertNull(ShopCatalog.claimClubPouch(PlayerProgress(), now), "members only")
        assertEquals(claimed.clubPouchNextMs, ProgressCodec.decode(ProgressCodec.encode(claimed)).clubPouchNextMs)
        // A store subscription that keeps renewing keeps the Club on a few days at a time.
        val renewed = ShopCatalog.onClubSubscriptionActive(PlayerProgress(), now)
        assertTrue(ShopCatalog.isClubMember(renewed, now + (GameConfig.CLUB_RENEWAL_GRACE_DAYS - 1) * 86_400_000L))
        assertFalse(ShopCatalog.isClubMember(renewed, now + (GameConfig.CLUB_RENEWAL_GRACE_DAYS + 1) * 86_400_000L))
    }

    @Test
    fun tryingAPartOnTheBoat() {
        val p = PlayerProgress()
        val bare = BoatDesign.DEFAULT
        assertEquals(HullPattern.NEON, BoatParts.applyTo(bare, BoatPart.of(HullPattern.NEON), p).pattern)
        // An emblem or a print needs a sail or a flag to show on.
        val withEmblem = BoatParts.applyTo(bare, BoatPart.of(Emblem.TRIDENT), p)
        assertEquals(Emblem.TRIDENT, withEmblem.emblem)
        assertTrue(withEmblem.sailStyle != SailStyle.NONE || withEmblem.flagStyle != FlagStyle.NONE)
        val printed = BoatParts.applyTo(bare.copy(flagStyle = FlagStyle.PENNANT), BoatPart.of(ClothPattern.STARS), p)
        assertEquals(SailStyle.NONE, printed.sailStyle, "a boat with a flag keeps its rig")
        assertEquals(ClothPattern.STARS, printed.cloth)
    }

    @Test
    fun sightTackleIsReportedInOnlineCasts() {
        val session = CastSession.seeded(com.deepline.fishing.core.online.OnlineProtocol.tournamentSpecs, 42L, lanternActive = true)
        session.detectorActive = true
        while (session.phase != CastPhase.SURFACED) session.update(1f / 30f, null)
        val result = session.result!!
        assertTrue(result.lanternUsed && result.detectorUsed && result.sightAided)
        val turn = BattleTurn(playerIndex = 0, castNumber = 1, round = 0, seed = 42L, timeLimitSec = null)
        val sent = BattleCast(result, 30f).toDto()
        assertTrue(sent.lanternUsed && sent.detectorUsed)
        // The server keeps what the client used: secrets that need the dark check it.
        val back = com.deepline.fishing.core.online.CastValidation.toBattleCast(sent, turn, com.deepline.fishing.core.online.OnlineProtocol.tournamentSpecs)!!
        assertTrue(back.result.lanternUsed && back.result.detectorUsed)
    }
}

class ShowcaseTest {
    private val now = 1_800_000_000_000L

    @Test
    fun titleFramesNeedTheirUnlock() {
        val p = PlayerProgress()
        assertNull(Showcase.setTitleFrame(p, TitleFrame.OCEAN, now))
        val owner = ShopCatalog.grant(p, ShopCatalog.byId("badge_ocean")!!, now)
        assertEquals(TitleFrame.OCEAN, Showcase.titleFrame(Showcase.setTitleFrame(owner, TitleFrame.OCEAN, now)!!, now))
        // The Club frame lasts as long as the membership.
        val member = ShopCatalog.grant(p, ShopCatalog.byId("captains_club")!!, now)
        val worn = Showcase.setTitleFrame(member, TitleFrame.CLUB, now)!!
        assertEquals(TitleFrame.CLUB, Showcase.titleFrame(worn, now))
        assertEquals(TitleFrame.GOLD, Showcase.titleFrame(worn, now + 40 * 86_400_000L))
    }

    @Test
    fun theAquariumHoldsCaughtFishOnly() {
        val caught = FishCatalog.all.take(8)
        val p = PlayerProgress(collection = caught.withIndex().associate { (i, s) -> s.id to CollectionEntry(1, 0, 10f, 100 + i) })
        assertEquals(Showcase.TANK_SIZE, Showcase.tankFish(p).size)
        assertEquals(caught.last(), Showcase.tankFish(p).first(), "best catches first")
        assertNull(Showcase.toggleTankFish(p, FishCatalog.all.last().id), "not caught")
        val fewer = Showcase.toggleTankFish(p, Showcase.tankFish(p).first().id)!!
        assertEquals(Showcase.TANK_SIZE - 1, Showcase.tankFish(fewer).size)
        val big = ShopCatalog.grant(p, ShopCatalog.byId("aquarium_expansion")!!, now)
        assertEquals(8, Showcase.tankFish(big).size)
        assertTrue(Showcase.shownDecor(p).all { it.free })
        val decorated = ShopCatalog.grant(p, ShopCatalog.byId("decor_sunken_ship")!!, now)
        assertTrue(Decor.SUNKEN_SHIP in Showcase.shownDecor(decorated))
        assertFalse(Decor.SUNKEN_SHIP in Showcase.shownDecor(Showcase.toggleDecor(decorated, Decor.SUNKEN_SHIP)!!))
        val back = ProgressCodec.decode(ProgressCodec.encode(fewer.copy(titleFrame = "OCEAN", cardStyle = "HOLO", hiddenDecor = setOf("PLANTS"))))
        assertEquals(fewer.tankFish, back.tankFish)
        assertEquals("OCEAN", back.titleFrame)
        assertEquals("HOLO", back.cardStyle)
        assertEquals(setOf("PLANTS"), back.hiddenDecor)
    }
}
