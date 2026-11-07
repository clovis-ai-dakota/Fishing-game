package com.deepline.fishing.core

/*
 * Ways to show a collection off, all looks only: the frame around the player's title badge,
 * the style of the Dex's caught cards, and an aquarium of caught fish with decor.
 */

/** The frame of the player's title badge (shown under their name, online too). */
enum class TitleFrame(val displayName: String, val unlockKey: String?) {
    GOLD("Gold", null),
    RAINBOW("Rainbow", "badge:RAINBOW"),
    OCEAN("Ocean", "badge:OCEAN"),
    EMBER("Ember", "badge:EMBER"),
    /** While a Captain's Club member. */
    CLUB("Captain's Club", null),
    CLUB_FOREVER("Founder's club", "badge:CLUB_FOREVER"),
    SEASON("Season", "badge:SEASON"),
    COLLECTOR("Collector", "badge:COLLECTOR"),
    EVENT("Weekend event", "badge:EVENT"),
    /** Won by finishing a weekly tournament in the top 10% (see `TournamentTier`). */
    TOURNAMENT("Tournament", "badge:TOURNAMENT");

    fun isAvailable(p: PlayerProgress, now: Long): Boolean = when (this) {
        GOLD -> true
        CLUB -> ShopCatalog.isClubMember(p, now)
        else -> unlockKey in p.ownedItems
    }

    companion object {
        fun of(name: String?) = entries.firstOrNull { it.name == name } ?: GOLD
    }
}

/** How the Dex shows the cards of caught species. */
enum class CardStyle(val displayName: String) {
    CLASSIC("Classic"),
    HOLO("Holographic"),
    GOLD("Gold foil"),
    NIGHT("Starry night"),
    CORAL("Coral");

    val unlockKey: String? get() = if (this == CLASSIC) null else "card:$name"

    fun isAvailable(p: PlayerProgress) = unlockKey == null || unlockKey in p.ownedItems

    companion object {
        fun of(name: String?) = entries.firstOrNull { it.name == name } ?: CLASSIC
    }
}

/** Things to put in the aquarium. The first two come with it. */
enum class Decor(val displayName: String, val description: String, val euroCents: Int) {
    PLANTS("Water plants", "Swaying green plants.", 0),
    PEBBLES("Pebbles", "Smooth river pebbles.", 0),
    SAND_CASTLE("Sand castle", "A little castle with towers.", 50),
    BUBBLER("Bubbler", "A treasure diver blowing bubbles.", 50),
    TREASURE_CHEST("Treasure chest", "A chest that opens to show gold.", 99),
    CORAL_GARDEN("Coral garden", "Pink and orange corals.", 99),
    LANTERN_LIGHTS("Lantern lights", "A string of glowing lanterns along the top.", 99),
    SUNKEN_SHIP("Sunken ship", "A wreck resting on the sand.", 199);

    val unlockKey: String get() = "decor:$name"
    val free: Boolean get() = euroCents == 0
}

object Showcase {
    const val TANK_SIZE = 6
    const val BIG_TANK_SIZE = 12

    fun hasBigTank(p: PlayerProgress) = "aquarium:big" in p.ownedItems

    fun tankSize(p: PlayerProgress) = if (hasBigTank(p)) BIG_TANK_SIZE else TANK_SIZE

    fun ownsDecor(p: PlayerProgress, d: Decor) = d.free || d.unlockKey in p.ownedItems

    /** Decor shown in the tank: what the player owns and hasn't put away. */
    fun shownDecor(p: PlayerProgress) = Decor.entries.filter { ownsDecor(p, it) && it.name !in p.hiddenDecor }

    /**
     * The fish in the tank: the player's picks, or else their best catches. Only caught species,
     * never more than the tank holds.
     */
    fun tankFish(p: PlayerProgress): List<FishSpecies> {
        val caught = FishCatalog.all.filter { p.isDiscovered(it) }
        val picked = p.tankFish.mapNotNull { id -> caught.firstOrNull { it.id == id } }
        val chosen = picked.ifEmpty { caught.sortedByDescending { p.collection[it.id]?.bestPoints ?: 0 } }
        return chosen.take(tankSize(p))
    }

    /** Adds [id] to the tank, or takes it out; null if it isn't caught or the tank is full. */
    fun toggleTankFish(p: PlayerProgress, id: String): PlayerProgress? {
        val species = FishCatalog.byId(id) ?: return null
        if (!p.isDiscovered(species)) return null
        val current = tankFish(p).map { it.id }
        return when {
            id in current -> p.copy(tankFish = current - id)
            current.size >= tankSize(p) -> null
            else -> p.copy(tankFish = current + id)
        }
    }

    fun toggleDecor(p: PlayerProgress, d: Decor): PlayerProgress? {
        if (!ownsDecor(p, d)) return null
        return p.copy(hiddenDecor = if (d.name in p.hiddenDecor) p.hiddenDecor - d.name else p.hiddenDecor + d.name)
    }

    fun setTitleFrame(p: PlayerProgress, frame: TitleFrame, now: Long): PlayerProgress? =
        if (frame.isAvailable(p, now)) p.copy(titleFrame = frame.name) else null

    /** The frame worn: falls back to gold when the chosen one isn't available any more (the Club ended). */
    fun titleFrame(p: PlayerProgress, now: Long): TitleFrame = TitleFrame.of(p.titleFrame).takeIf { it.isAvailable(p, now) } ?: TitleFrame.GOLD

    fun setCardStyle(p: PlayerProgress, style: CardStyle): PlayerProgress? =
        if (style.isAvailable(p)) p.copy(cardStyle = style.name) else null

    fun cardStyle(p: PlayerProgress): CardStyle = CardStyle.of(p.cardStyle).takeIf { it.isAvailable(p) } ?: CardStyle.CLASSIC

    // ---- Shop items --------------------------------------------------------------------------------

    val shopItems: List<ShopItem> =
        listOf(
            ShopItem("badge_rainbow", "Rainbow title badge", "A title badge in shifting rainbow colours.", ShopSection.STYLE, 99, ItemKind.PERMANENT, listOf(Grant.Unlock("badge:RAINBOW"))),
            ShopItem("badge_ocean", "Ocean title badge", "A title badge with rolling waves.", ShopSection.STYLE, 99, ItemKind.PERMANENT, listOf(Grant.Unlock("badge:OCEAN"))),
            ShopItem("badge_ember", "Ember title badge", "A title badge with glowing embers.", ShopSection.STYLE, 99, ItemKind.PERMANENT, listOf(Grant.Unlock("badge:EMBER"))),
        ) +
            (CardStyle.entries - CardStyle.CLASSIC).map {
                ShopItem("card_${it.name.lowercase()}", "${it.displayName} Dex cards", "Your caught species' Dex cards in ${it.displayName.lowercase()}.", ShopSection.STYLE, 50, ItemKind.PERMANENT, listOf(Grant.Unlock(it.unlockKey!!)))
            } +
            Decor.entries.filterNot { it.free }.map {
                ShopItem("decor_${it.name.lowercase()}", it.displayName, it.description, ShopSection.AQUARIUM, it.euroCents, ItemKind.PERMANENT, listOf(Grant.Unlock(it.unlockKey)))
            } +
            ShopItem(
                "aquarium_expansion", "Bigger aquarium", "Room for $BIG_TANK_SIZE fish instead of $TANK_SIZE, in a wider tank.",
                ShopSection.AQUARIUM, 499, ItemKind.PERMANENT, listOf(Grant.Unlock("aquarium:big")),
            )
}
