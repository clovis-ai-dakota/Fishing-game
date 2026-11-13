package com.deepline.fishing

import com.deepline.fishing.core.CastPhase
import com.deepline.fishing.core.CastSession
import com.deepline.fishing.core.DepthZone
import com.deepline.fishing.core.FishCatalog
import com.deepline.fishing.core.FishEntity
import com.deepline.fishing.core.FishSpecies
import com.deepline.fishing.core.GameConfig
import com.deepline.fishing.core.Obstacle
import com.deepline.fishing.core.ObstacleKind
import com.deepline.fishing.core.RopeSpecs
import com.deepline.fishing.core.RopeStat
import com.deepline.fishing.core.World

/**
 * The casts of the store screenshots (docs/store/screenshots.md, shots 1 to 5), hand-placed and
 * played by the game's own rules: every fish lives at its species' depth, every obstacle at its
 * kind's, the rope is one a player can buy, and the hook is steered like a finger would. The
 * locale layout test plays them too, to check the cast HUD in every language.
 *
 * A scene is played frame by frame through `GameViewModel.onFrame` (so notices and game feel
 * happen as in play) until [done]; [fingerX] is where a finger is held at that moment (the aim
 * ring), or null.
 */
class StoreScene(
    val name: String,
    val specs: RopeSpecs,
    val lantern: Boolean,
    private val world: () -> World,
    /** Where the finger is (world meters) for a given state, or null when nobody touches. */
    val steer: (CastSession) -> Float?,
    val done: (CastSession) -> Boolean,
    val fingerX: Float? = null,
) {
    fun newSession() = CastSession(specs, lantern, world())

    /** Plays a session of this scene without the app (the 3D-only checks), at 60 frames a second. */
    fun play(): CastSession {
        val s = newSession()
        var frames = 0
        while (!done(s) && s.phase != CastPhase.SURFACED && frames++ < 60 * 120) s.update(FRAME, steer(s))
        return s
    }

    companion object {
        const val FRAME = 1f / 60f
    }
}

object StoreScenes {
    private val c = GameConfig.WORLD_WIDTH / 2f

    fun specs(length: Int, strength: Int, agility: Int) =
        RopeSpecs.fromLevels(mapOf(RopeStat.LENGTH to length, RopeStat.STRENGTH to strength, RopeStat.AGILITY to agility))

    private fun species(id: String): FishSpecies = FishCatalog.byId(id) ?: error("no species $id")

    /** The [n]th species (in catalogue order) living at [depth] that matches [filter]. */
    private fun at(depth: Float, n: Int = 0, filter: (FishSpecies) -> Boolean = { true }): FishSpecies =
        FishCatalog.all.filter { !it.lastBumpOnly && depth >= it.minDepth && depth <= it.maxDepth && filter(it) }
            .let { it[n % it.size] }

    private fun fish(id: Int, sp: FishSpecies, x: Float, depth: Float, dir: Int, sparkling: Boolean = false): FishEntity {
        require(depth in sp.minDepth..sp.maxDepth) { "${sp.id} doesn't live at $depth m" }
        return FishEntity(id, sp, sparkling, x, depth, dir, 0f)
    }

    private fun obstacle(id: Int, kind: ObstacleKind, x: Float, depth: Float, radius: Float): Obstacle {
        require(depth in kind.minDepth..kind.maxDepth) { "no ${kind.name} at $depth m" }
        return Obstacle(id, kind, x, depth, radius)
    }

    /** A fish whose head is on the line at x = [lineX] (the hook catches it there on the way up). */
    private fun headOn(id: Int, sp: FishSpecies, lineX: Float, depth: Float, sparkling: Boolean = false, dir: Int = 1): FishEntity =
        fish(id, sp, lineX - dir * sp.bodyLengthM / 2f * 0.8f, depth, dir, sparkling)

    /** Shot 1: a fresh cast in the sunlit shallows, the hook gliding between a rock and two fish. */
    val descent = StoreScene(
        name = "descent",
        specs = specs(length = 2, strength = 3, agility = 2),
        lantern = false,
        world = {
            World(
                fish = listOf(
                    fish(0, species("coral_darter"), c - 4.2f, 25.5f, 1),
                    fish(1, species("reed_perch"), c - 5.6f, 28.6f, 1),
                    fish(2, species("silver_sprat"), c + 5.5f, 33.5f, -1, sparkling = true),
                    fish(3, at(36f, 1) { it.zone == DepthZone.SHALLOWS }, c - 3f, 38.5f, 1),
                    fish(4, at(42f, 3) { it.zone == DepthZone.SHALLOWS }, c + 4f, 43f, -1),
                    fish(5, species("sun_bream"), c - 6f, 47f, 1),
                    fish(6, at(14f, 2) { it.zone == DepthZone.SHALLOWS }, c + 6.5f, 13f, -1),
                ),
                obstacles = listOf(obstacle(0, ObstacleKind.ROCK, c + 3.2f, 27.5f, 1.5f)),
            )
        },
        steer = { s -> if (s.hookDepth < 8f) c + 0.4f else c - 0.9f },
        done = { s -> s.hookDepth >= 20f },
        fingerX = c - 1.6f,
    )

    /** Shot 2: the Twilight Reef, the second bump just used on a jelly (2 of 4 left). */
    val bump = StoreScene(
        name = "bump",
        specs = specs(length = 3, strength = 4, agility = 3),
        lantern = false,
        world = {
            World(
                fish = listOf(
                    fish(0, species("dusk_snapper"), c - 5f, 141f, 1),
                    fish(1, species("ribbon_eel"), c + 5.5f, 146f, -1),
                    fish(2, species("reef_grouper"), c - 4.5f, 160f, 1),
                    fish(3, at(165f, 4) { it.zone == DepthZone.TWILIGHT }, c + 5f, 166f, -1),
                    fish(4, at(172f, 7) { it.zone == DepthZone.TWILIGHT }, c - 6f, 176f, 1),
                    fish(5, at(135f, 2) { it.zone == DepthZone.TWILIGHT }, c + 6f, 134f, -1),
                ),
                obstacles = listOf(
                    obstacle(0, ObstacleKind.ROCK, c, 96f, 1.2f),
                    obstacle(1, ObstacleKind.JELLY, c + 0.2f, 151f, 1.3f),
                    obstacle(2, ObstacleKind.ROCK, c + 6.5f, 182f, 1.6f),
                ),
            )
        },
        steer = { null },
        done = { s -> s.touches >= 2 && s.elapsedSeconds - s.lastTouchAtSeconds >= 0.18f },
    )

    /**
     * Shots 3 and 5: down the Twilight Reef to the end of the rope, then fish hooked by the head
     * on the way up, a Sparkling one among them. Shot 3 stops just after the third catch; shot 5
     * plays the cast to the surface (the results).
     */
    private const val LINE_UP = 10f
    private const val LINE_DOWN = 15f

    private fun ascentWorld() = World(
        fish = listOf(
            headOn(0, species("reef_grouper"), LINE_UP, 205f),
            headOn(1, species("ribbon_eel"), LINE_UP, 196f, sparkling = true, dir = -1),
            headOn(2, species("dusk_snapper"), LINE_UP, 186f),
            fish(3, species("veil_ray"), c - 5.5f, 181f, 1),
            fish(4, at(170f, 5) { it.zone == DepthZone.TWILIGHT }, c + 8f, 171f, -1),
            fish(5, at(160f, 9) { it.zone == DepthZone.TWILIGHT }, c - 5f, 163f, 1),
            fish(6, at(214f, 3) { it.zone == DepthZone.TWILIGHT }, c - 5.5f, 214f, 1),
            headOn(7, species("sun_bream"), LINE_UP, 74f, dir = -1),
            headOn(8, species("coral_darter"), LINE_UP, 38f),
        ),
        obstacles = listOf(obstacle(0, ObstacleKind.JELLY, c - 6f, 192f, 1.2f), obstacle(1, ObstacleKind.ROCK, c + 8.2f, 200f, 1.2f)),
    )

    private fun ascentSteer(s: CastSession): Float = if (s.phase == CastPhase.DESCENDING && s.hookDepth > 20f) LINE_DOWN else LINE_UP

    val ascent = StoreScene(
        name = "ascent",
        specs = specs(length = 2, strength = 3, agility = 2),
        lantern = false,
        world = ::ascentWorld,
        steer = ::ascentSteer,
        done = { s -> s.catches.size >= 3 && s.elapsedSeconds - s.lastCatchAtSeconds >= 0.12f },
    )

    val results = StoreScene(
        name = "results",
        specs = ascent.specs,
        lantern = false,
        world = ::ascentWorld,
        steer = ::ascentSteer,
        done = { s -> s.phase == CastPhase.SURFACED },
    )

    /** Shot 4: the Midnight Trench below 300 m with a Lantern: glowing fish at the lit circle's edge. */
    val dark = StoreScene(
        name = "dark",
        specs = specs(length = 6, strength = 6, agility = 5),
        lantern = true,
        world = {
            val glowing = { sp: FishSpecies -> sp.glowing }
            World(
                fish = listOf(
                    fish(0, at(366f, 0, glowing), c + 5.2f, 366f, -1),
                    fish(1, at(352f, 1, glowing), c - 6.4f, 348f, 1),
                    fish(2, species("ghost_hake"), c - 3.5f, 358f, 1),
                    fish(3, species("ink_cuttle"), c + 3.8f, 343f, -1),
                    fish(4, species("needle_viper"), c + 1.5f, 372f, -1),
                    fish(5, at(380f, 2, glowing), c - 7f, 381f, 1),
                    fish(6, species("iron_sturgeon"), c + 4f, 392f, -1),
                ),
                obstacles = listOf(obstacle(0, ObstacleKind.JELLY, c - 2.6f, 368f, 1.2f), obstacle(1, ObstacleKind.ROCK, c + 6.8f, 334f, 1.5f)),
            )
        },
        steer = { s -> if (s.hookDepth < 300f) c - 0.5f else c + 0.3f },
        done = { s -> s.hookDepth >= 352f },
    )

    val all = listOf(descent, bump, ascent, dark, results)
}
