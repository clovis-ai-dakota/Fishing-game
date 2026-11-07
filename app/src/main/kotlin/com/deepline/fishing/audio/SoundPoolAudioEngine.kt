package com.deepline.fishing.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.SoundPool
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import java.io.File
import java.util.EnumMap

/**
 * The game's audio on Android:
 *
 * - Sound effects: a [SoundPool] (low latency, decoded once), with per-sound variation and voice
 *   limits from [SfxMixer].
 * - Music: one looping [MediaPlayer] per [MusicTrack], crossfaded by [MusicMixer] (a 20 Hz tick
 *   on the main thread while a fade runs). A track's player is created the first time it's needed
 *   and paused while silent.
 * - Audio focus: music asks for [AudioManager.AUDIOFOCUS_GAIN] (usage GAME) when it starts,
 *   pauses on a loss, ducks when asked to (Android 8+ ducks for us), and gives focus back when the
 *   app goes to the background. If another app is already playing music when ours would start,
 *   the game keeps quiet and lets it play (sound effects still play).
 * - Background: [pause] silences everything; [resume] picks up where it was.
 *
 * Everything runs on the main thread.
 */
class SoundPoolAudioEngine(private val context: Context) : AudioEngine {
    private val main = Handler(Looper.getMainLooper())
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private val sfxAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_GAME)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()
    private val musicAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_GAME)
        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
        .build()

    private val pool = SoundPool.Builder().setMaxStreams(MAX_STREAMS).setAudioAttributes(sfxAttributes).build()
    private val loaded = HashSet<Int>()
    private val samples: Map<Sfx, IntArray>
    private val sfxMixer = SfxMixer()
    private val music = MusicMixer()
    private val players = EnumMap<MusicTrack, Player>(MusicTrack::class.java)

    /** Starts true: nothing plays (and no audio focus is asked for) until the app is on screen. */
    private var background = true
    private var released = false
    private var hasFocus = false
    private var focusRequested = false
    /** Another app was playing music when ours wanted to start: stay quiet until the next resume. */
    private var otherMusicPlaying = false

    init {
        pool.setOnLoadCompleteListener { _, id, status -> if (status == 0) loaded += id }
        samples = Sfx.entries.associateWith { s -> IntArray(s.files.size) { i -> load(s.assets[i]) } }
    }

    // ---- Sound effects ---------------------------------------------------------------------------

    private fun load(asset: String): Int = try {
        context.assets.openFd(asset).use { pool.load(it, 1) }
    } catch (e: Exception) {
        // A compressed asset has no file descriptor: play it from a copy in the cache.
        runCatching { pool.load(cached(asset).path, 1) }.getOrElse {
            Log.w(TAG, "Can't load $asset: ${it.message}")
            0
        }
    }

    override fun play(sfx: Sfx, gain: Float, pitch: Float) {
        if (background || released) return
        val now = SystemClock.uptimeMillis()
        val plan = sfxMixer.plan(sfx, gain, pitch, now) ?: return
        val id = samples[sfx]?.getOrNull(plan.file) ?: return
        if (id == 0 || id !in loaded) return
        plan.steal?.let { pool.stop(it) }
        val stream = pool.play(id, plan.volume, plan.volume, 1, 0, plan.rate)
        if (stream != 0) sfxMixer.started(plan, stream, now)
    }

    // ---- Music ----------------------------------------------------------------------------------

    override fun setMusicMix(mix: Map<MusicTrack, Float>) {
        music.setTargets(mix)
        kick()
    }

    override fun setVolumes(master: Float, music: Float, sfx: Float) {
        sfxMixer.master = master
        sfxMixer.sfxVolume = sfx
        this.music.master = master
        this.music.musicVolume = music
        kick()
    }

    private var ticking = false
    private var lastTickMs = 0L
    private val tick = object : Runnable {
        override fun run() {
            val now = SystemClock.uptimeMillis()
            music.step((now - lastTickMs) / 1000f)
            lastTickMs = now
            applyPlayers()
            if (music.fading && !released) {
                main.postDelayed(this, TICK_MS)
            } else {
                ticking = false
            }
        }
    }

    /** Applies the mix now and keeps ticking while a crossfade runs. */
    private fun kick() {
        if (released) return
        applyPlayers()
        if (!ticking && music.fading) {
            ticking = true
            lastTickMs = SystemClock.uptimeMillis()
            main.postDelayed(tick, TICK_MS)
        }
    }

    private fun applyPlayers() {
        val audible = music.musicVolume * music.master > 0f
        val wantsMusic = audible && MusicTrack.entries.any { music.wanted(it) }
        if (wantsMusic && !background) ensureFocus()
        // Music turned all the way down: pause the players rather than play them silently.
        val canPlay = !background && hasFocus && !otherMusicPlaying && audible
        for (t in MusicTrack.entries) {
            val wanted = canPlay && music.wanted(t)
            var p = players[t]
            if (p == null && wanted) {
                p = runCatching { Player(t) }.getOrNull() ?: continue
                players[t] = p
            }
            p?.apply(music.volume(t), wanted)
        }
    }

    private inner class Player(track: MusicTrack) {
        private val mp = MediaPlayer()
        private var prepared = false
        private var failed = false
        private var volume = 0f
        private var shouldPlay = false

        init {
            mp.setAudioAttributes(musicAttributes)
            try {
                context.assets.openFd(track.asset).use { mp.setDataSource(it.fileDescriptor, it.startOffset, it.length) }
            } catch (e: Exception) {
                mp.setDataSource(cached(track.asset).path)
            }
            mp.isLooping = true
            mp.setVolume(0f, 0f)
            mp.setOnPreparedListener {
                prepared = true
                apply(volume, shouldPlay)
            }
            mp.setOnErrorListener { _, what, extra ->
                Log.w(TAG, "Music ${track.name} failed ($what, $extra)")
                failed = true
                true
            }
            mp.prepareAsync()
        }

        fun apply(volume: Float, play: Boolean) {
            this.volume = volume
            shouldPlay = play
            if (!prepared || failed) return
            mp.setVolume(volume, volume)
            if (play && !mp.isPlaying) mp.start() else if (!play && mp.isPlaying) mp.pause()
        }

        val isPlaying: Boolean get() = prepared && !failed && mp.isPlaying

        fun release() {
            runCatching { mp.release() }
        }
    }

    // ---- Audio focus ----------------------------------------------------------------------------

    private val focusListener = AudioManager.OnAudioFocusChangeListener { change ->
        when (change) {
            AudioManager.AUDIOFOCUS_GAIN -> {
                hasFocus = true
                music.duck = 1f
            }
            AudioManager.AUDIOFOCUS_LOSS -> {
                // Another app took over for good: stay quiet until the player comes back to the game.
                hasFocus = false
                focusRequested = false
                abandonFocus()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> hasFocus = false
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> music.duck = DUCK_LEVEL
        }
        kick()
    }

    private val focusRequest: AudioFocusRequest? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(musicAttributes)
            .setAcceptsDelayedFocusGain(true)
            .setWillPauseWhenDucked(false)
            .setOnAudioFocusChangeListener(focusListener, main)
            .build()
    } else {
        null
    }

    private fun ensureFocus() {
        if (hasFocus || focusRequested || otherMusicPlaying) return
        // Someone is listening to their own music: keep ours off (effects still play).
        if (players.values.none { it.isPlaying } && audioManager.isMusicActive) {
            otherMusicPlaying = true
            return
        }
        focusRequested = true
        val result = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioManager.requestAudioFocus(focusRequest!!)
        } else {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(focusListener, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN)
        }
        // DELAYED: the listener hears about it when focus frees up (e.g. after a call).
        hasFocus = result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        if (result == AudioManager.AUDIOFOCUS_REQUEST_FAILED) focusRequested = false
    }

    private fun abandonFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioManager.abandonAudioFocusRequest(focusRequest!!)
        } else {
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus(focusListener)
        }
    }

    // ---- Lifecycle ------------------------------------------------------------------------------

    override fun pause() {
        if (background || released) return
        background = true
        pool.autoPause()
        sfxMixer.clearVoices()
        applyPlayers()
        if (hasFocus || focusRequested) abandonFocus()
        hasFocus = false
        focusRequested = false
    }

    override fun resume() {
        if (!background || released) return
        background = false
        otherMusicPlaying = false
        pool.autoResume()
        kick()
    }

    override fun release() {
        if (released) return
        released = true
        main.removeCallbacks(tick)
        players.values.forEach { it.release() }
        players.clear()
        pool.release()
        if (hasFocus || focusRequested) abandonFocus()
    }

    private fun cached(asset: String): File {
        val f = File(context.cacheDir, asset.replace('/', '_'))
        if (!f.exists()) context.assets.open(asset).use { input -> f.outputStream().use { input.copyTo(it) } }
        return f
    }

    private companion object {
        const val TAG = "DeepLineAudio"
        const val MAX_STREAMS = 12
        const val TICK_MS = 50L
        const val DUCK_LEVEL = 0.25f
    }
}
