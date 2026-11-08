package com.deepline.fishing.ui.tour

import kotlin.math.roundToInt

import com.deepline.fishing.R
import com.deepline.fishing.Screen
import com.deepline.fishing.core.BotDifficulty
import com.deepline.fishing.core.DepthZone
import com.deepline.fishing.core.FishCatalog
import com.deepline.fishing.core.GameConfig
import com.deepline.fishing.core.online.OnlineProtocol
import com.deepline.fishing.ui.hourText
import com.deepline.fishing.ui.i18n.UiText

/** A UI element a tour step can point at (see `Modifier.tourTarget`). */
enum class TourTarget {
    HOME_CAST, HOME_POINTS, HOME_LANTERN, HOME_MILESTONES, HOME_SETTINGS, HOME_BOAT,
    NAV_BAR, TAB_DEX, TAB_SHOP, TAB_BATTLE,
    SHOP_ROPE, DEX_HEADER, DEX_FILTERS, MILESTONES_FIRST, BATTLE_MODES, BATTLE_OPPONENT,
    SKINS_CUSTOMISE, SKINS_ATMOSPHERE, SHOP_STYLE,
    SHOP_TACKLE, BATTLE_WAGER,
    SHOP_PRICES, SHOP_PASSES, BATTLE_TACKLE, SHOP_SEASON,
    DEX_AQUARIUM, SHOP_AQUARIUM, SKINS_BADGE,
    HOME_THEMES, THEMES_MODE, SHOP_THEMES,
    SETTINGS_TOUR,
    // -- Game feel (sound, haptics) targets below.
    SETTINGS_SOUND,

    // -- Daily loop targets below.
    HOME_DAILY, DAILY_CALENDAR, DAILY_CHALLENGES, DAILY_MIGRATION, SETTINGS_NOTIFICATIONS,

    // -- Social targets below.
    BATTLE_SOCIAL, SOCIAL_ID, FRIENDS_ADD, BOARD_KINDS, TOURNAMENT_ENTER, CLUBS_JOIN,

    // -- Privacy targets below.
    SETTINGS_PRIVACY,

    // -- Language targets below.
    SETTINGS_LANGUAGE,
}

/** A small illustration shown in a tour card. */
enum class TourArt { STEER, BUMPS, HEADS, WEIGHT, SPARKLING, DARKNESS }

/**
 * One step of the tour. [screen] is shown behind the card and [target], if any, is highlighted.
 * [title] and [body] are string resources (strings_tour.xml) with the numbers they quote from
 * [GameConfig] as arguments, resolved in the language shown when the card is drawn.
 * [sinceVersion] is the [Tour.VERSION] that introduced the step: players who already took an
 * older tour are shown just the newer steps as "What's new".
 */
data class TourStep(
    val id: String,
    val title: UiText,
    val body: UiText,
    val screen: Screen,
    val target: TourTarget? = null,
    val art: TourArt? = null,
    val sinceVersion: Int = 1,
    /** For a Dex step: which section to show (Species when null). */
    val dexSection: com.deepline.fishing.DexSection? = null,
    /** For a Shop step: which page to show (Rope when null). */
    val shopPage: com.deepline.fishing.ShopPage? = null,
)

/** A step's title and body from strings_tour.xml (`tour_<id>_title`, `tour_<id>_body`). */
internal fun text(id: Int, vararg args: Any): UiText = UiText.res(id, *args)

/**
 * The guided tour of the app.
 *
 * KEEP THIS UP TO DATE: every new player-facing feature gets a step (or an updated step) here.
 * When adding steps, bump [VERSION] and give the new steps `sinceVersion = VERSION`, so returning
 * players get a short "What's new" tour of just those steps. `TourContentTest` checks that every
 * screen, battle mode and rope upgrade is covered. Write the text in strings_tour.xml, with the
 * numbers from [GameConfig] passed as arguments (never typed into the text).
 */
object Tour {
    /**
     * 2: head-only bumps, smoother steering with an aim marker, fishing secrets.
     * 3: more secrets with bonuses that also count in battles, and skins.
     * 4: rope downgrades with a full refund, many more species, bolder skins and titles.
     * 5: bottom tabs (Fish, Dex, Upgrades, Battle) and the Dex with seen and caught fish.
     * 6: boat customisation, the premium skins shop (coming soon) and the Atmosphere option.
     * 7: the fast reel for a full line, a deeper bump-free zone and a brighter Lantern.
     * 8: the fast reel right after the last bump, a fairer deep, and new ways to spend points.
     * 9: the Shop tab (two prices for everything), the rare fish detector, sight tackle in
     *    battles, and passes.
     * 10: themes for the menus (a sea scene behind them) and a night mode.
     * 11: Settings (the gear), sound and haptics, daily rewards and challenges, friends,
     *     leaderboards, tournaments and clubs, privacy choices and languages.
     */
    const val VERSION = 11

    private val bumps = GameConfig.DESCENT_TOUCH_LIMIT
    private val calendarDays get() = UiText.plural(R.plurals.common_days, com.deepline.fishing.core.DailyRewards.calendar.size)

    /** Every step, also those of features switched off (see [steps]). */
    private val allSteps: List<TourStep> = listOf(
        TourStep(
            id = "welcome",
            title = text(R.string.tour_welcome_title),
            body = text(R.string.tour_welcome_body),
            screen = Screen.HOME,
        ),
        TourStep(
            id = "tabs",
            title = text(R.string.tour_tabs_title),
            body = text(R.string.tour_tabs_body),
            screen = Screen.HOME,
            target = TourTarget.NAV_BAR,
            sinceVersion = 9,
        ),
        TourStep(
            id = "cast",
            title = text(R.string.tour_cast_title),
            body = text(R.string.tour_cast_body),
            screen = Screen.HOME,
            target = TourTarget.HOME_CAST,
            sinceVersion = 5,
        ),
        TourStep(
            id = "steer",
            title = text(R.string.tour_steer_title),
            body = text(R.string.tour_steer_body),
            screen = Screen.HOME,
            art = TourArt.STEER,
            sinceVersion = 2,
        ),
        TourStep(
            id = "descent",
            title = text(R.string.tour_descent_title),
            body = text(R.string.tour_descent_body, bumps, GameConfig.BUMP_FREE_DEPTH.toInt()),
            screen = Screen.HOME,
            art = TourArt.BUMPS,
            sinceVersion = 7,
        ),
        TourStep(
            id = "ascent",
            title = text(R.string.tour_ascent_title),
            body = text(R.string.tour_ascent_body),
            screen = Screen.HOME,
            art = TourArt.HEADS,
        ),
        TourStep(
            id = "weight",
            title = text(R.string.tour_weight_title),
            body = text(R.string.tour_weight_body),
            screen = Screen.HOME,
            art = TourArt.WEIGHT,
        ),
        TourStep(
            id = "fast_reel",
            title = text(R.string.tour_fast_reel_title),
            body = text(R.string.tour_fast_reel_body, bumps, UiText.decimal(GameConfig.FAST_REEL_MAX_SECONDS)),
            screen = Screen.HOME,
            art = TourArt.WEIGHT,
            sinceVersion = 8,
        ),
        TourStep(
            id = "themes",
            title = text(R.string.tour_themes_title),
            body = text(R.string.tour_themes_body),
            screen = Screen.HOME,
            target = TourTarget.HOME_THEMES,
            sinceVersion = 10,
        ),
        TourStep(
            id = "night_mode",
            title = text(R.string.tour_night_mode_title),
            body = text(R.string.tour_night_mode_body),
            screen = Screen.THEMES,
            target = TourTarget.THEMES_MODE,
            sinceVersion = 10,
        ),
        TourStep(
            id = "points",
            title = text(R.string.tour_points_title),
            body = text(R.string.tour_points_body, GameConfig.SPARKLING_MULTIPLIER),
            screen = Screen.HOME,
            target = TourTarget.HOME_POINTS,
            art = TourArt.SPARKLING,
            sinceVersion = 5,
        ),
        TourStep(
            id = "upgrades",
            title = text(R.string.tour_upgrades_title),
            body = text(R.string.tour_upgrades_body),
            screen = Screen.SHOP,
            target = TourTarget.SHOP_ROPE,
            sinceVersion = 9,
            shopPage = com.deepline.fishing.ShopPage.ROPE,
        ),
        TourStep(
            id = "tackle",
            title = text(R.string.tour_tackle_title),
            body = text(R.string.tour_tackle_body, GameConfig.DETECTOR_RADIUS_M.toInt(), GameConfig.SPARKLE_LURE_MULTIPLIER.toInt()),
            screen = Screen.SHOP,
            target = TourTarget.SHOP_TACKLE,
            sinceVersion = 9,
            shopPage = com.deepline.fishing.ShopPage.TACKLE,
        ),
        TourStep(
            id = "two_prices",
            title = text(R.string.tour_two_prices_title),
            body = text(
                R.string.tour_two_prices_body,
                GameConfig.MINUTES_PER_EURO.toInt(),
                UiText.plural(R.plurals.common_hours, (GameConfig.LANTERN_MINUTES_PER_EURO / 60f).toInt()),
            ),
            screen = Screen.SHOP,
            target = TourTarget.SHOP_PRICES,
            sinceVersion = 9,
            shopPage = com.deepline.fishing.ShopPage.TACKLE,
        ),
        TourStep(
            id = "lantern",
            title = text(R.string.tour_lantern_title),
            body = text(
                R.string.tour_lantern_body,
                GameConfig.DARK_DEPTH.toInt(),
                GameConfig.VISIBLE_RADIUS_LANTERN.roundToInt(),
                GameConfig.VISIBLE_RADIUS_DARK.roundToInt(),
            ),
            screen = Screen.HOME,
            target = TourTarget.HOME_LANTERN,
            art = TourArt.DARKNESS,
            sinceVersion = 7,
        ),
        TourStep(
            id = "dex",
            title = text(R.string.tour_dex_title),
            body = text(R.string.tour_dex_body, FishCatalog.all.size, DepthZone.entries.size),
            screen = Screen.DEX,
            target = TourTarget.DEX_HEADER,
            sinceVersion = 5,
        ),
        TourStep(
            id = "dex_filters",
            title = text(R.string.tour_dex_filters_title),
            body = text(R.string.tour_dex_filters_body),
            screen = Screen.DEX,
            target = TourTarget.DEX_FILTERS,
            sinceVersion = 5,
        ),
        TourStep(
            id = "milestones",
            title = text(R.string.tour_milestones_title),
            body = text(R.string.tour_milestones_body),
            screen = Screen.MILESTONES,
            target = TourTarget.MILESTONES_FIRST,
            sinceVersion = 5,
        ),
        TourStep(
            id = "secrets",
            title = text(R.string.tour_secrets_title),
            body = text(R.string.tour_secrets_body),
            screen = Screen.DEX,
            target = TourTarget.TAB_DEX,
            sinceVersion = 5,
        ),
        TourStep(
            id = "boat",
            title = text(R.string.tour_boat_title),
            body = text(R.string.tour_boat_body),
            screen = Screen.HOME,
            target = TourTarget.HOME_BOAT,
            sinceVersion = 6,
        ),
        TourStep(
            id = "shop_style",
            title = text(R.string.tour_shop_style_title),
            body = text(R.string.tour_shop_style_body),
            screen = Screen.SHOP,
            target = TourTarget.SHOP_STYLE,
            sinceVersion = 9,
            shopPage = com.deepline.fishing.ShopPage.STYLE,
        ),
        TourStep(
            id = "aquarium",
            title = text(R.string.tour_aquarium_title),
            body = text(R.string.tour_aquarium_body),
            screen = Screen.SHOP,
            target = TourTarget.SHOP_AQUARIUM,
            sinceVersion = 9,
            shopPage = com.deepline.fishing.ShopPage.AQUARIUM,
        ),
        TourStep(
            id = "season",
            title = text(R.string.tour_season_title),
            body = text(R.string.tour_season_body, UiText.plural(R.plurals.common_weeks, GameConfig.SEASON_WEEKS)),
            screen = Screen.SHOP,
            target = TourTarget.SHOP_SEASON,
            sinceVersion = 9,
            shopPage = com.deepline.fishing.ShopPage.PASSES,
        ),
        TourStep(
            id = "passes",
            title = text(R.string.tour_passes_title),
            body = text(R.string.tour_passes_body),
            screen = Screen.SHOP,
            target = TourTarget.SHOP_PASSES,
            sinceVersion = 9,
            shopPage = com.deepline.fishing.ShopPage.PASSES,
        ),
        TourStep(
            id = "badges",
            title = text(R.string.tour_badges_title),
            body = text(R.string.tour_badges_body),
            screen = Screen.DEX,
            target = TourTarget.SKINS_BADGE,
            sinceVersion = 9,
            dexSection = com.deepline.fishing.DexSection.SKINS,
        ),
        TourStep(
            id = "atmosphere",
            title = text(R.string.tour_atmosphere_title),
            body = text(R.string.tour_atmosphere_body),
            screen = Screen.DEX,
            target = TourTarget.SKINS_ATMOSPHERE,
            sinceVersion = 6,
            dexSection = com.deepline.fishing.DexSection.SKINS,
        ),
        TourStep(
            id = "battle",
            title = text(R.string.tour_battle_title),
            body = text(R.string.tour_battle_body),
            screen = Screen.BATTLE_SETUP,
            target = TourTarget.BATTLE_MODES,
        ),
        TourStep(
            id = "opponents",
            title = text(R.string.tour_opponents_title),
            body = text(R.string.tour_opponents_body, OnlineProtocol.TOURNAMENT_ROPE_LEVEL),
            screen = Screen.BATTLE_SETUP,
            target = TourTarget.BATTLE_OPPONENT,
        ),
        TourStep(
            id = "battle_tackle",
            title = text(R.string.tour_battle_tackle_title),
            body = text(R.string.tour_battle_tackle_body),
            screen = Screen.BATTLE_SETUP,
            target = TourTarget.BATTLE_TACKLE,
            sinceVersion = 9,
        ),
        TourStep(
            id = "gifts",
            title = text(R.string.tour_gifts_title),
            body = text(R.string.tour_gifts_body),
            screen = Screen.BATTLE_SETUP,
            sinceVersion = 9,
        ),
        TourStep(
            id = "wager",
            title = text(R.string.tour_wager_title),
            body = text(
                R.string.tour_wager_body,
                UiText.decimal(GameConfig.wagerPayout(BotDifficulty.EASY)),
                UiText.decimal(GameConfig.wagerPayout(BotDifficulty.NORMAL)),
                UiText.decimal(GameConfig.wagerPayout(BotDifficulty.HARD)),
            ),
            screen = Screen.BATTLE_SETUP,
            target = TourTarget.BATTLE_WAGER,
            sinceVersion = 8,
        ),
        TourStep(
            id = "settings",
            title = text(R.string.tour_settings_title),
            body = text(R.string.tour_settings_body),
            screen = Screen.HOME,
            target = TourTarget.HOME_SETTINGS,
            sinceVersion = VERSION,
        ),
        // -- Game feel (sound, haptics) steps below.
        TourStep(
            id = "sound",
            title = text(R.string.tour_sound_title),
            body = text(R.string.tour_sound_body),
            screen = Screen.SETTINGS,
            target = TourTarget.SETTINGS_SOUND,
            sinceVersion = VERSION,
        ),

        // -- Daily loop steps below.
        TourStep(
            id = "daily",
            title = text(R.string.tour_daily_title),
            body = text(R.string.tour_daily_body, calendarDays, GameConfig.STARTING_STREAK_SHIELDS, GameConfig.MAX_STREAK_SHIELDS),
            screen = Screen.HOME,
            target = TourTarget.HOME_DAILY,
            sinceVersion = VERSION,
        ),
        TourStep(
            id = "daily_challenges",
            title = text(R.string.tour_daily_challenges_title),
            body = text(R.string.tour_daily_challenges_body, UiText.plural(R.plurals.tour_daily_swaps, GameConfig.DAILY_FREE_REROLLS)),
            screen = Screen.DAILY,
            target = TourTarget.DAILY_CHALLENGES,
            sinceVersion = VERSION,
        ),
        TourStep(
            id = "migrations",
            title = text(R.string.tour_migrations_title),
            body = text(R.string.tour_migrations_body, UiText.plural(R.plurals.common_days, GameConfig.MIGRATION_HOURS / 24)),
            screen = Screen.DAILY,
            target = TourTarget.DAILY_MIGRATION,
            sinceVersion = VERSION,
        ),
        TourStep(
            id = "reminders",
            title = text(R.string.tour_reminders_title),
            body = text(R.string.tour_reminders_body, hourText(GameConfig.NOTIFY_QUIET_START_HOUR), hourText(GameConfig.NOTIFY_QUIET_END_HOUR)),
            screen = Screen.SETTINGS,
            target = TourTarget.SETTINGS_NOTIFICATIONS,
            sinceVersion = VERSION,
        ),

        // -- Social steps below.
        *SocialTour.steps(VERSION).toTypedArray(),

        // -- Privacy steps below.
        TourStep(
            id = "privacy",
            title = text(R.string.tour_privacy_title),
            body = text(R.string.tour_privacy_body),
            screen = Screen.SETTINGS,
            target = TourTarget.SETTINGS_PRIVACY,
            sinceVersion = VERSION,
        ),

        // -- Language steps below.
        TourStep(
            id = "language",
            title = text(R.string.tour_language_title),
            body = text(R.string.tour_language_body),
            screen = Screen.SETTINGS,
            target = TourTarget.SETTINGS_LANGUAGE,
            sinceVersion = VERSION,
        ),

        TourStep(
            id = "replay",
            title = text(R.string.tour_replay_title),
            body = text(R.string.tour_replay_body),
            screen = Screen.SETTINGS,
            target = TourTarget.SETTINGS_TOUR,
            sinceVersion = VERSION,
        ),
    )

    /** The tour: every step whose feature is switched on (club steps only with `FeatureFlags.clubs`). */
    val steps: List<TourStep> get() = allSteps.filter(SocialTour::isShown)

    data class Plan(val steps: List<TourStep>, val whatsNew: Boolean)

    /**
     * What to show when the app opens: the full tour if the player asked to see it again (or never
     * answered, i.e. a first launch), otherwise just the steps added since the tour they last saw.
     */
    fun planAtLaunch(showAgain: Boolean, seenVersion: Int): Plan? = when {
        showAgain -> Plan(steps, whatsNew = false)
        seenVersion < VERSION -> steps.filter { it.sinceVersion > seenVersion }
            .takeIf { it.isNotEmpty() }
            ?.let { Plan(it, whatsNew = true) }
        else -> null
    }
}

/** A tour in progress. */
data class TourSession(val steps: List<TourStep>, val index: Int = 0, val whatsNew: Boolean = false) {
    val current: TourStep get() = steps[index]
    val isFirst: Boolean get() = index == 0
    val isLast: Boolean get() = index == steps.lastIndex
}
