package com.deepline.fishing.render3d

import com.deepline.fishing.Building
import com.deepline.fishing.ui.village.VillageCamera
import com.deepline.fishing.ui.village.VillageLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The village's camera: every building can be tapped where it stands, the first view shows all
 * six inside the HUD, and dragging and pinching stay within the village's grounds.
 */
class VillageCameraTest {
    /** Phone screens in pixels, short and tall, and the HUD's insets (left, top, right, bottom). */
    private val screens = listOf(
        Triple(1080f, 1920f, floatArrayOf(346f, 394f, 42f, 95f)),
        Triple(1080f, 2340f, floatArrayOf(346f, 394f, 42f, 95f)),
        Triple(1200f, 2580f, floatArrayOf(396f, 450f, 48f, 108f)),
        Triple(720f, 1280f, floatArrayOf(231f, 262f, 28f, 63f)),
    )

    @Test
    fun theFirstViewShowsEveryBuildingInsideTheHud() {
        for ((w, h, insets) in screens) {
            val cam = VillageCamera().apply { fit(w, h, insets) }
            for (b in Building.entries) {
                val r = assertNotNull(cam.screenRect(b, w, h)).let { cam.screenRect(b, w, h)!! }
                assertTrue("$w x $h: $b is left of the HUD (${r[0]})", r[0] >= insets[0] - 1f)
                assertTrue("$w x $h: $b is under the top bar (${r[1]})", r[1] >= insets[1] - 1f)
                assertTrue("$w x $h: $b is off the right (${r[2]})", r[2] <= w - insets[2] + 1f)
                assertTrue("$w x $h: $b is off the bottom (${r[3]})", r[3] <= h - insets[3] + 1f)
                // Big enough to tap (its name plate is a full-size target too).
                assertTrue("$w x $h: $b is ${r[2] - r[0]} px wide", r[2] - r[0] > w * 0.08f)
            }
        }
    }

    @Test
    fun eachBuildingIsPickedWhereItStands() {
        for ((w, h, insets) in screens) {
            val cam = VillageCamera().apply { fit(w, h, insets) }
            for (zoom in listOf(1f, 1.6f)) {
                cam.zoom(zoom)
                for (b in Building.entries) {
                    val (x, y) = cam.toScreen(VillageLayout.spots.getValue(b).center, w, h) ?: continue
                    if (x !in 0f..w || y !in 0f..h) continue
                    assertEquals("$w x $h, zoom $zoom: tapping the middle of $b", b, cam.pick(x, y, w, h))
                }
            }
        }
    }

    @Test
    fun theCameraStaysOverTheVillage() {
        val (w, h, insets) = screens[1]
        val cam = VillageCamera().apply { fit(w, h, insets) }
        val far = cam.maxDistance
        repeat(50) { cam.drag(5000f, 5000f, h) }
        assertEquals(VillageLayout.MIN_X, cam.targetX, 0.01f)
        assertEquals(VillageLayout.MIN_Z, cam.targetZ, 0.01f)
        repeat(50) { cam.drag(-5000f, -5000f, h) }
        assertEquals(VillageLayout.MAX_X, cam.targetX, 0.01f)
        assertEquals(VillageLayout.MAX_Z, cam.targetZ, 0.01f)
        repeat(20) { cam.zoom(3f) }
        assertEquals(cam.minDistance, cam.distance, 0.01f)
        repeat(20) { cam.zoom(0.3f) }
        assertEquals(far, cam.distance, 0.01f)
    }

    @Test
    fun dragFollowsTheFinger() {
        val (w, h, insets) = screens[1]
        val cam = VillageCamera().apply { fit(w, h, insets) }
        val dex = VillageLayout.spots.getValue(Building.DEX).center
        val before = cam.toScreen(dex, w, h)!!
        cam.drag(100f, 0f, h)
        val after = cam.toScreen(dex, w, h)!!
        // Moved the same way as the finger, by about as much.
        assertTrue(after.first - before.first in 70f..130f)
    }
}
