package com.deepline.fishing.ui.village

import com.deepline.fishing.core.BoatDesign
import com.deepline.fishing.core.BoatParts
import com.deepline.fishing.core.Cosmetic
import com.deepline.fishing.core.CosmeticSlot
import com.deepline.fishing.core.Grant
import com.deepline.fishing.core.PartRequirement
import com.deepline.fishing.core.PlayerProgress
import com.deepline.fishing.core.PremiumSkins
import com.deepline.fishing.core.Seasons
import com.deepline.fishing.core.Secrets
import com.deepline.fishing.core.ShopCatalog

/**
 * The island in the colours of what the player wears, more so the harder it was to get. Looks
 * only, like every skin: nothing on the island opens anything new or hides anything.
 *
 * [level]:
 * - 0 the classic look: the island as it is.
 * - 1 a boat of their own design (free options): the arena's pennant in its flag colour.
 * - 2 a shop look (a premade boat up to €1.99, shop paints or emblems, a shop hook, line or
 *   trail): also the market's awning and the pavilion's tiles.
 * - 3 a premium look (a set, a season pass boat, €4.99 and up, the Club's Aurora): also the roofs
 *   and the lighthouse, and bunting all over the island.
 * - 4 a legendary set (a legendary secret): all of that in the set's colours, its monument in
 *   the square and a crown for the lighthouse ([legend], the monument's key).
 *
 * Colours are ARGB; null leaves that part as built.
 */
data class IslandDress(
    val level: Int,
    val roof: Long? = null,
    val flag: Long? = null,
    val trim: Long? = null,
    val legend: String? = null,
) {
    /** Whether the island gets its bunting. */
    val bunting: Boolean get() = level >= 3

    companion object {
        val CLASSIC = IslandDress(0)

        /**
         * The legendary sets, by the secret that unlocks them: their monument's key ([legend]:
         * a leviathan's skeleton, rainbow crystals, a golden crowned fish) and colours (roof,
         * flag, trim). Then the shop gear's colours (flag, trim).
         */
        private val LEGENDS = mapOf(
            Secrets.DAVID_AND_GOLIATH to ("leviathan" to Triple(0xFF2F6F7EL, 0xFF59E3F2L, 0xFF1FA2B8L)),
            Secrets.PERFECT_CAST to ("prism" to Triple(0xFF7E6CF2L, 0xFFFF6FB5L, 0xFF63E0C6L)),
            Secrets.MASTER_ANGLER to ("crown" to Triple(0xFF6A3FA0L, 0xFFF2C14EL, 0xFFF2C14EL)),
        )

        /** Every legendary monument's key. */
        val legends: List<String> get() = LEGENDS.values.map { it.first }
        private val GEAR = mapOf(
            Cosmetic.CORAL_HOOK to (0xFFFF7F7FL to 0xFFFFF0E6L),
            Cosmetic.OCEAN_LINE to (0xFF2EC4D6L to 0xFF1565C0L),
            Cosmetic.HEART_TRAIL to (0xFFFF6F9FL to 0xFFFFE0EAL),
            Cosmetic.NEON_HOOK to (0xFFFF3FD2L to 0xFF2BE3FFL),
            Cosmetic.NEON_LINE to (0xFFFF4FB0L to 0xFF2BE3FFL),
            Cosmetic.STAR_TRAIL to (0xFF2BE3FFL to 0xFFB04FFFL),
            Cosmetic.OBSIDIAN_HOOK to (0xFF5B3A8CL to 0xFF2A2238L),
            Cosmetic.SILVER_LINE to (0xFFC9D3DCL to 0xFF6E7F8DL),
            Cosmetic.PETAL_TRAIL to (0xFFFFA8C8L to 0xFFFFF4F8L),
            Cosmetic.FOUNDER_TITLE to (0xFFF2C14EL to 0xFF1F3A68L),
        )

        /** What [p] wears, on the island. */
        fun of(p: PlayerProgress): IslandDress {
            val options = ArrayList<IslandDress>()
            // The boat first: it wins a tie.
            val boatCosmetic = p.equippedSkins[CosmeticSlot.BOAT]
            if (p.customBoatEquipped && boatCosmetic == null) options += ofDesign(p.boatDesign)
            for (slot in listOf(CosmeticSlot.BOAT, CosmeticSlot.HOOK, CosmeticSlot.LINE, CosmeticSlot.TRAIL, CosmeticSlot.TITLE)) {
                val c = p.equippedSkins[slot] ?: continue
                ofCosmetic(c)?.let { options += it }
            }
            return options.maxByOrNull { it.level }?.takeIf { it.level > 0 } ?: CLASSIC
        }

        private fun ofCosmetic(c: Cosmetic): IslandDress? {
            if (!c.fromShop) {
                val (legend, colors) = LEGENDS[c.unlockedBy] ?: return null
                val (roof, flag, trim) = colors
                return IslandDress(4, roof, flag, trim, legend)
            }
            val (flag, trim) = GEAR[c] ?: return null
            return IslandDress(2, flag = flag, trim = trim)
        }

        /** A boat design worn: a shop boat by its price, otherwise by the options it uses. */
        fun ofDesign(d: BoatDesign): IslandDress {
            val preset = PremiumSkins.all.firstOrNull { it.design == d }
            val level = when {
                preset != null && preset.id in Seasons.boats -> 3
                preset != null -> {
                    // The cheapest way to buy it: sets and specials make it premium.
                    val cents = ShopCatalog.all.filter { item -> item.grants.any { it is Grant.Preset && it.presetId == preset.id } }.minOfOrNull { it.euroCents }
                    if (cents == null || cents >= 299) 3 else 2
                }
                BoatParts.partsOf(d).any { BoatParts.requirement(it) == PartRequirement.ClubOnly } -> 3
                BoatParts.partsOf(d).any { BoatParts.requirement(it) == PartRequirement.PremiumOnly } -> 2
                d != BoatDesign.DEFAULT -> 1
                else -> 0
            }
            if (level == 0) return CLASSIC
            return IslandDress(
                level,
                roof = d.hullColor().takeIf { level >= 3 },
                flag = d.flagColor(),
                trim = d.trimColor().takeIf { level >= 2 },
            )
        }
    }
}
