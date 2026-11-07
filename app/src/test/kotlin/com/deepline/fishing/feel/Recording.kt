package com.deepline.fishing.feel

import com.deepline.fishing.audio.AudioEngine
import com.deepline.fishing.audio.MusicTrack
import com.deepline.fishing.audio.Sfx
import java.util.Collections

/** An audio engine that only remembers what it was asked to do (tests). */
class RecordingAudio : AudioEngine {
    data class Play(val sfx: Sfx, val gain: Float, val pitch: Float)

    val plays: MutableList<Play> = Collections.synchronizedList(ArrayList())
    var mix: Map<MusicTrack, Float> = emptyMap()
    var volumes = Triple(1f, 1f, 1f)
    var paused = false
    var released = false

    val played: List<Sfx> get() = synchronized(plays) { plays.map { it.sfx } }

    override fun play(sfx: Sfx, gain: Float, pitch: Float) {
        plays += Play(sfx, gain, pitch)
    }

    override fun setMusicMix(mix: Map<MusicTrack, Float>) {
        this.mix = mix
    }

    override fun setVolumes(master: Float, music: Float, sfx: Float) {
        volumes = Triple(master, music, sfx)
    }

    override fun pause() {
        paused = true
    }

    override fun resume() {
        paused = false
    }

    override fun release() {
        released = true
    }
}

class RecordingHaptics(override val available: Boolean = true) : Haptics {
    val performed: MutableList<Haptic> = Collections.synchronizedList(ArrayList())

    override fun perform(haptic: Haptic) {
        performed += haptic
    }
}
