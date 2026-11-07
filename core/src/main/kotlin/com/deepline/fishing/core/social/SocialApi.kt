package com.deepline.fishing.core.social

import com.deepline.fishing.core.BattleMode
import com.deepline.fishing.core.Cosmetic
import com.deepline.fishing.core.CosmeticSlot
import com.deepline.fishing.core.TitleFrame
import com.deepline.fishing.core.online.CastDto
import com.deepline.fishing.core.online.OnlineProtocol
import kotlinx.serialization.Serializable

/**
 * The battle server's social HTTP API: friends, weekly leaderboards, the weekly tournament and
 * clubs. Like the gift API (`AccountApi`), every call is a POST with a JSON body that carries the
 * player's public id and secret; the first call with an id registers it. Errors come back as an
 * HTTP status with a [SocialError] body naming the [SocialProblem].
 *
 * Friends
 * - [FRIENDS_PATH]: [SocialRequest] (with `profile`) → [FriendsResponse]. Also updates what
 *   friends see (name, title, badge) and marks the player as seen.
 * - [FRIEND_REQUEST_PATH], [FRIEND_ACCEPT_PATH], [FRIEND_DECLINE_PATH], [FRIEND_REMOVE_PATH]:
 *   [SocialRequest] with `target` → 204. A request to someone who already asked you makes you
 *   friends at once. Declining and removing are silent.
 * - [CHALLENGE_PATH]: [ChallengeRequest] → 204: invites a friend into a battle room (the code of a
 *   room created on `/battle`); it waits [com.deepline.fishing.core.GameConfig.CHALLENGE_MINUTES].
 *
 * Leaderboards: [LEADERBOARD_PATH]: [BoardRequest] → [BoardResponse].
 *
 * Tournament
 * - [TOURNAMENT_PATH]: [SocialRequest] → [TournamentInfo], with rewards waiting to be claimed.
 * - [TOURNAMENT_START_PATH]: [SocialRequest] → [AttemptStart] (uses an attempt, gives the seed).
 * - [TOURNAMENT_SUBMIT_PATH]: [SubmitRequest] → [SubmitResponse].
 * - [TOURNAMENT_CLAIM_PATH]: [ClaimRequest] (`week`) → 204, once the app has given the reward.
 *
 * Clubs (the server can switch them off with `DEEPLINE_CLUBS=off`: every club path is then 404)
 * - [CLUB_PATH]: [SocialRequest] → [MyClub]; [CLUB_CREATE_PATH]: [ClubNameRequest] → [MyClub];
 *   [CLUB_JOIN_PATH]: [ClubJoinRequest] → [MyClub]; [CLUB_RENAME_PATH]: [ClubNameRequest] → 204.
 * - [CLUB_LEAVE_PATH]: [SocialRequest] → 204; [CLUB_KICK_PATH], [CLUB_TRANSFER_PATH]:
 *   [SocialRequest] with `target` → 204 (owner only).
 * - [CLUB_CONTRIBUTE_PATH]: [ContributeRequest] → 204 (fish caught in solo casts, capped per day).
 * - [CLUB_CLAIM_PATH]: [ClaimRequest] (`keys`) → 204, once the app has given goal rewards.
 * - [CLUB_TOP_PATH]: [SocialRequest] → [TopClubs], this week's most active clubs.
 *
 * [FORGET_PATH]: [SocialRequest] → 204: deletes the player's social data (friendships on both
 * sides, requests, challenges, leaderboard and tournament entries, club membership).
 */
object SocialApi {
    const val FRIENDS_PATH = "/api/friends/list"
    const val FRIEND_REQUEST_PATH = "/api/friends/request"
    const val FRIEND_ACCEPT_PATH = "/api/friends/accept"
    const val FRIEND_DECLINE_PATH = "/api/friends/decline"
    const val FRIEND_REMOVE_PATH = "/api/friends/remove"
    const val CHALLENGE_PATH = "/api/friends/challenge"
    const val LEADERBOARD_PATH = "/api/leaderboards"
    const val TOURNAMENT_PATH = "/api/tournament"
    const val TOURNAMENT_START_PATH = "/api/tournament/start"
    const val TOURNAMENT_SUBMIT_PATH = "/api/tournament/submit"
    const val TOURNAMENT_CLAIM_PATH = "/api/tournament/claim"
    const val CLUB_PATH = "/api/clubs/mine"
    const val CLUB_CREATE_PATH = "/api/clubs/create"
    const val CLUB_JOIN_PATH = "/api/clubs/join"
    const val CLUB_RENAME_PATH = "/api/clubs/rename"
    const val CLUB_LEAVE_PATH = "/api/clubs/leave"
    const val CLUB_KICK_PATH = "/api/clubs/kick"
    const val CLUB_TRANSFER_PATH = "/api/clubs/transfer"
    const val CLUB_CONTRIBUTE_PATH = "/api/clubs/contribute"
    const val CLUB_CLAIM_PATH = "/api/clubs/claim"
    const val CLUB_TOP_PATH = "/api/clubs/top"
    const val FORGET_PATH = "/api/social/forget"

    /** Social request bodies are small (a cast is the biggest); anything larger is refused. */
    const val MAX_BODY_BYTES = 32 * 1024

    /** Clubs listed by [CLUB_TOP_PATH]. */
    const val TOP_CLUBS = 20

    /** Every club path, for switching clubs off on the server. */
    val clubPaths: List<String> = listOf(
        CLUB_PATH, CLUB_CREATE_PATH, CLUB_JOIN_PATH, CLUB_RENAME_PATH, CLUB_LEAVE_PATH, CLUB_KICK_PATH,
        CLUB_TRANSFER_PATH, CLUB_CONTRIBUTE_PATH, CLUB_CLAIM_PATH, CLUB_TOP_PATH,
    )
}

/** Why a social call failed (the body of an error response), and its HTTP status. */
@Serializable
enum class SocialProblem(val status: Int) {
    BAD_REQUEST(400),
    WRONG_SECRET(403),
    UNKNOWN_PLAYER(404),
    UNKNOWN_CLUB(404),
    CLUBS_OFF(404),
    NOT_FRIENDS(409),
    FRIENDS_FULL(409),
    THEIR_FRIENDS_FULL(409),
    ALREADY_IN_CLUB(409),
    NOT_IN_CLUB(409),
    NOT_OWNER(409),
    CLUB_FULL(409),
    REJOIN_LATER(409),
    NO_ATTEMPTS_LEFT(409),
    ATTEMPT_EXPIRED(409),
    CAST_REJECTED(422),
    TOO_BIG(413),
    TOO_MANY_REQUESTS(429),
}

@Serializable
data class SocialError(val problem: SocialProblem)

/**
 * What friends (and club members) see of a player: the name they chose, their title and its
 * badge frame. [clean] keeps only what the game knows: a sanitized name, a title that is a real
 * cosmetic, a real frame.
 */
@Serializable
data class Profile(val name: String = "Angler", val title: String? = null, val titleFrame: String? = null) {
    fun clean(): Profile = Profile(
        OnlineProtocol.sanitizeName(name),
        title?.takeIf { t -> Cosmetic.entries.any { it.slot == CosmeticSlot.TITLE && it.displayName == t } },
        titleFrame?.takeIf { f -> TitleFrame.entries.any { it.name == f } },
    )
}

// ---- Requests -------------------------------------------------------------------------------------

/** The plain request: who is asking, what friends see of them, and who it's about ([target]). */
@Serializable
data class SocialRequest(val id: String, val secret: String, val profile: Profile? = null, val target: String? = null)

@Serializable
data class ChallengeRequest(val id: String, val secret: String, val target: String, val code: String, val mode: BattleMode)

@Serializable
data class BoardRequest(val id: String, val secret: String, val board: BoardKind, val view: BoardView)

@Serializable
data class SubmitRequest(val id: String, val secret: String, val week: Long, val attempt: Int, val cast: CastDto)

/** Rewards the app has given: a tournament [week], or club goal [keys] ("week:tier"). */
@Serializable
data class ClaimRequest(val id: String, val secret: String, val week: Long? = null, val keys: List<String> = emptyList())

/** A generated club name: indexes in [Clubs.adjectives] and [Clubs.nouns]. */
@Serializable
data class ClubNameRequest(val id: String, val secret: String, val adjective: Int, val noun: Int, val profile: Profile? = null)

@Serializable
data class ClubJoinRequest(val id: String, val secret: String, val code: String, val profile: Profile? = null)

@Serializable
data class ContributeRequest(val id: String, val secret: String, val fish: Int)

// ---- Responses ------------------------------------------------------------------------------------

@Serializable
data class PlayerCard(val id: String, val name: String, val title: String? = null, val titleFrame: String? = null)

@Serializable
data class FriendDto(
    val id: String,
    val name: String,
    val title: String? = null,
    val titleFrame: String? = null,
    /** Seen by the server in the last few minutes. */
    val online: Boolean = false,
    /** This week's best cast, deepest catch (m) and best tournament score, if any. */
    val weekBestCast: Int? = null,
    val weekDeepestM: Float? = null,
    val weekTournament: Int? = null,
)

@Serializable
data class ChallengeDto(val from: String, val fromName: String, val code: String, val mode: BattleMode, val expiresAtMs: Long)

@Serializable
data class FriendsResponse(
    val friends: List<FriendDto> = emptyList(),
    val incoming: List<PlayerCard> = emptyList(),
    val outgoing: List<PlayerCard> = emptyList(),
    val challenges: List<ChallengeDto> = emptyList(),
)

/**
 * One row of a leaderboard. [id] is only given for the player and their friends: strangers are
 * shown by their generated angler name ([Leaderboards.anglerName]), never by id or typed name.
 */
@Serializable
data class BoardEntry(
    val rank: Int,
    val name: String,
    val value: Long,
    val speciesId: String? = null,
    val id: String? = null,
    val title: String? = null,
    val titleFrame: String? = null,
    val you: Boolean = false,
    val friend: Boolean = false,
)

@Serializable
data class BoardResponse(
    val board: BoardKind,
    val view: BoardView,
    val week: Long,
    val endsAtMs: Long,
    val entries: List<BoardEntry>,
    /** The player's own row (also when outside the entries shown), null before their first score. */
    val me: BoardEntry? = null,
    /** Players ranked in this view. */
    val players: Int = 0,
)

/** How a past tournament ended for the player, and whether the app has given its reward yet. */
@Serializable
data class TournamentResult(val week: Long, val rank: Int, val participants: Int, val best: Int, val tier: TournamentTier, val claimed: Boolean = false)

@Serializable
data class TournamentInfo(
    val week: Long,
    val startsAtMs: Long,
    val endsAtMs: Long,
    val attemptsMax: Int,
    val attemptsUsed: Int,
    /** The player's best score this week, rank and the number of players so far. */
    val best: Int? = null,
    val rank: Int? = null,
    val participants: Int = 0,
    val top: List<BoardEntry> = emptyList(),
    /** Finished tournaments whose reward is waiting. */
    val rewards: List<TournamentResult> = emptyList(),
)

@Serializable
data class AttemptStart(val week: Long, val attempt: Int, val seed: Long, val expiresAtMs: Long)

@Serializable
data class SubmitResponse(val week: Long, val attempt: Int, val points: Int, val best: Int, val rank: Int, val participants: Int)

@Serializable
data class ClubMember(
    val id: String,
    val name: String,
    val title: String? = null,
    val titleFrame: String? = null,
    val role: ClubRole = ClubRole.MEMBER,
    val weekFish: Int = 0,
    val online: Boolean = false,
)

/** A tier of a club's weekly goal the player can claim. */
@Serializable
data class ClubClaim(val week: Long, val tier: Int) {
    val key: String get() = Clubs.claimKey(week, tier)
}

@Serializable
data class ClubDto(
    val id: String,
    val name: String,
    val code: String,
    val week: Long,
    val endsAtMs: Long,
    /** Members, best contributors of the week first (the club's leaderboard). */
    val members: List<ClubMember>,
    val weekFish: Int,
    val tiersReached: Int,
    val claimable: List<ClubClaim> = emptyList(),
)

@Serializable
data class MyClub(val club: ClubDto? = null)

@Serializable
data class ClubSummary(val rank: Int, val name: String, val members: Int, val weekFish: Int, val mine: Boolean = false)

@Serializable
data class TopClubs(val week: Long, val clubs: List<ClubSummary>)
