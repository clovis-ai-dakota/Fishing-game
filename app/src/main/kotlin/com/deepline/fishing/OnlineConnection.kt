package com.deepline.fishing

import com.deepline.fishing.core.online.ClientMessage
import com.deepline.fishing.core.online.OnlineProtocol
import com.deepline.fishing.core.online.ServerMessage
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * One WebSocket connection to the battle server. Messages are delivered on the main thread.
 * [onClosed] is called once if the connection fails or drops (not after [close]).
 */
class OnlineConnection(
    private val scope: CoroutineScope,
    private val onMessage: (ServerMessage) -> Unit,
    private val onClosed: (String) -> Unit,
) {
    private val client = HttpClient(OkHttp) { install(WebSockets) }
    private val outbox = Channel<ClientMessage>(Channel.UNLIMITED)
    private var job: Job? = null

    @Volatile
    private var closedByUser = false

    fun connect(url: String, playerName: String, title: String? = null, titleFrame: String? = null, playerId: String? = null) {
        job = scope.launch {
            var error: String? = null
            try {
                client.webSocket(urlString = url) {
                    send(Frame.Text(OnlineProtocol.encode(ClientMessage.Hello(playerName, title = title, titleFrame = titleFrame, playerId = playerId))))
                    val writer = launch {
                        for (m in outbox) send(Frame.Text(OnlineProtocol.encode(m)))
                    }
                    try {
                        for (frame in incoming) {
                            if (frame !is Frame.Text) continue
                            val message = OnlineProtocol.decodeServer(frame.readText()) ?: continue
                            withContext(Dispatchers.Main.immediate) { onMessage(message) }
                        }
                    } finally {
                        writer.cancel()
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = e.message ?: e::class.simpleName
            }
            if (!closedByUser) {
                withContext(Dispatchers.Main.immediate) {
                    onClosed(error?.let { "Could not reach the server ($it)." } ?: "Disconnected from the server.")
                }
            }
        }
    }

    fun send(message: ClientMessage) {
        outbox.trySend(message)
    }

    fun close() {
        closedByUser = true
        job?.cancel()
        outbox.close()
        client.close()
    }
}
