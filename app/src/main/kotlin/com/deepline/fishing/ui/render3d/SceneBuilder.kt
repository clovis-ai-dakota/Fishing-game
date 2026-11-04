package com.deepline.fishing.ui.render3d

import com.deepline.fishing.core.CastPhase
import com.deepline.fishing.core.CastSession
import com.deepline.fishing.core.Cosmetic
import com.deepline.fishing.core.FishCatalog
import com.deepline.fishing.core.FishEntity
import com.deepline.fishing.core.FishSpecies
import com.deepline.fishing.core.GameConfig
import com.deepline.fishing.core.Lighting
import com.deepline.fishing.core.ObstacleKind
import com.deepline.fishing.core.Atmosphere
import com.deepline.fishing.ui.ColorScript
import com.deepline.fishing.ui.FishLooks
import com.deepline.fishing.ui.FishStyles
import com.deepline.fishing.ui.Skins
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.tan

/**
 * The camera: perspective, looking straight at the play plane (z = 0) so that plane maps to the
 * screen exactly like the 2D renderer (the 20 m wide water fills the width, the hook sits 40% of
 * the way down). Things behind the plane get smaller (parallax), things on it keep their size.
 */
class Camera3d(val widthPx: Int, val heightPx: Int, val topDepth: Float) {
    val aspect = widthPx / heightPx.toFloat()
    val fovY = 50f * PI.toFloat() / 180f
    val distance = (GameConfig.WORLD_WIDTH / 2f) / (tan(fovY / 2f) * aspect)
    val viewHeight = GameConfig.WORLD_WIDTH / aspect
    val centerDepth = topDepth + viewHeight / 2f
    val eye = Vec3(GameConfig.WORLD_WIDTH / 2f, -centerDepth, distance)
    val view = Mat4.lookAt(eye, Vec3(eye.x, eye.y, 0f), Vec3.UP)
    // Nothing comes closer than a few meters in front of the play plane: a far near plane keeps
    // depth precise even with a 16-bit depth buffer (thin fins and sails don't flicker).
    val proj = Mat4.perspective(fovY, aspect, max(1f, distance - 15f), distance + 160f)
    val viewProj = Mat4.multiply(proj, view)
    val pxPerMeter = widthPx / GameConfig.WORLD_WIDTH

    /** Where a world point lands on screen, in pixels from the top-left (like Compose). */
    fun toScreen(p: Vec3): Pair<Float, Float> {
        val c = Mat4.transformPoint(viewProj, p)
        return (c.x * 0.5f + 0.5f) * widthPx to (0.5f - c.y * 0.5f) * heightPx
    }

    companion object {
        /** The same framing rule as the 2D renderer. */
        fun forHook(widthPx: Int, heightPx: Int, hookDepth: Float): Camera3d {
            val viewHeight = GameConfig.WORLD_WIDTH / (widthPx / heightPx.toFloat())
            return Camera3d(widthPx, heightPx, max(hookDepth - viewHeight * 0.4f, -viewHeight * 0.2f))
        }
    }
}

/** Water colour by depth, from the shared [ColorScript]. */
internal fun waterColor(depth: Float, atmosphere: Atmosphere = Atmosphere.FAMILY): FloatArray = rgb(ColorScript.water(depth, atmosphere))

/**
 * Turns a cast into a [Frame]. It keeps a little state between frames (how far each fish has
 * turned), so use one builder per view and call [build] every frame.
 */
class SceneBuilder {
    private val yaw = HashMap<Int, Float>()
    private var lastTime = Float.NaN
    private var lastSession: CastSession? = null

    fun build(session: CastSession, timeSeconds: Float, widthPx: Int, heightPx: Int, skins: Skins = Skins.CLASSIC, cameraTop: Float? = null): Frame {
        if (session !== lastSession) {
            yaw.clear()
            lastSession = session
            lastTime = Float.NaN
        }
        val dt = if (lastTime.isNaN()) 1f else (timeSeconds - lastTime).coerceIn(0f, 0.5f)
        lastTime = timeSeconds

        val cam = cameraTop?.let { Camera3d(widthPx, heightPx, it) } ?: Camera3d.forHook(widthPx, heightPx, session.hookDepth)
        val items = ArrayList<DrawItem>()
        val top = cam.topDepth
        val bottom = top + cam.viewHeight
        val hookDepth = session.hookDepth

        // Light: bright sun near the surface, dimmer and bluer deeper.
        val camDepth = max(cam.centerDepth, 0f)
        val atmo = skins.atmosphere
        val lightK = ColorScript.lightStrength(camDepth)
        val lightTint = rgb(ColorScript.light(camDepth, atmo))
        val water = waterColor(camDepth, atmo)
        val darkness = Lighting.darkness(hookDepth)
        val hookWorld = Vec3(session.hookX, -hookDepth, 0f)
        val (hookSx, hookSy) = cam.toScreen(hookWorld)
        val globals = mapOf(
            "uView" to cam.view,
            "uProj" to cam.proj,
            "uInvViewProj" to Mat4.invert(cam.viewProj),
            "uTime" to f(timeSeconds),
            "uResolution" to f(widthPx.toFloat(), heightPx.toFloat()),
            "uCamPos" to f(cam.eye.x, cam.eye.y, cam.eye.z),
            "uLightDir" to f(0.35f, 1f, 0.55f),
            "uLightColor" to FloatArray(3) { lightTint[it] * lightK },
            "uAmbient" to FloatArray(3) { water[it] * 0.8f + 0.12f },
            // The background's water gradient, from the colour script (screen top to bottom).
            "uWaterTop" to waterColor(max(top, 0f), atmo),
            "uWaterBottom" to waterColor(max(bottom, 0f), atmo),
            "uDepthRange" to f(top, bottom),
            "uAtmosphere" to f(if (atmo == Atmosphere.NIGHTMARE) 1f else 0f),
            "uDarkColor" to if (atmo == Atmosphere.NIGHTMARE) f(0.06f, 0f, 0.01f) else f(0f, 0f, 0.01f),
            "uFogColor" to water,
            "uWaterTint" to water,
            "uFogNear" to f(cam.distance + 3f),
            "uFogFar" to f(cam.distance + 95f),
            "uHookScreen" to f(hookSx, heightPx - hookSy),
            "uPxPerMeter" to f(cam.pxPerMeter),
            "uVisibleRadius" to f(Lighting.visibleRadius(session.lanternActive)),
            "uDarkness" to f(darkness),
        )

        items += DrawItem(Program.BACKGROUND, "fullscreen", depthTest = false, depthWrite = false, cullBack = false)
        addDecor(items, top, bottom, timeSeconds, atmo)

        // Obstacles.
        for (o in session.world.obstacles) {
            if (o.snagged) continue
            if (o.depth < top - 4f || o.depth > bottom + 4f) continue
            items += obstacleItem(o.kind, o.id, Vec3(o.x, -o.depth, 0f), o.radius, timeSeconds, touched = o.touched)
        }

        // Free fish, turning smoothly when they change direction.
        val sprites = SpriteBatch()
        for (fe in session.world.fish) {
            if (fe.caught) continue
            if (fe.depth < top - 5f || fe.depth > bottom + 5f) continue
            val target = if (fe.direction > 0) FACING_RIGHT else FACING_LEFT
            val current = yaw[fe.id] ?: target
            val step = TURN_SPEED * dt
            val y = if (abs(target - current) <= step) target else current + step * if (target > current) 1f else -1f
            yaw[fe.id] = y
            val swimPhase = timeSeconds * (if (fe.isFleeing) 14f else 6f) + fe.id * 1.7f
            items += fishItem(fe.species, Vec3(fe.x, -fe.depth, 0f), y, 0f, swimPhase, if (fe.isFleeing) 1.6f else 1f, fe.sparkling, fe.id.toFloat(), atmosphere = atmo)
            if (fe.sparkling) addGlints(sprites, Vec3(fe.x, -fe.depth, 0f), fe.species.bodyLengthM, timeSeconds, fe.id)
        }

        // Boat, rod and line.
        val boatBob = sin(timeSeconds * 1.4f) * 0.06f
        val cx = GameConfig.WORLD_WIDTH / 2f
        val load = (session.carriedWeightKg / session.specs.maxWeightKg).coerceIn(0f, 1f)
        val bend = if (session.phase == CastPhase.ASCENDING) 0.25f + 0.6f * load else 0.1f
        val rodBase = Vec3(cx + 0.9f, 1.0f + boatBob, 0.8f)
        val rodTip = Vec3(cx + 1.9f + bend * 0.6f, 2.9f - bend + boatBob, 0.8f)
        if (-top < 8f) {
            items += boatItems(skins.boat, Vec3(cx, boatBob, 0f), timeSeconds, skins.customBoat)
            items += rodItem(rodBase, rodTip)
        }

        val s = GameConfig.HOOK_RADIUS
        val hookEye = Vec3(session.hookX, -hookDepth + 1.4f * s, 0f)
        val entry = Vec3(rodTip.x + (hookEye.x - rodTip.x) * 0.1f, 0f, 0.5f)
        val trail = (-session.hookVelocityX * 0.12f).coerceIn(-1.5f, 1.5f)
        val control = Vec3(hookEye.x + trail, entry.y + (hookEye.y - entry.y) * 0.6f, 0.15f)
        val linePts = ArrayList<Vec3>()
        linePts += rodTip
        linePts += entry
        for (i in 1..28) {
            val t = i / 28f
            val u = 1f - t
            linePts += entry * (u * u) + control * (2f * u * t) + hookEye * (t * t)
        }
        val bumpFlash = (1f - (session.elapsedSeconds - session.lastTouchAtSeconds) / 0.5f).coerceIn(0f, 1f)

        // Hook, bait and sinker.
        val hookAt = Mat4.translation(hookWorld.x, hookWorld.y, 0f)
        items += DrawItem(
            Program.TOON, "hook:${skins.hook?.name}",
            uniforms = model(hookAt) + mapOf("uMaterial" to f(1f), "uFlags" to f(0f, 0f, 0f, hookMode(skins.hook))),
            outlinePx = 1.6f, outlineColor = OUTLINE_INK,
        )
        items += DrawItem(Program.TOON, "worm", uniforms = model(hookAt) + mapOf("uMaterial" to f(1f)), outlinePx = 1.6f, outlineColor = f(0.35f, 0.08f, 0.1f))
        val sinkerT = 0.9f
        val su = 1f - sinkerT
        val sinker = entry * (su * su) + control * (2f * su * sinkerT) + hookEye * (sinkerT * sinkerT)
        items += DrawItem(Program.TOON, "sinker", uniforms = model(Mat4.trs(sinker, scale = Vec3(0.16f, 0.16f, 0.16f))) + mapOf("uMaterial" to f(1f)), outlinePx = 1.4f, outlineColor = OUTLINE_INK)

        // What the last bump snagged rides under the hook; catches hang head up, struggling.
        session.snaggedObstacle?.let { o ->
            items += obstacleItem(o.kind, o.id, Vec3(hookWorld.x, hookWorld.y - s * 0.8f - o.radius, 0f), o.radius, timeSeconds, touched = false)
        }
        val n = session.caughtFish.size
        session.caughtFish.forEachIndexed { i, fe ->
            val len = fe.species.bodyLengthM
            val ox = (i - (n - 1) / 2f) * 0.35f
            val center = Vec3(hookWorld.x + ox, hookWorld.y - s * 0.9f - len / 2f, 0.05f * i)
            items += fishItem(fe.species, center, FACING_RIGHT, PI.toFloat() / 2f, timeSeconds * 16f + fe.id, 2.2f, fe.sparkling, fe.id.toFloat(), atmosphere = atmo)
        }

        // The line, drawn over the scene (it's thin, so no depth fight with the water).
        items += lineItem(linePts, skins.line, bumpFlash)

        // Skins: an aura around the hook and a trail of bubbles or sparkles.
        addHookAura(sprites, hookWorld, s, timeSeconds, skins.hook)
        addTrail(sprites, hookEye, timeSeconds, skins.trail)
        addMarineSnow(sprites, top, bottom, timeSeconds, atmo)
        if (atmo == Atmosphere.NIGHTMARE) addNightmare(items, sprites, top, bottom, timeSeconds)

        // Jellies are see-through: drawn after the opaque things.
        items.sortWith(compareBy { if (it.blend == Blend.OPAQUE) 0 else 1 })
        if (sprites.isNotEmpty()) items += sprites.toItem(Blend.ALPHA)

        // Darkness, then what shines through it.
        if (darkness > 0f) {
            items += DrawItem(Program.DARKNESS, "fullscreen", uniforms = mapOf("uMode" to f(0f)), blend = Blend.ALPHA, depthTest = false, depthWrite = false, cullBack = false)
            if (session.lanternActive) {
                items += DrawItem(Program.DARKNESS, "fullscreen", uniforms = mapOf("uMode" to f(1f)), blend = Blend.ADD, depthTest = false, depthWrite = false, cullBack = false)
            }
        }
        val glows = SpriteBatch()
        val strength = 0.35f + 0.65f * darkness
        for (fe in session.world.fish) {
            if (fe.caught || fe.depth < top - 5f || fe.depth > bottom + 5f) continue
            val tip = FishModels.lureTip(fe.species) ?: continue
            val len = fe.species.bodyLengthM
            val yw = yaw[fe.id] ?: FACING_RIGHT
            val world = Mat4.transformPoint(Mat4.trs(Vec3(fe.x, -fe.depth, 0f), rotY = yw, scale = Vec3(len, len, len)), tip)
            val accent = rgb(FishLooks.of(fe.species, atmo).glow)
            glows.quad(world, max(len * 0.7f, 0.5f), floatArrayOf(accent[0], accent[1], accent[2], 0.85f * strength), SHAPE_HALO)
            glows.quad(world, max(len * 0.08f, 0.12f), floatArrayOf(1f, 1f, 0.95f, strength), SHAPE_DOT)
        }
        if (glows.isNotEmpty()) items += glows.toItem(Blend.ADD)

        return Frame(widthPx, heightPx, globals, items)
    }

    // ---- Items -------------------------------------------------------------------------------

    private fun model(m: FloatArray) = mapOf("uModel" to m, "uNormalMatrix" to Mat4.normalMatrix(m))

    private fun hookMode(c: Cosmetic?) = when (c) {
        Cosmetic.LEVIATHAN_BONE_HOOK -> 1f
        Cosmetic.PRISM_HOOK -> 2f
        Cosmetic.CROWN_HOOK -> 3f
        else -> 0f
    }

    private fun obstacleItem(kind: ObstacleKind, id: Int, at: Vec3, radius: Float, t: Float, touched: Boolean): DrawItem {
        val flags = f(if (touched) 1f else 0f, 0f, 0f, 0f)
        val depth = -at.y
        val moss = 1f - smoothstep(60f, 160f, depth)
        return when (kind) {
            ObstacleKind.ROCK -> DrawItem(
                Program.TOON, "rock:$id",
                uniforms = model(Mat4.trs(at, rotY = id * 0.9f, rotZ = id * 0.37f, scale = Vec3(radius, radius, radius))) +
                    mapOf("uMaterial" to f(2f), "uFlags" to flags, "uBase" to f(moss, 0f, 0f), "uAccent" to f(0.35f, 0.55f, 0.32f)),
                outlinePx = 2.2f, outlineColor = OUTLINE_INK,
            )
            ObstacleKind.DRIFTWOOD -> DrawItem(
                Program.TOON, "wood:$id",
                uniforms = model(Mat4.trs(at, rotY = 0.3f + id * 0.5f, rotZ = ((id * 37 % 40) - 20) * PI.toFloat() / 180f, scale = Vec3(radius, radius, radius))) +
                    mapOf("uMaterial" to f(2f), "uFlags" to flags),
                outlinePx = 2.2f, outlineColor = f(0.16f, 0.09f, 0.07f),
            )
            ObstacleKind.JELLY -> {
                val bob = sin(t * 2f + id) * radius * 0.08f
                DrawItem(
                    Program.TOON, "jelly",
                    uniforms = model(Mat4.trs(Vec3(at.x, at.y + bob + radius * 0.25f, at.z), rotY = id * 0.7f, scale = Vec3(radius, radius, radius))) +
                        mapOf("uMaterial" to f(2f), "uFlags" to flags, "uSwim" to f(0f, 0f, id.toFloat(), 0f), "uOpacity" to f(1f)),
                    blend = Blend.ALPHA, outlinePx = 1.6f, outlineColor = f(0.55f, 0.15f, 0.35f), cullBack = false, depthWrite = false,
                )
            }
        }
    }

    private fun boatItems(skin: Cosmetic?, at: Vec3, t: Float, design: com.deepline.fishing.core.BoatDesign? = null): List<DrawItem> {
        val roll = sin(t * 1.1f) * 0.025f
        val m = Mat4.trs(at, rotZ = roll)
        if (skin == null && design != null) {
            return listOf(
                DrawItem(
                    Program.TOON, "boatd:${design.sailStyle.name}:${design.flagStyle.name}:${design.emblem.name}",
                    uniforms = model(m) + mapOf(
                        "uMaterial" to f(3f),
                        "uBase" to rgb(design.hullColor()),
                        "uAccent" to rgb(design.hullBottomColor()),
                        "uExtra" to rgb(design.trimColor()),
                        "uExtra2" to rgb(design.cabinColor()),
                        "uSailColor" to rgb(design.sailColor()),
                        "uFlagColor" to rgb(design.flagColor()),
                        // x: trim mode (solid), y: sail mode (custom), z: hull pattern, w: custom flag.
                        "uBoatMode" to f(0f, 2f, design.pattern.ordinal.toFloat(), 1f),
                    ),
                    outlinePx = 2.4f, outlineColor = OUTLINE_INK,
                ),
            )
        }
        val (hullTop, hullBottom, trim, trimMode) = when (skin) {
            Cosmetic.GIANT_SLAYER_PENNANT -> listOf(rgb(0xFF37474F), rgb(0xFF102027), rgb(0xFF4DD0E1), f(0f))
            Cosmetic.PRISM_SAIL -> listOf(rgb(0xFFFAFAFA), rgb(0xFFB0BEC5), rgb(0xFFFFFFFF), f(1f))
            Cosmetic.ROYAL_BOAT -> listOf(rgb(0xFF7B1FA2), rgb(0xFF38006B), rgb(0xFFFFD54F), f(2f))
            else -> listOf(rgb(0xFFB5743A), rgb(0xFF5D3A1A), rgb(0xFFE8D5B5), f(0f))
        }
        val cabin = if (skin == Cosmetic.ROYAL_BOAT) rgb(0xFFFFF8E1) else rgb(0xFFF5E6CC)
        val out = ArrayList<DrawItem>()
        out += DrawItem(
            Program.TOON, "boat:${skin?.name}",
            uniforms = model(m) + mapOf(
                "uMaterial" to f(3f), "uBase" to hullTop, "uAccent" to hullBottom, "uExtra" to trim, "uExtra2" to cabin,
                "uBoatMode" to f(trimMode[0], if (skin == Cosmetic.PRISM_SAIL) 0f else 1f, 0f, 0f),
            ),
            outlinePx = 2.4f, outlineColor = OUTLINE_INK, cullBack = skin == null,
        )
        when (skin) {
            Cosmetic.GIANT_SLAYER_PENNANT -> {
                val flagAt = Mat4.multiply(m, Mat4.translation(-0.4f, 5.0f, 0f))
                out += DrawItem(Program.TOON, "flag:pennant", uniforms = model(flagAt) + mapOf("uMaterial" to f(3f)), outlinePx = 1.8f, outlineColor = OUTLINE_INK, cullBack = false)
                val leviathan = FishCatalog.byId("pale_leviathan")!!
                val emblem = Mat4.multiply(m, Mat4.trs(Vec3(0.5f, 4.35f, 0.08f), rotY = -0.2f, scale = Vec3(1.3f, 1.3f, 0.4f)))
                out += DrawItem(Program.TOON, "fish:${leviathan.id}", uniforms = model(emblem) + fishUniforms(leviathan, t * 3f, 0.6f, false, 0f))
            }
            Cosmetic.ROYAL_BOAT -> {
                val flagAt = Mat4.multiply(m, Mat4.translation(-0.2f, 5.4f, 0f))
                out += DrawItem(Program.TOON, "flag:royal", uniforms = model(flagAt) + mapOf("uMaterial" to f(3f), "uExtra" to rgb(0xFFFFD54F), "uBoatMode" to f(2f, 1f, 0f, 0f)), outlinePx = 1.6f, outlineColor = OUTLINE_INK, cullBack = false)
            }
            else -> Unit
        }
        return out
    }

    private fun rodItem(base: Vec3, tip: Vec3): DrawItem {
        val b = MeshBuilder()
        val ctrl = Vec3(base.x + (tip.x - base.x) * 0.3f, base.y + (tip.y - base.y) * 0.8f, base.z)
        val path = (0..10).map { i ->
            val t = i / 10f
            val u = 1f - t
            base * (u * u) + ctrl * (2f * u * t) + tip * (t * t)
        }
        b.tube(path, { u -> 0.07f - 0.045f * u }, 8, 37f)
        b.sphere(Vec3.lerp(base, tip, 0.1f) + Vec3(0f, 0f, 0.05f), 0.16f, Part.METAL, 6, 10, Vec3(1f, 1f, 0.6f))
        return DrawItem(Program.TOON, null, b.build(), uniforms = model(Mat4.identity()) + mapOf("uMaterial" to f(3f)), outlinePx = 1.8f, outlineColor = OUTLINE_INK)
    }

    private fun lineItem(points: List<Vec3>, skin: Cosmetic?, bumpFlash: Float): DrawItem {
        val width = if (skin == null) 0.05f else 0.09f
        val mode = when (skin) {
            Cosmetic.GIANTS_BRAID_LINE -> 1f
            Cosmetic.RAINBOW_LINE -> 2f
            Cosmetic.GOLDEN_LINE -> 3f
            else -> 0f
        }
        val b = MeshBuilder()
        var along = 0f
        for (i in points.indices) {
            val p = points[i]
            if (i > 0) along += (p - points[i - 1]).length()
            val tangent = (points[min(i + 1, points.size - 1)] - points[max(i - 1, 0)]).normalized()
            val side = Vec3(-tangent.y, tangent.x, 0f).normalized() * (width / 2f)
            val base = when (mode) {
                2f -> {
                    val c = hsvToRgb(((i / points.size.toFloat()) * 540f) % 360f, 0.8f, 1f)
                    floatArrayOf(c[0], c[1], c[2], 1f)
                }
                else -> floatArrayOf(0.91f, 0.96f, 0.98f, 0.95f)
            }
            val color = floatArrayOf(
                base[0] + (1f - base[0]) * bumpFlash, base[1] * (1f - 0.7f * bumpFlash), base[2] * (1f - 0.7f * bumpFlash), base[3],
            )
            b.vertex(p - side, Vec3(0f, 0f, 1f), along, 0f, 10f + mode, color)
            b.vertex(p + side, Vec3(0f, 0f, 1f), along, 1f, 10f + mode, color)
            if (i > 0) {
                val a = (i - 1) * 2
                b.quad(a, a + 2, a + 3, a + 1)
            }
        }
        return DrawItem(Program.SPRITE, null, b.build(), uniforms = mapOf("uAdditive" to f(0f)), blend = Blend.ALPHA, cullBack = false, depthWrite = false)
    }

    // ---- Decor and particles -------------------------------------------------------------------

    /** Rock formations, kelp and coral far behind the play plane (decor only, never in play). */
    private fun addDecor(items: MutableList<DrawItem>, top: Float, bottom: Float, t: Float, atmo: Atmosphere) {
        val tile = 40f
        for (k in floor(max(top, 0f) / tile).toInt()..floor(bottom / tile).toInt()) {
            for (i in 0 until 2) {
                val depth = k * tile + hash01(i, k, 11) * tile
                if (depth < 10f) continue
                // Far behind the play plane and hazy, so decor never looks like something to dodge.
                val z = -45f - hash01(i, k, 12) * 30f
                val x = -14f + hash01(i, k, 13) * 48f
                val size = 5f + hash01(i, k, 14) * 7f
                val tint = waterColor(depth, atmo)
                // Sandy rocks in the sun, slate deeper, black spires in the abyss.
                val stone = rgb(
                    when {
                        depth < 120f -> 0xFFC9B48A
                        depth < 320f -> 0xFF5B6E8C
                        else -> 0xFF22252E
                    },
                )
                // Below the sunlit water the formations fade to dark silhouettes, so the deep feels
                // vast and empty and nothing behind the fish competes with them.
                val sunlit = 1f - ColorScript.smooth(80f, 200f, depth)
                val rockCol = FloatArray(3) { (0.45f * tint[it] + 0.4f * stone[it]) * (0.55f + 0.45f * sunlit) }
                if (sunlit < 0.5f && i == 1) continue
                items += DrawItem(
                    Program.TOON, "decor:${(i + k * 3) % 6}",
                    uniforms = model(Mat4.trs(Vec3(x, -depth, z), rotY = hash01(i, k, 15) * 6f, scale = Vec3(size, size, size))) +
                        mapOf("uMaterial" to f(4f), "uBase" to rockCol),
                )
                if (depth < 140f) {
                    // Kelp and coral growing on the formation.
                    val kelpX = x + (hash01(i, k, 16) - 0.5f) * size
                    items += DrawItem(
                        Program.TOON, "kelp:${(i + k) % 4}",
                        uniforms = model(Mat4.trs(Vec3(kelpX, -depth + size * 0.8f, z + 1f), scale = Vec3(1.8f, 1.4f, 1.8f))) +
                            mapOf("uMaterial" to f(4f), "uSwim" to f(0f, 0f, i + k * 1.3f, 0f)),
                        cullBack = false,
                    )
                    val coral = listOf(rgb(0xFFFF7A6B), rgb(0xFFFFB347), rgb(0xFFF78FD0), rgb(0xFF5EE6B5))[(i + k) % 4]
                    items += DrawItem(
                        Program.TOON, "coral:${(i * 2 + k) % 4}",
                        uniforms = model(Mat4.trs(Vec3(x - size * 0.3f, -depth + size * 0.9f, z + 2f), scale = Vec3(2.4f, 2.4f, 2.4f))) +
                            mapOf("uMaterial" to f(4f), "uBase" to coral),
                    )
                }
            }
        }
    }

    /**
     * The Nightmare atmosphere's decor: huge shadowy shapes gliding far behind the play plane
     * with red eyes, below the twilight. Drawn before the darkness like everything else, so they
     * only show where the player can see anyway.
     */
    private fun addNightmare(items: MutableList<DrawItem>, sprites: SpriteBatch, top: Float, bottom: Float, t: Float) {
        val tile = 90f
        val shadow = FishCatalog.byId("abyss_wyrm") ?: return
        for (k in floor(max(top, 250f) / tile).toInt()..floor(bottom / tile).toInt()) {
            val depth = k * tile + hash01(k, 3, 21) * tile
            if (depth < 250f || depth < top - 20f || depth > bottom + 20f) continue
            val dir = if (hash01(k, 4, 22) > 0.5f) 1 else -1
            val span = 70f
            val x = -25f + ((t * 1.8f + hash01(k, 5, 23) * span) % span) .let { if (dir > 0) it else span - it }
            val z = -55f - hash01(k, 6, 24) * 20f
            val len = 22f + hash01(k, 7, 25) * 14f
            val yaw = if (dir > 0) 0f else PI.toFloat()
            val m = Mat4.trs(Vec3(x, -depth, z), rotY = yaw, scale = Vec3(len, len, len))
            items += DrawItem(
                Program.TOON, "fish:${shadow.id}:NIGHTMARE",
                uniforms = model(m) + fishUniforms(shadow, t * 1.5f, 0.6f, false, k.toFloat(), silhouette = 3f, atmosphere = Atmosphere.NIGHTMARE),
            )
            // Two red eyes on the shadow.
            val eye = FishModels.eyeOf(shadow, Atmosphere.NIGHTMARE)
            val p = Mat4.transformPoint(m, Vec3(eye.x, eye.y, 0.3f))
            val glow = 0.6f + 0.4f * sin(t * 2.3f + k)
            sprites.quad(p, len * 0.06f, floatArrayOf(1f, 0.1f, 0.08f, 0.9f * glow), SHAPE_HALO)
            sprites.quad(p, len * 0.018f, floatArrayOf(1f, 0.55f, 0.4f, glow), SHAPE_DOT)
        }
    }

    private fun addMarineSnow(sprites: SpriteBatch, top: Float, bottom: Float, t: Float, atmo: Atmosphere) {
        val tile = 30f
        for (k in floor(top / tile).toInt()..floor(bottom / tile).toInt()) {
            val speck = rgb(ColorScript.specks(k * tile + tile / 2f, atmo))
            for (i in 0 until 20) {
                val depth = k * tile + hash01(i, k, 2) * tile
                if (depth < 0.5f) continue
                val z = -28f + hash01(i, k, 5) * 32f
                val x = -8f + hash01(i, k, 1) * 36f + sin(t * 0.5f + i + k) * 0.2f
                val size = 0.06f + 0.1f * hash01(i, k, 4)
                sprites.quad(Vec3(x, -depth + sin(t * 0.3f + i) * 0.2f, z), size, floatArrayOf(speck[0], speck[1], speck[2], 0.25f + 0.35f * hash01(i, k, 3)), SHAPE_DOT)
            }
        }
    }

    private fun addGlints(sprites: SpriteBatch, center: Vec3, length: Float, t: Float, id: Int) {
        for (k in 0 until 3) {
            val ph = t * 1.6f + k * 2.1f + id
            val tw = 0.5f + 0.5f * sin(ph * 2f)
            val p = center + Vec3(cos(ph) * length * 0.4f, sin(ph * 1.3f) * length * 0.18f, 0.4f)
            sprites.quad(p, (0.25f + 0.2f * tw) * max(1f, length * 0.4f), floatArrayOf(1f, 0.86f, 0.35f, tw), SHAPE_STAR)
        }
    }

    private fun addHookAura(sprites: SpriteBatch, hook: Vec3, s: Float, t: Float, skin: Cosmetic?) {
        val c = hook + Vec3(0.3f * s, 0.4f * s, 0.3f)
        when (skin) {
            Cosmetic.LEVIATHAN_BONE_HOOK -> {
                val pulse = 0.5f + 0.5f * sin(t * 3f)
                sprites.quad(c, 3.8f * s, floatArrayOf(0.30f, 0.82f, 0.88f, 0.35f + 0.25f * pulse), SHAPE_HALO)
                for (k in 0 until 3) {
                    val a = t * 2.2f + k * 2f * PI.toFloat() / 3f
                    sprites.quad(c + Vec3(cos(a), sin(a) * 0.8f, 0.05f) * (1.45f * s), 0.45f * s, floatArrayOf(0.88f, 0.97f, 0.98f, 0.95f), SHAPE_RING)
                }
            }
            Cosmetic.PRISM_HOOK -> {
                for (k in 0 until 6) {
                    val a = -t * 1.6f + k * PI.toFloat() / 3f
                    val col = hsvToRgb((t * 90f + k * 60f) % 360f, 0.55f, 1f)
                    sprites.quad(c + Vec3(cos(a), sin(a), 0.05f) * (1.7f * s), (0.6f + 0.2f * sin(t * 5f + k)) * s, floatArrayOf(col[0], col[1], col[2], 0.95f), SHAPE_STAR)
                }
                sprites.quad(c, 3.4f * s, floatArrayOf(1f, 1f, 1f, 0.22f), SHAPE_HALO)
            }
            Cosmetic.CROWN_HOOK -> {
                sprites.quad(c, 3.8f * s, floatArrayOf(1f, 0.84f, 0.31f, 0.45f), SHAPE_HALO)
                for (k in 0 until 8) {
                    val a = t * 0.6f + k * PI.toFloat() / 4f
                    sprites.quad(c + Vec3(cos(a), sin(a), 0.05f) * (1.6f * s), 0.4f * s, floatArrayOf(1f, 0.84f, 0.31f, 0.6f), SHAPE_STAR)
                }
            }
            else -> Unit
        }
    }

    private fun addTrail(sprites: SpriteBatch, eye: Vec3, t: Float, skin: Cosmetic?) {
        val count = if (skin == null) 7 else 12
        val rise = if (skin == null) 2.4f else 3.4f
        val grow = if (skin == null) 1f else 1.7f
        for (i in 0 until count) {
            val p = (t * 0.9f + i / count.toFloat()) % 1f
            val b = Vec3(eye.x + sin(p * 9f + i * 2.3f) * 0.3f * grow + (i % 3 - 1) * 0.14f * grow, eye.y + p * rise, 0.25f)
            val fade = 1f - p
            val r = (0.08f + 0.12f * p) * grow
            when (skin) {
                Cosmetic.TIDE_TRAIL -> {
                    sprites.quad(b, r * 2.4f, floatArrayOf(0.88f, 0.97f, 0.98f, 0.95f * fade), SHAPE_RING)
                    if (i % 3 == 0) sprites.quad(b, r * 4f, floatArrayOf(0.30f, 0.82f, 0.88f, 0.35f * fade), SHAPE_HALO)
                }
                Cosmetic.PRISM_TRAIL -> {
                    val c = hsvToRgb((i * 360f / count + t * 120f) % 360f, 0.7f, 1f)
                    sprites.quad(b, r * 2.6f, floatArrayOf(c[0], c[1], c[2], 0.95f * fade), if (i % 2 == 0) SHAPE_STAR else SHAPE_RING)
                }
                Cosmetic.GOLDEN_TRAIL -> {
                    sprites.quad(b, r * 2.4f, floatArrayOf(1f, 0.84f, 0.31f, fade), if (i % 3 == 0) SHAPE_COIN else SHAPE_STAR)
                }
                else -> sprites.quad(b, r * 2f, floatArrayOf(1f, 1f, 1f, 0.6f * fade), SHAPE_RING)
            }
        }
    }

    companion object {
        /** A slight three-quarter view, so fish show some of their face. */
        const val FACING_RIGHT = -0.32f
        val FACING_LEFT = -PI.toFloat() + 0.32f
        /** Radians per second: a fish turns around in about a third of a second, through the front. */
        const val TURN_SPEED = 8.5f

        val OUTLINE_INK = f(0.05f, 0.07f, 0.13f)

        const val SHAPE_DOT = 0f
        const val SHAPE_RING = 1f
        const val SHAPE_STAR = 2f
        const val SHAPE_COIN = 3f
        const val SHAPE_HALO = 4f

        /** The uniforms that paint [species]: colours, pattern, swimming. */
        fun fishUniforms(
            species: FishSpecies,
            swimPhase: Float,
            amplitude: Float,
            sparkling: Boolean,
            seed: Float,
            silhouette: Float = 0f,
            atmosphere: Atmosphere = Atmosphere.FAMILY,
        ): Map<String, FloatArray> {
            val style = FishStyles.of(species)
            val look = FishLooks.of(species, atmosphere)
            val eye = FishModels.eyeOf(species, atmosphere)
            val iris = rgb(look.iris)
            return mapOf(
                "uMaterial" to f(0f),
                "uBase" to rgb(look.body),
                "uAccent" to rgb(look.accent),
                "uExtra" to rgb(look.glow),
                "uFlags" to f(if (sparkling) 1f else 0f, silhouette, style.pattern.ordinal.toFloat(), 0f),
                "uFish" to f(species.heightRatio, gillX(style.shape), look.cute, look.menace),
                "uFish2" to f(eye.x, eye.y, eye.z, if (look.photophores) 1f else 0f),
                "uEye" to f(iris[0], iris[1], iris[2], look.eyeStyle.ordinal.toFloat()),
                "uSwim" to f(swimPhase, amplitude, seed, 0f),
            )
        }

        private fun gillX(shape: com.deepline.fishing.ui.FishShape) = when (shape) {
            com.deepline.fishing.ui.FishShape.EEL -> 0.3f
            com.deepline.fishing.ui.FishShape.ANGLER -> 0.18f
            com.deepline.fishing.ui.FishShape.CUTTLE -> -10f
            else -> 0.26f
        }

        fun fishItem(
            species: FishSpecies,
            at: Vec3,
            yaw: Float,
            rotZ: Float,
            swimPhase: Float,
            amplitude: Float,
            sparkling: Boolean,
            seed: Float,
            silhouette: Float = 0f,
            len: Float = species.bodyLengthM,
            atmosphere: Atmosphere = Atmosphere.FAMILY,
        ): DrawItem {
            val m = Mat4.trs(at, rotY = yaw, rotZ = rotZ, scale = Vec3(len, len, len))
            val look = FishLooks.of(species, atmosphere)
            // Ink lines: a dark shade of the fish's own colours (anime style), not plain black.
            val ink = rgb(ColorScript.mix(look.body, 0xFF0A0E1A, 0.78f))
            val key = if (atmosphere == Atmosphere.NIGHTMARE) "fish:${species.id}:NIGHTMARE" else "fish:${species.id}"
            return DrawItem(
                Program.TOON, key,
                uniforms = mapOf("uModel" to m, "uNormalMatrix" to Mat4.normalMatrix(m)) + fishUniforms(species, swimPhase, amplitude, sparkling, seed, silhouette, atmosphere),
                outlinePx = 2.2f, outlineColor = if (silhouette > 0f) f(0.03f, 0.05f, 0.08f) else ink,
            )
        }

        fun hsvToRgb(h: Float, s: Float, v: Float): FloatArray {
            val c = v * s
            val hp = (h / 60f) % 6f
            val x = c * (1 - abs(hp % 2 - 1))
            val (r, g, b) = when {
                hp < 1 -> Triple(c, x, 0f)
                hp < 2 -> Triple(x, c, 0f)
                hp < 3 -> Triple(0f, c, x)
                hp < 4 -> Triple(0f, x, c)
                hp < 5 -> Triple(x, 0f, c)
                else -> Triple(c, 0f, x)
            }
            val m = v - c
            return floatArrayOf(r + m, g + m, b + m)
        }
    }
}

/** Camera-facing quads (the camera looks straight down -z, so they lie in the xy plane). */
class SpriteBatch {
    private val b = MeshBuilder()
    private var count = 0

    fun isNotEmpty() = count > 0

    fun quad(center: Vec3, size: Float, color: FloatArray, shape: Float) {
        val h = size / 2f
        val n = Vec3(0f, 0f, 1f)
        val a = b.vertex(center + Vec3(-h, -h, 0f), n, 0f, 0f, shape, color)
        val c1 = b.vertex(center + Vec3(h, -h, 0f), n, 1f, 0f, shape, color)
        val c2 = b.vertex(center + Vec3(h, h, 0f), n, 1f, 1f, shape, color)
        val d = b.vertex(center + Vec3(-h, h, 0f), n, 0f, 1f, shape, color)
        b.quad(a, c1, c2, d)
        count++
    }

    fun toItem(blend: Blend) = DrawItem(
        Program.SPRITE, null, b.build(),
        uniforms = mapOf("uAdditive" to f(if (blend == Blend.ADD) 1f else 0f)),
        blend = blend, cullBack = false, depthWrite = false,
    )
}

@Suppress("unused")
private fun FishEntity.debug() = "${species.id}@$x,$depth"
