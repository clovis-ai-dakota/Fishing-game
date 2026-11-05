package com.deepline.fishing.ui.shop

import com.deepline.fishing.core.BoatPart
import com.deepline.fishing.core.BoatPartKind
import com.deepline.fishing.core.CosmeticSlot
import com.deepline.fishing.core.Grant
import com.deepline.fishing.core.PlayerProgress
import com.deepline.fishing.core.PremiumSkins
import com.deepline.fishing.core.ShopCatalog
import com.deepline.fishing.core.ShopItem
import com.deepline.fishing.core.Tackle
import com.deepline.fishing.ui.boat.partName
import kotlin.math.roundToInt

/** How the shop words what an item gives and costs. */
object ShopText {
    /** What [item] gives this player, e.g. "24 Lanterns" or "Coral Reef Cruiser, Neon line". */
    fun contents(item: ShopItem, p: PlayerProgress): String = item.grants.joinToString(", ") { grant(item, it, p) }

    fun grant(item: ShopItem, g: Grant, p: PlayerProgress): String = when (g) {
        is Grant.TacklePack -> ShopCatalog.packCount(item, g, p).let { n -> "$n ${tackleName(g.tackle, n)}" }
        is Grant.BoatParts -> g.parts.joinToString(", ") { partLabel(it) }
        is Grant.Preset -> PremiumSkins.byId(g.presetId)?.name ?: "a boat"
        is Grant.Cosmetics -> g.items.joinToString(", ") { if (it.slot == CosmeticSlot.TITLE) "the ${it.displayName} title" else it.displayName }
        is Grant.Unlock -> unlockName(g.key)
        is Grant.ClubDays -> "${g.days} days of Captain's Club"
    }

    fun tackleName(t: Tackle, count: Int): String {
        val one = count == 1
        return when (t) {
            Tackle.LANTERN -> if (one) "Lantern" else "Lanterns"
            Tackle.LUCKY_CHARM -> if (one) "Lucky charm" else "Lucky charms"
            Tackle.SPARKLE_LURE -> if (one) "Sparkle lure" else "Sparkle lures"
            Tackle.DETECTOR -> if (one) "rare fish detector" else "rare fish detectors"
        }
    }

    /** "Metallic paint", "Crown emblem", "Stripes print". */
    fun partLabel(part: BoatPart): String = partName(part) + when (part.kind) {
        BoatPartKind.PATTERN -> " paint"
        BoatPartKind.EMBLEM -> " emblem"
        BoatPartKind.CLOTH -> " sail print"
        else -> ""
    }

    private val unlockNames = ArrayList<(String) -> String?>()

    /** Lets a feature name its own unlock keys ("card:", "decor:"...). */
    fun registerUnlockName(name: (String) -> String?) {
        synchronized(unlockNames) { unlockNames += name }
    }

    fun unlockName(key: String): String = synchronized(unlockNames) { unlockNames.firstNotNullOfOrNull { it(key) } } ?: when (key) {
        "badge:RAINBOW" -> "a rainbow title badge"
        "badge:CLUB_FOREVER" -> "a Club badge that never expires"
        else -> key.substringAfter(':').lowercase().replace('_', ' ')
    }

    /** "45 min", "1 h 30 min": fishing time a points price stands for. */
    fun fishingTime(minutes: Float): String {
        val m = minutes.roundToInt()
        return when {
            m < 60 -> "$m min"
            m % 60 == 0 -> "${m / 60} h"
            else -> "${m / 60} h ${m % 60} min"
        }
    }

    /** "2 d 5 h", "5 h 12 min", "12 min". */
    fun timeLeft(ms: Long): String {
        val min = (ms / 60_000L).coerceAtLeast(1)
        val h = min / 60
        val d = h / 24
        return when {
            d > 0 -> "$d d ${h % 24} h"
            h > 0 -> "$h h ${min % 60} min"
            else -> "$min min"
        }
    }
}
