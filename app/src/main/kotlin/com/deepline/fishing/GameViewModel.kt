package com.deepline.fishing

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
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
import kotlin.random.Random

enum class Screen { HOME, CAST, UPGRADES, COLLECTION, MILESTONES, BATTLE_SETUP, BATTLE }

/** A short message flashed over the game screen. */
data class Notice(val text: String, val shownAtSeconds: Float)

/** Who the player is battling: the computer at some difficulty, or a friend on the same device. */
sealed interface Opponent {
    data class Computer(val difficulty: BotDifficulty) : Opponent
    data object Friend : Opponent
}

class GameViewModel(app: Application) : AndroidViewModel(app) {
    private val repository = ProgressRepository(SharedPrefsStore(app))

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
            battle?.record(turn, BattleCast(result, duration))
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
        startBattle(b.mode, opponent)
    }

    /**
     * Plays the next turn. A human turn starts a cast; computer turns are simulated instantly
     * (all of the computer's consecutive casts at once).
     */
    fun playNextTurn() {
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
        battle?.forfeit(playerIndex)
        onBattleChanged()
        screen = Screen.BATTLE
    }

    fun leaveBattle() {
        val b = battle
        if (b != null && !b.isOver) b.forfeit(0)
        onBattleChanged()
        battle = null
        battleOpponent = null
        screen = Screen.HOME
    }

    private fun onBattleChanged() {
        battleVersion++
        val b = battle ?: return
        val outcome = b.outcome() ?: return
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
            else -> screen = Screen.HOME
        }
    }
}
