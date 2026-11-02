package com.deepline.fishing.core

/** The rope specs the player can upgrade by spending points. */
enum class RopeStat(
    val displayName: String,
    val description: String,
    val unit: String,
    val maxLevel: Int,
    private val baseValue: Float,
    private val perLevel: Float,
    private val baseCost: Int,
) {
    LENGTH("Rope Length", "How deep the hook can go before it turns back.", "m", 10, 120f, 50f, 120),
    STRENGTH("Rope Strength", "Maximum total weight of fish the line can carry.", "kg", 10, 6f, 4f, 150),
    AGILITY("Rope Agility", "How fast you can steer the hook sideways.", "m/s", 10, 8f, 1.2f, 100);

    fun valueAt(level: Int): Float = baseValue + perLevel * level.coerceIn(0, maxLevel)

    /** Points needed to go from [level] to level + 1, or null when already maxed. */
    fun costToUpgradeFrom(level: Int): Int? =
        if (level >= maxLevel) null else baseCost * (level + 1) * (level + 2) / 2
}

data class RopeSpecs(
    val lengthM: Float,
    val maxWeightKg: Float,
    val steerSpeed: Float,
) {
    companion object {
        fun fromLevels(levels: Map<RopeStat, Int>): RopeSpecs = RopeSpecs(
            lengthM = RopeStat.LENGTH.valueAt(levels[RopeStat.LENGTH] ?: 0),
            maxWeightKg = RopeStat.STRENGTH.valueAt(levels[RopeStat.STRENGTH] ?: 0),
            steerSpeed = RopeStat.AGILITY.valueAt(levels[RopeStat.AGILITY] ?: 0),
        )
    }
}

/** Rewards unlocked by the lifetime total of points earned. */
data class Milestone(
    val index: Int,
    val threshold: Long,
    val rewardPoints: Int,
    val rewardLanterns: Int,
)

object Milestones {
    val all: List<Milestone> = listOf(
        Milestone(0, 300, 100, 1),
        Milestone(1, 1_000, 250, 0),
        Milestone(2, 2_500, 0, 2),
        Milestone(3, 5_000, 750, 0),
        Milestone(4, 9_000, 0, 3),
        Milestone(5, 15_000, 2_000, 0),
        Milestone(6, 25_000, 0, 4),
        Milestone(7, 40_000, 5_000, 0),
        Milestone(8, 60_000, 0, 5),
        Milestone(9, 90_000, 12_000, 5),
    )
}
