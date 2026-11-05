package com.deepline.fishing.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SeasonsTest {
    private val day = 86_400_000L
    private val start = Seasons.EPOCH_MS

    private fun cast(fish: Int = 2, sparkling: Int = 0, depth: Float = 50f): CastResult {
        val sp = FishCatalog.all.first()
        val catches = (0 until fish).map { CatchRecord(sp, it < sparkling, sp.minDepth, 10) }
        return CastResult(catches, depth, 0, ReverseReason.ROPE_END, lanternUsed = false)
    }

    @Test
    fun seasonsFollowEachOther() {
        val s1 = Seasons.at(start + day)
        assertEquals(1, s1.number)
        assertEquals(GameConfig.SEASON_WEEKS * 7 * day, s1.endMs - s1.startMs)
        val s2 = Seasons.at(s1.endMs)
        assertEquals(2, s2.number)
        assertTrue(s2.name != s1.name)
    }

    @Test
    fun castsClimbTheTrackAndANewSeasonStartsAtZero() {
        var p = PlayerProgress()
        val now = start + day
        repeat(20) { p = Seasons.onCast(p, cast(), now) }
        assertEquals(20 * Seasons.castXp(cast()), p.seasonXp)
        assertEquals(p.seasonXp / GameConfig.SEASON_XP_PER_TIER, Seasons.tier(p, now))
        // The free track only, without the pass.
        val open = Seasons.claimable(p, now)
        assertTrue(open.isNotEmpty() && open.none { it.premium })
        val claimed = Seasons.claimAll(p, now)
        assertTrue(Seasons.claimable(claimed, now).isEmpty())
        assertTrue(claimed.lanterns >= p.lanterns)
        // Next season: back to tier 0.
        val later = Seasons.at(now).endMs + day
        assertEquals(0, Seasons.tier(claimed, later))
        assertEquals(Seasons.castXp(cast(fish = 0)), Seasons.onCast(claimed, cast(fish = 0), later).seasonXp)
    }

    @Test
    fun thePassOpensThePremiumTrackAndTheCollectorSkipsTiers() {
        val now = start + 3 * day
        var p = PlayerProgress(pointsBalance = 100_000_000)
        p = ShopCatalog.buyWithPoints(p, ShopCatalog.byId("season_pass")!!, now)!!
        assertTrue(Seasons.hasPass(p, Seasons.at(now)))
        assertFalse(ShopCatalog.canBuy(ShopCatalog.byId("season_pass")!!, p, now), "once a season")
        repeat(10) { p = Seasons.onCast(p, cast(), now) }
        assertTrue(Seasons.claimable(p, now).any { it.premium })
        p = Seasons.claimAll(p, now)
        assertTrue("badge:SEASON" in p.ownedItems)
        // Next season, the pass can be bought again.
        assertTrue(ShopCatalog.canBuy(ShopCatalog.byId("season_pass")!!, p, Seasons.at(now).endMs + day))

        var c = PlayerProgress(pointsBalance = 100_000_000)
        c = ShopCatalog.buyWithPoints(c, ShopCatalog.byId("collector_edition")!!, now)!!
        assertEquals(GameConfig.COLLECTOR_TIER_SKIP, Seasons.tier(c, now))
        assertTrue(ShopCatalog.ownsPreset(c, Seasons.boatOf(Seasons.at(now))))
        assertTrue("badge:COLLECTOR" in c.ownedItems)
    }

    @Test
    fun weekendEventsRunFridayEveningToMondayMorning() {
        val friday = start + 4 * day
        assertNull(Seasons.eventAt(friday + 12 * 3_600_000L), "Friday noon: not yet")
        val e = assertNotNull(Seasons.eventAt(friday + 19 * 3_600_000L))
        assertEquals(e, Seasons.eventAt(friday + 3 * day + 5 * 3_600_000L), "still on Monday at 5:00")
        assertNull(Seasons.eventAt(friday + 3 * day + 7 * 3_600_000L))
        assertTrue(Seasons.eventAt(friday + 7 * day + 19 * 3_600_000L)!!.goal != e.goal, "a new goal each week")
        assertFalse(ShopCatalog.canBuy(ShopCatalog.byId("event_pass")!!, PlayerProgress(), friday), "only during an event")
    }

    @Test
    fun theEventGoalGivesARewardAndThePassDoublesProgress() {
        val now = start + 4 * day + 20 * 3_600_000L
        val e = Seasons.eventAt(now)!!
        val r = when (e.goal) {
            EventGoal.SPARKLING -> cast(fish = 3, sparkling = 3)
            EventGoal.DEEP -> cast(depth = DepthZone.MIDNIGHT.minDepth + 1f)
            else -> cast(fish = 3)
        }
        val one = Seasons.eventCount(e.goal, r, PlayerProgress())
        assertTrue(one > 0)
        var free = PlayerProgress()
        var pass = ShopCatalog.grant(PlayerProgress(), ShopCatalog.byId("event_pass")!!, now)
        free = Seasons.onCast(free, r, now)
        pass = Seasons.onCast(pass, r, now)
        assertEquals(2 * free.eventProgress, pass.eventProgress)
        while (Seasons.eventClaimable(pass, now).isEmpty()) pass = Seasons.onCast(pass, r, now)
        val claimed = Seasons.claimEvent(pass, now)!!
        assertTrue("badge:EVENT" in claimed.ownedItems)
        assertNull(Seasons.claimEvent(claimed, now))
    }

    @Test
    fun seasonProgressIsSaved() {
        val p = PlayerProgress(seasonNumber = 3, seasonXp = 420, seasonClaims = setOf("f1", "p1"), eventKey = "w12", eventProgress = 7, eventClaims = setOf("free"))
        val back = ProgressCodec.decode(ProgressCodec.encode(p))
        assertEquals(p.seasonNumber, back.seasonNumber)
        assertEquals(p.seasonXp, back.seasonXp)
        assertEquals(p.seasonClaims, back.seasonClaims)
        assertEquals(p.eventKey, back.eventKey)
        assertEquals(p.eventProgress, back.eventProgress)
        assertEquals(p.eventClaims, back.eventClaims)
    }
}
