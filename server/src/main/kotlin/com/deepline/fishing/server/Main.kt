package com.deepline.fishing.server

import io.ktor.server.application.Application
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
    embeddedServer(Netty, port = port, host = "0.0.0.0") { battleServer() }.start(wait = true)
}

fun Application.battleServer(lobby: Lobby = Lobby()) {
    install(WebSockets) {
        pingPeriod = 15.seconds
        timeout = 30.seconds
        maxFrameSize = 64 * 1024
    }
    routing {
        get("/") { call.respondText("Deep Line battle server is running.\n") }
        get("/health") { call.respondText("ok") }
        webSocket("/battle") { lobby.handle(this) }
    }
}
