package com.deepline.fishing.core

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertTrue

/** A good player's whole career: casts, milestones, upgrades, lanterns. */
object Career {
    data class Report(val castsToLevel: Map<Int, Int>, val castsToMaxAll: Int?, val earned: Long, val lastCastPoints: List<Int>, val lanternsBought: Int, val seconds: Float, val secondsToLevel: Map<Int, Float>)

    fun play(seed: Long, casts: Int, buyLanterns: Boolean = true): Report {
        var p = PlayerProgress()
        val rnd = Random(seed)
        val castsToLevel = HashMap<Int, Int>()
        val secondsToLevel = HashMap<Int, Float>()
        var maxAll: Int? = null
        var lanternsBought = 0
        val recent = ArrayList<Int>()
        var seconds = 0f
        for (c in 1..casts) {
            val lantern = p.specs.lengthM > GameConfig.DARK_DEPTH && p.lanterns > 0
            if (lantern) p = Progression.consumeLantern(p) ?: p
            val s = CastSession(p.specs, lantern, WorldGenerator.generate(p.specs.lengthM, rnd))
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
            p = Progression.applyCast(p, r)
            for (id in Secrets.revealedBy(r) - p.discoveredSecrets) p = Progression.discoverSecret(p, id)
            for (m in Milestones.all) Progression.claimMilestone(p, m)?.let { p = it }
            // Spend: cheapest upgrade first (balanced rope), then a spare Lantern for the dark.
            while (true) {
                val stat = RopeStat.entries.filter { Progression.canUpgrade(p, it) }.minByOrNull { Progression.upgradeCost(p, it)!! } ?: break
                p = Progression.upgrade(p, stat)!!
            }
            if (buyLanterns && p.specs.lengthM > GameConfig.DARK_DEPTH && p.lanterns == 0) {
                Progression.buyLantern(p)?.let { p = it; lanternsBought++ }
            }
            val lvl = p.level(RopeStat.LENGTH)
            if (lvl !in castsToLevel) { castsToLevel[lvl] = c; secondsToLevel[lvl] = seconds }
            if (maxAll == null && RopeStat.entries.all { p.level(it) == it.maxLevel }) maxAll = c
            recent += r.totalPoints
            if (recent.size > 20) recent.removeAt(0)
        }
        return Report(castsToLevel, maxAll, p.pointsEarned, recent, lanternsBought, seconds, secondsToLevel)
    }
}

/**
 * The pacing of a good player's career with the current prices (rope costs, tackle, milestones):
 * if a change to scoring or prices moves these, re-tune [RopeStat.COST_CURVE] and
 * [GameConfig.TACKLE_PRICE_BY_LENGTH_LEVEL].
 */
class EconomyTest {
    @Test
    fun aGoodPlayersCareerHasTheIntendedPace() {
        val r = Career.play(seed = 2L, casts = 320)
        println("ECONOMY casts to Midnight ${r.castsToLevel[4]}, Abyss ${r.castsToLevel[8]}, full rope ${r.castsToLevel[10]}, all maxed ${r.castsToMaxAll}, earned ${r.earned}")
        val midnight = r.castsToLevel[4] ?: error("never reached the Midnight Trench")
        val abyss = r.castsToLevel[8] ?: error("never reached the Abyssal Floor")
        val full = r.castsToLevel[10] ?: error("never maxed the rope's length")
        assertTrue(midnight in 15..45, "Midnight Trench after $midnight casts")
        assertTrue(abyss in 100..200, "Abyssal Floor after $abyss casts")
        assertTrue(full in 200..330, "full rope after $full casts")
        // Tackle keeps costing a good share of a cast at every stage.
        for (level in 4..10) {
            val price = Tackle.LANTERN.price(level)
            assertTrue(price >= GameConfig.TACKLE_PRICE_BY_LENGTH_LEVEL[level] / 3, "Lantern at level $level costs $price")
        }
    }
}
