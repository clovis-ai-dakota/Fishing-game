package com.deepline.fishing.ui.render3d

import com.deepline.fishing.core.Atmosphere
import com.deepline.fishing.core.FishSpecies
import com.deepline.fishing.ui.FishLook
import com.deepline.fishing.ui.FishLooks
import com.deepline.fishing.ui.FishShape
import com.deepline.fishing.ui.FishStyles
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Procedural 3D fish, one mesh per species, in "unit" space: 1 long, nose at x = +0.5, tail at
 * x = -0.5 (the same space as the 2D art, so the drawn head matches the head hitbox), y up, the
 * fish's side facing +z (the camera). The draw call scales it by the species' body length.
 */
object FishModels {
    private val cache = HashMap<String, MeshData>()

    /** The mesh for [species] as it looks in [atmosphere] (features come from its [FishLook]). */
    fun mesh(species: FishSpecies, atmosphere: Atmosphere = Atmosphere.FAMILY): MeshData {
        val look = FishLooks.of(species, atmosphere)
        return synchronized(cache) { cache.getOrPut("${species.id}:${look.geometryKey}") { build(species, look) } }
    }

    /** Where the eye sits (x, y) and its radius (z), in unit space, as the mesh draws it. */
    fun eyeOf(species: FishSpecies, atmosphere: Atmosphere = Atmosphere.FAMILY): Vec3 {
        val o = Outline.of(FishStyles.of(species).shape, species.heightRatio)
        return Vec3(o.eye.x, o.eye.y, o.eyeRadius * FishLooks.of(species, atmosphere).eyeScale)
    }

    /** Where an anglerfish's lure bulb sits, in unit space (null for fish without one). */
    fun lureTip(species: FishSpecies): Vec3? =
        if (FishStyles.of(species).shape == FishShape.ANGLER) Vec3(0.64f, 0.78f * species.heightRatio, 0f) else null

    /** How thick the body is (z), relative to its height, by body plan. */
    private fun widthRatio(shape: FishShape) = when (shape) {
        FishShape.STANDARD -> 0.62f
        FishShape.DEEP_BODY -> 0.42f
        FishShape.EEL -> 0.9f
        FishShape.ANGLER -> 0.95f
        FishShape.CUTTLE -> 0.8f
    }

    fun build(species: FishSpecies, look: FishLook = FishLooks.of(species)): MeshData {
        val style = FishStyles.of(species)
        val shape = style.shape
        val h = species.heightRatio
        val outline = Outline.of(shape, h)
        val b = MeshBuilder()

        // Body: a loft through the 2D outline, densest at the nose and tail.
        val n = 30
        val stations = (0 until n).map { i ->
            val t = (1f - cos(i / (n - 1f) * PI.toFloat())) / 2f
            val x = outline.minX + (outline.maxX - outline.minX) * t
            val (top, bottom) = outline.heightAt(x)
            val center = (top + bottom) / 2f
            val half = max((top - bottom) / 2f, 0.0005f)
            // Fuller at the head ("cheeks"), thinning towards the tail.
            val along = (x - outline.minX) / (outline.maxX - outline.minX)
            val taper = 0.55f + 0.45f * smoothstep(0f, 0.55f, along)
            LoftStation(x, center, half, half, half * widthRatio(shape) * taper)
        }
        b.loft(stations, ringSides = 22, partId = Part.BODY)
        fun halfWidthAt(x: Float): Float {
            val s = stations.minByOrNull { kotlin.math.abs(it.x - x) }!!
            return s.halfWidth
        }

        // Tail fin.
        b.extrude(outline.tail, thickness = 0.02f, partId = Part.TAIL)
        // Dorsal and ventral fins rise from the middle of the back and belly.
        outline.dorsal?.let { b.extrude(it, thickness = 0.016f, partId = Part.FIN) }
        outline.ventral?.let { b.extrude(it, thickness = 0.016f, partId = Part.FIN) }
        outline.extraFins.forEach { b.extrude(it, thickness = 0.014f, partId = Part.FIN) }

        // Pectoral fins, angled out from both sides behind the eye.
        if (shape != FishShape.EEL) {
            val px = outline.pectoralX
            val (top, bottom) = outline.heightAt(px)
            val cy = (top + bottom) / 2f - (top - bottom) * 0.12f
            val petal = MeshBuilder().apply {
                extrude(
                    listOf(0f to 0f, -0.05f to 0.05f * h + 0.012f, -0.14f to 0.03f * h + 0.01f, -0.16f to -0.06f * h, -0.06f to -0.05f * h),
                    thickness = 0.012f,
                    partId = Part.PECTORAL,
                )
            }.build()
            for (side in listOf(1f, -1f)) {
                val z = side * halfWidthAt(px) * 0.92f
                b.append(petal, Mat4.trs(Vec3(px, cy, z), rotY = -side * 0.6f))
            }
        }

        // Big glossy anime eyes on both sides of the head (smaller and meaner in the deep).
        val eye = outline.eye
        val eyeR = outline.eyeRadius * look.eyeScale
        for (side in listOf(1f, -1f)) {
            val z = side * halfWidthAt(eye.x) * 0.78f
            b.sphere(Vec3(eye.x, eye.y, z), eyeR, Part.EYE, lat = 10, lon = 16, scale = Vec3(1f, 1f, 0.75f))
        }

        when (shape) {
            FishShape.ANGLER -> {
                // The lure: a stalk curving forward from the head to a glowing bulb.
                val start = Vec3(0.30f, outline.heightAt(0.30f).first - 0.02f * h, 0f)
                val tip = lureTip(species)!!
                val ctrl = Vec3(0.40f, tip.y + 0.25f * h, 0f)
                val stalk = quadratic(start.x to start.y, ctrl.x to ctrl.y, tip.x to tip.y, 12).map { Vec3(it.first, it.second, 0f) }
                b.tube(stalk, { 0.009f * (1f - 0.4f * it) + 0.003f }, 6, Part.FIN, cap = false)
                b.sphere(tip, 0.035f + 0.02f * h, Part.LURE, lat = 8, lon = 12)
                // Needle teeth along the jaws.
                val mouthY = 0.15f * h
                for (k in 0 until 4) {
                    val x = 0.455f - k * 0.03f
                    for (z in listOf(0.6f, 0f, -0.6f)) {
                        val zz = z * halfWidthAt(x)
                        val tooth = MeshBuilder().apply {
                            extrude(listOf(-0.008f to 0f, 0.008f to 0f, 0f to -0.05f * h - 0.01f), 0.01f, Part.TEETH)
                        }.build()
                        b.append(tooth, Mat4.translation(x, -mouthY + 0.02f * h, zz))
                    }
                }
            }
            FishShape.CUTTLE -> {
                // Arms reaching forward from the head.
                for (k in 0 until 5) {
                    val y0 = (k - 2) * 0.07f * h
                    val z0 = ((k % 2) * 2 - 1) * 0.04f * h
                    val path = (0..8).map { i ->
                        val t = i / 8f
                        Vec3(0.44f + t * 0.16f, y0 + y0 * 0.6f * t, z0)
                    }
                    b.tube(path, { (0.026f * h + 0.004f) * (1f - 0.75f * it) }, 6, Part.TENTACLE)
                }
            }
            else -> Unit
        }
        addMenace(b, outline, shape, h, look, ::halfWidthAt)
        if (species.id == "deep_sovereign") {
            // A crown of spines along the head.
            val crest = ArrayList<Pair<Float, Float>>()
            val baseY = outline.heightAt(0.2f).first - 0.03f * h
            crest += 0.32f to outline.heightAt(0.32f).first - 0.03f * h
            for (k in 0 until 5) {
                val x = 0.30f - k * 0.05f
                crest += (x - 0.012f) to baseY + (0.28f + 0.1f * (2 - kotlin.math.abs(k - 2))) * h
                crest += (x - 0.025f) to baseY + 0.06f * h
            }
            crest += 0.04f to outline.heightAt(0.04f).first - 0.03f * h
            b.extrude(crest, 0.02f, Part.LURE)
        }
        return b.build()
    }

    /**
     * What makes deep fish look dangerous: spines along the back (and belly), teeth or fangs at
     * the mouth, and sharper tail tips. Shape language: round reads friendly, pointed reads
     * menacing. Purely visual: hitboxes don't change.
     */
    private fun addMenace(b: MeshBuilder, outline: Outline, shape: FishShape, h: Float, look: FishLook, halfWidthAt: (Float) -> Float) {
        if (shape == FishShape.CUTTLE) return
        if (look.spines > 0) {
            val count = if (look.spines == 1) 5 else 8
            val height = (if (look.spines == 1) 0.12f else 0.22f) * h + 0.01f
            for (k in 0 until count) {
                val x = 0.2f - k * (0.45f / count)
                val top = outline.heightAt(x).first
                if (top <= 0f) continue
                val w = 0.45f / count * 0.45f
                b.extrude(listOf(x - w to top - 0.02f * h, x + w to top - 0.02f * h, x - w * 0.6f to top + height * (0.8f + 0.2f * (k % 2))), 0.012f, Part.FIN)
            }
            if (look.spines == 2) for (k in 0 until 4) {
                val x = 0.05f - k * 0.09f
                val bottom = outline.heightAt(x).second
                val w = 0.025f
                b.extrude(listOf(x - w to bottom + 0.02f * h, x + w to bottom + 0.02f * h, x - w * 0.8f to bottom - 0.12f * h - 0.005f), 0.012f, Part.FIN)
            }
        }
        if (look.teeth > 0 && shape != FishShape.ANGLER) {
            // The mouth sits a little below the middle of the nose.
            val nx = outline.maxX - 0.035f
            val (top, bottom) = outline.heightAt(nx)
            val mouth = (top + bottom) / 2f - (top - bottom) * 0.18f
            val tooth = MeshBuilder().apply {
                extrude(listOf(-0.007f to 0f, 0.007f to 0f, 0f to -(0.05f * h + 0.008f)), 0.008f, Part.TEETH)
            }.build()
            for (k in 0 until 3) {
                val x = nx - k * 0.022f
                for (z in listOf(0.55f, -0.55f)) b.append(tooth, Mat4.translation(x, mouth + 0.01f * h, z * halfWidthAt(x)))
            }
            if (look.teeth == 2) {
                // Fangs: two long ones down from the upper jaw, one up from the lower.
                val fang = MeshBuilder().apply {
                    extrude(listOf(-0.012f to 0f, 0.012f to 0f, 0.004f to -(0.2f * h + 0.02f)), 0.012f, Part.TEETH)
                }.build()
                for (z in listOf(0.5f, -0.5f)) {
                    b.append(fang, Mat4.translation(nx - 0.03f, mouth + 0.04f * h, z * halfWidthAt(nx - 0.03f)))
                    b.append(fang, Mat4.trs(Vec3(nx - 0.05f, mouth - 0.05f * h, z * halfWidthAt(nx - 0.05f)), rotZ = PI.toFloat(), scale = Vec3(0.8f, 0.7f, 1f)))
                }
            }
        }
        if (look.menace > 0.75f && shape != FishShape.EEL) {
            // Hooked tips on the tail fork.
            val tail = outline.tail
            val tipTop = tail.maxByOrNull { it.second } ?: return
            val tipBottom = tail.minByOrNull { it.second } ?: return
            b.extrude(listOf(tipTop.first + 0.03f to tipTop.second - 0.06f * h, tipTop.first to tipTop.second - 0.02f * h, tipTop.first - 0.06f to tipTop.second + 0.1f * h), 0.016f, Part.TAIL)
            b.extrude(listOf(tipBottom.first + 0.03f to tipBottom.second + 0.06f * h, tipBottom.first - 0.06f to tipBottom.second - 0.1f * h, tipBottom.first to tipBottom.second + 0.02f * h), 0.016f, Part.TAIL)
        }
    }

    /** The 2D outline of a species (y up), and where its fins and eye go. */
    private class Outline(
        val polygon: List<Pair<Float, Float>>,
        val tail: List<Pair<Float, Float>>,
        val dorsal: List<Pair<Float, Float>>?,
        val ventral: List<Pair<Float, Float>>?,
        val eye: Vec3,
        val eyeRadius: Float,
        val pectoralX: Float,
        val extraFins: List<List<Pair<Float, Float>>> = emptyList(),
        val ellipses: List<FloatArray>? = null,
    ) {
        val minX = (ellipses?.minOf { it[0] - it[2] } ?: polygon.minOf { it.first }) + 0.002f
        val maxX = (ellipses?.maxOf { it[0] + it[2] } ?: polygon.maxOf { it.first }) - 0.002f

        /** (top, bottom) of the body at [x]. */
        fun heightAt(x: Float): Pair<Float, Float> {
            ellipses?.let { es ->
                var top = -1f; var bottom = 1f; var any = false
                for (e in es) {
                    val dx = (x - e[0]) / e[2]
                    if (dx * dx >= 1f) continue
                    val dy = e[3] * sqrt(1f - dx * dx)
                    top = max(top, e[1] + dy); bottom = min(bottom, e[1] - dy); any = true
                }
                return if (any) top to bottom else 0f to 0f
            }
            var top = -Float.MAX_VALUE; var bottom = Float.MAX_VALUE
            for (i in polygon.indices) {
                val (x0, y0) = polygon[i]; val (x1, y1) = polygon[(i + 1) % polygon.size]
                if ((x0 <= x && x1 >= x) || (x1 <= x && x0 >= x)) {
                    val t = if (x1 == x0) 0f else (x - x0) / (x1 - x0)
                    val y = y0 + (y1 - y0) * t
                    top = max(top, y); bottom = min(bottom, y)
                }
            }
            return if (top == -Float.MAX_VALUE) 0f to 0f else top to bottom
        }

        companion object {
            /** Converts the 2D art's y-down coordinates (with heights in units of h) to y up. */
            private fun up(points: List<Pair<Float, Float>>) = points.map { it.first to -it.second }

            private fun c(h: Float, vararg p: Float): List<Pair<Float, Float>> {
                // p = x0,y0, x1,y1, x2,y2, x3,y3 with y in units of h.
                return cubic(p[0] to p[1] * h, p[2] to p[3] * h, p[4] to p[5] * h, p[6] to p[7] * h, 10)
            }

            fun of(shape: FishShape, h: Float): Outline {
                val forkedTail = up(
                    c(h, -0.34f, -0.08f, -0.40f, -0.18f, -0.46f, -0.40f, -0.53f, -0.56f) +
                        quadratic(-0.53f to -0.56f * h, -0.46f to 0f, -0.53f to 0.56f * h, 8) +
                        c(h, -0.53f, 0.56f, -0.46f, 0.40f, -0.40f, 0.18f, -0.34f, 0.08f),
                )
                return when (shape) {
                    FishShape.STANDARD -> Outline(
                        up(c(h, 0.50f, 0.02f, 0.47f, -0.30f, 0.30f, -0.50f, 0.08f, -0.50f) + c(h, 0.08f, -0.50f, -0.12f, -0.50f, -0.28f, -0.25f, -0.37f, -0.11f) +
                            c(h, -0.37f, 0.11f, -0.28f, 0.25f, -0.12f, 0.48f, 0.08f, 0.48f) + c(h, 0.08f, 0.48f, 0.30f, 0.48f, 0.46f, 0.30f, 0.50f, 0.02f)),
                        forkedTail,
                        up(c(h, -0.14f, -0.40f, -0.08f, -0.85f, 0.08f, -0.95f, 0.16f, -0.42f)),
                        up(quadratic(-0.14f to 0.40f * h, -0.07f to 0.80f * h, 0.05f to 0.42f * h, 8)),
                        Vec3(0.36f, 0.12f * h, 0f), 0.15f * h, 0.2f,
                    )
                    FishShape.DEEP_BODY -> Outline(
                        up(c(h, 0.50f, 0.04f, 0.48f, -0.38f, 0.25f, -0.56f, 0.02f, -0.52f) + c(h, 0.02f, -0.52f, -0.18f, -0.48f, -0.30f, -0.22f, -0.37f, -0.09f) +
                            c(h, -0.37f, 0.09f, -0.30f, 0.22f, -0.18f, 0.50f, 0.02f, 0.50f) + c(h, 0.02f, 0.50f, 0.25f, 0.52f, 0.48f, 0.36f, 0.50f, 0.04f)),
                        forkedTail,
                        up(c(h, -0.20f, -0.40f, -0.12f, -0.90f, 0.12f, -1.00f, 0.22f, -0.46f)),
                        up(quadratic(-0.14f to 0.42f * h, -0.07f to 0.80f * h, 0.05f to 0.44f * h, 8)),
                        Vec3(0.36f, 0.14f * h, 0f), 0.13f * h, 0.18f,
                    )
                    FishShape.EEL -> Outline(
                        up(c(h, 0.50f, 0f, 0.48f, -0.45f, 0.38f, -0.50f, 0.25f, -0.50f) + c(h, 0.25f, -0.50f, 0.0f, -0.50f, -0.30f, -0.40f, -0.50f, -0.05f) +
                            c(h, -0.50f, 0.05f, -0.30f, 0.40f, 0.0f, 0.50f, 0.25f, 0.50f) + c(h, 0.25f, 0.50f, 0.38f, 0.50f, 0.48f, 0.45f, 0.50f, 0f)),
                        up(listOf(-0.44f to -0.10f * h, -0.57f to 0f, -0.44f to 0.10f * h)),
                        up(c(h, 0.22f, -0.40f, 0.0f, -0.90f, -0.30f, -0.80f, -0.52f, -0.05f) + listOf(-0.30f to -0.10f * h, 0.0f to -0.20f * h)),
                        up(c(h, 0.10f, 0.40f, -0.05f, 0.85f, -0.30f, 0.75f, -0.52f, 0.05f) + listOf(-0.30f to 0.10f * h, 0.0f to 0.20f * h)),
                        Vec3(0.40f, 0.14f * h, 0f), 0.26f * h, 0.3f,
                    )
                    FishShape.ANGLER -> Outline(
                        up(c(h, 0.46f, 0.15f, 0.50f, -0.30f, 0.30f, -0.55f, 0.05f, -0.50f) + c(h, 0.05f, -0.50f, -0.15f, -0.46f, -0.30f, -0.20f, -0.37f, -0.10f) +
                            c(h, -0.37f, 0.10f, -0.28f, 0.30f, -0.10f, 0.52f, 0.12f, 0.50f) + c(h, 0.12f, 0.50f, 0.32f, 0.48f, 0.50f, 0.38f, 0.46f, 0.15f)),
                        forkedTail,
                        up(quadratic(-0.22f to -0.38f * h, -0.15f to -0.78f * h, -0.04f to -0.44f * h, 8)),
                        up(quadratic(-0.12f to 0.42f * h, -0.06f to 0.70f * h, 0.04f to 0.44f * h, 8)),
                        Vec3(0.28f, 0.24f * h, 0f), 0.12f * h, 0.12f,
                    )
                    FishShape.CUTTLE -> Outline(
                        emptyList(),
                        // The fin "skirt" seen around the mantle.
                        (0 until 28).map { k ->
                            val a = k / 28f * 2f * PI.toFloat()
                            (-0.055f + 0.435f * cos(a)) to (0.60f * h * kotlin.math.sin(a))
                        },
                        null, null,
                        Vec3(0.35f, 0.08f * h, 0f), 0.13f * h, 0.22f,
                        ellipses = listOf(floatArrayOf(-0.04f, 0f, 0.40f, 0.48f * h), floatArrayOf(0.355f, 0f, 0.115f, 0.34f * h)),
                    )
                }
            }
        }
    }
}
