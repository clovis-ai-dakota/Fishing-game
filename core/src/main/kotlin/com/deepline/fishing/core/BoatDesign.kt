package com.deepline.fishing.core

/**
 * A boat the player designs by hand (Dex > Skins > Customise). Purely visual, like every skin:
 * the boat never touches gameplay. Colours are indexes into [BoatPalette.swatches]; which
 * options a player may use is decided by the unlock rules (see `BoatParts`).
 */
data class BoatDesign(
    val hull: Int = BoatPalette.indexOf("Driftwood"),
    val hullBottom: Int = BoatPalette.indexOf("Walnut"),
    val trim: Int = BoatPalette.indexOf("Sand"),
    val cabin: Int = BoatPalette.indexOf("Cream"),
    val sail: Int = BoatPalette.indexOf("Cream"),
    val flag: Int = BoatPalette.indexOf("Coral"),
    val pattern: HullPattern = HullPattern.SOLID,
    val sailStyle: SailStyle = SailStyle.NONE,
    val flagStyle: FlagStyle = FlagStyle.NONE,
    val emblem: Emblem = Emblem.NONE,
) {
    /** ARGB colours, resolved from the palette. */
    fun hullColor() = BoatPalette.color(hull)
    fun hullBottomColor() = BoatPalette.color(hullBottom)
    fun trimColor() = BoatPalette.color(trim)
    fun cabinColor() = BoatPalette.color(cabin)
    fun sailColor() = BoatPalette.color(sail)
    fun flagColor() = BoatPalette.color(flag)

    companion object {
        val DEFAULT = BoatDesign()
    }
}

enum class HullPattern(val displayName: String) {
    SOLID("Solid"),
    STRIPE("Racing stripe"),
    TWO_TONE("Two-tone"),
    WAVES("Waves"),
    DOTS("Polka dots"),
    CHECKER("Checkers"),
}

enum class SailStyle(val displayName: String) {
    NONE("No sail"),
    TRIANGLE("Triangle sail"),
    SQUARE("Square sail"),
    TWIN("Twin sails"),
}

enum class FlagStyle(val displayName: String) {
    NONE("No flag"),
    PENNANT("Pennant"),
    SWALLOWTAIL("Swallowtail"),
    BANNER("Banner"),
}

enum class Emblem(val displayName: String) {
    NONE("No emblem"),
    STAR("Star"),
    ANCHOR("Anchor"),
    FISH("Fish"),
    HEART("Heart"),
    MOON("Moon"),
    WAVE("Wave"),
}

/**
 * The colours a boat can be painted: a curated set (sea and sunset tones, woods and neutrals,
 * a few bright accents) so any combination stays pleasant to look at.
 */
object BoatPalette {
    data class Swatch(val name: String, val argb: Long)

    val swatches: List<Swatch> = listOf(
        // Neutrals and woods
        Swatch("Cream", 0xFFF6EBD9),
        Swatch("Sand", 0xFFE3C99A),
        Swatch("Driftwood", 0xFFB5743A),
        Swatch("Walnut", 0xFF5D3A1A),
        Swatch("Pearl", 0xFFF4F6F8),
        Swatch("Slate", 0xFF546E7A),
        Swatch("Charcoal", 0xFF263238),
        // Sea
        Swatch("Lagoon", 0xFF26C6DA),
        Swatch("Teal", 0xFF00897B),
        Swatch("Ocean", 0xFF1E88E5),
        Swatch("Navy", 0xFF1A237E),
        Swatch("Seafoam", 0xFFA5D6A7),
        // Sunset
        Swatch("Coral", 0xFFFF7F6B),
        Swatch("Tangerine", 0xFFFF9800),
        Swatch("Sunflower", 0xFFFFD54F),
        Swatch("Rose", 0xFFF48FB1),
        Swatch("Berry", 0xFFAD1457),
        Swatch("Crimson", 0xFFC62828),
        // Accents
        Swatch("Lilac", 0xFFB39DDB),
        Swatch("Violet", 0xFF6A1B9A),
        Swatch("Mint", 0xFF64FFDA),
        Swatch("Lime", 0xFFC6FF00),
        Swatch("Gold", 0xFFFFC107),
        Swatch("Midnight", 0xFF0D1B2A),
    )

    fun indexOf(name: String) = swatches.indexOfFirst { it.name == name }.also { require(it >= 0) { "No swatch $name" } }

    fun color(index: Int): Long = swatches[index.coerceIn(0, swatches.size - 1)].argb
}

/**
 * How the deep water looks. FAMILY (default) gets gradually spookier with depth; NIGHTMARE,
 * unlocked by reaching the Abyssal Floor, makes the deep properly frightening. Both show exactly
 * the same things: only colours, decor and the look of the fish change.
 */
enum class Atmosphere(val displayName: String) {
    FAMILY("Family"),
    NIGHTMARE("Nightmare"),
}
