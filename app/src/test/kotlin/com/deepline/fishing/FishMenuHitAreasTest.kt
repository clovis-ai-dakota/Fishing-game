package com.deepline.fishing

import com.deepline.fishing.core.GameConfig
import com.deepline.fishing.ui.FishMenuHitAreas
import com.deepline.fishing.ui.FishMenuTarget
import com.deepline.fishing.ui.boatPreviewSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The Fish menu's scene: the boat, the line and the hook each open their own skins, and the sea
 * does nothing (it used to open the boat's skins wherever it was touched).
 */
class FishMenuHitAreasTest {
    private val cameraTop = -11.5f

    @Test
    fun boatLineHookAndSeaOnShortAndTallScreens() {
        for ((w, h) in listOf(1080f to 1636f, 720f to 1100f, 1440f to 2600f)) {
            val touch = w / 1080f * 126f // 48 dp at 2.625 px per dp, scaled with the screen
            val areas = FishMenuHitAreas.of(w, h, cameraTop, touch)
            val scale = w / GameConfig.WORLD_WIDTH
            val surface = -cameraTop * scale
            val middle = w / 2f
            // The hull, just above the waterline.
            assertEquals("$w: boat", FishMenuTarget.BOAT, areas.targetAt(middle - 1.5f * scale, surface - 0.6f * scale))
            // The hook's bend, 5 m down.
            val session = boatPreviewSession()
            assertEquals("$w: hook", FishMenuTarget.HOOK, areas.targetAt(session.hookX * scale, (session.hookDepth - cameraTop) * scale))
            // The line half-way down.
            val (lx, ly) = areas.lineMiddle
            assertEquals("$w: line", FishMenuTarget.LINE, areas.targetAt(lx, ly))
            // The open sea, left and right, and deep under the hook.
            assertNull("$w: sea left", areas.targetAt(w * 0.1f, surface + 3f * scale))
            assertNull("$w: sea right", areas.targetAt(w * 0.9f, surface + 6f * scale))
            assertNull("$w: under the hook", areas.targetAt(middle, h * 0.98f))
            // The sky away from the boat.
            assertNull("$w: sky", areas.targetAt(w * 0.05f, surface * 0.3f))
        }
    }

    @Test
    fun theHookIsAFingerSizedTarget() {
        val touch = 126f
        val areas = FishMenuHitAreas.of(1080f, 1636f, cameraTop, touch)
        assertTrue(areas.hook.width >= touch && areas.hook.height >= touch)
    }
}
