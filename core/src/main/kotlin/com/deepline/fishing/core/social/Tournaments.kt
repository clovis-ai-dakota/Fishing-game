package com.deepline.fishing.core.social

import com.deepline.fishing.core.BattleCast
import com.deepline.fishing.core.BattleTurn
import com.deepline.fishing.core.GameConfig
import com.deepline.fishing.core.PlayerProgress
import com.deepline.fishing.core.RopeSpecs
import com.deepline.fishing.core.SeasonGrant
import com.deepline.fishing.core.Seasons
import com.deepline.fishing.core.Tackle
import com.deepline.fishing.core.TitleFrame
import com.deepline.fishing.core.WorldGenerator
import com.deepline.fishing.core.online.CastDto
import com.deepline.fishing.core.online.CastValidation
import com.deepline.fishing.core.online.OnlineProtocol
import kotlin.math.abs
import kotlin.random.Random

/**
 * Where a player finished a weekly tournament, and what it gives. Bands are by final rank out of
 * everyone who took part: a band holds the ranks whose `(rank - 1) / participants` is below its
 * [topFraction], so the winner is always a [CHAMPION]. Rewards are small on purpose (well under a
 * weekend event's): the tournament is for bragging rights, not income, so the economy's pacing
 * (`EconomyTest`) doesn't change for players who enter it.
 */
enum class TournamentTier(val displayName: String, val topFraction: Double, val grants: List<SeasonGrant>) {
    CHAMPION(
        "Champion", 0.01,
        listOf(SeasonGrant.Points(5), SeasonGrant.Tackle(Tackle.LANTERN, 3), SeasonGrant.Unlock(TitleFrame.TOURNAMENT.unlockKey!!)),
    ),
    GOLD(
        "Top 10%", 0.10,
        listOf(SeasonGrant.Points(3), SeasonGrant.Tackle(Tackle.LANTERN, 2), SeasonGrant.Unlock(TitleFrame.TOURNAMENT.unlockKey!!)),
    ),
    SILVER("Top 25%", 0.25, listOf(SeasonGrant.Points(2), SeasonGrant.Tackle(Tackle.LANTERN, 1), SeasonGrant.Tackle(Tackle.DETECTOR, 1))),
    BRONZE("Top 50%", 0.50, listOf(SeasonGrant.Points(1), SeasonGrant.Tackle(Tackle.LANTERN, 1))),
    TOOK_PART("Took part", 1.0, listOf(SeasonGrant.Tackle(Tackle.DETECTOR, 1))),
}

/**
 * The weekly tournament: an asynchronous competition on **the same water for everyone**. Each
 * week (see [Weeks]) has one seed; every attempt of every player fishes the water it generates,
 * with the online rules (the standard tournament rope, [GameConfig.DESCENT_TOUCH_LIMIT] bumps, no
 * Lucky charm or Sparkle lure; Lanterns and detectors allowed, as in battles). A player has
 * [GameConfig.TOURNAMENT_ATTEMPTS] attempts a week; starting one uses it (quitting it scores 0),
 * and their best score counts. The server validates every attempt like a battle cast and also
 * checks every catch against the week's water ([WaterCheck]), then recomputes the points.
 * Rewards go by final rank once the week is over ([TournamentTier]).
 */
object Tournaments {
    /** Everyone fishes with the online battles' standard rope. */
    val specs: RopeSpecs get() = OnlineProtocol.tournamentSpecs

    /**
     * The week's water. [salt] is the server's secret (`DEEPLINE_TOURNAMENT_SALT`): with it the
     * water can't be worked out before the week, and the server only reveals the seed when a
     * player starts their first attempt.
     */
    fun seedOf(week: Long, salt: Long = 0L): Long {
        // SplitMix64: neighbouring weeks get unrelated water.
        var z = week * -0x61c8864680b583ebL + salt + 0x1F2E3D4C5B6A7988L
        z = (z xor (z ushr 30)) * -0x40a7b892e31b1a47L
        z = (z xor (z ushr 27)) * -0x6b2fb644ecceee15L
        return z xor (z ushr 31)
    }

    /** The turn a tournament attempt is validated as (one player, no clock). */
    fun turnOf(attempt: Int, seed: Long): BattleTurn = BattleTurn(playerIndex = 0, castNumber = attempt, round = 0, seed = seed, timeLimitSec = null)

    /**
     * Rebuilds an attempt from what the app reported, or null if it's impossible: the battle
     * rules ([CastValidation]) and every catch and snag must exist in the week's water. Points are
     * always recomputed.
     */
    fun validate(dto: CastDto, attempt: Int, seed: Long): BattleCast? {
        val cast = CastValidation.toBattleCast(dto, turnOf(attempt, seed), specs) ?: return null
        if (!WaterCheck.matches(dto, seed, specs)) return null
        return cast
    }

    /** The band for finishing [rank] (1 = first) of [participants]; null for a rank that can't be. */
    fun tierFor(rank: Int, participants: Int): TournamentTier? {
        if (participants < 1 || rank < 1 || rank > participants) return null
        val fraction = (rank - 1).toDouble() / participants
        return TournamentTier.entries.first { fraction < it.topFraction }
    }

    /** Whether the reward of [week] can still be claimed at [now] (the week is over, not too long ago). */
    fun isClaimable(week: Long, now: Long): Boolean {
        val current = Weeks.indexAt(now)
        return week < current && current - week <= GameConfig.TOURNAMENT_CLAIM_WEEKS
    }

    /**
     * Gives the reward for finishing [week] in [tier]; null if it was already given (the app keeps
     * the weeks it granted, so a reward is never given twice on one phone).
     */
    fun grant(p: PlayerProgress, week: Long, tier: TournamentTier, now: Long): PlayerProgress? {
        if (week in p.tournamentClaims) return null
        val claims = (p.tournamentClaims + week).sorted().takeLast(MAX_REMEMBERED_CLAIMS).toSet()
        return Seasons.give(p, tier.grants, now).copy(tournamentClaims = claims)
    }

    /** Claimed weeks kept in the save (older weeks can't be claimed any more anyway). */
    const val MAX_REMEMBERED_CLAIMS = 12
}

/**
 * Checks a reported cast against the water a seed generates: every fish caught must be a distinct
 * fish of that species, Sparkling or not, at its exact depth (fish swim sideways at a fixed
 * depth), and every snag a distinct object of that kind at its depth. A modified app can still
 * choose which of the water's fish it claims, but it can't invent fish that aren't there.
 */
object WaterCheck {
    private const val DEPTH_EPSILON = 0.01f

    fun matches(dto: CastDto, seed: Long, specs: RopeSpecs): Boolean {
        val world = WorldGenerator.generate(specs.lengthM, Random(seed))
        val usedFish = HashSet<Int>()
        for (c in dto.catches) {
            val fish = world.fish.firstOrNull {
                it.id !in usedFish && it.species.id == c.speciesId && it.sparkling == c.sparkling && abs(it.depth - c.depth) <= DEPTH_EPSILON
            } ?: return false
            usedFish += fish.id
        }
        val usedObstacles = HashSet<Int>()
        for (s in dto.snags) {
            val obstacle = world.obstacles.firstOrNull { it.id !in usedObstacles && it.kind == s.kind && abs(it.depth - s.depth) <= DEPTH_EPSILON }
                ?: return false
            usedObstacles += obstacle.id
        }
        return true
    }
}
