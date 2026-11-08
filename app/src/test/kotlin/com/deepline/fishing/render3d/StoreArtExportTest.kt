package com.deepline.fishing.render3d

import com.deepline.fishing.core.BotBrain
import com.deepline.fishing.core.BotDifficulty
import com.deepline.fishing.core.CastEvent
import com.deepline.fishing.core.CastPhase
import com.deepline.fishing.core.CastSession
import com.deepline.fishing.core.FishCatalog
import com.deepline.fishing.core.FishEntity
import com.deepline.fishing.core.GameConfig
import com.deepline.fishing.core.Obstacle
import com.deepline.fishing.core.ObstacleKind
import com.deepline.fishing.core.RopeSpecs
import com.deepline.fishing.core.World
import com.deepline.fishing.ui.render3d.SceneBuilder
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import kotlin.random.Random

/**
 * Store art drawn by the game's own 3D renderer (WebGL preview, `tools/render3d-preview`):
 * - the feature graphic's scene (1024 x 500), always exported (cheap), drawn and titled by
 *   `tools/store-graphics/make-store-graphics.sh`;
 * - the promo video's frames: real casts played by the computer rival's steering, only when
 *   `DEEPLINE_PROMO_VIDEO=1` (a few hundred frames), turned into an MP4 by
 *   `tools/promo-video/make-promo-video.sh`.
 */
class StoreArtExportTest {
    private val c = GameConfig.WORLD_WIDTH / 2f

    private fun fish(id: Int, species: String, x: Float, depth: Float, dir: Int, sparkling: Boolean = false, speed: Float = 0f) =
        FishEntity(id, FishCatalog.byId(species)!!, sparkling, x, depth, dir, speed)

    @Test
    fun featureGraphicScene() {
        // The boat on a sunny sea, the line going down past a few friendly shallows fish. Drawn
        // 1300 px wide (the world's 20 m) so that, cropped to Play's 1024 px from the left, the boat
        // sits right of centre and the title has the left side to itself.
        val world = World(
            fish = listOf(
                fish(0, "sun_bream", 13.6f, 1.9f, -1),
                fish(1, "coral_darter", 3.0f, 2.6f, 1),
                fish(2, "silver_sprat", 11.8f, 2.9f, -1, sparkling = true),
                fish(3, "reed_perch", 6.6f, 1.5f, 1),
            ),
            obstacles = listOf(Obstacle(0, ObstacleKind.JELLY, 15.3f, 2.5f, 0.8f)),
        )
        val session = CastSession(RopeSpecs(120f, 10f, 9f), false, world)
        while (session.hookDepth < 2.2f) session.update(1f / 60f, c + 0.8f)
        Render3dExport.write(SceneBuilder().build(session, 1.6f, 1300, 500, cameraTop = -4.0f), File("build/store-art/feature_graphic.json"))
    }

    /** One clip of a cast: frames from [fromSeconds] (cast time) for [seconds], with captions. */
    private class Clip(val session: CastSession, val steer: BotBrain?, val fromSeconds: Float, val seconds: Float, val caption: (CastSession) -> String)

    @Test
    fun promoVideoFrames() {
        assumeTrue("set DEEPLINE_PROMO_VIDEO=1 to export the promo video's frames", System.getenv("DEEPLINE_PROMO_VIDEO") == "1")
        val dir = File("build/promo-video").apply { deleteRecursively() }
        val seq = Render3dSequence(dir)
        val fps = 25
        val w = 608
        val h = 1080

        // Clip 1: a full cast (about 17 s) in the sunlit shallows and the twilight reef, steered by
        // the Hard rival's brain: dodging on the way down, hooking fish on the way up. The seed is
        // the one, among a few, that lands the most fish.
        val specs = RopeSpecs(130f, 14f, 9f)
        fun play(seed: Long): Int {
            val s = CastSession.seeded(specs, seed)
            val brain = BotBrain(BotDifficulty.HARD, Random(seed))
            var t = 0
            while (s.phase != CastPhase.SURFACED && t++ < 60 * 60) s.update(1f / 60f, brain.steer(s, 1f / 60f))
            return s.catches.size * 10 - s.touches
        }
        val seed = (1L..24L).maxByOrNull(::play)!!
        val main = CastSession.seeded(specs, seed)
        val clips = mutableListOf(
            Clip(main, BotBrain(BotDifficulty.HARD, Random(seed)), 0f, 60f) { s ->
                when {
                    // The rival dodges nearly everything: the caption follows the depth instead.
                    s.phase == CastPhase.DESCENDING && s.hookDepth < specs.lengthM * 0.45f -> "steer"
                    s.phase == CastPhase.DESCENDING -> "dodge"
                    else -> "hook"
                }
            },
        )
        // Clip 2: the dark below 300 m with a Lantern (4 s), on the way down.
        val dark = CastSession.seeded(RopeSpecs(520f, 30f, 9f), 7L, lanternActive = true)
        val darkBrain = BotBrain(BotDifficulty.HARD, Random(7L))
        clips += Clip(dark, darkBrain, -1f, 4f) { "dark" }

        var frame = 0
        val events = ArrayList<Map<String, Any?>>()
        for ((index, clip) in clips.withIndex()) {
            val s = clip.session
            val scene = SceneBuilder()
            val step = 1f / 60f
            // A negative start: fast-forward (unrendered) until the hook is past the dark depth.
            if (clip.fromSeconds < 0f) {
                while (s.hookDepth < GameConfig.DARK_DEPTH + 30f && s.phase == CastPhase.DESCENDING) s.update(step, clip.steer?.steer(s, step))
                s.drainEvents()
            }
            val start = s.elapsedSeconds
            var next = start
            while (s.phase != CastPhase.SURFACED && s.elapsedSeconds - start < clip.seconds) {
                // Render at the video's frame rate, simulate at 60 Hz in between.
                if (s.elapsedSeconds >= next) {
                    next += 1f / fps
                    val result = s.catches
                    seq.add(
                        scene.build(s, s.elapsedSeconds, w, h),
                        mapOf(
                            "clip" to index,
                            "caption" to clip.caption(s),
                            "depth" to s.hookDepth.toInt(),
                            "bumps" to s.touches,
                            "bumpLimit" to GameConfig.DESCENT_TOUCH_LIMIT,
                            "fish" to result.size,
                            "points" to result.sumOf { it.points },
                            "phase" to s.phase.name.lowercase(),
                        ),
                    )
                    frame++
                }
                s.update(step, clip.steer?.steer(s, step))
                for (e in s.drainEvents()) {
                    val kind = when (e) {
                        is CastEvent.Touched -> "bump"
                        is CastEvent.Caught -> if (e.record.sparkling || com.deepline.fishing.core.Progression.rarityStars(e.record.species) >= 3) "catch_rare" else "catch"
                        is CastEvent.LastBumpHooked -> if (e.fish != null) "catch" else "snag"
                        is CastEvent.Surfaced -> "surface"
                        else -> null
                    } ?: continue
                    events += mapOf("frame" to frame, "kind" to kind)
                }
            }
            if (index == 0) events += mapOf("frame" to frame, "kind" to "clip_end")
        }
        val total = seq.finish()
        File(dir, "events.json").writeText(
            events.joinToString(",", "{\"fps\":$fps,\"frames\":$total,\"seed\":$seed,\"events\":[", "]}") { e ->
                "{\"frame\":${e["frame"]},\"kind\":\"${e["kind"]}\"}"
            },
        )
        println("promo video: $total frames from seed $seed")
    }
}
