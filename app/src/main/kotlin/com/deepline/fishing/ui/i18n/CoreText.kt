package com.deepline.fishing.ui.i18n

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import com.deepline.fishing.R
import com.deepline.fishing.core.Atmosphere
import com.deepline.fishing.core.BattleMode
import com.deepline.fishing.core.BattleScore
import com.deepline.fishing.core.BoatPalette
import com.deepline.fishing.core.BotDifficulty
import com.deepline.fishing.core.CardStyle
import com.deepline.fishing.core.ChallengeDifficulty
import com.deepline.fishing.core.ClothPattern
import com.deepline.fishing.core.Cosmetic
import com.deepline.fishing.core.CosmeticSlot
import com.deepline.fishing.core.Decor
import com.deepline.fishing.core.DepthZone
import com.deepline.fishing.core.Emblem
import com.deepline.fishing.core.EventGoal
import com.deepline.fishing.core.FishSpecies
import com.deepline.fishing.core.FlagStyle
import com.deepline.fishing.core.GameConfig
import com.deepline.fishing.core.HullPattern
import com.deepline.fishing.core.MenuTheme
import com.deepline.fishing.core.NightMode
import com.deepline.fishing.core.ObstacleKind
import com.deepline.fishing.core.PremiumSkin
import com.deepline.fishing.core.PremiumSkins
import com.deepline.fishing.core.RopeStat
import com.deepline.fishing.core.SailStyle
import com.deepline.fishing.core.Season
import com.deepline.fishing.core.Secrets
import com.deepline.fishing.core.ShopItem
import com.deepline.fishing.core.ShopSection
import com.deepline.fishing.core.Showcase
import com.deepline.fishing.core.Tackle
import com.deepline.fishing.core.Themes
import com.deepline.fishing.core.TitleFrame
import com.deepline.fishing.core.social.BoardKind
import com.deepline.fishing.core.social.BoardView
import com.deepline.fishing.core.social.ClubRole
import com.deepline.fishing.core.social.TournamentTier

/*
 * The names and descriptions of things core defines, in the game's language.
 *
 * Core keeps its English names (`displayName`, `title`, `name`...) for the server, the tests and
 * the analytics: the app never shows them. It shows these instead, from strings_core.xml and
 * strings_species.xml:
 *
 *   zone.label()            in a composable (a String)
 *   zone.labelText()        anywhere else (a UiText, resolved where it is shown)
 *   tackle.descriptionText(), mode.rulesText(), item.nameText()...
 *
 * Every enum maps with an exhaustive `when`, so a new entry doesn't compile until it has a string;
 * ids (shop items, boats, species) are checked by CoreTextTest.
 */

// ---- Labels: composable and UiText forms ----------------------------------------------------------

fun DepthZone.labelText(): UiText = UiText.res(CoreText.zone(this))
@Composable @ReadOnlyComposable fun DepthZone.label(): String = labelText().asString()
/** "Shallows", "Reef", "Trench", "Floor". */
fun DepthZone.shortLabelText(): UiText = UiText.res(CoreText.zoneShort(this))
@Composable @ReadOnlyComposable fun DepthZone.shortLabel(): String = shortLabelText().asString()

fun ObstacleKind.labelText(): UiText = UiText.res(CoreText.obstacle(this))
@Composable @ReadOnlyComposable fun ObstacleKind.label(): String = labelText().asString()

fun RopeStat.labelText(): UiText = UiText.res(CoreText.rope(this))
@Composable @ReadOnlyComposable fun RopeStat.label(): String = labelText().asString()
/** "Length", "Strength", "Agility". */
fun RopeStat.shortLabelText(): UiText = UiText.res(CoreText.ropeShort(this))
@Composable @ReadOnlyComposable fun RopeStat.shortLabel(): String = shortLabelText().asString()
fun RopeStat.descriptionText(): UiText = UiText.res(CoreText.ropeDesc(this))
@Composable @ReadOnlyComposable fun RopeStat.description(): String = descriptionText().asString()

fun Tackle.labelText(): UiText = UiText.res(CoreText.tackle(this))
@Composable @ReadOnlyComposable fun Tackle.label(): String = labelText().asString()
fun Tackle.descriptionText(): UiText = when (this) {
    Tackle.LANTERN -> UiText.res(R.string.core_tackle_lantern_desc, GameConfig.DARK_DEPTH.toInt())
    Tackle.LUCKY_CHARM -> UiText.res(
        R.string.core_tackle_lucky_charm_desc,
        GameConfig.DESCENT_TOUCH_LIMIT + GameConfig.LUCKY_CHARM_EXTRA_BUMPS,
        GameConfig.DESCENT_TOUCH_LIMIT,
    )
    Tackle.SPARKLE_LURE -> UiText.res(R.string.core_tackle_sparkle_lure_desc, GameConfig.SPARKLE_LURE_MULTIPLIER.toInt())
    Tackle.DETECTOR -> UiText.res(R.string.core_tackle_detector_desc, GameConfig.DETECTOR_RADIUS_M.toInt())
}
@Composable @ReadOnlyComposable fun Tackle.description(): String = descriptionText().asString()

fun BattleMode.labelText(): UiText = UiText.res(CoreText.mode(this))
@Composable @ReadOnlyComposable fun BattleMode.label(): String = labelText().asString()
/** The mode's rules, one or two sentences. */
fun BattleMode.rulesText(): UiText = when (this) {
    BattleMode.BEST_OF_FIVE -> UiText.res(R.string.core_mode_best_of_five_rules)
    BattleMode.TIME_ATTACK -> UiText.res(R.string.core_mode_time_attack_rules, ((timeLimitSec ?: 0f) / 60f).toInt())
    BattleMode.HEAVY_HAUL -> UiText.res(R.string.core_mode_heavy_haul_rules, castsPerPlayer ?: 0)
    BattleMode.SPECIES_HUNT -> UiText.res(R.string.core_mode_species_hunt_rules, castsPerPlayer ?: 0)
    BattleMode.DEEP_DIVE -> UiText.res(R.string.core_mode_deep_dive_rules, castsPerPlayer ?: 0)
}
@Composable @ReadOnlyComposable fun BattleMode.rules(): String = rulesText().asString()

/** A battle player's score line ("2 rounds", "12.5 kg"...), from core's numbers. */
fun BattleScore.labelText(mode: BattleMode): UiText = when (mode) {
    BattleMode.BEST_OF_FIVE -> primary.toInt().let { UiText.plural(R.plurals.core_score_rounds, it) }
    BattleMode.TIME_ATTACK -> UiText.res(R.string.core_score_best_cast, UiText.number(primary.toLong()))
    BattleMode.HEAVY_HAUL -> UiText.res(R.string.core_score_kg, UiText.Decimal(primary, 1))
    BattleMode.SPECIES_HUNT -> primary.toInt().let { UiText.plural(R.plurals.core_score_species, it) }
    BattleMode.DEEP_DIVE -> UiText.res(R.string.core_score_deepest, UiText.Decimal(primary, 0))
}

fun BotDifficulty.labelText(): UiText = UiText.res(CoreText.bot(this))
@Composable @ReadOnlyComposable fun BotDifficulty.label(): String = labelText().asString()

fun ChallengeDifficulty.labelText(): UiText = UiText.res(CoreText.challengeDifficulty(this))
@Composable @ReadOnlyComposable fun ChallengeDifficulty.label(): String = labelText().asString()

fun CosmeticSlot.labelText(): UiText = UiText.res(CoreText.slot(this))
@Composable @ReadOnlyComposable fun CosmeticSlot.label(): String = labelText().asString()

fun Cosmetic.labelText(): UiText = UiText.res(CoreText.cosmetic(this))
@Composable @ReadOnlyComposable fun Cosmetic.label(): String = labelText().asString()
fun Cosmetic.descriptionText(): UiText = UiText.res(CoreText.cosmeticDesc(this))
@Composable @ReadOnlyComposable fun Cosmetic.description(): String = descriptionText().asString()

/** A skin set's name ("Giant-slayer"), from core's English set name. */
fun skinSetText(setName: String): UiText = CoreText.sets[setName]?.let { UiText.res(it) } ?: UiText.raw(setName)
@Composable @ReadOnlyComposable fun skinSetLabel(setName: String): String = skinSetText(setName).asString()

/**
 * A worn title (core relays titles by their English name, also online): its name in the game's
 * language, or [title] itself when it isn't a known title (a player's name shown in its place).
 */
fun titleText(title: String): UiText =
    Cosmetic.entries.firstOrNull { it.slot == CosmeticSlot.TITLE && it.displayName == title }?.labelText() ?: UiText.raw(title)
@Composable @ReadOnlyComposable fun titleLabel(title: String): String = titleText(title).asString()

fun ShopSection.labelText(): UiText = UiText.res(CoreText.shopSection(this))
@Composable @ReadOnlyComposable fun ShopSection.label(): String = labelText().asString()

/** A shop item's name: its own string, or the skin, boat, decor or theme it is. */
fun ShopItem.nameText(): UiText {
    CoreText.items[id]?.let { return UiText.res(it.first) }
    CoreText.cosmeticOfItem(id)?.let { return it.labelText() }
    CoreText.boatOfItem(id)?.let { return it.nameText() }
    CoreText.decorOfItem(id)?.let { return it.labelText() }
    CoreText.themeOfItem(id)?.let { return UiText.res(R.string.core_shop_item_theme, it.labelText()) }
    return UiText.raw(name)
}
@Composable @ReadOnlyComposable fun ShopItem.label(): String = nameText().asString()

fun ShopItem.descriptionText(): UiText {
    when (id) {
        "starter_pack" -> return UiText.res(R.string.core_shop_item_starter_pack_desc, GameConfig.STARTER_PACK_LANTERNS)
        "captains_club" -> return UiText.res(R.string.core_shop_item_captains_club_desc, GameConfig.CLUB_DAYS)
        "collector_edition" -> return UiText.res(R.string.core_shop_item_collector_edition_desc, GameConfig.COLLECTOR_TIER_SKIP)
        "aquarium_expansion" -> return UiText.res(R.string.core_shop_item_aquarium_expansion_desc, Showcase.BIG_TANK_SIZE, Showcase.TANK_SIZE)
        "theme_collection" -> return UiText.res(R.string.core_shop_item_theme_collection_desc, Themes.forSale.size)
    }
    CoreText.items[id]?.let { return UiText.res(it.second) }
    CoreText.cosmeticOfItem(id)?.let { return it.descriptionText() }
    CoreText.boatOfItem(id)?.let { return it.descriptionText() }
    CoreText.decorOfItem(id)?.let { return it.descriptionText() }
    CoreText.themeOfItem(id)?.let { return it.descriptionText() }
    return UiText.raw(description)
}
@Composable @ReadOnlyComposable fun ShopItem.description(): String = descriptionText().asString()

/** The tag on a shop card ("Set", "Special", "+20%"), or null. */
fun ShopItem.badgeText(): UiText? = when (badge) {
    null -> null
    "Set" -> UiText.res(R.string.core_shop_badge_set) // i18n-ignore: core's badge value
    "Special" -> UiText.res(R.string.core_shop_badge_special) // i18n-ignore: core's badge value
    else -> UiText.raw(badge!!)
}

fun PremiumSkin.nameText(): UiText = CoreText.boats[id]?.let { UiText.res(it.first) } ?: UiText.raw(name)
@Composable @ReadOnlyComposable fun PremiumSkin.label(): String = nameText().asString()
fun PremiumSkin.descriptionText(): UiText = CoreText.boats[id]?.let { UiText.res(it.second) } ?: UiText.raw(tagline)
@Composable @ReadOnlyComposable fun PremiumSkin.description(): String = descriptionText().asString()

/** A premade boat's name by id ("a boat" style fallbacks are the caller's). */
fun boatNameText(presetId: String): UiText? = PremiumSkins.byId(presetId)?.nameText()

/** A boat paint colour's name, by its swatch name (core's English name). */
fun swatchText(swatchName: String): UiText = CoreText.colors[swatchName]?.let { UiText.res(it) } ?: UiText.raw(swatchName)
@Composable @ReadOnlyComposable fun swatchLabel(swatchName: String): String = swatchText(swatchName).asString()
fun BoatPalette.Swatch.labelText(): UiText = swatchText(name)

fun HullPattern.labelText(): UiText = UiText.res(CoreText.pattern(this))
@Composable @ReadOnlyComposable fun HullPattern.label(): String = labelText().asString()
fun ClothPattern.labelText(): UiText = UiText.res(CoreText.cloth(this))
@Composable @ReadOnlyComposable fun ClothPattern.label(): String = labelText().asString()
fun SailStyle.labelText(): UiText = UiText.res(CoreText.sail(this))
@Composable @ReadOnlyComposable fun SailStyle.label(): String = labelText().asString()
fun FlagStyle.labelText(): UiText = UiText.res(CoreText.flag(this))
@Composable @ReadOnlyComposable fun FlagStyle.label(): String = labelText().asString()
fun Emblem.labelText(): UiText = UiText.res(CoreText.emblem(this))
@Composable @ReadOnlyComposable fun Emblem.label(): String = labelText().asString()

fun Atmosphere.labelText(): UiText = UiText.res(CoreText.atmosphere(this))
@Composable @ReadOnlyComposable fun Atmosphere.label(): String = labelText().asString()

fun TitleFrame.labelText(): UiText = UiText.res(CoreText.frame(this))
@Composable @ReadOnlyComposable fun TitleFrame.label(): String = labelText().asString()

fun CardStyle.labelText(): UiText = UiText.res(CoreText.cardStyle(this))
@Composable @ReadOnlyComposable fun CardStyle.label(): String = labelText().asString()

fun Decor.labelText(): UiText = UiText.res(CoreText.decor(this))
@Composable @ReadOnlyComposable fun Decor.label(): String = labelText().asString()
fun Decor.descriptionText(): UiText = UiText.res(CoreText.decorDesc(this))
@Composable @ReadOnlyComposable fun Decor.description(): String = descriptionText().asString()

fun MenuTheme.labelText(): UiText = UiText.res(CoreText.theme(this))
@Composable @ReadOnlyComposable fun MenuTheme.label(): String = labelText().asString()
fun MenuTheme.descriptionText(): UiText = UiText.res(CoreText.themeDesc(this))
@Composable @ReadOnlyComposable fun MenuTheme.description(): String = descriptionText().asString()

fun NightMode.labelText(): UiText = UiText.res(CoreText.nightMode(this))
@Composable @ReadOnlyComposable fun NightMode.label(): String = labelText().asString()

/** The season's name ("Coral Tides"), from core's English name. */
fun Season.nameText(): UiText = CoreText.seasons[name]?.let { UiText.res(it) } ?: UiText.raw(name)
@Composable @ReadOnlyComposable fun Season.label(): String = nameText().asString()

fun EventGoal.labelText(): UiText = UiText.res(CoreText.eventGoal(this))
@Composable @ReadOnlyComposable fun EventGoal.label(): String = labelText().asString()

fun TournamentTier.labelText(): UiText = UiText.res(CoreText.tournamentTier(this))
@Composable @ReadOnlyComposable fun TournamentTier.label(): String = labelText().asString()

fun ClubRole.labelText(): UiText = UiText.res(CoreText.clubRole(this))
@Composable @ReadOnlyComposable fun ClubRole.label(): String = labelText().asString()

fun BoardKind.labelText(): UiText = UiText.res(CoreText.board(this))
@Composable @ReadOnlyComposable fun BoardKind.label(): String = labelText().asString()
fun BoardKind.descriptionText(): UiText = UiText.res(CoreText.boardDesc(this))
@Composable @ReadOnlyComposable fun BoardKind.description(): String = descriptionText().asString()

fun BoardView.labelText(): UiText = UiText.res(CoreText.boardView(this))
@Composable @ReadOnlyComposable fun BoardView.label(): String = labelText().asString()

/** A species' name in the game's language (ids never change; names are translated). */
fun FishSpecies.nameText(): UiText = speciesNames[id]?.let { UiText.res(it) } ?: UiText.raw(name)
@Composable @ReadOnlyComposable fun FishSpecies.label(): String = nameText().asString()

/**
 * A fishing secret's title. Secrets are hidden mechanics: show a title only once the player has
 * found that secret, and never in the tour (TourContentTest checks).
 */
fun secretTitleText(secretId: String): UiText = CoreText.secretTitles[secretId]?.let { UiText.res(it) } ?: UiText.raw(secretId)
@Composable @ReadOnlyComposable fun secretTitle(secretId: String): String = secretTitleText(secretId).asString()

// ---- The mappings --------------------------------------------------------------------------------

/** Enum or id -> string resource. Use the extension functions above rather than these directly. */
object CoreText {
    @StringRes fun zone(x: DepthZone): Int = when (x) {
        DepthZone.SHALLOWS -> R.string.core_zone_shallows
        DepthZone.TWILIGHT -> R.string.core_zone_twilight
        DepthZone.MIDNIGHT -> R.string.core_zone_midnight
        DepthZone.ABYSS -> R.string.core_zone_abyss
    }

    @StringRes fun zoneShort(x: DepthZone): Int = when (x) {
        DepthZone.SHALLOWS -> R.string.core_zone_shallows_short
        DepthZone.TWILIGHT -> R.string.core_zone_twilight_short
        DepthZone.MIDNIGHT -> R.string.core_zone_midnight_short
        DepthZone.ABYSS -> R.string.core_zone_abyss_short
    }

    @StringRes fun obstacle(x: ObstacleKind): Int = when (x) {
        ObstacleKind.DRIFTWOOD -> R.string.core_obstacle_driftwood
        ObstacleKind.ROCK -> R.string.core_obstacle_rock
        ObstacleKind.JELLY -> R.string.core_obstacle_jelly
    }

    @StringRes fun rope(x: RopeStat): Int = when (x) {
        RopeStat.LENGTH -> R.string.core_rope_length
        RopeStat.STRENGTH -> R.string.core_rope_strength
        RopeStat.AGILITY -> R.string.core_rope_agility
    }

    @StringRes fun ropeShort(x: RopeStat): Int = when (x) {
        RopeStat.LENGTH -> R.string.core_rope_length_short
        RopeStat.STRENGTH -> R.string.core_rope_strength_short
        RopeStat.AGILITY -> R.string.core_rope_agility_short
    }

    @StringRes fun ropeDesc(x: RopeStat): Int = when (x) {
        RopeStat.LENGTH -> R.string.core_rope_length_desc
        RopeStat.STRENGTH -> R.string.core_rope_strength_desc
        RopeStat.AGILITY -> R.string.core_rope_agility_desc
    }

    @StringRes fun tackle(x: Tackle): Int = when (x) {
        Tackle.LANTERN -> R.string.core_tackle_lantern
        Tackle.LUCKY_CHARM -> R.string.core_tackle_lucky_charm
        Tackle.SPARKLE_LURE -> R.string.core_tackle_sparkle_lure
        Tackle.DETECTOR -> R.string.core_tackle_detector
    }

    @StringRes fun mode(x: BattleMode): Int = when (x) {
        BattleMode.BEST_OF_FIVE -> R.string.core_mode_best_of_five
        BattleMode.TIME_ATTACK -> R.string.core_mode_time_attack
        BattleMode.HEAVY_HAUL -> R.string.core_mode_heavy_haul
        BattleMode.SPECIES_HUNT -> R.string.core_mode_species_hunt
        BattleMode.DEEP_DIVE -> R.string.core_mode_deep_dive
    }

    @StringRes fun bot(x: BotDifficulty): Int = when (x) {
        BotDifficulty.EASY -> R.string.core_bot_easy
        BotDifficulty.NORMAL -> R.string.core_bot_normal
        BotDifficulty.HARD -> R.string.core_bot_hard
    }

    @StringRes fun challengeDifficulty(x: ChallengeDifficulty): Int = when (x) {
        ChallengeDifficulty.EASY -> R.string.core_challenge_easy
        ChallengeDifficulty.MEDIUM -> R.string.core_challenge_medium
        ChallengeDifficulty.HARD -> R.string.core_challenge_hard
    }

    @StringRes fun slot(x: CosmeticSlot): Int = when (x) {
        CosmeticSlot.HOOK -> R.string.core_slot_hook
        CosmeticSlot.LINE -> R.string.core_slot_line
        CosmeticSlot.BOAT -> R.string.core_slot_boat
        CosmeticSlot.TRAIL -> R.string.core_slot_trail
        CosmeticSlot.TITLE -> R.string.core_slot_title
    }

    @StringRes fun cosmetic(x: Cosmetic): Int = when (x) {
        Cosmetic.LEVIATHAN_BONE_HOOK -> R.string.core_cosmetic_leviathan_bone_hook
        Cosmetic.GIANTS_BRAID_LINE -> R.string.core_cosmetic_giants_braid_line
        Cosmetic.GIANT_SLAYER_PENNANT -> R.string.core_cosmetic_giant_slayer_pennant
        Cosmetic.TIDE_TRAIL -> R.string.core_cosmetic_tide_trail
        Cosmetic.GIANT_SLAYER_TITLE -> R.string.core_cosmetic_giant_slayer_title
        Cosmetic.PRISM_HOOK -> R.string.core_cosmetic_prism_hook
        Cosmetic.RAINBOW_LINE -> R.string.core_cosmetic_rainbow_line
        Cosmetic.PRISM_SAIL -> R.string.core_cosmetic_prism_sail
        Cosmetic.PRISM_TRAIL -> R.string.core_cosmetic_prism_trail
        Cosmetic.PERFECTIONIST_TITLE -> R.string.core_cosmetic_perfectionist_title
        Cosmetic.CROWN_HOOK -> R.string.core_cosmetic_crown_hook
        Cosmetic.GOLDEN_LINE -> R.string.core_cosmetic_golden_line
        Cosmetic.ROYAL_BOAT -> R.string.core_cosmetic_royal_boat
        Cosmetic.GOLDEN_TRAIL -> R.string.core_cosmetic_golden_trail
        Cosmetic.MASTER_ANGLER_TITLE -> R.string.core_cosmetic_master_angler_title
        Cosmetic.CORAL_HOOK -> R.string.core_cosmetic_coral_hook
        Cosmetic.NEON_HOOK -> R.string.core_cosmetic_neon_hook
        Cosmetic.OBSIDIAN_HOOK -> R.string.core_cosmetic_obsidian_hook
        Cosmetic.OCEAN_LINE -> R.string.core_cosmetic_ocean_line
        Cosmetic.NEON_LINE -> R.string.core_cosmetic_neon_line
        Cosmetic.SILVER_LINE -> R.string.core_cosmetic_silver_line
        Cosmetic.HEART_TRAIL -> R.string.core_cosmetic_heart_trail
        Cosmetic.STAR_TRAIL -> R.string.core_cosmetic_star_trail
        Cosmetic.PETAL_TRAIL -> R.string.core_cosmetic_petal_trail
        Cosmetic.FOUNDER_TITLE -> R.string.core_cosmetic_founder_title
    }

    @StringRes fun cosmeticDesc(x: Cosmetic): Int = when (x) {
        Cosmetic.LEVIATHAN_BONE_HOOK -> R.string.core_cosmetic_leviathan_bone_hook_desc
        Cosmetic.GIANTS_BRAID_LINE -> R.string.core_cosmetic_giants_braid_line_desc
        Cosmetic.GIANT_SLAYER_PENNANT -> R.string.core_cosmetic_giant_slayer_pennant_desc
        Cosmetic.TIDE_TRAIL -> R.string.core_cosmetic_tide_trail_desc
        Cosmetic.GIANT_SLAYER_TITLE -> R.string.core_cosmetic_giant_slayer_title_desc
        Cosmetic.PRISM_HOOK -> R.string.core_cosmetic_prism_hook_desc
        Cosmetic.RAINBOW_LINE -> R.string.core_cosmetic_rainbow_line_desc
        Cosmetic.PRISM_SAIL -> R.string.core_cosmetic_prism_sail_desc
        Cosmetic.PRISM_TRAIL -> R.string.core_cosmetic_prism_trail_desc
        Cosmetic.PERFECTIONIST_TITLE -> R.string.core_cosmetic_perfectionist_title_desc
        Cosmetic.CROWN_HOOK -> R.string.core_cosmetic_crown_hook_desc
        Cosmetic.GOLDEN_LINE -> R.string.core_cosmetic_golden_line_desc
        Cosmetic.ROYAL_BOAT -> R.string.core_cosmetic_royal_boat_desc
        Cosmetic.GOLDEN_TRAIL -> R.string.core_cosmetic_golden_trail_desc
        Cosmetic.MASTER_ANGLER_TITLE -> R.string.core_cosmetic_master_angler_title_desc
        Cosmetic.CORAL_HOOK -> R.string.core_cosmetic_coral_hook_desc
        Cosmetic.NEON_HOOK -> R.string.core_cosmetic_neon_hook_desc
        Cosmetic.OBSIDIAN_HOOK -> R.string.core_cosmetic_obsidian_hook_desc
        Cosmetic.OCEAN_LINE -> R.string.core_cosmetic_ocean_line_desc
        Cosmetic.NEON_LINE -> R.string.core_cosmetic_neon_line_desc
        Cosmetic.SILVER_LINE -> R.string.core_cosmetic_silver_line_desc
        Cosmetic.HEART_TRAIL -> R.string.core_cosmetic_heart_trail_desc
        Cosmetic.STAR_TRAIL -> R.string.core_cosmetic_star_trail_desc
        Cosmetic.PETAL_TRAIL -> R.string.core_cosmetic_petal_trail_desc
        Cosmetic.FOUNDER_TITLE -> R.string.core_cosmetic_founder_title_desc
    }

    @StringRes fun shopSection(x: ShopSection): Int = when (x) {
        ShopSection.TACKLE -> R.string.core_shop_section_tackle
        ShopSection.STYLE -> R.string.core_shop_section_style
        ShopSection.PASSES -> R.string.core_shop_section_passes
        ShopSection.AQUARIUM -> R.string.core_shop_section_aquarium
    }

    @StringRes fun pattern(x: HullPattern): Int = when (x) {
        HullPattern.SOLID -> R.string.core_pattern_solid
        HullPattern.STRIPE -> R.string.core_pattern_stripe
        HullPattern.TWO_TONE -> R.string.core_pattern_two_tone
        HullPattern.WAVES -> R.string.core_pattern_waves
        HullPattern.DOTS -> R.string.core_pattern_dots
        HullPattern.CHECKER -> R.string.core_pattern_checker
        HullPattern.FLAMES -> R.string.core_pattern_flames
        HullPattern.STARRY -> R.string.core_pattern_starry
        HullPattern.METALLIC -> R.string.core_pattern_metallic
        HullPattern.NEON -> R.string.core_pattern_neon
        HullPattern.AURORA -> R.string.core_pattern_aurora
    }

    @StringRes fun cloth(x: ClothPattern): Int = when (x) {
        ClothPattern.PLAIN -> R.string.core_cloth_plain
        ClothPattern.STRIPES -> R.string.core_cloth_stripes
        ClothPattern.STARS -> R.string.core_cloth_stars
        ClothPattern.WAVES -> R.string.core_cloth_waves
    }

    @StringRes fun sail(x: SailStyle): Int = when (x) {
        SailStyle.NONE -> R.string.core_sail_none
        SailStyle.TRIANGLE -> R.string.core_sail_triangle
        SailStyle.SQUARE -> R.string.core_sail_square
        SailStyle.TWIN -> R.string.core_sail_twin
    }

    @StringRes fun flag(x: FlagStyle): Int = when (x) {
        FlagStyle.NONE -> R.string.core_flag_none
        FlagStyle.PENNANT -> R.string.core_flag_pennant
        FlagStyle.SWALLOWTAIL -> R.string.core_flag_swallowtail
        FlagStyle.BANNER -> R.string.core_flag_banner
    }

    @StringRes fun emblem(x: Emblem): Int = when (x) {
        Emblem.NONE -> R.string.core_emblem_none
        Emblem.STAR -> R.string.core_emblem_star
        Emblem.ANCHOR -> R.string.core_emblem_anchor
        Emblem.FISH -> R.string.core_emblem_fish
        Emblem.HEART -> R.string.core_emblem_heart
        Emblem.MOON -> R.string.core_emblem_moon
        Emblem.WAVE -> R.string.core_emblem_wave
        Emblem.CROWN -> R.string.core_emblem_crown
        Emblem.SKULL -> R.string.core_emblem_skull
        Emblem.LIGHTNING -> R.string.core_emblem_lightning
        Emblem.SHELL -> R.string.core_emblem_shell
        Emblem.TRIDENT -> R.string.core_emblem_trident
    }

    @StringRes fun atmosphere(x: Atmosphere): Int = when (x) {
        Atmosphere.FAMILY -> R.string.core_atmosphere_family
        Atmosphere.NIGHTMARE -> R.string.core_atmosphere_nightmare
    }

    @StringRes fun frame(x: TitleFrame): Int = when (x) {
        TitleFrame.GOLD -> R.string.core_frame_gold
        TitleFrame.RAINBOW -> R.string.core_frame_rainbow
        TitleFrame.OCEAN -> R.string.core_frame_ocean
        TitleFrame.EMBER -> R.string.core_frame_ember
        TitleFrame.CLUB -> R.string.core_frame_club
        TitleFrame.CLUB_FOREVER -> R.string.core_frame_club_forever
        TitleFrame.SEASON -> R.string.core_frame_season
        TitleFrame.COLLECTOR -> R.string.core_frame_collector
        TitleFrame.EVENT -> R.string.core_frame_event
        TitleFrame.TOURNAMENT -> R.string.core_frame_tournament
    }

    @StringRes fun cardStyle(x: CardStyle): Int = when (x) {
        CardStyle.CLASSIC -> R.string.core_card_classic
        CardStyle.HOLO -> R.string.core_card_holo
        CardStyle.GOLD -> R.string.core_card_gold
        CardStyle.NIGHT -> R.string.core_card_night
        CardStyle.CORAL -> R.string.core_card_coral
    }

    @StringRes fun decor(x: Decor): Int = when (x) {
        Decor.PLANTS -> R.string.core_decor_plants
        Decor.PEBBLES -> R.string.core_decor_pebbles
        Decor.SAND_CASTLE -> R.string.core_decor_sand_castle
        Decor.BUBBLER -> R.string.core_decor_bubbler
        Decor.TREASURE_CHEST -> R.string.core_decor_treasure_chest
        Decor.CORAL_GARDEN -> R.string.core_decor_coral_garden
        Decor.LANTERN_LIGHTS -> R.string.core_decor_lantern_lights
        Decor.SUNKEN_SHIP -> R.string.core_decor_sunken_ship
    }

    @StringRes fun decorDesc(x: Decor): Int = when (x) {
        Decor.PLANTS -> R.string.core_decor_plants_desc
        Decor.PEBBLES -> R.string.core_decor_pebbles_desc
        Decor.SAND_CASTLE -> R.string.core_decor_sand_castle_desc
        Decor.BUBBLER -> R.string.core_decor_bubbler_desc
        Decor.TREASURE_CHEST -> R.string.core_decor_treasure_chest_desc
        Decor.CORAL_GARDEN -> R.string.core_decor_coral_garden_desc
        Decor.LANTERN_LIGHTS -> R.string.core_decor_lantern_lights_desc
        Decor.SUNKEN_SHIP -> R.string.core_decor_sunken_ship_desc
    }

    @StringRes fun theme(x: MenuTheme): Int = when (x) {
        MenuTheme.LAGOON -> R.string.core_theme_lagoon
        MenuTheme.KELP_FOREST -> R.string.core_theme_kelp_forest
        MenuTheme.SANDBAR -> R.string.core_theme_sandbar
        MenuTheme.CORAL_REEF -> R.string.core_theme_coral_reef
        MenuTheme.POLAR -> R.string.core_theme_polar
        MenuTheme.SUNSET_HARBOR -> R.string.core_theme_sunset_harbor
        MenuTheme.KOI_POND -> R.string.core_theme_koi_pond
        MenuTheme.TREASURE_COVE -> R.string.core_theme_treasure_cove
        MenuTheme.GLOW_TIDE -> R.string.core_theme_glow_tide
        MenuTheme.AURORA -> R.string.core_theme_aurora
    }

    @StringRes fun themeDesc(x: MenuTheme): Int = when (x) {
        MenuTheme.LAGOON -> R.string.core_theme_lagoon_desc
        MenuTheme.KELP_FOREST -> R.string.core_theme_kelp_forest_desc
        MenuTheme.SANDBAR -> R.string.core_theme_sandbar_desc
        MenuTheme.CORAL_REEF -> R.string.core_theme_coral_reef_desc
        MenuTheme.POLAR -> R.string.core_theme_polar_desc
        MenuTheme.SUNSET_HARBOR -> R.string.core_theme_sunset_harbor_desc
        MenuTheme.KOI_POND -> R.string.core_theme_koi_pond_desc
        MenuTheme.TREASURE_COVE -> R.string.core_theme_treasure_cove_desc
        MenuTheme.GLOW_TIDE -> R.string.core_theme_glow_tide_desc
        MenuTheme.AURORA -> R.string.core_theme_aurora_desc
    }

    @StringRes fun nightMode(x: NightMode): Int = when (x) {
        NightMode.DAY -> R.string.core_night_day
        NightMode.NIGHT -> R.string.core_night_night
        NightMode.AUTO -> R.string.core_night_auto
    }

    @StringRes fun eventGoal(x: EventGoal): Int = when (x) {
        EventGoal.SPARKLING -> R.string.core_goal_sparkling
        EventGoal.FISH -> R.string.core_goal_fish
        EventGoal.DEEP -> R.string.core_goal_deep
        EventGoal.SPECIES -> R.string.core_goal_species
    }

    @StringRes fun tournamentTier(x: TournamentTier): Int = when (x) {
        TournamentTier.CHAMPION -> R.string.core_tier_champion
        TournamentTier.GOLD -> R.string.core_tier_gold
        TournamentTier.SILVER -> R.string.core_tier_silver
        TournamentTier.BRONZE -> R.string.core_tier_bronze
        TournamentTier.TOOK_PART -> R.string.core_tier_took_part
    }

    @StringRes fun clubRole(x: ClubRole): Int = when (x) {
        ClubRole.OWNER -> R.string.core_role_owner
        ClubRole.MEMBER -> R.string.core_role_member
    }

    @StringRes fun board(x: BoardKind): Int = when (x) {
        BoardKind.BEST_CAST -> R.string.core_board_best_cast
        BoardKind.DEEPEST -> R.string.core_board_deepest
        BoardKind.RAREST -> R.string.core_board_rarest
    }

    @StringRes fun boardDesc(x: BoardKind): Int = when (x) {
        BoardKind.BEST_CAST -> R.string.core_board_best_cast_desc
        BoardKind.DEEPEST -> R.string.core_board_deepest_desc
        BoardKind.RAREST -> R.string.core_board_rarest_desc
    }

    @StringRes fun boardView(x: BoardView): Int = when (x) {
        BoardView.GLOBAL -> R.string.core_view_global
        BoardView.LEAGUE -> R.string.core_view_league
        BoardView.FRIENDS -> R.string.core_view_friends
    }

    internal val colors: Map<String, Int> = mapOf(
        "Cream" to R.string.core_color_cream,
        "Sand" to R.string.core_color_sand,
        "Driftwood" to R.string.core_color_driftwood,
        "Walnut" to R.string.core_color_walnut,
        "Pearl" to R.string.core_color_pearl,
        "Slate" to R.string.core_color_slate,
        "Charcoal" to R.string.core_color_charcoal,
        "Lagoon" to R.string.core_color_lagoon,
        "Teal" to R.string.core_color_teal,
        "Ocean" to R.string.core_color_ocean,
        "Navy" to R.string.core_color_navy,
        "Seafoam" to R.string.core_color_seafoam,
        "Coral" to R.string.core_color_coral,
        "Tangerine" to R.string.core_color_tangerine,
        "Sunflower" to R.string.core_color_sunflower,
        "Rose" to R.string.core_color_rose,
        "Berry" to R.string.core_color_berry,
        "Crimson" to R.string.core_color_crimson,
        "Lilac" to R.string.core_color_lilac,
        "Violet" to R.string.core_color_violet,
        "Mint" to R.string.core_color_mint,
        "Lime" to R.string.core_color_lime,
        "Gold" to R.string.core_color_gold,
        "Midnight" to R.string.core_color_midnight,
    )
    internal val boats: Map<String, Pair<Int, Int>> = mapOf(
        "coral_cruiser" to (R.string.core_boat_coral_cruiser to R.string.core_boat_coral_cruiser_desc),
        "lagoon_explorer" to (R.string.core_boat_lagoon_explorer to R.string.core_boat_lagoon_explorer_desc),
        "sunset_racer" to (R.string.core_boat_sunset_racer to R.string.core_boat_sunset_racer_desc),
        "midnight_corsair" to (R.string.core_boat_midnight_corsair to R.string.core_boat_midnight_corsair_desc),
        "starlight_voyager" to (R.string.core_boat_starlight_voyager to R.string.core_boat_starlight_voyager_desc),
        "royal_regatta" to (R.string.core_boat_royal_regatta to R.string.core_boat_royal_regatta_desc),
        "galaxy_dreamer" to (R.string.core_boat_galaxy_dreamer to R.string.core_boat_galaxy_dreamer_desc),
        "neon_nights" to (R.string.core_boat_neon_nights to R.string.core_boat_neon_nights_desc),
        "founders_flagship" to (R.string.core_boat_founders_flagship to R.string.core_boat_founders_flagship_desc),
        "season_tidecaller" to (R.string.core_boat_season_tidecaller to R.string.core_boat_season_tidecaller_desc),
        "season_moonbloom" to (R.string.core_boat_season_moonbloom to R.string.core_boat_season_moonbloom_desc),
        "season_frostfin" to (R.string.core_boat_season_frostfin to R.string.core_boat_season_frostfin_desc),
        "season_goldwake" to (R.string.core_boat_season_goldwake to R.string.core_boat_season_goldwake_desc),
    )
    internal val items: Map<String, Pair<Int, Int>> = mapOf(
        "lantern_pouch" to (R.string.core_shop_item_lantern_pouch to R.string.core_shop_item_lantern_pouch_desc),
        "lantern_crate" to (R.string.core_shop_item_lantern_crate to R.string.core_shop_item_lantern_crate_desc),
        "lantern_chest" to (R.string.core_shop_item_lantern_chest to R.string.core_shop_item_lantern_chest_desc),
        "lantern_barrel" to (R.string.core_shop_item_lantern_barrel to R.string.core_shop_item_lantern_barrel_desc),
        "detector_pouch" to (R.string.core_shop_item_detector_pouch to R.string.core_shop_item_detector_pouch_desc),
        "detector_crate" to (R.string.core_shop_item_detector_crate to R.string.core_shop_item_detector_crate_desc),
        "charm_bag" to (R.string.core_shop_item_charm_bag to R.string.core_shop_item_charm_bag_desc),
        "lure_box" to (R.string.core_shop_item_lure_box to R.string.core_shop_item_lure_box_desc),
        "paint_flames" to (R.string.core_shop_item_paint_flames to R.string.core_shop_item_paint_flames_desc),
        "paint_starry" to (R.string.core_shop_item_paint_starry to R.string.core_shop_item_paint_starry_desc),
        "paint_metallic" to (R.string.core_shop_item_paint_metallic to R.string.core_shop_item_paint_metallic_desc),
        "paint_neon" to (R.string.core_shop_item_paint_neon to R.string.core_shop_item_paint_neon_desc),
        "emblem_crown" to (R.string.core_shop_item_emblem_crown to R.string.core_shop_item_emblem_crown_desc),
        "emblem_skull" to (R.string.core_shop_item_emblem_skull to R.string.core_shop_item_emblem_skull_desc),
        "emblem_lightning" to (R.string.core_shop_item_emblem_lightning to R.string.core_shop_item_emblem_lightning_desc),
        "emblem_shell" to (R.string.core_shop_item_emblem_shell to R.string.core_shop_item_emblem_shell_desc),
        "emblem_trident" to (R.string.core_shop_item_emblem_trident to R.string.core_shop_item_emblem_trident_desc),
        "cloth_stripes" to (R.string.core_shop_item_cloth_stripes to R.string.core_shop_item_cloth_stripes_desc),
        "cloth_stars" to (R.string.core_shop_item_cloth_stars to R.string.core_shop_item_cloth_stars_desc),
        "cloth_waves" to (R.string.core_shop_item_cloth_waves to R.string.core_shop_item_cloth_waves_desc),
        "set_coral_reef" to (R.string.core_shop_item_set_coral_reef to R.string.core_shop_item_set_coral_reef_desc),
        "set_neon_nights" to (R.string.core_shop_item_set_neon_nights to R.string.core_shop_item_set_neon_nights_desc),
        "starter_pack" to (R.string.core_shop_item_starter_pack to R.string.core_shop_item_starter_pack_desc),
        "captains_club" to (R.string.core_shop_item_captains_club to R.string.core_shop_item_captains_club_desc),
        "founders_pack" to (R.string.core_shop_item_founders_pack to R.string.core_shop_item_founders_pack_desc),
        "season_pass" to (R.string.core_shop_item_season_pass to R.string.core_shop_item_season_pass_desc),
        "collector_edition" to (R.string.core_shop_item_collector_edition to R.string.core_shop_item_collector_edition_desc),
        "event_pass" to (R.string.core_shop_item_event_pass to R.string.core_shop_item_event_pass_desc),
        "badge_rainbow" to (R.string.core_shop_item_badge_rainbow to R.string.core_shop_item_badge_rainbow_desc),
        "badge_ocean" to (R.string.core_shop_item_badge_ocean to R.string.core_shop_item_badge_ocean_desc),
        "badge_ember" to (R.string.core_shop_item_badge_ember to R.string.core_shop_item_badge_ember_desc),
        "card_holo" to (R.string.core_shop_item_card_holo to R.string.core_shop_item_card_holo_desc),
        "card_gold" to (R.string.core_shop_item_card_gold to R.string.core_shop_item_card_gold_desc),
        "card_night" to (R.string.core_shop_item_card_night to R.string.core_shop_item_card_night_desc),
        "card_coral" to (R.string.core_shop_item_card_coral to R.string.core_shop_item_card_coral_desc),
        "aquarium_expansion" to (R.string.core_shop_item_aquarium_expansion to R.string.core_shop_item_aquarium_expansion_desc),
        "theme_collection" to (R.string.core_shop_item_theme_collection to R.string.core_shop_item_theme_collection_desc),
    )

    internal val sets: Map<String, Int> = mapOf(
        "Giant-slayer" to R.string.core_set_giant_slayer,
        "Prismatic" to R.string.core_set_prismatic,
        "Sovereign" to R.string.core_set_sovereign,
        "Coral Reef" to R.string.core_set_coral_reef,
        "Neon Nights" to R.string.core_set_neon_nights,
    )

    internal val seasons: Map<String, Int> = mapOf(
        "Coral Tides" to R.string.core_season_coral_tides,
        "Midnight Bloom" to R.string.core_season_midnight_bloom,
        "Frost Current" to R.string.core_season_frost_current,
        "Sunken Gold" to R.string.core_season_sunken_gold,
        "Storm Season" to R.string.core_season_storm_season,
        "Lantern Festival" to R.string.core_season_lantern_festival,
    )

    internal val secretTitles: Map<String, Int> = mapOf(
        Secrets.LAST_BUMP to R.string.core_secret_last_bump,
        Secrets.FREE_PASS to R.string.core_secret_free_pass,
        Secrets.SCHOOLING to R.string.core_secret_schooling,
        Secrets.SURFACE_SKIM to R.string.core_secret_surface_skim,
        Secrets.ZEN_CAST to R.string.core_secret_zen_cast,
        Secrets.PINBALL to R.string.core_secret_pinball,
        Secrets.SECOND_ENCOUNTER to R.string.core_secret_second_encounter,
        Secrets.CLEAN_DROP to R.string.core_secret_clean_drop,
        Secrets.ROCK_BOTTOM to R.string.core_secret_rock_bottom,
        Secrets.BUZZER_BEATER to R.string.core_secret_buzzer_beater,
        Secrets.CORNER_POCKET to R.string.core_secret_corner_pocket,
        Secrets.GRAND_TOUR to R.string.core_secret_grand_tour,
        Secrets.SPARKLE_SNAG to R.string.core_secret_sparkle_snag,
        Secrets.LADDER to R.string.core_secret_ladder,
        Secrets.PERFECT_LOAD to R.string.core_secret_perfect_load,
        Secrets.MOONLIT_ANGLER to R.string.core_secret_moonlit_angler,
        Secrets.SHUTOUT to R.string.core_secret_shutout,
        Secrets.WHILE_IT_FLEES to R.string.core_secret_while_it_flees,
        Secrets.BLIND_DROP to R.string.core_secret_blind_drop,
        Secrets.ZONE_SWEEP to R.string.core_secret_zone_sweep,
        Secrets.GRAND_SLAM to R.string.core_secret_grand_slam,
        Secrets.DOUBLE_HEADER to R.string.core_secret_double_header,
        Secrets.CROWD_SURFER to R.string.core_secret_crowd_surfer,
        Secrets.DAVID_AND_GOLIATH to R.string.core_secret_david_and_goliath,
        Secrets.PERFECT_CAST to R.string.core_secret_perfect_cast,
        Secrets.MASTER_ANGLER to R.string.core_secret_master_angler,
    )

    /** Shop items that are a skin, a boat, a decor or a theme ("gear_coral_hook", "boat_neon_nights"...). */
    internal fun cosmeticOfItem(id: String): Cosmetic? =
        if (id.startsWith("gear_")) Cosmetic.entries.firstOrNull { "gear_" + it.name.lowercase() == id } else null

    internal fun boatOfItem(id: String): PremiumSkin? = if (id.startsWith("boat_")) PremiumSkins.byId(id.removePrefix("boat_")) else null

    internal fun decorOfItem(id: String): Decor? =
        if (id.startsWith("decor_")) Decor.entries.firstOrNull { "decor_" + it.name.lowercase() == id } else null

    internal fun themeOfItem(id: String): MenuTheme? =
        if (id.startsWith("theme_")) MenuTheme.entries.firstOrNull { "theme_" + it.name.lowercase() == id } else null
}
