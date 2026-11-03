package com.deepline.fishing.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SecretsTest {
    private fun sp(id: String) = FishCatalog.byId(id)!!
    private fun catch(id: String, depth: Float, byLastBump: Boolean = false, bumped: Boolean = false) =
        CatchRecord(sp(id), false, depth, Scoring.points(sp(id), depth, false), byLastBump, bumped)

    private fun result(
        catches: List<CatchRecord> = emptyList(),
        maxDepth: Float = 100f,
        bumps: List<BumpKind> = emptyList(),
        reason: ReverseReason? = if (bumps.size == GameConfig.DESCENT_TOUCH_LIMIT) ReverseReason.TOUCH_LIMIT else ReverseReason.ROPE_END,
        snags: List<SnagRecord> = emptyList(),
        strength: Float = 30f,
    ) = CastResult(catches, maxDepth, bumps.size, reason, false, snags, bumps, strength)

    private fun bonus(r: CastResult, id: String) = r.secretBonuses.firstOrNull { it.secretId == id }?.points

    @Test
    fun schoolingNeedsThreeOfOneSpecies() {
        val three = result(List(3) { catch("silver_sprat", 20f + it) })
        assertEquals(SecretRules.SCHOOLING_BONUS, bonus(three, Secrets.SCHOOLING))
        assertNull(bonus(result(List(2) { catch("silver_sprat", 20f) } + catch("coral_darter", 20f)), Secrets.SCHOOLING))
    }

    @Test
    fun secondEncounterDoublesTheReCaughtFish() {
        val again = catch("dusk_snapper", 150f, bumped = true)
        val r = result(listOf(again, catch("silver_sprat", 30f)), bumps = listOf(BumpKind.FISH_HEAD))
        assertEquals(again.points, bonus(r, Secrets.SECOND_ENCOUNTER))
        assertNull(bonus(result(listOf(catch("dusk_snapper", 150f))), Secrets.SECOND_ENCOUNTER))
    }

    @Test
    fun cleanDropNeedsALongRopeAndNoBumps() {
        val catches = listOf(catch("ghost_hake", 320f))
        val clean = result(catches, maxDepth = 320f)
        assertEquals(Math.round(clean.basePoints * 0.5).toInt(), bonus(clean, Secrets.CLEAN_DROP))
        assertNull(bonus(result(catches, maxDepth = 250f), Secrets.CLEAN_DROP), "rope too short")
        assertNull(bonus(result(catches, maxDepth = 320f, bumps = listOf(BumpKind.ROCK)), Secrets.CLEAN_DROP), "one bump")
    }

    @Test
    fun grandTourNeedsAllFourKindsAndDoublesWhatWasHooked() {
        val snag = SnagRecord(ObstacleKind.JELLY, 200f, Scoring.snagPoints(ObstacleKind.JELLY, 200f))
        val tour = result(
            bumps = listOf(BumpKind.FISH_HEAD, BumpKind.ROCK, BumpKind.DRIFTWOOD, BumpKind.JELLY),
            snags = listOf(snag),
            maxDepth = 200f,
        )
        assertEquals(snag.points, bonus(tour, Secrets.GRAND_TOUR))
        val repeated = result(bumps = listOf(BumpKind.ROCK, BumpKind.ROCK, BumpKind.DRIFTWOOD, BumpKind.JELLY), snags = listOf(snag))
        assertNull(bonus(repeated, Secrets.GRAND_TOUR))
    }

    @Test
    fun ladderNeedsFourAscentCatchesInStrictlyIncreasingWeight() {
        // Caught on the way up (deepest first): 0.2 → 0.4 → 0.9 → 1.6 kg.
        val ladder = listOf(catch("silver_sprat", 80f), catch("coral_darter", 60f), catch("reed_perch", 40f), catch("sun_bream", 35f))
        val r = result(ladder)
        assertEquals((r.basePoints * 0.5).let { Math.round(it).toInt() }, bonus(r, Secrets.LADDER))
        assertNull(bonus(result(ladder.reversed()), Secrets.LADDER), "decreasing")
        assertNull(bonus(result(ladder.take(3)), Secrets.LADDER), "too short")
        // The last-bump hook isn't part of the climb.
        val withHook = listOf(catch("pale_leviathan", 600f, byLastBump = true)) + ladder
        assertTrue(bonus(result(withHook), Secrets.LADDER) != null)
    }

    @Test
    fun zoneSweepNeedsAFishFromEveryZone() {
        val sweep = listOf(catch("star_drifter", 510f), catch("ghost_hake", 400f), catch("dusk_snapper", 200f), catch("silver_sprat", 50f))
        val r = result(sweep, maxDepth = 520f)
        assertEquals(r.basePoints, bonus(r, Secrets.ZONE_SWEEP))
        assertNull(bonus(result(sweep.drop(1)), Secrets.ZONE_SWEEP))
    }

    @Test
    fun davidAndGoliathNeedsTheLeviathanOnARopeTooWeakForIt() {
        val goliath = catch("pale_leviathan", 590f, byLastBump = true)
        val weak = result(listOf(goliath), maxDepth = 590f, bumps = List(4) { if (it == 3) BumpKind.FISH_HEAD else BumpKind.ROCK }, strength = 22f)
        assertEquals(goliath.points * 2, bonus(weak, Secrets.DAVID_AND_GOLIATH))
        val strong = weak.copy(lineStrengthKg = 26f)
        assertNull(bonus(strong, Secrets.DAVID_AND_GOLIATH), "a strong rope is no feat")
    }

    @Test
    fun bonusesAddUpAndCountInTheCastScore() {
        val sweep = listOf(catch("star_drifter", 510f), catch("ghost_hake", 400f), catch("dusk_snapper", 200f), catch("silver_sprat", 50f))
        val r = result(sweep, maxDepth = 520f)
        assertEquals(r.basePoints + r.secretBonuses.sumOf { it.points }, r.totalPoints)
        assertEquals(setOf(Secrets.ZONE_SWEEP, Secrets.CLEAN_DROP), Secrets.revealedBy(r), "it also reached a 520 m rope end with no bumps")
        // Battles score casts by totalPoints, so secret bonuses count there too.
        assertEquals(r.totalPoints, BattleCast(r, 30f).points)
    }

    @Test
    fun discoveryRewardIsGrantedOnceAndLegendaryUnlocksCosmetics() {
        var p = PlayerProgress()
        p = Progression.discoverSecret(p, Secrets.ZONE_SWEEP)
        val reward = SecretRewards.of(Secrets.ZONE_SWEEP)
        assertEquals(reward.points.toLong(), p.pointsBalance)
        assertEquals(reward.points.toLong(), p.pointsEarned)
        assertEquals(GameConfig.STARTING_LANTERNS + reward.lanterns, p.lanterns)
        assertEquals(p, Progression.discoverSecret(p, Secrets.ZONE_SWEEP), "only once")

        assertTrue(p.unlockedCosmetics.isEmpty())
        assertEquals(p, Progression.equipSkin(p, CosmeticSlot.HOOK, Cosmetic.LEVIATHAN_BONE_HOOK), "locked")
        p = Progression.discoverSecret(p, Secrets.DAVID_AND_GOLIATH)
        assertEquals(Cosmetic.entries.toSet(), p.unlockedCosmetics.toSet())
        assertTrue(p.equippedSkins.isEmpty(), "unlocking doesn't force a skin on")
        p = Progression.equipSkin(p, CosmeticSlot.HOOK, Cosmetic.LEVIATHAN_BONE_HOOK)
        assertEquals(Cosmetic.LEVIATHAN_BONE_HOOK, p.equippedSkins[CosmeticSlot.HOOK])
        assertEquals(p, Progression.equipSkin(p, CosmeticSlot.LINE, Cosmetic.LEVIATHAN_BONE_HOOK), "wrong slot")
        assertFalse(CosmeticSlot.HOOK in Progression.equipSkin(p, CosmeticSlot.HOOK, null).equippedSkins)

        val repo = ProgressRepository(InMemoryStore())
        repo.save(p)
        assertEquals(p, repo.load())
    }

    @Test
    fun everySecretHasARewardEntry() {
        for (id in Secrets.all) SecretRewards.of(id)
        assertTrue(Secrets.all.toSet().size == Secrets.all.size)
        assertTrue(SecretRewards.of(Secrets.DAVID_AND_GOLIATH).cosmetics.isNotEmpty())
    }
}
