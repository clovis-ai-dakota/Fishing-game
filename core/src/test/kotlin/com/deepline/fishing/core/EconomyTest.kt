package com.deepline.fishing.core

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertTrue

/** A good player's whole career: casts, milestones, upgrades, lanterns, the daily loop. */
object Career {
    data class Report(
        val castsToLevel: Map<Int, Int>,
        val castsToMaxAll: Int?,
        val earned: Long,
        val lastCastPoints: List<Int>,
        val lanternsBought: Int,
        val seconds: Float,
        val secondsToLevel: Map<Int, Float>,
        /** Average points per cast by Rope Length level (see GameConfig.POINTS_PER_CAST_BY_LENGTH_LEVEL). */
        val pointsPerCast: Map<Int, Int> = emptyMap(),
        /** Points from daily rewards and challenges, and how many challenges were claimed. */
        val dailyPoints: Long = 0,
        val challengesClaimed: Int = 0,
    )

    fun play(seed: Long, casts: Int, buyLanterns: Boolean = true, daily: Boolean = true): Report {
        var p = PlayerProgress()
        // Days are UTC days here, so the career is the same on every machine.
        LocalDays.zoneOverride = java.util.TimeZone.getTimeZone("UTC")
        val castPoints = HashMap<Int, MutableList<Int>>()
        var dailyPoints = 0L
        var challengesClaimed = 0
        val rnd = Random(seed)
        val castsToLevel = HashMap<Int, Int>()
        val secondsToLevel = HashMap<Int, Float>()
        var maxAll: Int? = null
        var lanternsBought = 0
        val recent = ArrayList<Int>()
        var seconds = 0f
        for (c in 1..casts) {
            // About three casts a day, so a long career spans several seasons (free track only).
            val now = Seasons.EPOCH_MS + c * 8 * 3_600_000L
            ShopClock.override = now
            val day = LocalDays.today()
            // The daily reward, claimed at the first session of each day.
            if (daily) {
                Daily.claimReward(p, day)?.let { dailyPoints += it.pointsBalance - p.pointsBalance; p = it }
            }
            val lantern = p.specs.lengthM > GameConfig.DARK_DEPTH && p.lanterns > 0
            if (lantern) p = Progression.consumeLantern(p) ?: p
            // Migrations (solo casts) make two species more common for two days a week.
            val boost = if (daily) Migrations.boostAt(now) else null
            val s = CastSession(p.specs, lantern, WorldGenerator.generate(p.specs.lengthM, rnd, boost = boost))
            val down = GoodPlayer(Random(rnd.nextLong()))
            val up = BotBrain(BotDifficulty.NORMAL, Random(rnd.nextLong()))
            var guard = 0
            while (s.phase != CastPhase.SURFACED && guard++ < 60_000) {
                val t = if (s.phase == CastPhase.DESCENDING) down.steer(s, 1f / 60f) else up.steer(s, 1f / 60f)
                s.update(1f / 60f, t)
                if (s.lineIsFull && s.canFastReel) s.startFastReel()
            }
            s.endNow()
            seconds += s.elapsedSeconds + 8f // + time on menus and the results panel
            val r = s.result!!
            castPoints.getOrPut(p.level(RopeStat.LENGTH)) { ArrayList() } += r.totalPoints
            // Finished challenges of an earlier day are collected by applyCast itself.
            val before = p.pointsBalance + r.totalPoints
            p = Progression.applyCast(p, r)
            if (daily) {
                dailyPoints += p.pointsBalance - before
                // The day's challenges come as they come (no rerolls, no tackle bought for them).
                for (d in ChallengeDifficulty.entries) {
                    Daily.claimChallenge(p, d, day)?.let { dailyPoints += it.pointsBalance - p.pointsBalance; challengesClaimed++; p = it }
                }
            } else {
                // Without the daily loop, challenges never pay.
                p = p.copy(pointsBalance = before, dailyChallenges = p.dailyChallenges.map { it.copy(claimed = true) })
            }
            for (id in Secrets.revealedBy(r) - p.discoveredSecrets) p = Progression.discoverSecret(p, id)
            for (m in Milestones.all) Progression.claimMilestone(p, m)?.let { p = it }
            p = Seasons.claimAll(p, now)
            // Spend: cheapest upgrade first (balanced rope), then a spare Lantern for the dark.
            while (true) {
                val stat = RopeStat.entries.filter { Progression.canUpgrade(p, it) }.minByOrNull { Progression.upgradeCost(p, it)!! } ?: break
                p = Progression.upgrade(p, stat)!!
            }
            if (buyLanterns && p.specs.lengthM > GameConfig.DARK_DEPTH && p.lanterns == 0 &&
                p.pointsBalance >= 3 * Progression.tacklePrice(p, Tackle.LANTERN)
            ) {
                Progression.buyLantern(p)?.let { p = it; lanternsBought++ }
            }
            val lvl = p.level(RopeStat.LENGTH)
            if (lvl !in castsToLevel) { castsToLevel[lvl] = c; secondsToLevel[lvl] = seconds }
            if (maxAll == null && RopeStat.entries.all { p.level(it) == it.maxLevel }) maxAll = c
            recent += r.totalPoints
            if (recent.size > 20) recent.removeAt(0)
        }
        ShopClock.override = null
        LocalDays.zoneOverride = null
        val perCast = castPoints.mapValues { (_, v) -> v.average().toInt() }
        return Report(castsToLevel, maxAll, p.pointsEarned, recent, lanternsBought, seconds, secondsToLevel, perCast, dailyPoints, challengesClaimed)
    }
}

/**
 * The pacing of a good player's career with the current prices (rope costs, tackle, milestones)
 * and player income (the season's free track, daily rewards and challenges, migrations): if a
 * change to scoring, prices or rewards moves these, re-tune [RopeStat.COST_CURVE],
 * [GameConfig.TACKLE_PRICE_BY_LENGTH_LEVEL] or the rewards ([DailyRewards], [Seasons.rewards]).
 */
class EconomyTest {
    @Test
    fun aGoodPlayersCareerHasTheIntendedPace() {
        // One career depends a lot on its luck (the water, migrations), so the pace is the median
        // of a few careers.
        val runs = (1L..5L).map { Career.play(seed = it, casts = 340) }
        for ((i, r) in runs.withIndex()) {
            println(
                "ECONOMY seed ${i + 1}: Midnight ${r.castsToLevel[4]}, Abyss ${r.castsToLevel[8]}, full rope ${r.castsToLevel[10]}, " +
                    "all maxed ${r.castsToMaxAll}, earned ${r.earned}, daily loop ${r.dailyPoints} (${r.challengesClaimed} challenges)",
            )
        }
        fun median(what: String, f: (Career.Report) -> Int?) = runs.map { f(it) ?: error("a career never reached $what") }.sorted()[runs.size / 2]
        val midnight = median("the Midnight Trench") { it.castsToLevel[4] }
        val abyss = median("the Abyssal Floor") { it.castsToLevel[8] }
        val full = median("a full rope") { it.castsToLevel[10] }
        println("ECONOMY median: Midnight $midnight, Abyss $abyss, full rope $full; points per cast by length level ${runs[1].pointsPerCast.toSortedMap()}")
        val solo = Career.play(seed = 2L, casts = 340, daily = false)
        println("ECONOMY seed 2 without the daily loop: Midnight ${solo.castsToLevel[4]}, Abyss ${solo.castsToLevel[8]}, full rope ${solo.castsToLevel[10]}")
        assertTrue(midnight in 15..45, "Midnight Trench after $midnight casts")
        assertTrue(abyss in 100..200, "Abyssal Floor after $abyss casts")
        assertTrue(full in 200..330, "full rope after $full casts")
        for (r in runs) {
            // The daily loop is a bonus on top of fishing, not the main income.
            assertTrue(r.dailyPoints < r.earned / 5, "daily loop paid ${r.dailyPoints} of ${r.earned}")
            assertTrue(r.challengesClaimed > 0)
            // Points per cast stay close to the table daily rewards and challenge targets are sized by.
            for ((level, measured) in r.pointsPerCast) {
                val table = GameConfig.POINTS_PER_CAST_BY_LENGTH_LEVEL[level]
                assertTrue(measured in table / 2..table * 2, "points per cast at length $level: measured $measured, table $table")
            }
        }
        // Tackle keeps costing a good share of a cast at every stage.
        for (level in 4..10) {
            val price = Tackle.LANTERN.price(level)
            assertTrue(price >= GameConfig.TACKLE_PRICE_BY_LENGTH_LEVEL[level] / 3, "Lantern at level $level costs $price")
        }
    }
}
