package com.deepline.fishing

import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import com.deepline.fishing.analytics.Analytics
import com.deepline.fishing.analytics.AnalyticsBackend
import com.deepline.fishing.core.BattleMode
import com.deepline.fishing.core.GameConfig
import com.deepline.fishing.core.NightMode
import com.deepline.fishing.core.PlayerId
import com.deepline.fishing.core.PlayerProgress
import com.deepline.fishing.core.RopeStat
import com.deepline.fishing.core.ShopClock
import com.deepline.fishing.core.TitleFrame
import com.deepline.fishing.core.online.CastDto
import com.deepline.fishing.core.social.AttemptStart
import com.deepline.fishing.core.social.BoardEntry
import com.deepline.fishing.core.social.BoardKind
import com.deepline.fishing.core.social.BoardResponse
import com.deepline.fishing.core.social.BoardView
import com.deepline.fishing.core.social.ChallengeDto
import com.deepline.fishing.core.social.ClubClaim
import com.deepline.fishing.core.social.ClubDto
import com.deepline.fishing.core.social.ClubMember
import com.deepline.fishing.core.social.ClubRole
import com.deepline.fishing.core.social.ClubSummary
import com.deepline.fishing.core.social.Clubs
import com.deepline.fishing.core.social.FriendDto
import com.deepline.fishing.core.social.FriendsResponse
import com.deepline.fishing.core.social.Leaderboards
import com.deepline.fishing.core.social.MyClub
import com.deepline.fishing.core.social.PlayerCard
import com.deepline.fishing.core.social.Profile
import com.deepline.fishing.core.social.SocialProblem
import com.deepline.fishing.core.social.SubmitResponse
import com.deepline.fishing.core.social.TopClubs
import com.deepline.fishing.core.social.TournamentInfo
import com.deepline.fishing.core.social.TournamentResult
import com.deepline.fishing.core.social.TournamentTier
import com.deepline.fishing.core.social.Tournaments
import com.deepline.fishing.core.social.Weeks
import com.deepline.fishing.social.SocialClient
import com.deepline.fishing.ui.tour.Tour
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * A pretend server: friends, boards, the tournament and clubs in memory. Tournament attempts are
 * checked with the real rules and the real water check, so a cast the app didn't fish on the
 * week's water is refused, as the server would.
 */
class FakeSocialClient : SocialClient({ "" }) {
    var reachable = true
    val week = Weeks.indexAt(ShopClock.now())
    val friendsList = mutableListOf<FriendDto>()
    val incoming = mutableListOf<PlayerCard>()
    val outgoing = mutableListOf<PlayerCard>()
    val challenges = mutableListOf<ChallengeDto>()
    val requested = mutableListOf<String>()
    val sentChallenges = mutableListOf<Pair<String, String>>()
    val boardsAsked = mutableListOf<Pair<BoardKind, BoardView>>()
    var attemptsUsed = 0
    var best: Int? = null
    val submitted = mutableListOf<CastDto>()
    val rewards = mutableListOf<TournamentResult>()
    val claimedWeeks = mutableListOf<Long>()
    var club: ClubDto? = null
    var clubCalls = 0
    val contributed = mutableListOf<Int>()
    val claimedKeys = mutableListOf<String>()
    var created: Pair<Int, Int>? = null

    private fun check() {
        if (!reachable) throw SocialException(null)
    }

    override suspend fun friends(who: PlayerId, profile: Profile): FriendsResponse {
        check()
        return FriendsResponse(friendsList.toList(), incoming.toList(), outgoing.toList(), challenges.toList())
    }

    override suspend fun requestFriend(who: PlayerId, target: String) {
        check()
        requested += target
        outgoing += PlayerCard(target, Leaderboards.anglerName(target))
    }

    override suspend fun acceptFriend(who: PlayerId, target: String) {
        check()
        val card = incoming.first { it.id == target }
        incoming -= card
        friendsList += FriendDto(card.id, card.name, card.title, card.titleFrame, online = true, weekBestCast = 1_200, weekDeepestM = 212f)
    }

    override suspend fun declineFriend(who: PlayerId, target: String) {
        check()
        incoming.removeAll { it.id == target }
        outgoing.removeAll { it.id == target }
    }

    override suspend fun removeFriend(who: PlayerId, target: String) {
        check()
        friendsList.removeAll { it.id == target }
    }

    override suspend fun challenge(who: PlayerId, target: String, code: String, mode: BattleMode) {
        check()
        sentChallenges += target to code
    }

    override suspend fun leaderboard(who: PlayerId, board: BoardKind, view: BoardView): BoardResponse {
        check()
        boardsAsked += board to view
        val rows = listOf(
            BoardEntry(1, "Swift Marlin 7Q", 9_000, "ghost_hake".takeIf { board != BoardKind.BEST_CAST }, title = "Giant-slayer", titleFrame = "OCEAN"),
            BoardEntry(2, "Bob", 5_000, "ink_cuttle".takeIf { board != BoardKind.BEST_CAST }, id = "BOBBBBBB", friend = true),
            BoardEntry(3, "Angler", 4_000, "dusk_snapper".takeIf { board != BoardKind.BEST_CAST }, id = who.id, you = true),
        )
        val shown = if (view == BoardView.FRIENDS) rows.drop(1).mapIndexed { i, e -> e.copy(rank = i + 1) } else rows
        return BoardResponse(board, view, week, Weeks.endOf(week), shown, shown.last(), shown.size)
    }

    override suspend fun tournament(who: PlayerId): TournamentInfo {
        check()
        val top = best?.let { listOf(BoardEntry(1, "Angler", it.toLong(), id = who.id, you = true)) }.orEmpty()
        return TournamentInfo(
            week, Weeks.startOf(week), Weeks.endOf(week), GameConfig.TOURNAMENT_ATTEMPTS, attemptsUsed, best,
            rank = best?.let { 1 }, participants = if (best != null) 1 else 0, top = top, rewards = rewards.toList(),
        )
    }

    override suspend fun startAttempt(who: PlayerId): AttemptStart {
        check()
        if (attemptsUsed >= GameConfig.TOURNAMENT_ATTEMPTS) throw SocialException(SocialProblem.NO_ATTEMPTS_LEFT)
        attemptsUsed++
        best = best ?: 0
        return AttemptStart(week, attemptsUsed, Tournaments.seedOf(week), ShopClock.now() + 15 * 60_000L)
    }

    override suspend fun submitAttempt(who: PlayerId, week: Long, attempt: Int, cast: CastDto): SubmitResponse {
        check()
        submitted += cast
        val valid = Tournaments.validate(cast, attempt, Tournaments.seedOf(week)) ?: throw SocialException(SocialProblem.CAST_REJECTED)
        best = maxOf(best ?: 0, valid.points)
        return SubmitResponse(week, attempt, valid.points, best!!, 1, 1)
    }

    override suspend fun claimTournament(who: PlayerId, week: Long) {
        check()
        claimedWeeks += week
        rewards.removeAll { it.week == week }
    }

    private fun clubCall() {
        clubCalls++
        check()
    }

    override suspend fun myClub(who: PlayerId, profile: Profile): MyClub {
        clubCall()
        return MyClub(club)
    }

    private fun newClub(who: PlayerId, adjective: Int, noun: Int, owner: Boolean) = ClubDto(
        "CLUB1", Clubs.name(adjective, noun)!!, "ABC234", week, Weeks.endOf(week),
        members = listOf(
            ClubMember("SAILORS1", "Sailor Sam", role = if (owner) ClubRole.MEMBER else ClubRole.OWNER, weekFish = 180, online = true),
            ClubMember(who.id, "Angler", role = if (owner) ClubRole.OWNER else ClubRole.MEMBER, weekFish = 20),
        ),
        weekFish = 200, tiersReached = 1, claimable = listOf(ClubClaim(week, 1)),
    )

    override suspend fun createClub(who: PlayerId, profile: Profile, adjective: Int, noun: Int): MyClub {
        clubCall()
        created = adjective to noun
        club = newClub(who, adjective, noun, owner = true).copy(claimable = emptyList())
        return MyClub(club)
    }

    override suspend fun joinClub(who: PlayerId, profile: Profile, code: String): MyClub {
        clubCall()
        if (code != "ABC234") throw SocialException(SocialProblem.UNKNOWN_CLUB)
        club = newClub(who, 3, 4, owner = false)
        return MyClub(club)
    }

    override suspend fun leaveClub(who: PlayerId) {
        clubCall()
        club = null
    }

    override suspend fun contribute(who: PlayerId, fish: Int) {
        clubCall()
        contributed += fish
    }

    override suspend fun claimClub(who: PlayerId, keys: List<String>) {
        clubCall()
        claimedKeys += keys
        club = club?.copy(claimable = emptyList())
    }

    override suspend fun topClubs(who: PlayerId): TopClubs {
        clubCall()
        return TopClubs(week, listOfNotNull(club?.let { ClubSummary(1, it.name, it.members.size, it.weekFish, mine = true) }))
    }
}

/**
 * Shared setup for the social UI tests: the app against a [FakeSocialClient], analytics recorded.
 * Each scenario is its own class, so each gets a fresh JVM (see `forkEvery` in app/build.gradle.kts):
 * a ViewModel would otherwise keep the first test's Application, and its saved progress.
 */
abstract class SocialUiTestBase : BoatUiTestBase() {
    private val flagBefore = FeatureFlags.clubs
    protected val events = ArrayList<String>()

    @After
    fun restore() {
        FeatureFlags.clubs = flagBefore
        Analytics.backend = AnalyticsBackend.None
    }

    protected fun launchSocial(progress: PlayerProgress = PlayerProgress(lanterns = 3)): Pair<GameViewModel, FakeSocialClient> {
        Analytics.backend = object : AnalyticsBackend {
            override fun event(name: String, params: Map<String, Any?>) {
                events += name
            }
            override fun screen(name: String) = Unit
            override fun recordError(error: Throwable, message: String?) = Unit
        }
        val vm = launch(progress)
        val fake = FakeSocialClient()
        vm.social.client = fake
        // No battle server in these tests: joining a room fails fast.
        vm.updateServerUrl("ws://127.0.0.1:1/battle")
        return vm to fake
    }

    protected fun tap(tag: String) {
        val node = compose.onNodeWithTag(tag)
        runCatching { node.performScrollTo() }
        node.performClick()
        compose.waitForIdle()
    }

    protected fun exists(tag: String) = compose.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty()
}

/** Friends: requests by id, answers, challenges into a battle room, removing. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w400dp-h860dp-xxhdpi")
class SocialFriendsUiTest : SocialUiTestBase() {
    @Test
    fun friendsAreAddedByIdAndCanBeChallenged() {
        val (vm, fake) = launchSocial()
        val bob = PlayerCard("BOBBBBBB", "Bob", "Giant-slayer", "OCEAN")
        fake.incoming += bob
        // The Battle tab leads to friends and rankings.
        compose.onNodeWithTag("tab_battle").performClick()
        tap("battle_social")
        assertEquals(Screen.SOCIAL, vm.screen)
        compose.onNodeWithTag("my_player_id").assertExists()
        compose.shot("90_social_hub")
        tap("social_friends")
        assertEquals(Screen.FRIENDS, vm.screen)
        compose.shot("91_friends_requests")

        // A typed id is forgiving about case and spaces.
        val friendId = PlayerId.random().id
        compose.onNodeWithTag("friend_id").performTextInput(friendId.lowercase().chunked(4).joinToString(" "))
        tap("friend_send")
        compose.waitUntil(5_000) { fake.requested.isNotEmpty() }
        assertEquals(listOf(friendId), fake.requested)
        compose.waitUntil(5_000) { vm.social.message?.contains("Friend request sent") == true }
        // Not an id.
        compose.onNodeWithTag("friend_id").performTextInput("abc")
        tap("friend_send")
        assertTrue(vm.social.message!!.contains("letters and digits"))

        // Bob asked: accept him.
        tap("friend_accept_${bob.id}")
        compose.waitUntil(5_000) { vm.social.friends?.friends?.any { it.id == bob.id } == true }
        compose.onNodeWithText("Bob").assertExists()
        compose.onNodeWithText("Online").assertExists()
        compose.shot("92_friends_list")

        // Bob challenges the player: joining opens the online lobby on his mode.
        fake.challenges += ChallengeDto(bob.id, "Bob", "QWERT", BattleMode.DEEP_DIVE, ShopClock.now() + 600_000L)
        vm.social.refreshFriends()
        compose.waitUntil(5_000) { exists("challenge_join_QWERT") }
        compose.shot("93_friend_challenge")
        tap("challenge_join_QWERT")
        assertEquals(Screen.ONLINE_LOBBY, vm.screen)
        assertEquals(BattleMode.DEEP_DIVE, vm.onlineMode)
        vm.closeOnlineLobby()

        // Challenging Bob creates a room and sends him its code.
        vm.openSocial(Screen.FRIENDS)
        compose.waitUntil(5_000) { exists("friend_challenge_${bob.id}") }
        tap("friend_challenge_${bob.id}")
        tap("challenge_mode_heavy_haul")
        assertEquals(Screen.ONLINE_LOBBY, vm.screen)
        assertEquals(BattleMode.HEAVY_HAUL, vm.onlineMode)
        vm.social.onRoomCreated("ZXCVB", BattleMode.HEAVY_HAUL)
        compose.waitUntil(5_000) { fake.sentChallenges.isNotEmpty() }
        assertEquals(listOf(bob.id to "ZXCVB"), fake.sentChallenges)
        assertTrue(vm.social.challengeStatus!!.contains("Challenge sent to Bob"))
        vm.closeOnlineLobby()

        // Removing a friend asks first.
        vm.openSocial(Screen.FRIENDS)
        compose.waitUntil(5_000) { exists("friend_remove_${bob.id}") }
        tap("friend_remove_${bob.id}")
        tap("friend_remove_confirm")
        compose.waitUntil(5_000) { fake.friendsList.isEmpty() }
        assertTrue(events.containsAll(listOf("friend_request_sent", "friend_add", "friend_challenge", "friend_remove")))
        // Back goes to the hub, then the Battle tab.
        vm.onBack()
        assertEquals(Screen.SOCIAL, vm.screen)
        vm.onBack()
        assertEquals(Screen.BATTLE_SETUP, vm.screen)
    }
}

/** The weekly leaderboards: every board and view, and the offline state. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w400dp-h860dp-xxhdpi")
class SocialLeaderboardsUiTest : SocialUiTestBase() {
    @Test
    fun leaderboardsShowEveryViewAndTheOfflineState() {
        val (vm, fake) = launchSocial()
        fake.reachable = false
        vm.openSocial(Screen.LEADERBOARDS)
        compose.waitUntil(5_000) { vm.social.offline }
        compose.onNodeWithTag("social_offline").assertExists()
        compose.shot("94_leaderboards_offline")
        // The server is back.
        fake.reachable = true
        tap("social_retry")
        compose.waitUntil(5_000) { vm.social.board != null }
        assertFalse(vm.social.offline)
        compose.onNodeWithTag("board_me").assertExists()
        compose.onNodeWithText("Swift Marlin 7Q").assertExists()
        compose.shot("95_leaderboards_global")
        tap("view_friends")
        compose.waitUntil(5_000) { vm.social.board?.view == BoardView.FRIENDS }
        tap("board_rarest")
        compose.waitUntil(5_000) { vm.social.board?.board == BoardKind.RAREST }
        assertEquals(BoardKind.RAREST to BoardView.FRIENDS, fake.boardsAsked.last())
        compose.shot("96_leaderboards_friends_rarest")
        // Readable at night too.
        vm.setNightMode(NightMode.NIGHT)
        compose.waitForIdle()
        compose.shot("96b_leaderboards_night")
        assertTrue("leaderboard_view" in events)
    }
}

/** The weekly tournament: a reward claimed once, an attempt on the week's water, a quit attempt. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w400dp-h860dp-xxhdpi")
class SocialTournamentUiTest : SocialUiTestBase() {
    @Test
    fun aTournamentAttemptIsFishedOnTheWeeksWaterAndSent() {
        val start = PlayerProgress(lanterns = 3, ropeLevels = mapOf(RopeStat.LENGTH to 1))
        val (vm, fake) = launchSocial(start)
        val lastWeek = fake.week - 1
        fake.rewards += TournamentResult(lastWeek, rank = 3, participants = 40, best = 2_400, tier = TournamentTier.GOLD)
        vm.openSocial(Screen.TOURNAMENT)
        compose.waitUntil(5_000) { vm.social.tournament != null }
        compose.onNodeWithText("${GameConfig.TOURNAMENT_ATTEMPTS} of ${GameConfig.TOURNAMENT_ATTEMPTS}").assertExists()
        compose.shot("97_tournament")

        // Last week's reward: claimed once, the badge frame included.
        tap("tournament_claim")
        assertEquals(start.lanterns + 2, vm.progress.lanterns)
        assertTrue(TitleFrame.TOURNAMENT.isAvailable(vm.progress, ShopClock.now()))
        assertTrue(lastWeek in vm.progress.tournamentClaims)
        compose.waitUntil(5_000) { fake.claimedWeeks.isNotEmpty() }
        assertFalse(exists("tournament_reward"))

        // An attempt, with a Lantern (allowed, as in battles).
        vm.toggleLantern(true)
        tap("tournament_start")
        compose.waitUntil(5_000) { vm.screen == Screen.CAST }
        assertNotNull(vm.tournamentAttempt)
        assertEquals(Tournaments.specs, vm.session!!.specs)
        assertEquals(start.lanterns + 2 - 1, vm.progress.lanterns)
        var guard = 0
        while (vm.pendingResult == null && guard++ < 40_000) {
            if (vm.secretPopup != null) vm.answerSecretPopup(false)
            vm.onFrame(1f / 30f, null)
        }
        assertNotNull(vm.pendingResult)
        vm.collectResult()
        assertEquals(Screen.TOURNAMENT, vm.screen)
        assertNull(vm.tournamentAttempt)
        compose.waitUntil(5_000) { vm.social.lastAttempt != null }
        // The cast sent is one the server accepts on the week's water, and it scored nothing locally.
        val sent = fake.submitted.single()
        assertEquals(Tournaments.validate(sent, 1, Tournaments.seedOf(fake.week))!!.points, vm.social.lastAttempt!!.points)
        assertEquals(0, vm.progress.castsMade)
        assertTrue("tournament catches stay out of the fish log", vm.progress.collection.isEmpty())
        compose.onNodeWithTag("tournament_last").assertExists()
        compose.shot("98_tournament_after_attempt")

        // Quitting an attempt uses it (the clock is held so the cast can't end on its own).
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag("tournament_start").performScrollTo().performClick()
        assertEquals(Screen.CAST, vm.screen)
        vm.pause()
        compose.mainClock.advanceTimeBy(200)
        compose.onNodeWithText("Quitting scores 0 for this tournament attempt, and it still counts as one of your attempts.").assertExists()
        vm.abandonCast()
        compose.mainClock.autoAdvance = true
        assertEquals(Screen.TOURNAMENT, vm.screen)
        compose.waitUntil(5_000) { vm.social.tournament?.attemptsUsed == 2 }
        assertEquals(1, fake.submitted.size)
        assertTrue(events.containsAll(listOf("tournament_reward", "tournament_start", "post_score")))
        vm.setNightMode(NightMode.NIGHT)
        compose.waitForIdle()
        compose.shot("98b_tournament_night")
    }
}

/** Clubs, with the feature switched on: join by code, the weekly goal, leave, start one. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w400dp-h860dp-xxhdpi")
class SocialClubsUiTest : SocialUiTestBase() {
    @Test
    fun clubsWhenTheFeatureIsOn() {
        FeatureFlags.clubs = true
        val (vm, fake) = launchSocial(PlayerProgress(lanterns = 1))
        vm.openSocial()
        compose.waitUntil(5_000) { exists("social_clubs") }
        tap("social_clubs")
        assertEquals(Screen.CLUBS, vm.screen)
        compose.waitUntil(5_000) { vm.social.club != null }
        compose.shot("99_clubs_join_or_start")
        // A wrong code, then the right one (case doesn't matter).
        compose.onNodeWithTag("club_code").performTextInput("zzz999")
        tap("club_join")
        compose.waitUntil(5_000) { vm.social.message?.contains("No club has that code") == true }
        vm.social.joinClub("abc-234")
        compose.waitUntil(5_000) { vm.social.club?.club != null }
        compose.onNodeWithTag("club_code_shown").assertExists()
        compose.shot("99b_club")
        // The weekly goal's first tier is reached: claim it.
        tap("club_claim")
        assertEquals(2, vm.progress.lanterns)
        assertTrue(Clubs.claimKey(fake.week, 1) in vm.progress.clubClaims)
        compose.waitUntil(5_000) { fake.claimedKeys.isNotEmpty() }
        // Fish caught since joining are reported for the club's goal.
        SharedPrefsStore(androidx.test.core.app.ApplicationProvider.getApplicationContext()).putString("social.clubFishReported", "-25")
        vm.social.refreshClub()
        compose.waitUntil(5_000) { fake.contributed.isNotEmpty() }
        assertEquals(listOf(25), fake.contributed)
        // Leave, then start a club with a generated name.
        tap("club_leave")
        tap("club_leave_confirm")
        compose.waitUntil(5_000) { vm.social.club?.club == null }
        tap("club_name_1")
        tap("club_create")
        compose.waitUntil(5_000) { fake.created != null }
        assertEquals(fake.club!!.name, Clubs.name(fake.created!!.first, fake.created!!.second))
        compose.waitUntil(5_000) { exists("club_rename") }
        vm.setNightMode(NightMode.NIGHT)
        compose.waitForIdle()
        compose.shot("99c_club_owner_night")
        assertTrue(events.containsAll(listOf("join_group", "club_leave", "club_reward")))
        assertTrue(Tour.steps.any { it.id == "clubs" })
    }
}

/** Clubs switched off (as in release builds): no club screen, card, tour step or server call. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w400dp-h860dp-xxhdpi")
class SocialClubsOffUiTest : SocialUiTestBase() {
    @Test
    fun withClubsOffThereIsNoClubAnywhere() {
        FeatureFlags.clubs = false
        val (vm, fake) = launchSocial()
        compose.onNodeWithTag("tab_battle").performClick()
        compose.onNodeWithText("Add friends, climb the weekly leaderboards and fish the weekly tournament.").assertExists()
        tap("battle_social")
        compose.waitUntil(5_000) { vm.social.tournament != null }
        assertFalse(exists("social_clubs"))
        vm.openSocial(Screen.CLUBS)
        assertEquals(Screen.SOCIAL, vm.screen)
        vm.social.refreshClub()
        vm.social.joinClub("ABC234")
        compose.waitForIdle()
        assertEquals("no club call is ever made", 0, fake.clubCalls)
        assertTrue(Tour.steps.none { it.screen == Screen.CLUBS })
        compose.shot("90b_social_hub_no_clubs")
    }
}
