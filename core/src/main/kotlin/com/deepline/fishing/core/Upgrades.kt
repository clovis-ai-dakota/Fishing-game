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
    LENGTH("Rope Length", "How deep the hook can go before it turns back.", "m", 10, 120f, 50f, 100),
    STRENGTH("Rope Strength", "Maximum total weight of fish the line can carry.", "kg", 10, 6f, 4f, 110),
    AGILITY("Rope Agility", "How fast you can steer the hook sideways.", "m/s", 10, 8f, 1.2f, 90);

    fun valueAt(level: Int): Float = baseValue + perLevel * level.coerceIn(0, maxLevel)

    /**
     * Points needed to go from [level] to level + 1, or null when already maxed: [COST_CURVE]
     * scaled by this stat's weight ([baseCost] in percent), rounded to tens.
     */
    fun costToUpgradeFrom(level: Int): Int? =
        if (level >= maxLevel) null else ((COST_CURVE[level].toLong() * baseCost / 100) / 10 * 10).toInt()

    companion object {
        /**
         * The price of each level, following income: deeper ropes earn far more per cast, so
         * each level costs about as many casts at that reach as the pacing target (a good player
         * reaches the Midnight Trench around cast 30, the Abyssal Floor around cast 140 and the
         * end of a full rope around cast 250; see EconomyTest).
         */
        val COST_CURVE = intArrayOf(200, 900, 3_000, 9_000, 22_000, 45_000, 70_000, 100_000, 150_000, 200_000)
    }
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
        // Spread over a whole career (a good player earns about 2 million points maxing the rope).
        Milestone(0, 300, 100, 1),
        Milestone(1, 1_500, 500, 0),
        Milestone(2, 5_000, 0, 2),
        Milestone(3, 12_000, 3_000, 0),
        Milestone(4, 30_000, 0, 3),
        Milestone(5, 60_000, 12_000, 0),
        Milestone(6, 120_000, 0, 4),
        Milestone(7, 220_000, 40_000, 0),
        Milestone(8, 400_000, 0, 5),
        Milestone(9, 650_000, 100_000, 0),
        Milestone(10, 1_000_000, 0, 6),
        Milestone(11, 1_500_000, 250_000, 0),
        Milestone(12, 2_200_000, 0, 8),
        Milestone(13, 3_000_000, 500_000, 0),
        Milestone(14, 4_000_000, 0, 10),
    )
}
