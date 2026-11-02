package com.deepline.fishing.core

import kotlin.math.max

data class CollectionEntry(
    val caught: Int = 0,
    val sparklingCaught: Int = 0,
    val deepestM: Float = 0f,
    val bestPoints: Int = 0,
)

/**
 * Everything that is saved between sessions.
 *
 * [pointsBalance] is what can be spent on upgrades; [pointsEarned] is the total earned
 * during the current event and drives milestones (spending never lowers it).
 */
data class PlayerProgress(
    val pointsBalance: Long = 0,
    val pointsEarned: Long = 0,
    val claimedMilestones: Set<Int> = emptySet(),
    val ropeLevels: Map<RopeStat, Int> = emptyMap(),
    val lanterns: Int = GameConfig.STARTING_LANTERNS,
    val collection: Map<String, CollectionEntry> = emptyMap(),
    val eventStartMillis: Long = 0,
    val castsMade: Int = 0,
) {
    fun level(stat: RopeStat): Int = ropeLevels[stat] ?: 0
    val specs: RopeSpecs get() = RopeSpecs.fromLevels(ropeLevels)
    val discoveredCount: Int get() = collection.count { it.value.caught > 0 }
}

/** Pure functions that turn one [PlayerProgress] into the next. */
object Progression {
    fun ensureEventStarted(p: PlayerProgress, nowMillis: Long): PlayerProgress =
        if (p.eventStartMillis == 0L) p.copy(eventStartMillis = nowMillis) else p

    fun eventEndsAt(p: PlayerProgress): Long = p.eventStartMillis + GameConfig.EVENT_DURATION_MS

    fun isEventOver(p: PlayerProgress, nowMillis: Long): Boolean =
        p.eventStartMillis != 0L && nowMillis >= eventEndsAt(p)

    /** Starts a fresh event: points and milestones reset; upgrades, lanterns and the log are kept. */
    fun startNewEvent(p: PlayerProgress, nowMillis: Long): PlayerProgress = p.copy(
        pointsBalance = 0,
        pointsEarned = 0,
        claimedMilestones = emptySet(),
        eventStartMillis = nowMillis,
    )

    fun applyCast(p: PlayerProgress, result: CastResult): PlayerProgress {
        val collection = p.collection.toMutableMap()
        for (c in result.catches) {
            val old = collection[c.species.id] ?: CollectionEntry()
            collection[c.species.id] = CollectionEntry(
                caught = old.caught + 1,
                sparklingCaught = old.sparklingCaught + if (c.sparkling) 1 else 0,
                deepestM = max(old.deepestM, c.depth),
                bestPoints = max(old.bestPoints, c.points),
            )
        }
        val gained = result.totalPoints.toLong()
        return p.copy(
            pointsBalance = p.pointsBalance + gained,
            pointsEarned = p.pointsEarned + gained,
            collection = collection,
            castsMade = p.castsMade + 1,
        )
    }

    fun upgradeCost(p: PlayerProgress, stat: RopeStat): Int? = stat.costToUpgradeFrom(p.level(stat))

    fun canUpgrade(p: PlayerProgress, stat: RopeStat): Boolean {
        val cost = upgradeCost(p, stat) ?: return false
        return p.pointsBalance >= cost
    }

    /** Returns the upgraded progress, or null if maxed out or not affordable. */
    fun upgrade(p: PlayerProgress, stat: RopeStat): PlayerProgress? {
        val cost = upgradeCost(p, stat) ?: return null
        if (p.pointsBalance < cost) return null
        return p.copy(
            pointsBalance = p.pointsBalance - cost,
            ropeLevels = p.ropeLevels + (stat to p.level(stat) + 1),
        )
    }

    fun buyLantern(p: PlayerProgress): PlayerProgress? {
        if (p.pointsBalance < GameConfig.LANTERN_PRICE) return null
        return p.copy(pointsBalance = p.pointsBalance - GameConfig.LANTERN_PRICE, lanterns = p.lanterns + 1)
    }

    fun consumeLantern(p: PlayerProgress): PlayerProgress? =
        if (p.lanterns <= 0) null else p.copy(lanterns = p.lanterns - 1)

    fun isMilestoneReached(p: PlayerProgress, m: Milestone): Boolean = p.pointsEarned >= m.threshold

    fun isMilestoneClaimable(p: PlayerProgress, m: Milestone): Boolean =
        isMilestoneReached(p, m) && m.index !in p.claimedMilestones

    fun claimMilestone(p: PlayerProgress, m: Milestone): PlayerProgress? {
        if (!isMilestoneClaimable(p, m)) return null
        return p.copy(
            // Bonus points are spendable but do not count towards further milestones.
            pointsBalance = p.pointsBalance + m.rewardPoints,
            lanterns = p.lanterns + m.rewardLanterns,
            claimedMilestones = p.claimedMilestones + m.index,
        )
    }

    fun nextMilestone(p: PlayerProgress): Milestone? =
        Milestones.all.firstOrNull { !isMilestoneReached(p, it) }

    fun claimableCount(p: PlayerProgress): Int = Milestones.all.count { isMilestoneClaimable(p, it) }
}
