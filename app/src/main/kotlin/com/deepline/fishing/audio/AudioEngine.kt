package com.deepline.fishing.audio

import android.content.Context
import android.os.Build

/**
 * Plays the game's sound effects and music. The app uses [SoundPoolAudioEngine]; Robolectric
 * tests get [NoAudio] (or a recording fake through [AudioEngines.factory]).
 */
interface AudioEngine {
    /** Plays [sfx] once, at [gain] (0..1) on top of its own mix volume, at [pitch] (1: as recorded). */
    fun play(sfx: Sfx, gain: Float = 1f, pitch: Float = 1f)

    /** How loud each music track should be (0..1; a missing track is 0). The engine crossfades. */
    fun setMusicMix(mix: Map<MusicTrack, Float>)

    /** The player's volumes (0..1): everything, the music, the sound effects. */
    fun setVolumes(master: Float, music: Float, sfx: Float)

    /** The app went to the background: silence everything until [resume]. */
    fun pause()

    fun resume()

    fun release()
}

/** Plays nothing (tests, and phones where audio failed to start). */
object NoAudio : AudioEngine {
    override fun play(sfx: Sfx, gain: Float, pitch: Float) = Unit
    override fun setMusicMix(mix: Map<MusicTrack, Float>) = Unit
    override fun setVolumes(master: Float, music: Float, sfx: Float) = Unit
    override fun pause() = Unit
    override fun resume() = Unit
    override fun release() = Unit
}

object AudioEngines {
    /** Tests may replace this before the app starts (e.g. with a recording fake). */
    @Volatile
    var factory: ((Context) -> AudioEngine)? = null

    fun create(context: Context): AudioEngine {
        factory?.let { return it(context) }
        // Robolectric (the UI tests) has no audio output.
        if (Build.FINGERPRINT == "robolectric") return NoAudio
        return runCatching { SoundPoolAudioEngine(context.applicationContext) }.getOrElse { NoAudio }
    }
}
