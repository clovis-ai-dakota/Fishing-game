package com.deepline.fishing.core

/** The kinds of choices in the boat editor. Colours are unlocked per swatch, for every slot. */
enum class BoatPartKind { COLOR, PATTERN, SAIL, FLAG, EMBLEM }

/** One option of the boat editor: a swatch, a hull pattern, a sail, a flag or an emblem. */
data class BoatPart(val kind: BoatPartKind, val id: String) {
    /** Saved in [PlayerProgress.unlockedBoatParts]. */
    val key: String get() = "${kind.name}:$id"

    companion object {
        fun color(index: Int) = BoatPart(BoatPartKind.COLOR, BoatPalette.swatches[index.coerceIn(0, BoatPalette.swatches.size - 1)].name)
        fun of(p: HullPattern) = BoatPart(BoatPartKind.PATTERN, p.name)
        fun of(s: SailStyle) = BoatPart(BoatPartKind.SAIL, s.name)
        fun of(f: FlagStyle) = BoatPart(BoatPartKind.FLAG, f.name)
        fun of(e: Emblem) = BoatPart(BoatPartKind.EMBLEM, e.name)

        fun fromKey(key: String): BoatPart? {
            val kind = BoatPartKind.entries.firstOrNull { it.name == key.substringBefore(':') } ?: return null
            val part = BoatPart(kind, key.substringAfter(':', ""))
            return part.takeIf { it in BoatParts.all }
        }
    }
}

/** What it takes to use a boat option. */
sealed interface PartRequirement {
    /** Always available. */
    data object Free : PartRequirement

    /** Bought once with points from the balance (lifetime earnings don't change). */
    data class Points(val cost: Int) : PartRequirement

    /** Available once this many species are in the Dex as caught. */
    data class SpeciesCaught(val count: Int) : PartRequirement

    /** Available once a fish from [zone] has been caught. */
    data class FishInZone(val zone: DepthZone) : PartRequirement

    /** Available once this many milestones have been claimed. */
    data class MilestonesClaimed(val count: Int) : PartRequirement

    /** Only on the premade premium skins of the shop: never in the editor. */
    data object PremiumOnly : PartRequirement
}

/**
 * The unlock table of the boat editor (all its tunable numbers are here). The default design
 * only uses free options; a great boat takes many casts of points and progress.
 */
object BoatParts {
    private val free = PartRequirement.Free

    private fun pts(cost: Int) = PartRequirement.Points(cost * GameConfig.BOAT_PART_PRICE_SCALE)

    private val colors: Map<String, PartRequirement> = mapOf(
        "Cream" to free,
        "Sand" to free,
        "Driftwood" to free,
        "Walnut" to free,
        "Pearl" to free,
        "Slate" to free,
        "Coral" to free,
        "Ocean" to free,
        "Charcoal" to pts(300),
        "Lagoon" to pts(500),
        "Teal" to pts(500),
        "Seafoam" to pts(600),
        "Tangerine" to pts(600),
        "Navy" to pts(800),
        "Sunflower" to pts(800),
        "Rose" to pts(800),
        "Lilac" to pts(1_000),
        "Berry" to pts(1_200),
        "Crimson" to pts(1_200),
        "Violet" to pts(1_500),
        "Midnight" to PartRequirement.FishInZone(DepthZone.MIDNIGHT),
        "Mint" to PartRequirement.SpeciesCaught(25),
        "Lime" to PartRequirement.SpeciesCaught(50),
        "Gold" to PartRequirement.MilestonesClaimed(5),
    )

    private val patterns: Map<HullPattern, PartRequirement> = mapOf(
        HullPattern.SOLID to free,
        HullPattern.STRIPE to pts(800),
        HullPattern.TWO_TONE to pts(1_500),
        HullPattern.WAVES to PartRequirement.FishInZone(DepthZone.TWILIGHT),
        HullPattern.DOTS to pts(2_500),
        HullPattern.CHECKER to PartRequirement.SpeciesCaught(40),
        HullPattern.FLAMES to PartRequirement.PremiumOnly,
        HullPattern.STARRY to PartRequirement.PremiumOnly,
    )

    private val sails: Map<SailStyle, PartRequirement> = mapOf(
        SailStyle.NONE to free,
        SailStyle.TRIANGLE to pts(2_000),
        SailStyle.SQUARE to pts(4_000),
        SailStyle.TWIN to PartRequirement.FishInZone(DepthZone.ABYSS),
    )

    private val flags: Map<FlagStyle, PartRequirement> = mapOf(
        FlagStyle.NONE to free,
        FlagStyle.PENNANT to pts(1_000),
        FlagStyle.SWALLOWTAIL to pts(3_000),
        FlagStyle.BANNER to PartRequirement.MilestonesClaimed(8),
    )

    private val emblems: Map<Emblem, PartRequirement> = mapOf(
        Emblem.NONE to free,
        Emblem.STAR to pts(1_500),
        Emblem.FISH to PartRequirement.SpeciesCaught(15),
        Emblem.ANCHOR to pts(2_500),
        Emblem.HEART to pts(3_000),
        Emblem.MOON to PartRequirement.FishInZone(DepthZone.MIDNIGHT),
        Emblem.WAVE to pts(5_000),
        Emblem.CROWN to PartRequirement.PremiumOnly,
        Emblem.SKULL to PartRequirement.PremiumOnly,
        Emblem.LIGHTNING to PartRequirement.PremiumOnly,
    )

    /** Every option, in editor order. */
    val all: List<BoatPart> =
        BoatPalette.swatches.indices.map { BoatPart.color(it) } +
            HullPattern.entries.map { BoatPart.of(it) } +
            SailStyle.entries.map { BoatPart.of(it) } +
            FlagStyle.entries.map { BoatPart.of(it) } +
            Emblem.entries.map { BoatPart.of(it) }

    fun requirement(part: BoatPart): PartRequirement = when (part.kind) {
        BoatPartKind.COLOR -> colors[part.id]
        BoatPartKind.PATTERN -> patterns[HullPattern.entries.firstOrNull { it.name == part.id }]
        BoatPartKind.SAIL -> sails[SailStyle.entries.firstOrNull { it.name == part.id }]
        BoatPartKind.FLAG -> flags[FlagStyle.entries.firstOrNull { it.name == part.id }]
        BoatPartKind.EMBLEM -> emblems[Emblem.entries.firstOrNull { it.name == part.id }]
    } ?: PartRequirement.PremiumOnly

    fun isPremiumOnly(part: BoatPart) = requirement(part) == PartRequirement.PremiumOnly

    /** Whether the player may use [part] in their own design now. */
    fun isAvailable(p: PlayerProgress, part: BoatPart): Boolean = when (val r = requirement(part)) {
        PartRequirement.Free -> true
        is PartRequirement.Points -> part.key in p.unlockedBoatParts
        is PartRequirement.SpeciesCaught -> p.discoveredCount >= r.count
        is PartRequirement.FishInZone -> p.collection.any { (id, e) -> e.caught > 0 && FishCatalog.byId(id)?.zone == r.zone }
        is PartRequirement.MilestonesClaimed -> p.claimedMilestones.size >= r.count
        PartRequirement.PremiumOnly -> false
    }

    /** The options [design] uses (each colour once). */
    fun partsOf(design: BoatDesign): Set<BoatPart> = setOf(
        BoatPart.color(design.hull), BoatPart.color(design.hullBottom), BoatPart.color(design.trim),
        BoatPart.color(design.cabin), BoatPart.color(design.sail), BoatPart.color(design.flag),
        BoatPart.of(design.pattern), BoatPart.of(design.sailStyle), BoatPart.of(design.flagStyle), BoatPart.of(design.emblem),
    )
}

/** A premade boat for the shop. */
data class PremiumSkin(val id: String, val name: String, val tagline: String, val price: String, val design: BoatDesign)

/**
 * The shop's premade boats. They can't be bought or worn yet: payments are not wired, so the shop
 * shows them as "coming soon".
 *
 * PLACEHOLDER PRICES: set the real ones (and the store product ids) when payments are added.
 */
object PremiumSkins {
    private fun c(name: String) = BoatPalette.indexOf(name)

    val all: List<PremiumSkin> = listOf(
        PremiumSkin(
            "coral_cruiser", "Coral Reef Cruiser", "Sunny colours and a rose sail.", "$0.99",
            BoatDesign(c("Coral"), c("Pearl"), c("Sunflower"), c("Cream"), c("Rose"), c("Lagoon"), HullPattern.WAVES, SailStyle.TRIANGLE, FlagStyle.PENNANT, Emblem.STAR),
        ),
        PremiumSkin(
            "lagoon_explorer", "Lagoon Explorer", "Polka dots and an anchor, ready for adventure.", "$0.99",
            BoatDesign(c("Lagoon"), c("Teal"), c("Pearl"), c("Seafoam"), c("Pearl"), c("Sunflower"), HullPattern.DOTS, SailStyle.TRIANGLE, FlagStyle.PENNANT, Emblem.ANCHOR),
        ),
        PremiumSkin(
            "sunset_racer", "Sunset Racer", "Flames from bow to stern.", "$1.49",
            BoatDesign(c("Tangerine"), c("Berry"), c("Sunflower"), c("Cream"), c("Sunflower"), c("Crimson"), HullPattern.FLAMES, SailStyle.TRIANGLE, FlagStyle.PENNANT, Emblem.LIGHTNING),
        ),
        PremiumSkin(
            "midnight_corsair", "Midnight Corsair", "A pirate ship with black sails.", "$1.99",
            BoatDesign(c("Charcoal"), c("Midnight"), c("Crimson"), c("Slate"), c("Midnight"), c("Crimson"), HullPattern.STRIPE, SailStyle.SQUARE, FlagStyle.SWALLOWTAIL, Emblem.SKULL),
        ),
        PremiumSkin(
            "starlight_voyager", "Starlight Voyager", "A night sky painted on the hull.", "$2.99",
            BoatDesign(c("Navy"), c("Midnight"), c("Gold"), c("Lilac"), c("Lilac"), c("Gold"), HullPattern.STARRY, SailStyle.TWIN, FlagStyle.BANNER, Emblem.MOON),
        ),
        PremiumSkin(
            "royal_regatta", "Royal Regatta", "Gold trim and a crowned sail.", "$3.99",
            BoatDesign(c("Pearl"), c("Navy"), c("Gold"), c("Cream"), c("Navy"), c("Navy"), HullPattern.TWO_TONE, SailStyle.SQUARE, FlagStyle.BANNER, Emblem.CROWN),
        ),
        PremiumSkin(
            "galaxy_dreamer", "Galaxy Dreamer", "Violet stars and mint highlights.", "$4.99",
            BoatDesign(c("Violet"), c("Midnight"), c("Mint"), c("Lilac"), c("Violet"), c("Mint"), HullPattern.STARRY, SailStyle.TWIN, FlagStyle.SWALLOWTAIL, Emblem.STAR),
        ),
    )
}
