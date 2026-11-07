package com.deepline.fishing.core

/** A happy moment, right after which the game may ask for a Google Play review. */
enum class ReviewMoment {
    /** A solo cast landed a rare species ([GameConfig.Review.RARE_CATCH_STARS]+ stars) or a Sparkling fish. */
    RARE_CATCH,

    /** A solo cast landed a species never caught before. */
    NEW_SPECIES,

    /** A battle against the computer or online was won (not by the opponent's forfeit). */
    BATTLE_WON,
}

/**
 * What this phone remembers about review prompts (device-only, in the app's key-value store: a
 * cloud restore never carries it to another phone, and Play's own quota is per account anyway).
 * The game never learns whether the player rated: Play doesn't tell, by design.
 */
data class ReviewState(
    /** Different local days on which the player finished a solo cast. */
    val playDays: Int = 0,
    val lastPlayDay: Long? = null,
    /** When the review flow was last launched (epoch ms), and how many times in all. */
    val lastAskedMs: Long? = null,
    val asks: Int = 0,
    /** The last setback (a lost battle, a failed purchase...), epoch ms. */
    val lastSetbackMs: Long? = null,
)

/**
 * When to ask for a Play review (Google Play In-App Review API). Pure rules, unit tested; the
 * app's ReviewController stores the [ReviewState] and launches Play's card.
 *
 * Following Google's guidance (docs/research/launch.md): ask only once the player has played
 * enough to have an opinion, right after a happy moment, never during a cast, never right after a
 * setback, rarely (Play's own quota is about a month and silently drops extra calls, so the game
 * keeps a much longer cool-down), never with a question first ("Do you like the game?") and never
 * for a reward. There is no "Rate us" button: the card may not show, so a button could do nothing.
 */
object ReviewPrompts {
    private const val MINUTE_MS = 60_000L

    /** A solo cast was collected at [now]: counts the day as a day of play. */
    fun onPlayed(s: ReviewState, now: Long, offsetMs: Int = LocalDays.offsetMs(now)): ReviewState {
        val day = LocalDays.dayOf(now, offsetMs)
        val last = s.lastPlayDay
        // A clock set back never counts a day twice.
        if (last != null && day <= last) return s
        return s.copy(playDays = s.playDays + 1, lastPlayDay = day)
    }

    /** Something went wrong for the player at [now]: no prompt for a while. */
    fun onSetback(s: ReviewState, now: Long): ReviewState = s.copy(lastSetbackMs = now)

    /** The review flow was launched at [now]. */
    fun onAsked(s: ReviewState, now: Long): ReviewState = s.copy(lastAskedMs = now, asks = s.asks + 1)

    /**
     * Whether the cast [result] (collected on top of [before], the progress before it) was a happy
     * moment: a rare or Sparkling catch, or a species never caught before. Casts that land nothing
     * never are.
     */
    fun momentOf(before: PlayerProgress, result: CastResult): ReviewMoment? {
        val catches = result.catches
        if (catches.isEmpty()) return null
        if (catches.any { it.sparkling || it.species.lastBumpOnly || Progression.rarityStars(it.species) >= GameConfig.Review.RARE_CATCH_STARS }) {
            return ReviewMoment.RARE_CATCH
        }
        if (catches.any { (before.collection[it.species.id]?.caught ?: 0) == 0 }) return ReviewMoment.NEW_SPECIES
        return null
    }

    /** A battle ended: a win (by playing, not by the opponent leaving) is a happy moment. */
    fun momentOfBattle(won: Boolean?, byForfeit: Boolean): ReviewMoment? =
        if (won == true && !byForfeit) ReviewMoment.BATTLE_WON else null

    /** Why the game won't ask now (null: it may). For tests and the analytics doc. */
    fun blocker(s: ReviewState, moment: ReviewMoment?, castsMade: Int, now: Long): String? {
        val r = GameConfig.Review
        return when {
            moment == null -> "no happy moment"
            castsMade < r.MIN_CASTS -> "too few casts"
            s.playDays < r.MIN_PLAY_DAYS -> "too few days of play"
            s.asks >= r.MAX_ASKS -> "asked enough"
            // A clock set back before the last ask counts as "just asked".
            s.lastAskedMs != null && now - s.lastAskedMs < r.COOLDOWN_DAYS * LocalDays.DAY_MS -> "asked recently"
            s.lastSetbackMs != null && now - s.lastSetbackMs < r.SETBACK_QUIET_MINUTES * MINUTE_MS -> "right after a setback"
            else -> null
        }
    }

    fun shouldAsk(s: ReviewState, moment: ReviewMoment?, castsMade: Int, now: Long): Boolean =
        blocker(s, moment, castsMade, now) == null
}
