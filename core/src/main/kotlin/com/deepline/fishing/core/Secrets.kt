package com.deepline.fishing.core

import kotlin.math.roundToInt

/**
 * Fishing secrets: hidden mechanics that reward players who understand the game well.
 *
 * Each secret has a trigger, a **bonus** added to the cast's score every time it happens (solo
 * and in battles, for the computer rival too; online, the server recomputes it), and a one-time
 * **discovery reward**. The app explains a secret the first time it happens. Secrets are never
 * spelled out in the tour.
 */
object Secrets {
    /** The last bump hooks whatever it hit, which is the only way to land objects. */
    const val LAST_BUMP = "last_bump"
    /** 3+ fish of one species in a cast. */
    const val SCHOOLING = "schooling"
    /** On the way up, catch a fish you bumped on the way down. */
    const val SECOND_ENCOUNTER = "second_encounter"
    /** Reach the end of a long rope without a single bump. */
    const val CLEAN_DROP = "clean_drop"
    /** The 4 bumps hit a fish head, a rock, driftwood and a jelly. */
    const val GRAND_TOUR = "grand_tour"
    /** 4+ fish caught on the way up in strictly increasing weight. */
    const val LADDER = "ladder"
    /** A fish from every depth zone in one cast. */
    const val ZONE_SWEEP = "zone_sweep"
    /** The last bump hooks the Pale Leviathan on a rope too weak to carry it. */
    const val DAVID_AND_GOLIATH = "david_and_goliath"

    /** All secrets, in rough order of difficulty. */
    val all: List<String> = listOf(LAST_BUMP, SCHOOLING, SECOND_ENCOUNTER, CLEAN_DROP, GRAND_TOUR, LADDER, ZONE_SWEEP, DAVID_AND_GOLIATH)

    /** Which secrets this cast revealed. */
    fun revealedBy(result: CastResult): Set<String> {
        val ids = result.secretBonuses.map { it.secretId }.toMutableSet()
        if (result.lastBumpHooked) ids += LAST_BUMP
        return ids
    }
}

/** What a bump on the way down hit. */
enum class BumpKind { FISH_HEAD, DRIFTWOOD, ROCK, JELLY;

    companion object {
        fun of(kind: ObstacleKind): BumpKind = when (kind) {
            ObstacleKind.DRIFTWOOD -> DRIFTWOOD
            ObstacleKind.ROCK -> ROCK
            ObstacleKind.JELLY -> JELLY
        }
    }
}

/** Points a secret added to a cast. */
data class SecretBonus(val secretId: String, val points: Int)

/** The thresholds and bonuses of every secret. All numbers for secrets live here. */
object SecretRules {
    const val SCHOOLING_COUNT = 3
    const val SCHOOLING_BONUS = 100

    /** Second encounter: that fish is worth this many times its points. */
    const val SECOND_ENCOUNTER_MULTIPLIER = 2.0

    const val CLEAN_DROP_MIN_ROPE_M = 300f
    const val CLEAN_DROP_MULTIPLIER = 1.5

    /** Grand tour: what the last bump hooked is worth this many times its points. */
    const val GRAND_TOUR_MULTIPLIER = 2.0

    const val LADDER_COUNT = 4
    const val LADDER_MULTIPLIER = 1.5

    const val ZONE_SWEEP_MULTIPLIER = 2.0

    const val GOLIATH_SPECIES_ID = "pale_leviathan"
    /** David and Goliath: the leviathan is worth this many times its points. */
    const val DAVID_AND_GOLIATH_MULTIPLIER = 3.0

    /**
     * The bonuses [r] earned. Cast-wide multipliers each add (multiplier - 1) × the cast's base
     * points (they add up rather than compound), and per-catch ones apply to that catch only.
     */
    fun bonuses(r: CastResult): List<SecretBonus> {
        val base = r.basePoints
        val out = ArrayList<SecretBonus>()
        fun add(id: String, points: Double) {
            val p = points.roundToInt()
            if (p > 0) out += SecretBonus(id, p)
        }
        val ascent = r.catches.filter { !it.byLastBump }

        if (r.catches.groupingBy { it.species.id }.eachCount().values.any { it >= SCHOOLING_COUNT }) {
            add(Secrets.SCHOOLING, SCHOOLING_BONUS.toDouble())
        }

        val reencountered = ascent.filter { it.bumpedOnDescent }
        if (reencountered.isNotEmpty()) {
            add(Secrets.SECOND_ENCOUNTER, reencountered.sumOf { it.points * (SECOND_ENCOUNTER_MULTIPLIER - 1.0) })
        }

        if (r.reverseReason == ReverseReason.ROPE_END && r.touchesUsed == 0 && r.maxDepth >= CLEAN_DROP_MIN_ROPE_M) {
            add(Secrets.CLEAN_DROP, base * (CLEAN_DROP_MULTIPLIER - 1.0))
        }

        if (r.bumps.size == GameConfig.DESCENT_TOUCH_LIMIT && r.bumps.toSet() == BumpKind.entries.toSet()) {
            val hooked = r.catches.firstOrNull { it.byLastBump }?.points ?: r.snags.firstOrNull()?.points ?: 0
            add(Secrets.GRAND_TOUR, hooked * (GRAND_TOUR_MULTIPLIER - 1.0))
        }

        if (ascent.size >= LADDER_COUNT && ascent.zipWithNext().all { (a, b) -> b.species.weightKg > a.species.weightKg }) {
            add(Secrets.LADDER, base * (LADDER_MULTIPLIER - 1.0))
        }

        if (r.catches.map { it.species.zone }.toSet() == DepthZone.entries.toSet()) {
            add(Secrets.ZONE_SWEEP, base * (ZONE_SWEEP_MULTIPLIER - 1.0))
        }

        val goliath = r.catches.firstOrNull { it.byLastBump && it.species.id == GOLIATH_SPECIES_ID }
        if (goliath != null && goliath.species.weightKg > r.lineStrengthKg) {
            add(Secrets.DAVID_AND_GOLIATH, goliath.points * (DAVID_AND_GOLIATH_MULTIPLIER - 1.0))
        }
        return out
    }
}

/** The one-time reward for discovering a secret. */
data class SecretReward(val points: Int, val lanterns: Int, val cosmetics: List<Cosmetic> = emptyList())

object SecretRewards {
    private val rewards: Map<String, SecretReward> = mapOf(
        Secrets.LAST_BUMP to SecretReward(0, 0),
        Secrets.SCHOOLING to SecretReward(300, 0),
        Secrets.SECOND_ENCOUNTER to SecretReward(750, 1),
        Secrets.CLEAN_DROP to SecretReward(1_000, 1),
        Secrets.GRAND_TOUR to SecretReward(2_500, 2),
        Secrets.LADDER to SecretReward(2_000, 2),
        Secrets.ZONE_SWEEP to SecretReward(8_000, 5),
        Secrets.DAVID_AND_GOLIATH to SecretReward(
            15_000,
            6,
            Cosmetic.entries.filter { it.unlockedBy == Secrets.DAVID_AND_GOLIATH },
        ),
    )

    fun of(id: String): SecretReward = rewards[id] ?: SecretReward(0, 0)
}

/** Where a cosmetic goes. Only one cosmetic per slot is worn at a time. */
enum class CosmeticSlot(val displayName: String) { HOOK("Hook"), LINE("Line"), BOAT("Boat") }

/**
 * Purely visual rewards for legendary secrets. They never change gameplay: same hook size and
 * hitbox, and nothing that lights up dark water. The app picks how to draw them.
 */
enum class Cosmetic(
    val displayName: String,
    val description: String,
    val slot: CosmeticSlot,
    val unlockedBy: String,
) {
    LEVIATHAN_BONE_HOOK(
        "Leviathan bone hook",
        "An ivory hook carved with rings, for the angler who landed a giant on a thin line.",
        CosmeticSlot.HOOK,
        Secrets.DAVID_AND_GOLIATH,
    ),
    GIANTS_BRAID_LINE(
        "Giant's braid",
        "A pale braided line in the leviathan's colours.",
        CosmeticSlot.LINE,
        Secrets.DAVID_AND_GOLIATH,
    ),
    GIANT_SLAYER_PENNANT(
        "Giant-slayer pennant",
        "A pennant with a leviathan on it, flying from your boat.",
        CosmeticSlot.BOAT,
        Secrets.DAVID_AND_GOLIATH,
    ),
}
