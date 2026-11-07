package com.deepline.fishing.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** When the game may ask for a Google Play review (GameConfig.Review). */
class ReviewPromptsTest {
    private val r = GameConfig.Review
    private val day0 = 20_400L
    private fun at(day: Long, hour: Int = 12) = day * LocalDays.DAY_MS + hour * LocalDays.HOUR_MS

    private val common = FishCatalog.all.first { !it.lastBumpOnly && Progression.rarityStars(it) < r.RARE_CATCH_STARS }
    private val rare = FishCatalog.all.first { !it.lastBumpOnly && Progression.rarityStars(it) >= r.RARE_CATCH_STARS }

    private fun cast(vararg catches: CatchRecord) = CastResult(catches.toList(), 40f, 0, null, false)
    private fun catchOf(s: FishSpecies, sparkling: Boolean = false) = CatchRecord(s, sparkling, 30f, 10)

    /** A player who has played long enough: [r.MIN_PLAY_DAYS] days. */
    private fun seasoned(): ReviewState =
        (0 until r.MIN_PLAY_DAYS).fold(ReviewState()) { s, d -> ReviewPrompts.onPlayed(s, at(day0 + d), 0) }

    @Test
    fun aHappyMomentAfterEnoughPlayIsTheTimeToAsk() {
        val s = seasoned()
        val now = at(day0 + r.MIN_PLAY_DAYS)
        assertTrue(ReviewPrompts.shouldAsk(s, ReviewMoment.RARE_CATCH, r.MIN_CASTS, now))
        assertTrue(ReviewPrompts.shouldAsk(s, ReviewMoment.BATTLE_WON, r.MIN_CASTS + 100, now))
        // No happy moment, no prompt.
        assertEquals("no happy moment", ReviewPrompts.blocker(s, null, r.MIN_CASTS, now))
    }

    @Test
    fun newPlayersAreNeverAsked() {
        val now = at(day0 + 10)
        assertEquals("too few casts", ReviewPrompts.blocker(seasoned(), ReviewMoment.RARE_CATCH, r.MIN_CASTS - 1, now))
        // Many casts on one or two days aren't enough: they must come back on different days.
        val oneDay = (0 until 50).fold(ReviewState()) { s, i -> ReviewPrompts.onPlayed(s, at(day0) + i * 60_000L, 0) }
        assertEquals(1, oneDay.playDays)
        assertEquals("too few days of play", ReviewPrompts.blocker(oneDay, ReviewMoment.RARE_CATCH, 500, now))
    }

    @Test
    fun daysOfPlayFollowLocalMidnightAndIgnoreAClockSetBack() {
        val paris = java.util.TimeZone.getTimeZone("Europe/Paris")
        // 23:30 UTC is already the next day in Paris.
        val late = at(day0, 23) + 30 * 60_000L
        val s = ReviewPrompts.onPlayed(ReviewPrompts.onPlayed(ReviewState(), at(day0, 10), paris.getOffset(late)), late, paris.getOffset(late))
        assertEquals(2, s.playDays)
        // Setting the clock back a day doesn't count that day again.
        val back = ReviewPrompts.onPlayed(s, at(day0 - 1), 0)
        assertEquals(s, back)
    }

    @Test
    fun neverRightAfterASetback() {
        val s = seasoned()
        val now = at(day0 + 5)
        val after = ReviewPrompts.onSetback(s, now - 5 * 60_000L)
        assertEquals("right after a setback", ReviewPrompts.blocker(after, ReviewMoment.BATTLE_WON, r.MIN_CASTS, now))
        // Once things have calmed down, a happy moment may ask again.
        val later = now + r.SETBACK_QUIET_MINUTES * 60_000L
        assertTrue(ReviewPrompts.shouldAsk(after, ReviewMoment.BATTLE_WON, r.MIN_CASTS, later))
    }

    @Test
    fun atMostOnceEveryCoolDownAndAFewTimesInAll() {
        var s = seasoned()
        var now = at(day0 + 5)
        assertTrue(ReviewPrompts.shouldAsk(s, ReviewMoment.NEW_SPECIES, r.MIN_CASTS, now))
        s = ReviewPrompts.onAsked(s, now)
        assertEquals(1, s.asks)
        assertEquals("asked recently", ReviewPrompts.blocker(s, ReviewMoment.NEW_SPECIES, r.MIN_CASTS, now + LocalDays.DAY_MS))
        assertEquals("asked recently", ReviewPrompts.blocker(s, ReviewMoment.NEW_SPECIES, r.MIN_CASTS, now + (r.COOLDOWN_DAYS - 1) * LocalDays.DAY_MS))
        // A clock set back before the last ask doesn't open the gate.
        assertFalse(ReviewPrompts.shouldAsk(s, ReviewMoment.NEW_SPECIES, r.MIN_CASTS, now - 400 * LocalDays.DAY_MS))
        repeat(r.MAX_ASKS - 1) {
            now += r.COOLDOWN_DAYS * LocalDays.DAY_MS
            assertTrue(ReviewPrompts.shouldAsk(s, ReviewMoment.NEW_SPECIES, r.MIN_CASTS, now))
            s = ReviewPrompts.onAsked(s, now)
        }
        now += r.COOLDOWN_DAYS * LocalDays.DAY_MS
        assertEquals("asked enough", ReviewPrompts.blocker(s, ReviewMoment.NEW_SPECIES, r.MIN_CASTS, now))
    }

    @Test
    fun happyCastsAreRareCatchesOrNewSpecies() {
        val fresh = PlayerProgress()
        val knows = PlayerProgress(collection = mapOf(common.id to CollectionEntry(caught = 3), rare.id to CollectionEntry(caught = 1)))
        assertNull(ReviewPrompts.momentOf(fresh, cast()), "an empty cast is never happy")
        assertEquals(ReviewMoment.NEW_SPECIES, ReviewPrompts.momentOf(fresh, cast(catchOf(common))))
        assertNull(ReviewPrompts.momentOf(knows, cast(catchOf(common))), "a common fish already in the log")
        assertEquals(ReviewMoment.RARE_CATCH, ReviewPrompts.momentOf(knows, cast(catchOf(rare))))
        assertEquals(ReviewMoment.RARE_CATCH, ReviewPrompts.momentOf(knows, cast(catchOf(common, sparkling = true))))
        assertEquals(ReviewMoment.RARE_CATCH, ReviewPrompts.momentOf(knows, cast(catchOf(common), catchOf(FishCatalog.sovereign))))
    }

    @Test
    fun onlyBattlesWonByPlayingAreHappy() {
        assertEquals(ReviewMoment.BATTLE_WON, ReviewPrompts.momentOfBattle(won = true, byForfeit = false))
        assertNull(ReviewPrompts.momentOfBattle(won = true, byForfeit = true))
        assertNull(ReviewPrompts.momentOfBattle(won = false, byForfeit = false))
        assertNull(ReviewPrompts.momentOfBattle(won = null, byForfeit = false))
    }

    @Test
    fun theNumbersStayReasonable() {
        // Play's own quota is about a month: our cool-down is always much longer.
        assertTrue(r.COOLDOWN_DAYS >= 60)
        assertTrue(r.MIN_PLAY_DAYS >= 2 && r.MIN_CASTS >= 10)
        assertTrue(r.MAX_ASKS in 1..5)
    }
}
