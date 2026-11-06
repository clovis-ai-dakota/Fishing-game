package com.deepline.fishing.core

import kotlin.random.Random

object WorldGenerator {
    /**
     * Populates the water column down to slightly past [maxDepth]. A [boost] (a migration, solo
     * casts only) makes some species more common; it never changes how many fish there are.
     */
    fun generate(maxDepth: Float, random: Random, sparklingChance: Float = GameConfig.SPARKLING_CHANCE, boost: SpawnBoost? = null): World {
        val limit = maxDepth + 15f
        val fish = ArrayList<FishEntity>()
        var depth = GameConfig.FIRST_FISH_DEPTH
        var id = 0
        while (depth < limit) {
            val candidates = FishCatalog.regular.filter { depth >= it.minDepth && depth <= it.maxDepth }
            if (candidates.isNotEmpty()) {
                val species = weightedPick(candidates, random) { boost?.weightOf(it) ?: it.rarity }
                val halfLength = species.bodyLengthM / 2f
                fish += FishEntity(
                    id = id++,
                    species = species,
                    sparkling = random.nextFloat() < sparklingChance,
                    x = halfLength + random.nextFloat() * (GameConfig.WORLD_WIDTH - 2 * halfLength),
                    depth = depth,
                    direction = if (random.nextBoolean()) 1 else -1,
                    baseSpeed = species.speed * (0.8f + random.nextFloat() * 0.4f),
                )
            }
            depth += GameConfig.FISH_SPACING * (0.5f + random.nextFloat())
        }

        val obstacles = ArrayList<Obstacle>()
        depth = GameConfig.FIRST_OBSTACLE_DEPTH
        id = 0
        while (depth < limit) {
            val kinds = ObstacleKind.entries.filter { depth >= it.minDepth && depth <= it.maxDepth }
            val kind = kinds[random.nextInt(kinds.size)]
            val radius = 0.9f + random.nextFloat() * 1.1f
            obstacles += Obstacle(
                id = id++,
                kind = kind,
                x = radius + random.nextFloat() * (GameConfig.WORLD_WIDTH - 2 * radius),
                depth = depth,
                radius = radius,
            )
            depth += GameConfig.obstacleSpacingAt(depth) * (0.6f + random.nextFloat() * 0.8f)
        }
        // The legendary fish, at the very bottom, in a few of the casts that can reach it.
        // (Drawn last so it doesn't change the rest of the water for a given seed.)
        val bottom = minOf(maxDepth, GameConfig.SOVEREIGN_MAX_DEPTH) - 1f
        if (bottom >= GameConfig.SOVEREIGN_MIN_DEPTH && random.nextFloat() < GameConfig.SOVEREIGN_SPAWN_CHANCE) {
            val s = FishCatalog.sovereign
            val half = s.bodyLengthM / 2f
            fish += FishEntity(
                id = fish.size + 10_000,
                species = s,
                sparkling = false,
                x = half + random.nextFloat() * (GameConfig.WORLD_WIDTH - 2 * half),
                depth = GameConfig.SOVEREIGN_MIN_DEPTH + random.nextFloat() * (bottom - GameConfig.SOVEREIGN_MIN_DEPTH),
                direction = if (random.nextBoolean()) 1 else -1,
                baseSpeed = s.speed,
            )
        }
        return World(fish, obstacles)
    }

    private fun <T> weightedPick(items: List<T>, random: Random, weight: (T) -> Int): T {
        val total = items.sumOf(weight)
        var roll = random.nextInt(total)
        for (item in items) {
            roll -= weight(item)
            if (roll < 0) return item
        }
        return items.last()
    }
}
