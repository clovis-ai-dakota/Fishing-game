package com.deepline.fishing.server

import com.deepline.fishing.core.BattleMode
import com.deepline.fishing.core.BotDifficulty
import com.deepline.fishing.core.BotPlayer
import com.deepline.fishing.core.GameConfig
import com.deepline.fishing.core.PlayerId
import com.deepline.fishing.core.online.CatchDto
import com.deepline.fishing.core.online.ClientMessage
import com.deepline.fishing.core.online.OnlineMatch
import com.deepline.fishing.core.online.OnlineProtocol
import com.deepline.fishing.core.online.ServerMessage
import com.deepline.fishing.core.online.toDto
import com.deepline.fishing.core.social.AttemptStart
import com.deepline.fishing.core.social.BoardKind
import com.deepline.fishing.core.social.BoardRequest
import com.deepline.fishing.core.social.BoardResponse
import com.deepline.fishing.core.social.BoardView
import com.deepline.fishing.core.social.ClubRole
import com.deepline.fishing.core.social.Clubs
import com.deepline.fishing.core.social.FriendsResponse
import com.deepline.fishing.core.social.Leaderboards
import com.deepline.fishing.core.social.Profile
import com.deepline.fishing.core.social.SocialApi
import com.deepline.fishing.core.social.SocialError
import com.deepline.fishing.core.social.SocialProblem
import com.deepline.fishing.core.social.SocialRequest
import com.deepline.fishing.core.social.SubmitRequest
import com.deepline.fishing.core.social.TournamentTier
import com.deepline.fishing.core.social.Tournaments
import com.deepline.fishing.core.social.Weeks
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocketSession
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SocialTest {
    private val day = 86_400_000L
    private val week = 100L
    private var now = Weeks.startOf(week) + 2 * day
    private val accounts = AccountStore()
    private fun store(dir: java.io.File? = null, clubs: Boolean = true, salt: Long = 0L) =
        SocialStore(accounts, dir, clock = { now }, tournamentSalt = salt, clubsEnabled = clubs, random = kotlin.random.Random(7))

    private val alice = PlayerId.random()
    private val bob = PlayerId.random()
    private val carol = PlayerId.random()

    private fun SocialStore.hello(who: PlayerId, name: String) = friends(who.id, who.secret, Profile(name))

    private fun problem(block: () -> Unit): SocialProblem = assertFailsWith<SocialFailure> { block() }.problem

    /** Deleting the account through the API (as Settings > Privacy does) erases the social data too. */
    @Test
    fun deletingTheAccountThroughTheApiForgetsTheFriends() = testApplication {
        val social = store()
        application { battleServer(Lobby(), accounts, social) }
        social.hello(alice, "Alice")
        social.hello(bob, "Bob")
        social.requestFriend(alice.id, alice.secret, bob.id)
        social.acceptFriend(bob.id, bob.secret, alice.id)
        assertEquals(listOf(alice.id), social.hello(bob, "Bob").friends.map { it.id })
        val body = Json.encodeToString(
            com.deepline.fishing.core.online.AuthRequest.serializer(),
            com.deepline.fishing.core.online.AuthRequest(alice.id, alice.secret),
        )
        val response = client.post(com.deepline.fishing.core.online.AccountApi.DELETE_ACCOUNT_PATH) { setBody(body) }
        assertEquals(HttpStatusCode.NoContent, response.status)
        assertTrue(social.hello(bob, "Bob").friends.isEmpty(), "the deleted player is no longer Bob's friend")
    }

    @Test
    fun friendRequestsNeedAnAnswer() {
        val s = store()
        s.hello(alice, "Alice")
        s.hello(bob, "Bob")
        // An id nobody uses can't be asked; nor can yourself.
        assertEquals(SocialProblem.UNKNOWN_PLAYER, problem { s.requestFriend(alice.id, alice.secret, PlayerId.random().id) })
        assertEquals(SocialProblem.BAD_REQUEST, problem { s.requestFriend(alice.id, alice.secret, alice.id) })
        assertEquals(SocialProblem.BAD_REQUEST, problem { s.requestFriend(alice.id, alice.secret, "not an id") })
        assertEquals(SocialProblem.WRONG_SECRET, problem { s.requestFriend(alice.id, bob.secret, bob.id) })

        s.requestFriend(alice.id, alice.secret, bob.id)
        assertEquals(listOf(bob.id), s.hello(alice, "Alice").outgoing.map { it.id })
        val bobs = s.hello(bob, "Bob")
        assertEquals(listOf("Alice"), bobs.incoming.map { it.name })
        assertTrue(bobs.friends.isEmpty(), "not friends until Bob accepts")

        s.acceptFriend(bob.id, bob.secret, alice.id)
        val list = s.hello(alice, "Alice")
        assertEquals(listOf("Bob"), list.friends.map { it.name })
        assertTrue(list.friends.single().online)
        assertTrue(list.outgoing.isEmpty())

        // Removing is silent and two-sided.
        s.removeFriend(bob.id, bob.secret, alice.id)
        assertTrue(s.hello(alice, "Alice").friends.isEmpty())
        assertTrue(s.hello(bob, "Bob").friends.isEmpty())
    }

    @Test
    fun askingBackMakesFriendsAndDecliningIsSilent() {
        val s = store()
        for ((p, n) in listOf(alice to "Alice", bob to "Bob", carol to "Carol")) s.hello(p, n)
        s.requestFriend(alice.id, alice.secret, bob.id)
        s.requestFriend(bob.id, bob.secret, alice.id)
        assertEquals(listOf(bob.id), s.hello(alice, "Alice").friends.map { it.id })
        // Carol asks Alice, Alice declines: Carol sees no answer but the request is gone for Alice.
        s.requestFriend(carol.id, carol.secret, alice.id)
        s.declineFriend(alice.id, alice.secret, carol.id)
        assertTrue(s.hello(alice, "Alice").incoming.isEmpty())
        assertTrue(s.hello(carol, "Carol").outgoing.isEmpty())
        // Accepting a request that doesn't exist fails.
        assertEquals(SocialProblem.UNKNOWN_PLAYER, problem { s.acceptFriend(alice.id, alice.secret, carol.id) })
    }

    @Test
    fun friendRequestsAreRateLimitedAndListsCapped() {
        val s = store()
        s.hello(alice, "Alice")
        val others = List(GameConfig.FRIEND_REQUESTS_PER_DAY + 1) { PlayerId.random().also { p -> s.hello(p, "P") } }
        for (p in others.take(GameConfig.FRIEND_REQUESTS_PER_DAY)) s.requestFriend(alice.id, alice.secret, p.id)
        assertEquals(SocialProblem.TOO_MANY_REQUESTS, problem { s.requestFriend(alice.id, alice.secret, others.last().id) })
        // A day later Alice can ask again.
        now += day + 1
        s.requestFriend(alice.id, alice.secret, others.last().id)
        // A player can't be flooded with requests.
        val target = PlayerId.random().also { s.hello(it, "Popular") }
        repeat(GameConfig.MAX_FRIEND_REQUESTS) { PlayerId.random().also { p -> s.hello(p, "Fan"); s.requestFriend(p.id, p.secret, target.id) } }
        val late = PlayerId.random().also { s.hello(it, "Late") }
        assertEquals(SocialProblem.THEIR_FRIENDS_FULL, problem { s.requestFriend(late.id, late.secret, target.id) })
    }

    @Test
    fun friendsCanBeChallengedToABattleRoom() {
        val s = store()
        s.hello(alice, "Alice")
        s.hello(bob, "Bob")
        assertEquals(SocialProblem.NOT_FRIENDS, problem { s.challenge(alice.id, alice.secret, bob.id, "ABCDE", BattleMode.HEAVY_HAUL) })
        s.requestFriend(alice.id, alice.secret, bob.id)
        s.acceptFriend(bob.id, bob.secret, alice.id)
        assertEquals(SocialProblem.BAD_REQUEST, problem { s.challenge(alice.id, alice.secret, bob.id, "AB", BattleMode.HEAVY_HAUL) })
        s.challenge(alice.id, alice.secret, bob.id, "abcde", BattleMode.HEAVY_HAUL)
        val c = s.hello(bob, "Bob").challenges.single()
        assertEquals("ABCDE", c.code)
        assertEquals("Alice", c.fromName)
        assertEquals(BattleMode.HEAVY_HAUL, c.mode)
        // Challenges expire.
        now += GameConfig.CHALLENGE_MINUTES * 60_000L + 1
        assertTrue(s.hello(bob, "Bob").challenges.isEmpty())
    }

    /** Plays a real attempt (the bot) on the week's water and submits it. */
    private fun SocialStore.play(who: PlayerId, difficulty: BotDifficulty = BotDifficulty.HARD, salt: Long = 0L): Int {
        val start = startAttempt(who.id, who.secret)
        assertEquals(Tournaments.seedOf(start.week, salt), start.seed)
        val cast = BotPlayer.playCast(Tournaments.specs, start.seed, difficulty)
        now += (cast.durationSec * 1000).toLong() + 1_000
        return submitAttempt(who.id, who.secret, start.week, start.attempt, cast.toDto()).points
    }

    @Test
    fun theTournamentIsTheSameWaterWithLimitedAttempts() {
        val s = store(salt = 99L)
        s.hello(alice, "Alice")
        val info = s.tournament(alice.id, alice.secret)
        assertEquals(week, info.week)
        assertEquals(0, info.attemptsUsed)
        assertEquals(Weeks.endOf(week), info.endsAtMs)
        val points = s.play(alice, salt = 99L)
        assertTrue(points > 0)
        val after = s.tournament(alice.id, alice.secret)
        assertEquals(1, after.attemptsUsed)
        assertEquals(points, after.best)
        assertEquals(1, after.rank)
        // Starting an attempt uses it, even if nothing is sent back.
        repeat(GameConfig.TOURNAMENT_ATTEMPTS - 1) { s.startAttempt(alice.id, alice.secret) }
        assertEquals(SocialProblem.NO_ATTEMPTS_LEFT, problem { s.startAttempt(alice.id, alice.secret) })
        assertEquals(points, s.tournament(alice.id, alice.secret).best, "quitting never lowers the best")
        // The tournament also fills the leaderboards.
        assertEquals(points.toLong(), s.leaderboard(alice.id, alice.secret, BoardKind.BEST_CAST, BoardView.GLOBAL).me!!.value)
    }

    @Test
    fun attemptsAreCheckedAgainstTheWaterAndTheClock() {
        val s = store()
        s.hello(alice, "Alice")
        val start = s.startAttempt(alice.id, alice.secret)
        val real = BotPlayer.playCast(Tournaments.specs, start.seed, BotDifficulty.HARD)
        // Sent back faster than the cast could have been fished: refused, and the attempt stays at 0.
        assertEquals(SocialProblem.CAST_REJECTED, problem { s.submitAttempt(alice.id, alice.secret, start.week, start.attempt, real.toDto()) })
        assertEquals(SocialProblem.ATTEMPT_EXPIRED, problem { s.submitAttempt(alice.id, alice.secret, start.week, start.attempt, real.toDto()) })
        // A fish that isn't in this week's water.
        val second = s.startAttempt(alice.id, alice.secret)
        now += 60_000
        val fake = real.toDto().copy(catches = listOf(CatchDto("silver_sprat", false, 20f)))
        assertEquals(SocialProblem.CAST_REJECTED, problem { s.submitAttempt(alice.id, alice.secret, second.week, second.attempt, fake) })
        // Too late.
        val third = s.startAttempt(alice.id, alice.secret)
        now += GameConfig.TOURNAMENT_ATTEMPT_MINUTES * 60_000L + 1
        assertEquals(SocialProblem.ATTEMPT_EXPIRED, problem { s.submitAttempt(alice.id, alice.secret, third.week, third.attempt, real.toDto()) })
        // Points are recomputed by the server.
        val fourth = s.startAttempt(alice.id, alice.secret)
        now += 120_000
        val inflated = real.toDto().copy(catches = real.toDto().catches.map { it.copy(points = 99_999) })
        assertEquals(real.points, s.submitAttempt(alice.id, alice.secret, fourth.week, fourth.attempt, inflated).points)
    }

    @Test
    fun rewardsByFinalRankAreClaimedOnceAfterTheWeek() {
        val s = store()
        val players = List(4) { PlayerId.random().also { p -> s.hello(p, "P$it") } }
        val scores = players.map { s.play(it, BotDifficulty.entries[it.hashCode().mod(3)]) }
        assertTrue(s.tournament(players[0].id, players[0].secret).rewards.isEmpty(), "nothing until the week is over")
        // Monday 00:00 UTC: a new tournament, a new leaderboard.
        now = Weeks.startOf(week + 1) + 1
        val next = s.tournament(players[0].id, players[0].secret)
        assertEquals(week + 1, next.week)
        assertEquals(0, next.participants)
        assertNull(s.leaderboard(players[0].id, players[0].secret, BoardKind.BEST_CAST, BoardView.GLOBAL).me)
        val ranking = players.indices.sortedByDescending { scores[it] }
        val winner = players[ranking.first()]
        val reward = s.tournament(winner.id, winner.secret).rewards.single()
        assertEquals(week, reward.week)
        assertEquals(1, reward.rank)
        assertEquals(4, reward.participants)
        assertEquals(TournamentTier.CHAMPION, reward.tier)
        s.claimTournament(winner.id, winner.secret, week)
        assertTrue(s.tournament(winner.id, winner.secret).rewards.isEmpty(), "claimed once")
        // The last of four took part.
        val last = players[ranking.last()]
        assertEquals(TournamentTier.TOOK_PART, s.tournament(last.id, last.secret).rewards.single().tier)
        // Too old to claim.
        now = Weeks.startOf(week + GameConfig.TOURNAMENT_CLAIM_WEEKS + 1) + 1
        assertTrue(s.tournament(last.id, last.secret).rewards.isEmpty())
    }

    @Test
    fun leaderboardsShowFriendsByNameAndStrangersByAnglerName() {
        val s = store()
        s.hello(alice, "Alice")
        s.hello(bob, "Bob")
        s.hello(carol, "Carol")
        s.requestFriend(alice.id, alice.secret, bob.id)
        s.acceptFriend(bob.id, bob.secret, alice.id)
        for (p in listOf(alice, bob, carol)) s.play(p)
        val global = s.leaderboard(alice.id, alice.secret, BoardKind.BEST_CAST, BoardView.GLOBAL)
        assertEquals(3, global.players)
        assertEquals((1..3).toList(), global.entries.map { it.rank })
        assertTrue(global.entries.zipWithNext().all { (a, b) -> a.value >= b.value })
        val carolRow = global.entries.single { !it.you && !it.friend }
        assertEquals(Leaderboards.anglerName(carol.id), carolRow.name)
        assertNull(carolRow.id, "strangers' ids aren't shown")
        assertEquals("Bob", global.entries.single { it.friend }.name)
        assertEquals("Alice", global.me!!.name)
        // Friends: just Alice and Bob.
        val friends = s.leaderboard(alice.id, alice.secret, BoardKind.DEEPEST, BoardView.FRIENDS)
        assertEquals(setOf(alice.id, bob.id), friends.entries.mapNotNull { it.id }.toSet())
        assertNotNull(friends.entries.first().speciesId)
        // A league holds the first players of the week.
        val league = s.leaderboard(carol.id, carol.secret, BoardKind.RAREST, BoardView.LEAGUE)
        assertEquals(3, league.players)
        // The friends list shows this week's scores.
        val bobAsFriend = s.hello(alice, "Alice").friends.single()
        assertNotNull(bobAsFriend.weekBestCast)
        assertNotNull(bobAsFriend.weekDeepestM)
        assertNotNull(bobAsFriend.weekTournament)
    }

    @Test
    fun clubsJoinByCodeWithACapAndRoles() {
        val s = store()
        s.hello(alice, "Alice")
        s.hello(bob, "Bob")
        s.hello(carol, "Carol")
        assertEquals(SocialProblem.BAD_REQUEST, problem { s.createClub(alice.id, alice.secret, -1, 0, null) })
        val club = s.createClub(alice.id, alice.secret, 3, 4, null).club!!
        assertEquals(Clubs.name(3, 4), club.name)
        assertEquals(ClubRole.OWNER, club.members.single().role)
        assertEquals(SocialProblem.ALREADY_IN_CLUB, problem { s.createClub(alice.id, alice.secret, 1, 1, null) })
        assertEquals(SocialProblem.UNKNOWN_CLUB, problem { s.joinClub(bob.id, bob.secret, "ZZZZZZ", null) })
        val joined = s.joinClub(bob.id, bob.secret, club.code.lowercase(), Profile("Bob")).club!!
        assertEquals(2, joined.members.size)
        s.joinClub(carol.id, carol.secret, club.code, null)
        // Only the owner manages the club.
        assertEquals(SocialProblem.NOT_OWNER, problem { s.kick(bob.id, bob.secret, carol.id) })
        s.kick(alice.id, alice.secret, carol.id)
        assertNull(s.myClub(carol.id, carol.secret, null).club)
        assertEquals(SocialProblem.REJOIN_LATER, problem { s.joinClub(carol.id, carol.secret, club.code, null) })
        now += GameConfig.CLUB_REJOIN_HOURS * 3_600_000L + 1
        s.joinClub(carol.id, carol.secret, club.code, null)
        // Hand over, then the new owner leaves: the longest-standing member takes over.
        s.transfer(alice.id, alice.secret, bob.id)
        assertEquals(bob.id, s.myClub(alice.id, alice.secret, null).club!!.members.single { it.role == ClubRole.OWNER }.id)
        s.leaveClub(bob.id, bob.secret)
        assertEquals(alice.id, s.myClub(carol.id, carol.secret, null).club!!.members.single { it.role == ClubRole.OWNER }.id)
        s.renameClub(alice.id, alice.secret, 5, 6)
        assertEquals(Clubs.name(5, 6), s.myClub(carol.id, carol.secret, null).club!!.name)
        // The last one out closes the club.
        s.leaveClub(carol.id, carol.secret)
        s.leaveClub(alice.id, alice.secret)
        assertEquals(SocialProblem.UNKNOWN_CLUB, problem { s.joinClub(bob.id, bob.secret, club.code, null) })
        // Full clubs refuse newcomers.
        val owner = PlayerId.random().also { s.hello(it, "Owner") }
        val big = s.createClub(owner.id, owner.secret, 0, 0, null).club!!
        repeat(GameConfig.CLUB_MAX_MEMBERS - 1) { PlayerId.random().also { p -> s.joinClub(p.id, p.secret, big.code, null) } }
        assertEquals(SocialProblem.CLUB_FULL, problem { s.joinClub(bob.id, bob.secret, big.code, null) })
    }

    @Test
    fun theWeeklyClubGoalIsSharedAndCapped() {
        val s = store()
        val club = s.createClub(alice.id, alice.secret, 0, 0, null).club!!
        s.joinClub(bob.id, bob.secret, club.code, null)
        s.joinClub(carol.id, carol.secret, club.code, null)
        assertEquals(SocialProblem.BAD_REQUEST, problem { s.contribute(alice.id, alice.secret, -5) })
        s.contribute(alice.id, alice.secret, 60)
        s.contribute(bob.id, bob.secret, 50)
        var mine = s.myClub(alice.id, alice.secret, null).club!!
        assertEquals(110, mine.weekFish)
        assertEquals(1, mine.tiersReached)
        assertEquals(alice.id, mine.members.first().id, "the best contributor leads the club board")
        assertEquals(listOf(1), mine.claimable.map { it.tier })
        // Carol didn't help: nothing to claim.
        assertTrue(s.myClub(carol.id, carol.secret, null).club!!.claimable.isEmpty())
        // A day's contributions are capped.
        s.contribute(bob.id, bob.secret, GameConfig.CLUB_FISH_PER_MEMBER_PER_DAY)
        mine = s.myClub(alice.id, alice.secret, null).club!!
        assertEquals(60 + GameConfig.CLUB_FISH_PER_MEMBER_PER_DAY, mine.weekFish)
        assertEquals(2, mine.tiersReached)
        s.claimClub(alice.id, alice.secret, mine.claimable.map { it.key })
        assertTrue(s.myClub(alice.id, alice.secret, null).club!!.claimable.isEmpty())
        val top = s.topClubs(alice.id, alice.secret)
        assertTrue(top.clubs.first().mine)
        // A new week, a new goal.
        now = Weeks.startOf(week + 1) + 1
        assertEquals(0, s.myClub(alice.id, alice.secret, null).club!!.weekFish)
    }

    @Test
    fun clubsCanBeSwitchedOffOnTheServer() {
        val s = store(clubs = false)
        assertEquals(SocialProblem.CLUBS_OFF, problem { s.myClub(alice.id, alice.secret, null) })
        assertEquals(SocialProblem.CLUBS_OFF, problem { s.createClub(alice.id, alice.secret, 0, 0, null) })
    }

    @Test
    fun everythingSurvivesARestartAndCanBeForgotten() {
        val dir = Files.createTempDirectory("social").toFile()
        val s = store(dir)
        s.hello(alice, "Alice")
        s.hello(bob, "Bob")
        s.requestFriend(alice.id, alice.secret, bob.id)
        s.acceptFriend(bob.id, bob.secret, alice.id)
        val points = s.play(alice)
        val club = s.createClub(alice.id, alice.secret, 2, 2, null).club!!
        s.joinClub(bob.id, bob.secret, club.code, null)
        s.contribute(alice.id, alice.secret, 10)
        // A new server process reads the files back.
        val again = store(dir)
        assertEquals(listOf("Bob"), again.hello(alice, "Alice").friends.map { it.name })
        assertEquals(points, again.tournament(alice.id, alice.secret).best)
        assertEquals(10, again.myClub(bob.id, bob.secret, null).club!!.weekFish)
        // Alice deletes her social data: Bob loses a friend and becomes the club's owner.
        again.forget(alice.id, alice.secret)
        assertTrue(again.hello(bob, "Bob").friends.isEmpty())
        val bobsClub = again.myClub(bob.id, bob.secret, null).club!!
        assertEquals(ClubRole.OWNER, bobsClub.members.single().role)
        assertEquals(0, bobsClub.weekFish)
        assertNull(again.leaderboard(bob.id, bob.secret, BoardKind.BEST_CAST, BoardView.GLOBAL).entries.firstOrNull { it.name == "Alice" })
        assertNull(again.tournament(alice.id, alice.secret).best)
        assertFalse(java.io.File(dir, "players/${alice.id}.json").exists() && store(dir).hello(alice, "A").friends.isNotEmpty())
        dir.deleteRecursively()
    }

    /**
     * The account store calls the social store when an account is deleted, and every social call
     * checks the secret with the account store: deleting accounts while others use social calls
     * must never leave the two waiting for each other (which would hang the whole server).
     */
    @Test
    fun deletingAccountsWhileOthersUseSocialCallsNeverDeadlocks() {
        val social = store()
        accounts.onDelete(social::forgetPlayer)
        social.hello(bob, "Bob")
        val stop = java.util.concurrent.atomic.AtomicBoolean(false)
        val reader = kotlin.concurrent.thread(isDaemon = true) { while (!stop.get()) social.hello(bob, "Bob") }
        val deleter = kotlin.concurrent.thread(isDaemon = true) {
            repeat(5_000) {
                val p = PlayerId.random()
                accounts.authenticate(p.id, p.secret)
                accounts.delete(p.id, p.secret)
            }
        }
        deleter.join(20_000)
        stop.set(true)
        reader.join(5_000)
        assertFalse(deleter.isAlive || reader.isAlive, "account deletion and a social call deadlocked")
    }

    @Test
    fun accountDeletionErasesEverySocialTrace() {
        val dir = Files.createTempDirectory("social-delete").toFile()
        val dave = PlayerId.random()
        val s = store(dir)
        for ((p, n) in listOf(alice to "Alice", bob to "Bob", carol to "Carol", dave to "Dave")) s.hello(p, n)
        // Last week's tournament, still claimable.
        s.play(alice)
        s.play(bob)
        now = Weeks.startOf(week + 1) + 2 * day
        // This week: a friend, requests both ways, a challenge, scores and a club Alice owns.
        s.requestFriend(alice.id, alice.secret, bob.id)
        s.acceptFriend(bob.id, bob.secret, alice.id)
        s.requestFriend(carol.id, carol.secret, alice.id)
        s.requestFriend(alice.id, alice.secret, dave.id)
        s.challenge(alice.id, alice.secret, bob.id, "ABCDE", BattleMode.DEEP_DIVE)
        s.play(alice)
        s.play(bob)
        val club = s.createClub(alice.id, alice.secret, 1, 2, null).club!!
        now += 1_000
        s.joinClub(bob.id, bob.secret, club.code, null)
        now += 1_000
        s.joinClub(carol.id, carol.secret, club.code, null)
        s.contribute(alice.id, alice.secret, 40)
        s.contribute(bob.id, bob.secret, 15)
        assertTrue(dir.walkTopDown().filter { it.isFile }.any { alice.id in it.readText() })

        // Deleting the account (no secret needed: the account store already checked it), from a
        // fresh server process, so nothing is in memory and every file has to be swept.
        val again = store(dir)
        again.forgetPlayer(alice.id)
        val left = dir.walkTopDown().filter { it.isFile && alice.id in it.readText() }.map { it.relativeTo(dir).path }.toList()
        assertEquals(emptyList(), left, "no file mentions the deleted player")
        val bobs = again.hello(bob, "Bob")
        assertTrue(bobs.friends.isEmpty())
        assertTrue(bobs.challenges.isEmpty())
        assertTrue(again.hello(carol, "Carol").outgoing.isEmpty())
        assertTrue(again.hello(dave, "Dave").incoming.isEmpty())
        // The club passes to the longest-standing member, without Alice's fish.
        val c = again.myClub(carol.id, carol.secret, null).club!!
        assertEquals(setOf(bob.id, carol.id), c.members.map { it.id }.toSet())
        assertEquals(bob.id, c.members.single { it.role == ClubRole.OWNER }.id)
        assertEquals(15, c.weekFish)
        // Leaderboards and tournaments, this week and last: Bob moves up.
        val board = again.leaderboard(bob.id, bob.secret, BoardKind.BEST_CAST, BoardView.GLOBAL)
        assertEquals(listOf(true), board.entries.map { it.you })
        val t = again.tournament(bob.id, bob.secret)
        assertEquals(1, t.participants)
        assertEquals(1, t.rank)
        val lastWeek = t.rewards.single()
        assertEquals(1, lastWeek.participants)
        assertEquals(TournamentTier.CHAMPION, lastWeek.tier)
        // Ids that aren't player ids (they'd be file names) are ignored; unknown players are harmless.
        again.forgetPlayer("../../etc")
        again.forgetPlayer(PlayerId.random().id)
        // The id starts afresh if it's used again.
        assertTrue(again.hello(alice, "Alice").friends.isEmpty())
        assertNull(again.tournament(alice.id, alice.secret).best)
        dir.deleteRecursively()
    }

    @Test
    fun rateLimitsUseASlidingWindow() {
        var t = 0L
        val limiter = RateLimiter { t }
        repeat(3) { assertTrue(limiter.allow("k", 3, 1_000)) }
        assertFalse(limiter.allow("k", 3, 1_000))
        assertTrue(limiter.allow("other", 3, 1_000), "each key has its own count")
        t = 999
        assertFalse(limiter.allow("k", 3, 1_000))
        t = 1_000
        assertTrue(limiter.allow("k", 3, 1_000), "the oldest action has left the window")
    }

    // ---- Over HTTP and WebSocket ----------------------------------------------------------------------

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun theHttpApi() = testApplication {
        val social = store()
        application { battleServer(Lobby(), accounts, social) }
        suspend fun post(path: String, body: String) = client.post(path) { setBody(body) }
        fun req(who: PlayerId, target: String? = null, name: String? = null) =
            json.encodeToString(SocialRequest.serializer(), SocialRequest(who.id, who.secret, name?.let { Profile(it) }, target))

        assertEquals(HttpStatusCode.OK, post(SocialApi.FRIENDS_PATH, req(alice, name = "Alice")).status)
        assertEquals(HttpStatusCode.OK, post(SocialApi.FRIENDS_PATH, req(bob, name = "Bob")).status)
        assertEquals(HttpStatusCode.NoContent, post(SocialApi.FRIEND_REQUEST_PATH, req(alice, bob.id)).status)
        assertEquals(HttpStatusCode.NoContent, post(SocialApi.FRIEND_ACCEPT_PATH, req(bob, alice.id)).status)
        val list = json.decodeFromString(FriendsResponse.serializer(), post(SocialApi.FRIENDS_PATH, req(alice)).bodyAsText())
        assertEquals("Bob", list.friends.single().name)
        // Errors name their problem.
        val wrong = post(SocialApi.FRIENDS_PATH, json.encodeToString(SocialRequest.serializer(), SocialRequest(alice.id, bob.secret)))
        assertEquals(HttpStatusCode.Forbidden, wrong.status)
        assertEquals(SocialProblem.WRONG_SECRET, json.decodeFromString(SocialError.serializer(), wrong.bodyAsText()).problem)
        assertEquals(HttpStatusCode.BadRequest, post(SocialApi.FRIENDS_PATH, "{nope").status)
        assertEquals(HttpStatusCode.PayloadTooLarge, post(SocialApi.FRIENDS_PATH, "x".repeat(SocialApi.MAX_BODY_BYTES + 1)).status)
        // A tournament attempt over HTTP.
        val start = json.decodeFromString(AttemptStart.serializer(), post(SocialApi.TOURNAMENT_START_PATH, req(alice)).bodyAsText())
        val cast = BotPlayer.playCast(Tournaments.specs, start.seed, BotDifficulty.NORMAL)
        now += (cast.durationSec * 1000).toLong() + 1_000
        val submit = post(SocialApi.TOURNAMENT_SUBMIT_PATH, json.encodeToString(SubmitRequest.serializer(), SubmitRequest(alice.id, alice.secret, start.week, start.attempt, cast.toDto())))
        assertEquals(HttpStatusCode.OK, submit.status)
        val board = post(SocialApi.LEADERBOARD_PATH, json.encodeToString(BoardRequest.serializer(), BoardRequest(bob.id, bob.secret, BoardKind.BEST_CAST, BoardView.FRIENDS)))
        assertEquals("Alice", json.decodeFromString(BoardResponse.serializer(), board.bodyAsText()).entries.single().name)
    }

    @Test
    fun clubPathsAnswer404WhenClubsAreOff() = testApplication {
        val social = store(clubs = false)
        application { battleServer(Lobby(), accounts, social) }
        val r = client.post(SocialApi.CLUB_PATH) { setBody(json.encodeToString(SocialRequest.serializer(), SocialRequest(alice.id, alice.secret))) }
        assertEquals(HttpStatusCode.NotFound, r.status)
        assertEquals(SocialProblem.CLUBS_OFF, json.decodeFromString(SocialError.serializer(), r.bodyAsText()).problem)
    }

    @Test
    fun verifiedPlayersBattleCastsCountOnTheLeaderboards() = testApplication {
        val social = store()
        application { battleServer(Lobby(), accounts, social) }
        suspend fun join(name: String, who: PlayerId, secret: String?) = createClient { install(WebSockets) }.webSocketSession("/battle").also {
            it.incoming.receive()
            it.send(Frame.Text(OnlineProtocol.encode(ClientMessage.Hello(name, playerId = who.id, secret = secret))))
        }
        suspend fun DefaultClientWebSocketSession.next(): ServerMessage = withTimeout(5_000) {
            while (true) {
                val f = incoming.receive() as? Frame.Text ?: continue
                return@withTimeout OnlineProtocol.decodeServer(f.readText())!!
            }
            @Suppress("UNREACHABLE_CODE")
            error("unreachable")
        }
        suspend fun DefaultClientWebSocketSession.started(): ServerMessage.MatchStarted {
            while (true) {
                val m = next()
                if (m is ServerMessage.MatchStarted) return m
            }
        }
        // Bob's id is already his (an id belongs to the first secret it's used with).
        social.hello(bob, "Bob")
        // Alice proves who she is; someone else only claims Bob's id (with the wrong secret).
        val a = join("Alice", alice, alice.secret)
        val b = join("Bob", bob, carol.secret)
        a.send(Frame.Text(OnlineProtocol.encode(ClientMessage.QuickMatch(BattleMode.HEAVY_HAUL))))
        b.send(Frame.Text(OnlineProtocol.encode(ClientMessage.QuickMatch(BattleMode.HEAVY_HAUL))))
        val startA = a.started()
        val startB = b.started()
        for ((ws, start) in listOf(a to startA, b to startB)) {
            val turn = OnlineMatch(start).myNextTurn()!!
            val cast = BotPlayer.playCast(OnlineProtocol.tournamentSpecs, turn.seed, BotDifficulty.HARD)
            ws.send(Frame.Text(OnlineProtocol.encode(ClientMessage.CastFinished(turn, cast.toDto()))))
        }
        // Both casts are recorded by the match (each player sees both).
        repeat(2) { while (a.next() !is ServerMessage.CastRecorded) Unit }
        val board = social.leaderboard(alice.id, alice.secret, BoardKind.BEST_CAST, BoardView.GLOBAL)
        assertEquals(listOf(true), board.entries.map { it.you }, "only the verified player is ranked")
    }
}
