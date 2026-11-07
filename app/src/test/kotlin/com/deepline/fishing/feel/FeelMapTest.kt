package com.deepline.fishing.feel

import com.deepline.fishing.audio.Sfx
import com.deepline.fishing.core.BumpKind
import com.deepline.fishing.core.CastEvent
import com.deepline.fishing.core.CastResult
import com.deepline.fishing.core.CatchRecord
import com.deepline.fishing.core.FishCatalog
import com.deepline.fishing.core.GameConfig
import com.deepline.fishing.core.ObstacleKind
import com.deepline.fishing.core.Progression
import com.deepline.fishing.core.ReverseReason
import com.deepline.fishing.core.SnagRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FeelMapTest {
    private val limit = GameConfig.DESCENT_TOUCH_LIMIT
    private val common = FishCatalog.all.first { Progression.rarityStars(it) == 1 }
    private val rare = FishCatalog.regular.first { Progression.rarityStars(it) in GameConfig.Feel.RARE_CATCH_STARS..4 }

    private fun record(species: com.deepline.fishing.core.FishSpecies, sparkling: Boolean = false) =
        CatchRecord(species, sparkling, 50f, 10)

    @Test
    fun everyBumpKindHasItsOwnSound() {
        val sounds = BumpKind.entries.map { FeelMap.bump(it, 1, limit).sounds.first().sfx }
        assertEquals(listOf(Sfx.BUMP_FISH, Sfx.BUMP_WOOD, Sfx.BUMP_ROCK, Sfx.BUMP_JELLY), sounds)
    }

    @Test
    fun theLastBumpIsStronger() {
        val first = FeelMap.forCastEvent(CastEvent.Touched(1, BumpKind.ROCK), limit)
        val last = FeelMap.forCastEvent(CastEvent.Touched(limit, BumpKind.ROCK), limit)
        assertEquals(Haptic.BUMP, first.haptic)
        assertEquals(Haptic.BUMP_HEAVY, last.haptic)
        assertTrue(Sfx.BUMP_LAST in last.sounds.map { it.sfx })
        assertFalse(Sfx.BUMP_LAST in first.sounds.map { it.sfx })
        assertTrue(last.trauma > first.trauma)
        assertTrue(last.burstStrength > first.burstStrength)
        // Each bump sounds a little lower and louder than the one before.
        val pitches = (1..limit).map { FeelMap.bump(BumpKind.FISH_HEAD, it, limit).sounds.first().pitch }
        assertEquals(pitches.sortedDescending(), pitches)
        // With a Lucky charm's extra bump, the 4th isn't the last any more.
        assertNull(FeelMap.forCastEvent(CastEvent.Touched(limit, BumpKind.ROCK), limit + 1).sounds.firstOrNull { it.sfx == Sfx.BUMP_LAST })
    }

    @Test
    fun catchesScaleWithRarity() {
        assertEquals(CatchTier.COMMON, FeelMap.tierOf(common, sparkling = false))
        assertEquals(CatchTier.SPARKLING, FeelMap.tierOf(common, sparkling = true))
        assertEquals(CatchTier.RARE, FeelMap.tierOf(rare, sparkling = false))
        assertEquals(CatchTier.LEGENDARY, FeelMap.tierOf(FishCatalog.sovereign, sparkling = false))

        val c = FeelMap.forCastEvent(CastEvent.Caught(record(common)), limit)
        assertEquals(listOf(Sfx.CATCH), c.sounds.map { it.sfx })
        assertEquals(Haptic.CATCH, c.haptic)
        assertFalse(c.slowMo)
        assertEquals(0f, c.flash, 0f)

        val s = FeelMap.forCastEvent(CastEvent.Caught(record(common, sparkling = true)), limit)
        assertTrue(Sfx.SPARKLE in s.sounds.map { it.sfx })
        assertTrue(s.slowMo && s.flash > 0f && s.goldFlash && s.glints)

        val r = FeelMap.forCastEvent(CastEvent.Caught(record(rare)), limit)
        assertEquals(Sfx.CATCH_RARE, r.sounds.first().sfx)
        assertEquals(Haptic.CATCH_RARE, r.haptic)
        assertTrue(r.slowMo && r.flash > 0f)

        val l = FeelMap.catchOf(FishCatalog.sovereign, sparkling = false)
        assertTrue(Sfx.CATCH_LEGENDARY in l.sounds.map { it.sfx })
        assertEquals(Haptic.CATCH_EPIC, l.haptic)
        assertTrue(l.slowMo && l.flash >= r.flash && l.burstStrength > r.burstStrength && l.trauma > r.trauma)
    }

    @Test
    fun aStreakOfCatchesClimbsTheScale() {
        val pitches = (0..9).map { FeelMap.forCastEvent(CastEvent.Caught(record(common)), limit, streak = it).sounds.first().pitch }
        assertEquals(1f, pitches.first(), 1e-6f)
        assertEquals(pitches.sorted(), pitches)
        // Capped at a fifth (7 semitones).
        assertEquals(FeelMap.semitones(7), pitches.last(), 1e-6f)
        assertEquals(1.4983f, FeelMap.semitones(7), 1e-3f)
    }

    @Test
    fun theLastBumpHookLeavesTheHapticToTheBump() {
        val fish = FeelMap.forCastEvent(CastEvent.LastBumpHooked(record(common), null), limit)
        assertNull(fish.haptic)
        assertEquals(Sfx.CATCH, fish.sounds.first().sfx)
        val snag = FeelMap.forCastEvent(CastEvent.LastBumpHooked(null, SnagRecord(ObstacleKind.ROCK, 80f, 300)), limit)
        assertEquals(listOf(Sfx.SNAG), snag.sounds.map { it.sfx })
        assertTrue(snag.trauma > 0f)
    }

    @Test
    fun otherCastMoments() {
        assertEquals(Sfx.FAST_REEL, FeelMap.forCastEvent(CastEvent.FastReel, limit).sounds.single().sfx)
        assertEquals(Haptic.REEL, FeelMap.forCastEvent(CastEvent.FastReel, limit).haptic)
        assertEquals(Sfx.TOO_HEAVY, FeelMap.forCastEvent(CastEvent.TooHeavy(common), limit).sounds.single().sfx)
        assertEquals(Feedback.NONE, FeelMap.forCastEvent(CastEvent.Reversed(ReverseReason.ROPE_END), limit))
        assertEquals(Feedback.NONE, FeelMap.forCastEvent(CastEvent.Surfaced, limit))
        val empty = CastResult(emptyList(), 120f, 0, ReverseReason.ROPE_END, false)
        assertEquals(Sfx.RESULT_EMPTY, FeelMap.forResult(empty).sounds.single().sfx)
        assertEquals(Sfx.RESULT_GOOD, FeelMap.forResult(empty.copy(catches = listOf(record(common)))).sounds.single().sfx)
    }

    @Test
    fun menuCues() {
        assertEquals(listOf(Sfx.CLAIM, Sfx.REWARD), FeelMap.forCue(Cue.CLAIM).sounds.map { it.sfx })
        assertEquals(Haptic.SUCCESS, FeelMap.forCue(Cue.CLAIM).haptic)
        assertEquals(Haptic.SUCCESS, FeelMap.forCue(Cue.BATTLE_WIN).haptic)
        assertNull("No haptic for a loss", FeelMap.forCue(Cue.BATTLE_LOSE).haptic)
        // Ordinary taps never vibrate.
        assertNull(FeelMap.forCue(Cue.UI_TAP).haptic)
        assertNull(FeelMap.forCue(Cue.UI_TOGGLE).haptic)
        // Secrets share one sound that tells nothing about which one it was.
        assertEquals(listOf(Sfx.SECRET), FeelMap.forCue(Cue.SECRET).sounds.map { it.sfx })
        for (c in Cue.entries) assertTrue("$c makes a sound", FeelMap.forCue(c).sounds.isNotEmpty())
    }

    @Test
    fun jinglesAreNeverDetuned() {
        // Random pitch only suits noises; tuned sounds would go out of key.
        val tuned = listOf(Sfx.CATCH, Sfx.CATCH_RARE, Sfx.CATCH_LEGENDARY, Sfx.RESULT_GOOD, Sfx.RESULT_EMPTY, Sfx.REWARD, Sfx.BATTLE_WIN, Sfx.BATTLE_LOSE, Sfx.BATTLE_DRAW, Sfx.SECRET)
        for (s in tuned) assertEquals("$s has random pitch", 0f, s.pitchJitter, 0f)
    }
}
