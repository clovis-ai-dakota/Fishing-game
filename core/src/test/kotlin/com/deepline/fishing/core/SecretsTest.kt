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

    private fun fish(id: String, depth: Float, at: Float = Float.NaN, sparkling: Boolean = false, fleeing: Boolean = false, wall: Boolean = false, bumped: Boolean = fleeing, lastBump: Boolean = false) =
        CatchRecord(sp(id), sparkling, depth, Scoring.points(sp(id), depth, sparkling), lastBump, bumped, at, fleeing, wall)

    @Test
    fun tierOneSecrets() {
        assertEquals(SecretRules.FREE_PASS_BONUS, bonus(result().copy(freePasses = 3), Secrets.FREE_PASS))
        assertNull(bonus(result().copy(freePasses = 2), Secrets.FREE_PASS))
        assertEquals(SecretRules.SURFACE_SKIM_BONUS, bonus(result(listOf(catch("silver_sprat", 8f))), Secrets.SURFACE_SKIM))
        assertNull(bonus(result(listOf(catch("silver_sprat", 12f))), Secrets.SURFACE_SKIM))
        val three = List(3) { catch("coral_darter", 30f + it) }
        assertEquals(SecretRules.ZEN_CAST_BONUS, bonus(result(three).copy(steered = false), Secrets.ZEN_CAST))
        assertNull(bonus(result(three), Secrets.ZEN_CAST), "steered")
    }

    @Test
    fun tierTwoSecrets() {
        val fourBumps = List(4) { BumpKind.ROCK }
        assertEquals(SecretRules.PINBALL_BONUS, bonus(result(bumps = fourBumps).copy(bumpTimes = listOf(3f, 3.5f, 4.2f, 4.9f)), Secrets.PINBALL))
        assertNull(bonus(result(bumps = fourBumps).copy(bumpTimes = listOf(3f, 3.5f, 4.2f, 5.5f)), Secrets.PINBALL))

        val snag = SnagRecord(ObstacleKind.ROCK, 120f, Scoring.snagPoints(ObstacleKind.ROCK, 120f))
        val bottom = result(maxDepth = 118.5f, bumps = fourBumps, snags = listOf(snag)).copy(ropeLengthM = 120f)
        assertEquals(snag.points, bonus(bottom, Secrets.ROCK_BOTTOM))
        assertNull(bonus(bottom.copy(maxDepth = 110f), Secrets.ROCK_BOTTOM))

        val late = result(listOf(catch("silver_sprat", 40f))).copy(timeLeftAtStart = 12f)
        assertEquals(SecretRules.BUZZER_BEATER_BONUS, bonus(late, Secrets.BUZZER_BEATER))
        assertNull(bonus(late.copy(timeLeftAtStart = 20f), Secrets.BUZZER_BEATER))
        assertNull(bonus(late.copy(timeLeftAtStart = null), Secrets.BUZZER_BEATER), "not a timed battle")
    }

    @Test
    fun tierThreeSecrets() {
        assertEquals(SecretRules.CORNER_POCKET_BONUS, bonus(result(listOf(fish("dusk_snapper", 150f, wall = true))), Secrets.CORNER_POCKET))
        val shiny = fish("dusk_snapper", 150f, sparkling = true, lastBump = true)
        assertEquals(shiny.points, bonus(result(listOf(shiny), bumps = List(4) { BumpKind.FISH_HEAD }), Secrets.SPARKLE_SNAG))

        // 0.2 + 0.4 + 0.9 + 0.2 + 1.6 = 3.3 kg on a 3.3 kg line, filled by a 1.6 kg fish.
        val load = listOf(catch("silver_sprat", 80f), catch("coral_darter", 60f), catch("reed_perch", 40f), catch("silver_sprat", 38f), catch("sun_bream", 35f))
        val exact = result(load, strength = 3.3f)
        assertEquals(Math.round(exact.basePoints * 0.5).toInt(), bonus(exact, Secrets.PERFECT_LOAD))
        assertNull(bonus(result(load, strength = 3.4f), Secrets.PERFECT_LOAD), "0.1 kg short")
        assertNull(bonus(result(load.take(4) + catch("sun_bream", 35f), strength = 3.1f).copy(catches = load.take(4)), Secrets.PERFECT_LOAD), "too few catches")
        val toppedOff = listOf(catch("sun_bream", 80f)) + load.take(4)
        assertNull(bonus(result(toppedOff, strength = 3.3f), Secrets.PERFECT_LOAD), "filled by a tiny fish")

        val angler = catch("glowfin_angler", 490f)
        assertEquals(angler.points * 2, bonus(result(listOf(angler)), Secrets.MOONLIT_ANGLER))
        assertNull(bonus(result(listOf(angler)).copy(lanternUsed = true), Secrets.MOONLIT_ANGLER), "with a Lantern")
    }

    @Test
    fun tierFourSecrets() {
        val fleeing = fish("dusk_snapper", 150f, fleeing = true)
        assertEquals(fleeing.points * 3, bonus(result(listOf(fleeing), bumps = listOf(BumpKind.FISH_HEAD)), Secrets.WHILE_IT_FLEES))

        val deep = listOf(catch("ghost_hake", 400f))
        val blind = result(deep, maxDepth = 520f)
        assertEquals(blind.basePoints, bonus(blind, Secrets.BLIND_DROP))
        assertNull(bonus(blind.copy(lanternUsed = true), Secrets.BLIND_DROP))
        assertNull(bonus(result(deep, maxDepth = 450f), Secrets.BLIND_DROP))

        val snag = SnagRecord(ObstacleKind.JELLY, 200f, Scoring.snagPoints(ObstacleKind.JELLY, 200f))
        val slam = result(List(8) { catch("silver_sprat", 50f - it) }, bumps = List(4) { BumpKind.JELLY }, snags = listOf(snag))
        assertTrue(bonus(slam, Secrets.GRAND_SLAM) != null)
        assertNull(bonus(slam.copy(catches = slam.catches.take(7)), Secrets.GRAND_SLAM))

        val pair = listOf(fish("silver_sprat", 30f, at = 10f), fish("coral_darter", 30f, at = 10f), fish("reed_perch", 20f, at = 11f))
        assertEquals((pair[0].points + pair[1].points) * 2, bonus(result(pair), Secrets.DOUBLE_HEADER))
    }

    @Test
    fun perfectCastCombinesRockBottomPerfectLoadAndASparklingCatch() {
        val snag = SnagRecord(ObstacleKind.ROCK, 120f, Scoring.snagPoints(ObstacleKind.ROCK, 120f)) // 4 kg
        val shiny = fish("silver_sprat", 60f, sparkling = true) // 0.2 kg
        val others = List(3) { catch("silver_sprat", 50f - it) } + catch("sun_bream", 30f) // 0.6 + 1.6 kg, filled by the bream
        val r = result(listOf(shiny) + others, maxDepth = 119f, bumps = List(4) { BumpKind.ROCK }, snags = listOf(snag), strength = 6.4f)
            .copy(ropeLengthM = 120f)
        assertEquals(r.basePoints * 2, bonus(r, Secrets.PERFECT_CAST))
        assertNull(bonus(r.copy(lineStrengthKg = 6f), Secrets.PERFECT_CAST), "load not exact")
    }

    @Test
    fun shutoutAndMasterAngler() {
        val players = listOf(BattlePlayer("You"), BattlePlayer("Rival", BotDifficulty.HARD))
        val b = Battle(BattleMode.BEST_OF_FIVE, players, 1L)
        fun cast(points: Int) = BattleCast(CastResult(listOf(CatchRecord(sp("silver_sprat"), false, 10f, points)), 10f, 0, null, false), 10f)
        repeat(3) { b.record(b.nextTurn()!!, cast(100)); b.record(b.nextTurn()!!, cast(10)) }
        assertEquals(setOf(Secrets.SHUTOUT), Secrets.revealedByBattle(b, b.outcome()!!, 0, BotDifficulty.HARD))
        assertTrue(Secrets.revealedByBattle(b, b.outcome()!!, 0, BotDifficulty.NORMAL).isEmpty(), "only against Hard")

        val almost = PlayerProgress(collection = FishCatalog.all.drop(1).associate { it.id to CollectionEntry(caught = 1) })
        assertTrue(Secrets.revealedByProgress(almost).isEmpty())
        val all = almost.copy(collection = almost.collection + (FishCatalog.all.first().id to CollectionEntry(caught = 1)))
        assertEquals(setOf(Secrets.MASTER_ANGLER), Secrets.revealedByProgress(all))
    }

    @Test
    fun theCatalogHas200SpeciesAndOneLegendary() {
        assertEquals(200, FishCatalog.all.size)
        assertEquals(200, FishCatalog.all.map { it.id }.toSet().size)
        assertEquals(listOf(FishCatalog.sovereign), FishCatalog.all.filter { it.lastBumpOnly })
        assertEquals(FishCatalog.sovereign, FishCatalog.all.last(), "the 200th")
        assertTrue(FishCatalog.all.all { it.minDepth <= RopeStat.LENGTH.valueAt(RopeStat.LENGTH.maxLevel) }, "every fish is reachable")
        for (id in listOf("silver_sprat", "pale_leviathan", "glowfin_angler")) assertTrue(FishCatalog.byId(id) != null, "hand-made ids kept")
    }

    @Test
    fun ropeDowngradeRefundsWhatTheLevelCost() {
        var p = PlayerProgress(pointsBalance = 5_000, pointsEarned = 9_000)
        p = Progression.upgrade(p, RopeStat.STRENGTH)!!
        p = Progression.upgrade(p, RopeStat.STRENGTH)!!
        val afterUpgrades = p.pointsBalance
        val refund = Progression.downgradeRefund(p, RopeStat.STRENGTH)!!
        assertEquals(RopeStat.STRENGTH.costToUpgradeFrom(1), refund)
        p = Progression.downgrade(p, RopeStat.STRENGTH)!!
        assertEquals(1, p.level(RopeStat.STRENGTH))
        assertEquals(afterUpgrades + refund, p.pointsBalance)
        assertEquals(9_000L, p.pointsEarned, "milestone progress unchanged")
        p = Progression.downgrade(p, RopeStat.STRENGTH)!!
        assertEquals(5_000L, p.pointsBalance, "every point back")
        assertNull(Progression.downgrade(p, RopeStat.STRENGTH), "nothing below level 0")
    }

    @Test
    fun everyLegendaryUnlocksAFullSetIncludingATitle() {
        for (id in listOf(Secrets.DAVID_AND_GOLIATH, Secrets.PERFECT_CAST, Secrets.MASTER_ANGLER)) {
            val slots = SecretRewards.of(id).cosmetics.map { it.slot }.toSet()
            assertEquals(CosmeticSlot.entries.toSet(), slots, id)
        }
    }

    @Test
    fun bonusesAddUpAndCountInTheCastScore() {
        val sweep = listOf(catch("star_drifter", 510f), catch("ghost_hake", 400f), catch("dusk_snapper", 200f), catch("silver_sprat", 50f))
        val r = result(sweep, maxDepth = 520f)
        assertEquals(r.basePoints + r.secretBonuses.sumOf { it.points }, r.totalPoints)
        // It also reached a 520 m rope end with no bumps and no Lantern: Clean drop and Blind drop.
        assertEquals(setOf(Secrets.ZONE_SWEEP, Secrets.CLEAN_DROP, Secrets.BLIND_DROP), Secrets.revealedBy(r))
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
        assertEquals(SecretRewards.of(Secrets.DAVID_AND_GOLIATH).cosmetics.toSet(), p.unlockedCosmetics.toSet())
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
