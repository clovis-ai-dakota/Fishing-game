package com.deepline.fishing.core

import kotlin.random.Random

/**
 * Species whose spawns are boosted in the water about to be generated: each spawns with at least
 * [weight] (see [WorldGenerator.generate]). Only which species appear changes, never how many fish
 * or obstacles there are, the scoring or the rules.
 */
data class SpawnBoost(val speciesIds: Set<String>, val weight: Int) {
    fun weightOf(species: FishSpecies): Int = if (species.id in speciesIds) maxOf(species.rarity, weight) else species.rarity
}

/** A migration: two species far more common than usual, for a limited time. */
data class Migration(val week: Long, val species: List<FishSpecies>, val startMs: Long, val endMs: Long) {
    val key: String get() = "m$week"
    val boost: SpawnBoost get() = SpawnBoost(species.map { it.id }.toSet(), GameConfig.MIGRATION_SPAWN_WEIGHT)

    fun isLive(now: Long) = now in startMs until endMs
    fun msLeft(now: Long) = (endMs - now).coerceAtLeast(0L)
    fun msUntilStart(now: Long) = (startMs - now).coerceAtLeast(0L)
}

/**
 * Migrations: every week from Tuesday 00:00 UTC, for [GameConfig.MIGRATION_HOURS] hours (between
 * the weekend events), two less common species become as common as the most common fish where
 * they live: one from the upper water (so every angler can join) and one from the deep. The
 * rotation is a fixed shuffle, the same for everyone. See docs/research/daily-loop.md.
 *
 * Solo only: battles fish water made from a shared seed, which migrations never change.
 */
object Migrations {
    private const val DAY = LocalDays.DAY_MS

    /** Tuesday 00:00 UTC ([Seasons.EPOCH_MS] is a Monday). */
    private const val START_OFFSET_MS = DAY
    private const val WEEK_MS = 7 * DAY
    private const val ROTATION_SEED = 20_260_106L

    /** Less common species of the upper water and of the deep, in the order they migrate. */
    private val upper: List<FishSpecies> by lazy { pool(DepthZone.SHALLOWS, DepthZone.TWILIGHT) }
    private val deep: List<FishSpecies> by lazy { pool(DepthZone.MIDNIGHT, DepthZone.ABYSS) }

    private fun pool(vararg zones: DepthZone): List<FishSpecies> =
        FishCatalog.regular.filter { it.zone in zones && it.rarity <= GameConfig.MIGRATION_MAX_RARITY }
            .shuffled(Random(ROTATION_SEED + zones.first().ordinal))

    private fun weekAt(now: Long): Long = Math.floorDiv(now - Seasons.EPOCH_MS - START_OFFSET_MS, WEEK_MS)

    /** The migration of week [week] (counted from the first season's start). */
    fun of(week: Long): Migration {
        val start = Seasons.EPOCH_MS + START_OFFSET_MS + week * WEEK_MS
        val species = listOf(
            upper[Math.floorMod(week, upper.size.toLong()).toInt()],
            deep[Math.floorMod(week, deep.size.toLong()).toInt()],
        )
        return Migration(week, species, start, start + GameConfig.MIGRATION_HOURS * LocalDays.HOUR_MS)
    }

    /** The migration under way at [now], or null. */
    fun at(now: Long): Migration? = of(weekAt(now)).takeIf { it.isLive(now) }

    /** The migration under way, or else the next one to start. */
    fun currentOrNext(now: Long): Migration = at(now) ?: of(weekAt(now) + 1)

    /** The spawn boost for a solo cast starting at [now] (null outside migrations). */
    fun boostAt(now: Long): SpawnBoost? = at(now)?.boost

    /** Whether [species] is migrating at [now]. */
    fun isMigrating(species: FishSpecies, now: Long): Boolean = at(now)?.species?.contains(species) == true
}
