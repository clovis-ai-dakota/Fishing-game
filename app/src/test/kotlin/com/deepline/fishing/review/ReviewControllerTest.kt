package com.deepline.fishing.review

import android.app.Activity
import com.deepline.fishing.core.CastResult
import com.deepline.fishing.core.CatchRecord
import com.deepline.fishing.core.FishCatalog
import com.deepline.fishing.core.GameConfig
import com.deepline.fishing.core.KeyValueStore
import com.deepline.fishing.core.LocalDays
import com.deepline.fishing.core.PlayerProgress
import com.deepline.fishing.core.ReviewMoment
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The review controller: happy moments, setbacks, persistence, and Play failing quietly. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ReviewControllerTest {
    private class MapStore : KeyValueStore {
        val map = HashMap<String, String>()
        override fun getString(key: String): String? = map[key]
        override fun putString(key: String, value: String) {
            map[key] = value
        }
    }

    private class Prompter(val fail: Boolean = false) : ReviewPrompter {
        var launches = 0
        override suspend fun launch(activity: Activity) {
            launches++
            if (fail) error("Play Store missing")
        }
    }

    private val day0 = 20_400L
    private var now = day0 * LocalDays.DAY_MS + 12 * LocalDays.HOUR_MS
    private val store = MapStore()
    private val activity: Activity by lazy { Robolectric.buildActivity(Activity::class.java).get() }

    private val sparklingCast = CastResult(listOf(CatchRecord(FishCatalog.all.first(), true, 20f, 30)), 40f, 0, null, false)
    private val seasoned = PlayerProgress(castsMade = GameConfig.Review.MIN_CASTS)

    private fun controller(prompter: ReviewPrompter) = ReviewController(store, prompter) { now }

    /** Plays one solo cast on each of [days] days (a new species each time: never happy alone after the first). */
    private fun ReviewController.playDays(days: Int) {
        repeat(days) {
            onCastCollected(seasoned, seasoned, CastResult(emptyList(), 30f, 0, null, false))
            now += LocalDays.DAY_MS
        }
    }

    @Test
    fun aHappyMomentBecomesPendingAndLaunchesOnce() = runBlocking {
        val prompter = Prompter()
        val c = controller(prompter)
        c.playDays(GameConfig.Review.MIN_PLAY_DAYS)
        assertNull(c.pending)
        c.onCastCollected(seasoned, seasoned, sparklingCast)
        assertEquals(ReviewMoment.RARE_CATCH, c.pending)
        c.launch(activity)
        c.launch(activity)
        assertEquals(1, prompter.launches)
        assertNull(c.pending)
        // Remembered on the phone: a new controller (next launch) knows it asked.
        assertEquals(1, controller(prompter).state.asks)
        assertEquals((GameConfig.Review.MIN_PLAY_DAYS + 1), controller(prompter).state.playDays)
    }

    @Test
    fun startingACastDropsThePendingMoment() {
        val c = controller(Prompter())
        c.playDays(GameConfig.Review.MIN_PLAY_DAYS)
        c.onBattleEnded(won = true, byForfeit = false, castsMade = seasoned.castsMade)
        assertEquals(ReviewMoment.BATTLE_WON, c.pending)
        c.onBusy()
        assertNull(c.pending)
    }

    @Test
    fun lossesAndForfeitsAreNeverHappy() {
        val c = controller(Prompter())
        c.playDays(GameConfig.Review.MIN_PLAY_DAYS)
        c.onBattleEnded(won = true, byForfeit = true, castsMade = seasoned.castsMade)
        assertNull(c.pending)
        c.onBattleEnded(won = false, byForfeit = false, castsMade = seasoned.castsMade)
        assertEquals(now, c.state.lastSetbackMs)
        // Right after the loss even a rare catch doesn't ask...
        c.onCastCollected(seasoned, seasoned, sparklingCast)
        assertNull(c.pending)
        // ...but an hour later it may.
        now += GameConfig.Review.SETBACK_QUIET_MINUTES * 60_000L
        c.onCastCollected(seasoned, seasoned, sparklingCast)
        assertEquals(ReviewMoment.RARE_CATCH, c.pending)
    }

    @Test
    fun playFailingIsSilent() = runBlocking {
        val prompter = Prompter(fail = true)
        val c = controller(prompter)
        c.playDays(GameConfig.Review.MIN_PLAY_DAYS)
        c.onCastCollected(seasoned, seasoned, sparklingCast)
        c.launch(activity)
        // No exception reaches the game, and the ask still counts (the game can't know what Play did).
        assertEquals(1, prompter.launches)
        assertEquals(1, c.state.asks)
    }
}
