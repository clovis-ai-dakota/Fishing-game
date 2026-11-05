package com.deepline.fishing.core

/**
 * How the app's menus look: a sea scene behind the tabs and cards in its colours. Lagoon is the
 * default; every theme has a day and a night version ([NightMode]). Themes are looks only: the
 * water of a cast is never changed by them.
 *
 * Shop themes cost more the richer they are ([tier]): a palette with a simple scene (1), a scene
 * with its own decor (2), a fully animated scene (3), and the most elaborate, glowing one (4).
 */
enum class MenuTheme(val displayName: String, val description: String, val euroCents: Int, val tier: Int, val clubOnly: Boolean = false) {
    LAGOON("Lagoon", "Sunlit turquoise water, rays of light and rising bubbles.", 0, 0),
    KELP_FOREST("Kelp Forest", "Green-gold light through swaying kelp.", 50, 1),
    SANDBAR("Sandbar", "Shallow, sandy water with rippling light.", 50, 1),
    CORAL_REEF("Coral Reef", "Coral pinks and oranges, with a reef below.", 99, 2),
    POLAR("Polar Waters", "Icy blues, floating ice and drifting snow.", 99, 2),
    SUNSET_HARBOR("Sunset Harbor", "A glowing sky over the harbour, boats on the horizon.", 199, 3),
    KOI_POND("Koi Pond", "Jade water, koi gliding by and lily pads above.", 199, 3),
    TREASURE_COVE("Treasure Cove", "Deep blue and gold: coins glinting on the sea floor.", 199, 3),
    GLOW_TIDE("Glow Tide", "Glowing jellyfish and sparkling plankton in a living sea.", 499, 4),
    /** A Captain's Club perk, while a member. */
    AURORA("Aurora", "Northern lights over the water.", 0, 3, clubOnly = true);

    val unlockKey: String get() = "theme:$name"

    /** Sold in the shop (not the default, not the Club's). */
    val forSale: Boolean get() = euroCents > 0

    fun isAvailable(p: PlayerProgress, now: Long): Boolean = when {
        this == LAGOON -> true
        clubOnly -> ShopCatalog.isClubMember(p, now)
        else -> unlockKey in p.ownedItems
    }

    companion object {
        fun of(name: String?) = entries.firstOrNull { it.name == name } ?: LAGOON
    }
}

/** Day, night, or whichever the phone uses. The default is day. */
enum class NightMode(val displayName: String) {
    DAY("Day"),
    NIGHT("Night"),
    AUTO("Like my phone");

    fun isNight(phoneIsDark: Boolean) = this == NIGHT || (this == AUTO && phoneIsDark)

    companion object {
        fun of(name: String?) = entries.firstOrNull { it.name == name } ?: DAY
    }
}

object Themes {
    /** The theme shown: the chosen one, or Lagoon once it isn't available (the Club ended). */
    fun theme(p: PlayerProgress, now: Long): MenuTheme = MenuTheme.of(p.menuTheme).takeIf { it.isAvailable(p, now) } ?: MenuTheme.LAGOON

    fun setTheme(p: PlayerProgress, theme: MenuTheme, now: Long): PlayerProgress? =
        if (theme.isAvailable(p, now)) p.copy(menuTheme = theme.name) else null

    fun setNightMode(p: PlayerProgress, mode: NightMode): PlayerProgress = p.copy(nightMode = mode.name)

    fun nightMode(p: PlayerProgress): NightMode = NightMode.of(p.nightMode)

    val forSale: List<MenuTheme> get() = MenuTheme.entries.filter { it.forSale }

    val shopItems: List<ShopItem> =
        forSale.map {
            ShopItem("theme_${it.name.lowercase()}", "${it.displayName} theme", it.description, ShopSection.STYLE, it.euroCents, ItemKind.PERMANENT, listOf(Grant.Unlock(it.unlockKey)))
        } +
            ShopItem(
                "theme_collection", "Every theme", "All ${forSale.size} themes for the menus, day and night.",
                ShopSection.STYLE, 999, ItemKind.PERMANENT, forSale.map { Grant.Unlock(it.unlockKey) },
            )
}
