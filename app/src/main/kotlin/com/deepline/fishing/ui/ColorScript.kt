package com.deepline.fishing.ui

import com.deepline.fishing.core.Atmosphere
import com.deepline.fishing.core.DepthZone
import com.deepline.fishing.core.FishSpecies
import kotlin.math.max
import kotlin.math.min

/**
 * The game's colour script: how colour, value and saturation change with depth, shared by the 3D
 * and 2D renderers and the UI. It follows the usual colour-script practice of planning the mood
 * journey first (bright and warm = safe, then cooler, darker and less saturated = uneasy, with
 * sparse saturated accents for danger), and the physics of water (red light goes first, then
 * everything turns blue, then black):
 *
 * - Sunlit Shallows: high value, warm sunlight, saturated tropical colours, round friendly fish.
 * - Twilight Reef: mid value, cooler blues and violets, gentle glowing accents.
 * - Midnight Trench: low value, desaturated, cold; bioluminescent dots, spines and teeth.
 * - Abyssal Floor: near-black with sparse red and cyan glows; fangs and glowing eyes.
 *
 * The Nightmare atmosphere keeps the shallows as they are and pushes the deep towards blood-red
 * darkness. Colours only: what can be seen is the same in every atmosphere.
 */
object ColorScript {
    private val familyWater = listOf(
        0f to 0xFF6FE3F2, 40f to 0xFF2FB8E6, 120f to 0xFF1877C9, 220f to 0xFF234C9E,
        320f to 0xFF2A2F6E, 450f to 0xFF141634, 560f to 0xFF0B0A1C, 700f to 0xFF050409,
    )
    private val nightmareWater = listOf(
        0f to 0xFF6FE3F2, 40f to 0xFF2FB8E6, 120f to 0xFF1877C9, 220f to 0xFF2A3F86,
        320f to 0xFF2C1A48, 450f to 0xFF220A1A, 560f to 0xFF15040A, 700f to 0xFF080102,
    )
    private val familyLight = listOf(0f to 0xFFFFF1D6, 120f to 0xFFE6F0FF, 300f to 0xFFA9B9F2, 500f to 0xFF7E86C9, 700f to 0xFF5E6096)
    private val nightmareLight = listOf(0f to 0xFFFFF1D6, 120f to 0xFFE6F0FF, 300f to 0xFFB29BC8, 500f to 0xFF9C6170, 700f to 0xFF7A3644)

    /** Water colour at [depth] meters (ARGB). */
    fun water(depth: Float, atmosphere: Atmosphere = Atmosphere.FAMILY): Long =
        sample(if (atmosphere == Atmosphere.NIGHTMARE) nightmareWater else familyWater, depth)

    /** The colour of the light falling on things at [depth]. */
    fun light(depth: Float, atmosphere: Atmosphere = Atmosphere.FAMILY): Long =
        sample(if (atmosphere == Atmosphere.NIGHTMARE) nightmareLight else familyLight, depth)

    /** How bright the light is at [depth] (1 at the surface). */
    fun lightStrength(depth: Float) = 1f - 0.4f * smooth(0f, 300f, depth) - 0.25f * smooth(300f, 620f, depth)

    /** The floating specks: sun motes near the surface, plankton deeper, embers in a nightmare. */
    fun specks(depth: Float, atmosphere: Atmosphere): Long = when {
        depth < 120f -> 0xFFFFF6D8
        atmosphere == Atmosphere.NIGHTMARE && depth > 300f -> 0xFFFF5A3C
        depth < 320f -> 0xFFBFE8FF
        else -> 0xFF5CF2E4
    }

    /** 0 in the friendly shallows, 1 in the abyss: how menacing things look at [depth]. */
    fun menace(depth: Float) = smooth(60f, 560f, depth)

    fun zoneAccent(zone: DepthZone): Long = when (zone) {
        DepthZone.SHALLOWS -> 0xFF5ED8F0
        DepthZone.TWILIGHT -> 0xFF8C9EFF
        DepthZone.MIDNIGHT -> 0xFF4FD1C5
        DepthZone.ABYSS -> 0xFFFF6B6B
    }

    /** A card background for a zone: the water of that zone, top to bottom. */
    fun zoneGradient(zone: DepthZone): Pair<Long, Long> = water(zone.minDepth + 10f) to water(min(zone.maxDepth, 680f))

    internal fun smooth(e0: Float, e1: Float, x: Float): Float {
        val t = ((x - e0) / (e1 - e0)).coerceIn(0f, 1f)
        return t * t * (3 - 2 * t)
    }

    private fun sample(stops: List<Pair<Float, Long>>, depth: Float): Long {
        val d = max(depth, 0f)
        for (i in 1 until stops.size) {
            val (d1, c1) = stops[i]
            if (d <= d1) {
                val (d0, c0) = stops[i - 1]
                return mix(c0, c1, (d - d0) / (d1 - d0))
            }
        }
        return stops.last().second
    }

    fun mix(a: Long, b: Long, t: Float): Long {
        val k = t.coerceIn(0f, 1f)
        fun ch(c: Long, s: Int) = ((c shr s) and 0xFF).toFloat()
        fun m(s: Int) = (ch(a, s) + (ch(b, s) - ch(a, s)) * k).toInt().coerceIn(0, 255).toLong()
        return (0xFFL shl 24) or (m(16) shl 16) or (m(8) shl 8) or m(0)
    }
}

/** How a fish's eyes are drawn. */
enum class EyeStyle { CUTE, NORMAL, GLOWING, SLIT }

/**
 * The look of a species: colours and the features that make it read as friendly or menacing,
 * all from its depth (round, bright and big-eyed near the surface; spines, teeth, glowing eyes
 * and dark bodies in the deep). Visual only.
 */
data class FishLook(
    val body: Long,
    val accent: Long,
    val glow: Long,
    val iris: Long,
    val eyeStyle: EyeStyle,
    /** Eye size relative to the standard eye. */
    val eyeScale: Float,
    /** 0..1: blush, smile, big highlights. */
    val cute: Float,
    /** 0..1: spines, teeth, darker countershading. */
    val menace: Float,
    val teeth: Int,
    val spines: Int,
    val photophores: Boolean,
) {
    /** A key for the mesh cache: species whose looks share geometry share meshes. */
    val geometryKey get() = "${(eyeScale * 100).toInt()}:$teeth:$spines"
}

object FishLooks {
    private data class Pal(val body: Long, val accent: Long)

    // Zone palettes: each body colour comes with a complementary or contrasting accent.
    private val shallows = listOf(
        Pal(0xFFFFD84D, 0xFFFF7A59), Pal(0xFFFF8A8A, 0xFF2EC4B6), Pal(0xFFFFA94D, 0xFF4DB8FF),
        Pal(0xFF4DD6E8, 0xFFFFC93C), Pal(0xFFA8E05F, 0xFF8E6CEF), Pal(0xFF7CC6FF, 0xFFFF8E72),
        Pal(0xFFF7A8D8, 0xFF5EE6B5), Pal(0xFFFFB997, 0xFF19A7A0),
    )
    private val twilight = listOf(
        Pal(0xFF8C9EFF, 0xFFFFC857), Pal(0xFFB39DFF, 0xFF2BD4C5), Pal(0xFF2BB3A8, 0xFFE05FB0),
        Pal(0xFF5C6BC0, 0xFFFFA37A), Pal(0xFFC77DDB, 0xFFB8E86B), Pal(0xFF5E8EC9, 0xFFFF9F43),
    )
    private val midnight = listOf(
        Pal(0xFF3D4A66, 0xFF3FF2FF), Pal(0xFF4A2E5C, 0xFF7CFF9E), Pal(0xFF1F4A52, 0xFFB57CFF),
        Pal(0xFF3A3F47, 0xFFFFB347), Pal(0xFF22305C, 0xFFFF6FB5),
    )
    private val abyss = listOf(
        Pal(0xFF1B1824, 0xFFFF3B3B), Pal(0xFF5A0F18, 0xFFFFB000), Pal(0xFFC9CED6, 0xFF9DFF5C),
        Pal(0xFF2B1638, 0xFF2CF5E8), Pal(0xFF24262B, 0xFFE53935),
    )

    /** The hand-made species keep a designed look. */
    private val designed = mapOf(
        "silver_sprat" to Pal(0xFFBFE3F5, 0xFF4AA3D9),
        "coral_darter" to Pal(0xFFFF8A65, 0xFF2EC4B6),
        "reed_perch" to Pal(0xFFC5E86C, 0xFF3E8E41),
        "sun_bream" to Pal(0xFFFFD54F, 0xFFFF8A3D),
        "dusk_snapper" to Pal(0xFFE86A92, 0xFF6E5BD9),
        "ribbon_eel" to Pal(0xFF2BB3A8, 0xFFE05FB0),
        "reef_grouper" to Pal(0xFF5C6BC0, 0xFFFFA37A),
        "veil_ray" to Pal(0xFFB39DFF, 0xFF2BD4C5),
        "ghost_hake" to Pal(0xFF8FA3B8, 0xFF3FF2FF),
        "needle_viper" to Pal(0xFF22305C, 0xFFFF6FB5),
        "ink_cuttle" to Pal(0xFF4A2E5C, 0xFF7CFF9E),
        "iron_sturgeon" to Pal(0xFF3A3F47, 0xFFFFB347),
        "glowfin_angler" to Pal(0xFF2B1638, 0xFF2CF5E8),
        "star_drifter" to Pal(0xFF1C1A2E, 0xFFFFE27A),
        "abyss_wyrm" to Pal(0xFF5A0F18, 0xFFFFB000),
        "pale_leviathan" to Pal(0xFFD8DEE6, 0xFF9DB4C0),
        "deep_sovereign" to Pal(0xFF4A148C, 0xFFFFD54F),
    )

    private val cache = HashMap<String, FishLook>()

    fun of(species: FishSpecies, atmosphere: Atmosphere = Atmosphere.FAMILY): FishLook =
        synchronized(cache) { cache.getOrPut("${species.id}:${atmosphere.name}") { build(species, atmosphere) } }

    private fun build(species: FishSpecies, atmosphere: Atmosphere): FishLook {
        val h = (species.id.hashCode() and 0x7FFFFFFF)
        val pal = designed[species.id] ?: when (species.zone) {
            DepthZone.SHALLOWS -> shallows[h % shallows.size]
            DepthZone.TWILIGHT -> twilight[h % twilight.size]
            DepthZone.MIDNIGHT -> midnight[h % midnight.size]
            DepthZone.ABYSS -> abyss[h % abyss.size]
        }
        var menace = ColorScript.menace(species.minDepth)
        var body = pal.body
        var iris = ColorScript.mix(pal.accent, 0xFF101828, 0.35f)
        // The Nightmare atmosphere leaves the harmless shallows alone and makes the deep worse.
        if (atmosphere == Atmosphere.NIGHTMARE && menace > 0.3f) {
            menace = min(1f, menace + 0.35f)
            body = ColorScript.mix(body, 0xFF14060A, 0.35f)
            iris = 0xFFFF2A2A
        }
        val cute = 1f - ColorScript.smooth(0.05f, 0.35f, menace)
        val eyeStyle = when {
            menace < 0.25f -> EyeStyle.CUTE
            menace < 0.5f -> EyeStyle.NORMAL
            menace < 0.8f -> EyeStyle.GLOWING
            else -> EyeStyle.SLIT
        }
        if (eyeStyle == EyeStyle.SLIT && atmosphere == Atmosphere.FAMILY) iris = ColorScript.mix(pal.accent, 0xFFFFB000, 0.5f)
        return FishLook(
            body = body,
            accent = pal.accent,
            glow = pal.accent,
            iris = iris,
            eyeStyle = eyeStyle,
            eyeScale = 1.3f - 0.55f * menace,
            cute = cute,
            menace = menace,
            teeth = when {
                menace < 0.45f -> 0
                menace < 0.75f -> 1
                else -> 2
            },
            spines = when {
                menace < 0.4f -> 0
                menace < 0.7f -> 1
                else -> 2
            },
            photophores = menace >= 0.5f && species.lengthM < 3.5f,
        )
    }
}
