package com.deepline.fishing.feel

import com.deepline.fishing.audio.MusicTrack
import com.deepline.fishing.audio.Sfx
import com.deepline.fishing.core.BotBrain
import com.deepline.fishing.core.BotDifficulty
import com.deepline.fishing.core.CastEvent
import com.deepline.fishing.core.CastPhase
import com.deepline.fishing.core.CastSession
import com.deepline.fishing.core.CatchRecord
import com.deepline.fishing.core.FishCatalog
import com.deepline.fishing.core.FishEntity
import com.deepline.fishing.core.GameConfig
import com.deepline.fishing.core.InMemoryStore
import com.deepline.fishing.core.Obstacle
import com.deepline.fishing.core.ObstacleKind
import com.deepline.fishing.core.RopeSpecs
import com.deepline.fishing.core.RopeStat
import com.deepline.fishing.core.World
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class GameFeelTest {
    private val audio = RecordingAudio()
    private val haptics = RecordingHaptics()
    private val store = InMemoryStore()
    private fun feel(reduceMotion: Boolean = false) = GameFeel(audio, haptics, store, systemReduceMotion = reduceMotion, random = Random(1))

    private fun rope(level: Int) = RopeSpecs.fromLevels(RopeStat.entries.associateWith { level })

    /** Plays a whole cast like the view model does; returns how much cast time passed per wall-clock second. */
    private fun play(f: GameFeel, s: CastSession, solo: Boolean, seed: Long = 1, steer: Boolean = true): Float {
        f.onCastStart(s)
        val brain = BotBrain(BotDifficulty.HARD, Random(seed))
        var wall = 0.0
        var cast = 0.0
        var frames = 0
        while (s.phase != CastPhase.SURFACED && frames < 30_000) {
            val dt = 1f / 60f
            val sim = f.simDt(dt, solo)
            s.update(sim, if (steer) brain.steer(s, dt) else null)
            f.onCastFrame(s, s.drainEvents())
            f.fx(s)
            wall += dt
            cast += sim
            frames++
        }
        return (cast / wall).toFloat()
    }

    @Test
    fun aCastSoundsLikeACast() {
        val f = feel()
        assertEquals(mapOf(MusicTrack.MENU to 1f), audio.mix)
        val s = CastSession.create(rope(3), lanternActive = false, random = Random(5))
        play(f, s, solo = true)
        val played = audio.played
        assertEquals(Sfx.CAST_SPLASH, played.first())
        assertTrue(Sfx.BUBBLE in played)
        assertTrue("reel clicks on the way up", Sfx.REEL_TICK in played)
        assertTrue(Sfx.SURFACE_SPLASH in played)
        assertTrue("a cast's music is the depth layers", audio.mix.keys == setOf(MusicTrack.SHALLOWS, MusicTrack.DEEP))
        f.onCastResult(s.result!!)
        assertTrue(audio.played.last() == Sfx.RESULT_GOOD || audio.played.last() == Sfx.RESULT_EMPTY)
        f.onCastEnd()
        assertEquals(mapOf(MusicTrack.MENU to 1f), audio.mix)
    }

    @Test
    fun theMusicGetsDarkerWithDepth() {
        val f = feel()
        val s = CastSession(rope(10), false, World(emptyList(), emptyList()))
        f.onCastStart(s)
        assertEquals(1f, audio.mix[MusicTrack.SHALLOWS]!!, 0f)
        while (s.hookDepth < 420f) {
            s.update(f.simDt(1f / 60f, true), null)
            f.onCastFrame(s, s.drainEvents())
        }
        assertEquals(1f, audio.mix[MusicTrack.DEEP]!!, 0f)
        assertEquals(0f, audio.mix[MusicTrack.SHALLOWS]!!, 0f)
    }

    private fun rareCatchSession(): CastSession {
        val rare = FishCatalog.regular.first { com.deepline.fishing.core.Progression.rarityStars(it) in GameConfig.Feel.RARE_CATCH_STARS..4 }
        val c = GameConfig.WORLD_WIDTH / 2f
        // A rare fish facing the hook just below the bump-free zone's end, caught on the way up.
        val fish = FishEntity(0, rare, false, c - rare.bodyLengthM / 2f * 0.8f, 30f, 1, 0f)
        return CastSession(RopeSpecs(40f, 50f, 9f), false, World(listOf(fish), emptyList()))
    }

    @Test
    fun slowMotionOnlyInSoloCasts() {
        val solo = feel()
        val soloSpeed = play(solo, rareCatchSession(), solo = true, steer = false)
        assertTrue("the rare catch slowed the solo cast ($soloSpeed)", soloSpeed < 0.999f)
        val battle = feel()
        assertEquals("battles always run in real time", 1f, play(battle, rareCatchSession(), solo = false, steer = false), 1e-4f)
        assertEquals(1f, battle.timeScale, 0f)
    }

    @Test
    fun reduceMotionTurnsOffShakeSlowMotionAndFlashes() {
        val f = feel(reduceMotion = true)
        assertTrue(f.settings.reduceMotion)
        val s = rareCatchSession()
        f.onCastStart(s)
        var sawParticles = false
        while (s.phase != CastPhase.SURFACED) {
            val dt = 1f / 60f
            assertEquals(dt, f.simDt(dt, solo = true), 0f)
            s.update(dt, null)
            f.onCastFrame(s, s.drainEvents())
            val fx = f.fx(s)
            assertEquals(0f, fx.shakeX, 0f)
            assertEquals(0f, fx.shakeY, 0f)
            assertEquals(0f, fx.flash, 0f)
            sawParticles = sawParticles || fx.particles.isNotEmpty()
        }
        assertTrue("particles stay (they don't move the screen)", sawParticles)
        assertTrue(Sfx.CATCH_RARE in audio.played)
    }

    @Test
    fun bumpsShakeTheDrawingNotTheGame() {
        val f = feel()
        val c = GameConfig.WORLD_WIDTH / 2f
        val rock = Obstacle(0, ObstacleKind.ROCK, c, 70f, 1.2f)
        val s = CastSession(RopeSpecs(200f, 20f, 9f), false, World(emptyList(), listOf(rock)))
        val twin = CastSession(RopeSpecs(200f, 20f, 9f), false, World(emptyList(), listOf(Obstacle(0, ObstacleKind.ROCK, c, 70f, 1.2f))))
        f.onCastStart(s)
        var shook = false
        while (s.touches == 0 || s.elapsedSeconds < 5f) {
            val dt = 1f / 60f
            val sim = f.simDt(dt, solo = true)
            s.update(sim, null)
            twin.update(sim, null)
            f.onCastFrame(s, s.drainEvents())
            val fx = f.fx(s)
            shook = shook || fx.shakeX != 0f || fx.shakeY != 0f
            // The game itself is untouched by the shake.
            assertEquals(twin.hookX, s.hookX, 0f)
            assertEquals(twin.hookDepth, s.hookDepth, 0f)
        }
        assertTrue(shook)
        assertTrue(Sfx.BUMP_ROCK in audio.played)
        assertTrue(Haptic.BUMP in haptics.performed)
    }

    @Test
    fun hapticsCanBeTurnedOff() {
        val f = feel()
        f.cue(Cue.CLAIM)
        assertEquals(listOf(Haptic.SUCCESS), haptics.performed)
        f.update(f.settings.copy(haptics = false))
        f.commit(GameFeel.SETTING_HAPTICS)
        f.cue(Cue.CLAIM)
        assertEquals(1, haptics.performed.size)
        assertEquals(listOf(Sfx.CLAIM, Sfx.REWARD, Sfx.CLAIM, Sfx.REWARD), audio.played)
        // Saved for next time.
        assertFalse(FeelSettings.load(store).haptics)
    }

    @Test
    fun volumesReachTheEngine() {
        val f = feel()
        f.update(f.settings.copy(master = 0.5f, music = 0.25f, sfx = 0.75f))
        assertEquals(Triple(0.5f, 0.25f, 0.75f), audio.volumes)
    }

    @Test
    fun aBattleResultPlaysOnce() {
        val f = feel()
        val battle = Any()
        f.onBattleOutcome(battle, winner = 0, me = 0)
        f.onBattleOutcome(battle, winner = 0, me = 0)
        f.onBattleOutcome(Any(), winner = 1, me = 0)
        f.onBattleOutcome(Any(), winner = null, me = 0)
        f.onBattleOutcome(Any(), winner = 1, me = null)
        assertEquals(listOf(Sfx.BATTLE_WIN, Sfx.BATTLE_LOSE, Sfx.BATTLE_DRAW, Sfx.BATTLE_WIN), audio.played)
    }

    @Test
    fun catchesInARowRiseInPitch() {
        val f = feel()
        val s = CastSession(rope(0), false, World(emptyList(), emptyList()))
        f.onCastStart(s)
        audio.plays.clear()
        val sprat = FishCatalog.byId("silver_sprat")!!
        repeat(3) { f.onCastFrame(s, listOf(CastEvent.Caught(CatchRecord(sprat, false, 10f, 5)))) }
        val pitches = audio.plays.filter { it.sfx == Sfx.CATCH }.map { it.pitch }
        assertEquals(3, pitches.size)
        assertTrue(pitches[0] < pitches[1] && pitches[1] < pitches[2])
    }

    @Test
    fun backgroundAndRelease() {
        val f = feel()
        f.onBackground()
        assertTrue(audio.paused)
        f.onForeground()
        assertFalse(audio.paused)
        f.release()
        assertTrue(audio.released)
    }
}
