package com.deepline.fishing.core

import kotlin.math.roundToLong

/** The sections of the Shop tab (rope upgrades are their own section, points only). */
enum class ShopSection(val displayName: String) {
    TACKLE("Tackle"),
    STYLE("Style"),
    PASSES("Passes"),
    AQUARIUM("Aquarium"),
}

/** How an item behaves once bought. */
enum class ItemKind {
    /** Used up (tackle packs): can be bought again and again. */
    CONSUMABLE,
    /** Owned for good: bought once. */
    PERMANENT,
    /** Bought for the current season or event (see `Seasons`). */
    SEASONAL,
    /** A monthly membership (Captain's Club). */
    SUBSCRIPTION,
}

/** What an item gives. */
sealed interface Grant {
    /** A pack of tackle sized to its price (see [ShopCatalog.packCount]), or a fixed [count]. */
    data class TacklePack(val tackle: Tackle, val count: Int? = null) : Grant

    /** Boat editor options (paints, emblems, prints...). */
    data class BoatParts(val parts: List<BoatPart>) : Grant

    /** A premade boat: its design, and every option it uses. */
    data class Preset(val presetId: String) : Grant

    /** Gear and title cosmetics sold in the shop. */
    data class Cosmetics(val items: List<Cosmetic>) : Grant

    /** Any other unlock, recorded as a key in [PlayerProgress.ownedItems] ("badge:RAINBOW"...). */
    data class Unlock(val key: String) : Grant

    /** Captain's Club time. */
    data class ClubDays(val days: Int) : Grant
}

/**
 * Something for sale. Every item can be bought with euros ([euroCents], the store price) or with
 * points, at the shop's exchange rate (see [ShopCatalog.pointsPrice]). [id] is also the store's
 * product id.
 */
data class ShopItem(
    val id: String,
    val name: String,
    val description: String,
    val section: ShopSection,
    val euroCents: Int,
    val kind: ItemKind,
    val grants: List<Grant>,
    /** Minutes of fishing per euro when paying with points. */
    val minutesPerEuro: Float = GameConfig.MINUTES_PER_EURO,
    /** A short tag shown on the card ("Best value", "-50%"...). */
    val badge: String? = null,
) {
    val euroLabel: String get() = "€" + "%d.%02d".format(euroCents / 100, euroCents % 100)
}

/** The shop's clock (tests and the developer menu can move it). */
object ShopClock {
    @Volatile
    var override: Long? = null

    fun now(): Long = override ?: System.currentTimeMillis()
}

/**
 * The shop: every item, its prices and what buying it does. All prices and contents are here.
 * Prices are mostly €0.50 to €2, a few at €5 and two specials at €10.
 */
object ShopCatalog {
    private fun parts(vararg p: BoatPart) = Grant.BoatParts(p.toList())

    private val tackle = listOf(
        ShopItem("lantern_pouch", "Lantern pouch", "A pouch of Lanterns.", ShopSection.TACKLE, 50, ItemKind.CONSUMABLE,
            listOf(Grant.TacklePack(Tackle.LANTERN)), minutesPerEuro = GameConfig.LANTERN_MINUTES_PER_EURO),
        ShopItem("lantern_crate", "Lantern crate", "A crate of Lanterns.", ShopSection.TACKLE, 99, ItemKind.CONSUMABLE,
            listOf(Grant.TacklePack(Tackle.LANTERN)), minutesPerEuro = GameConfig.LANTERN_MINUTES_PER_EURO),
        ShopItem("lantern_chest", "Lantern chest", "A chest of Lanterns.", ShopSection.TACKLE, 199, ItemKind.CONSUMABLE,
            listOf(Grant.TacklePack(Tackle.LANTERN)), minutesPerEuro = GameConfig.LANTERN_MINUTES_PER_EURO),
        ShopItem("lantern_barrel", "Lantern barrel", "A barrel of Lanterns, with a bulk bonus.", ShopSection.TACKLE, 499, ItemKind.CONSUMABLE,
            listOf(Grant.TacklePack(Tackle.LANTERN)), minutesPerEuro = GameConfig.LANTERN_MINUTES_PER_EURO, badge = "+20%"),
        ShopItem("detector_pouch", "Detector pouch", "A pouch of rare fish detectors.", ShopSection.TACKLE, 50, ItemKind.CONSUMABLE,
            listOf(Grant.TacklePack(Tackle.DETECTOR))),
        ShopItem("detector_crate", "Detector crate", "A crate of rare fish detectors.", ShopSection.TACKLE, 99, ItemKind.CONSUMABLE,
            listOf(Grant.TacklePack(Tackle.DETECTOR))),
        ShopItem("charm_bag", "Charm bag", "A bag of Lucky charms.", ShopSection.TACKLE, 50, ItemKind.CONSUMABLE,
            listOf(Grant.TacklePack(Tackle.LUCKY_CHARM))),
        ShopItem("lure_box", "Lure box", "A box of Sparkle lures.", ShopSection.TACKLE, 50, ItemKind.CONSUMABLE,
            listOf(Grant.TacklePack(Tackle.SPARKLE_LURE))),
    )

    private val style = listOf(
        // Single paints and emblems: €0.50.
        ShopItem("paint_flames", "Flames paint", "Flames licking up the hull.", ShopSection.STYLE, 50, ItemKind.PERMANENT, listOf(parts(BoatPart.of(HullPattern.FLAMES)))),
        ShopItem("paint_starry", "Starry night paint", "Twinkling stars on the hull.", ShopSection.STYLE, 50, ItemKind.PERMANENT, listOf(parts(BoatPart.of(HullPattern.STARRY)))),
        ShopItem("paint_metallic", "Metallic paint", "A polished metal sheen.", ShopSection.STYLE, 50, ItemKind.PERMANENT, listOf(parts(BoatPart.of(HullPattern.METALLIC)))),
        ShopItem("paint_neon", "Neon paint", "Glowing neon bands.", ShopSection.STYLE, 50, ItemKind.PERMANENT, listOf(parts(BoatPart.of(HullPattern.NEON)))),
        ShopItem("emblem_crown", "Crown emblem", "A golden crown.", ShopSection.STYLE, 50, ItemKind.PERMANENT, listOf(parts(BoatPart.of(Emblem.CROWN)))),
        ShopItem("emblem_skull", "Skull emblem", "A friendly pirate skull.", ShopSection.STYLE, 50, ItemKind.PERMANENT, listOf(parts(BoatPart.of(Emblem.SKULL)))),
        ShopItem("emblem_lightning", "Lightning emblem", "A bolt of lightning.", ShopSection.STYLE, 50, ItemKind.PERMANENT, listOf(parts(BoatPart.of(Emblem.LIGHTNING)))),
        ShopItem("emblem_shell", "Shell emblem", "A scallop shell.", ShopSection.STYLE, 50, ItemKind.PERMANENT, listOf(parts(BoatPart.of(Emblem.SHELL)))),
        ShopItem("emblem_trident", "Trident emblem", "The sea king's trident.", ShopSection.STYLE, 50, ItemKind.PERMANENT, listOf(parts(BoatPart.of(Emblem.TRIDENT)))),
        // Sail and flag prints: €0.99.
        ShopItem("cloth_stripes", "Striped sails", "Stripes on the sail and the flag.", ShopSection.STYLE, 99, ItemKind.PERMANENT, listOf(parts(BoatPart.of(ClothPattern.STRIPES)))),
        ShopItem("cloth_stars", "Starry sails", "Stars on the sail and the flag.", ShopSection.STYLE, 99, ItemKind.PERMANENT, listOf(parts(BoatPart.of(ClothPattern.STARS)))),
        ShopItem("cloth_waves", "Wave sails", "Waves on the sail and the flag.", ShopSection.STYLE, 99, ItemKind.PERMANENT, listOf(parts(BoatPart.of(ClothPattern.WAVES)))),
        // Gear pieces: €0.99 (looks only: same size and hitbox).
    ) + listOf(
        Cosmetic.CORAL_HOOK, Cosmetic.NEON_HOOK, Cosmetic.OBSIDIAN_HOOK,
        Cosmetic.OCEAN_LINE, Cosmetic.NEON_LINE, Cosmetic.SILVER_LINE,
        Cosmetic.HEART_TRAIL, Cosmetic.STAR_TRAIL, Cosmetic.PETAL_TRAIL,
    ).map { c ->
        ShopItem("gear_${c.name.lowercase()}", c.displayName, c.description, ShopSection.STYLE, 99, ItemKind.PERMANENT, listOf(Grant.Cosmetics(listOf(c))))
    } + listOf(
        // Premade boats: €0.99 to €1.99.
        preset("coral_cruiser", 99),
        preset("lagoon_explorer", 99),
        preset("sunset_racer", 149),
        preset("midnight_corsair", 149),
        preset("starlight_voyager", 199),
        preset("royal_regatta", 199),
        preset("galaxy_dreamer", 199),
        preset("neon_nights", 99),
        // Complete sets (boat + hook + line + trail): €2.99, cheaper than the four pieces.
        ShopItem(
            "set_coral_reef", "Coral Reef set", "The Coral Reef Cruiser with a matching hook, line and trail.", ShopSection.STYLE, 299, ItemKind.PERMANENT,
            listOf(Grant.Preset("coral_cruiser"), Grant.Cosmetics(listOf(Cosmetic.CORAL_HOOK, Cosmetic.OCEAN_LINE, Cosmetic.HEART_TRAIL))),
            badge = "Set",
        ),
        ShopItem(
            "set_neon_nights", "Neon Nights set", "The Neon Nights boat with a matching hook, line and trail.", ShopSection.STYLE, 299, ItemKind.PERMANENT,
            listOf(Grant.Preset("neon_nights"), Grant.Cosmetics(listOf(Cosmetic.NEON_HOOK, Cosmetic.NEON_LINE, Cosmetic.STAR_TRAIL))),
            badge = "Set",
        ),
    )

    private fun preset(id: String, cents: Int): ShopItem {
        val skin = PremiumSkins.byId(id) ?: error("No preset $id")
        return ShopItem("boat_$id", skin.name, skin.tagline, ShopSection.STYLE, cents, ItemKind.PERMANENT, listOf(Grant.Preset(id)))
    }

    private val passes = listOf(
        ShopItem(
            "starter_pack", "Starter pack", "The Coral Reef Cruiser, Metallic paint, a Neon line, a rainbow title badge and ${GameConfig.STARTER_PACK_LANTERNS} Lanterns.",
            ShopSection.PASSES, 199, ItemKind.PERMANENT,
            listOf(
                Grant.Preset("coral_cruiser"),
                parts(BoatPart.of(HullPattern.METALLIC)),
                Grant.Cosmetics(listOf(Cosmetic.NEON_LINE)),
                Grant.Unlock("badge:RAINBOW"),
                Grant.TacklePack(Tackle.LANTERN, count = GameConfig.STARTER_PACK_LANTERNS),
            ),
        ),
        ShopItem(
            "captains_club", "Captain's Club", "${GameConfig.CLUB_DAYS} days: the members-only Aurora paint, a club title badge, cloud save, and a free Lantern pouch each month.",
            ShopSection.PASSES, 199, ItemKind.SUBSCRIPTION, listOf(Grant.ClubDays(GameConfig.CLUB_DAYS)),
        ),
        ShopItem(
            "founders_pack", "Founder's pack", "The Founder title, the Founder's Flagship boat and a club badge that never expires.",
            ShopSection.PASSES, 999, ItemKind.PERMANENT,
            listOf(Grant.Cosmetics(listOf(Cosmetic.FOUNDER_TITLE)), Grant.Preset("founders_flagship"), Grant.Unlock("badge:CLUB_FOREVER")),
            badge = "Special",
        ),
    )

    /** Items added by other parts of the game (seasons, events, the aquarium...). */
    private val extensions = ArrayList<() -> List<ShopItem>>()

    /** Registers more items (each feature keeps its own catalogue next to its rules). */
    fun register(items: () -> List<ShopItem>) {
        synchronized(extensions) { extensions += items }
    }

    val all: List<ShopItem>
        get() = tackle + style + passes + synchronized(extensions) { extensions.flatMap { it() } }

    fun byId(id: String): ShopItem? = all.firstOrNull { it.id == id }

    fun inSection(section: ShopSection) = all.filter { it.section == section }

    // ---- Prices --------------------------------------------------------------------------------

    /** Minutes of fishing an item costs when paid with points. */
    fun minutes(item: ShopItem): Float = item.euroCents / 100f * item.minutesPerEuro * GameConfig.tierBonus(item.euroCents)

    /** The points price for this player: [minutes] of a good player's earnings at their rope's reach. */
    fun pointsPrice(item: ShopItem, p: PlayerProgress): Long {
        val level = p.level(RopeStat.LENGTH).coerceIn(0, GameConfig.POINTS_PER_MINUTE_BY_LENGTH_LEVEL.size - 1)
        val raw = minutes(item) * GameConfig.POINTS_PER_MINUTE_BY_LENGTH_LEVEL[level]
        return niceRound(raw.toDouble())
    }

    /** Rounds to 2 significant digits (1,234 -> 1,200; 56,789 -> 57,000). */
    fun niceRound(v: Double): Long {
        if (v < 100) return v.roundToLong().coerceAtLeast(1)
        var step = 1L
        while (v / step >= 100) step *= 10
        return (v / step).roundToLong() * step
    }

    /** How many of [tackle] a pack holds for this player: its points price in single items. */
    fun packCount(item: ShopItem, grant: Grant.TacklePack, p: PlayerProgress): Int =
        grant.count ?: (pointsPrice(item, p) / Progression.tacklePrice(p, grant.tackle)).toInt().coerceAtLeast(1)

    /**
     * What a bundle's contents would cost bought one by one, in euro cents (tackle at the pouch's
     * rate). Bundles show the saving as their badge, so it's always true.
     */
    fun separateCents(item: ShopItem, p: PlayerProgress): Int = item.grants.sumOf { g -> separateCents(item, g, p) }

    private fun separateCents(bundle: ShopItem, g: Grant, p: PlayerProgress): Int {
        val singles = all.filter { it !== bundle && it.grants.size == 1 }
        return when (g) {
            is Grant.TacklePack -> {
                val pouch = singles.filter { s -> (s.grants.first() as? Grant.TacklePack)?.let { it.tackle == g.tackle && it.count == null } == true }
                    .minByOrNull { it.euroCents } ?: return 0
                val perPouch = packCount(pouch, pouch.grants.first() as Grant.TacklePack, p)
                (packCount(bundle, g, p).toDouble() / perPouch * pouch.euroCents).roundToLong().toInt()
            }
            is Grant.Cosmetics -> g.items.sumOf { c -> singles.firstOrNull { (it.grants.first() as? Grant.Cosmetics)?.items == listOf(c) }?.euroCents ?: 0 }
            is Grant.BoatParts -> g.parts.sumOf { part -> singles.firstOrNull { (it.grants.first() as? Grant.BoatParts)?.parts == listOf(part) }?.euroCents ?: 0 }
            else -> singles.firstOrNull { it.grants.first() == g }?.euroCents ?: 0
        }
    }

    /** The saving shown on a bundle ("-45%"), or null when it isn't a bundle of shop items. */
    fun discountPercent(item: ShopItem, p: PlayerProgress): Int? {
        if (item.grants.size < 2) return null
        val separate = separateCents(item, p)
        if (separate <= item.euroCents) return null
        return ((1.0 - item.euroCents.toDouble() / separate) * 100).toInt()
    }

    // ---- Ownership -----------------------------------------------------------------------------

    fun owns(p: PlayerProgress, key: String) = key in p.ownedItems

    fun isClubMember(p: PlayerProgress, now: Long): Boolean = p.clubUntilMs > now || owns(p, "badge:CLUB_FOREVER")

    fun ownsCosmetic(p: PlayerProgress, c: Cosmetic) = owns(p, "cosmetic:${c.name}")

    fun ownsPreset(p: PlayerProgress, presetId: String) = owns(p, "preset:$presetId")

    /** Whether [item] can be bought now (permanent items once, the starter pack in its window...). */
    fun canBuy(item: ShopItem, p: PlayerProgress, now: Long): Boolean = when {
        item.kind == ItemKind.PERMANENT && owns(p, item.id) -> false
        item.id == "starter_pack" -> starterOfferOpen(p, now)
        else -> availability(item, p, now)
    }

    /** Extra availability rules registered by features (seasons, events...). */
    private val availabilityRules = ArrayList<(ShopItem, PlayerProgress, Long) -> Boolean?>()

    fun registerAvailability(rule: (ShopItem, PlayerProgress, Long) -> Boolean?) {
        synchronized(availabilityRules) { availabilityRules += rule }
    }

    private fun availability(item: ShopItem, p: PlayerProgress, now: Long): Boolean =
        synchronized(availabilityRules) { availabilityRules.firstNotNullOfOrNull { it(item, p, now) } } ?: true

    fun starterOfferOpen(p: PlayerProgress, now: Long) =
        !owns(p, "starter_pack") && p.starterOfferEndsMs > 0 && now < p.starterOfferEndsMs

    /** Counts an app session, and opens the starter pack offer on the right one. */
    fun onSessionStart(p: PlayerProgress, now: Long): PlayerProgress {
        val sessions = p.sessions + 1
        val opens = sessions >= GameConfig.STARTER_OFFER_FROM_SESSION && p.starterOfferEndsMs == 0L
        return p.copy(
            sessions = sessions,
            starterOfferEndsMs = if (opens) now + GameConfig.STARTER_OFFER_HOURS * 3_600_000L else p.starterOfferEndsMs,
        )
    }

    // ---- Captain's Club --------------------------------------------------------------------------

    /** The pouch a Club member gets for free each month. */
    val clubPouch: ShopItem get() = byId("lantern_pouch")!!

    fun clubPouchReady(p: PlayerProgress, now: Long) = isClubMember(p, now) && now >= p.clubPouchNextMs

    /** Gives a Club member their monthly Lantern pouch; null when it isn't ready. */
    fun claimClubPouch(p: PlayerProgress, now: Long): PlayerProgress? {
        if (!clubPouchReady(p, now)) return null
        return grant(p, clubPouch, now).copy(clubPouchNextMs = now + GameConfig.CLUB_DAYS * 86_400_000L)
    }

    /**
     * A store reports an active Club subscription (it renews by itself): membership lasts at least
     * [GameConfig.CLUB_RENEWAL_GRACE_DAYS] more days, so a lapsed renewal ends it soon after.
     */
    fun onClubSubscriptionActive(p: PlayerProgress, now: Long): PlayerProgress =
        p.copy(clubUntilMs = maxOf(p.clubUntilMs, now + GameConfig.CLUB_RENEWAL_GRACE_DAYS * 86_400_000L))

    // ---- Buying ----------------------------------------------------------------------------------

    /** Buys [item] with points; null when it can't be bought now or the player can't afford it. */
    fun buyWithPoints(p: PlayerProgress, item: ShopItem, now: Long): PlayerProgress? {
        if (!canBuy(item, p, now)) return null
        val price = pointsPrice(item, p)
        if (p.pointsBalance < price) return null
        return grant(p.copy(pointsBalance = p.pointsBalance - price), item, now)
    }

    /**
     * Gives what [item] contains (after a store purchase, a points purchase or a gift). Permanent
     * items are recorded as owned; packs are sized for this player's rope.
     */
    fun grant(p: PlayerProgress, item: ShopItem, now: Long): PlayerProgress {
        var q = p
        for (g in item.grants) q = apply(q, item, g, now)
        if (item.kind == ItemKind.PERMANENT) q = q.copy(ownedItems = q.ownedItems + item.id)
        return q
    }

    private fun apply(p: PlayerProgress, item: ShopItem, g: Grant, now: Long): PlayerProgress = when (g) {
        is Grant.TacklePack -> Progression.withTackle(p, g.tackle, Progression.tackleCount(p, g.tackle) + packCount(item, g, p))
        is Grant.BoatParts -> p.copy(unlockedBoatParts = p.unlockedBoatParts + g.parts.map { it.key })
        is Grant.Preset -> {
            val design = PremiumSkins.byId(g.presetId)?.design
            val keys = design?.let { BoatParts.partsOf(it).map { part -> part.key } } ?: emptyList()
            p.copy(ownedItems = p.ownedItems + "preset:${g.presetId}", unlockedBoatParts = p.unlockedBoatParts + keys)
        }
        is Grant.Cosmetics -> p.copy(ownedItems = p.ownedItems + g.items.map { "cosmetic:${it.name}" })
        is Grant.Unlock -> p.copy(ownedItems = p.ownedItems + g.key)
        is Grant.ClubDays -> p.copy(clubUntilMs = maxOf(p.clubUntilMs, now) + g.days * 86_400_000L)
    }

    /** Wears a premade boat the player owns (as their custom boat). */
    fun wearPreset(p: PlayerProgress, presetId: String): PlayerProgress? {
        if (!ownsPreset(p, presetId)) return null
        val design = PremiumSkins.byId(presetId)?.design ?: return null
        return Progression.equipCustomBoat(p.copy(boatDesign = design), on = true)
    }
}
