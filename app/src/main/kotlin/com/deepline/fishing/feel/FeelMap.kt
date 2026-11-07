package com.deepline.fishing.feel

import com.deepline.fishing.audio.Sfx
import com.deepline.fishing.core.BumpKind
import com.deepline.fishing.core.CastEvent
import com.deepline.fishing.core.CastResult
import com.deepline.fishing.core.FishSpecies
import com.deepline.fishing.core.GameConfig
import com.deepline.fishing.core.Progression
import kotlin.math.pow

/** One sound to play: [gain] on top of the sound's own volume, [pitch] 1 as recorded. */
data class SoundCue(val sfx: Sfx, val gain: Float = 1f, val pitch: Float = 1f)

/**
 * Everything a moment of the game triggers: sounds, a haptic, and the render-only juice (shake
 * trauma, a slow-motion moment, a flash, a particle burst). [GameFeel] applies it, dropping what
 * the settings turn off.
 */
data class Feedback(
    val sounds: List<SoundCue> = emptyList(),
    val haptic: Haptic? = null,
    val trauma: Float = 0f,
    /** A hit-stop then slow motion (solo casts only, see [GameConfig.Feel]). */
    val slowMo: Boolean = false,
    /** Flash strength 0..1 (0: none). */
    val flash: Float = 0f,
    val goldFlash: Boolean = false,
    val burst: BurstKind? = null,
    val burstStrength: Float = 1f,
    val glints: Boolean = false,
) {
    companion object {
        val NONE = Feedback()
    }
}

/** Moments outside casts that make a sound. */
enum class Cue { UI_TAP, UI_TOGGLE, CLAIM, PURCHASE, SECRET, LANTERN, BATTLE_WIN, BATTLE_LOSE, BATTLE_DRAW }

/** How rare a catch feels. */
enum class CatchTier { COMMON, SPARKLING, RARE, LEGENDARY }

/**
 * What each game event sounds and feels like (pure, so it's unit tested). Bumps get heavier as
 * they're used up and the last one adds a thud; catches scale with rarity; secrets all share one
 * sound that reveals nothing about which secret it was.
 */
object FeelMap {
    fun tierOf(species: FishSpecies, sparkling: Boolean): CatchTier {
        val stars = Progression.rarityStars(species)
        return when {
            species.lastBumpOnly || stars >= 5 -> CatchTier.LEGENDARY
            stars >= GameConfig.Feel.RARE_CATCH_STARS -> CatchTier.RARE
            sparkling -> CatchTier.SPARKLING
            else -> CatchTier.COMMON
        }
    }

    /**
     * [touchLimit]: bumps allowed this cast. [streak]: catches in a row just before this one
     * (each raises a common catch's chime a semitone, up to a fifth).
     */
    fun forCastEvent(event: CastEvent, touchLimit: Int, streak: Int = 0): Feedback = when (event) {
        is CastEvent.Touched -> bump(event.kind, event.touchesUsed, touchLimit)
        is CastEvent.Caught -> catchOf(event.record.species, event.record.sparkling, streak)
        is CastEvent.LastBumpHooked -> {
            val fish = event.fish
            if (fish != null) {
                // The bump itself (its own Touched event) brings the thud; this is the catch.
                catchOf(fish.species, fish.sparkling, streak).copy(haptic = null)
            } else {
                Feedback(sounds = listOf(SoundCue(Sfx.SNAG)), trauma = GameConfig.Feel.TRAUMA_SNAG, burst = BurstKind.BUMP_PUFF, burstStrength = 1.8f)
            }
        }
        is CastEvent.TooHeavy -> Feedback(sounds = listOf(SoundCue(Sfx.TOO_HEAVY)))
        CastEvent.FastReel -> Feedback(sounds = listOf(SoundCue(Sfx.FAST_REEL)), haptic = Haptic.REEL)
        is CastEvent.Reversed, CastEvent.Surfaced -> Feedback.NONE
    }

    fun bump(kind: BumpKind, touchesUsed: Int, touchLimit: Int): Feedback {
        val last = touchesUsed >= touchLimit
        val sfx = when (kind) {
            BumpKind.FISH_HEAD -> Sfx.BUMP_FISH
            BumpKind.ROCK -> Sfx.BUMP_ROCK
            BumpKind.DRIFTWOOD -> Sfx.BUMP_WOOD
            BumpKind.JELLY -> Sfx.BUMP_JELLY
        }
        // Each bump a little lower and louder: the line is running out of chances.
        val used = (touchesUsed - 1).coerceAtLeast(0)
        val main = SoundCue(sfx, gain = (0.8f + 0.07f * used).coerceAtMost(1f), pitch = 1f - 0.05f * used)
        return Feedback(
            sounds = if (last) listOf(main, SoundCue(Sfx.BUMP_LAST)) else listOf(main),
            haptic = if (last) Haptic.BUMP_HEAVY else Haptic.BUMP,
            trauma = if (last) GameConfig.Feel.TRAUMA_LAST_BUMP else GameConfig.Feel.TRAUMA_BUMP,
            burst = BurstKind.BUMP_PUFF,
            burstStrength = if (last) 1.6f else 1f,
        )
    }

    fun catchOf(species: FishSpecies, sparkling: Boolean, streak: Int = 0): Feedback {
        val sparkle = if (sparkling) listOf(SoundCue(Sfx.SPARKLE)) else emptyList()
        return when (tierOf(species, sparkling)) {
            CatchTier.LEGENDARY -> Feedback(
                sounds = listOf(SoundCue(Sfx.CATCH_RARE), SoundCue(Sfx.CATCH_LEGENDARY)) + sparkle,
                haptic = Haptic.CATCH_EPIC,
                trauma = GameConfig.Feel.TRAUMA_RARE_CATCH * 1.6f,
                slowMo = true, flash = 1f, goldFlash = true,
                burst = BurstKind.CATCH_BUBBLES, burstStrength = 2.2f, glints = true,
            )
            CatchTier.RARE -> Feedback(
                sounds = listOf(SoundCue(Sfx.CATCH_RARE)) + sparkle,
                haptic = Haptic.CATCH_RARE,
                trauma = GameConfig.Feel.TRAUMA_RARE_CATCH,
                slowMo = true, flash = 0.75f, goldFlash = sparkling,
                burst = BurstKind.CATCH_BUBBLES, burstStrength = 1.6f, glints = sparkling,
            )
            CatchTier.SPARKLING -> Feedback(
                sounds = listOf(SoundCue(Sfx.CATCH, pitch = semitones(streak))) + sparkle,
                haptic = Haptic.CATCH_RARE,
                slowMo = true, flash = 0.6f, goldFlash = true,
                burst = BurstKind.CATCH_BUBBLES, burstStrength = 1.3f, glints = true,
            )
            CatchTier.COMMON -> Feedback(
                sounds = listOf(SoundCue(Sfx.CATCH, gain = 0.85f, pitch = semitones(streak))),
                haptic = Haptic.CATCH,
                burst = BurstKind.CATCH_BUBBLES,
            )
        }
    }

    fun forResult(result: CastResult): Feedback =
        if (result.catches.isEmpty() && result.snags.isEmpty()) {
            Feedback(sounds = listOf(SoundCue(Sfx.RESULT_EMPTY)))
        } else {
            Feedback(sounds = listOf(SoundCue(Sfx.RESULT_GOOD)))
        }

    fun forCue(cue: Cue): Feedback = when (cue) {
        Cue.UI_TAP -> Feedback(sounds = listOf(SoundCue(Sfx.UI_TAP)))
        Cue.UI_TOGGLE -> Feedback(sounds = listOf(SoundCue(Sfx.UI_TOGGLE)))
        Cue.CLAIM -> Feedback(sounds = listOf(SoundCue(Sfx.CLAIM), SoundCue(Sfx.REWARD)), haptic = Haptic.SUCCESS)
        Cue.PURCHASE -> Feedback(sounds = listOf(SoundCue(Sfx.PURCHASE)), haptic = Haptic.TICK)
        // The same sound for every secret: it never hints at which one.
        Cue.SECRET -> Feedback(sounds = listOf(SoundCue(Sfx.SECRET)), haptic = Haptic.CATCH_RARE)
        Cue.LANTERN -> Feedback(sounds = listOf(SoundCue(Sfx.LANTERN)))
        Cue.BATTLE_WIN -> Feedback(sounds = listOf(SoundCue(Sfx.BATTLE_WIN)), haptic = Haptic.SUCCESS)
        Cue.BATTLE_LOSE -> Feedback(sounds = listOf(SoundCue(Sfx.BATTLE_LOSE)))
        Cue.BATTLE_DRAW -> Feedback(sounds = listOf(SoundCue(Sfx.BATTLE_DRAW)))
    }

    /** A pitch [steps] semitones up (capped at a fifth). */
    fun semitones(steps: Int): Float = 2f.pow(steps.coerceIn(0, MAX_STREAK_SEMITONES) / 12f)

    const val MAX_STREAK_SEMITONES = 7
}
