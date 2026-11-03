package com.deepline.fishing.ui.tour

import com.deepline.fishing.Screen
import com.deepline.fishing.core.GameConfig
import com.deepline.fishing.core.online.OnlineProtocol

/** A UI element a tour step can point at (see `Modifier.tourTarget`). */
enum class TourTarget {
    HOME_CAST, HOME_POINTS, HOME_LANTERN, HOME_BATTLE, HOME_TOUR,
    UPGRADES_ROPE, COLLECTION_HEADER, MILESTONES_FIRST, BATTLE_MODES, BATTLE_OPPONENT,
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
    /** 2: head-only bumps, smoother steering with an aim marker, fishing secrets. */
    const val VERSION = 2

    private val bumps = GameConfig.DESCENT_TOUCH_LIMIT
    private val bumpFree = GameConfig.BUMP_FREE_DEPTH.toInt()
    private val dark = GameConfig.DARK_DEPTH.toInt()

    val steps: List<TourStep> = listOf(
        TourStep(
            id = "welcome",
            title = "Welcome to Deep Line!",
            body = "Catch every fish in the deep. This short tour shows how to play and what everything does. " +
                "Tap Next, or Skip tour if you already know your way around.",
            screen = Screen.HOME,
        ),
        TourStep(
            id = "cast",
            title = "Cast your line",
            body = "Tap Cast line to drop your hook from the boat.",
            screen = Screen.HOME,
            target = TourTarget.HOME_CAST,
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
            sinceVersion = 2,
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
            id = "points",
            title = "Score points",
            body = "Deeper catches are worth more points. Sparkling fish (gold outline and glints) are worth " +
                "${GameConfig.SPARKLING_MULTIPLIER}× more.",
            screen = Screen.HOME,
            target = TourTarget.HOME_POINTS,
            art = TourArt.SPARKLING,
        ),
        TourStep(
            id = "upgrades",
            title = "Upgrade your rope",
            body = "Spend points here. Rope Length lets you reach deeper zones and new fish, Rope Strength " +
                "carries more weight, and Rope Agility steers faster.",
            screen = Screen.UPGRADES,
            target = TourTarget.UPGRADES_ROPE,
        ),
        TourStep(
            id = "lantern",
            title = "Lanterns light the deep",
            body = "Below $dark m the water is dark. Switch this on before a cast to light up much more water " +
                "around the hook. One Lantern is used per cast; earn more from milestones or buy them in Upgrades.",
            screen = Screen.HOME,
            target = TourTarget.HOME_LANTERN,
            art = TourArt.DARKNESS,
        ),
        TourStep(
            id = "log",
            title = "Fill your Fish log",
            body = "16 species live in 4 depth zones. Every catch is recorded here. Fish you haven't caught yet " +
                "show as silhouettes, with the depths where they live.",
            screen = Screen.COLLECTION,
            target = TourTarget.COLLECTION_HEADER,
        ),
        TourStep(
            id = "milestones",
            title = "Milestone rewards",
            body = "Every point you earn counts towards milestone rewards (bonus points and Lanterns), even after " +
                "you spend it. Claim them here.",
            screen = Screen.MILESTONES,
            target = TourTarget.MILESTONES_FIRST,
        ),
        TourStep(
            id = "secrets",
            title = "Fishing secrets",
            body = "Some tricks are only found by playing. When you discover one, it's explained on the spot, and " +
                "a Fishing secrets button appears on the home screen so you can read it again.",
            screen = Screen.HOME,
            sinceVersion = 2,
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
            id = "replay",
            title = "That's it, good fishing!",
            body = "You can replay this tour any time with this button.",
            screen = Screen.HOME,
            target = TourTarget.HOME_TOUR,
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
