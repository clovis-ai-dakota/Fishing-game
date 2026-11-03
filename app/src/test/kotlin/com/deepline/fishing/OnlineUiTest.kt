package com.deepline.fishing

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.deepline.fishing.core.BattleMode
import com.deepline.fishing.core.BotDifficulty
import com.deepline.fishing.core.BotPlayer
import com.deepline.fishing.core.online.ClientMessage
import com.deepline.fishing.core.online.OnlineMatch
import com.deepline.fishing.core.online.OnlineProtocol
import com.deepline.fishing.core.online.ServerMessage
import com.deepline.fishing.core.online.toDto
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.net.HttpURLConnection
import java.net.ServerSocket
import java.net.URL
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread

/**
 * End to end: the real battle server runs as a separate process, the app creates a room
 * through its UI, and a scripted opponent joins with the code. Both play a full Heavy Haul.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w400dp-h860dp-xxhdpi")
class OnlineUiTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    private var server: Process? = null
    private var port = 0
    private var scenario: ActivityScenario<MainActivity>? = null

    @Before
    fun startServer() {
        port = ServerSocket(0).use { it.localPort }
        val bin = System.getProperty("deepline.serverBin") ?: error("deepline.serverBin not set")
        server = ProcessBuilder(bin).apply {
            environment()["PORT"] = port.toString()
            redirectErrorStream(true)
            redirectOutput(File("build/online-test-server.log"))
        }.start()
        val deadline = System.currentTimeMillis() + 30_000
        while (System.currentTimeMillis() < deadline) {
            val up = runCatching {
                (URL("http://127.0.0.1:$port/health").openConnection() as HttpURLConnection).run {
                    connectTimeout = 500
                    responseCode == 200
                }
            }.getOrDefault(false)
            if (up) return
            Thread.sleep(200)
        }
        error("Battle server did not start")
    }

    /** Saved settings can outlive a test in the same class: every test starts from a clean slate. */
    @org.junit.Before
    fun clearSavedState() {
        ApplicationProvider.getApplicationContext<android.content.Context>()
            .getSharedPreferences("deepline", android.content.Context.MODE_PRIVATE)
            .edit().clear().commit()
    }

    @After
    fun stop() {
        scenario?.close()
        server?.destroy()
    }

    private fun ComposeTestRule.click(text: String) {
        val matcher = hasText(text)
        if (onAllNodes(matcher).fetchSemanticsNodes().isEmpty()) {
            onAllNodes(hasScrollAction()).onFirst().performScrollToNode(matcher)
        }
        val node = onNode(matcher)
        runCatching { node.performScrollTo() }
        node.performClick()
    }

    /**
     * Plays the current cast to its end (up to [maxSeconds] of game time). If the last bump hooks
     * something for the first time, the cast pauses to explain the secret: answer it and go on.
     */
    private fun ComposeTestRule.playOutCast(endText: String, maxSeconds: Int = 150) {
        var elapsed = 0
        while (elapsed < maxSeconds) {
            mainClock.autoAdvance = false
            repeat(2 * 60) { mainClock.advanceTimeBy(16) }
            elapsed += 2
            if (onAllNodes(hasText("Fishing secret discovered!")).fetchSemanticsNodes().isNotEmpty()) {
                // The cast is paused behind the dialog. Answer it, then let the dialog window
                // close for a few frames so it isn't mistaken for a new one.
                if (onAllNodes(hasText("No thanks")).fetchSemanticsNodes().isNotEmpty()) {
                    onNode(hasText("No thanks")).performClick()
                }
                repeat(10) { mainClock.advanceTimeByFrame() }
                continue
            }
            if (onAllNodes(hasText(endText)).fetchSemanticsNodes().isNotEmpty()) break
        }
        mainClock.autoAdvance = true
    }

    private fun ComposeTestRule.waitFor(matcher: SemanticsMatcher, millis: Long = 15_000) =
        waitUntil(millis) { onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty() }

    private fun ComposeTestRule.shot(name: String) {
        waitForIdle()
        lateinit var bitmap: Bitmap
        scenario!!.onActivity { a ->
            val v = a.window.decorView
            bitmap = Bitmap.createBitmap(v.width, v.height, Bitmap.Config.ARGB_8888)
            v.draw(Canvas(bitmap))
        }
        val dir = File("build/screenshots").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    /** A scripted opponent: joins the room, fishes its 3 casts with the bot, then reads to the end. */
    private fun startOpponent(code: String, result: AtomicReference<ServerMessage.MatchOver?>) = thread(isDaemon = true) {
        runBlocking {
            HttpClient(OkHttp) { install(WebSockets) }.use { client ->
                client.webSocket("ws://127.0.0.1:$port/battle") {
                    suspend fun send(m: ClientMessage) = send(Frame.Text(OnlineProtocol.encode(m)))
                    send(ClientMessage.Hello("Scripted Rival"))
                    send(ClientMessage.JoinRoom(code))
                    var match: OnlineMatch? = null
                    for (frame in incoming) {
                        if (frame !is Frame.Text) continue
                        when (val m = OnlineProtocol.decodeServer(frame.readText())) {
                            is ServerMessage.MatchStarted -> {
                                match = OnlineMatch(m)
                                repeat(3) {
                                    val turn = match.battle.nextTurnFor(match.myIndex)!!
                                    val cast = BotPlayer.playCast(OnlineProtocol.tournamentSpecs, turn.seed, BotDifficulty.NORMAL)
                                    send(ClientMessage.CastFinished(turn, cast.toDto()))
                                    // Record locally right away so the next turn number is correct.
                                    match.apply(ServerMessage.CastRecorded(turn, cast.toDto()))
                                }
                            }
                            is ServerMessage.MatchOver -> {
                                result.set(m)
                                return@webSocket
                            }
                            else -> Unit
                        }
                    }
                }
            }
        }
    }

    @Test
    fun appPlaysAFullOnlineMatch() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        SharedPrefsStore(context).apply {
            putString("online.server", "ws://127.0.0.1:$port/battle")
            putString("online.name", "Tester")
            putString("tour.showAgain", "false")
            putString("tour.seenVersion", com.deepline.fishing.ui.tour.Tour.VERSION.toString())
        }
        scenario = ActivityScenario.launch(MainActivity::class.java)

        compose.click("1v1 Battle")
        compose.click("Heavy Haul")
        compose.click("Online (room code or quick match)")
        compose.click("Go online")
        compose.click("Create room")
        compose.waitFor(hasText("Share this room code"))
        compose.shot("16_online_room_code")

        lateinit var vm: GameViewModel
        scenario!!.onActivity { vm = ViewModelProvider(it)[GameViewModel::class.java] }
        val code = checkNotNull(vm.roomCode) { "no room code" }

        val opponentResult = AtomicReference<ServerMessage.MatchOver?>(null)
        startOpponent(code, opponentResult)

        repeat(3) { i ->
            compose.waitFor(hasText("Cast!"))
            if (i == 0) compose.shot("17_online_battle_hub")
            compose.click("Cast!")
            compose.playOutCast("Continue")
            compose.click("Continue")
        }

        compose.waitFor(hasText("wins!", substring = true) or hasText("It's a draw!"))
        compose.shot("18_online_result")
        // Both players' third casts are listed on screen (not just recorded in the model).
        compose.onAllNodes(hasText("#3:", substring = true)).assertCountEquals(2)

        val deadline = System.currentTimeMillis() + 10_000
        while (opponentResult.get() == null && System.currentTimeMillis() < deadline) Thread.sleep(50)
        val over = checkNotNull(opponentResult.get()) { "opponent never saw the end of the match" }
        val mine = checkNotNull(vm.onlineMatch?.outcome)
        assertEquals("both sides agree on the winner", over.winner, mine.winner)
        assertEquals(3, vm.onlineMatch!!.battle.castsOf(0).size)
        assertEquals(3, vm.onlineMatch!!.battle.castsOf(1).size)
    }
}
