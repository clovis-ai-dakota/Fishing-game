package com.deepline.fishing.review

import android.app.Activity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.deepline.fishing.analytics.GameAnalytics
import com.deepline.fishing.core.CastResult
import com.deepline.fishing.core.KeyValueStore
import com.deepline.fishing.core.PlayerProgress
import com.deepline.fishing.core.ReviewMoment
import com.deepline.fishing.core.ReviewPrompts
import com.deepline.fishing.core.ReviewState
import com.deepline.fishing.core.ShopClock
import kotlinx.coroutines.CancellationException

/**
 * When to show Google Play's review card (rules in core `ReviewPrompts`, numbers in
 * `GameConfig.Review`). The view model reports happy moments and setbacks; a happy moment that
 * passes the rules becomes [pending], and MainActivity launches the card as soon as the screen is
 * calm (no cast, tour, dialog or payment under way). There is no "Rate us" button and no question
 * before the card, and nothing is ever given for a review.
 */
class ReviewController(
    private val store: KeyValueStore,
    private val prompter: ReviewPrompter,
    private val clock: () -> Long = { ShopClock.now() },
) {
    var state: ReviewState = ReviewPrefs.load(store)
        private set

    /** A happy moment waiting for a calm screen to show the card. */
    var pending by mutableStateOf<ReviewMoment?>(null)
        private set

    private var pendingCasts = 0

    /** A solo cast was collected ([before] is the progress before it was added). */
    fun onCastCollected(before: PlayerProgress, after: PlayerProgress, result: CastResult) {
        val now = clock()
        save(ReviewPrompts.onPlayed(state, now))
        consider(ReviewPrompts.momentOf(before, result), after.castsMade, now)
    }

    /** A battle against the computer or online ended; [won] is null for a draw. */
    fun onBattleEnded(won: Boolean?, byForfeit: Boolean, castsMade: Int) {
        if (won == false) return onSetback()
        consider(ReviewPrompts.momentOfBattle(won, byForfeit), castsMade, clock())
    }

    /** Something went wrong for the player (a lost battle, a failed purchase...): no card for a while. */
    fun onSetback() {
        pending = null
        save(ReviewPrompts.onSetback(state, clock()))
    }

    /** A cast or a battle starts: the happy moment has passed. */
    fun onBusy() {
        pending = null
    }

    private fun consider(moment: ReviewMoment?, castsMade: Int, now: Long) {
        if (ReviewPrompts.shouldAsk(state, moment, castsMade, now)) {
            pending = moment
            pendingCasts = castsMade
        }
    }

    /**
     * Shows Play's card for the [pending] moment. The ask is recorded before Play answers: Play
     * may decide not to show anything, and the game can't know either way.
     */
    suspend fun launch(activity: Activity) {
        val moment = pending ?: return
        pending = null
        save(ReviewPrompts.onAsked(state, clock()))
        GameAnalytics.reviewPrompt(moment, state.asks, pendingCasts, state.playDays)
        try {
            prompter.launch(activity)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Play unavailable (no Play Store, sideloaded build...): Google asks apps not to tell
            // the player and to carry on as usual.
        }
    }

    private fun save(s: ReviewState) {
        state = s
        ReviewPrefs.save(store, s)
    }
}

/** The review prompts' memory, on this device only (the key-value store, not the save). */
object ReviewPrefs {
    private const val PLAY_DAYS = "review.playDays"
    private const val LAST_PLAY_DAY = "review.lastPlayDay"
    private const val LAST_ASKED = "review.lastAskedMs"
    private const val ASKS = "review.asks"
    private const val LAST_SETBACK = "review.lastSetbackMs"

    fun load(store: KeyValueStore) = ReviewState(
        playDays = store.getString(PLAY_DAYS)?.toIntOrNull() ?: 0,
        lastPlayDay = store.getString(LAST_PLAY_DAY)?.toLongOrNull(),
        lastAskedMs = store.getString(LAST_ASKED)?.toLongOrNull(),
        asks = store.getString(ASKS)?.toIntOrNull() ?: 0,
        lastSetbackMs = store.getString(LAST_SETBACK)?.toLongOrNull(),
    )

    fun save(store: KeyValueStore, s: ReviewState) {
        store.putString(PLAY_DAYS, s.playDays.toString())
        store.putString(LAST_PLAY_DAY, s.lastPlayDay?.toString().orEmpty())
        store.putString(LAST_ASKED, s.lastAskedMs?.toString().orEmpty())
        store.putString(ASKS, s.asks.toString())
        store.putString(LAST_SETBACK, s.lastSetbackMs?.toString().orEmpty())
    }
}
