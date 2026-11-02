package com.deepline.fishing.core.online

import com.deepline.fishing.core.Battle
import com.deepline.fishing.core.BattleOutcome
import com.deepline.fishing.core.BattlePlayer
import com.deepline.fishing.core.BattleTurn

/**
 * Client-side mirror of an online battle. It only ever records casts confirmed by the server,
 * so both players' mirrors stay identical to the server's battle.
 */
class OnlineMatch(start: ServerMessage.MatchStarted) {
    val mode = start.mode
    val myIndex = start.yourIndex
    val opponentIndex = 1 - start.yourIndex
    val battle = Battle(start.mode, start.playerNames.map { BattlePlayer(it) }, start.seed)

    var opponentLive: LiveStatus? = null
        private set
    var outcome: BattleOutcome? = null
        private set
    var overReason: String? = null
        private set

    /** My cast was sent and is waiting for the server's confirmation. */
    var awaitingConfirmation: BattleTurn? = null
        private set

    /** My next cast, or null if the match is over, I'm waiting for the opponent, or a cast is in flight. */
    fun myNextTurn(): BattleTurn? =
        if (outcome != null || awaitingConfirmation != null) null else battle.nextTurnFor(myIndex)

    val isWaitingForOpponent: Boolean
        get() = outcome == null && awaitingConfirmation == null && battle.nextTurnFor(myIndex) == null

    fun markSent(turn: BattleTurn) {
        awaitingConfirmation = turn
    }

    /** Applies a server message. Returns true when it changed the match. */
    fun apply(message: ServerMessage): Boolean = when (message) {
        is ServerMessage.CastRecorded -> {
            val cast = CastValidation.toBattleCast(message.cast, message.turn, OnlineProtocol.tournamentSpecs)
            if (cast != null && message.turn == battle.nextTurnFor(message.turn.playerIndex)) {
                battle.record(message.turn, cast)
                if (message.turn.playerIndex == myIndex) awaitingConfirmation = null else opponentLive = null
            }
            true
        }
        is ServerMessage.OpponentLive -> {
            opponentLive = message.status
            true
        }
        is ServerMessage.MatchOver -> {
            if (message.byForfeit && message.winner != null) battle.forfeit(1 - message.winner)
            outcome = BattleOutcome(message.winner, message.byForfeit)
            overReason = message.reason
            awaitingConfirmation = null
            true
        }
        else -> false
    }

    /** Marks the match as lost because the connection dropped. */
    fun connectionLost(reason: String) {
        if (outcome != null) return
        battle.forfeit(myIndex)
        outcome = BattleOutcome(opponentIndex, byForfeit = true)
        overReason = reason
        awaitingConfirmation = null
    }
}
