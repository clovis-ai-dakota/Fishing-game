package com.deepline.fishing

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
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

enum class Screen { HOME, CAST, UPGRADES, COLLECTION, MILESTONES }

/** A short message flashed over the game screen. */
data class Notice(val text: String, val shownAtSeconds: Float)

class GameViewModel(app: Application) : AndroidViewModel(app) {
    private val repository = ProgressRepository(SharedPrefsStore(app))

    var progress by mutableStateOf(
        Progression.ensureEventStarted(repository.load(), System.currentTimeMillis()),
    )
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

    init {
        repository.save(progress)
    }

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

    fun isEventOver(now: Long = System.currentTimeMillis()) = Progression.isEventOver(progress, now)

    fun startCast() {
        if (isEventOver()) return
        var p = progress
        val lantern = useLantern && p.lanterns > 0
        if (lantern) p = Progression.consumeLantern(p) ?: p
        update(p)
        session = CastSession.create(p.specs, lantern)
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
        if (s.phase == CastPhase.SURFACED) pendingResult = s.result
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

    /** Leaves the cast without scoring anything. A lantern used for it is not refunded. */
    fun abandonCast() {
        session = null
        paused = false
        pendingResult = null
        screen = Screen.HOME
    }

    fun collectResult() {
        pendingResult?.let { update(Progression.applyCast(progress, it)) }
        pendingResult = null
        session = null
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

    fun startNewEvent() {
        update(Progression.startNewEvent(progress, System.currentTimeMillis()))
    }

    /** Handles the system back button; returns false when the app should close. */
    fun onBack(): Boolean = when (screen) {
        Screen.HOME -> false
        Screen.CAST -> {
            if (pendingResult != null) collectResult() else if (paused) resume() else pause()
            true
        }
        else -> {
            screen = Screen.HOME
            true
        }
    }
}
