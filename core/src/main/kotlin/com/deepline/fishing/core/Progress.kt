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
    val luckyCharms: Int = 0,
    val sparkleLures: Int = 0,
    val detectors: Int = 0,
    /** Shop items owned (ids from [ShopCatalog]) and other unlock keys ("badge:RAINBOW"...). */
    val ownedItems: Set<String> = emptySet(),
    /** Captain's Club membership ends at this time (epoch ms; 0: never a member). */
    val clubUntilMs: Long = 0L,
    /** When a Club member can next claim their free Lantern pouch (epoch ms; 0: right away). */
    val clubPouchNextMs: Long = 0L,
    /** The season [seasonXp] and [seasonClaims] belong to (see `Seasons`). */
    val seasonNumber: Int = 0,
    val seasonXp: Int = 0,
    /** Season rewards claimed ("f3": free tier 3, "p3": premium tier 3). */
    val seasonClaims: Set<String> = emptySet(),
    /** Looks for showing off (see `Showcase`): the title badge's frame and the Dex's card style. */
    val titleFrame: String = TitleFrame.GOLD.name,
    val cardStyle: String = CardStyle.CLASSIC.name,
    /** Species picked for the aquarium (empty: the best catches), and decor put away. */
    val tankFish: List<String> = emptyList(),
    val hiddenDecor: Set<String> = emptySet(),
    /** The weekend event [eventProgress] and [eventClaims] belong to. */
    val eventKey: String = "",
    val eventProgress: Int = 0,
    val eventClaims: Set<String> = emptySet(),
    /** How many times the app was opened (for the starter pack's timing). */
    val sessions: Int = 0,
    /** When the starter pack offer ends (epoch ms; 0: not offered yet). */
    val starterOfferEndsMs: Long = 0L,
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
    /** Species that have come into view during a cast (caught ones count as seen too). */
    val seenSpecies: Set<String> = emptySet(),
    /** The boat the player designed (Dex > Skins > Customise), worn when [customBoatEquipped]. */
    val boatDesign: BoatDesign = BoatDesign.DEFAULT,
    val customBoatEquipped: Boolean = false,
    /** Boat options bought with points ([BoatPart.key]); other options unlock by progress. */
    val unlockedBoatParts: Set<String> = emptySet(),
    /** The Nightmare atmosphere unlocks the first time a cast reaches the Abyssal Floor. */
    val atmosphereUnlocked: Boolean = false,
    val atmosphere: Atmosphere = Atmosphere.FAMILY,
) {
    fun level(stat: RopeStat): Int = ropeLevels[stat] ?: 0
    val specs: RopeSpecs get() = RopeSpecs.fromLevels(ropeLevels)
    val discoveredCount: Int get() = collection.count { it.value.caught > 0 }

    fun isDiscovered(species: FishSpecies): Boolean = (collection[species.id]?.caught ?: 0) > 0

    /** Species seen or caught, for the Dex. */
    val seenCount: Int get() = FishCatalog.all.count { Progression.dexState(this, it.id) != DexState.UNKNOWN }

    /** Cosmetics unlocked by the secrets discovered so far. */
    val unlockedCosmetics: List<Cosmetic>
        get() = Cosmetic.entries.filter { it.unlockedBy in discoveredSecrets || (it.fromShop && ShopCatalog.ownsCosmetic(this, it)) }
}

/** How much the Dex knows about a species. Caught implies seen. */
enum class DexState { UNKNOWN, SEEN, CAUGHT }

/** Pure functions that turn one [PlayerProgress] into the next. */
object Progression {
    fun dexState(p: PlayerProgress, speciesId: String): DexState = when {
        (p.collection[speciesId]?.caught ?: 0) > 0 -> DexState.CAUGHT
        speciesId in p.seenSpecies -> DexState.SEEN
        else -> DexState.UNKNOWN
    }

    /** 1 to 5 stars: the rarer the species (lower spawn weight), the more stars. */
    fun rarityStars(species: FishSpecies): Int =
        1 + GameConfig.RARITY_STAR_WEIGHTS.count { species.rarity <= it }

    /** The species' Dex number, from 1 (in [FishCatalog.all] order). */
    fun dexNumber(species: FishSpecies): Int = FishCatalog.all.indexOf(species) + 1

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
        // Solo casts also climb the season's track and count for a weekend event.
        return Seasons.onCast(p, result, ShopClock.now()).copy(
            pointsBalance = p.pointsBalance + gained,
            pointsEarned = p.pointsEarned + gained,
            collection = collection,
            seenSpecies = p.seenSpecies + result.seenSpeciesIds + result.catches.map { it.species.id },
            castsMade = p.castsMade + 1,
            // Discovery rewards are granted when a secret is first explained (discoverSecret).
            discoveredSecrets = p.discoveredSecrets + Secrets.revealedBy(result),
            objectsSnagged = p.objectsSnagged + result.snags.size,
            atmosphereUnlocked = p.atmosphereUnlocked || result.maxDepth >= DepthZone.ABYSS.minDepth,
        )
    }

    // --- Boat customisation and atmosphere ----------------------------------------------------

    fun isBoatPartAvailable(p: PlayerProgress, part: BoatPart): Boolean = BoatParts.isAvailable(p, part)

    /**
     * Buys [part] with points (spendable balance only: lifetime earnings, and so milestones, don't
     * change). Null if it isn't sold for points, is already owned, or is too expensive.
     */
    fun unlockBoatPart(p: PlayerProgress, part: BoatPart): PlayerProgress? {
        val cost = (BoatParts.requirement(part) as? PartRequirement.Points)?.cost ?: return null
        if (part.key in p.unlockedBoatParts || p.pointsBalance < cost) return null
        return p.copy(pointsBalance = p.pointsBalance - cost, unlockedBoatParts = p.unlockedBoatParts + part.key)
    }

    /** Saves [design] as the player's boat. Null if it uses an option they can't use yet. */
    fun setBoatDesign(p: PlayerProgress, design: BoatDesign): PlayerProgress? {
        if (BoatParts.partsOf(design).any { !BoatParts.isAvailable(p, it) }) return null
        return p.copy(boatDesign = design)
    }

    /** Wears the hand-made boat (taking off any boat cosmetic), or the classic boat when false. */
    fun equipCustomBoat(p: PlayerProgress, on: Boolean): PlayerProgress =
        if (on) p.copy(customBoatEquipped = true, equippedSkins = p.equippedSkins - CosmeticSlot.BOAT) else p.copy(customBoatEquipped = false)

    /** Nightmare needs to be unlocked first; Family is always allowed. */
    fun setAtmosphere(p: PlayerProgress, atmosphere: Atmosphere): PlayerProgress? = when {
        atmosphere == Atmosphere.NIGHTMARE && !p.atmosphereUnlocked -> null
        else -> p.copy(atmosphere = atmosphere)
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
        // A boat cosmetic replaces the hand-made boat.
        slot == CosmeticSlot.BOAT -> p.copy(equippedSkins = p.equippedSkins + (slot to cosmetic), customBoatEquipped = false)
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

    fun buyLantern(p: PlayerProgress): PlayerProgress? = buyTackle(p, Tackle.LANTERN)

    /** The price of [item] for this player's rope. */
    fun tacklePrice(p: PlayerProgress, item: Tackle): Int = item.price(p.level(RopeStat.LENGTH))

    fun tackleCount(p: PlayerProgress, item: Tackle): Int = when (item) {
        Tackle.LANTERN -> p.lanterns
        Tackle.LUCKY_CHARM -> p.luckyCharms
        Tackle.SPARKLE_LURE -> p.sparkleLures
        Tackle.DETECTOR -> p.detectors
    }

    internal fun withTackle(p: PlayerProgress, item: Tackle, count: Int) = when (item) {
        Tackle.LANTERN -> p.copy(lanterns = count)
        Tackle.LUCKY_CHARM -> p.copy(luckyCharms = count)
        Tackle.SPARKLE_LURE -> p.copy(sparkleLures = count)
        Tackle.DETECTOR -> p.copy(detectors = count)
    }

    fun buyTackle(p: PlayerProgress, item: Tackle): PlayerProgress? {
        val price = tacklePrice(p, item)
        if (p.pointsBalance < price) return null
        return withTackle(p.copy(pointsBalance = p.pointsBalance - price), item, tackleCount(p, item) + 1)
    }

    /** Uses one [item] (for the cast about to start); null when the player has none. */
    fun consumeTackle(p: PlayerProgress, item: Tackle): PlayerProgress? {
        val n = tackleCount(p, item)
        return if (n <= 0) null else withTackle(p, item, n - 1)
    }

    /** Puts [stake] points on a battle against the computer; null when they can't afford it. */
    fun placeWager(p: PlayerProgress, stake: Long): PlayerProgress? =
        if (stake <= 0 || p.pointsBalance < stake) null else p.copy(pointsBalance = p.pointsBalance - stake)

    /**
     * Settles a wager: a win pays the stake back times [GameConfig.wagerPayout], a draw refunds
     * it, a loss keeps it. Winnings are spendable but don't count towards milestones.
     */
    fun settleWager(p: PlayerProgress, stake: Long, rival: BotDifficulty, won: Boolean?): PlayerProgress = when (won) {
        true -> p.copy(pointsBalance = p.pointsBalance + (stake * GameConfig.wagerPayout(rival)).toLong())
        null -> p.copy(pointsBalance = p.pointsBalance + stake)
        false -> p
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
