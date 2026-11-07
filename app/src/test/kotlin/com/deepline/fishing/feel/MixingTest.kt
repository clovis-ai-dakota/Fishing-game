package com.deepline.fishing.feel

import com.deepline.fishing.audio.MusicMixer
import com.deepline.fishing.audio.MusicTrack
import com.deepline.fishing.audio.Sfx
import com.deepline.fishing.audio.SfxMixer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class MixingTest {
    @Test
    fun soundsVaryWithinTheirRange() {
        val m = SfxMixer(Random(1))
        val sfx = Sfx.BUBBLE
        val rates = HashSet<Float>()
        var now = 0L
        repeat(200) {
            now += 1_000
            val p = m.plan(sfx, gain = 1f, pitch = 1f, nowMs = now)!!
            assertTrue(p.rate in (1f - sfx.pitchJitter)..(1f + sfx.pitchJitter))
            assertTrue(p.volume <= sfx.volume * (1f + sfx.volumeJitter) + 1e-6f)
            assertTrue(p.volume >= sfx.volume * (1f - sfx.volumeJitter) - 1e-6f)
            rates += p.rate
        }
        assertTrue("pitch is randomised", rates.size > 50)
    }

    @Test
    fun theSameFileNeverPlaysTwiceInARow() {
        val m = SfxMixer(Random(7))
        var last = -1
        var now = 0L
        val seen = HashSet<Int>()
        repeat(100) {
            now += 1_000
            val f = m.plan(Sfx.CAST_SPLASH, 1f, 1f, now)!!.file
            assertNotEquals(last, f)
            last = f
            seen += f
        }
        assertEquals(Sfx.CAST_SPLASH.files.indices.toSet(), seen)
    }

    @Test
    fun repeatsTooCloseTogetherAreDropped() {
        val m = SfxMixer(Random(2))
        assertNotNull(m.plan(Sfx.TOO_HEAVY, 1f, 1f, 1_000))
        assertNull(m.plan(Sfx.TOO_HEAVY, 1f, 1f, 1_000 + Sfx.TOO_HEAVY.minGapMs - 1))
        assertNotNull(m.plan(Sfx.TOO_HEAVY, 1f, 1f, 1_000 + Sfx.TOO_HEAVY.minGapMs + 1))
    }

    @Test
    fun voicesAreLimitedByStealingTheOldest() {
        val m = SfxMixer(Random(3))
        val sfx = Sfx.CATCH
        var now = 0L
        val steals = ArrayList<Int?>()
        for (handle in 1..sfx.maxVoices + 2) {
            now += 100
            val p = m.plan(sfx, 1f, 1f, now)!!
            steals += p.steal
            m.started(p, handle, now)
        }
        assertEquals(List(sfx.maxVoices) { null } + listOf(1, 2), steals)
        assertEquals(sfx.maxVoices, m.activeVoices(sfx, now))
        // Old voices are counted as finished after a while.
        assertEquals(0, m.activeVoices(sfx, now + SfxMixer.VOICE_HOLD_MS + 1))
    }

    @Test
    fun mutedMeansSilent() {
        val m = SfxMixer(Random(4))
        m.sfxVolume = 0f
        assertNull(m.plan(Sfx.CATCH, 1f, 1f, 0))
        m.sfxVolume = 1f
        m.master = 0f
        assertNull(m.plan(Sfx.CATCH, 1f, 1f, 10_000))
        m.master = 0.5f
        val p = m.plan(Sfx.CATCH, 1f, 1f, 20_000)!!
        assertTrue(p.volume <= 0.5f * Sfx.CATCH.volume * 1.06f)
        // Pitch requests are clamped to what SoundPool can play.
        assertEquals(SfxMixer.MAX_RATE, m.plan(Sfx.CATCH, 1f, 9f, 30_000)!!.rate, 0f)
    }

    @Test
    fun musicCrossfadesSmoothly() {
        val m = MusicMixer()
        m.setTargets(mapOf(MusicTrack.MENU to 1f))
        m.step(10f)
        assertEquals(1f, m.level(MusicTrack.MENU), 0f)
        // Into a cast: the menu fades out while the shallow layer fades in, over the crossfade time.
        m.setTargets(mapOf(MusicTrack.SHALLOWS to 1f))
        val half = MusicMixer.CROSSFADE_SECONDS / 2f
        m.step(half)
        assertEquals(0.5f, m.level(MusicTrack.MENU), 1e-4f)
        assertEquals(0.5f, m.level(MusicTrack.SHALLOWS), 1e-4f)
        assertTrue(m.fading)
        m.step(half)
        assertEquals(0f, m.level(MusicTrack.MENU), 1e-4f)
        assertEquals(1f, m.level(MusicTrack.SHALLOWS), 1e-4f)
        assertFalse(m.fading)
        assertFalse(m.wanted(MusicTrack.MENU))
        // The volume follows the settings and ducking.
        m.musicVolume = 0.5f
        m.master = 0.5f
        m.duck = 0.5f
        assertEquals(MusicTrack.SHALLOWS.volume * 0.125f, m.volume(MusicTrack.SHALLOWS), 1e-5f)
    }

    @Test
    fun theDeepLayerTakesOverAroundTheDark() {
        assertEquals(0f, GameFeel.musicDepthMix(0f), 0f)
        assertEquals(0f, GameFeel.musicDepthMix(100f), 0f)
        assertEquals(1f, GameFeel.musicDepthMix(400f), 0f)
        var last = 0f
        for (d in 0..620 step 10) {
            val k = GameFeel.musicDepthMix(d.toFloat())
            assertTrue(k >= last)
            last = k
        }
    }
}
