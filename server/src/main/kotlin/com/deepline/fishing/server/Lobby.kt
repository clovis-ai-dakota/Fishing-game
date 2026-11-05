package com.deepline.fishing.server

import com.deepline.fishing.core.Battle
import com.deepline.fishing.core.BattleMode
import com.deepline.fishing.core.BattlePlayer
import com.deepline.fishing.core.Cosmetic
import com.deepline.fishing.core.CosmeticSlot
import com.deepline.fishing.core.online.CastValidation
import com.deepline.fishing.core.online.ClientMessage
import com.deepline.fishing.core.online.OnlineProtocol
import com.deepline.fishing.core.online.ServerMessage
import com.deepline.fishing.core.online.toDto
import io.ktor.websocket.Frame
import io.ktor.websocket.WebSocketSession
import io.ktor.websocket.readText
import kotlinx.coroutines.channels.ClosedSendChannelException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.slf4j.LoggerFactory
import kotlin.random.Random

/** One connected player. */
class Connection(private val session: WebSocketSession, val id: Long) {
    var name: String = "Angler"
    var title: String? = null
    var titleFrame: String? = null
    var playerId: String? = null
    var match: Match? = null
    var playerIndex: Int = -1
    var waitingRoom: String? = null
    var waitingQuick: BattleMode? = null

    val isBusy: Boolean get() = (match?.isFinished == false) || waitingRoom != null || waitingQuick != null

    suspend fun send(message: ServerMessage) {
        try {
            session.send(Frame.Text(OnlineProtocol.encode(message)))
        } catch (_: ClosedSendChannelException) {
            // The player left; the disconnect is handled by the lobby.
        } catch (_: Exception) {
        }
    }
}

/** An online battle between two connections. The server's [battle] is authoritative. */
class Match(val mode: BattleMode, val players: List<Connection>, seed: Long) {
    val battle = Battle(mode, players.map { BattlePlayer(it.name) }, seed)
    private val mutex = Mutex()

    @Volatile
    var isFinished = false
        private set

    suspend fun start() {
        players.forEachIndexed { i, c ->
            c.match = this
            c.playerIndex = i
        }
        val names = players.map { it.name }
        val titles = players.map { it.title }
        val frames = players.map { it.titleFrame }
        val ids = players.map { it.playerId }
        players.forEachIndexed { i, c ->
            c.send(ServerMessage.MatchStarted(mode, battle.seed, i, names, titles, frames, ids))
        }
    }

    suspend fun onCast(player: Int, message: ClientMessage.CastFinished) = mutex.withLock {
        if (isFinished) return@withLock
        val expected = battle.nextTurnFor(player)
        if (expected == null || message.turn != expected) {
            players[player].send(ServerMessage.Error("That cast is not expected right now."))
            return@withLock
        }
        val cast = CastValidation.toBattleCast(message.cast, expected, OnlineProtocol.tournamentSpecs)
        if (cast == null) {
            players[player].send(ServerMessage.Error("That cast is not possible with the tournament rope."))
            return@withLock
        }
        battle.record(expected, cast)
        val recorded = ServerMessage.CastRecorded(expected, cast.toDto())
        players.forEach { it.send(recorded) }
        battle.outcome()?.let { finish(ServerMessage.MatchOver(it.winner, it.byForfeit)) }
    }

    suspend fun onLive(player: Int, message: ClientMessage.Live) {
        if (!isFinished) players[1 - player].send(ServerMessage.OpponentLive(message.status))
    }

    suspend fun onForfeit(player: Int, reason: String) = mutex.withLock {
        if (isFinished) return@withLock
        battle.forfeit(player)
        finish(ServerMessage.MatchOver(winner = 1 - player, byForfeit = true, reason = reason))
    }

    private suspend fun finish(message: ServerMessage.MatchOver) {
        isFinished = true
        players.forEach { it.send(message) }
    }
}

/** Pairs players (room codes and quick match) and routes their messages to matches. */
class Lobby(private val random: Random = Random.Default) {
    private val log = LoggerFactory.getLogger(Lobby::class.java)
    private val mutex = Mutex()
    private val rooms = HashMap<String, Pair<BattleMode, Connection>>()
    private val quickQueue = HashMap<BattleMode, Connection>()
    private var nextId = 1L

    suspend fun handle(session: WebSocketSession) {
        val connection = mutex.withLock { Connection(session, nextId++) }
        connection.send(ServerMessage.Welcome())
        try {
            for (frame in session.incoming) {
                if (frame !is Frame.Text) continue
                val message = OnlineProtocol.decodeClient(frame.readText())
                if (message == null) {
                    connection.send(ServerMessage.Error("Unreadable message."))
                    continue
                }
                onMessage(connection, message)
            }
        } finally {
            onDisconnect(connection)
        }
    }

    private suspend fun onMessage(c: Connection, message: ClientMessage) {
        when (message) {
            is ClientMessage.Hello -> {
                if (message.protocolVersion != OnlineProtocol.VERSION) {
                    c.send(ServerMessage.Error("Please update the app to play online."))
                }
                c.name = OnlineProtocol.sanitizeName(message.name)
                // Only titles that exist as cosmetics are passed on.
                c.title = message.title?.takeIf { t -> Cosmetic.entries.any { it.slot == CosmeticSlot.TITLE && it.displayName == t } }
                c.titleFrame = message.titleFrame?.takeIf { f -> com.deepline.fishing.core.TitleFrame.entries.any { it.name == f } }
                c.playerId = message.playerId?.takeIf(com.deepline.fishing.core.PlayerId::isValid)
            }
            is ClientMessage.CreateRoom -> createRoom(c, message.mode)
            is ClientMessage.JoinRoom -> joinRoom(c, OnlineProtocol.normalizeRoomCode(message.code))
            is ClientMessage.QuickMatch -> quickMatch(c, message.mode)
            ClientMessage.CancelWaiting -> {
                mutex.withLock { stopWaiting(c) }
                c.send(ServerMessage.WaitingCancelled)
            }
            is ClientMessage.CastFinished -> c.match?.onCast(c.playerIndex, message)
            is ClientMessage.Live -> c.match?.onLive(c.playerIndex, message)
            ClientMessage.Forfeit -> c.match?.onForfeit(c.playerIndex, "${c.name} forfeited.")
        }
    }

    private suspend fun createRoom(c: Connection, mode: BattleMode) {
        val code = mutex.withLock {
            if (c.isBusy) return@withLock null
            var code: String
            do {
                code = (1..OnlineProtocol.ROOM_CODE_LENGTH)
                    .map { OnlineProtocol.ROOM_CODE_ALPHABET[random.nextInt(OnlineProtocol.ROOM_CODE_ALPHABET.length)] }
                    .joinToString("")
            } while (code in rooms)
            rooms[code] = mode to c
            c.waitingRoom = code
            code
        }
        if (code == null) {
            c.send(ServerMessage.Error("You are already waiting for or playing a match."))
        } else {
            log.info("Room {} created for {} by {}", code, mode, c.name)
            c.send(ServerMessage.RoomCreated(code, mode))
        }
    }

    private suspend fun joinRoom(c: Connection, code: String) {
        var error: String? = null
        val match = mutex.withLock {
            if (c.isBusy) {
                error = "You are already waiting for or playing a match."
                return@withLock null
            }
            val room = rooms[code]
            if (room == null || room.second === c) {
                error = "No room with the code $code."
                return@withLock null
            }
            rooms.remove(code)
            val (mode, host) = room
            host.waitingRoom = null
            Match(mode, listOf(host, c), random.nextLong())
        }
        error?.let { c.send(ServerMessage.Error(it)) }
        match?.let {
            log.info("Room {} started: {} vs {}", code, it.players[0].name, it.players[1].name)
            it.start()
        }
    }

    private suspend fun quickMatch(c: Connection, mode: BattleMode) {
        var busy = false
        val match = mutex.withLock {
            if (c.isBusy) {
                busy = true
                return@withLock null
            }
            val other = quickQueue.remove(mode)
            if (other != null && other !== c) {
                other.waitingQuick = null
                Match(mode, listOf(other, c), random.nextLong())
            } else {
                quickQueue[mode] = c
                c.waitingQuick = mode
                null
            }
        }
        when {
            busy -> c.send(ServerMessage.Error("You are already waiting for or playing a match."))
            match != null -> {
                log.info("Quick match {}: {} vs {}", mode, match.players[0].name, match.players[1].name)
                match.start()
            }
            else -> c.send(ServerMessage.Waiting(mode))
        }
    }

    /** Must be called with [mutex] held. */
    private fun stopWaiting(c: Connection) {
        c.waitingRoom?.let { code -> if (rooms[code]?.second === c) rooms.remove(code) }
        c.waitingQuick?.let { mode -> if (quickQueue[mode] === c) quickQueue.remove(mode) }
        c.waitingRoom = null
        c.waitingQuick = null
    }

    private suspend fun onDisconnect(c: Connection) {
        mutex.withLock { stopWaiting(c) }
        c.match?.onForfeit(c.playerIndex, "${c.name} left the battle.")
    }

    /** For tests and monitoring. */
    suspend fun waitingCount(): Int = mutex.withLock { rooms.size + quickQueue.size }
}
