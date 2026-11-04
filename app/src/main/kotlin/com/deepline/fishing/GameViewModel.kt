package com.deepline.fishing

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.deepline.fishing.core.Atmosphere
import com.deepline.fishing.core.Battle
import com.deepline.fishing.core.BattleCast
import com.deepline.fishing.core.BattleMode
import com.deepline.fishing.core.BattlePlayer
import com.deepline.fishing.core.BattleTurn
import com.deepline.fishing.core.BoatDesign
import com.deepline.fishing.core.BoatPart
import com.deepline.fishing.core.BotDifficulty
import com.deepline.fishing.core.BotPlayer
import com.deepline.fishing.core.CastEvent
import com.deepline.fishing.core.CastPhase
import com.deepline.fishing.core.CastResult
import com.deepline.fishing.core.CastSession
import com.deepline.fishing.core.Cosmetic
import com.deepline.fishing.core.CosmeticSlot
import com.deepline.fishing.core.GameConfig
import com.deepline.fishing.core.Milestone
import com.deepline.fishing.core.PlayerProgress
import com.deepline.fishing.core.Progression
import com.deepline.fishing.core.ProgressRepository
import com.deepline.fishing.core.ReverseReason
import com.deepline.fishing.core.RopeSpecs
import com.deepline.fishing.core.RopeStat
import com.deepline.fishing.core.SecretRules
import com.deepline.fishing.core.Secrets
import com.deepline.fishing.core.online.ClientMessage
import com.deepline.fishing.core.online.LiveStatus
import com.deepline.fishing.core.online.OnlineMatch
import com.deepline.fishing.core.online.OnlineProtocol
import com.deepline.fishing.core.online.ServerMessage
import com.deepline.fishing.core.online.toDto
import com.deepline.fishing.ui.Skins
import com.deepline.fishing.ui.tour.Tour
import com.deepline.fishing.ui.tour.TourSession
import kotlin.random.Random

/**
 * Where the player is. [HOME] (the Fish tab), [DEX], [UPGRADES] and [BATTLE_SETUP] are the four
 * bottom-bar tabs; the others are full screens pushed on top of them (no tab bar).
 */
enum class Screen { HOME, DEX, UPGRADES, BATTLE_SETUP, CAST, FISH_DETAIL, MILESTONES, BATTLE, ONLINE_LOBBY, BOAT_EDITOR, SHOP }

/** The four top-level destinations of the bottom navigation bar, in order. */
enum class MainTab(val label: String, val screen: Screen) {
    FISH("Fish", Screen.HOME),
    DEX("Dex", Screen.DEX),
    UPGRADES("Upgrades", Screen.UPGRADES),
    BATTLE("Battle", Screen.BATTLE_SETUP);

    companion object {
        fun of(screen: Screen): MainTab? = entries.firstOrNull { it.screen == screen }
    }
}

/** The sections of the Dex tab. Secrets only appears once one is found; Skins is always there. */
enum class DexSection(val label: String) { FISH("Species"), SECRETS("Secrets"), SKINS("Skins") }

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
    var dexSection by mutableStateOf(DexSection.FISH)
        private set
    /** The species shown by the Dex detail page (an index in `FishCatalog.all`). */
    var dexFishIndex by mutableIntStateOf(0)
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

    // --- Tour ----------------------------------------------------------------------------

    /** The guided tour, while it is showing. */
    var tour by mutableStateOf<TourSession?>(null)
        private set
    /** True right after the tour closes: ask whether to show it again next launch. */
    var askTourAgain by mutableStateOf(false)
        private set

    init {
        // A first launch (no answer stored yet) shows the full tour; afterwards it only opens
        // again if the player asked for it, or to show steps added since the version they saw.
        val showAgain = store.getString(KEY_TOUR_AGAIN)?.toBooleanStrictOrNull() ?: true
        val seen = store.getString(KEY_TOUR_SEEN)?.toIntOrNull() ?: 0
        Tour.planAtLaunch(showAgain, seen)?.let { beginTour(TourSession(it.steps, whatsNew = it.whatsNew)) }
    }

    /** Starts the full tour from the menu. */
    fun startTour() = beginTour(TourSession(Tour.steps))

    private fun beginTour(session: TourSession) {
        if (this.session != null) return
        askTourAgain = false
        tour = session
        showTourStep(session)
    }

    fun tourNext() {
        val t = tour ?: return
        if (t.isLast) return endTour()
        val next = t.copy(index = t.index + 1)
        tour = next
        showTourStep(next)
    }

    fun tourBack() {
        val t = tour ?: return
        if (t.isFirst) return
        val previous = t.copy(index = t.index - 1)
        tour = previous
        showTourStep(previous)
    }

    private fun showTourStep(t: TourSession) {
        screen = t.current.screen
        if (screen == Screen.DEX) dexSection = t.current.dexSection ?: DexSection.FISH
    }

    /** Skipping and finishing both close the tour and ask about next time. */
    fun skipTour() = endTour()

    private fun endTour() {
        tour = null
        screen = Screen.HOME
        store.putString(KEY_TOUR_SEEN, Tour.VERSION.toString())
        askTourAgain = true
    }

    fun answerTourAgain(showNextTime: Boolean) {
        store.putString(KEY_TOUR_AGAIN, showNextTime.toString())
        askTourAgain = false
    }

    private fun update(p: PlayerProgress) {
        progress = p
        repository.save(p)
    }

    fun navigate(target: Screen) {
        if (target == Screen.CAST) return startCast()
        screen = target
    }

    /** A tap on the bottom navigation bar. */
    fun selectTab(tab: MainTab) {
        screen = tab.screen
    }

    /** Opens the Dex on [section] (falls back to Species if that section isn't unlocked yet). */
    fun openDex(section: DexSection = dexSection) {
        dexSection = if (section in availableDexSections) section else DexSection.FISH
        screen = Screen.DEX
    }

    /** Dex sections the player can open: Secrets once one is found; Skins (boat customising) always. */
    val availableDexSections: List<DexSection>
        get() = DexSection.entries.filter {
            when (it) {
                DexSection.FISH -> true
                DexSection.SECRETS -> progress.discoveredSecrets.isNotEmpty()
                DexSection.SKINS -> true
            }
        }

    // --- Skins: boat customisation, shop, atmosphere ------------------------------------------

    /** Everything the player wears, as the renderers need it. */
    val skins: Skins
        get() = Skins.of(
            progress.equippedSkins,
            customBoat = progress.boatDesign.takeIf { progress.customBoatEquipped },
            atmosphere = progress.atmosphere,
        )

    fun openBoatEditor() {
        screen = Screen.BOAT_EDITOR
    }

    fun openShop() {
        screen = Screen.SHOP
    }

    /** Buys a boat option with points; false if it can't be bought or isn't affordable. */
    fun unlockBoatPart(part: BoatPart): Boolean {
        val next = Progression.unlockBoatPart(progress, part) ?: return false
        update(next)
        return true
    }

    /** Saves [design] and wears it; false if it uses an option that's still locked. */
    fun saveBoatDesign(design: BoatDesign): Boolean {
        val next = Progression.setBoatDesign(progress, design) ?: return false
        update(Progression.equipCustomBoat(next, true))
        return true
    }

    fun equipCustomBoat(on: Boolean) {
        update(Progression.equipCustomBoat(progress, on))
    }

    fun setAtmosphere(atmosphere: Atmosphere) {
        Progression.setAtmosphere(progress, atmosphere)?.let(::update)
    }

    /** Opens the Dex page of the species at [index] in `FishCatalog.all`. */
    fun openFishDetail(index: Int) {
        dexFishIndex = index
        screen = Screen.FISH_DETAIL
    }

    /** Remembers which page the detail pager is on, so back returns to the right place. */
    fun showFishDetail(index: Int) {
        dexFishIndex = index
    }

    fun toggleLantern(enabled: Boolean) {
        useLantern = enabled && progress.lanterns > 0
    }

    fun startCast() {
        var p = progress
        val lantern = useLantern && p.lanterns > 0
        if (lantern) p = Progression.consumeLantern(p) ?: p
        update(p)
        beginSession(castFactory(p.specs, lantern), turn = null)
    }

    private fun beginSession(s: CastSession, turn: BattleTurn?) {
        explainedThisCast.clear()
        secretQueue = emptyList()
        session = s
        battleTurn = turn
        pendingResult = null
        notice = null
        paused = false
        screen = Screen.CAST
    }

    /** Builds the water for a regular cast. Tests swap it to fish hand-placed water. */
    internal var castFactory: (RopeSpecs, Boolean) -> CastSession = { specs, lantern -> CastSession.create(specs, lantern) }

    /** Fishing secrets waiting to be explained; the first one is showing (the cast is paused). */
    private var secretQueue by mutableStateOf<List<String>>(emptyList())

    /** The fishing secret being explained right now. */
    val secretPopup: String? get() = secretQueue.firstOrNull()

    /** Secrets already explained during the current cast (so they don't pop up twice). */
    private val explainedThisCast = HashSet<String>()

    fun onFrame(dt: Float, steerTargetX: Float?) {
        val s = session ?: return
        if (paused || pendingResult != null || secretPopup != null) return
        s.update(dt, steerTargetX)
        val events = s.drainEvents()
        // A last-bump hook comes with the line turning back; its notice is the one worth showing.
        val hooked = events.any { it is CastEvent.LastBumpHooked }
        for (event in events) {
            val text = when (event) {
                is CastEvent.Touched ->
                    if (event.touchesUsed < GameConfig.DESCENT_TOUCH_LIMIT) {
                        "Bump! ${GameConfig.DESCENT_TOUCH_LIMIT - event.touchesUsed} left"
                    } else {
                        null
                    }
                is CastEvent.Reversed -> when (event.reason) {
                    ReverseReason.TOUCH_LIMIT -> if (hooked) null else "Line pulled up! Aim for heads"
                    ReverseReason.ROPE_END -> "End of the rope! Reel in"
                }
                is CastEvent.Caught -> buildString {
                    if (event.record.sparkling) append("Sparkling ")
                    append(event.record.species.name)
                    append("  +${event.record.points}")
                }
                is CastEvent.TooHeavy -> "Too heavy: ${event.species.name}"
                is CastEvent.LastBumpHooked -> {
                    onSecretTriggered(Secrets.LAST_BUMP)
                    "Last bump hooked: ${event.name}  +${event.points}"
                }
                CastEvent.Surfaced -> null
                CastEvent.FastReel -> null
            }
            if (text != null) notice = Notice(text, s.elapsedSeconds)
        }
        val limit = battleTurn?.timeLimitSec
        if (limit != null && s.phase != CastPhase.SURFACED && s.elapsedSeconds >= limit) {
            s.endNow()
            notice = Notice("Time's up!", s.elapsedSeconds)
        }
        if (s.phase == CastPhase.SURFACED) {
            val result = s.result
            pendingResult = result
            // Secrets pulled off in this cast are revealed with the results.
            result?.let { r -> Secrets.revealedBy(r).forEach(::onSecretTriggered) }
        }
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

    /** A hidden mechanic just happened: remember it, and explain it unless the player opted out. */
    private fun onSecretTriggered(id: String) {
        if (!explainedThisCast.add(id)) return
        val explain = Progression.shouldExplainSecret(progress, id)
        // The first time also grants the secret's one-time reward (points, Lanterns, skins).
        update(Progression.discoverSecret(progress, id))
        if (explain) secretQueue = secretQueue + id
    }

    /** Closes the secret explanation; [showAgain] is the answer to "show this again next time?". */
    fun answerSecretPopup(showAgain: Boolean) {
        val id = secretPopup ?: return
        update(Progression.setSecretPopupMuted(progress, id, muted = !showAgain))
        secretQueue = secretQueue.drop(1)
    }

    /** Hauls a full line straight up (see [CastSession.startFastReel]). */
    fun fastReel() {
        val s = session ?: return
        if (paused || pendingResult != null) return
        s.startFastReel()
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
        // Master angler: the fish log has just been completed (it can only happen once).
        (Secrets.revealedByProgress(progress) - progress.discoveredSecrets).forEach(::onSecretTriggered)
        useLantern = useLantern && progress.lanterns > 0
        screen = Screen.HOME
    }

    fun equipSkin(slot: CosmeticSlot, cosmetic: Cosmetic?) {
        update(Progression.equipSkin(progress, slot, cosmetic))
    }

    /** Steps a rope stat down one level and refunds what it cost. */
    fun downgrade(stat: RopeStat) {
        Progression.downgrade(progress, stat)?.let(::update)
    }

    /** The title the player wears (shown at home and in battles), or null. */
    val title: String? get() = progress.equippedSkins[CosmeticSlot.TITLE]?.displayName

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
            beginSession(CastSession.seeded(OnlineProtocol.tournamentSpecs, turn.seed, timeLeftAtStart = turn.timeLimitSec), turn)
            return
        }
        val b = battle ?: return
        var turn = b.nextTurn() ?: return
        val firstBot = b.players[turn.playerIndex].bot
        if (firstBot == null) {
            // Both players use the same rope and the same water; no lanterns in battles.
            beginSession(CastSession.seeded(progress.specs, turn.seed, timeLeftAtStart = turn.timeLimitSec), turn)
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
        screen = if (wasOnline) Screen.ONLINE_LOBBY else Screen.BATTLE_SETUP
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
            // Shutout: a battle-result secret, paid into the player's points.
            val rival = (battleOpponent as Opponent.Computer).difficulty
            for (id in Secrets.revealedByBattle(b, outcome, playerIndex = 0, rival = rival)) {
                if (id in progress.discoveredSecrets) {
                    update(progress.copy(pointsBalance = progress.pointsBalance + SecretRules.SHUTOUT_BONUS))
                }
                onSecretTriggered(id)
            }
        }
    }

    /**
     * Handles the system back button: a pushed screen goes back to where it came from, any other
     * tab goes back to the Fish tab, and on the Fish tab back leaves the app (not handled here).
     */
    fun onBack() {
        when (screen) {
            Screen.HOME -> Unit
            Screen.CAST -> if (pendingResult != null) collectResult() else if (paused) resume() else pause()
            Screen.BATTLE -> if (battle?.isOver != false) leaveBattle() else Unit
            Screen.ONLINE_LOBBY -> closeOnlineLobby()
            Screen.FISH_DETAIL -> screen = Screen.DEX
            Screen.BOAT_EDITOR, Screen.SHOP -> openDex(DexSection.SKINS)
            Screen.DEX, Screen.UPGRADES, Screen.BATTLE_SETUP, Screen.MILESTONES -> screen = Screen.HOME
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
                it.connect(serverUrl, OnlineProtocol.sanitizeName(playerName), title)
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
        const val KEY_TOUR_AGAIN = "tour.showAgain"
        const val KEY_TOUR_SEEN = "tour.seenVersion"
        const val LIVE_INTERVAL_SEC = 0.5f
    }
}
