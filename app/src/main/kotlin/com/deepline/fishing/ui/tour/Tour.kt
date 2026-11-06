package com.deepline.fishing.ui.tour

import kotlin.math.roundToInt

import com.deepline.fishing.Screen
import com.deepline.fishing.core.Atmosphere
import com.deepline.fishing.core.CosmeticSlot
import com.deepline.fishing.core.DepthZone
import com.deepline.fishing.core.FishCatalog
import com.deepline.fishing.core.GameConfig
import com.deepline.fishing.core.Tackle
import com.deepline.fishing.core.online.OnlineProtocol

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

    // -- Social targets below.

    // -- Privacy targets below.

    // -- Language targets below.

}

/** A small illustration shown in a tour card. */
enum class TourArt { STEER, BUMPS, HEADS, WEIGHT, SPARKLING, DARKNESS }

/**
 * One step of the tour. [screen] is shown behind the card and [target], if any, is highlighted.
 * [sinceVersion] is the [Tour.VERSION] that introduced the step: players who already took an
 * older tour are shown just the newer steps as "What's new".
 */
data class TourStep(
    val id: String,
    val title: String,
    val body: String,
    val screen: Screen,
    val target: TourTarget? = null,
    val art: TourArt? = null,
    val sinceVersion: Int = 1,
    /** For a Dex step: which section to show (Species when null). */
    val dexSection: com.deepline.fishing.DexSection? = null,
    /** For a Shop step: which page to show (Rope when null). */
    val shopPage: com.deepline.fishing.ShopPage? = null,
)

/**
 * The guided tour of the app.
 *
 * KEEP THIS UP TO DATE: every new player-facing feature gets a step (or an updated step) here.
 * When adding steps, bump [VERSION] and give the new steps `sinceVersion = VERSION`, so returning
 * players get a short "What's new" tour of just those steps. `TourContentTest` checks that every
 * screen, battle mode and rope upgrade is covered.
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
    private val bumpFree = GameConfig.BUMP_FREE_DEPTH.toInt()
    private val dark = GameConfig.DARK_DEPTH.toInt()
    private val skinSlots = (CosmeticSlot.entries - CosmeticSlot.TITLE).map { it.displayName.lowercase() }
        .let { it.dropLast(1).joinToString(", ") + " and " + it.last() }

    val steps: List<TourStep> = listOf(
        TourStep(
            id = "welcome",
            title = "Welcome to Deep Line!",
            body = "Catch every fish in the deep. This short tour shows how to play and what everything does. " +
                "Tap Next, or Skip tour if you already know your way around.",
            screen = Screen.HOME,
        ),
        TourStep(
            id = "tabs",
            title = "Four tabs",
            body = "The bar at the bottom takes you everywhere: Fish is where you cast, the Dex collects every " +
                "species you meet, the Shop has rope upgrades, tackle and looks for your boat, and Battle is " +
                "for 1v1 matches.",
            screen = Screen.HOME,
            target = TourTarget.NAV_BAR,
            sinceVersion = 9,
        ),
        TourStep(
            id = "cast",
            title = "Cast your line",
            body = "The big Cast line button at the bottom of the Fish tab drops your hook from the boat.",
            screen = Screen.HOME,
            target = TourTarget.HOME_CAST,
            sinceVersion = 5,
        ),
        TourStep(
            id = "steer",
            title = "Steer the hook",
            body = "During a cast, drag left and right anywhere on the screen. A ring shows where your finger " +
                "is: the hook glides over to it and follows it. Rope Agility makes it faster.",
            screen = Screen.HOME,
            art = TourArt.STEER,
            sinceVersion = 2,
        ),
        TourStep(
            id = "descent",
            title = "Going down: dodge",
            body = "The hook sinks until it has bumped $bumps times or reaches the end of the rope, then it turns " +
                "back up. On a fish, only the head counts as a bump (the same spot you aim for on the way up); " +
                "brushing a body or tail is free. Rocks, driftwood and jellies count wherever you touch them. " +
                "The first $bumpFree m are bump-free.",
            screen = Screen.HOME,
            art = TourArt.BUMPS,
            sinceVersion = 7,
        ),
        TourStep(
            id = "ascent",
            title = "Coming up: aim for heads",
            body = "On the way up, steer so the hook meets a fish's head: the pale face by its eye. " +
                "Touching the tail or body does nothing.",
            screen = Screen.HOME,
            art = TourArt.HEADS,
        ),
        TourStep(
            id = "weight",
            title = "Mind the weight",
            body = "Your rope can only carry so much. Watch the Load bar: once it's full, heavier fish slip away.",
            screen = Screen.HOME,
            art = TourArt.WEIGHT,
        ),
        TourStep(
            id = "fast_reel",
            title = "Line full? Reel in fast",
            body = "As soon as you've used your $bumps bumps, or once nothing more can fit on your line, a Reel " +
                "in fast button appears on the way up: it hauls the hook to the boat in " +
                "${GameConfig.FAST_REEL_MAX_SECONDS} seconds at most, wherever you are. Nothing more is caught " +
                "after that. Handy against the clock in timed battles.",
            screen = Screen.HOME,
            art = TourArt.WEIGHT,
            sinceVersion = 8,
        ),
        TourStep(
            id = "themes",
            title = "Themes",
            body = "The palette button at the top changes how the menus look: the Lagoon by default, with a sea scene " +
                "behind every tab. More themes are in the Shop for euros or points, and the richer ones cost more. " +
                "The water you fish in never changes.",
            screen = Screen.HOME,
            target = TourTarget.HOME_THEMES,
            sinceVersion = 10,
        ),
        TourStep(
            id = "night_mode",
            title = "Night mode",
            body = "Every theme has a night version: choose Day, Night, or follow your phone's dark mode.",
            screen = Screen.THEMES,
            target = TourTarget.THEMES_MODE,
            sinceVersion = 10,
        ),
        TourStep(
            id = "points",
            title = "Score points",
            body = "Deeper catches are worth more points, shown here at the top. Sparkling fish (gold outline " +
                "and glints) are worth ${GameConfig.SPARKLING_MULTIPLIER}× more.",
            screen = Screen.HOME,
            target = TourTarget.HOME_POINTS,
            art = TourArt.SPARKLING,
            sinceVersion = 5,
        ),
        TourStep(
            id = "upgrades",
            title = "Upgrade your rope",
            body = "Spend points in the Shop tab, on its Rope page. Rope Length lets you reach deeper zones and " +
                "new fish, Rope Strength carries more weight, and Rope Agility steers faster. Changed your mind? " +
                "Downgrade a level and get back every point it cost.",
            screen = Screen.SHOP,
            target = TourTarget.SHOP_ROPE,
            sinceVersion = 9,
            shopPage = com.deepline.fishing.ShopPage.ROPE,
        ),
        TourStep(
            id = "tackle",
            title = "Tackle",
            body = "The Tackle page sells one-cast items to switch on before you cast. Lanterns light the dark, a " +
                "${Tackle.DETECTOR.displayName} marks species you haven't caught yet within " +
                "${GameConfig.DETECTOR_RADIUS_M.toInt()} m of the hook, a ${Tackle.LUCKY_CHARM.displayName} gives one " +
                "extra bump on the way down, and a ${Tackle.SPARKLE_LURE.displayName} brings " +
                "${GameConfig.SPARKLE_LURE_MULTIPLIER.toInt()}× as many Sparkling fish. Prices grow with your rope's reach.",
            screen = Screen.SHOP,
            target = TourTarget.SHOP_TACKLE,
            sinceVersion = 9,
            shopPage = com.deepline.fishing.ShopPage.TACKLE,
        ),
        TourStep(
            id = "two_prices",
            title = "Two ways to pay",
            body = "Everything in the shop has two prices: euros, or points. A points price is about " +
                "${GameConfig.MINUTES_PER_EURO.toInt()} minutes of fishing per euro at your rope's reach " +
                "(${(GameConfig.LANTERN_MINUTES_PER_EURO / 60f).toInt()} hour for Lantern packs), so paying only saves " +
                "time: everything can be earned by playing. Paying with points asks you to confirm first.",
            screen = Screen.SHOP,
            target = TourTarget.SHOP_PRICES,
            sinceVersion = 9,
            shopPage = com.deepline.fishing.ShopPage.TACKLE,
        ),
        TourStep(
            id = "lantern",
            title = "Lanterns light the deep",
            body = "Below $dark m the water is dark. Switch this on before a cast to light up much more water " +
                "around the hook (${GameConfig.VISIBLE_RADIUS_LANTERN.roundToInt()} m instead of " +
                "${GameConfig.VISIBLE_RADIUS_DARK.roundToInt()} m). One Lantern is used per cast; earn more from " +
                "milestones or buy them in the Shop.",
            screen = Screen.HOME,
            target = TourTarget.HOME_LANTERN,
            art = TourArt.DARKNESS,
            sinceVersion = 7,
        ),
        TourStep(
            id = "dex",
            title = "Fill your Dex",
            body = "${FishCatalog.all.size} species live in ${DepthZone.entries.size} depth zones, some of them " +
                "rare, and each has a numbered card. A fish that swims close to your hook is marked as Seen: you " +
                "learn its name and depths. Catch it to fill its card with its weight, points and your records.",
            screen = Screen.DEX,
            target = TourTarget.DEX_HEADER,
            sinceVersion = 5,
        ),
        TourStep(
            id = "dex_filters",
            title = "Browse by zone",
            body = "Filter the cards by depth zone, or show only the fish you've caught. Tap a card to open its " +
                "page, then swipe to flip through the Dex.",
            screen = Screen.DEX,
            target = TourTarget.DEX_FILTERS,
            sinceVersion = 5,
        ),
        TourStep(
            id = "milestones",
            title = "Milestone rewards",
            body = "Every point you earn counts towards milestone rewards (bonus points and Lanterns), even after " +
                "you spend it. The trophy at the top of the Fish tab opens them, with a badge when one is ready " +
                "to claim.",
            screen = Screen.MILESTONES,
            target = TourTarget.MILESTONES_FIRST,
            sinceVersion = 5,
        ),
        TourStep(
            id = "secrets",
            title = "Fishing secrets",
            body = "Some tricks are only found by playing, and they pay off: every time you pull one off it adds " +
                "a bonus to the cast, in battles too. The first time, it's explained on the spot and earns a " +
                "discovery reward, and a Secrets section appears in the Dex. The rarest secrets also unlock " +
                "Skins for your $skinSlots, plus a title shown under your name, in online battles too. They're " +
                "made to show off: looks only, never an advantage.",
            screen = Screen.DEX,
            target = TourTarget.TAB_DEX,
            sinceVersion = 5,
        ),
        TourStep(
            id = "boat",
            title = "Customise your boat",
            body = "Tap your boat at the top of the Fish tab to open the Skins section of the Dex. Customise boat " +
                "lets you paint the hull, trim and cabin, and add a pattern, sails, a flag and an emblem. Basic " +
                "colours are free; the rest unlock with points, or as you catch more species, reach new zones " +
                "and claim milestones.",
            screen = Screen.HOME,
            target = TourTarget.HOME_BOAT,
            sinceVersion = 6,
        ),
        TourStep(
            id = "shop_style",
            title = "Style",
            body = "The Style page has premade boats, paints, emblems and sail prints (previewed on your own boat), " +
                "and new hooks, lines and trails. Like every skin, they only change the look, never the game.",
            screen = Screen.SHOP,
            target = TourTarget.SHOP_STYLE,
            sinceVersion = 9,
            shopPage = com.deepline.fishing.ShopPage.STYLE,
        ),
        TourStep(
            id = "aquarium",
            title = "Your aquarium",
            body = "The fish you've caught swim in your aquarium: open it with the Aquarium button in the Dex and " +
                "pick who swims there. Decor and a bigger tank are on the Shop's Aquarium page.",
            screen = Screen.SHOP,
            target = TourTarget.SHOP_AQUARIUM,
            sinceVersion = 9,
            shopPage = com.deepline.fishing.ShopPage.AQUARIUM,
        ),
        TourStep(
            id = "season",
            title = "Seasons and weekend events",
            body = "Each season lasts ${GameConfig.SEASON_WEEKS} weeks. Every solo cast earns season XP and opens tiers " +
                "of rewards: tackle and points for everyone, and more (a season boat, a badge) with the season pass. " +
                "Every weekend also brings a fishing goal with its own reward. None of it changes battles.",
            screen = Screen.SHOP,
            target = TourTarget.SHOP_SEASON,
            sinceVersion = 9,
            shopPage = com.deepline.fishing.ShopPage.PASSES,
        ),
        TourStep(
            id = "passes",
            title = "Passes",
            body = "The Captain's Club is a monthly membership: a members-only paint, a club title badge, cloud save " +
                "and a free Lantern pouch every month. New anglers also get a one-time Starter pack offer for a " +
                "few days. A dot on the Shop tab means something is waiting for you there.",
            screen = Screen.SHOP,
            target = TourTarget.SHOP_PASSES,
            sinceVersion = 9,
            shopPage = com.deepline.fishing.ShopPage.PASSES,
        ),
        TourStep(
            id = "badges",
            title = "Title badges and Dex cards",
            body = "Choose the frame of your title badge (shown under your name, online too) and how the Dex " +
                "shows the species you've caught. More come from the Shop, season passes and events.",
            screen = Screen.DEX,
            target = TourTarget.SKINS_BADGE,
            sinceVersion = 9,
            dexSection = com.deepline.fishing.DexSection.SKINS,
        ),
        TourStep(
            id = "atmosphere",
            title = "Atmosphere",
            body = "The water is harmless near the surface and gets spookier the deeper you go. Reach the " +
                "${DepthZone.ABYSS.displayName} to unlock ${Atmosphere.NIGHTMARE.displayName}, a frightening look " +
                "for the deep: you see exactly as much as before, only the mood changes.",
            screen = Screen.DEX,
            target = TourTarget.SKINS_ATMOSPHERE,
            sinceVersion = 6,
            dexSection = com.deepline.fishing.DexSection.SKINS,
        ),
        TourStep(
            id = "battle",
            title = "1v1 battles",
            body = "Pick a mode: Best of 5, 3-Minute Rush, Heavy Haul, Species Hunt or Deep Dive. " +
                "Both players fish the very same water, so skill decides.",
            screen = Screen.BATTLE_SETUP,
            target = TourTarget.BATTLE_MODES,
        ),
        TourStep(
            id = "opponents",
            title = "Choose an opponent",
            body = "Play the computer (Easy, Normal or Hard), a friend on this phone, or someone online: create a " +
                "room and share its code, or use Quick match. Online, both players use the same tournament rope " +
                "(level ${OnlineProtocol.TOURNAMENT_ROPE_LEVEL} in every stat).",
            screen = Screen.BATTLE_SETUP,
            target = TourTarget.BATTLE_OPPONENT,
        ),
        TourStep(
            id = "battle_tackle",
            title = "Tackle in battles",
            body = "Lanterns and ${Tackle.DETECTOR.displayName}s work in battles too: switch them on here, and each of " +
                "your casts uses one. Anyone can get them, so they're part of the game. The Lucky charm and the " +
                "Sparkle lure are for solo casts only.",
            screen = Screen.BATTLE_SETUP,
            target = TourTarget.BATTLE_TACKLE,
            sinceVersion = 9,
        ),
        TourStep(
            id = "gifts",
            title = "Gifts and cloud save",
            body = "After an online battle you can send your opponent a gift: tackle or looks, paid with euros or " +
                "points. Gifts for you arrive when you open the game. Captain's Club members are backed up on the " +
                "server: the Shop's Account button shows the recovery code that restores it on a new phone.",
            screen = Screen.BATTLE_SETUP,
            sinceVersion = 9,
        ),
        TourStep(
            id = "wager",
            title = "Wager points",
            body = "Against the computer you can stake points on the result: a win pays the stake back " +
                "${GameConfig.wagerPayout(com.deepline.fishing.core.BotDifficulty.EASY)}× against Easy, " +
                "${GameConfig.wagerPayout(com.deepline.fishing.core.BotDifficulty.NORMAL)}× against Normal and " +
                "${GameConfig.wagerPayout(com.deepline.fishing.core.BotDifficulty.HARD)}× against Hard. A draw refunds it.",
            screen = Screen.BATTLE_SETUP,
            target = TourTarget.BATTLE_WAGER,
            sinceVersion = 8,
        ),
        TourStep(
            id = "settings",
            title = "Settings",
            body = "The gear at the top of the Fish tab opens Settings.",
            screen = Screen.HOME,
            target = TourTarget.HOME_SETTINGS,
            sinceVersion = VERSION,
        ),
        // -- Game feel (sound, haptics) steps below.
        TourStep(
            id = "sound",
            title = "Sound and feel",
            body = "Set the volume, the music and the sound effects separately here. The music follows your " +
                "hook: calm near the surface, darker in the deep. Vibration adds a light touch to bumps, " +
                "catches and big wins, and Reduce motion turns off screen shake, slow motion and flashes.",
            screen = Screen.SETTINGS,
            target = TourTarget.SETTINGS_SOUND,
            sinceVersion = VERSION,
        ),

        // -- Daily loop steps below.

        // -- Social steps below.

        // -- Privacy steps below.

        // -- Language steps below.

        TourStep(
            id = "replay",
            title = "That's it, good fishing!",
            body = "You can replay this tour any time from Settings.",
            screen = Screen.SETTINGS,
            target = TourTarget.SETTINGS_TOUR,
            sinceVersion = VERSION,
        ),
    )

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
