package com.deepline.fishing.core

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class CastSessionTest {
    private val center = GameConfig.WORLD_WIDTH / 2f
    private val sprat = FishCatalog.byId("silver_sprat")!!
    private val bream = FishCatalog.byId("sun_bream")!!

    private fun specs(length: Float = 120f, weight: Float = 6f) = RopeSpecs(length, weight, 8f)

    /** A fish that does not move, placed so that the hook at [center] meets the given part of it. */
    private fun stillFish(id: Int, species: FishSpecies, depth: Float, hookOn: String, direction: Int = 1): FishEntity {
        val half = species.bodyLengthM / 2f
        val offset = when (hookOn) {
            "head" -> half * 0.8f
            "tail" -> -half * 0.8f
            else -> 0f
        }
        // hook x = fish.x + direction * offset  =>  fish.x = center - direction * offset
        return FishEntity(id, species, false, center - direction * offset, depth, direction, 0f)
    }

    private fun runToEnd(session: CastSession, steer: Float? = null) {
        var guard = 0
        while (session.phase != CastPhase.SURFACED && guard++ < 100_000) session.update(1f / 60f, steer)
    }

    private fun runWhileDescending(session: CastSession) {
        var guard = 0
        while (session.phase == CastPhase.DESCENDING && guard++ < 100_000) session.update(1f / 60f, null)
    }

    @Test
    fun hookReversesAfterFourTouches() {
        val obstacles = listOf(40f, 50f, 60f, 70f, 80f).mapIndexed { i, d ->
            Obstacle(i, ObstacleKind.ROCK, center, d, 1f)
        }
        val session = CastSession(specs(length = 120f), false, World(emptyList(), obstacles))
        runWhileDescending(session)

        assertEquals(CastPhase.ASCENDING, session.phase)
        assertEquals(ReverseReason.TOUCH_LIMIT, session.reverseReason)
        assertEquals(4, session.touches)
        assertTrue(session.maxDepthReached in 68f..71f, "reversed at ${session.maxDepthReached}")
        assertFalse(obstacles[4].touched)
        assertTrue(obstacles[3].snagged, "the last bump snags what it hit")
    }

    @Test
    fun hittingFishHeadsOnTheWayDownCountsAsBumps() {
        val fish = listOf(40f, 50f, 60f, 70f).mapIndexed { i, d -> stillFish(i, sprat, d, "head") }
        val session = CastSession(specs(length = 120f), false, World(fish, emptyList()))
        runWhileDescending(session)

        assertEquals(ReverseReason.TOUCH_LIMIT, session.reverseReason)
        assertTrue(session.maxDepthReached < 71f)
        assertTrue(fish.all { it.touchedOnDescent })
    }

    @Test
    fun brushingBodiesAndTailsOnTheWayDownIsFree() {
        val fish = listOf(40f, 50f, 60f, 70f).mapIndexed { i, d -> stillFish(i, sprat, d, if (i % 2 == 0) "body" else "tail") }
        val session = CastSession(specs(length = 100f), false, World(fish, emptyList()))
        runWhileDescending(session)

        assertEquals(0, session.touches)
        assertEquals(ReverseReason.ROPE_END, session.reverseReason)
        assertTrue(fish.none { it.touchedOnDescent })
    }

    @Test
    fun theLastBumpSnagsTheObjectItHitForBigPoints() {
        val obstacles = listOf(40f, 50f, 60f).mapIndexed { i, d -> Obstacle(i, ObstacleKind.DRIFTWOOD, center, d, 1f) } +
            Obstacle(3, ObstacleKind.ROCK, center, 70f, 1f)
        val session = CastSession(specs(length = 120f), false, World(emptyList(), obstacles))
        val events = ArrayList<CastEvent>()
        var guard = 0
        while (session.phase != CastPhase.SURFACED && guard++ < 100_000) {
            session.update(1f / 60f, null)
            events += session.drainEvents()
        }

        val result = session.result!!
        val snag = result.snags.single()
        assertEquals(ObstacleKind.ROCK, snag.kind)
        assertEquals(Scoring.snagPoints(ObstacleKind.ROCK, 70f), snag.points)
        assertEquals(snag.points, result.totalPoints)
        assertTrue(snag.points > Scoring.points(FishCatalog.byId("dusk_snapper")!!, 70f, false), "objects are worth big points")
        assertEquals(ObstacleKind.ROCK.weightKg, result.totalWeightKg)
        assertTrue(result.lastBumpHooked)
        assertTrue(obstacles.take(3).none { it.snagged }, "only the last bump snags")
        assertTrue(events.filterIsInstance<CastEvent.LastBumpHooked>().single().snag != null)
    }

    @Test
    fun theLastBumpHooksTheFishItHitEvenIfItIsHeavy() {
        val leviathan = FishCatalog.byId("pale_leviathan")!! // 24 kg on a 6 kg line
        val obstacles = listOf(40f, 50f, 60f).mapIndexed { i, d -> Obstacle(i, ObstacleKind.ROCK, center, d, 1f) }
        val big = stillFish(10, leviathan, 75f, "head")
        val onTheWayUp = stillFish(11, sprat, 20f, "head")
        val session = CastSession(specs(length = 120f, weight = 6f), false, World(listOf(big, onTheWayUp), obstacles))
        runToEnd(session)

        val result = session.result!!
        val hooked = result.catches.single { it.byLastBump }
        assertEquals(leviathan, hooked.species)
        assertTrue(result.snags.isEmpty())
        assertFalse(onTheWayUp.caught, "the line is already over its weight limit")
        assertEquals(1, result.catches.size)
    }

    @Test
    fun aCastRecordsWhatEachBumpHitAndReCaughtFish() {
        // Bump a grouper's head at 130 m, then a rock, driftwood and a jelly; on the way up the
        // hook meets the same grouper's head again.
        val grouper = stillFish(0, FishCatalog.byId("reef_grouper")!!, 130f, "head")
        val obstacles = listOf(
            Obstacle(1, ObstacleKind.ROCK, center, 140f, 1f),
            Obstacle(2, ObstacleKind.DRIFTWOOD, center, 150f, 1f),
            Obstacle(3, ObstacleKind.JELLY, center, 160f, 1f),
        )
        val session = CastSession(specs(length = 200f, weight = 20f), false, World(listOf(grouper), obstacles))
        runToEnd(session)

        val r = session.result!!
        assertEquals(listOf(BumpKind.FISH_HEAD, BumpKind.ROCK, BumpKind.DRIFTWOOD, BumpKind.JELLY), r.bumps)
        assertEquals(ObstacleKind.JELLY, r.snags.single().kind)
        assertTrue(r.catches.single().bumpedOnDescent)
        assertEquals(setOf(Secrets.LAST_BUMP, Secrets.GRAND_TOUR, Secrets.SECOND_ENCOUNTER), Secrets.revealedBy(r))
        assertEquals(20f, r.lineStrengthKg)
    }

    @Test
    fun theHookGlidesOntoTheFingerWithoutOvershooting() {
        val session = CastSession(specs(), false, World(emptyList(), emptyList()))
        val target = center + 1f
        var previous = session.hookX
        repeat(30) { // half a second
            session.update(1f / 60f, target)
            assertTrue(session.hookX >= previous && session.hookX <= target + 1e-4f)
            previous = session.hookX
        }
        assertEquals(target, session.hookX, 0.01f)

        // Following a finger that moves slower than the rope's agility, it stays close behind.
        var finger = target
        repeat(60) {
            finger += 3f / 60f
            session.update(1f / 60f, finger)
        }
        assertTrue(kotlin.math.abs(finger - session.hookX) < 0.35f, "gap ${finger - session.hookX}")
    }

    @Test
    fun touchesInTheFirst30mDontCostBumps() {
        // A fish right where the line lands, plus a rock and more fish heads in the first 30 m.
        val fish = listOf(1f, 12f, 25f).mapIndexed { i, d -> stillFish(i, sprat, d, "head") }
        val rock = Obstacle(0, ObstacleKind.ROCK, center, 18f, 1.5f)
        val session = CastSession(specs(length = 60f), false, World(fish, listOf(rock)))
        runWhileDescending(session)

        assertEquals(0, session.touches)
        assertEquals(ReverseReason.ROPE_END, session.reverseReason)
        assertTrue(fish.none { it.touchedOnDescent || it.isFleeing })
        assertFalse(rock.touched)
    }

    @Test
    fun somethingStillTouchingTheHookWhenItLeavesTheBumpFreeZoneIsLetThrough() {
        // A big rock spanning the 30 m line: touched from 27.9 m on, still overlapping at 30 m.
        val rock = Obstacle(0, ObstacleKind.ROCK, center, 30f, 1.5f)
        val below = Obstacle(1, ObstacleKind.ROCK, center, 45f, 1f)
        val session = CastSession(specs(length = 60f), false, World(emptyList(), listOf(rock, below)))
        runWhileDescending(session)

        assertFalse(rock.touched)
        assertTrue(below.touched, "bumps count again below 30 m")
        assertEquals(1, session.touches)
    }

    @Test
    fun fishPassedInTheBumpFreeZoneCanStillBeCaughtOnTheWayUp() {
        val fish = stillFish(0, sprat, 15f, "head")
        val session = CastSession(specs(length = 50f), false, World(listOf(fish), emptyList()))
        runToEnd(session)
        assertEquals(0, session.result!!.touchesUsed)
        assertEquals(1, session.result!!.catches.size)
    }

    @Test
    fun hookReversesAtRopeEndWhenNothingIsTouched() {
        val session = CastSession(specs(length = 60f), false, World(emptyList(), emptyList()))
        runToEnd(session)
        val result = assertNotNull(session.result)
        assertEquals(ReverseReason.ROPE_END, result.reverseReason)
        assertEquals(60f, result.maxDepth)
        assertEquals(0, result.touchesUsed)
    }

    @Test
    fun nudgingTheHeadOnTheWayUpCatchesTheFish() {
        val fish = stillFish(0, sprat, 30f, "head")
        val session = CastSession(specs(length = 50f), false, World(listOf(fish), emptyList()))
        runToEnd(session)

        val result = assertNotNull(session.result)
        assertEquals(1, result.catches.size)
        assertEquals(sprat, result.catches[0].species)
        assertEquals(30f, result.catches[0].depth)
        assertEquals(Scoring.points(sprat, 30f, false), result.totalPoints)
    }

    @Test
    fun headIsDeterminedByFacingDirection() {
        val facingLeft = stillFish(0, sprat, 30f, "head", direction = -1)
        val session = CastSession(specs(length = 50f), false, World(listOf(facingLeft), emptyList()))
        runToEnd(session)
        assertEquals(1, session.result!!.catches.size)
    }

    @Test
    fun nudgingTheTailYieldsNothing() {
        val fish = stillFish(0, sprat, 30f, "tail")
        val session = CastSession(specs(length = 50f), false, World(listOf(fish), emptyList()))
        runToEnd(session)
        assertTrue(session.result!!.catches.isEmpty())
        assertFalse(fish.caught)
    }

    @Test
    fun catchesStopAtTheLinesWeightLimit() {
        // 1.6 kg each; a 4 kg line can hold two of them but not three.
        val fish = listOf(20f, 30f, 40f).mapIndexed { i, d -> stillFish(i, bream, d, "head") }
        val session = CastSession(specs(length = 50f, weight = 4f), false, World(fish, emptyList()))
        val events = ArrayList<CastEvent>()
        var guard = 0
        while (session.phase != CastPhase.SURFACED && guard++ < 100_000) {
            session.update(1f / 60f, null)
            events += session.drainEvents()
        }

        val result = session.result!!
        assertEquals(2, result.catches.size)
        assertTrue(result.totalWeightKg <= 4f)
        // Caught on the way up, so the deepest ones are hooked first.
        assertEquals(listOf(40f, 30f), result.catches.map { it.depth })
        assertTrue(events.any { it is CastEvent.TooHeavy })
    }

    @Test
    fun steeringMovesTheHookAtTheRopesAgility() {
        val session = CastSession(specs(), false, World(emptyList(), emptyList()))
        session.update(0.05f, 0f)
        val moved = center - session.hookX
        assertEquals(8f * 0.05f, moved, 0.01f)
    }

    @Test
    fun generatedWorldOnlyContainsFishInTheirHabitat() {
        val world = WorldGenerator.generate(620f, Random(42))
        assertTrue(world.fish.size > 100)
        for (f in world.fish) {
            assertTrue(f.depth >= f.species.minDepth && f.depth <= f.species.maxDepth, f.species.id)
            assertTrue(f.x - f.halfLength >= 0f && f.x + f.halfLength <= GameConfig.WORLD_WIDTH)
        }
        assertTrue(world.obstacles.isNotEmpty())
    }
}
