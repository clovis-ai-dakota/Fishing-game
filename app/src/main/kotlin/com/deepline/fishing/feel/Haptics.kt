package com.deepline.fishing.feel

import android.content.Context
import android.media.AudioAttributes
import android.os.Build
import android.os.SystemClock
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.VibrationEffect.Composition as C
import android.os.Vibrator
import android.os.VibratorManager

/**
 * The game's haptic effects, from lightest to strongest. Used sparingly (bumps, catches, the fast
 * reel and big wins; never on ordinary taps) and always with a sound, as Android's haptics
 * guidelines recommend.
 */
enum class Haptic {
    /** A light confirmation (a purchase). */
    TICK,
    /** A bump on the way down. */
    BUMP,
    /** The last bump (the line turns back) or a snag. */
    BUMP_HEAVY,
    /** A fish on the line. */
    CATCH,
    /** A rare or Sparkling catch, a secret. */
    CATCH_RARE,
    /** The legendary fish. */
    CATCH_EPIC,
    /** The fast reel starting. */
    REEL,
    /** A reward claimed, a battle won. */
    SUCCESS,
}

interface Haptics {
    /** Whether this phone can vibrate at all (the setting is hidden otherwise). */
    val available: Boolean

    fun perform(haptic: Haptic)

    companion object {
        /** Tests may replace this before the app starts. */
        @Volatile
        var factory: ((Context) -> Haptics)? = null

        fun create(context: Context): Haptics =
            factory?.invoke(context) ?: runCatching { AndroidHaptics(context.applicationContext) }.getOrElse { NoHaptics }
    }
}

object NoHaptics : Haptics {
    override val available = false
    override fun perform(haptic: Haptic) = Unit
}

/**
 * Plays [Haptic]s with the best API the phone has:
 *
 * - Android 11+ with rich actuators: `VibrationEffect.Composition` primitives (click, thud,
 *   spin, rises), when the phone supports every primitive of the effect.
 * - Android 10+: predefined effects (tick, click, heavy click, double click).
 * - Android 8 to 9: short one-shots, with amplitude where the motor supports it.
 * - Android 7: short on/off pulses, and nothing for the lightest effects (a buzz is worse than no
 *   haptic at all).
 *
 * Effects closer together than [MIN_GAP_MS] are dropped so they never blur into a buzz.
 */
class AndroidHaptics(context: Context) : Haptics {
    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

    override val available: Boolean = vibrator?.hasVibrator() == true

    private var lastAt = 0L

    override fun perform(haptic: Haptic) {
        val v = vibrator ?: return
        if (!available) return
        val now = SystemClock.uptimeMillis()
        if (now - lastAt < MIN_GAP_MS) return
        lastAt = now
        runCatching { vibrate(v, haptic) }
    }

    private fun vibrate(v: Vibrator, haptic: Haptic) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) composition(v, haptic)?.let { return play(v, it) }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) return play(v, VibrationEffect.createPredefined(predefined(haptic)))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val (ms, amplitude) = oneShot(haptic)
            val a = if (v.hasAmplitudeControl()) amplitude else VibrationEffect.DEFAULT_AMPLITUDE
            return play(v, VibrationEffect.createOneShot(ms, a))
        }
        // Android 7: plain on/off. Skip the lightest effects rather than buzz.
        if (haptic == Haptic.TICK || haptic == Haptic.BUMP) return
        @Suppress("DEPRECATION")
        v.vibrate(oneShot(haptic).first)
    }

    private fun play(v: Vibrator, effect: VibrationEffect) {
        if (Build.VERSION.SDK_INT >= 33) {
            // Media-style feedback (games): follows the phone's media vibration intensity.
            v.vibrate(effect, VibrationAttributes.createForUsage(VibrationAttributes.USAGE_MEDIA))
        } else {
            @Suppress("DEPRECATION")
            v.vibrate(effect, AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).build())
        }
    }

    /** A composition of primitives, or null when the phone lacks one of them. */
    private fun composition(v: Vibrator, haptic: Haptic): VibrationEffect? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
        val thud = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) C.PRIMITIVE_THUD else C.PRIMITIVE_CLICK
        val spin = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) C.PRIMITIVE_SPIN else C.PRIMITIVE_QUICK_RISE
        // (primitive, scale 0..1, delay before it in ms)
        val parts: List<Triple<Int, Float, Int>> = when (haptic) {
            Haptic.TICK -> listOf(Triple(C.PRIMITIVE_TICK, 0.6f, 0))
            Haptic.BUMP -> listOf(Triple(C.PRIMITIVE_CLICK, 0.55f, 0))
            Haptic.BUMP_HEAVY -> listOf(Triple(thud, 1f, 0))
            Haptic.CATCH -> listOf(Triple(C.PRIMITIVE_CLICK, 0.8f, 0))
            Haptic.CATCH_RARE -> listOf(Triple(C.PRIMITIVE_CLICK, 0.7f, 0), Triple(C.PRIMITIVE_CLICK, 1f, 70))
            Haptic.CATCH_EPIC -> listOf(Triple(C.PRIMITIVE_SLOW_RISE, 0.6f, 0), Triple(C.PRIMITIVE_CLICK, 1f, 0), Triple(thud, 1f, 90))
            Haptic.REEL -> listOf(Triple(spin, 0.6f, 0), Triple(spin, 0.4f, 40))
            Haptic.SUCCESS -> listOf(Triple(C.PRIMITIVE_QUICK_RISE, 0.6f, 0), Triple(C.PRIMITIVE_CLICK, 1f, 30))
        }
        if (!v.areAllPrimitivesSupported(*parts.map { it.first }.distinct().toIntArray())) return null
        val c = VibrationEffect.startComposition()
        for ((primitive, scale, delay) in parts) c.addPrimitive(primitive, scale, delay)
        return c.compose()
    }

    private fun predefined(haptic: Haptic): Int = when (haptic) {
        Haptic.TICK, Haptic.BUMP -> VibrationEffect.EFFECT_TICK
        Haptic.CATCH, Haptic.REEL -> VibrationEffect.EFFECT_CLICK
        Haptic.BUMP_HEAVY, Haptic.CATCH_EPIC -> VibrationEffect.EFFECT_HEAVY_CLICK
        Haptic.CATCH_RARE, Haptic.SUCCESS -> VibrationEffect.EFFECT_DOUBLE_CLICK
    }

    /** Duration (ms) and amplitude (1..255) for phones without predefined effects. */
    private fun oneShot(haptic: Haptic): Pair<Long, Int> = when (haptic) {
        Haptic.TICK -> 10L to 60
        Haptic.BUMP -> 15L to 110
        Haptic.CATCH, Haptic.REEL -> 20L to 160
        Haptic.BUMP_HEAVY, Haptic.CATCH_RARE, Haptic.SUCCESS -> 30L to 220
        Haptic.CATCH_EPIC -> 45L to 255
    }

    private companion object {
        const val MIN_GAP_MS = 60L
    }
}
