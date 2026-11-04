package com.deepline.fishing.core

import com.deepline.fishing.core.online.CastDto
import com.deepline.fishing.core.online.CastValidation
import com.deepline.fishing.core.online.OnlineProtocol
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The fast reel (a full line hauled straight up) and the Crowd surfer secret. */
class FastReelTest {
    private val center = GameConfig.WORLD_WIDTH / 2f

    /** Descends to the end of the rope, then reels up until [stop] says so. */
    private fun reelUntil(session: CastSession, stop: (CastSession) -> Boolean) {
        var guard = 0
        while (session.phase != CastPhase.SURFACED && guard++ < 200_000) {
            if (session.phase == CastPhase.ASCENDING && stop(session)) return
            session.update(1f / 60f, null)
        }
    }

    /** A line whose limit is reached by snagging a rock with the last bump. */
    private fun fullAfterSnag(length: Float, weight: Float): CastSession {
        val rocks = (0 until 4).map { i -> Obstacle(i, ObstacleKind.ROCK, center, length - 40f + i * 8f, 1f) }
        return CastSession(RopeSpecs(length, weight, 8f), false, World(emptyList(), rocks))
    }

    @Test
    fun aFullLineCanBeHauledUpInAtMostOneAndAHalfSeconds() {
        // A 4 kg rock on a 4 kg line, the hook at about 600 m: nothing else can fit.
        val session = fullAfterSnag(620f, ObstacleKind.ROCK.weightKg)
        reelUntil(session) { true }
        assertEquals(CastPhase.ASCENDING, session.phase)
        assertTrue(session.lineIsFull)
        assertTrue(session.canFastReel)
        val start = session.elapsedSeconds
        assertTrue(session.startFastReel())
        assertFalse(session.canFastReel, "only once")
        while (session.phase != CastPhase.SURFACED) session.update(1f / 60f, null)
        val took = session.elapsedSeconds - start
        assertTrue(took <= GameConfig.FAST_REEL_MAX_SECONDS + 0.02f, "took $took s from ${session.result!!.maxDepth} m")
        assertNotNull(session.result!!.fastReelAt)
    }

    @Test
    fun nearTheSurfaceItTakesLessThanThat() {
        val session = fullAfterSnag(100f, ObstacleKind.ROCK.weightKg)
        reelUntil(session) { it.hookDepth < 15f }
        assertTrue(session.startFastReel())
        val start = session.elapsedSeconds
        while (session.phase != CastPhase.SURFACED) session.update(1f / 60f, null)
        assertTrue(session.elapsedSeconds - start < 1.1f)
    }

    @Test
    fun itIsOnlyOfferedWhenNothingMoreCanFit() {
        // Plenty of room left: no fast reel.
        val roomy = CastSession(RopeSpecs(120f, 30f, 8f), false, World(emptyList(), emptyList()))
        reelUntil(roomy) { true }
        assertFalse(roomy.lineIsFull)
        assertFalse(roomy.startFastReel())
        // Not on the way down either.
        val down = fullAfterSnag(620f, ObstacleKind.ROCK.weightKg)
        down.update(1f / 60f, null)
        assertFalse(down.canFastReel)
        // The rule uses the catalogue: the lightest species living above the hook.
        val lightest = FishCatalog.regular.filter { it.minDepth <= 10f }.minOf { it.weightKg }
        assertEquals(lightest, FishCatalog.lightestAbove(10f))
        assertTrue(FishCatalog.lightestAbove(600f) <= FishCatalog.lightestAbove(100f))
    }

    @Test
    fun brushingFishWithAFullLineCountsForTheCrowdSurfer() {
        // A full line (rock snagged), then a column of fish on the way up, hook dead centre.
        val sprat = FishCatalog.byId("silver_sprat")!!
        val fish = (0 until 60).map { i -> FishEntity(100 + i, sprat, false, center, 590f - i * 9f, 1, 0f) }
        val rocks = (0 until 4).map { i -> Obstacle(i, ObstacleKind.ROCK, center + 6f, 600f + i * 5f, 1f) }
        val session = CastSession(RopeSpecs(620f, ObstacleKind.ROCK.weightKg, 8f), false, World(fish, rocks), startX = center + 6f)
        var guard = 0
        while (session.phase != CastPhase.SURFACED && guard++ < 200_000) {
            // Down at the rocks' column, back to the fish column on the way up.
            session.update(1f / 60f, if (session.phase == CastPhase.ASCENDING) center else center + 6f)
        }
        val result = session.result!!
        assertTrue(result.fullLineTouches >= SecretRules.CROWD_SURFER_COUNT, "touched ${result.fullLineTouches}")
        assertTrue(result.catches.isEmpty(), "the line was full")
        assertTrue(Secrets.CROWD_SURFER in Secrets.revealedBy(result))
        assertEquals(4, Secrets.tierOf(Secrets.CROWD_SURFER))
    }

    @Test
    fun theFastReelEndsTheCrowdSurfing() {
        val sprat = FishCatalog.byId("silver_sprat")!!
        val fish = (0 until 30).map { i -> FishEntity(100 + i, sprat, false, center, 300f - i * 9f, 1, 0f) }
        val rocks = (0 until 4).map { i -> Obstacle(i, ObstacleKind.ROCK, center, 302f + i * 5f, 1f) }
        val session = CastSession(RopeSpecs(330f, ObstacleKind.ROCK.weightKg, 8f), false, World(fish, rocks))
        reelUntil(session) { it.canFastReel }
        session.startFastReel()
        while (session.phase != CastPhase.SURFACED) session.update(1f / 60f, null)
        assertTrue(session.result!!.fullLineTouches <= 1)
    }

    @Test
    fun theServerChecksFullLineTouches() {
        val specs = OnlineProtocol.tournamentSpecs
        val turn = BattleTurn(0, 1, 0, 1L, null)
        val snag = com.deepline.fishing.core.online.SnagDto(ObstacleKind.ROCK, 300f)
        val bumps = listOf(BumpKind.ROCK, BumpKind.ROCK, BumpKind.ROCK, BumpKind.ROCK)
        val base = CastDto(emptyList(), 300f, GameConfig.DESCENT_TOUCH_LIMIT, ReverseReason.TOUCH_LIMIT, 40f, listOf(snag), bumps)
        // A 4 kg rock on the tournament line leaves room for small fish: not full, so no touches.
        assertNull(CastValidation.toBattleCast(base.copy(fullLineTouches = 50), turn, specs))
        assertNull(CastValidation.toBattleCast(base.copy(fullLineTouches = -1), turn, specs))
        assertNotNull(CastValidation.toBattleCast(base, turn, specs))
    }
}
