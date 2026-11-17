package com.deepline.fishing.ui

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.ui.graphics.TransformOrigin
import com.deepline.fishing.Screen

/**
 * How the game moves from one screen to the next (see docs/ui-design.md). Every move says where
 * the player is going, and none lasts a second:
 *
 * - a building's menu grows out of the building that was tapped on the village map, and shrinks
 *   back into it on the way back (faster); any other screen grows out of the middle;
 * - casting dives: the menu leaves by the top as the water comes up from below; collecting the
 *   catch surfaces again.
 *
 * Durations follow the usual guidance for interface motion (Nielsen Norman Group, "Executing UX
 * animations: duration and motion characteristics"): about 100 ms for simple feedback, 200 to
 * 300 ms for a screen change, 400 ms only for a big move, never 500 ms (it feels like a drag),
 * and leaving a little faster than arriving.
 *
 * With "Reduce motion" on ([calm]), every move is a short fade.
 */
object ScreenMotion {
    const val PUSH_MS = 240
    const val POP_MS = 170
    const val DIVE_MS = 400
    const val SURFACE_MS = 300
    const val CALM_MS = 120

    /**
     * No motion at all, for tests that drive the clock by hand: with the clock paused a move
     * would stay half-way, both screens showing.
     */
    @Volatile
    var instant = false

    private val none: ContentTransform get() = androidx.compose.animation.EnterTransition.None togetherWith androidx.compose.animation.ExitTransition.None

    private val calmFade: ContentTransform get() = fadeIn(tween(CALM_MS)) togetherWith fadeOut(tween(CALM_MS))

    /** How far from the village a screen is: back always goes up a level. */
    fun level(screen: Screen): Int = when (screen) {
        Screen.VILLAGE -> 0
        Screen.HOME, Screen.DEX, Screen.SHOP, Screen.BATTLE_SETUP, Screen.ROPE, Screen.AQUARIUM,
        Screen.MILESTONES, Screen.THEMES, Screen.SETTINGS, Screen.DAILY -> 1
        else -> 2
    }

    /**
     * From [from] to [to]. [origin] is where a building's menu grows from (fractions of the
     * screen; the middle when null).
     */
    fun between(from: Screen, to: Screen, calm: Boolean, origin: Pair<Float, Float>? = null): ContentTransform {
        if (instant) return none
        if (calm) return calmFade
        val pivot = origin?.let { TransformOrigin(it.first, it.second) } ?: TransformOrigin.Center
        return when {
            to == Screen.CAST || to == Screen.BATTLE && from == Screen.CAST ->
                if (to == Screen.CAST) {
                    (slideInVertically(tween(DIVE_MS, easing = FastOutSlowInEasing)) { it } + fadeIn(tween(DIVE_MS / 2))) togetherWith
                        (slideOutVertically(tween(DIVE_MS, easing = FastOutSlowInEasing)) { -it } + fadeOut(tween(DIVE_MS)))
                } else {
                    fadeIn(tween(PUSH_MS)) togetherWith fadeOut(tween(PUSH_MS))
                }
            from == Screen.CAST ->
                (slideInVertically(tween(SURFACE_MS, easing = FastOutSlowInEasing)) { -it } + fadeIn(tween(SURFACE_MS))) togetherWith
                    (slideOutVertically(tween(SURFACE_MS, easing = FastOutSlowInEasing)) { it } + fadeOut(tween(SURFACE_MS / 2)))
            // Back up a level: the screen shrinks away (into its building), faster than it came.
            level(to) < level(from) -> {
                val into = if (to == Screen.VILLAGE) pivot else TransformOrigin.Center
                fadeIn(tween(POP_MS)) togetherWith (scaleOut(tween(POP_MS), targetScale = 0.6f, transformOrigin = into) + fadeOut(tween(POP_MS)))
            }
            else -> {
                val out = if (from == Screen.VILLAGE) pivot else TransformOrigin.Center
                val start = if (from == Screen.VILLAGE) 0.6f else 0.94f
                (scaleIn(tween(PUSH_MS, easing = FastOutSlowInEasing), initialScale = start, transformOrigin = out) + fadeIn(tween(PUSH_MS))) togetherWith fadeOut(tween(PUSH_MS / 2))
            }
        }
    }
}
