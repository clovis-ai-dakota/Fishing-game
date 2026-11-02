package com.deepline.fishing.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RulesTest {
    private val hake = FishCatalog.byId("ghost_hake")!!

    @Test
    fun deeperCatchesScoreMore() {
        assertTrue(Scoring.points(hake, 450f, false) > Scoring.points(hake, 320f, false))
        assertEquals(hake.basePoints, Scoring.points(hake, 0f, false))
    }

    @Test
    fun sparklingFishAreWorthABonus() {
        val normal = Scoring.points(hake, 400f, false)
        val sparkling = Scoring.points(hake, 400f, true)
        assertEquals(normal * GameConfig.SPARKLING_MULTIPLIER, sparkling)
    }

    @Test
    fun darknessOnlyAppliesBelow300m() {
        assertEquals(0f, Lighting.darkness(299f))
        assertEquals(0f, Lighting.darkness(300f))
        assertEquals(1f, Lighting.darkness(400f))
        assertTrue(Lighting.visibleRadius(true) > Lighting.visibleRadius(false))
    }

    @Test
    fun upgradesCostPointsAndRaiseSpecs() {
        val start = PlayerProgress(pointsBalance = 1_000)
        val cost = RopeStat.LENGTH.costToUpgradeFrom(0)!!
        val upgraded = assertNotNull(Progression.upgrade(start, RopeStat.LENGTH))
        assertEquals(1_000L - cost, upgraded.pointsBalance)
        assertEquals(1, upgraded.level(RopeStat.LENGTH))
        assertTrue(upgraded.specs.lengthM > start.specs.lengthM)
        assertNull(Progression.upgrade(PlayerProgress(pointsBalance = 0), RopeStat.LENGTH))
    }

    @Test
    fun upgradesStopAtMaxLevel() {
        val maxed = PlayerProgress(pointsBalance = 1_000_000, ropeLevels = mapOf(RopeStat.STRENGTH to 10))
        assertNull(Progression.upgrade(maxed, RopeStat.STRENGTH))
    }

    @Test
    fun spendingDoesNotLowerMilestoneProgress() {
        val catches = listOf(CatchRecord(hake, false, 400f, 400))
        val result = CastResult(catches, 400f, 1, ReverseReason.TOUCH_LIMIT, false)
        var p = Progression.applyCast(PlayerProgress(), result)
        assertEquals(400L, p.pointsEarned)
        p = Progression.upgrade(p, RopeStat.AGILITY)!!
        assertEquals(400L, p.pointsEarned)

        val first = Milestones.all.first()
        assertTrue(Progression.isMilestoneClaimable(p, first))
        val claimed = Progression.claimMilestone(p, first)!!
        assertEquals(p.lanterns + first.rewardLanterns, claimed.lanterns)
        assertNull(Progression.claimMilestone(claimed, first))
    }

    @Test
    fun castUpdatesTheCollectionLog() {
        val catches = listOf(
            CatchRecord(hake, false, 320f, 100),
            CatchRecord(hake, true, 410f, 500),
        )
        val p = Progression.applyCast(PlayerProgress(), CastResult(catches, 410f, 2, null, false))
        val entry = p.collection.getValue(hake.id)
        assertEquals(2, entry.caught)
        assertEquals(1, entry.sparklingCaught)
        assertEquals(410f, entry.deepestM)
        assertEquals(500, entry.bestPoints)
        assertEquals(1, p.discoveredCount)
    }

    @Test
    fun newEventResetsPointsButKeepsGear() {
        val p = PlayerProgress(
            pointsBalance = 50, pointsEarned = 5_000, claimedMilestones = setOf(0, 1),
            ropeLevels = mapOf(RopeStat.LENGTH to 3), lanterns = 4, eventStartMillis = 1,
        )
        assertTrue(Progression.isEventOver(p, 1 + GameConfig.EVENT_DURATION_MS))
        val fresh = Progression.startNewEvent(p, 99)
        assertEquals(0L, fresh.pointsEarned)
        assertTrue(fresh.claimedMilestones.isEmpty())
        assertEquals(3, fresh.level(RopeStat.LENGTH))
        assertEquals(4, fresh.lanterns)
    }

    @Test
    fun progressSurvivesASaveLoadRoundTrip() {
        val p = PlayerProgress(
            pointsBalance = 1234, pointsEarned = 5678, claimedMilestones = setOf(0, 2),
            ropeLevels = mapOf(RopeStat.LENGTH to 2, RopeStat.AGILITY to 5), lanterns = 3,
            collection = mapOf("ghost_hake" to CollectionEntry(4, 1, 455.5f, 820)),
            eventStartMillis = 1_700_000_000_000, castsMade = 9,
        )
        val repo = ProgressRepository(InMemoryStore())
        repo.save(p)
        assertEquals(p, repo.load())
        assertEquals(PlayerProgress(), ProgressRepository(InMemoryStore()).load())
    }
}
