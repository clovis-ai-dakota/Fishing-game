package com.deepline.fishing.ui.village

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import com.deepline.fishing.Building
import com.deepline.fishing.core.FishSpecies
import com.deepline.fishing.ui.Skins
import com.deepline.fishing.ui.render3d.Blend
import com.deepline.fishing.ui.render3d.DrawItem
import com.deepline.fishing.ui.render3d.Frame
import com.deepline.fishing.ui.render3d.Mat4
import com.deepline.fishing.ui.render3d.MeshBuilder
import com.deepline.fishing.ui.render3d.Part
import com.deepline.fishing.ui.render3d.Program
import com.deepline.fishing.ui.render3d.SceneBuilder
import com.deepline.fishing.ui.render3d.Vec3
import com.deepline.fishing.ui.theme.MenuPalette
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * Builds the village's 3D frames (on the preview's render thread): the ground, the town, your
 * boat at the pier wearing your skins, the fish of your aquarium behind its glass, the sea and a
 * few gulls. Lit by the sun by day and the moon at night ([MenuPalette.night]), with the lamps
 * and windows lit. One instance per view (it keeps the boat's scene builder).
 */
class VillageScene {
    private val boats = SceneBuilder()

    /** What the scene shows besides the village itself (read on the render thread). */
    class Looks(val palette: MenuPalette, val skins: Skins, val tankFish: List<FishSpecies>)

    fun build(widthPx: Int, heightPx: Int, t: Float, camera: VillageCamera, looks: Looks): Frame {
        val w = widthPx.toFloat()
        val h = heightPx.toFloat()
        val view = camera.view()
        val proj = camera.projection(w / h)
        val eye = camera.eye
        val p = looks.palette
        val night = p.night
        val sky = rgb(p.sky.last())
        val glow = if (night) 1f else 0f
        val globals = mapOf(
            "uView" to view,
            "uProj" to proj,
            "uTime" to floatArrayOf(t),
            "uResolution" to floatArrayOf(w, h),
            "uCamPos" to floatArrayOf(eye.x, eye.y, eye.z),
            // The sun high on the left (the moon by night), so fronts facing the sea are lit.
            "uLightDir" to if (night) floatArrayOf(0.35f, 1f, 0.6f) else floatArrayOf(-0.55f, 1f, 0.6f),
            "uLightColor" to if (night) floatArrayOf(0.42f, 0.5f, 0.72f) else floatArrayOf(1.04f, 1f, 0.92f),
            "uAmbient" to if (night) floatArrayOf(0.3f, 0.36f, 0.6f) else floatArrayOf(0.7f, 0.78f, 0.9f),
            // Far hills fade into the sky's haze.
            "uFogColor" to sky,
            "uFogNear" to floatArrayOf(camera.distance * 1.5f),
            "uFogFar" to floatArrayOf(camera.distance * 3.6f),
            "uWaterTint" to rgb(p.sea.last()),
            "uDarkness" to floatArrayOf(0f),
        )
        val items = ArrayList<DrawItem>()
        val painted = mapOf("uModel" to Mat4.identity(), "uNormalMatrix" to Mat4.normalMatrix(Mat4.identity()), "uMaterial" to floatArrayOf(5f), "uGlow" to floatArrayOf(glow))
        items += DrawItem(Program.TOON, "village:terrain", uniforms = painted)
        items += DrawItem(Program.TOON, "village:town", uniforms = painted, cullBack = false)
        // Your boat moored along the pier, bow out to sea, in your skins.
        items += boats.boatItems(looks.skins.boat, VillageLayout.boatAt, t, looks.skins.customBoat, rotY = PI.toFloat() / 2f)
        // The aquarium's fish swimming circles under the dome.
        val c = VillageLayout.spots.getValue(Building.AQUARIUM).center
        for ((i, s) in looks.tankFish.take(5).withIndex()) {
            val speed = 0.35f + 0.08f * i
            val a = t * speed + i * 1.3f
            val r = 1.6f + 0.55f * i
            val at = Vec3(c.x + cos(a) * r, 2.4f + 0.45f * i + 0.2f * sin(t * 0.7f + i), c.z + sin(a) * r)
            // Facing along the circle.
            val yaw = atan2(cos(a), sin(a))
            items += SceneBuilder.fishItem(s, at, yaw, 0f, t * 5f + i, 1f, false, i.toFloat(), len = 0.9f)
        }
        // The sea, then the glass (see-through, after what's behind it).
        val deep = rgb(lerp(p.sea.last(), Color(0xFF0B4A66), if (night) 0.3f else 0.15f))
        val shallow = rgb(lerp(p.sea.first(), Color(0xFF7FE6E0), if (night) 0.1f else 0.35f))
        items += DrawItem(
            Program.TOON, "village:sea",
            uniforms = mapOf(
                "uModel" to Mat4.identity(), "uNormalMatrix" to Mat4.normalMatrix(Mat4.identity()), "uMaterial" to floatArrayOf(6f),
                "uBase" to deep, "uAccent" to shallow, "uExtra" to sky, "uGlow" to floatArrayOf(glow),
            ),
        )
        items += DrawItem(
            Program.TOON, "village:glass",
            uniforms = painted + mapOf("uOpacity" to floatArrayOf(if (night) 0.55f else 0.34f)),
            blend = Blend.ALPHA, depthWrite = false,
        )
        items += gulls(t, night)
        return Frame(widthPx, heightPx, globals, items, clearColor = sky)
    }

    /** A few gulls circling over the bay: two wings each, flapping. */
    private fun gulls(t: Float, night: Boolean): DrawItem {
        val b = MeshBuilder()
        val white = floatArrayOf(0.96f, 0.97f, 0.98f, 1f)
        for (k in 0 until 5) {
            val a = t * (0.18f + 0.03f * k) + k * 1.9f
            val center = Vec3(-4f + 9f * cos(a + k), 14f + 2.5f * k % 4, 8f + 10f * sin(a))
            val dir = Vec3(-sin(a), 0f, cos(a))
            val side = Vec3(dir.z, 0f, -dir.x)
            val flap = sin(t * 6f + k) * 0.5f
            val span = 0.9f
            val tipL = center + side * span + Vec3(0f, flap * span, 0f)
            val tipR = center - side * span + Vec3(0f, flap * span, 0f)
            val nose = center + dir * 0.35f
            val tail = center - dir * 0.3f
            val n = Vec3.UP
            val i0 = b.vertex(nose, n, 0f, 0f, Part.PAINTED, white)
            val i1 = b.vertex(tail, n, 0f, 1f, Part.PAINTED, white)
            val i2 = b.vertex(tipL, n, 1f, 0f, Part.PAINTED, white)
            val i3 = b.vertex(tipR, n, 1f, 1f, Part.PAINTED, white)
            b.triangle(i0, i2, i1)
            b.triangle(i0, i1, i3)
        }
        return DrawItem(
            Program.TOON, null, b.build(),
            uniforms = mapOf("uModel" to Mat4.identity(), "uNormalMatrix" to Mat4.normalMatrix(Mat4.identity()), "uMaterial" to floatArrayOf(5f), "uGlow" to floatArrayOf(if (night) 0.4f else 0f)),
            cullBack = false,
        )
    }

    private fun rgb(c: Color) = floatArrayOf(c.red, c.green, c.blue)
}
