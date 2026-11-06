package com.deepline.fishing.audio

import kotlin.math.abs
import kotlin.random.Random

/**
 * Decides how each sound effect plays (pure logic, so it can be unit tested; the engine only
 * executes the plans): which of its files, how loud and at what pitch (with the sound's random
 * variation), and which older voice to stop when it already has [Sfx.maxVoices] playing.
 */
class SfxMixer(private val random: Random = Random.Default) {
    /** One play: file index in [Sfx.files], volume (0..1), playback rate, and a voice to stop first. */
    data class Plan(val sfx: Sfx, val file: Int, val volume: Float, val rate: Float, val steal: Int?)

    var master = 1f
    var sfxVolume = 1f

    private class Voice(val handle: Int, val startedMs: Long)

    private val voices = HashMap<Sfx, ArrayDeque<Voice>>()
    private val lastPlayedMs = HashMap<Sfx, Long>()
    private val lastFile = HashMap<Sfx, Int>()

    /** How to play [sfx] now, or null when it shouldn't play (muted, or repeated too soon). */
    fun plan(sfx: Sfx, gain: Float, pitch: Float, nowMs: Long): Plan? {
        val base = master * sfxVolume * sfx.volume * gain.coerceIn(0f, 1f)
        if (base <= 0.001f) return null
        val last = lastPlayedMs[sfx]
        if (last != null && nowMs - last < sfx.minGapMs) return null
        lastPlayedMs[sfx] = nowMs
        val file = pickFile(sfx)
        val volume = (base * (1f + sfx.volumeJitter * (random.nextFloat() * 2f - 1f))).coerceIn(0f, 1f)
        val rate = (pitch * (1f + sfx.pitchJitter * (random.nextFloat() * 2f - 1f))).coerceIn(MIN_RATE, MAX_RATE)
        val active = voices.getOrPut(sfx) { ArrayDeque() }
        active.removeAll { nowMs - it.startedMs > VOICE_HOLD_MS }
        val steal = if (active.size >= sfx.maxVoices) active.removeFirst().handle else null
        return Plan(sfx, file, volume, rate, steal)
    }

    /** The engine started [plan] as voice [handle]. */
    fun started(plan: Plan, handle: Int, nowMs: Long) {
        voices.getOrPut(plan.sfx) { ArrayDeque() }.addLast(Voice(handle, nowMs))
    }

    /** Voices currently counted as playing for [sfx]. */
    fun activeVoices(sfx: Sfx, nowMs: Long): Int = voices[sfx]?.count { nowMs - it.startedMs <= VOICE_HOLD_MS } ?: 0

    fun clearVoices() = voices.clear()

    /** A random file, never the one played last (when there's a choice). */
    private fun pickFile(sfx: Sfx): Int {
        val n = sfx.files.size
        if (n == 1) return 0
        val previous = lastFile[sfx]
        var pick = random.nextInt(n)
        if (pick == previous) pick = (pick + 1 + random.nextInt(n - 1)) % n
        lastFile[sfx] = pick
        return pick
    }

    companion object {
        /** SoundPool plays at 0.5x to 2x. */
        const val MIN_RATE = 0.5f
        const val MAX_RATE = 2f
        /** A voice counts as playing this long after it started (most sounds are under a second). */
        const val VOICE_HOLD_MS = 1_500L
    }
}

/**
 * The music's crossfades (pure logic): each track's level glides towards its target at
 * [fadePerSecond], and its output volume is that level times the track's mix volume, the music and
 * master volumes and any ducking.
 */
class MusicMixer(private val fadePerSecond: Float = 1f / CROSSFADE_SECONDS) {
    private val levels = HashMap<MusicTrack, Float>()
    private var targets: Map<MusicTrack, Float> = emptyMap()

    var master = 1f
    var musicVolume = 1f
    /** Lowered while another app briefly needs the speaker (audio focus "can duck"). */
    var duck = 1f

    fun setTargets(mix: Map<MusicTrack, Float>) {
        targets = mix.mapValues { it.value.coerceIn(0f, 1f) }
    }

    fun target(track: MusicTrack): Float = targets[track] ?: 0f

    fun level(track: MusicTrack): Float = levels[track] ?: 0f

    /** True while some track hasn't reached its target yet. */
    val fading: Boolean get() = MusicTrack.entries.any { abs(level(it) - target(it)) > 1e-4f }

    /** Moves every level towards its target by [dtSec]. */
    fun step(dtSec: Float) {
        val step = fadePerSecond * dtSec.coerceAtLeast(0f)
        for (t in MusicTrack.entries) {
            val l = level(t)
            val goal = target(t)
            levels[t] = if (abs(goal - l) <= step) goal else l + step * if (goal > l) 1f else -1f
        }
    }

    /** The volume to give [track]'s player now. */
    fun volume(track: MusicTrack): Float = (level(track) * track.volume * musicVolume * master * duck).coerceIn(0f, 1f)

    /** Whether [track] should be playing at all (audible, or about to be). */
    fun wanted(track: MusicTrack): Boolean = level(track) > 0f || target(track) > 0f

    companion object {
        /** A full crossfade takes this long. */
        const val CROSSFADE_SECONDS = 2.5f
    }
}
