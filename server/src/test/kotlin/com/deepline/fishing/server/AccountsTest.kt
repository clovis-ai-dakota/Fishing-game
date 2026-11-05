package com.deepline.fishing.server

import com.deepline.fishing.core.BattleMode
import com.deepline.fishing.core.PlayerId
import com.deepline.fishing.core.online.AccountApi
import com.deepline.fishing.core.online.AckRequest
import com.deepline.fishing.core.online.AuthRequest
import com.deepline.fishing.core.online.ClientMessage
import com.deepline.fishing.core.online.GiftRequest
import com.deepline.fishing.core.online.Inbox
import com.deepline.fishing.core.online.OnlineProtocol
import com.deepline.fishing.core.online.SaveRequest
import com.deepline.fishing.core.online.SaveResponse
import com.deepline.fishing.core.online.ServerMessage
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocketSession
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class AccountsTest {
    private val alice = PlayerId.random()
    private val bob = PlayerId.random()

    @Test
    fun recoveryCodesRoundTrip() {
        val code = alice.recoveryCode
        assertTrue(code.startsWith("DL-"))
        assertEquals(alice, PlayerId.fromRecoveryCode(code))
        assertEquals(alice, PlayerId.fromRecoveryCode(code.lowercase().replace("-", " ")))
        assertEquals(null, PlayerId.fromRecoveryCode("DL-1234"))
    }

    @Test
    fun savesNeedTheRightSecretAndSurviveARestart() {
        val dir = Files.createTempDirectory("accounts").toFile()
        val store = AccountStore(dir) { 42L }
        store.save(alice.id, alice.secret, "progress v1")
        assertFailsWith<AccountStore.Failure> { store.load(alice.id, bob.secret) }
        // A new server process reads the files back.
        val again = AccountStore(dir)
        assertEquals("progress v1", again.load(alice.id, alice.secret).save)
        assertEquals(42L, again.load(alice.id, alice.secret).savedAtMs)
        assertFailsWith<AccountStore.Failure> { again.save(alice.id, alice.secret, "x".repeat(AccountApi.MAX_SAVE_BYTES + 1)) }
        dir.deleteRecursively()
    }

    @Test
    fun giftsWaitUntilAcknowledged() {
        val store = AccountStore()
        store.sendGift(alice.id, alice.secret, "Alice", bob.id, "gear_neon_line")
        // Bob hadn't used the API yet: his first request claims the id and the gift.
        val inbox = store.inbox(bob.id, bob.secret)
        assertEquals(listOf("gear_neon_line"), inbox.map { it.itemId })
        assertEquals("Alice", inbox.single().fromName)
        assertEquals(1, store.inbox(bob.id, bob.secret).size, "kept until acknowledged")
        store.ackGifts(bob.id, bob.secret, inbox.map { it.id })
        assertTrue(store.inbox(bob.id, bob.secret).isEmpty())
        // Passes and memberships can't be given; nor can unknown items.
        for (bad in listOf("captains_club", "season_pass", "nope")) {
            assertFailsWith<AccountStore.Failure> { store.sendGift(alice.id, alice.secret, "Alice", bob.id, bad) }
        }
        assertFailsWith<AccountStore.Failure> { store.sendGift(alice.id, bob.secret, "Alice", bob.id, "gear_neon_line") }
    }

    private fun ApplicationTestBuilder.setup() = application { battleServer(Lobby(), AccountStore()) }

    private val json = Json

    @Test
    fun theHttpApi() = testApplication {
        setup()
        suspend fun post(path: String, body: String) = client.post(path) { setBody(body) }
        val saved = post(AccountApi.SAVE_PATH, json.encodeToString(SaveRequest.serializer(), SaveRequest(alice.id, alice.secret, "my save")))
        assertEquals(HttpStatusCode.NoContent, saved.status)
        val loaded = post(AccountApi.LOAD_PATH, json.encodeToString(AuthRequest.serializer(), AuthRequest(alice.id, alice.secret)))
        assertEquals("my save", json.decodeFromString(SaveResponse.serializer(), loaded.bodyAsText()).data)
        assertEquals(HttpStatusCode.Forbidden, post(AccountApi.LOAD_PATH, json.encodeToString(AuthRequest.serializer(), AuthRequest(alice.id, bob.secret))).status)
        assertEquals(HttpStatusCode.NotFound, post(AccountApi.LOAD_PATH, json.encodeToString(AuthRequest.serializer(), AuthRequest(bob.id, bob.secret))).status)
        assertEquals(HttpStatusCode.BadRequest, post(AccountApi.LOAD_PATH, "{not json").status)

        val gift = post(AccountApi.SEND_GIFT_PATH, json.encodeToString(GiftRequest.serializer(), GiftRequest(alice.id, alice.secret, "Alice", bob.id, "lantern_pouch")))
        assertEquals(HttpStatusCode.NoContent, gift.status)
        val inbox = json.decodeFromString(Inbox.serializer(), post(AccountApi.INBOX_PATH, json.encodeToString(AuthRequest.serializer(), AuthRequest(bob.id, bob.secret))).bodyAsText())
        assertEquals("lantern_pouch", inbox.gifts.single().itemId)
        post(AccountApi.ACK_PATH, json.encodeToString(AckRequest.serializer(), AckRequest(bob.id, bob.secret, inbox.gifts.map { it.id })))
        val after = json.decodeFromString(Inbox.serializer(), post(AccountApi.INBOX_PATH, json.encodeToString(AuthRequest.serializer(), AuthRequest(bob.id, bob.secret))).bodyAsText())
        assertTrue(after.gifts.isEmpty())
    }

    @Test
    fun opponentsSeeEachOthersFrameAndId() = testApplication {
        setup()
        suspend fun join(name: String, id: PlayerId, frame: String?) = createClient { install(WebSockets) }.webSocketSession("/battle").also {
            it.incoming.receive()
            it.send(Frame.Text(OnlineProtocol.encode(ClientMessage.Hello(name, titleFrame = frame, playerId = id.id))))
        }
        val a = join("A", alice, "OCEAN")
        val b = join("B", bob, "NOT_A_FRAME")
        a.send(Frame.Text(OnlineProtocol.encode(ClientMessage.QuickMatch(BattleMode.HEAVY_HAUL))))
        b.send(Frame.Text(OnlineProtocol.encode(ClientMessage.QuickMatch(BattleMode.HEAVY_HAUL))))
        suspend fun started(s: io.ktor.client.plugins.websocket.DefaultClientWebSocketSession): ServerMessage.MatchStarted = withTimeout(5_000) {
            while (true) {
                val f = s.incoming.receive() as? Frame.Text ?: continue
                val m = OnlineProtocol.decodeServer(f.readText())
                if (m is ServerMessage.MatchStarted) return@withTimeout m
            }
            @Suppress("UNREACHABLE_CODE")
            error("unreachable")
        }
        val m = started(a)
        started(b)
        assertEquals(setOf(alice.id, bob.id), m.playerIds.toSet())
        // Unknown frames aren't relayed.
        assertEquals(setOf("OCEAN", null), m.playerFrames.toSet())
    }
}
