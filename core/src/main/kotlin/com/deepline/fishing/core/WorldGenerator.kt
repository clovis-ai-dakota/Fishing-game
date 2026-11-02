package com.deepline.fishing.core

import kotlin.random.Random

object WorldGenerator {
    /** Populates the water column down to slightly past [maxDepth]. */
    fun generate(maxDepth: Float, random: Random): World {
        val limit = maxDepth + 15f
        val fish = ArrayList<FishEntity>()
        var depth = GameConfig.FIRST_FISH_DEPTH
        var id = 0
        while (depth < limit) {
            val candidates = FishCatalog.all.filter { depth >= it.minDepth && depth <= it.maxDepth }
            if (candidates.isNotEmpty()) {
                val species = weightedPick(candidates, random) { it.rarity }
                val halfLength = species.bodyLengthM / 2f
                fish += FishEntity(
                    id = id++,
                    species = species,
                    sparkling = random.nextFloat() < GameConfig.SPARKLING_CHANCE,
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
            depth += GameConfig.OBSTACLE_SPACING * (0.6f + random.nextFloat() * 0.8f)
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
