package com.deepline.fishing.core

import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * Builds the regular species beyond the hand-made ones, so the fish log holds 200 in total.
 *
 * The output is fully deterministic (fixed seed, fixed name lists): ids are slugs of the names, so
 * saved progress keeps pointing at the same fish. Changing these lists or numbers changes species,
 * so append rather than edit once players have caught them.
 */
internal object SpeciesGenerator {
    /** How many species to generate per zone (with the 16 hand-made ones: 199 regular). */
    private class ZonePlan(
        val zone: DepthZone,
        val count: Int,
        val from: Float,
        val to: Float,
        val hueFrom: Float,
        val hueTo: Float,
        val value: Float,
        val adjectives: List<String>,
        val bases: List<String>,
    )

    private val plans = listOf(
        ZonePlan(
            DepthZone.SHALLOWS, 41, 2f, 98f, 20f, 200f, 0.95f,
            listOf("Sunny", "Golden", "Speckled", "Banded", "Lemon", "Rosy", "Pebble", "Minted", "Sandy", "Copper", "Bright", "Lagoon"),
            listOf("Goby", "Minnow", "Wrasse", "Chub", "Shiner", "Mullet", "Blenny", "Damsel", "Tetra", "Gudgeon"),
        ),
        ZonePlan(
            DepthZone.TWILIGHT, 50, 100f, 298f, 180f, 330f, 0.8f,
            listOf("Dusk", "Indigo", "Violet", "Amber", "Velvet", "Crimson", "Twilight", "Saffron", "Cobalt", "Mauve", "Ember", "Tidal"),
            listOf("Snapper", "Grouper", "Lionfish", "Triggerfish", "Parrotfish", "Tang", "Barracuda", "Batfish", "Hogfish", "Angelfish", "Cardinal", "Ray"),
        ),
        ZonePlan(
            DepthZone.MIDNIGHT, 47, 300f, 498f, 160f, 280f, 0.6f,
            listOf("Ghost", "Ink", "Iron", "Obsidian", "Phantom", "Shadow", "Hollow", "Ashen", "Smoke", "Gloom", "Silent", "Pewter"),
            listOf("Hake", "Viper", "Hatchetfish", "Dragonfish", "Bristlemouth", "Barreleye", "Fangtooth", "Sabretooth", "Snipe Eel", "Loosejaw", "Sturgeon", "Cuttle"),
        ),
        ZonePlan(
            DepthZone.ABYSS, 45, 500f, 598f, 260f, 400f, 0.55f,
            listOf("Abyssal", "Void", "Ancient", "Crystal", "Starlit", "Hadal", "Eternal", "Pallid", "Rune", "Frost", "Nebula", "Spectral"),
            listOf("Angler", "Gulper", "Grenadier", "Snailfish", "Seadevil", "Ghostshark", "Tripodfish", "Cusk", "Tonguefish", "Wyrm", "Drifter", "Chimaera"),
        ),
    )

    /** Fish with lures or lights. */
    private val glowingBases = setOf("Angler", "Seadevil", "Dragonfish", "Loosejaw")
    private val eelBases = setOf("Snipe Eel", "Wyrm")

    fun generate(taken: Set<String>): List<FishSpecies> {
        val random = Random(20261103)
        val names = HashSet(taken)
        val out = ArrayList<FishSpecies>()
        plans.forEachIndexed { zi, plan ->
            val picked = ArrayList<Pair<String, String>>()
            var k = 0
            while (picked.size < plan.count) {
                check(k < plan.adjectives.size * plan.bases.size * 2) { "Not enough unique names for ${plan.zone}" }
                // Cycle through the fish types first so neighbouring depths get different kinds.
                val base = plan.bases[k % plan.bases.size]
                // Stepping adjectives by 7 (coprime with 12) reaches every adjective/type pair.
                val adjective = plan.adjectives[(k % plan.bases.size + (k / plan.bases.size) * 7 + zi * 3) % plan.adjectives.size]
                val name = "$adjective $base"
                if (names.add(name)) picked += adjective to base
                k++
            }
            picked.forEachIndexed { i, (_, base) ->
                val name = "${picked[i].first} $base"
                out += makeSpecies(name, base, plan, i, random)
            }
        }
        return out
    }

    private fun makeSpecies(name: String, base: String, plan: ZonePlan, index: Int, random: Random): FishSpecies {
        // Spread the species evenly through the zone, each living in a band 40-140 m wide.
        val t = if (plan.count == 1) 0.5f else index / (plan.count - 1f)
        val center = plan.from + t * (plan.to - plan.from)
        val width = 40f + random.nextFloat() * 100f
        val minDepth = (center - width / 2f).coerceIn(plan.from, plan.to - 4f)
        val maxDepth = (center + width / 2f).coerceAtLeast(minDepth + 30f)
        val deep = minDepth / 620f

        val heightRatio = when {
            base in eelBases -> 0.14f + random.nextFloat() * 0.06f
            base in setOf("Grouper", "Batfish", "Angelfish", "Gulper", "Angler", "Seadevil") -> 0.5f + random.nextFloat() * 0.12f
            else -> 0.28f + random.nextFloat() * 0.22f
        }
        val length = 0.5f + random.nextFloat() * 1.2f + deep * 2.0f
        val weight = (length.pow(2.2f) * heightRatio * 2.2f).coerceIn(0.1f, 30f)
        val rarity = 2 + (28 * (1f - random.nextFloat().pow(2))).roundToInt()
        val rareBoost = 1f + (30 - rarity) / 40f
        val points = ((5f + 330f * deep.pow(1.4f)) * (0.8f + 0.4f * random.nextFloat()) * rareBoost + weight * 2f).roundToInt()
        val speed = (0.6f + random.nextFloat() * 2.0f - deep * 0.8f).coerceAtLeast(0.4f)

        val hue = plan.hueFrom + random.nextFloat() * (plan.hueTo - plan.hueFrom)
        val body = hsv(hue % 360f, 0.45f + random.nextFloat() * 0.4f, plan.value)
        val accent = hsv((hue + 30f + random.nextFloat() * 120f) % 360f, 0.7f, plan.value * 0.75f)

        return FishSpecies(
            id = name.lowercase().replace(' ', '_'),
            name = name,
            weightKg = (weight * 10f).roundToInt() / 10f,
            basePoints = points,
            minDepth = minDepth.roundToInt().toFloat(),
            maxDepth = maxDepth.roundToInt().toFloat(),
            lengthM = (length * 10f).roundToInt() / 10f,
            heightRatio = heightRatio,
            speed = speed,
            rarity = rarity,
            bodyColor = body,
            accentColor = accent,
            glowing = base in glowingBases,
        )
    }

    /** HSV (hue in degrees) to an opaque ARGB color. */
    private fun hsv(h: Float, s: Float, v: Float): Long {
        val c = v * s
        val x = c * (1 - abs((h / 60f) % 2 - 1))
        val m = v - c
        val (r, g, b) = when ((h / 60f).toInt()) {
            0 -> Triple(c, x, 0f)
            1 -> Triple(x, c, 0f)
            2 -> Triple(0f, c, x)
            3 -> Triple(0f, x, c)
            4 -> Triple(x, 0f, c)
            else -> Triple(c, 0f, x)
        }
        fun ch(f: Float) = ((f + m) * 255f).roundToInt().coerceIn(0, 255).toLong()
        return (0xFFL shl 24) or (ch(r) shl 16) or (ch(g) shl 8) or ch(b)
    }
}
