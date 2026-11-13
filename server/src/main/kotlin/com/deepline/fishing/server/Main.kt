package com.deepline.fishing.server

import com.deepline.fishing.core.online.AccountApi
import com.deepline.fishing.core.online.AckRequest
import com.deepline.fishing.core.online.AuthRequest
import com.deepline.fishing.core.online.GiftRequest
import com.deepline.fishing.core.online.Inbox
import com.deepline.fishing.core.online.SaveRequest
import com.deepline.fishing.core.online.SaveResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.File
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.pingPeriod
import io.ktor.server.websocket.timeout
import io.ktor.server.websocket.webSocket
import kotlin.time.Duration.Companion.seconds

fun main() {
    val port = System.getenv("PORT")?.toIntOrNull() ?: 8080
    // Gifts and cloud saves are kept as files here (one per player).
    val data = File(System.getenv("DEEPLINE_DATA_DIR") ?: "data/accounts")
    val accounts = AccountStore(data)
    // Friends, weekly leaderboards and tournaments, clubs: one file per player, week and club.
    val social = SocialStore(
        accounts,
        File(System.getenv("DEEPLINE_SOCIAL_DIR") ?: "data/social"),
        // A secret salt keeps each week's tournament water unknown until the week starts.
        tournamentSalt = System.getenv("DEEPLINE_TOURNAMENT_SALT")?.toLongOrNull() ?: 0L,
        // Clubs ship in a later app update; "off" makes every club call answer 404.
        clubsEnabled = System.getenv("DEEPLINE_CLUBS")?.lowercase() != "off",
    )
    // Behind a hosting proxy: the header it sets to the player's address (for rate limits).
    val clientIpHeader = System.getenv("DEEPLINE_CLIENT_IP_HEADER")?.takeIf { it.isNotBlank() }
    embeddedServer(Netty, port = port, host = "0.0.0.0") {
        battleServer(accounts = accounts, social = social, clientIpHeader = clientIpHeader)
    }.start(wait = true)
}

fun Application.battleServer(
    lobby: Lobby = Lobby(),
    accounts: AccountStore = AccountStore(),
    social: SocialStore = SocialStore(accounts),
    clientIpHeader: String? = null,
) {
    // Verified players' battle casts count on the weekly leaderboards.
    lobby.verifyPlayer = { id, secret ->
        runCatching { accounts.authenticate(id, secret) }.isSuccess.also { ok -> if (ok) social.seen(id) }
    }
    lobby.onValidatedCast = social::recordBattleCast
    // Deleting an account erases every social trace of the player too (friends, boards, clubs).
    accounts.onDelete(social::forgetPlayer)
    install(WebSockets) {
        pingPeriod = 15.seconds
        timeout = 30.seconds
        maxFrameSize = 64 * 1024
    }
    routing {
        get("/") { call.respondText("Deep Line battle server is running.\n") }
        get("/health") { call.respondText("ok") }
        webSocket("/battle") { lobby.handle(this) }
        accountRoutes(accounts)
        accountDeletionPage(accounts)
        socialRoutes(social, clientIpHeader = clientIpHeader)
    }
}

private val apiJson = Json { ignoreUnknownKeys = true }

/** The biggest account request: a cloud save, even with every character escaped in the JSON. */
private const val MAX_ACCOUNT_BODY_BYTES = 6 * AccountApi.MAX_SAVE_BYTES + 64 * 1024

/** The gift and cloud save API (see [AccountApi]). */
private fun Route.accountRoutes(accounts: AccountStore) {
    suspend fun ApplicationCall.handle(block: suspend (String) -> String?) {
        val body = receiveTextAtMost(MAX_ACCOUNT_BODY_BYTES) ?: return respond(HttpStatusCode.PayloadTooLarge)
        try {
            val out = block(body)
            if (out == null) respond(HttpStatusCode.NoContent) else respondText(out, ContentType.Application.Json)
        } catch (e: AccountStore.Failure) {
            respond(
                when (e.problem) {
                    AccountStore.Problem.BAD_REQUEST -> HttpStatusCode.BadRequest
                    AccountStore.Problem.FORBIDDEN -> HttpStatusCode.Forbidden
                    AccountStore.Problem.TOO_BIG -> HttpStatusCode.PayloadTooLarge
                    AccountStore.Problem.NOT_FOUND -> HttpStatusCode.NotFound
                },
            )
        } catch (e: SerializationException) {
            respond(HttpStatusCode.BadRequest)
        } catch (e: IllegalArgumentException) {
            respond(HttpStatusCode.BadRequest)
        }
    }
    post(AccountApi.SAVE_PATH) {
        call.handle { b -> apiJson.decodeFromString(SaveRequest.serializer(), b).let { accounts.save(it.id, it.secret, it.data) }; null }
    }
    post(AccountApi.LOAD_PATH) {
        call.handle { b ->
            val r = apiJson.decodeFromString(AuthRequest.serializer(), b)
            val a = accounts.load(r.id, r.secret)
            apiJson.encodeToString(SaveResponse.serializer(), SaveResponse(a.save!!, a.savedAtMs))
        }
    }
    post(AccountApi.SEND_GIFT_PATH) {
        call.handle { b -> apiJson.decodeFromString(GiftRequest.serializer(), b).let { accounts.sendGift(it.from, it.secret, it.fromName, it.to, it.itemId) }; null }
    }
    post(AccountApi.INBOX_PATH) {
        call.handle { b ->
            val r = apiJson.decodeFromString(AuthRequest.serializer(), b)
            apiJson.encodeToString(Inbox.serializer(), Inbox(accounts.inbox(r.id, r.secret)))
        }
    }
    post(AccountApi.ACK_PATH) {
        call.handle { b -> apiJson.decodeFromString(AckRequest.serializer(), b).let { accounts.ackGifts(it.id, it.secret, it.giftIds) }; null }
    }
    post(AccountApi.DELETE_ACCOUNT_PATH) {
        call.handle { b -> apiJson.decodeFromString(AuthRequest.serializer(), b).let { accounts.delete(it.id, it.secret) }; null }
    }
}
