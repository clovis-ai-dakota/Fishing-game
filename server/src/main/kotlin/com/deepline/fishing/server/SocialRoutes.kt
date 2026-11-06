package com.deepline.fishing.server

import com.deepline.fishing.core.social.BoardRequest
import com.deepline.fishing.core.social.ChallengeRequest
import com.deepline.fishing.core.social.ClaimRequest
import com.deepline.fishing.core.social.ClubJoinRequest
import com.deepline.fishing.core.social.ClubNameRequest
import com.deepline.fishing.core.social.ContributeRequest
import com.deepline.fishing.core.social.SocialApi
import com.deepline.fishing.core.social.SocialError
import com.deepline.fishing.core.social.SocialProblem
import com.deepline.fishing.core.social.SocialRequest
import com.deepline.fishing.core.social.SubmitRequest
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.plugins.origin
import io.ktor.server.request.receiveText
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

private val socialJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

/** Requests per IP address per minute, across every social call (flood protection). */
private const val REQUESTS_PER_IP_PER_MINUTE = 600

/** The social API (see [SocialApi]): friends, leaderboards, the tournament and clubs. */
fun Route.socialRoutes(social: SocialStore, limiter: RateLimiter = RateLimiter()) {
    /** A POST whose JSON body is a [request]; [block] returns the JSON answer, or null for 204. */
    fun <T> endpoint(path: String, request: KSerializer<T>, block: (T) -> String?) {
        post(path) { call.answer(limiter) { body -> block(socialJson.decodeFromString(request, body)) } }
    }
    fun <T> T.json(serializer: KSerializer<T>): String = socialJson.encodeToString(serializer, this)

    // Friends
    endpoint(SocialApi.FRIENDS_PATH, SocialRequest.serializer()) {
        social.friends(it.id, it.secret, it.profile).json(com.deepline.fishing.core.social.FriendsResponse.serializer())
    }
    endpoint(SocialApi.FRIEND_REQUEST_PATH, SocialRequest.serializer()) { social.requestFriend(it.id, it.secret, it.target); null }
    endpoint(SocialApi.FRIEND_ACCEPT_PATH, SocialRequest.serializer()) { social.acceptFriend(it.id, it.secret, it.target); null }
    endpoint(SocialApi.FRIEND_DECLINE_PATH, SocialRequest.serializer()) { social.declineFriend(it.id, it.secret, it.target); null }
    endpoint(SocialApi.FRIEND_REMOVE_PATH, SocialRequest.serializer()) { social.removeFriend(it.id, it.secret, it.target); null }
    endpoint(SocialApi.CHALLENGE_PATH, ChallengeRequest.serializer()) { social.challenge(it.id, it.secret, it.target, it.code, it.mode); null }

    // Leaderboards
    endpoint(SocialApi.LEADERBOARD_PATH, BoardRequest.serializer()) {
        social.leaderboard(it.id, it.secret, it.board, it.view).json(com.deepline.fishing.core.social.BoardResponse.serializer())
    }

    // Tournament
    endpoint(SocialApi.TOURNAMENT_PATH, SocialRequest.serializer()) {
        social.tournament(it.id, it.secret).json(com.deepline.fishing.core.social.TournamentInfo.serializer())
    }
    endpoint(SocialApi.TOURNAMENT_START_PATH, SocialRequest.serializer()) {
        social.startAttempt(it.id, it.secret).json(com.deepline.fishing.core.social.AttemptStart.serializer())
    }
    endpoint(SocialApi.TOURNAMENT_SUBMIT_PATH, SubmitRequest.serializer()) {
        social.submitAttempt(it.id, it.secret, it.week, it.attempt, it.cast).json(com.deepline.fishing.core.social.SubmitResponse.serializer())
    }
    endpoint(SocialApi.TOURNAMENT_CLAIM_PATH, ClaimRequest.serializer()) { social.claimTournament(it.id, it.secret, it.week); null }

    // Clubs
    val myClub = com.deepline.fishing.core.social.MyClub.serializer()
    endpoint(SocialApi.CLUB_PATH, SocialRequest.serializer()) { social.myClub(it.id, it.secret, it.profile).json(myClub) }
    endpoint(SocialApi.CLUB_CREATE_PATH, ClubNameRequest.serializer()) { social.createClub(it.id, it.secret, it.adjective, it.noun, it.profile).json(myClub) }
    endpoint(SocialApi.CLUB_JOIN_PATH, ClubJoinRequest.serializer()) { social.joinClub(it.id, it.secret, it.code, it.profile).json(myClub) }
    endpoint(SocialApi.CLUB_RENAME_PATH, ClubNameRequest.serializer()) { social.renameClub(it.id, it.secret, it.adjective, it.noun); null }
    endpoint(SocialApi.CLUB_LEAVE_PATH, SocialRequest.serializer()) { social.leaveClub(it.id, it.secret); null }
    endpoint(SocialApi.CLUB_KICK_PATH, SocialRequest.serializer()) { social.kick(it.id, it.secret, it.target); null }
    endpoint(SocialApi.CLUB_TRANSFER_PATH, SocialRequest.serializer()) { social.transfer(it.id, it.secret, it.target); null }
    endpoint(SocialApi.CLUB_CONTRIBUTE_PATH, ContributeRequest.serializer()) { social.contribute(it.id, it.secret, it.fish); null }
    endpoint(SocialApi.CLUB_CLAIM_PATH, ClaimRequest.serializer()) { social.claimClub(it.id, it.secret, it.keys); null }
    endpoint(SocialApi.CLUB_TOP_PATH, SocialRequest.serializer()) {
        social.topClubs(it.id, it.secret).json(com.deepline.fishing.core.social.TopClubs.serializer())
    }

    // Account deletion
    endpoint(SocialApi.FORGET_PATH, SocialRequest.serializer()) { social.forget(it.id, it.secret); null }
}

private suspend fun ApplicationCall.answer(limiter: RateLimiter, block: (String) -> String?) {
    val declared = request.headers[HttpHeaders.ContentLength]?.toLongOrNull()
    if (declared != null && declared > SocialApi.MAX_BODY_BYTES) return problem(SocialProblem.TOO_BIG)
    if (!limiter.allow("ip:" + request.origin.remoteHost, REQUESTS_PER_IP_PER_MINUTE, 60_000L)) return problem(SocialProblem.TOO_MANY_REQUESTS)
    val body = receiveText()
    if (body.length > SocialApi.MAX_BODY_BYTES) return problem(SocialProblem.TOO_BIG)
    try {
        val out = block(body)
        if (out == null) respond(HttpStatusCode.NoContent) else respondText(out, ContentType.Application.Json)
    } catch (e: SocialFailure) {
        problem(e.problem)
    } catch (e: SerializationException) {
        problem(SocialProblem.BAD_REQUEST)
    } catch (e: IllegalArgumentException) {
        problem(SocialProblem.BAD_REQUEST)
    }
}

private suspend fun ApplicationCall.problem(p: SocialProblem) =
    respondText(socialJson.encodeToString(SocialError.serializer(), SocialError(p)), ContentType.Application.Json, HttpStatusCode.fromValue(p.status))
