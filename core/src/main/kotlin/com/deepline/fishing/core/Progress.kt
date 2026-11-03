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
 * [pointsBalance] is what can be spent on upgrades; [pointsEarned] is the lifetime total and
 * drives milestones (spending never lowers it). There is no time limit: progress is kept forever.
 */
data class PlayerProgress(
    val pointsBalance: Long = 0,
    val pointsEarned: Long = 0,
    val claimedMilestones: Set<Int> = emptySet(),
    val ropeLevels: Map<RopeStat, Int> = emptyMap(),
    val lanterns: Int = GameConfig.STARTING_LANTERNS,
    val collection: Map<String, CollectionEntry> = emptyMap(),
    val castsMade: Int = 0,
    /** Record against the computer rival. */
    val battleWins: Int = 0,
    val battleLosses: Int = 0,
    val battleDraws: Int = 0,
    /** Hidden mechanics the player has discovered (ids from [Secrets]). */
    val discoveredSecrets: Set<String> = emptySet(),
    /** Secrets whose explanation the player doesn't want to see pop up again. */
    val mutedSecretPopups: Set<String> = emptySet(),
    val objectsSnagged: Int = 0,
    /** The cosmetic worn in each slot (absent = classic look). */
    val equippedSkins: Map<CosmeticSlot, Cosmetic> = emptyMap(),
) {
    fun level(stat: RopeStat): Int = ropeLevels[stat] ?: 0
    val specs: RopeSpecs get() = RopeSpecs.fromLevels(ropeLevels)
    val discoveredCount: Int get() = collection.count { it.value.caught > 0 }

    fun isDiscovered(species: FishSpecies): Boolean = (collection[species.id]?.caught ?: 0) > 0

    /** Cosmetics unlocked by the secrets discovered so far. */
    val unlockedCosmetics: List<Cosmetic> get() = Cosmetic.entries.filter { it.unlockedBy in discoveredSecrets }
}

/** Pure functions that turn one [PlayerProgress] into the next. */
object Progression {
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
            // Discovery rewards are granted when a secret is first explained (discoverSecret).
            discoveredSecrets = p.discoveredSecrets + Secrets.revealedBy(result),
            objectsSnagged = p.objectsSnagged + result.snags.size,
        )
    }

    /** Records a newly found secret and grants its one-time reward (nothing if already found). */
    fun discoverSecret(p: PlayerProgress, id: String): PlayerProgress {
        if (id in p.discoveredSecrets) return p
        val reward = SecretRewards.of(id)
        return p.copy(
            discoveredSecrets = p.discoveredSecrets + id,
            pointsBalance = p.pointsBalance + reward.points,
            pointsEarned = p.pointsEarned + reward.points,
            lanterns = p.lanterns + reward.lanterns,
        )
    }

    /** Wears [cosmetic] in its slot, or the classic look when null. Locked cosmetics are refused. */
    fun equipSkin(p: PlayerProgress, slot: CosmeticSlot, cosmetic: Cosmetic?): PlayerProgress = when {
        cosmetic == null -> p.copy(equippedSkins = p.equippedSkins - slot)
        cosmetic.slot != slot || cosmetic !in p.unlockedCosmetics -> p
        else -> p.copy(equippedSkins = p.equippedSkins + (slot to cosmetic))
    }

    /** Whether to pop up the explanation of [id] now that it has just happened. */
    fun shouldExplainSecret(p: PlayerProgress, id: String): Boolean =
        id !in p.discoveredSecrets || id !in p.mutedSecretPopups

    fun setSecretPopupMuted(p: PlayerProgress, id: String, muted: Boolean): PlayerProgress =
        p.copy(mutedSecretPopups = if (muted) p.mutedSecretPopups + id else p.mutedSecretPopups - id)

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

    /** Points refunded when stepping [stat] down one level: exactly what that level cost. */
    fun downgradeRefund(p: PlayerProgress, stat: RopeStat): Int? {
        val level = p.level(stat)
        return if (level <= 0) null else stat.costToUpgradeFrom(level - 1)
    }

    /**
     * Steps [stat] down one level and refunds what it cost (spendable points only: lifetime
     * earnings, and so milestones, are unchanged). Null at level 0.
     */
    fun downgrade(p: PlayerProgress, stat: RopeStat): PlayerProgress? {
        val refund = downgradeRefund(p, stat) ?: return null
        val level = p.level(stat) - 1
        return p.copy(
            pointsBalance = p.pointsBalance + refund,
            ropeLevels = if (level == 0) p.ropeLevels - stat else p.ropeLevels + (stat to level),
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

    /** Updates the record against the computer rival. [won] is null for a draw. */
    fun recordBattle(p: PlayerProgress, won: Boolean?): PlayerProgress = when (won) {
        true -> p.copy(battleWins = p.battleWins + 1)
        false -> p.copy(battleLosses = p.battleLosses + 1)
        null -> p.copy(battleDraws = p.battleDraws + 1)
    }

    fun nextMilestone(p: PlayerProgress): Milestone? =
        Milestones.all.firstOrNull { !isMilestoneReached(p, it) }

    fun claimableCount(p: PlayerProgress): Int = Milestones.all.count { isMilestoneClaimable(p, it) }
}
