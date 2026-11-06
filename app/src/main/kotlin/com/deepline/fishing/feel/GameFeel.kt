package com.deepline.fishing.feel

import android.content.Context
import android.provider.Settings
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.deepline.fishing.analytics.Analytics
import com.deepline.fishing.audio.AudioEngine
import com.deepline.fishing.audio.AudioEngines
import com.deepline.fishing.audio.MusicTrack
import com.deepline.fishing.audio.Sfx
import com.deepline.fishing.core.CastEvent
import com.deepline.fishing.core.CastPhase
import com.deepline.fishing.core.CastResult
import com.deepline.fishing.core.CastSession
import com.deepline.fishing.core.GameConfig
import com.deepline.fishing.core.KeyValueStore
import kotlin.math.abs
import kotlin.random.Random

/**
 * Game feel: sound, music, haptics and the render-only juice of casts, behind one object the
 * view model delegates to. It turns game moments into feedback with [FeelMap] and applies the
 * player's [settings] (volumes, haptics, reduce motion).
 *
 * Music: the menu track outside casts; during a cast a bright layer near the surface crossfades
 * into a darker one as the hook goes down (around [GameConfig.DARK_DEPTH]).
 */
class GameFeel(
    private val audio: AudioEngine,
    private val haptics: Haptics,
    private val store: KeyValueStore,
    systemReduceMotion: Boolean = false,
    private val random: Random = Random.Default,
) {
    var settings by mutableStateOf(FeelSettings.load(store, systemReduceMotion))
        private set

    /** Whether the phone can vibrate (the haptics switch is disabled otherwise). */
    val hapticsAvailable: Boolean get() = haptics.available

    /** The current cast's shake, slow motion, flash and particles. */
    val juice = CastJuice()

    private var inCast = false
    private var touchLimit = GameConfig.DESCENT_TOUCH_LIMIT
    private var streak = 0
    private var lastCatchSec = Float.NEGATIVE_INFINITY
    private var nextBubbleSec = 0f
    private var reeledM = 0f
    private var lastDepth = 0f
    private var surfaceSplashed = false
    private var lanternLit = false
    private var lastMusicDepth = Float.NaN
    private var lastOutcomeFor: Any? = null

    init {
        applyVolumes()
        audio.setMusicMix(MENU_MIX)
    }

    // ---- Settings ----------------------------------------------------------------------------

    /** Changes the settings now (e.g. while a slider moves); [commit] saves them. */
    fun update(next: FeelSettings) {
        settings = next
        applyVolumes()
    }

    /** Saves the settings and logs which one changed ([setting]: an analytics name). */
    fun commit(setting: String) {
        settings.save(store)
        val s = settings
        val value: Any = when (setting) {
            SETTING_MASTER -> percent(s.master)
            SETTING_MUSIC -> percent(s.music)
            SETTING_SFX -> percent(s.sfx)
            SETTING_HAPTICS -> s.haptics
            SETTING_REDUCE_MOTION -> s.reduceMotion
            else -> ""
        }
        Analytics.event("settings_change", mapOf("setting" to setting, "value" to value))
    }

    /** A sample at the current effects volume, after the slider is let go. */
    fun previewSfx() = audio.play(Sfx.CATCH)

    private fun percent(v: Float) = (v * 100f).toInt()

    private fun applyVolumes() {
        val s = settings
        audio.setVolumes(s.master, s.music, s.sfx)
    }

    // ---- Menus ---------------------------------------------------------------------------------

    fun cue(cue: Cue) = perform(FeelMap.forCue(cue))

    /** A battle just ended: a jingle for the player's result, once per battle ([battle] identifies it). */
    fun onBattleOutcome(battle: Any, winner: Int?, me: Int?) {
        if (lastOutcomeFor === battle) return
        lastOutcomeFor = battle
        cue(
            when {
                winner == null -> Cue.BATTLE_DRAW
                // Pass and play: someone at this phone won.
                me == null || winner == me -> Cue.BATTLE_WIN
                else -> Cue.BATTLE_LOSE
            },
        )
    }

    private fun perform(f: Feedback) {
        for (s in f.sounds) audio.play(s.sfx, s.gain, s.pitch)
        if (settings.haptics) f.haptic?.let(haptics::perform)
    }

    // ---- Casts ---------------------------------------------------------------------------------

    fun onCastStart(session: CastSession) {
        inCast = true
        juice.reset()
        touchLimit = session.touchLimit
        streak = 0
        lastCatchSec = Float.NEGATIVE_INFINITY
        nextBubbleSec = FIRST_BUBBLE_SEC
        reeledM = 0f
        lastDepth = session.hookDepth
        surfaceSplashed = false
        lanternLit = false
        audio.play(Sfx.CAST_SPLASH)
        juice.addBurst(BurstKind.ENTRY_SPLASH, session.hookX, 0f, session.elapsedSeconds)
        updateMusic(session.hookDepth, force = true)
    }

    /**
     * How much cast time a frame of [dt] wall-clock seconds is: less during a slow-motion moment,
     * which only happens in [solo] casts (battles and online casts always run in real time).
     */
    fun simDt(dt: Float, solo: Boolean): Float = juice.advance(dt, allowSlowMo = solo && !settings.reduceMotion)

    /** How fast the cast runs right now (for the renderers' animation clock). */
    val timeScale: Float get() = juice.timeScale

    /** The cast's events of this frame, then the ambience (bubbles, reel, music by depth). */
    fun onCastFrame(session: CastSession, events: List<CastEvent>) {
        val now = session.elapsedSeconds
        for (e in events) {
            if (e == CastEvent.Surfaced) surfaceSplash(session)
            val isCatch = e is CastEvent.Caught || (e is CastEvent.LastBumpHooked && e.fish != null)
            if (isCatch) {
                streak = if (now - lastCatchSec <= STREAK_WINDOW_SEC) streak + 1 else 0
                lastCatchSec = now
            }
            val f = FeelMap.forCastEvent(e, touchLimit, streak)
            perform(f)
            juice.apply(f, session.hookX, session.hookDepth, now, settings.reduceMotion)
        }
        ambience(session)
        updateMusic(session.hookDepth)
    }

    fun onCastResult(result: CastResult) = perform(FeelMap.forResult(result))

    /** The cast screen closed: back to the menu music. */
    fun onCastEnd() {
        if (!inCast) return
        inCast = false
        juice.reset()
        lastMusicDepth = Float.NaN
        audio.setMusicMix(MENU_MIX)
    }

    /** What the renderers add to this frame of [session]. */
    fun fx(session: CastSession): CastFx = juice.frame(session.elapsedSeconds, settings.reduceMotion)

    private fun ambience(s: CastSession) {
        val now = s.elapsedSeconds
        val depth = s.hookDepth
        if (s.phase != CastPhase.SURFACED && now >= nextBubbleSec) {
            // Bubbles drifting past: quieter and lower in the deep, sparser on the way up.
            val deep = (depth / 600f).coerceIn(0f, 1f)
            audio.play(Sfx.BUBBLE, gain = (0.6f + 0.4f * random.nextFloat()) * (1f - 0.45f * deep), pitch = 1f - 0.18f * deep)
            val gap = if (s.phase == CastPhase.DESCENDING) 0.5f + 0.9f * random.nextFloat() else 1f + 1.3f * random.nextFloat()
            nextBubbleSec = now + gap
        }
        if (s.phase == CastPhase.ASCENDING && !s.isFastReeling) {
            // The reel's ratchet: a click every few meters of line, lower with a heavy line.
            reeledM += (lastDepth - depth).coerceAtLeast(0f)
            if (reeledM >= REEL_TICK_METERS) {
                reeledM %= REEL_TICK_METERS
                val load = (s.carriedWeightKg / s.specs.maxWeightKg).coerceIn(0f, 1f)
                audio.play(Sfx.REEL_TICK, gain = 0.7f + 0.3f * load, pitch = 1f - 0.15f * load)
            }
        }
        lastDepth = depth
        // The line breaking the surface: a splash just before the results.
        if (s.phase == CastPhase.ASCENDING && depth < SURFACE_SPLASH_DEPTH) surfaceSplash(s)
        // The Lantern comes into its own as the hook enters the dark.
        if (s.lanternActive && !lanternLit && depth >= GameConfig.DARK_DEPTH) {
            lanternLit = true
            audio.play(Sfx.LANTERN)
        }
    }

    private fun surfaceSplash(s: CastSession) {
        if (surfaceSplashed || s.hookDepth > SURFACE_SPLASH_DEPTH) return
        surfaceSplashed = true
        val load = s.catches.size + s.snags.size
        audio.play(Sfx.SURFACE_SPLASH, gain = if (load > 0) 1f else 0.6f)
        juice.addBurst(BurstKind.SURFACE_SPLASH, s.hookX, 0f, s.elapsedSeconds, strength = 1f + 0.15f * load.coerceAtMost(6))
    }

    private fun updateMusic(depth: Float, force: Boolean = false) {
        if (!force && abs(depth - lastMusicDepth) < MUSIC_STEP_M) return
        lastMusicDepth = depth
        val deep = musicDepthMix(depth)
        audio.setMusicMix(mapOf(MusicTrack.SHALLOWS to 1f - deep, MusicTrack.DEEP to deep))
    }

    // ---- App lifecycle -------------------------------------------------------------------------

    fun onBackground() = audio.pause()

    fun onForeground() = audio.resume()

    fun release() = audio.release()

    companion object {
        const val SETTING_MASTER = "master_volume"
        const val SETTING_MUSIC = "music_volume"
        const val SETTING_SFX = "sfx_volume"
        const val SETTING_HAPTICS = "haptics"
        const val SETTING_REDUCE_MOTION = "reduce_motion"

        private val MENU_MIX = mapOf(MusicTrack.MENU to 1f)
        private const val FIRST_BUBBLE_SEC = 0.4f
        private const val STREAK_WINDOW_SEC = 1.5f
        private const val REEL_TICK_METERS = 3f
        private const val SURFACE_SPLASH_DEPTH = 3f
        /** The music mix is updated every few meters of depth (it crossfades anyway). */
        private const val MUSIC_STEP_M = 4f

        /** How much of the dark layer plays at [depth]: none until 150 m above the dark, all 50 m into it. */
        fun musicDepthMix(depth: Float): Float {
            val from = GameConfig.DARK_DEPTH - 150f
            val to = GameConfig.DARK_DEPTH + 50f
            val t = ((depth - from) / (to - from)).coerceIn(0f, 1f)
            return t * t * (3f - 2f * t)
        }

        fun create(context: Context, store: KeyValueStore): GameFeel {
            // The phone's "remove animations" accessibility setting is the default for Reduce motion.
            val systemReduceMotion = runCatching {
                Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
            }.getOrDefault(false)
            return GameFeel(AudioEngines.create(context), Haptics.create(context), store, systemReduceMotion)
        }
    }
}
