package com.deepline.fishing.ui.render3d

import com.deepline.fishing.core.Cosmetic
import com.deepline.fishing.core.FishCatalog

/**
 * One frame to draw, as plain data: built on the game thread from the game state, then drawn by
 * a GL backend (the app's OpenGL ES renderer, or the WebGL preview used to check the shaders).
 * Items are drawn in list order.
 */
class Frame(
    val widthPx: Int,
    val heightPx: Int,
    /** Uniforms shared by every program (camera, light, fog, darkness...). */
    val globals: Map<String, FloatArray>,
    val items: List<DrawItem>,
)

enum class Blend { OPAQUE, ALPHA, ADD }

/** The shader programs, each a vertex + fragment pair in `assets/shaders`. */
enum class Program(val vertex: String, val fragment: String) {
    TOON("toon.vert", "toon.frag"),
    OUTLINE("outline.vert", "outline.frag"),
    BACKGROUND("screen.vert", "background.frag"),
    DARKNESS("screen.vert", "darkness.frag"),
    SPRITE("sprite.vert", "sprite.frag"),
}

class DrawItem(
    val program: Program,
    /** A mesh from [Meshes] (cached on the GPU), or null with [dynamicMesh] set. */
    val meshKey: String?,
    val dynamicMesh: MeshData? = null,
    val uniforms: Map<String, FloatArray> = emptyMap(),
    val blend: Blend = Blend.OPAQUE,
    /** Ink outline width in pixels (0: none). Drawn first, with [Program.OUTLINE]. */
    val outlinePx: Float = 0f,
    val outlineColor: FloatArray? = null,
    val cullBack: Boolean = true,
    val depthTest: Boolean = true,
    val depthWrite: Boolean = true,
)

/** Builds the cached meshes by key ("fish:silver_sprat", "rock:3", "boat:ROYAL_BOAT"...). */
object Meshes {
    fun get(key: String): MeshData {
        val kind = key.substringBefore(':')
        val arg = key.substringAfter(':', "")
        fun cosmetic() = Cosmetic.entries.firstOrNull { it.name == arg }
        return when (kind) {
            "fish" -> {
                // "fish:<id>" or "fish:<id>:NIGHTMARE".
                val id = arg.substringBefore(':')
                val atmosphere = if (arg.endsWith(":NIGHTMARE")) com.deepline.fishing.core.Atmosphere.NIGHTMARE else com.deepline.fishing.core.Atmosphere.FAMILY
                FishModels.mesh(FishCatalog.byId(id) ?: error("Unknown species $id"), atmosphere)
            }
            "hook" -> PropModels.hook(cosmetic())
            "worm" -> PropModels.worm()
            "sinker" -> PropModels.sinker()
            "rock" -> PropModels.rock(arg.toInt())
            "wood" -> PropModels.driftwood(arg.toInt())
            "jelly" -> PropModels.jelly()
            "boat" -> PropModels.boat(cosmetic())
            "boatd" -> {
                // "boatd:<sail>:<flag>:<emblem>"
                val (sail, flag, emblem) = arg.split(':')
                PropModels.customBoat(
                    com.deepline.fishing.core.SailStyle.valueOf(sail),
                    com.deepline.fishing.core.FlagStyle.valueOf(flag),
                    com.deepline.fishing.core.Emblem.valueOf(emblem),
                )
            }
            "flag" -> if (arg == "royal") PropModels.royalStreamer() else PropModels.pennantFlag()
            "kelp" -> PropModels.kelp(arg.toInt())
            "decor" -> PropModels.decorRock(arg.toInt())
            "coral" -> PropModels.coral(arg.toInt())
            "fullscreen" -> PropModels.fullscreenTriangle()
            else -> error("Unknown mesh $key")
        }
    }
}

internal fun rgb(argb: Long) = floatArrayOf(((argb shr 16) and 0xFF) / 255f, ((argb shr 8) and 0xFF) / 255f, (argb and 0xFF) / 255f)

internal fun rgba(argb: Long, alpha: Float = 1f) = floatArrayOf(((argb shr 16) and 0xFF) / 255f, ((argb shr 8) and 0xFF) / 255f, (argb and 0xFF) / 255f, alpha)

internal fun f(vararg v: Float) = v
