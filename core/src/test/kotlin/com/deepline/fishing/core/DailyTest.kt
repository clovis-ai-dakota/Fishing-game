package com.deepline.fishing.core

import java.util.TimeZone
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The login streak and its reward calendar, local days and time zones. */
class DailyStreakTest {
    private val day = 20_400L

    @AfterTest
    fun reset() {
        LocalDays.zoneOverride = null
        ShopClock.override = null
    }

    @Test
    fun localDaysFollowTheTimeZone() {
        // 23:30 UTC: already the next day in Paris (00:30), still the same day in New York (18:30).
        val t = day * LocalDays.DAY_MS + 23 * LocalDays.HOUR_MS + 30 * 60_000L
        val paris = TimeZone.getTimeZone("Europe/Paris")
        val newYork = TimeZone.getTimeZone("America/New_York")
        assertEquals(day, LocalDays.dayOf(t, 0))
        assertEquals(day + 1, LocalDays.dayOf(t, paris.getOffset(t)))
        assertEquals(day, LocalDays.dayOf(t, newYork.getOffset(t)))
        assertEquals(23, LocalDays.hourOf(t, 0))
        assertEquals(LocalDays.HOUR_MS / 2, LocalDays.msUntilTomorrow(t, 0))
        // Before 1970 still works (floor, not truncation).
        assertEquals(-1L, LocalDays.dayOf(-1L, 0))
        LocalDays.zoneOverride = paris
        ShopClock.override = t
        assertEquals(day + 1, LocalDays.today())
    }

    @Test
    fun claimingOncePerDayBuildsAStreakAndWalksTheCalendar() {
        var p = PlayerProgress()
        assertTrue(Daily.status(p, day).canClaim)
        assertEquals(0, Daily.status(p, day).streak)
        p = Daily.claimReward(p, day)!!
        assertEquals(1, p.dailyStreak)
        assertEquals(DailyRewards.pointsFor(p, (DailyRewards.calendar[0][0] as DailyGrant.Points).casts), p.pointsBalance)
        // Bonus points never count towards milestones.
        assertEquals(0L, p.pointsEarned)
        // Once per day.
        assertNull(Daily.claimReward(p, day))
        assertTrue(Daily.status(p, day).claimedToday)
        // The next day: the streak grows and the calendar moves on.
        p = Daily.claimReward(p, day + 1)!!
        assertEquals(2, p.dailyStreak)
        assertEquals(1, p.detectors)
        assertEquals(3, Daily.status(p, day + 2).nextCalendarDay)
        assertTrue(Daily.status(p, day + 2).atRisk)
        assertEquals(2, p.dailyBestStreak)
    }

    @Test
    fun aShieldCoversAMissedDayAndTooManyMissedDaysStartTheStreakOverWithoutLosingTheCalendar() {
        var p = PlayerProgress()
        for (d in 0L until 3L) p = Daily.claimReward(p, day + d)!!
        assertEquals(3, p.dailyStreak)
        assertEquals(GameConfig.STARTING_STREAK_SHIELDS, p.streakShields)
        // One missed day (day + 3): the shield covers it.
        val status = Daily.status(p, day + 4)
        assertEquals(1, status.missedDays)
        assertEquals(3, status.streak)
        p = Daily.claimReward(p, day + 4)!!
        assertEquals(4, p.dailyStreak)
        assertEquals(GameConfig.STARTING_STREAK_SHIELDS - 1, p.streakShields)
        // Two missed days without a shield: the streak starts over, the calendar carries on.
        assertEquals(0, Daily.status(p, day + 7).streak)
        val calendarBefore = p.dailyClaims
        p = Daily.claimReward(p, day + 7)!!
        assertEquals(1, p.dailyStreak)
        assertEquals(4, p.dailyBestStreak)
        assertEquals(calendarBefore + 1, p.dailyClaims)
        assertEquals(6, Daily.status(p, day + 8).nextCalendarDay)
    }

    @Test
    fun daySevenGivesAShieldUpToTheMaximum() {
        var p = PlayerProgress()
        for (d in 0L until 7L) p = Daily.claimReward(p, day + d)!!
        assertEquals(minOf(GameConfig.STARTING_STREAK_SHIELDS + 1, GameConfig.MAX_STREAK_SHIELDS), p.streakShields)
        assertTrue(p.lanterns > GameConfig.STARTING_LANTERNS)
        for (d in 7L until 21L) p = Daily.claimReward(p, day + d)!!
        assertEquals(GameConfig.MAX_STREAK_SHIELDS, p.streakShields)
        assertEquals(21, p.dailyStreak)
        // Back to day 1 of the calendar.
        assertEquals(1, Daily.status(p, day + 21).nextCalendarDay)
    }

    @Test
    fun aClockSetBackOrAFlightWestNeverClaimsTwice() {
        var p = Daily.claimReward(PlayerProgress(), day)!!
        // The clock is set back two days: nothing to claim, nothing breaks.
        assertNull(Daily.claimReward(p, day - 2))
        val back = Daily.status(p, day - 2)
        assertFalse(back.canClaim)
        assertEquals(1, back.streak)
        // Claimed just after midnight in Paris, then flown to New York: it's the day before there.
        val t = (day + 1) * LocalDays.DAY_MS - 30 * 60_000L // 23:30 UTC = 00:30 in Paris (winter)
        val parisDay = LocalDays.dayOf(t, TimeZone.getTimeZone("Europe/Paris").getOffset(t))
        val newYorkDay = LocalDays.dayOf(t + LocalDays.HOUR_MS, TimeZone.getTimeZone("America/New_York").getOffset(t))
        p = Daily.claimReward(p, parisDay)!!
        assertNull(Daily.claimReward(p, newYorkDay))
        // The clock comes back to the right day: the streak is intact and claims carry on.
        p = Daily.claimReward(p, parisDay + 1)!!
        assertEquals(3, p.dailyStreak)
    }

    @Test
    fun daylightSavingDaysAreStillOneDay() {
        val paris = TimeZone.getTimeZone("Europe/Paris")
        // 29 March 2026 has 23 hours in Paris; 25 October 2026 has 25.
        for (start in listOf(1_774_738_800_000L, 1_792_879_200_000L)) { // local midnights
            val first = LocalDays.dayOf(start + 1, paris.getOffset(start + 1))
            val evening = start + 22 * LocalDays.HOUR_MS
            assertEquals(first, LocalDays.dayOf(evening, paris.getOffset(evening)))
            val nextMorning = start + 26 * LocalDays.HOUR_MS
            assertEquals(first + 1, LocalDays.dayOf(nextMorning, paris.getOffset(nextMorning)))
        }
    }
}

/** Daily challenges: generation matched to the player, progress, claims and rerolls. */
class DailyChallengeTest {
    private val day = 20_400L

    private fun cast(vararg catches: CatchRecord, depth: Float = 100f) =
        CastResult(catches.toList(), depth, 0, ReverseReason.ROPE_END, false)

    private fun catch(id: String, sparkling: Boolean = false) = CatchRecord(FishCatalog.byId(id)!!, sparkling, FishCatalog.byId(id)!!.minDepth + 5f, 10)

    @Test
    fun threeChallengesADayOneOfEachDifficultyAndKindTheSameForTheSamePlayer() {
        val p = PlayerProgress()
        val a = Daily.generateDay(p, day)
        assertEquals(ChallengeDifficulty.entries, a.map { it.difficulty })
        assertEquals(3, a.map { it.kind }.toSet().size)
        assertEquals(a, Daily.generateDay(p, day))
        // Other days bring other challenges.
        assertTrue((1L..10L).any { Daily.generateDay(p, day + it) != a })
    }

    @Test
    fun challengesOnlyAskForWhatTheRopeCanReach() {
        val ropes = listOf(PlayerProgress(), PlayerProgress(ropeLevels = mapOf(RopeStat.LENGTH to 4, RopeStat.STRENGTH to 2)), PlayerProgress(ropeLevels = RopeStat.entries.associateWith { it.maxLevel }))
        for (base in ropes) {
            val p = base.copy(seenSpecies = FishCatalog.all.map { it.id }.toSet())
            for (d in 0L until 200L) {
                for (rerolled in listOf(Daily.generateDay(p, day + d), Daily.reroll(Daily.refresh(p, day + d), ChallengeDifficulty.HARD, day + d)!!.dailyChallenges)) {
                    for (c in rerolled) {
                        c.zone?.let { assertTrue(it.minDepth < p.specs.lengthM, "${c.kind} in ${it.displayName} with a ${p.specs.lengthM} m rope") }
                        c.species?.let {
                            assertTrue(it.minDepth < p.specs.lengthM && it.weightKg <= p.specs.maxWeightKg && !it.lastBumpOnly, "${it.name} with a ${p.specs} rope")
                        }
                        if (c.kind == ChallengeKind.REACH_DEPTH) assertTrue(c.target <= p.specs.lengthM)
                        assertTrue(c.target > 0)
                    }
                }
            }
        }
    }

    @Test
    fun speciesChallengesNameOnlySeenFishAndNewSpeciesOnlyWhileSomeAreLeftInReach() {
        val p = PlayerProgress()
        for (d in 0L until 300L) {
            val all = Daily.generateDay(p, day + d)
            // A fresh player has seen nothing yet.
            assertTrue(all.none { it.kind == ChallengeKind.CATCH_SPECIES })
        }
        // Every species within a starting rope's reach already caught: no "new species" challenge.
        val reach = FishCatalog.regular.filter { it.minDepth <= p.specs.lengthM - 10f }
        val done = p.copy(collection = reach.associate { it.id to CollectionEntry(caught = 1) })
        for (d in 0L until 300L) assertTrue(Daily.generateDay(done, day + d).none { it.kind == ChallengeKind.NEW_SPECIES })
    }

    @Test
    fun solosCastsMakeProgressAndFinishedChallengesAreClaimedOnce() {
        var p = Daily.refresh(PlayerProgress(), day).copy(
            dailyChallenges = listOf(
                DailyChallenge(ChallengeDifficulty.EASY, ChallengeKind.CATCH_FISH, 3),
                DailyChallenge(ChallengeDifficulty.MEDIUM, ChallengeKind.REACH_DEPTH, 100),
                DailyChallenge(ChallengeDifficulty.HARD, ChallengeKind.NEW_SPECIES, 2),
            ),
        )
        p = Daily.onCast(p, cast(catch("silver_sprat"), catch("silver_sprat"), depth = 80f), day)
        assertEquals(listOf(2, 80, 1), p.dailyChallenges.map { it.progress })
        // A species already caught isn't new; the deepest cast counts, progress stops at the target.
        val caught = p.copy(collection = mapOf("silver_sprat" to CollectionEntry(caught = 2)))
        p = Daily.onCast(caught, cast(catch("silver_sprat"), catch("coral_darter"), catch("reed_perch"), depth = 120f), day)
        assertEquals(listOf(3, 100, 2), p.dailyChallenges.map { it.progress })
        assertTrue(p.dailyChallenges.all { it.claimable })
        val before = p
        p = Daily.claimChallenge(p, ChallengeDifficulty.HARD, day)!!
        assertTrue(p.pointsBalance > before.pointsBalance)
        assertEquals(before.luckyCharms + 1, p.luckyCharms)
        assertEquals(0L, p.pointsEarned - before.pointsEarned)
        assertNull(Daily.claimChallenge(p, ChallengeDifficulty.HARD, day))
        // A claimed challenge doesn't move any more.
        p = Daily.onCast(p, cast(catch("sun_bream")), day)
        assertEquals(2, p.dailyChallenges.last().progress)
    }

    @Test
    fun applyCastCountsForTheChallengesOfToday() {
        LocalDays.zoneOverride = TimeZone.getTimeZone("UTC")
        ShopClock.override = day * LocalDays.DAY_MS + 10 * LocalDays.HOUR_MS
        try {
            val p = Progression.applyCast(PlayerProgress(), cast(catch("silver_sprat")))
            assertEquals(day, p.challengeDay)
            assertEquals(3, p.dailyChallenges.size)
            val casts = p.dailyChallenges.firstOrNull { it.kind == ChallengeKind.CASTS }
            if (casts != null) assertEquals(1, casts.progress)
            val fish = p.dailyChallenges.firstOrNull { it.kind == ChallengeKind.CATCH_FISH }
            if (fish != null) assertEquals(1, fish.progress)
        } finally {
            LocalDays.zoneOverride = null
            ShopClock.override = null
        }
    }

    @Test
    fun oneFreeRerollADayForAnUnfinishedChallenge() {
        var p = Daily.refresh(PlayerProgress(), day)
        val old = p.dailyChallenges.first { it.difficulty == ChallengeDifficulty.MEDIUM }
        assertEquals(GameConfig.DAILY_FREE_REROLLS, Daily.rerollsLeft(p, day))
        p = Daily.reroll(p, ChallengeDifficulty.MEDIUM, day)!!
        val fresh = p.dailyChallenges.first { it.difficulty == ChallengeDifficulty.MEDIUM }
        assertNotEquals(old.kind, fresh.kind)
        assertEquals(ChallengeDifficulty.MEDIUM, fresh.difficulty)
        assertEquals(3, p.dailyChallenges.map { it.kind }.toSet().size)
        assertEquals(0, Daily.rerollsLeft(p, day))
        assertNull(Daily.reroll(p, ChallengeDifficulty.EASY, day))
        // Rerolls don't carry over, but a new day brings a new one.
        assertEquals(GameConfig.DAILY_FREE_REROLLS, Daily.rerollsLeft(p, day + 1))
        // A finished challenge can't be rerolled.
        val done = Daily.refresh(PlayerProgress(), day).let { q ->
            q.copy(dailyChallenges = q.dailyChallenges.map { if (it.difficulty == ChallengeDifficulty.EASY) it.copy(progress = it.target) else it })
        }
        assertFalse(Daily.canReroll(done, ChallengeDifficulty.EASY, day))
    }

    @Test
    fun aNewDayCollectsFinishedChallengesAndTheClockGoingBackKeepsTodays() {
        var p = Daily.refresh(PlayerProgress(), day)
        p = p.copy(dailyChallenges = p.dailyChallenges.map { if (it.difficulty == ChallengeDifficulty.HARD) it.copy(progress = it.target) else it })
        val r = Daily.rollover(p, day + 1)
        assertEquals(listOf(ChallengeDifficulty.HARD), r.collected.map { it.difficulty })
        assertTrue(r.progress.pointsBalance > p.pointsBalance)
        assertEquals(day + 1, r.progress.challengeDay)
        assertTrue(r.progress.dailyChallenges.none { it.progress > 0 })
        // The clock is set back to yesterday: today's challenges stay (no second set to claim).
        val back = Daily.rollover(r.progress, day)
        assertEquals(r.progress, back.progress)
        assertTrue(back.collected.isEmpty())
    }
}

/** Migrations: the weekly schedule and the spawn boost (solo water only). */
class MigrationTest {
    private val week = 7 * LocalDays.DAY_MS

    @Test
    fun aMigrationEveryTuesdayForTwoDaysBetweenTheWeekendEvents() {
        for (w in 0L until 60L) {
            val m = Migrations.of(w)
            // Seasons.EPOCH_MS is a Monday 00:00 UTC.
            assertEquals(Seasons.EPOCH_MS + w * week + LocalDays.DAY_MS, m.startMs)
            assertEquals(GameConfig.MIGRATION_HOURS * LocalDays.HOUR_MS, m.endMs - m.startMs)
            assertEquals(m, Migrations.at(m.startMs))
            assertEquals(m, Migrations.at(m.endMs - 1))
            assertNull(Migrations.at(m.endMs))
            assertNull(Migrations.at(m.startMs - 1))
            assertEquals(Migrations.of(w + 1), Migrations.currentOrNext(m.endMs))
            // Never during a weekend event.
            for (t in m.startMs until m.endMs step LocalDays.HOUR_MS) assertNull(Seasons.eventAt(t))
            // One species from the upper water, one from the deep, both less common ones.
            assertEquals(2, m.species.size)
            assertTrue(m.species[0].zone in setOf(DepthZone.SHALLOWS, DepthZone.TWILIGHT))
            assertTrue(m.species[1].zone in setOf(DepthZone.MIDNIGHT, DepthZone.ABYSS))
            assertTrue(m.species.all { it.rarity <= GameConfig.MIGRATION_MAX_RARITY && !it.lastBumpOnly })
        }
        // The rotation changes from week to week.
        assertTrue((1L..5L).any { Migrations.of(it).species != Migrations.of(0).species })
    }

    @Test
    fun migratingSpeciesAreFarMoreCommonButTheWaterHoldsAsManyFish() {
        val m = Migrations.of(3)
        val target = m.species[0]
        var normal = 0
        var boosted = 0
        for (seed in 1L..200L) {
            val plain = WorldGenerator.generate(target.maxDepth, Random(seed))
            val more = WorldGenerator.generate(target.maxDepth, Random(seed), boost = m.boost)
            assertEquals(plain.fish.size, more.fish.size)
            assertEquals(plain.obstacles.map { it.kind to it.depth }, more.obstacles.map { it.kind to it.depth })
            normal += plain.fish.count { it.species == target }
            boosted += more.fish.count { it.species == target }
        }
        assertTrue(boosted > 2 * normal.coerceAtLeast(1), "$normal -> $boosted")
        // No boost: the very same water as before migrations existed.
        fun water(w: World) = w.fish.map { it.species.id to it.depth } + w.obstacles.map { it.kind.name to it.depth }
        assertEquals(water(WorldGenerator.generate(400f, Random(9))), water(WorldGenerator.generate(400f, Random(9), boost = null)))
    }
}

/** Reminders: kind and rare. */
class ReminderPlanTest {
    private val day = 20_400L
    private val utc = 0
    private fun at(hour: Int, d: Long = day) = d * LocalDays.DAY_MS + hour * LocalDays.HOUR_MS
    private val on = ReminderSettings(enabled = true)

    /** A player who claimed yesterday (a [streak]-day streak) and not yet today. */
    private fun player(streak: Int) = PlayerProgress(dailyStreak = streak, dailyBestStreak = streak, dailyLastClaimDay = day - 1, dailyClaims = streak)

    // A day with no migration or weekend event, so only daily reminders are in play.
    private val quietDay: Long = (0L..30L).map { day + it }.first { d ->
        (0 until 24).none { h -> Migrations.at(at(h, d)) != null || Seasons.eventAt(at(h, d)) != null }
    }

    @Test
    fun nothingWithoutOptInAtNightOrTwiceADay() {
        val p = player(1).copy(dailyLastClaimDay = quietDay - 1)
        assertNull(DailyReminders.plan(p, ReminderSettings(enabled = false), at(13, quietDay), utc))
        for (h in listOf(0, 3, 8, 21, 23)) assertNull(DailyReminders.plan(p, on, at(h, quietDay), utc), "hour $h")
        assertEquals(ReminderKind.DAILY_READY, DailyReminders.plan(p, on, at(13, quietDay), utc)?.kind)
        assertNull(DailyReminders.plan(p, on.copy(lastSentDay = quietDay), at(19, quietDay), utc))
        // Quiet hours are local: 13:00 UTC is 22:00 in Tokyo.
        val tokyo = TimeZone.getTimeZone("Asia/Tokyo").getOffset(at(13, quietDay))
        assertNull(DailyReminders.plan(p, on, at(13, quietDay), tokyo))
    }

    @Test
    fun dailyReminderOnlyWhenTheRewardWaitsAndTheGameWasntOpenedToday() {
        val p = player(1).copy(dailyLastClaimDay = quietDay - 1)
        assertNull(DailyReminders.plan(p, on, at(10, quietDay), utc))
        assertNull(DailyReminders.plan(p, on.copy(lastOpenDay = quietDay), at(13, quietDay), utc))
        assertNull(DailyReminders.plan(p.copy(dailyLastClaimDay = quietDay), on, at(13, quietDay), utc))
        assertNull(DailyReminders.plan(p, on.copy(dailyReady = false), at(13, quietDay), utc))
    }

    @Test
    fun aStreakWorthKeepingIsRemindedInTheEveningInsteadOfAtNoon() {
        val p = player(5).copy(dailyLastClaimDay = quietDay - 1)
        assertNull(DailyReminders.plan(p, on, at(13, quietDay), utc))
        val r = DailyReminders.plan(p, on, at(GameConfig.NOTIFY_STREAK_FROM_HOUR, quietDay), utc)
        assertEquals(ReminderKind.STREAK_AT_RISK, r?.kind)
        assertEquals(5, r?.streak)
        // With streak reminders off, the daily one is sent instead.
        assertEquals(ReminderKind.DAILY_READY, DailyReminders.plan(p, on.copy(streakAtRisk = false), at(13, quietDay), utc)?.kind)
    }

    @Test
    fun eachEventIsAnnouncedOnceNearItsStart() {
        val m = Migrations.of(Math.floorDiv(at(12) - Seasons.EPOCH_MS, 7 * LocalDays.DAY_MS) + 1)
        val p = Daily.claimReward(PlayerProgress(), LocalDays.dayOf(m.startMs, utc))!!
        val t = m.startMs + 10 * LocalDays.HOUR_MS
        val r = DailyReminders.plan(p, on, t, utc)
        assertEquals(ReminderKind.EVENT_STARTING, r?.kind)
        assertEquals(m.key, r?.eventKey)
        assertNull(DailyReminders.plan(p, on.copy(lastEventKey = m.key), t, utc))
        assertNull(DailyReminders.plan(p, on.copy(events = false), t, utc))
        // A day and more after the start it's old news.
        assertNull(DailyReminders.plan(p, on, m.startMs + 34 * LocalDays.HOUR_MS, utc))
    }
}

/** The daily loop is saved with the rest of the progress. */
class DailyPersistenceTest {
    @Test
    fun streakAndChallengesRoundTrip() {
        val p = Daily.claimReward(PlayerProgress(), 20_400L)!!.let { Daily.refresh(it, 20_400L) }.copy(
            challengeRerolls = 1,
            dailyChallenges = listOf(
                DailyChallenge(ChallengeDifficulty.EASY, ChallengeKind.CATCH_ZONE, 5, zone = DepthZone.TWILIGHT, progress = 2),
                DailyChallenge(ChallengeDifficulty.MEDIUM, ChallengeKind.CATCH_SPECIES, 1, speciesId = "dusk_snapper", progress = 1, claimed = true),
                DailyChallenge(ChallengeDifficulty.HARD, ChallengeKind.EARN_POINTS, 2_600),
            ),
            streakShields = 2,
            dailyBestStreak = 9,
        )
        val q = ProgressCodec.decode(ProgressCodec.encode(p))
        assertEquals(p.dailyStreak, q.dailyStreak)
        assertEquals(p.dailyBestStreak, q.dailyBestStreak)
        assertEquals(p.dailyLastClaimDay, q.dailyLastClaimDay)
        assertEquals(p.dailyClaims, q.dailyClaims)
        assertEquals(p.streakShields, q.streakShields)
        assertEquals(p.challengeDay, q.challengeDay)
        assertEquals(p.challengeRerolls, q.challengeRerolls)
        assertEquals(p.dailyChallenges, q.dailyChallenges)
        assertEquals(p, q)
    }

    @Test
    fun oldSavesAndBrokenLinesStillLoad() {
        val fresh = ProgressCodec.decode("balance=10\n")
        assertNull(fresh.dailyLastClaimDay)
        assertEquals(GameConfig.STARTING_STREAK_SHIELDS, fresh.streakShields)
        assertTrue(fresh.dailyChallenges.isEmpty())
        val broken = ProgressCodec.decode(
            "daily=1,x,3\nchallengeDay=oops\nchallenge.EASY=NOPE,1,,,0,0\nchallenge.HARD=CATCH_SPECIES,1,,no_such_fish,0,0\n" +
                "challenge.MEDIUM=CASTS,3,,,1,0\nchallenge.WHAT=CASTS,3,,,1,0\n",
        )
        assertEquals(0, broken.dailyStreak)
        assertNull(broken.challengeDay)
        assertEquals(listOf(ChallengeDifficulty.MEDIUM), broken.dailyChallenges.map { it.difficulty })
        assertNotNull(broken.dailyChallenges.single().takeIf { it.kind == ChallengeKind.CASTS && it.progress == 1 })
    }
}
