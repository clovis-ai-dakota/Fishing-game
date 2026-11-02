package com.deepline.fishing

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.deepline.fishing.core.Battle
import com.deepline.fishing.core.BattleCast
import com.deepline.fishing.core.BattleMode
import com.deepline.fishing.core.BattlePlayer
import com.deepline.fishing.core.BattleTurn
import com.deepline.fishing.core.BotDifficulty
import com.deepline.fishing.core.BotPlayer
import com.deepline.fishing.core.CastEvent
import com.deepline.fishing.core.CastPhase
import com.deepline.fishing.core.CastResult
import com.deepline.fishing.core.CastSession
import com.deepline.fishing.core.GameConfig
import com.deepline.fishing.core.Milestone
import com.deepline.fishing.core.PlayerProgress
import com.deepline.fishing.core.Progression
import com.deepline.fishing.core.ProgressRepository
import com.deepline.fishing.core.ReverseReason
import com.deepline.fishing.core.RopeStat
import com.deepline.fishing.core.online.ClientMessage
import com.deepline.fishing.core.online.LiveStatus
import com.deepline.fishing.core.online.OnlineMatch
import com.deepline.fishing.core.online.OnlineProtocol
import com.deepline.fishing.core.online.ServerMessage
import com.deepline.fishing.core.online.toDto
import kotlin.random.Random

enum class Screen { HOME, CAST, UPGRADES, COLLECTION, MILESTONES, BATTLE_SETUP, BATTLE, ONLINE_LOBBY }

/** A short message flashed over the game screen. */
data class Notice(val text: String, val shownAtSeconds: Float)

/** Who the player is battling: the computer at some difficulty, or a friend on the same device. */
sealed interface Opponent {
    data class Computer(val difficulty: BotDifficulty) : Opponent
    data object Friend : Opponent
    data object Online : Opponent
}

enum class OnlineStatus { DISCONNECTED, CONNECTING, CONNECTED, WAITING_ROOM, WAITING_QUICK, IN_MATCH }

class GameViewModel(app: Application) : AndroidViewModel(app) {
    private val store = SharedPrefsStore(app)
    private val repository = ProgressRepository(store)

    var progress by mutableStateOf(repository.load())
        private set
    var screen by mutableStateOf(Screen.HOME)
        private set
    var useLantern by mutableStateOf(false)
        private set
    var session by mutableStateOf<CastSession?>(null)
        private set
    var paused by mutableStateOf(false)
        private set
    var pendingResult by mutableStateOf<CastResult?>(null)
        private set
    var notice by mutableStateOf<Notice?>(null)
        private set

    // --- Battles -------------------------------------------------------------------------

    var battle by mutableStateOf<Battle?>(null)
        private set
    /** The human turn currently being fished, if the current cast belongs to a battle. */
    var battleTurn by mutableStateOf<BattleTurn?>(null)
        private set
    /** Bumped whenever the (non-observable) battle changes, so battle screens recompose. */
    var battleVersion by mutableIntStateOf(0)
        private set
    private var battleOpponent: Opponent? = null
    private var battleRecorded = false

    // --- Online --------------------------------------------------------------------------

    var onlineStatus by mutableStateOf(OnlineStatus.DISCONNECTED)
        private set
    var onlineError by mutableStateOf<String?>(null)
        private set
    var roomCode by mutableStateOf<String?>(null)
        private set
    var onlineMode by mutableStateOf(BattleMode.BEST_OF_FIVE)
        private set
    var onlineMatch by mutableStateOf<OnlineMatch?>(null)
        private set
    var playerName by mutableStateOf(store.getString(KEY_NAME) ?: "Angler")
        private set
    var serverUrl by mutableStateOf(store.getString(KEY_SERVER) ?: BuildConfig.DEFAULT_SERVER_URL)
        private set
    private var connection: OnlineConnection? = null
    private var lastLiveSentAt = -1f

    val isOnlineBattle: Boolean get() = onlineMatch != null && battle === onlineMatch?.battle

    private fun update(p: PlayerProgress) {
        progress = p
        repository.save(p)
    }

    fun navigate(target: Screen) {
        if (target == Screen.CAST) return startCast()
        screen = target
    }

    fun toggleLantern(enabled: Boolean) {
        useLantern = enabled && progress.lanterns > 0
    }

    fun startCast() {
        var p = progress
        val lantern = useLantern && p.lanterns > 0
        if (lantern) p = Progression.consumeLantern(p) ?: p
        update(p)
        beginSession(CastSession.create(p.specs, lantern), turn = null)
    }

    private fun beginSession(s: CastSession, turn: BattleTurn?) {
        session = s
        battleTurn = turn
        pendingResult = null
        notice = null
        paused = false
        screen = Screen.CAST
    }

    fun onFrame(dt: Float, steerTargetX: Float?) {
        val s = session ?: return
        if (paused || pendingResult != null) return
        s.update(dt, steerTargetX)
        for (event in s.drainEvents()) {
            val text = when (event) {
                is CastEvent.Touched ->
                    if (event.touchesUsed < GameConfig.DESCENT_TOUCH_LIMIT) {
                        "Bump! ${GameConfig.DESCENT_TOUCH_LIMIT - event.touchesUsed} left"
                    } else {
                        null
                    }
                is CastEvent.Reversed -> when (event.reason) {
                    ReverseReason.TOUCH_LIMIT -> "Line pulled up! Aim for heads"
                    ReverseReason.ROPE_END -> "End of the rope! Reel in"
                }
                is CastEvent.Caught -> buildString {
                    if (event.record.sparkling) append("Sparkling ")
                    append(event.record.species.name)
                    append("  +${event.record.points}")
                }
                is CastEvent.TooHeavy -> "Too heavy: ${event.species.name}"
                CastEvent.Surfaced -> null
            }
            if (text != null) notice = Notice(text, s.elapsedSeconds)
        }
        val limit = battleTurn?.timeLimitSec
        if (limit != null && s.phase != CastPhase.SURFACED && s.elapsedSeconds >= limit) {
            s.endNow()
            notice = Notice("Time's up!", s.elapsedSeconds)
        }
        if (s.phase == CastPhase.SURFACED) pendingResult = s.result
        sendLiveStatus(s)
    }

    private fun sendLiveStatus(s: CastSession) {
        val turn = battleTurn ?: return
        if (!isOnlineBattle) return
        if (lastLiveSentAt >= 0f && s.elapsedSeconds - lastLiveSentAt < LIVE_INTERVAL_SEC) return
        lastLiveSentAt = s.elapsedSeconds
        connection?.send(
            ClientMessage.Live(
                LiveStatus(
                    castNumber = turn.castNumber,
                    depth = s.hookDepth,
                    points = s.pointsSoFar,
                    fish = s.catches.size,
                    ascending = s.phase == CastPhase.ASCENDING,
                ),
            ),
        )
    }

    /** Seconds left on the battle clock during a timed battle cast, otherwise null. */
    fun battleTimeLeft(): Float? {
        val limit = battleTurn?.timeLimitSec ?: return null
        val elapsed = session?.elapsedSeconds ?: 0f
        return (limit - elapsed).coerceAtLeast(0f)
    }

    fun pause() {
        if (session != null && pendingResult == null) paused = true
    }

    fun resume() {
        paused = false
    }

    fun onAppPaused() {
        if (screen == Screen.CAST) pause()
    }

    /** Leaves the cast without scoring anything. In a battle, this forfeits the match. */
    fun abandonCast() {
        val turn = battleTurn
        session = null
        paused = false
        pendingResult = null
        battleTurn = null
        if (turn != null) {
            onlineMatch?.let { if (isOnlineBattle) it.markSent(turn) }
            forfeitBattle(turn.playerIndex)
        } else {
            screen = Screen.HOME
        }
    }

    fun collectResult() {
        val result = pendingResult
        val turn = battleTurn
        val s = session
        pendingResult = null
        session = null
        battleTurn = null
        if (turn != null && result != null && s != null) {
            val duration = turn.timeLimitSec?.let { minOf(it, s.elapsedSeconds) } ?: s.elapsedSeconds
            val cast = BattleCast(result, duration)
            val online = onlineMatch
            if (isOnlineBattle && online != null) {
                // Online casts are only recorded once the server confirms them.
                online.markSent(turn)
                connection?.send(ClientMessage.CastFinished(turn, cast.toDto()))
            } else {
                battle?.record(turn, cast)
            }
            onBattleChanged()
            screen = Screen.BATTLE
            return
        }
        // Battle casts never reach here: only regular casts count for points and the fish log.
        result?.let { update(Progression.applyCast(progress, it)) }
        useLantern = useLantern && progress.lanterns > 0
        screen = Screen.HOME
    }

    fun buyUpgrade(stat: RopeStat) {
        Progression.upgrade(progress, stat)?.let(::update)
    }

    fun buyLantern() {
        Progression.buyLantern(progress)?.let(::update)
    }

    fun claimMilestone(milestone: Milestone) {
        Progression.claimMilestone(progress, milestone)?.let(::update)
    }

    // --- Battle flow ---------------------------------------------------------------------

    fun startBattle(mode: BattleMode, opponent: Opponent) {
        val players = when (opponent) {
            is Opponent.Computer -> listOf(
                BattlePlayer("You"),
                BattlePlayer("Rival (${opponent.difficulty.displayName})", opponent.difficulty),
            )
            Opponent.Friend -> listOf(BattlePlayer("Player 1"), BattlePlayer("Player 2"))
            Opponent.Online -> return openOnlineLobby(mode)
        }
        battle = Battle(mode, players, Random.nextLong())
        battleOpponent = opponent
        battleRecorded = false
        battleTurn = null
        onBattleChanged()
        screen = Screen.BATTLE
    }

    fun rematch() {
        val b = battle ?: return
        val opponent = battleOpponent ?: return
        if (opponent == Opponent.Online) {
            clearBattle()
            openOnlineLobby(b.mode)
        } else {
            startBattle(b.mode, opponent)
        }
    }

    /** The next cast to play from this device, or null when waiting or finished. */
    fun battleNextTurn(): BattleTurn? {
        val online = onlineMatch
        return if (isOnlineBattle && online != null) online.myNextTurn() else battle?.nextTurn()
    }

    /**
     * Plays the next turn. A human turn starts a cast; computer turns are simulated instantly
     * (all of the computer's consecutive casts at once).
     */
    fun playNextTurn() {
        val online = onlineMatch
        if (isOnlineBattle && online != null) {
            val turn = online.myNextTurn() ?: return
            lastLiveSentAt = -1f
            beginSession(CastSession.seeded(OnlineProtocol.tournamentSpecs, turn.seed), turn)
            return
        }
        val b = battle ?: return
        var turn = b.nextTurn() ?: return
        val firstBot = b.players[turn.playerIndex].bot
        if (firstBot == null) {
            // Both players use the same rope and the same water; no lanterns in battles.
            beginSession(CastSession.seeded(progress.specs, turn.seed), turn)
            return
        }
        while (true) {
            val bot = b.players[turn.playerIndex].bot ?: break
            b.record(turn, BotPlayer.playCast(progress.specs, turn.seed, bot, turn.timeLimitSec))
            turn = b.nextTurn() ?: break
        }
        onBattleChanged()
    }

    fun forfeitBattle(playerIndex: Int = 0) {
        if (isOnlineBattle) {
            // The server answers with MatchOver.
            connection?.send(ClientMessage.Forfeit)
        } else {
            battle?.forfeit(playerIndex)
            onBattleChanged()
        }
        screen = Screen.BATTLE
    }

    fun leaveBattle() {
        val wasOnline = isOnlineBattle
        val b = battle
        if (wasOnline) {
            if (onlineMatch?.outcome == null) connection?.send(ClientMessage.Forfeit)
        } else if (b != null && !b.isOver) {
            b.forfeit(0)
            onBattleChanged()
        }
        clearBattle()
        screen = if (wasOnline) Screen.ONLINE_LOBBY else Screen.HOME
    }

    private fun clearBattle() {
        battle = null
        battleOpponent = null
        onlineMatch = null
        if (onlineStatus == OnlineStatus.IN_MATCH) onlineStatus = OnlineStatus.CONNECTED
    }

    private fun onBattleChanged() {
        battleVersion++
        val b = battle ?: return
        val outcome = (if (isOnlineBattle) onlineMatch?.outcome else b.outcome()) ?: return
        if (!battleRecorded && battleOpponent is Opponent.Computer) {
            battleRecorded = true
            update(Progression.recordBattle(progress, outcome.winner?.let { it == 0 }))
        }
    }

    /** Handles the system back button. */
    fun onBack() {
        when (screen) {
            Screen.HOME -> Unit
            Screen.CAST -> if (pendingResult != null) collectResult() else if (paused) resume() else pause()
            Screen.BATTLE -> if (battle?.isOver != false) leaveBattle() else Unit
            Screen.BATTLE_SETUP -> screen = Screen.HOME
            Screen.ONLINE_LOBBY -> closeOnlineLobby()
            else -> screen = Screen.HOME
        }
    }

    // --- Online lobby ---------------------------------------------------------------------

    fun openOnlineLobby(mode: BattleMode) {
        onlineMode = mode
        onlineError = null
        screen = Screen.ONLINE_LOBBY
    }

    fun closeOnlineLobby() {
        disconnect()
        screen = Screen.BATTLE_SETUP
    }

    fun updatePlayerName(name: String) {
        playerName = name.take(OnlineProtocol.MAX_NAME_LENGTH)
        store.putString(KEY_NAME, playerName)
    }

    fun updateServerUrl(url: String) {
        serverUrl = url.trim()
        store.putString(KEY_SERVER, serverUrl)
        // A different server needs a fresh connection.
        if (onlineStatus == OnlineStatus.CONNECTED) disconnect()
    }

    fun createRoom() = sendToServer(ClientMessage.CreateRoom(onlineMode))

    fun joinRoom(code: String) {
        val normalized = OnlineProtocol.normalizeRoomCode(code)
        if (normalized.length != OnlineProtocol.ROOM_CODE_LENGTH) {
            onlineError = "Room codes have ${OnlineProtocol.ROOM_CODE_LENGTH} characters."
            return
        }
        sendToServer(ClientMessage.JoinRoom(normalized))
    }

    fun quickMatch() = sendToServer(ClientMessage.QuickMatch(onlineMode))

    fun cancelWaiting() {
        connection?.send(ClientMessage.CancelWaiting)
    }

    private fun sendToServer(message: ClientMessage) {
        onlineError = null
        if (connection == null) {
            onlineStatus = OnlineStatus.CONNECTING
            connection = OnlineConnection(viewModelScope, ::onServerMessage, ::onConnectionClosed).also {
                it.connect(serverUrl, OnlineProtocol.sanitizeName(playerName))
            }
        }
        connection?.send(message)
    }

    private fun onServerMessage(message: ServerMessage) {
        when (message) {
            is ServerMessage.Welcome -> if (onlineStatus == OnlineStatus.CONNECTING) onlineStatus = OnlineStatus.CONNECTED
            is ServerMessage.RoomCreated -> {
                roomCode = message.code
                onlineStatus = OnlineStatus.WAITING_ROOM
            }
            is ServerMessage.Waiting -> onlineStatus = OnlineStatus.WAITING_QUICK
            ServerMessage.WaitingCancelled -> {
                roomCode = null
                onlineStatus = OnlineStatus.CONNECTED
            }
            is ServerMessage.MatchStarted -> {
                val match = OnlineMatch(message)
                roomCode = null
                onlineMatch = match
                battle = match.battle
                battleOpponent = Opponent.Online
                battleRecorded = false
                battleTurn = null
                onlineStatus = OnlineStatus.IN_MATCH
                onBattleChanged()
                screen = Screen.BATTLE
            }
            is ServerMessage.Error -> {
                onlineError = message.message
                if (onlineStatus == OnlineStatus.CONNECTING) onlineStatus = OnlineStatus.CONNECTED
            }
            is ServerMessage.CastRecorded, is ServerMessage.OpponentLive, is ServerMessage.MatchOver -> {
                onlineMatch?.apply(message)
                onBattleChanged()
            }
        }
    }

    private fun onConnectionClosed(reason: String) {
        connection = null
        roomCode = null
        onlineStatus = OnlineStatus.DISCONNECTED
        onlineError = reason
        val match = onlineMatch
        if (match != null && match.outcome == null) {
            match.connectionLost(reason)
            if (screen == Screen.CAST && battleTurn != null) {
                session = null
                paused = false
                pendingResult = null
                battleTurn = null
                screen = Screen.BATTLE
            }
            onBattleChanged()
        }
    }

    private fun disconnect() {
        connection?.close()
        connection = null
        roomCode = null
        if (onlineMatch == null) onlineStatus = OnlineStatus.DISCONNECTED
    }

    override fun onCleared() {
        connection?.close()
        super.onCleared()
    }

    private companion object {
        const val KEY_NAME = "online.name"
        const val KEY_SERVER = "online.server"
        const val LIVE_INTERVAL_SEC = 0.5f
    }
}
