package com.deepline.fishing.core.social

import com.deepline.fishing.core.BotDifficulty
import com.deepline.fishing.core.BotPlayer
import com.deepline.fishing.core.FishCatalog
import com.deepline.fishing.core.GameConfig
import com.deepline.fishing.core.PlayerId
import com.deepline.fishing.core.PlayerProgress
import com.deepline.fishing.core.ProgressCodec
import com.deepline.fishing.core.SeasonGrant
import com.deepline.fishing.core.Seasons
import com.deepline.fishing.core.Tackle
import com.deepline.fishing.core.TitleFrame
import com.deepline.fishing.core.WorldGenerator
import com.deepline.fishing.core.online.CatchDto
import com.deepline.fishing.core.online.toDto
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneOffset
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SocialRulesTest {
    private val day = 86_400_000L

    @Test
    fun weeksResetOnMondayAtMidnightUtc() {
        for (week in listOf(0L, 1L, 45L, 300L)) {
            val start = Instant.ofEpochMilli(Weeks.startOf(week)).atZone(ZoneOffset.UTC)
            assertEquals(DayOfWeek.MONDAY, start.dayOfWeek)
            assertEquals(0, start.hour)
            assertEquals(0, start.minute)
            assertEquals(week, Weeks.indexAt(Weeks.startOf(week)))
            assertEquals(week - 1, Weeks.indexAt(Weeks.startOf(week) - 1))
            assertEquals(week, Weeks.indexAt(Weeks.endOf(week) - 1))
            assertEquals(Weeks.startOf(week + 1), Weeks.endOf(week))
        }
    }

    @Test
    fun everyoneFishesTheSameWaterAllWeek() {
        val week = Weeks.indexAt(Weeks.EPOCH_MS + 40 * 7 * day + 3 * day)
        assertEquals(Tournaments.seedOf(week), Tournaments.seedOf(week))
        assertNotEquals(Tournaments.seedOf(week), Tournaments.seedOf(week + 1))
        // The server's salt keeps the water secret until the week starts.
        assertNotEquals(Tournaments.seedOf(week), Tournaments.seedOf(week, salt = 12345L))
        // The same seed always makes the same water.
        val a = WorldGenerator.generate(Tournaments.specs.lengthM, Random(Tournaments.seedOf(week)))
        val b = WorldGenerator.generate(Tournaments.specs.lengthM, Random(Tournaments.seedOf(week)))
        assertEquals(a.fish.map { it.species.id to it.depth }, b.fish.map { it.species.id to it.depth })
        // The online rules: the standard rope and 4 bumps.
        assertEquals(com.deepline.fishing.core.online.OnlineProtocol.tournamentSpecs, Tournaments.specs)
    }

    @Test
    fun attemptsAreValidatedLikeBattleCastsAndAgainstTheWater() {
        val seed = Tournaments.seedOf(77)
        val real = BotPlayer.playCast(Tournaments.specs, seed, BotDifficulty.HARD)
        assertTrue(real.result.catches.isNotEmpty(), "the bot catches something")
        val dto = real.toDto()
        val ok = assertNotNull(Tournaments.validate(dto, attempt = 1, seed = seed))
        assertEquals(real.points, ok.points)
        // Points are recomputed, never taken from the app.
        val inflated = dto.copy(catches = dto.catches.map { it.copy(points = 99_999) })
        assertEquals(real.points, Tournaments.validate(inflated, 1, seed)!!.points)
        // A fish that isn't in this water (another week's water) is refused...
        assertNull(Tournaments.validate(dto, attempt = 1, seed = Tournaments.seedOf(78)))
        // ...and so is the same fish claimed twice, or with a Sparkle it didn't have.
        val first = dto.catches.first()
        assertNull(Tournaments.validate(dto.copy(catches = dto.catches + first), 1, seed))
        assertNull(Tournaments.validate(dto.copy(catches = listOf(first.copy(sparkling = !first.sparkling))), 1, seed))
        // Battle rules still apply: a deep fish in the shallows is impossible.
        assertNull(Tournaments.validate(dto.copy(catches = listOf(CatchDto("pale_leviathan", false, 20f))), 1, seed))
    }

    @Test
    fun rewardsGoByFinalRank() {
        assertEquals(TournamentTier.CHAMPION, Tournaments.tierFor(1, 1))
        assertEquals(TournamentTier.CHAMPION, Tournaments.tierFor(1, 1000))
        assertEquals(TournamentTier.CHAMPION, Tournaments.tierFor(10, 1000))
        assertEquals(TournamentTier.GOLD, Tournaments.tierFor(11, 1000))
        assertEquals(TournamentTier.GOLD, Tournaments.tierFor(100, 1000))
        assertEquals(TournamentTier.SILVER, Tournaments.tierFor(101, 1000))
        assertEquals(TournamentTier.BRONZE, Tournaments.tierFor(500, 1000))
        assertEquals(TournamentTier.TOOK_PART, Tournaments.tierFor(501, 1000))
        assertEquals(TournamentTier.TOOK_PART, Tournaments.tierFor(1000, 1000))
        // Small fields: the winner is the champion; second of three is in the top half.
        assertEquals(TournamentTier.TOOK_PART, Tournaments.tierFor(2, 2))
        assertEquals(TournamentTier.BRONZE, Tournaments.tierFor(2, 3))
        assertNull(Tournaments.tierFor(0, 10))
        assertNull(Tournaments.tierFor(11, 10))
        // Better ranks never get less.
        val order = TournamentTier.entries
        var last = 0
        for (rank in 1..300) {
            val i = order.indexOf(Tournaments.tierFor(rank, 300)!!)
            assertTrue(i >= last)
            last = i
        }
    }

    @Test
    fun aRewardIsGivenOnceAndOnlyAfterTheWeek() {
        val now = Weeks.startOf(50) + 2 * day
        assertFalse(Tournaments.isClaimable(50, now), "still running")
        assertTrue(Tournaments.isClaimable(49, now))
        assertFalse(Tournaments.isClaimable(50 - GameConfig.TOURNAMENT_CLAIM_WEEKS - 1L, now), "too old")
        val p = PlayerProgress()
        val won = assertNotNull(Tournaments.grant(p, 49, TournamentTier.GOLD, now))
        assertEquals(p.lanterns + 2, won.lanterns)
        assertTrue(won.pointsBalance > 0)
        assertTrue(TitleFrame.TOURNAMENT.isAvailable(won, now), "the tournament badge frame")
        assertNull(Tournaments.grant(won, 49, TournamentTier.GOLD, now), "never twice")
        // Remembered in the save.
        assertEquals(setOf(49L), ProgressCodec.decode(ProgressCodec.encode(won)).tournamentClaims)
    }

    @Test
    fun socialRewardsStaySmallerThanAWeekendEvent() {
        // Tournaments and clubs are for fun and bragging rights: a week's best is under the weekend
        // event's reward, so the career simulation's pacing (EconomyTest) holds for players who join.
        fun minutes(g: List<SeasonGrant>) = g.filterIsInstance<SeasonGrant.Points>().sumOf { it.minutes }
        fun lanterns(g: List<SeasonGrant>) = g.filterIsInstance<SeasonGrant.Tackle>().filter { it.tackle == Tackle.LANTERN }.sumOf { it.count }
        val event = Seasons.eventFree
        for (tier in TournamentTier.entries) {
            assertTrue(minutes(tier.grants) <= minutes(event))
            assertTrue(lanterns(tier.grants) <= lanterns(event))
        }
        val clubWeek = (1..GameConfig.CLUB_GOAL_TIERS.size).flatMap { Clubs.rewardOf(it) }
        assertTrue(minutes(clubWeek) <= minutes(event))
        assertTrue(lanterns(clubWeek) <= lanterns(event))
    }

    @Test
    fun leaderboardValuesComeFromTheCast() {
        val cast = BotPlayer.playCast(Tournaments.specs, Tournaments.seedOf(3), BotDifficulty.HARD).result
        val v = Leaderboards.valuesOf(cast)
        assertEquals(cast.totalPoints.toLong(), v[BoardKind.BEST_CAST]!!.value)
        val deepest = cast.catches.maxBy { it.depth }
        assertEquals(deepest.species.id, v[BoardKind.DEEPEST]!!.speciesId)
        assertEquals(deepest.depth, Leaderboards.depthOf(v[BoardKind.DEEPEST]!!.value), 0.01f)
        // Rarer species rank higher whatever the depth; depth breaks ties.
        val common = FishCatalog.byId("silver_sprat")!!
        val rare = FishCatalog.byId("veil_ray")!!
        assertTrue(Leaderboards.rarityValue(rare, 180f) > Leaderboards.rarityValue(common, 80f))
        assertTrue(Leaderboards.rarityValue(common, 80f) > Leaderboards.rarityValue(common, 20f))
    }

    @Test
    fun strangersSeeAGeneratedNameNeverTheTypedOne() {
        val who = PlayerId.random()
        val name = Leaderboards.anglerName(who.id)
        assertEquals(name, Leaderboards.anglerName(who.id))
        assertTrue(name.endsWith(who.id.takeLast(2)))
        assertFalse(who.id in name)
        assertEquals(3, name.split(' ').size)
    }

    @Test
    fun profilesKeepOnlyWhatTheGameKnows() {
        val clean = Profile("  Bob\u0007 the very long angler name ", title = "Made-up title", titleFrame = "NOPE").clean()
        assertEquals(com.deepline.fishing.core.online.OnlineProtocol.MAX_NAME_LENGTH, clean.name.length)
        assertNull(clean.title)
        assertNull(clean.titleFrame)
        val real = Profile("Ann", "Giant-slayer", "OCEAN").clean()
        assertEquals("Giant-slayer", real.title)
        assertEquals("OCEAN", real.titleFrame)
    }

    @Test
    fun clubNamesAreGeneratedAndCodesForgiving() {
        assertEquals("Brave Anglers", Clubs.name(0, 0))
        assertNull(Clubs.name(-1, 0))
        assertNull(Clubs.name(0, Clubs.nouns.size))
        val picks = Clubs.suggestions(Random(1))
        assertEquals(3, picks.toSet().size)
        assertTrue(picks.all { (a, n) -> Clubs.name(a, n) != null })
        val code = Clubs.randomCode(Random(2))
        assertTrue(Clubs.isValidCode(code))
        assertEquals(code, Clubs.normalizeCode(" ${code.lowercase().chunked(3).joinToString("-")} "))
        assertFalse(Clubs.isValidCode("ABC"))
        // Tiers of the weekly goal.
        assertEquals(0, Clubs.tiersReached(GameConfig.CLUB_GOAL_TIERS[0] - 1))
        assertEquals(1, Clubs.tiersReached(GameConfig.CLUB_GOAL_TIERS[0]))
        assertEquals(GameConfig.CLUB_GOAL_TIERS.size, Clubs.tiersReached(100_000))
        // A tier's reward is given once per phone.
        val now = Weeks.startOf(60)
        val p = assertNotNull(Clubs.grant(PlayerProgress(), 60, 2, now))
        assertEquals(PlayerProgress().lanterns + 1, p.lanterns)
        assertEquals(1, p.detectors)
        assertNull(Clubs.grant(p, 60, 2, now))
        assertNull(Clubs.grant(p, 60, 9, now))
        assertEquals(setOf("60:2"), ProgressCodec.decode(ProgressCodec.encode(p)).clubClaims)
    }
}
