package com.deepline.fishing.social

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.deepline.fishing.FeatureFlags
import com.deepline.fishing.analytics.Analytics
import com.deepline.fishing.core.BattleMode
import com.deepline.fishing.core.GameConfig
import com.deepline.fishing.core.KeyValueStore
import com.deepline.fishing.core.PlayerId
import com.deepline.fishing.core.PlayerProgress
import com.deepline.fishing.core.ShopClock
import com.deepline.fishing.core.online.CastDto
import com.deepline.fishing.core.social.AttemptStart
import com.deepline.fishing.core.social.BoardKind
import com.deepline.fishing.core.social.BoardResponse
import com.deepline.fishing.core.social.BoardView
import com.deepline.fishing.core.social.ChallengeDto
import com.deepline.fishing.core.social.ClubDto
import com.deepline.fishing.core.social.ClubMember
import com.deepline.fishing.core.social.Clubs
import com.deepline.fishing.core.social.FriendDto
import com.deepline.fishing.core.social.FriendsResponse
import com.deepline.fishing.core.social.MyClub
import com.deepline.fishing.core.social.PlayerCard
import com.deepline.fishing.core.social.Profile
import com.deepline.fishing.core.social.SocialProblem
import com.deepline.fishing.core.social.SubmitResponse
import com.deepline.fishing.core.social.TopClubs
import com.deepline.fishing.core.social.TournamentInfo
import com.deepline.fishing.core.social.TournamentResult
import com.deepline.fishing.core.social.Tournaments
import com.deepline.fishing.ui.shop.ShopText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * The social side of the game: friends, the weekly leaderboards, the weekly tournament and clubs
 * (clubs only while [FeatureFlags.clubs] is on: otherwise nothing here calls a club endpoint).
 * Holds what the social screens show, as Compose state, and talks to the server through [client]
 * (tests swap in a fake). Nothing here is needed to play: when the server can't be reached the
 * screens say so and the rest of the game is unaffected.
 */
class SocialController(
    private val scope: CoroutineScope,
    private val store: KeyValueStore,
    private val identity: () -> PlayerId,
    private val profile: () -> Profile,
    private val progress: () -> PlayerProgress,
    private val update: (PlayerProgress) -> Unit,
    baseUrl: () -> String,
) {
    var client: SocialClient = SocialClient(baseUrl)

    var friends by mutableStateOf<FriendsResponse?>(null)
        private set
    var boardKind by mutableStateOf(BoardKind.BEST_CAST)
        private set
    var boardView by mutableStateOf(BoardView.GLOBAL)
        private set
    var board by mutableStateOf<BoardResponse?>(null)
        private set
    var tournament by mutableStateOf<TournamentInfo?>(null)
        private set
    /** How the last attempt went (shown on the tournament screen once it's back). */
    var lastAttempt by mutableStateOf<SubmitResponse?>(null)
        private set
    /** The player's club; [MyClub.club] is null when they're in none. Null until loaded. */
    var club by mutableStateOf<MyClub?>(null)
        private set
    var topClubs by mutableStateOf<TopClubs?>(null)
        private set

    private var running by mutableIntStateOf(0)

    /** A request is running (screens show a progress indicator). */
    val busy: Boolean get() = running > 0

    /** The last load couldn't reach the server: screens show an offline card. */
    var offline by mutableStateOf(false)
        private set

    /** What just happened ("Friend request sent."), until dismissed. */
    var message by mutableStateOf<String?>(null)
        private set

    /** About a challenge being sent, shown in the online lobby while waiting for the friend. */
    var challengeStatus by mutableStateOf<String?>(null)
        private set

    private var pendingChallenge: Pair<String, String>? = null
    private var pendingSubmit: Pair<AttemptStart, CastDto>? = null

    init {
        registerNames()
    }

    fun dismissMessage() {
        message = null
    }

    /** Loads something a screen shows; failing to reach the server shows the offline card. */
    private fun load(block: suspend () -> Unit) = scope.launch {
        running++
        try {
            block()
            offline = false
        } catch (e: SocialClient.SocialException) {
            if (e.problem == null) offline = true else message = SocialText.problem(e.problem)
        } finally {
            running--
        }
    }

    /** Does something the player asked for; a failure is explained in [message]. */
    private fun act(block: suspend () -> Unit) = scope.launch {
        running++
        try {
            block()
        } catch (e: SocialClient.SocialException) {
            if (e.problem == null) offline = true
            message = SocialText.problem(e.problem)
        } finally {
            running--
        }
    }

    /** Something the player doesn't wait for (acknowledgements, club fish): failures are retried later. */
    private fun quietly(block: suspend () -> Unit) = scope.launch {
        try {
            block()
        } catch (_: SocialClient.SocialException) {
        }
    }

    /** A social screen opened: refresh what it shows. */
    fun onOpen(screen: com.deepline.fishing.Screen) {
        when (screen) {
            com.deepline.fishing.Screen.SOCIAL -> {
                refreshFriends()
                refreshTournament()
                if (FeatureFlags.clubs) refreshClub()
            }
            com.deepline.fishing.Screen.FRIENDS -> refreshFriends()
            com.deepline.fishing.Screen.LEADERBOARDS -> refreshBoard()
            com.deepline.fishing.Screen.TOURNAMENT -> refreshTournament()
            com.deepline.fishing.Screen.CLUBS -> refreshClub()
            else -> Unit
        }
    }

    /** At launch: club members' fish caught since last time count for their club's goal. */
    fun onLaunch() {
        if (FeatureFlags.clubs && !store.getString(KEY_CLUB).isNullOrEmpty()) quietly { reportClubFish() }
    }

    // ---- Friends -------------------------------------------------------------------------------------

    fun refreshFriends() = load { friends = client.friends(identity(), profile()) }

    /** Asks the player whose id was typed (case, spaces and dashes don't matter) to be friends. */
    fun sendFriendRequest(typed: String) {
        val id = typed.uppercase().filter { it in PlayerId.ALPHABET }
        when {
            !PlayerId.isValid(id) -> message = "A player id has ${PlayerId.ID_LENGTH} letters and digits."
            id == identity().id -> message = "That's your own player id. Share it with your friends so they can add you."
            else -> act {
                client.requestFriend(identity(), id)
                val now = client.friends(identity(), profile())
                friends = now
                val friend = now.friends.firstOrNull { it.id == id }
                if (friend != null) {
                    Analytics.event("friend_add", mapOf("method" to "request_back"))
                    message = "You're now friends with ${friend.name}."
                } else {
                    Analytics.event("friend_request_sent")
                    message = "Friend request sent to $id. You'll be friends once they accept."
                }
            }
        }
    }

    fun accept(card: PlayerCard) = act {
        client.acceptFriend(identity(), card.id)
        Analytics.event("friend_add", mapOf("method" to "accept"))
        message = "You're now friends with ${card.name}."
        friends = client.friends(identity(), profile())
    }

    /** Declines a request, or takes back one the player sent. */
    fun decline(card: PlayerCard) = act {
        client.declineFriend(identity(), card.id)
        friends = client.friends(identity(), profile())
    }

    fun remove(friend: FriendDto) = act {
        client.removeFriend(identity(), friend.id)
        Analytics.event("friend_remove")
        message = "${friend.name} is no longer on your friends list."
        friends = client.friends(identity(), profile())
    }

    /** The next battle room the player creates is sent to [friend] as a challenge. */
    fun prepareChallenge(friend: FriendDto) {
        pendingChallenge = friend.id to friend.name
        challengeStatus = "Creating a room for ${friend.name}…"
    }

    /** A battle room was created: if it's for a challenge, invite the friend into it. */
    fun onRoomCreated(code: String, mode: BattleMode) {
        val (to, name) = pendingChallenge ?: run {
            challengeStatus = null
            return
        }
        pendingChallenge = null
        scope.launch {
            challengeStatus = try {
                client.challenge(identity(), to, code, mode)
                Analytics.event("friend_challenge", mapOf("mode" to mode.name.lowercase()))
                "Challenge sent to $name. They'll see it on their Friends screen for ${GameConfig.CHALLENGE_MINUTES} minutes."
            } catch (e: SocialClient.SocialException) {
                SocialText.problem(e.problem) + " The challenge wasn't sent: share the room code with your friend instead."
            }
        }
    }

    /** The player is joining [challenge]'s room: it's no longer waiting. */
    fun onChallengeAccepted(challenge: ChallengeDto) {
        friends = friends?.let { f -> f.copy(challenges = f.challenges - challenge) }
        Analytics.event("friend_challenge_accept", mapOf("mode" to challenge.mode.name.lowercase()))
    }

    // ---- Leaderboards --------------------------------------------------------------------------------

    fun refreshBoard() {
        val kind = boardKind
        val view = boardView
        load {
            board = client.leaderboard(identity(), kind, view)
            Analytics.event("leaderboard_view", mapOf("board" to kind.name.lowercase(), "view" to view.name.lowercase()))
        }
    }

    fun selectBoard(kind: BoardKind) {
        boardKind = kind
        refreshBoard()
    }

    fun selectView(view: BoardView) {
        boardView = view
        refreshBoard()
    }

    // ---- Tournament ----------------------------------------------------------------------------------

    fun refreshTournament() = load {
        sendPendingAttempt()
        tournament = withoutGranted(client.tournament(identity()))
    }

    /** Rewards this phone already gave (the server missed the acknowledgement): acknowledge again. */
    private fun withoutGranted(t: TournamentInfo): TournamentInfo {
        val given = t.rewards.filter { it.week in progress().tournamentClaims }
        if (given.isEmpty()) return t
        quietly { for (r in given) client.claimTournament(identity(), r.week) }
        return t.copy(rewards = t.rewards - given.toSet())
    }

    /** Uses one of the week's attempts; [onStarted] then begins the cast on the week's water. */
    fun startAttempt(onStarted: (AttemptStart) -> Unit) = act {
        val start = client.startAttempt(identity())
        Analytics.event("tournament_start", mapOf("attempt" to start.attempt))
        lastAttempt = null
        onStarted(start)
    }

    /** Sends a finished attempt (kept and sent again later if the server can't be reached now). */
    fun submitAttempt(start: AttemptStart, cast: CastDto) {
        pendingSubmit = start to cast
        lastAttempt = null
        act {
            sendPendingAttempt()
            tournament = withoutGranted(client.tournament(identity()))
        }
    }

    private suspend fun sendPendingAttempt() {
        val (start, cast) = pendingSubmit ?: return
        try {
            val r = client.submitAttempt(identity(), start.week, start.attempt, cast)
            pendingSubmit = null
            lastAttempt = r
            Analytics.event("post_score", mapOf("score" to r.points, "level" to "tournament"))
        } catch (e: SocialClient.SocialException) {
            // Refused (too late, impossible): it stays at 0. Unreachable: try again later.
            if (e.problem != null) pendingSubmit = null
            throw e
        }
    }

    /** Gives a finished tournament's reward (once per phone) and tells the server. */
    fun claimReward(result: TournamentResult) {
        val before = progress()
        val contents = result.tier.grants.joinToString(", ") { ShopText.seasonGrant(it, before) }
        Tournaments.grant(before, result.week, result.tier, ShopClock.now())?.let {
            update(it)
            message = "Tournament reward (${result.tier.displayName}): $contents."
            Analytics.event("tournament_reward", mapOf("tier" to result.tier.name.lowercase()))
        }
        tournament = tournament?.let { t -> t.copy(rewards = t.rewards - result) }
        quietly { client.claimTournament(identity(), result.week) }
    }

    // ---- Clubs (flagged) -----------------------------------------------------------------------------

    fun refreshClub() {
        if (!FeatureFlags.clubs) return
        load {
            reportClubFish()
            val mine = withoutGranted(client.myClub(identity(), profile()))
            club = mine
            rememberClub(mine.club)
            topClubs = client.topClubs(identity())
        }
    }

    fun createClub(adjective: Int, noun: Int) {
        if (!FeatureFlags.clubs) return
        act {
            val mine = client.createClub(identity(), profile(), adjective, noun)
            club = mine
            rememberClub(mine.club)
            Analytics.event("join_group", mapOf("method" to "create"))
            mine.club?.let { message = "Welcome to the ${it.name}! Share the code ${it.code} to invite anglers." }
        }
    }

    fun joinClub(typed: String) {
        if (!FeatureFlags.clubs) return
        val code = Clubs.normalizeCode(typed)
        if (!Clubs.isValidCode(code)) {
            message = "A club code has ${Clubs.CODE_LENGTH} letters and digits."
            return
        }
        act {
            val mine = client.joinClub(identity(), profile(), code)
            club = mine
            rememberClub(mine.club)
            Analytics.event("join_group", mapOf("method" to "code"))
            mine.club?.let { message = "Welcome to the ${it.name}!" }
        }
    }

    fun leaveClub() {
        if (!FeatureFlags.clubs) return
        act {
            client.leaveClub(identity())
            club = MyClub(null)
            rememberClub(null)
            Analytics.event("club_leave")
            message = "You left the club."
        }
    }

    fun kick(member: ClubMember) = clubAction("${member.name} was removed from the club.") { client.kick(identity(), member.id) }

    fun transfer(member: ClubMember) = clubAction("${member.name} now owns the club.") { client.transfer(identity(), member.id) }

    fun renameClub(adjective: Int, noun: Int) = clubAction("The club is now the ${Clubs.name(adjective, noun)}.") {
        client.renameClub(identity(), adjective, noun)
    }

    private fun clubAction(done: String, block: suspend () -> Unit) {
        if (!FeatureFlags.clubs) return
        act {
            block()
            message = done
            club = client.myClub(identity(), profile())
        }
    }

    /** Gives the club goal rewards waiting (once per phone) and tells the server. */
    fun claimClubRewards() {
        if (!FeatureFlags.clubs) return
        val c = club?.club ?: return
        val now = ShopClock.now()
        var p = progress()
        val lines = ArrayList<String>()
        for (claim in c.claimable) {
            val contents = Clubs.rewardOf(claim.tier).joinToString(", ") { ShopText.seasonGrant(it, p) }
            Clubs.grant(p, claim.week, claim.tier, now)?.let {
                p = it
                lines += contents
            }
        }
        if (lines.isNotEmpty()) {
            update(p)
            message = "Club goal reward: ${lines.joinToString(", ")}."
            Analytics.event("club_reward", mapOf("tiers" to lines.size))
        }
        club = MyClub(c.copy(claimable = emptyList()))
        val keys = c.claimable.map { it.key }
        quietly { client.claimClub(identity(), keys) }
    }

    /** Goal rewards this phone already gave: acknowledged again, not offered again. */
    private fun withoutGranted(mine: MyClub): MyClub {
        val c = mine.club ?: return mine
        val given = c.claimable.filter { it.key in progress().clubClaims }
        if (given.isEmpty()) return mine
        quietly { client.claimClub(identity(), given.map { it.key }) }
        return MyClub(c.copy(claimable = c.claimable - given.toSet()))
    }

    /** Remembers the club (to report fish at launch); joining a club starts counting from now. */
    private fun rememberClub(dto: ClubDto?) {
        val before = store.getString(KEY_CLUB).orEmpty()
        val now = dto?.id.orEmpty()
        if (before == now) return
        store.putString(KEY_CLUB, now)
        if (dto != null) store.putString(KEY_FISH_REPORTED, totalCaught(progress()).toString())
    }

    /** Sends the fish caught in solo casts since the last report (the server caps a day's). */
    private suspend fun reportClubFish() {
        if (!FeatureFlags.clubs || store.getString(KEY_CLUB).isNullOrEmpty()) return
        val total = totalCaught(progress())
        val reported = store.getString(KEY_FISH_REPORTED)?.toIntOrNull()
        if (reported == null || total < reported) {
            store.putString(KEY_FISH_REPORTED, total.toString())
            return
        }
        val fish = (total - reported).coerceAtMost(GameConfig.CLUB_FISH_PER_MEMBER_PER_DAY)
        if (fish <= 0) return
        client.contribute(identity(), fish)
        store.putString(KEY_FISH_REPORTED, total.toString())
    }

    private fun totalCaught(p: PlayerProgress): Int = p.collection.values.sumOf { it.caught }

    // ---- Account -------------------------------------------------------------------------------------

    /** Deletes the player's friends, rankings and club membership from the server. */
    fun forgetMe() = act {
        client.forget(identity())
        friends = FriendsResponse()
        board = null
        tournament = null
        lastAttempt = null
        club = MyClub(null)
        store.putString(KEY_CLUB, "")
        Analytics.event("social_data_deleted")
        message = "Your friends, rankings and club membership were deleted from the server."
    }

    fun close() = client.close()

    companion object {
        private const val KEY_CLUB = "social.club"
        private const val KEY_FISH_REPORTED = "social.clubFishReported"

        @Volatile
        private var namesRegistered = false

        /** Names the tournament badge in purchase and reward messages. */
        private fun registerNames() {
            if (namesRegistered) return
            namesRegistered = true
            ShopText.registerUnlockName { key -> if (key == com.deepline.fishing.core.TitleFrame.TOURNAMENT.unlockKey) "the Tournament title badge" else null }
        }
    }
}

/** What the social screens say when the server says no. Whole sentences. */
object SocialText {
    fun problem(p: SocialProblem?): String = when (p) {
        null -> "Can't reach the server. Check your connection and try again."
        SocialProblem.BAD_REQUEST -> "The server couldn't accept that."
        SocialProblem.WRONG_SECRET -> "The server doesn't recognise this player. Restore your recovery code from the Shop's Account button."
        SocialProblem.UNKNOWN_PLAYER -> "No angler has that player id yet."
        SocialProblem.UNKNOWN_CLUB -> "No club has that code."
        SocialProblem.CLUBS_OFF -> "Clubs aren't open on this server yet."
        SocialProblem.NOT_FRIENDS -> "You can only challenge your friends."
        SocialProblem.FRIENDS_FULL -> "Your friends list is full (${GameConfig.MAX_FRIENDS} friends). Remove someone first."
        SocialProblem.THEIR_FRIENDS_FULL -> "That angler can't take more friend requests right now."
        SocialProblem.ALREADY_IN_CLUB -> "You're already in a club. Leave it first."
        SocialProblem.NOT_IN_CLUB -> "You're not in a club."
        SocialProblem.NOT_OWNER -> "Only the club's owner can do that."
        SocialProblem.CLUB_FULL -> "That club is full (${GameConfig.CLUB_MAX_MEMBERS} members)."
        SocialProblem.REJOIN_LATER -> "You were removed from that club, so you can only rejoin it after ${GameConfig.CLUB_REJOIN_HOURS} hours."
        SocialProblem.NO_ATTEMPTS_LEFT -> "You've used every attempt this week. A new tournament starts on Monday at 00:00 UTC."
        SocialProblem.ATTEMPT_EXPIRED -> "That attempt took too long to come back, so it scored 0."
        SocialProblem.CAST_REJECTED -> "The server couldn't accept that attempt, so it scored 0."
        SocialProblem.TOO_BIG -> "That's too much for the server."
        SocialProblem.TOO_MANY_REQUESTS -> "That's a lot at once. Please wait a little and try again."
    }
}
