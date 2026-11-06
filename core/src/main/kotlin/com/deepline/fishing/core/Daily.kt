package com.deepline.fishing.core

import java.util.TimeZone
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * The daily loop: a login streak with a 7-day reward calendar, and three daily challenges.
 * (Migrations are in `Migrations.kt`, reminders in `Reminders.kt`.) See docs/research/daily-loop.md.
 *
 * Days are the player's **local calendar days** ([LocalDays]): the daily reward and the challenges
 * reset at local midnight. Everything here is solo and only ever moves forwards: a day earlier
 * than the last one claimed (the clock set back, or a flight west) can't claim anything again.
 *
 * Forgiving by design: the calendar never goes backwards (a missed day only pauses it), streak
 * shields cover missed days, and challenges finished but not claimed are collected when the day
 * changes. Numbers are in [GameConfig] and the calendar table in [DailyRewards]; tuned with
 * EconomyTest (a good player claims them about three casts a day).
 */
object LocalDays {
    const val DAY_MS = 86_400_000L
    const val HOUR_MS = 3_600_000L

    /** Pins the time zone (tests and the career simulation); null: the phone's. */
    @Volatile
    var zoneOverride: TimeZone? = null

    /** The local time zone's offset from UTC at [now] (daylight saving included). */
    fun offsetMs(now: Long): Int = (zoneOverride ?: TimeZone.getDefault()).getOffset(now)

    /** The local day number at [now]: days since 1 January 1970, in local time. */
    fun dayOf(now: Long, offsetMs: Int = offsetMs(now)): Long = Math.floorDiv(now + offsetMs, DAY_MS)

    /** The local hour (0 to 23) at [now]. */
    fun hourOf(now: Long, offsetMs: Int = offsetMs(now)): Int = (Math.floorMod(now + offsetMs, DAY_MS) / HOUR_MS).toInt()

    /** Time left until local midnight (when the daily reward and challenges reset). */
    fun msUntilTomorrow(now: Long, offsetMs: Int = offsetMs(now)): Long = DAY_MS - Math.floorMod(now + offsetMs, DAY_MS)

    /** Today, by the shop clock in the phone's time zone. */
    fun today(): Long = dayOf(ShopClock.now())
}

/** Something the daily loop gives. */
sealed interface DailyGrant {
    /**
     * Points worth this many casts of a good player at the rope's reach
     * ([GameConfig.POINTS_PER_CAST_BY_LENGTH_LEVEL]). Spendable; like other bonus points they
     * don't count towards milestones.
     */
    data class Points(val casts: Float) : DailyGrant

    data class Tackle(val tackle: com.deepline.fishing.core.Tackle, val count: Int) : DailyGrant

    /** A streak shield, while the player holds fewer than [GameConfig.MAX_STREAK_SHIELDS]. */
    data object Shield : DailyGrant
}

object DailyRewards {
    /**
     * The reward calendar, one entry per day; day 7 is the big one, then it starts over. The
     * calendar moves on with each claim, never with missed days. Tuned with EconomyTest.
     */
    val calendar: List<List<DailyGrant>> = listOf(
        listOf(DailyGrant.Points(0.2f)),
        listOf(DailyGrant.Tackle(Tackle.DETECTOR, 1)),
        listOf(DailyGrant.Points(0.3f)),
        listOf(DailyGrant.Tackle(Tackle.LUCKY_CHARM, 1)),
        listOf(DailyGrant.Points(0.4f)),
        listOf(DailyGrant.Tackle(Tackle.SPARKLE_LURE, 1)),
        listOf(DailyGrant.Points(0.6f), DailyGrant.Tackle(Tackle.LANTERN, 1), DailyGrant.Shield),
    )

    /**
     * What a challenge pays, by difficulty: tackle to try (a detector, a Sparkle lure, a Lucky
     * charm) and points ([GameConfig.CHALLENGE_REWARD_CASTS]).
     */
    fun challengeReward(difficulty: ChallengeDifficulty): List<DailyGrant> {
        val tackle = when (difficulty) {
            ChallengeDifficulty.EASY -> Tackle.DETECTOR
            ChallengeDifficulty.MEDIUM -> Tackle.SPARKLE_LURE
            ChallengeDifficulty.HARD -> Tackle.LUCKY_CHARM
        }
        val casts = GameConfig.CHALLENGE_REWARD_CASTS[difficulty.ordinal]
        return listOfNotNull(DailyGrant.Points(casts).takeIf { casts > 0f }, DailyGrant.Tackle(tackle, 1))
    }

    /** About the points a good player earns per cast with this rope. */
    fun pointsPerCast(p: PlayerProgress): Int =
        GameConfig.POINTS_PER_CAST_BY_LENGTH_LEVEL[p.level(RopeStat.LENGTH).coerceIn(0, GameConfig.POINTS_PER_CAST_BY_LENGTH_LEVEL.size - 1)]

    fun pointsFor(p: PlayerProgress, casts: Float): Long = ShopCatalog.niceRound(casts.toDouble() * pointsPerCast(p))

    fun give(p: PlayerProgress, grants: List<DailyGrant>): PlayerProgress = grants.fold(p) { q, g ->
        when (g) {
            is DailyGrant.Points -> q.copy(pointsBalance = q.pointsBalance + pointsFor(q, g.casts))
            is DailyGrant.Tackle -> Progression.withTackle(q, g.tackle, Progression.tackleCount(q, g.tackle) + g.count)
            DailyGrant.Shield -> q.copy(streakShields = (q.streakShields + 1).coerceAtMost(GameConfig.MAX_STREAK_SHIELDS))
        }
    }
}

/** Where the login streak stands on a given day (for the Daily card and reminders). */
data class StreakStatus(
    /** The streak as it stands today: 0 when it would start over at the next claim. */
    val streak: Int,
    val bestStreak: Int,
    val shields: Int,
    val claimedToday: Boolean,
    val canClaim: Boolean,
    /** The calendar day (1 to 7) the next claim gives. */
    val nextCalendarDay: Int,
    /** Days missed since the last claim, which the next claim's shields must cover. */
    val missedDays: Int,
    /** Claimed yesterday but not yet today: tomorrow it would need a shield, or start over. */
    val atRisk: Boolean,
)

enum class ChallengeDifficulty(val label: String) { EASY("Easy"), MEDIUM("Medium"), HARD("Hard") }

enum class ChallengeKind {
    /** Cast [DailyChallenge.target] times. */
    CASTS,
    CATCH_FISH,
    /** Catch fish of the species living in [DailyChallenge.zone]. */
    CATCH_ZONE,
    /** Reach [DailyChallenge.target] meters in one cast (progress: the deepest so far). */
    REACH_DEPTH,
    /** Catch the species [DailyChallenge.speciesId] (one the player has seen). */
    CATCH_SPECIES,
    EARN_POINTS,
    /** Land this many kilograms of fish. */
    LAND_WEIGHT,
    SPARKLING,
    /** Land [DailyChallenge.target] fish in a single cast (progress: the best cast so far). */
    BIG_CAST,
    /** Catch species never caught before. */
    NEW_SPECIES,
}

/** One of the day's challenges: one per [ChallengeDifficulty]. */
data class DailyChallenge(
    val difficulty: ChallengeDifficulty,
    val kind: ChallengeKind,
    val target: Int,
    val zone: DepthZone? = null,
    val speciesId: String? = null,
    val progress: Int = 0,
    val claimed: Boolean = false,
) {
    val done: Boolean get() = progress >= target
    val claimable: Boolean get() = done && !claimed
    val species: FishSpecies? get() = speciesId?.let(FishCatalog::byId)
}

object Daily {
    // ---- Login streak and reward calendar -------------------------------------------------------

    fun status(p: PlayerProgress, day: Long): StreakStatus {
        val last = p.dailyLastClaimDay
        val gap = if (last == null) 0L else day - last
        val missed = (gap - 1).coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()
        val broken = last == null || missed > p.streakShields
        return StreakStatus(
            streak = if (broken) 0 else p.dailyStreak,
            bestStreak = p.dailyBestStreak,
            shields = p.streakShields,
            claimedToday = last != null && gap <= 0,
            canClaim = last == null || gap > 0,
            nextCalendarDay = p.dailyClaims % DailyRewards.calendar.size + 1,
            missedDays = if (last == null) 0 else missed,
            atRisk = last != null && gap == 1L && p.dailyStreak > 0,
        )
    }

    /** What the next claim gives (the calendar's next day). */
    fun nextReward(p: PlayerProgress): List<DailyGrant> = DailyRewards.calendar[p.dailyClaims % DailyRewards.calendar.size]

    /**
     * Claims the daily reward for [day], or null when it was already claimed that day (or on a
     * later day: a clock set back never claims twice). Missed days use up streak shields; without
     * enough, the streak starts over at 1, and the calendar simply carries on.
     */
    fun claimReward(p: PlayerProgress, day: Long): PlayerProgress? {
        val s = status(p, day)
        if (!s.canClaim) return null
        val shieldsUsed = if (s.streak > 0) s.missedDays else 0
        val streak = s.streak + 1
        val q = p.copy(
            dailyStreak = streak,
            dailyBestStreak = max(p.dailyBestStreak, streak),
            dailyLastClaimDay = day,
            dailyClaims = p.dailyClaims + 1,
            streakShields = p.streakShields - shieldsUsed,
        )
        return DailyRewards.give(q, nextReward(p))
    }

    // ---- Daily challenges ----------------------------------------------------------------------

    /** What changing day did: the new progress and the finished challenges collected for the player. */
    data class Rollover(val progress: PlayerProgress, val collected: List<DailyChallenge>)

    /**
     * Moves the challenges to [day]: the old day's finished but unclaimed challenges are
     * collected (their rewards given), then the new day's are made. Nothing changes on the same
     * day, or when [day] is earlier than the challenges' day (the clock was set back).
     */
    fun rollover(p: PlayerProgress, day: Long): Rollover {
        val current = p.challengeDay
        if (current != null && day <= current && p.dailyChallenges.isNotEmpty()) return Rollover(p, emptyList())
        val collected = p.dailyChallenges.filter { it.claimable }
        val q = collected.fold(p) { acc, c -> DailyRewards.give(acc, DailyRewards.challengeReward(c.difficulty)) }
        return Rollover(q.copy(challengeDay = day, challengeRerolls = 0, dailyChallenges = generateDay(q, day)), collected)
    }

    fun refresh(p: PlayerProgress, day: Long): PlayerProgress = rollover(p, day).progress

    /** Adds a solo cast to the day's challenges ([p] is the progress before the cast). */
    fun onCast(p: PlayerProgress, r: CastResult, day: Long): PlayerProgress {
        val q = refresh(p, day)
        return q.copy(dailyChallenges = q.dailyChallenges.map { c -> if (c.claimed) c else c.copy(progress = progressAfter(c, r, p)) })
    }

    /** Claims the finished challenge of [difficulty]; null when there's nothing to claim. */
    fun claimChallenge(p: PlayerProgress, difficulty: ChallengeDifficulty, day: Long): PlayerProgress? {
        val q = refresh(p, day)
        val c = q.dailyChallenges.firstOrNull { it.difficulty == difficulty }?.takeIf { it.claimable } ?: return null
        return DailyRewards.give(q, DailyRewards.challengeReward(difficulty))
            .copy(dailyChallenges = q.dailyChallenges.map { if (it.difficulty == difficulty) it.copy(claimed = true) else it })
    }

    fun rerollsLeft(p: PlayerProgress, day: Long): Int =
        (GameConfig.DAILY_FREE_REROLLS - refresh(p, day).challengeRerolls).coerceAtLeast(0)

    fun canReroll(p: PlayerProgress, difficulty: ChallengeDifficulty, day: Long): Boolean {
        val q = refresh(p, day)
        val c = q.dailyChallenges.firstOrNull { it.difficulty == difficulty } ?: return false
        return rerollsLeft(q, day) > 0 && !c.done && !c.claimed
    }

    /**
     * Swaps the unfinished challenge of [difficulty] for another of the same difficulty and a
     * different kind (progress on it is lost). Uses one of the day's free rerolls.
     */
    fun reroll(p: PlayerProgress, difficulty: ChallengeDifficulty, day: Long): PlayerProgress? {
        if (!canReroll(p, difficulty, day)) return null
        val q = refresh(p, day)
        val old = q.dailyChallenges.first { it.difficulty == difficulty }
        val n = q.challengeRerolls + 1
        val others = q.dailyChallenges.filter { it.difficulty != difficulty }.map { it.kind }.toSet()
        val fresh = generate(q, day, difficulty, n, avoid = others + old.kind) ?: return null
        return q.copy(challengeRerolls = n, dailyChallenges = q.dailyChallenges.map { if (it.difficulty == difficulty) fresh else it })
    }

    /** The day's three challenges, one per difficulty, each of a different kind. */
    fun generateDay(p: PlayerProgress, day: Long): List<DailyChallenge> {
        val made = ArrayList<DailyChallenge>()
        for (d in ChallengeDifficulty.entries) {
            generate(p, day, d, reroll = 0, avoid = made.map { it.kind }.toSet())?.let { made += it }
        }
        return made
    }

    private fun generate(p: PlayerProgress, day: Long, difficulty: ChallengeDifficulty, reroll: Int, avoid: Set<ChallengeKind>): DailyChallenge? {
        val rnd = Random(day * 1_000_003L + difficulty.ordinal * 7_919L + reroll * 104_729L)
        val all = candidates(p, difficulty, rnd)
        val options = all.filter { it.kind !in avoid }.ifEmpty { all }
        return if (options.isEmpty()) null else options[rnd.nextInt(options.size)]
    }

    /** The zone where a cast spends most of its time: the deepest the rope really fishes in. */
    fun homeZone(lengthM: Float): DepthZone =
        DepthZone.entries.lastOrNull { it.minDepth + HOME_ZONE_MARGIN_M <= lengthM } ?: DepthZone.SHALLOWS

    /** Every challenge of [difficulty] this player can do with their rope and log. */
    private fun candidates(p: PlayerProgress, difficulty: ChallengeDifficulty, rnd: Random): List<DailyChallenge> {
        val specs = p.specs
        val home = homeZone(specs.lengthM)
        val perCast = DailyRewards.pointsPerCast(p)
        fun c(kind: ChallengeKind, target: Int, zone: DepthZone? = null, species: String? = null) =
            DailyChallenge(difficulty, kind, target.coerceAtLeast(1), zone, species)
        // Species within reach that the rope can lift.
        val reachable = FishCatalog.regular.filter { it.minDepth <= specs.lengthM - 10f && it.weightKg <= specs.maxWeightKg }
        return when (difficulty) {
            ChallengeDifficulty.EASY -> listOf(
                c(ChallengeKind.CASTS, 3),
                c(ChallengeKind.CATCH_FISH, 10),
                c(ChallengeKind.CATCH_ZONE, 5, zone = home),
                c(ChallengeKind.REACH_DEPTH, (specs.lengthM * 0.8f / 10f).toInt() * 10),
            )
            ChallengeDifficulty.MEDIUM -> listOfNotNull(
                DepthZone.entries.getOrNull(home.ordinal - 1)?.let { c(ChallengeKind.CATCH_ZONE, 3, zone = it) },
                // A common fish the player has seen, living near the end of the rope.
                reachable.filter {
                    it.rarity >= COMMON_RARITY && it.maxDepth >= specs.lengthM - 80f &&
                        Progression.dexState(p, it.id) != DexState.UNKNOWN
                }.takeIf { it.isNotEmpty() }?.let { c(ChallengeKind.CATCH_SPECIES, 1, species = it[rnd.nextInt(it.size)].id) },
                c(ChallengeKind.EARN_POINTS, ShopCatalog.niceRound(perCast * GameConfig.CHALLENGE_POINTS_CASTS_MEDIUM.toDouble()).toInt()),
                c(ChallengeKind.LAND_WEIGHT, ceil(specs.maxWeightKg * 1.5f).toInt()),
                c(ChallengeKind.CATCH_FISH, 18),
            )
            ChallengeDifficulty.HARD -> listOfNotNull(
                c(ChallengeKind.SPARKLING, 2),
                c(ChallengeKind.BIG_CAST, 5 + p.level(RopeStat.STRENGTH) / 3),
                c(ChallengeKind.NEW_SPECIES, 1).takeIf { reachable.count { !p.isDiscovered(it) } >= 3 },
                c(ChallengeKind.EARN_POINTS, ShopCatalog.niceRound(perCast * GameConfig.CHALLENGE_POINTS_CASTS_HARD.toDouble()).toInt()),
                c(ChallengeKind.CATCH_ZONE, 12, zone = home),
            )
        }
    }

    /** [c]'s progress once cast [r] is counted ([before]: the progress before the cast). */
    private fun progressAfter(c: DailyChallenge, r: CastResult, before: PlayerProgress): Int {
        val n = when (c.kind) {
            ChallengeKind.CASTS -> c.progress + 1
            ChallengeKind.CATCH_FISH -> c.progress + r.catches.size
            ChallengeKind.CATCH_ZONE -> c.progress + r.catches.count { it.species.zone == c.zone }
            ChallengeKind.REACH_DEPTH -> max(c.progress, (r.maxDepth + 0.5f).toInt())
            ChallengeKind.CATCH_SPECIES -> c.progress + r.catches.count { it.species.id == c.speciesId }
            ChallengeKind.EARN_POINTS -> c.progress + r.totalPoints
            ChallengeKind.LAND_WEIGHT -> c.progress + r.catches.sumOf { it.species.weightKg.toDouble() }.roundToInt()
            ChallengeKind.SPARKLING -> c.progress + r.catches.count { it.sparkling }
            ChallengeKind.BIG_CAST -> max(c.progress, r.catches.size)
            ChallengeKind.NEW_SPECIES -> c.progress + r.catches.map { it.species.id }.distinct().count { (before.collection[it]?.caught ?: 0) == 0 }
        }
        return n.coerceAtMost(c.target)
    }

    /** A zone counts as the rope's home once the rope reaches this far into it. */
    private const val HOME_ZONE_MARGIN_M = 40f

    /** "Catch a ..." challenges pick from species at least this common (spawn weight). */
    private const val COMMON_RARITY = 20
}
