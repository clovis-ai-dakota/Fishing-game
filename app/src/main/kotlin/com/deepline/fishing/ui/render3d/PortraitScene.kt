package com.deepline.fishing.ui.render3d

import com.deepline.fishing.core.FishSpecies
import com.deepline.fishing.ui.FishShape
import com.deepline.fishing.ui.FishStyles
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.tan

/** How much of a fish the Dex shows: a dark shape, a tinted shape, or the full colours. */
enum class PortraitLook { UNKNOWN, SEEN, CAUGHT }

/**
 * A single fish on a transparent background, for the Dex: framed to fill the picture, lit like a
 * studio shot, turned by [yaw] (radians; [SceneBuilder.FACING_RIGHT] is the usual 3/4 view).
 */
object PortraitScene {
    fun build(species: FishSpecies, look: PortraitLook, widthPx: Int, heightPx: Int, yaw: Float, timeSeconds: Float, sparkling: Boolean = false): Frame {
        val aspect = widthPx / heightPx.toFloat()
        val fov = 30f * PI.toFloat() / 180f
        // The fish is 1 long; fins, tail and lures stick out a little. Fit width and height.
        val shape = FishStyles.of(species).shape
        val extentX = if (shape == FishShape.ANGLER) 0.78f else 0.62f
        val extentY = max(0.32f, species.heightRatio * (if (shape == FishShape.ANGLER) 1.15f else 0.95f))
        val center = Vec3(if (shape == FishShape.ANGLER) 0.08f else 0f, PORTRAIT_Y + species.heightRatio * 0.06f, 0f)
        val halfTan = tan(fov / 2f)
        val distance = max(extentX / (halfTan * aspect), extentY / halfTan) * 1.12f
        val eye = Vec3(center.x, center.y + distance * 0.08f, distance)
        val view = Mat4.lookAt(eye, center, Vec3.UP)
        val proj = Mat4.perspective(fov, aspect, max(0.05f, distance - 3f), distance + 3f)
        val globals = mapOf(
            "uView" to view,
            "uProj" to proj,
            "uTime" to f(timeSeconds),
            "uResolution" to f(widthPx.toFloat(), heightPx.toFloat()),
            "uCamPos" to f(eye.x, eye.y, eye.z),
            "uLightDir" to f(0.45f, 1f, 0.8f),
            "uLightColor" to f(1f, 1f, 1f),
            "uAmbient" to f(0.55f, 0.65f, 0.75f),
            "uFogColor" to f(0f, 0f, 0f),
            "uWaterTint" to f(0f, 0f, 0f),
            "uFogNear" to f(1e5f),
            "uFogFar" to f(2e5f),
            "uDarkness" to f(0f),
        )
        val silhouette = when (look) {
            PortraitLook.UNKNOWN -> 1f
            PortraitLook.SEEN -> 2f
            PortraitLook.CAUGHT -> 0f
        }
        val item = SceneBuilder.fishItem(
            species, Vec3(0f, PORTRAIT_Y, 0f), yaw, 0f,
            swimPhase = timeSeconds * 4f, amplitude = if (look == PortraitLook.CAUGHT) 0.7f else 0.4f,
            sparkling = sparkling && look == PortraitLook.CAUGHT, seed = 1f, silhouette = silhouette, len = 1f,
        )
        // Outlines are sized for a 540 px wide view: scale them to this picture.
        return Frame(widthPx, heightPx, globals, listOf(item))
    }

    /** Held above y = 0 so the underwater tint doesn't apply. */
    private const val PORTRAIT_Y = 10f
}
