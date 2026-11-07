package com.deepline.fishing

import com.deepline.fishing.core.PlayerId
import com.deepline.fishing.core.online.AccountApi
import com.deepline.fishing.core.online.AckRequest
import com.deepline.fishing.core.online.AuthRequest
import com.deepline.fishing.core.online.Gift
import com.deepline.fishing.core.online.GiftRequest
import com.deepline.fishing.core.online.Inbox
import com.deepline.fishing.core.online.SaveRequest
import com.deepline.fishing.core.online.SaveResponse
import com.deepline.fishing.ui.i18n.UiText
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

/**
 * Gifts, cloud saves and account deletion on the battle server (see [AccountApi]). Every call
 * runs off the main thread and throws [AccountException] with a message for the player when it
 * fails ([AccountException.text], in strings_shop.xml).
 */
open class AccountClient(private val baseUrl: () -> String) {
    /** A failed call. [text] is what the player is told; the exception message stays technical. */
    class AccountException(val text: UiText, message: String) : Exception(message) {
        /** A failure with text that is already final (tests, a server's own words). */
        constructor(message: String) : this(UiText.raw(message), message)
    }

    // Made on first use, so a player who never goes online never opens a connection pool.
    private val lazyClient = lazy {
        HttpClient(OkHttp) {
            install(HttpTimeout) {
                connectTimeoutMillis = 5_000
                requestTimeoutMillis = 10_000
            }
        }
    }
    private val client by lazyClient
    private val json = Json { ignoreUnknownKeys = true }

    private suspend fun post(path: String, body: String): Pair<HttpStatusCode, String> = withContext(Dispatchers.IO) {
        try {
            val r = client.post(baseUrl() + path) {
                contentType(ContentType.Application.Json)
                setBody(body)
            }
            r.status to r.bodyAsText()
        } catch (e: Exception) {
            throw AccountException(UiText.res(R.string.shop_account_unreachable), "unreachable")
        }
    }

    private fun check(status: HttpStatusCode) {
        if (status.isSuccess()) return
        throw AccountException(
            when (status) {
                HttpStatusCode.Forbidden -> UiText.res(R.string.shop_account_wrong_code)
                HttpStatusCode.NotFound -> UiText.res(R.string.shop_account_nothing_saved)
                HttpStatusCode.PayloadTooLarge -> UiText.res(R.string.shop_account_too_large)
                HttpStatusCode.BadRequest -> UiText.res(R.string.shop_account_refused)
                else -> UiText.res(R.string.shop_account_server_problem, status.value)
            },
            "HTTP ${status.value}",
        )
    }

    open suspend fun save(who: PlayerId, data: String) {
        check(post(AccountApi.SAVE_PATH, json.encodeToString(SaveRequest.serializer(), SaveRequest(who.id, who.secret, data))).first)
    }

    open suspend fun load(who: PlayerId): SaveResponse {
        val (status, body) = post(AccountApi.LOAD_PATH, json.encodeToString(AuthRequest.serializer(), AuthRequest(who.id, who.secret)))
        check(status)
        return json.decodeFromString(SaveResponse.serializer(), body)
    }

    open suspend fun sendGift(from: PlayerId, fromName: String, to: String, itemId: String) {
        check(post(AccountApi.SEND_GIFT_PATH, json.encodeToString(GiftRequest.serializer(), GiftRequest(from.id, from.secret, fromName, to, itemId))).first)
    }

    open suspend fun inbox(who: PlayerId): List<Gift> {
        val (status, body) = post(AccountApi.INBOX_PATH, json.encodeToString(AuthRequest.serializer(), AuthRequest(who.id, who.secret)))
        check(status)
        return json.decodeFromString(Inbox.serializer(), body).gifts
    }

    open suspend fun ack(who: PlayerId, giftIds: List<String>) {
        check(post(AccountApi.ACK_PATH, json.encodeToString(AckRequest.serializer(), AckRequest(who.id, who.secret, giftIds))).first)
    }

    /** Erases this player's account on the server (Settings > Privacy). */
    open suspend fun deleteAccount(who: PlayerId) {
        check(post(AccountApi.DELETE_ACCOUNT_PATH, json.encodeToString(AuthRequest.serializer(), AuthRequest(who.id, who.secret))).first)
    }

    fun close() {
        if (lazyClient.isInitialized()) client.close()
    }
}
