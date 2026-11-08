package com.deepline.fishing.ui.shop

import com.deepline.fishing.R
import com.deepline.fishing.core.BoatPart
import com.deepline.fishing.core.BoatPartKind
import com.deepline.fishing.core.CardStyle
import com.deepline.fishing.core.CosmeticSlot
import com.deepline.fishing.core.Decor
import com.deepline.fishing.core.GameConfig
import com.deepline.fishing.core.Grant
import com.deepline.fishing.core.MenuTheme
import com.deepline.fishing.core.PlayerProgress
import com.deepline.fishing.core.SeasonGrant
import com.deepline.fishing.core.Seasons
import com.deepline.fishing.core.ShopCatalog
import com.deepline.fishing.core.ShopItem
import com.deepline.fishing.core.Tackle
import com.deepline.fishing.core.TitleFrame
import com.deepline.fishing.ui.boat.partName
import com.deepline.fishing.ui.i18n.UiText
import com.deepline.fishing.ui.i18n.asString
import com.deepline.fishing.ui.i18n.boatNameText
import com.deepline.fishing.ui.i18n.labelText
import com.deepline.fishing.ui.i18n.nameText
import kotlin.math.roundToInt

/** How the shop words what an item gives and costs (strings_shop.xml, resolved where shown). */
object ShopText {
    /** What [item] gives this player, e.g. "24 Lanterns" or "Coral Reef Cruiser, Neon line". */
    fun contents(item: ShopItem, p: PlayerProgress): UiText = UiText.join(item.grants.map { grant(item, it, p) }, ", ")

    fun grant(item: ShopItem, g: Grant, p: PlayerProgress): UiText = when (g) {
        is Grant.TacklePack -> tackleCount(g.tackle, ShopCatalog.packCount(item, g, p))
        is Grant.BoatParts -> UiText.join(g.parts.map { partLabel(it) }, ", ")
        is Grant.Preset -> boatNameText(g.presetId) ?: UiText.res(R.string.shop_grant_a_boat)
        is Grant.Cosmetics -> UiText.join(
            g.items.map { if (it.slot == CosmeticSlot.TITLE) UiText.res(R.string.shop_grant_title, it.labelText()) else it.labelText() },
            ", ",
        )
        is Grant.Unlock -> unlockName(g.key)
        is Grant.ClubDays -> UiText.plural(R.plurals.shop_grant_club_days, g.days)
        is Grant.Feature -> when (item.id) {
            "season_pass" -> UiText.res(R.string.shop_grant_season_pass)
            "collector_edition" -> UiText.res(R.string.shop_grant_collector_edition, GameConfig.COLLECTOR_TIER_SKIP)
            "event_pass" -> UiText.res(R.string.shop_grant_event_pass)
            else -> item.nameText()
        }
    }

    /** What a season tier or an event reward gives, e.g. "3 Lanterns" or "2,400 pts". */
    fun seasonGrant(g: SeasonGrant, p: PlayerProgress): UiText = when (g) {
        is SeasonGrant.Points -> com.deepline.fishing.ui.pointsText(Seasons.pointsFor(p, g.minutes))
        is SeasonGrant.Tackle -> tackleCount(g.tackle, g.count)
        is SeasonGrant.Boat -> boatNameText(g.presetId)?.let { UiText.res(R.string.shop_grant_season_boat, it) } ?: UiText.res(R.string.shop_grant_a_boat)
        is SeasonGrant.Unlock -> unlockName(g.key)
    }

    /** Several season grants, comma-separated. */
    fun seasonGrants(list: List<SeasonGrant>, p: PlayerProgress): UiText = UiText.join(list.map { seasonGrant(it, p) }, ", ")

    /** "3 Lanterns", "1 Lucky charm": a number of one kind of tackle. */
    fun tackleCount(t: Tackle, count: Int): UiText = when (t) {
        Tackle.LANTERN -> UiText.plural(R.plurals.common_lanterns, count)
        Tackle.LUCKY_CHARM -> UiText.plural(R.plurals.shop_grant_lucky_charms, count)
        Tackle.SPARKLE_LURE -> UiText.plural(R.plurals.shop_grant_sparkle_lures, count)
        Tackle.DETECTOR -> UiText.plural(R.plurals.shop_grant_detectors, count)
    }

    /** "Metallic paint", "Crown emblem", "Stripes sail print". */
    fun partLabel(part: BoatPart): UiText = when (part.kind) {
        BoatPartKind.PATTERN -> UiText.res(R.string.shop_grant_paint, partName(part))
        BoatPartKind.EMBLEM -> UiText.res(R.string.shop_grant_emblem, partName(part))
        BoatPartKind.CLOTH -> UiText.res(R.string.shop_grant_sail_print, partName(part))
        else -> partName(part)
    }

    private val unlockNames = ArrayList<(String) -> UiText?>()

    /** Lets a feature name its own unlock keys ("card:", "decor:"...). */
    fun registerUnlockName(name: (String) -> UiText?) {
        synchronized(unlockNames) { unlockNames += name }
    }

    fun unlockName(key: String): UiText {
        synchronized(unlockNames) { unlockNames.firstNotNullOfOrNull { it(key) } }?.let { return it }
        val kind = key.substringBefore(':')
        val id = key.substringAfter(':')
        return when (key) {
            "badge:RAINBOW" -> UiText.res(R.string.shop_unlock_badge_rainbow)
            "badge:CLUB_FOREVER" -> UiText.res(R.string.shop_unlock_badge_club_forever)
            "badge:SEASON" -> UiText.res(R.string.shop_unlock_badge_season)
            "badge:COLLECTOR" -> UiText.res(R.string.shop_unlock_badge_collector)
            "badge:EVENT" -> UiText.res(R.string.shop_unlock_badge_event)
            "badge:TOURNAMENT" -> UiText.res(R.string.shop_unlock_badge_tournament)
            "aquarium:big" -> UiText.res(R.string.core_shop_item_aquarium_expansion)
            else -> when (kind) {
                "badge" -> TitleFrame.entries.firstOrNull { it.name == id }?.let { UiText.res(R.string.shop_unlock_badge_frame, it.labelText()) }
                "card" -> CardStyle.entries.firstOrNull { it.name == id }
                    ?.let { style -> ShopCatalog.byId("card_" + style.name.lowercase())?.nameText() ?: style.labelText() }
                "decor" -> Decor.entries.firstOrNull { it.name == id }?.labelText()
                "theme" -> MenuTheme.entries.firstOrNull { it.name == id }?.let { UiText.res(R.string.core_shop_item_theme, it.labelText()) }
                else -> null
            } ?: UiText.raw(id.lowercase().replace('_', ' '))
        }
    }

    /** "45 min", "1 h 30 min": fishing time a points price stands for. */
    fun fishingTime(minutes: Float): UiText {
        val m = minutes.roundToInt()
        return when {
            m < 60 -> UiText.res(R.string.common_duration_minutes, m)
            m % 60 == 0 -> UiText.res(R.string.common_duration_hours, m / 60)
            else -> UiText.res(R.string.common_duration_hours_minutes, m / 60, m % 60)
        }
    }

    /** "2 d 5 h", "5 h 12 min", "12 min". */
    fun timeLeft(ms: Long): UiText {
        val min = (ms / 60_000L).coerceAtLeast(1).toInt()
        val h = min / 60
        val d = h / 24
        return when {
            d > 0 -> UiText.res(R.string.common_duration_days_hours, d, h % 24)
            h > 0 -> UiText.res(R.string.common_duration_hours_minutes, h, min % 60)
            else -> UiText.res(R.string.common_duration_minutes, min)
        }
    }
}

/** [ShopText.timeLeft] in a composable. */
@androidx.compose.runtime.Composable
fun ShopText.timeLeftLabel(ms: Long): String = timeLeft(ms).asString()

/** [ShopText.fishingTime] in a composable. */
@androidx.compose.runtime.Composable
fun ShopText.fishingTimeLabel(minutes: Float): String = fishingTime(minutes).asString()
