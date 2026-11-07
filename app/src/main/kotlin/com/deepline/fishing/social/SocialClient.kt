package com.deepline.fishing.social

import com.deepline.fishing.core.BattleMode
import com.deepline.fishing.core.PlayerId
import com.deepline.fishing.core.online.CastDto
import com.deepline.fishing.core.social.AttemptStart
import com.deepline.fishing.core.social.BoardKind
import com.deepline.fishing.core.social.BoardRequest
import com.deepline.fishing.core.social.BoardResponse
import com.deepline.fishing.core.social.BoardView
import com.deepline.fishing.core.social.ChallengeRequest
import com.deepline.fishing.core.social.ClaimRequest
import com.deepline.fishing.core.social.ClubJoinRequest
import com.deepline.fishing.core.social.ClubNameRequest
import com.deepline.fishing.core.social.ContributeRequest
import com.deepline.fishing.core.social.FriendsResponse
import com.deepline.fishing.core.social.MyClub
import com.deepline.fishing.core.social.Profile
import com.deepline.fishing.core.social.SocialApi
import com.deepline.fishing.core.social.SocialError
import com.deepline.fishing.core.social.SocialProblem
import com.deepline.fishing.core.social.SocialRequest
import com.deepline.fishing.core.social.SubmitRequest
import com.deepline.fishing.core.social.SubmitResponse
import com.deepline.fishing.core.social.TopClubs
import com.deepline.fishing.core.social.TournamentInfo
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json

/**
 * Friends, leaderboards, the weekly tournament and clubs on the battle server (see [SocialApi]).
 * Every call runs off the main thread and throws [SocialException] when it fails: [problem] says
 * why when the server answered, and is null when it couldn't be reached. Tests swap in a fake
 * (every call is open).
 */
open class SocialClient(private val baseUrl: () -> String) {
    class SocialException(val problem: SocialProblem?) : Exception(problem?.name ?: "unreachable")

    // Made on first use, so a player who never opens the social screens never opens a connection pool.
    private val lazyClient = lazy {
        HttpClient(OkHttp) {
            install(HttpTimeout) {
                connectTimeoutMillis = 5_000
                requestTimeoutMillis = 10_000
            }
        }
    }
    private val client by lazyClient
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private suspend fun post(path: String, body: String): String = withContext(Dispatchers.IO) {
        val response = try {
            client.post(baseUrl() + path) {
                contentType(ContentType.Application.Json)
                setBody(body)
            }
        } catch (e: Exception) {
            throw SocialException(null)
        }
        val text = runCatching { response.bodyAsText() }.getOrDefault("")
        if (!response.status.isSuccess()) {
            val problem = runCatching { json.decodeFromString(SocialError.serializer(), text).problem }.getOrNull()
            throw SocialException(problem ?: SocialProblem.BAD_REQUEST)
        }
        text
    }

    private suspend fun <Q, A> call(path: String, request: KSerializer<Q>, body: Q, answer: KSerializer<A>): A =
        json.decodeFromString(answer, post(path, json.encodeToString(request, body)))

    private suspend fun <Q> send(path: String, request: KSerializer<Q>, body: Q) {
        post(path, json.encodeToString(request, body))
    }

    private fun plain(who: PlayerId, target: String? = null, profile: Profile? = null) = SocialRequest(who.id, who.secret, profile, target)

    // ---- Friends -----------------------------------------------------------------------------------

    open suspend fun friends(who: PlayerId, profile: Profile): FriendsResponse =
        call(SocialApi.FRIENDS_PATH, SocialRequest.serializer(), plain(who, profile = profile), FriendsResponse.serializer())

    open suspend fun requestFriend(who: PlayerId, target: String) = send(SocialApi.FRIEND_REQUEST_PATH, SocialRequest.serializer(), plain(who, target))

    open suspend fun acceptFriend(who: PlayerId, target: String) = send(SocialApi.FRIEND_ACCEPT_PATH, SocialRequest.serializer(), plain(who, target))

    open suspend fun declineFriend(who: PlayerId, target: String) = send(SocialApi.FRIEND_DECLINE_PATH, SocialRequest.serializer(), plain(who, target))

    open suspend fun removeFriend(who: PlayerId, target: String) = send(SocialApi.FRIEND_REMOVE_PATH, SocialRequest.serializer(), plain(who, target))

    open suspend fun challenge(who: PlayerId, target: String, code: String, mode: BattleMode) =
        send(SocialApi.CHALLENGE_PATH, ChallengeRequest.serializer(), ChallengeRequest(who.id, who.secret, target, code, mode))

    // ---- Leaderboards and the tournament -------------------------------------------------------------

    open suspend fun leaderboard(who: PlayerId, board: BoardKind, view: BoardView): BoardResponse =
        call(SocialApi.LEADERBOARD_PATH, BoardRequest.serializer(), BoardRequest(who.id, who.secret, board, view), BoardResponse.serializer())

    open suspend fun tournament(who: PlayerId): TournamentInfo =
        call(SocialApi.TOURNAMENT_PATH, SocialRequest.serializer(), plain(who), TournamentInfo.serializer())

    open suspend fun startAttempt(who: PlayerId): AttemptStart =
        call(SocialApi.TOURNAMENT_START_PATH, SocialRequest.serializer(), plain(who), AttemptStart.serializer())

    open suspend fun submitAttempt(who: PlayerId, week: Long, attempt: Int, cast: CastDto): SubmitResponse =
        call(SocialApi.TOURNAMENT_SUBMIT_PATH, SubmitRequest.serializer(), SubmitRequest(who.id, who.secret, week, attempt, cast), SubmitResponse.serializer())

    open suspend fun claimTournament(who: PlayerId, week: Long) =
        send(SocialApi.TOURNAMENT_CLAIM_PATH, ClaimRequest.serializer(), ClaimRequest(who.id, who.secret, week = week))

    // ---- Clubs (only called while FeatureFlags.clubs is on) -------------------------------------------

    open suspend fun myClub(who: PlayerId, profile: Profile): MyClub =
        call(SocialApi.CLUB_PATH, SocialRequest.serializer(), plain(who, profile = profile), MyClub.serializer())

    open suspend fun createClub(who: PlayerId, profile: Profile, adjective: Int, noun: Int): MyClub =
        call(SocialApi.CLUB_CREATE_PATH, ClubNameRequest.serializer(), ClubNameRequest(who.id, who.secret, adjective, noun, profile), MyClub.serializer())

    open suspend fun joinClub(who: PlayerId, profile: Profile, code: String): MyClub =
        call(SocialApi.CLUB_JOIN_PATH, ClubJoinRequest.serializer(), ClubJoinRequest(who.id, who.secret, code, profile), MyClub.serializer())

    open suspend fun renameClub(who: PlayerId, adjective: Int, noun: Int) =
        send(SocialApi.CLUB_RENAME_PATH, ClubNameRequest.serializer(), ClubNameRequest(who.id, who.secret, adjective, noun))

    open suspend fun leaveClub(who: PlayerId) = send(SocialApi.CLUB_LEAVE_PATH, SocialRequest.serializer(), plain(who))

    open suspend fun kick(who: PlayerId, member: String) = send(SocialApi.CLUB_KICK_PATH, SocialRequest.serializer(), plain(who, member))

    open suspend fun transfer(who: PlayerId, member: String) = send(SocialApi.CLUB_TRANSFER_PATH, SocialRequest.serializer(), plain(who, member))

    open suspend fun contribute(who: PlayerId, fish: Int) =
        send(SocialApi.CLUB_CONTRIBUTE_PATH, ContributeRequest.serializer(), ContributeRequest(who.id, who.secret, fish))

    open suspend fun claimClub(who: PlayerId, keys: List<String>) =
        send(SocialApi.CLUB_CLAIM_PATH, ClaimRequest.serializer(), ClaimRequest(who.id, who.secret, keys = keys))

    open suspend fun topClubs(who: PlayerId): TopClubs =
        call(SocialApi.CLUB_TOP_PATH, SocialRequest.serializer(), plain(who), TopClubs.serializer())

    // ---- Account -------------------------------------------------------------------------------------

    /** Deletes the player's friends, rankings and club membership on the server. */
    open suspend fun forget(who: PlayerId) = send(SocialApi.FORGET_PATH, SocialRequest.serializer(), plain(who))

    fun close() {
        if (lazyClient.isInitialized()) client.close()
    }
}
