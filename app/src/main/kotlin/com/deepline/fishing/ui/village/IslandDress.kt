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
 * The island in the colours of the boat the player wears, more so the harder it was to get. Looks
 * only, like every skin: nothing on the island opens anything new or hides anything.
 *
 * [level]:
 * - 0 the classic look: the island as it is.
 * - 1 a boat of their own design (free options): the arena's pennant in its flag colour.
 * - 2 a shop boat (a premade boat up to €1.99, shop paints or emblems): also the market's awning
 *   and the pavilion's tiles.
 * - 3 a premium look (a set, a season pass boat, €4.99 and up, the Club's Aurora): also the roofs
 *   and the lighthouse, and bunting all over the island.
 * - 4 a legendary boat (a legendary secret): all of that in the set's colours, its monument in
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
         * flag, trim).
         */
        private val LEGENDS = mapOf(
            Secrets.DAVID_AND_GOLIATH to ("leviathan" to Triple(0xFF2F6F7EL, 0xFF59E3F2L, 0xFF1FA2B8L)),
            Secrets.PERFECT_CAST to ("prism" to Triple(0xFF7E6CF2L, 0xFFFF6FB5L, 0xFF63E0C6L)),
            Secrets.MASTER_ANGLER to ("crown" to Triple(0xFF6A3FA0L, 0xFFF2C14EL, 0xFFF2C14EL)),
        )

        /** Every legendary monument's key. */
        val legends: List<String> get() = LEGENDS.values.map { it.first }

        /**
         * The boat [p] wears, on the island: its own design, a premade boat, or a boat skin. Only
         * the boat: changing boats changes the island (the hook, line and trail don't).
         */
        fun of(p: PlayerProgress): IslandDress {
            val boat = p.equippedSkins[CosmeticSlot.BOAT]
            return when {
                boat != null -> ofCosmetic(boat) ?: CLASSIC
                p.customBoatEquipped -> ofDesign(p.boatDesign)
                else -> CLASSIC
            }
        }

        private fun ofCosmetic(c: Cosmetic): IslandDress? {
            // Boat skins are the legendary sets' boats.
            val (legend, colors) = LEGENDS[c.unlockedBy] ?: return null
            val (roof, flag, trim) = colors
            return IslandDress(4, roof, flag, trim, legend)
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
