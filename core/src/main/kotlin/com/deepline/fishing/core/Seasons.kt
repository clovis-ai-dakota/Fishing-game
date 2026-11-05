package com.deepline.fishing.core

/**
 * Seasons and weekend events.
 *
 * A season lasts [GameConfig.SEASON_WEEKS] weeks. Every solo cast earns season XP (the same for
 * every rope, so new and old players climb at the same pace), and each [GameConfig.SEASON_XP_PER_TIER]
 * XP opens a tier of the season's track. The free track has tackle and points for everyone; the
 * season pass (euros or points) adds a premium track with more tackle, a season badge and a
 * season boat. The collector's edition is the pass plus a head start, the boat and a badge.
 *
 * Weekend events run from Friday evening to Monday morning (UTC) with one goal ("catch Sparkling
 * fish"...). Reaching it gives a reward; the event pass doubles the progress and adds more.
 *
 * Everything here is solo: it never changes battles or what a cast is worth.
 */
data class Season(val number: Int, val name: String, val startMs: Long, val endMs: Long) {
    fun msLeft(now: Long) = (endMs - now).coerceAtLeast(0L)
}

/** What a tier, or an event, gives. */
sealed interface SeasonGrant {
    /** Points worth this many minutes of a good player's fishing at the rope's reach. */
    data class Points(val minutes: Int) : SeasonGrant
    data class Tackle(val tackle: com.deepline.fishing.core.Tackle, val count: Int) : SeasonGrant
    /** A premade boat (the season's boat). */
    data class Boat(val presetId: String) : SeasonGrant
    /** An unlock key ("badge:SEASON"...). */
    data class Unlock(val key: String) : SeasonGrant
}

data class SeasonReward(val tier: Int, val premium: Boolean, val grants: List<SeasonGrant>) {
    /** Saved in [PlayerProgress.seasonClaims]. */
    val key: String get() = (if (premium) "p" else "f") + tier
}

enum class EventGoal(val displayName: String, val target: Int) {
    SPARKLING("Catch Sparkling fish", 12),
    FISH("Catch fish", 60),
    DEEP("Reach the Midnight Trench", 6),
    SPECIES("Catch different species", 25),
}

data class WeekendEvent(val key: String, val goal: EventGoal, val startMs: Long, val endMs: Long) {
    fun msLeft(now: Long) = (endMs - now).coerceAtLeast(0L)
}

object Seasons {
    private const val DAY = 86_400_000L
    private const val HOUR = 3_600_000L

    /** Season 1 started on Monday 5 January 2026, 00:00 UTC. */
    const val EPOCH_MS = 1_767_571_200_000L
    private val lengthMs get() = GameConfig.SEASON_WEEKS * 7 * DAY

    private val names = listOf("Coral Tides", "Midnight Bloom", "Frost Current", "Sunken Gold", "Storm Season", "Lantern Festival")

    /** The season boats, in turn: each season's pass gives the next one. */
    val boats = listOf("season_tidecaller", "season_moonbloom", "season_frostfin", "season_goldwake")

    fun at(now: Long): Season {
        val n = Math.floorDiv(now - EPOCH_MS, lengthMs).toInt().coerceAtLeast(0)
        val start = EPOCH_MS + n * lengthMs
        return Season(n + 1, names[n % names.size], start, start + lengthMs)
    }

    fun boatOf(season: Season) = boats[(season.number - 1) % boats.size]

    // ---- XP and tiers ---------------------------------------------------------------------------

    fun castXp(r: CastResult): Int =
        GameConfig.SEASON_XP_PER_CAST + GameConfig.SEASON_XP_PER_FISH * r.catches.size + GameConfig.SEASON_XP_PER_SECRET * r.secretBonuses.size

    /** [p] with its season progress moved to the season running at [now] (a new season starts at 0). */
    fun current(p: PlayerProgress, now: Long): PlayerProgress {
        val s = at(now)
        return if (p.seasonNumber == s.number) p else p.copy(seasonNumber = s.number, seasonXp = 0, seasonClaims = emptySet())
    }

    /** Adds a solo cast's XP to the season, and its progress to a live weekend event. */
    fun onCast(p: PlayerProgress, r: CastResult, now: Long): PlayerProgress {
        val q = current(p, now)
        return onEventCast(q.copy(seasonXp = q.seasonXp + castXp(r)), r, now)
    }

    fun hasPass(p: PlayerProgress, s: Season) = "season:${s.number}:pass" in p.ownedItems
    fun hasCollector(p: PlayerProgress, s: Season) = "season:${s.number}:collector" in p.ownedItems

    /** Tiers open on the track: XP, plus the collector's head start. */
    fun tier(p: PlayerProgress, now: Long): Int {
        val q = current(p, now)
        val skip = if (hasCollector(q, at(now))) GameConfig.COLLECTOR_TIER_SKIP else 0
        return (q.seasonXp / GameConfig.SEASON_XP_PER_TIER + skip).coerceAtMost(GameConfig.SEASON_TIERS)
    }

    /** XP into the next tier, out of [GameConfig.SEASON_XP_PER_TIER]. */
    fun xpIntoTier(p: PlayerProgress, now: Long): Int =
        if (tier(p, now) >= GameConfig.SEASON_TIERS) GameConfig.SEASON_XP_PER_TIER else current(p, now).seasonXp % GameConfig.SEASON_XP_PER_TIER

    /** Both tracks of [season]: tackle and points for everyone, more with the pass. */
    fun rewards(season: Season): List<SeasonReward> = (1..GameConfig.SEASON_TIERS).flatMap { t ->
        // Tuned with EconomyTest (the free track is part of every player's income).
        val free: List<SeasonGrant> = when {
            t == GameConfig.SEASON_TIERS -> listOf(SeasonGrant.Tackle(Tackle.LANTERN, 3), SeasonGrant.Points(5))
            t == 10 -> listOf(SeasonGrant.Points(3))
            t % 5 == 0 -> listOf(SeasonGrant.Tackle(Tackle.LANTERN, 2))
            t % 4 == 1 -> listOf(SeasonGrant.Tackle(Tackle.SPARKLE_LURE, 1))
            t % 4 == 2 -> listOf(SeasonGrant.Tackle(Tackle.DETECTOR, 1))
            t % 4 == 3 -> listOf(SeasonGrant.Tackle(Tackle.LUCKY_CHARM, 1))
            else -> listOf(SeasonGrant.Tackle(if (t % 8 == 0) Tackle.SPARKLE_LURE else Tackle.LUCKY_CHARM, 1))
        }
        val premium: List<SeasonGrant> = when (t) {
            1 -> listOf(SeasonGrant.Unlock("badge:SEASON"), SeasonGrant.Tackle(Tackle.LANTERN, 5))
            10 -> listOf(SeasonGrant.Boat(boatOf(season)))
            GameConfig.SEASON_TIERS -> listOf(
                SeasonGrant.Tackle(Tackle.LANTERN, 20), SeasonGrant.Tackle(Tackle.DETECTOR, 10),
                SeasonGrant.Tackle(Tackle.LUCKY_CHARM, 5), SeasonGrant.Tackle(Tackle.SPARKLE_LURE, 5),
            )
            else -> when (t % 3) {
                0 -> listOf(SeasonGrant.Tackle(Tackle.DETECTOR, 3))
                1 -> listOf(SeasonGrant.Tackle(Tackle.LANTERN, 4))
                else -> listOf(SeasonGrant.Tackle(Tackle.LUCKY_CHARM, 1), SeasonGrant.Tackle(Tackle.SPARKLE_LURE, 1))
            }
        }
        listOf(SeasonReward(t, false, free), SeasonReward(t, true, premium))
    }

    fun claimable(p: PlayerProgress, now: Long): List<SeasonReward> {
        val q = current(p, now)
        val s = at(now)
        val open = tier(q, now)
        return rewards(s).filter { it.tier <= open && it.key !in q.seasonClaims && (!it.premium || hasPass(q, s)) }
    }

    fun claim(p: PlayerProgress, reward: SeasonReward, now: Long): PlayerProgress? {
        if (reward !in claimable(p, now)) return null
        val q = current(p, now)
        return give(q, reward.grants, now).copy(seasonClaims = q.seasonClaims + reward.key)
    }

    /** Claims everything open on the track. */
    fun claimAll(p: PlayerProgress, now: Long): PlayerProgress =
        claimable(p, now).fold(p) { q, r -> claim(q, r, now) ?: q }

    fun pointsFor(p: PlayerProgress, minutes: Int): Long {
        val level = p.level(RopeStat.LENGTH).coerceIn(0, GameConfig.POINTS_PER_MINUTE_BY_LENGTH_LEVEL.size - 1)
        return ShopCatalog.niceRound(minutes.toDouble() * GameConfig.POINTS_PER_MINUTE_BY_LENGTH_LEVEL[level])
    }

    fun give(p: PlayerProgress, grants: List<SeasonGrant>, now: Long): PlayerProgress = grants.fold(p) { q, g ->
        when (g) {
            is SeasonGrant.Points -> pointsFor(q, g.minutes).let { q.copy(pointsBalance = q.pointsBalance + it) }
            is SeasonGrant.Tackle -> Progression.withTackle(q, g.tackle, Progression.tackleCount(q, g.tackle) + g.count)
            is SeasonGrant.Boat -> {
                val design = PremiumSkins.byId(g.presetId)?.design
                val keys = design?.let { BoatParts.partsOf(it).map { part -> part.key } } ?: emptyList()
                q.copy(ownedItems = q.ownedItems + "preset:${g.presetId}", unlockedBoatParts = q.unlockedBoatParts + keys)
            }
            is SeasonGrant.Unlock -> q.copy(ownedItems = q.ownedItems + g.key)
        }
    }

    // ---- Weekend events --------------------------------------------------------------------------

    /** The event running at [now] (Friday 18:00 to Monday 06:00 UTC), or null. */
    fun eventAt(now: Long): WeekendEvent? {
        // EPOCH_MS is a Monday: each week's event starts 4 days 18 hours in and lasts 60 hours.
        val offset = 4 * DAY + 18 * HOUR
        val week = Math.floorDiv(now - EPOCH_MS - offset, 7 * DAY)
        val start = EPOCH_MS + offset + week * 7 * DAY
        val end = start + 2 * DAY + 12 * HOUR
        if (now !in start until end) return null
        val goal = EventGoal.entries[Math.floorMod(week, EventGoal.entries.size.toLong()).toInt()]
        return WeekendEvent("w$week", goal, start, end)
    }

    /** When the next event starts (for "next event in 2 d"). */
    fun nextEventStart(now: Long): Long {
        val offset = 4 * DAY + 18 * HOUR
        val week = Math.floorDiv(now - EPOCH_MS - offset, 7 * DAY)
        return EPOCH_MS + offset + (week + 1) * 7 * DAY
    }

    fun hasEventPass(p: PlayerProgress, e: WeekendEvent) = "event:${e.key}:pass" in p.ownedItems

    /** [p] with its event progress moved to the event at [now] (a new event starts at 0). */
    fun currentEvent(p: PlayerProgress, now: Long): PlayerProgress {
        val e = eventAt(now) ?: return p
        return if (p.eventKey == e.key) p else p.copy(eventKey = e.key, eventProgress = 0, eventClaims = emptySet())
    }

    /** How much a cast counts towards [goal] (the event pass doubles it). */
    fun eventCount(goal: EventGoal, r: CastResult, p: PlayerProgress): Int = when (goal) {
        EventGoal.SPARKLING -> r.catches.count { it.sparkling }
        EventGoal.FISH -> r.catches.size
        EventGoal.DEEP -> if (r.maxDepth >= DepthZone.MIDNIGHT.minDepth) 1 else 0
        EventGoal.SPECIES -> r.catches.map { it.species.id }.distinct().size
    }

    private fun onEventCast(p: PlayerProgress, r: CastResult, now: Long): PlayerProgress {
        val e = eventAt(now) ?: return p
        val q = currentEvent(p, now)
        val n = eventCount(e.goal, r, q) * if (hasEventPass(q, e)) 2 else 1
        return q.copy(eventProgress = (q.eventProgress + n).coerceAtMost(e.goal.target))
    }

    /** What reaching the event's goal gives: [free] for everyone, [pass] with the event pass. */
    val eventFree: List<SeasonGrant> = listOf(SeasonGrant.Tackle(Tackle.LANTERN, 5), SeasonGrant.Points(10))
    val eventPass: List<SeasonGrant> = listOf(
        SeasonGrant.Tackle(Tackle.LANTERN, 15), SeasonGrant.Tackle(Tackle.DETECTOR, 5), SeasonGrant.Tackle(Tackle.SPARKLE_LURE, 3),
        SeasonGrant.Unlock("badge:EVENT"),
    )

    fun eventClaimable(p: PlayerProgress, now: Long): List<String> {
        val e = eventAt(now) ?: return emptyList()
        val q = currentEvent(p, now)
        if (q.eventProgress < e.goal.target) return emptyList()
        return listOfNotNull("free".takeIf { it !in q.eventClaims }, "pass".takeIf { it !in q.eventClaims && hasEventPass(q, e) })
    }

    fun claimEvent(p: PlayerProgress, now: Long): PlayerProgress? {
        val open = eventClaimable(p, now)
        if (open.isEmpty()) return null
        var q = currentEvent(p, now)
        for (k in open) q = give(q, if (k == "free") eventFree else eventPass, now).copy(eventClaims = q.eventClaims + k)
        return q
    }

    // ---- Shop items --------------------------------------------------------------------------------

    val shopItems: List<ShopItem> = listOf(
        ShopItem(
            "season_pass", "Season pass", "This season's premium track: a season badge, the season boat at tier 10 and lots of tackle.",
            ShopSection.PASSES, 499, ItemKind.SEASONAL, listOf(Grant.Feature("season_pass")),
        ),
        ShopItem(
            "collector_edition", "Collector's edition", "The season pass, ${GameConfig.COLLECTOR_TIER_SKIP} tiers straight away, " +
                "the season boat now and a collector's badge.",
            ShopSection.PASSES, 999, ItemKind.SEASONAL, listOf(Grant.Feature("season_collector")), badge = "Special",
        ),
        ShopItem(
            "event_pass", "Weekend event pass", "Doubles this weekend's event progress and adds a bigger reward with an event badge.",
            ShopSection.PASSES, 199, ItemKind.SEASONAL, listOf(Grant.Feature("event_pass")),
        ),
    )

    /** Whether a seasonal item can be bought now (null: not a seasonal item). */
    fun availability(item: ShopItem, p: PlayerProgress, now: Long): Boolean? = when (item.id) {
        "season_pass" -> !hasPass(p, at(now))
        "collector_edition" -> !hasCollector(p, at(now))
        "event_pass" -> eventAt(now)?.let { !hasEventPass(p, it) } ?: false
        else -> null
    }

    /** What buying a seasonal item does. */
    fun grantFeature(key: String, p: PlayerProgress, now: Long): PlayerProgress? {
        val s = at(now)
        return when (key) {
            "season_pass" -> current(p, now).let { it.copy(ownedItems = it.ownedItems + "season:${s.number}:pass") }
            "season_collector" -> give(current(p, now), listOf(SeasonGrant.Boat(boatOf(s)), SeasonGrant.Unlock("badge:COLLECTOR")), now).let {
                it.copy(ownedItems = it.ownedItems + "season:${s.number}:pass" + "season:${s.number}:collector")
            }
            "event_pass" -> eventAt(now)?.let { e -> currentEvent(p, now).let { it.copy(ownedItems = it.ownedItems + "event:${e.key}:pass") } } ?: p
            else -> null
        }
    }
}
