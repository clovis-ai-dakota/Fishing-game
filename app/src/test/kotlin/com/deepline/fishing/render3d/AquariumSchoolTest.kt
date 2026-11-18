package com.deepline.fishing.render3d

import com.deepline.fishing.core.FishCatalog
import com.deepline.fishing.ui.render3d.AquariumScene
import com.deepline.fishing.ui.render3d.AquariumSchool
import com.deepline.fishing.ui.render3d.Vec3
import org.junit.Assert.assertTrue
import org.junit.Test

/** The aquarium's fish live together: every kind of moment happens, and nobody leaves the tank. */
class AquariumSchoolTest {
    @Test
    fun fishSchoolChaseFleeMeetFollowAndNibbleInsideTheTank() {
        // Small and big species together, as a player's tank would hold them.
        val bySize = FishCatalog.all.sortedBy { it.bodyLengthM }
        val fish = bySize.take(4) + bySize.takeLast(2)
        val school = AquariumSchool(seed = 3)
        val decor = listOf(Vec3(-2f, 1.2f, -0.5f), Vec3(1.5f, 0.8f, 0.4f))
        school.sync(fish, 8f, decor)
        assertTrue("some fish are big", school.swimmers.any { it.big })
        assertTrue("some fish are small", school.swimmers.any { !it.big })
        val seen = HashSet<AquariumSchool.Mood>()
        repeat(30 * 240) {
            school.step(1f / 30f)
            for (s in school.swimmers) {
                seen += s.mood
                assertTrue(listOf(s.x, s.y, s.z, s.vx, s.vy, s.vz, s.yaw).all { it.isFinite() })
                assertTrue("out through the glass: ${s.x}", kotlin.math.abs(s.x) <= 4f)
                assertTrue("in the sand or out of the water: ${s.y}", s.y >= AquariumSchool.MIN_Y && s.y <= AquariumScene.HEIGHT)
                assertTrue(kotlin.math.abs(s.z) <= AquariumScene.DEPTH / 2f)
                assertTrue("too fast: ${s.speed}", s.speed < 3f)
            }
        }
        for (m in AquariumSchool.Mood.entries) assertTrue("never seen: $m (saw $seen)", m in seen)
        // Double-tapped, a fish reacts: the five reactions in turn, each one ending.
        val tapped = school.swimmers.first()
        val reactions = HashSet<AquariumSchool.Reaction>()
        repeat(5) {
            school.react(tapped)
            reactions += tapped.reaction!!
            var guard = 0
            while (tapped.reaction != null && guard++ < 200) {
                school.step(1f / 30f)
                assertTrue(listOf(tapped.roll, tapped.puff, tapped.loop, tapped.x, tapped.y).all { it.isFinite() })
                assertTrue(tapped.puff in 1f..1.6f)
            }
            assertTrue("a reaction never ends", tapped.reaction == null)
        }
        assertTrue(reactions == AquariumSchool.Reaction.entries.toSet())
        // A fish taken out of the tank leaves the school; the others keep swimming where they are.
        val before = school.swimmers.drop(1).map { it.x }
        school.sync(fish.drop(1), 8f, decor)
        assertTrue(school.swimmers.size == fish.size - 1)
        assertTrue(school.swimmers.map { it.x } == before)
    }
}
