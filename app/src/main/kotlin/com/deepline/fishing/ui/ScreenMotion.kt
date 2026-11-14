package com.deepline.fishing.ui

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import com.deepline.fishing.MainTab
import com.deepline.fishing.Screen
import com.deepline.fishing.ui.theme.CAMERA_MS
import com.deepline.fishing.ui.theme.Place

/**
 * How the game moves from one screen to the next (see docs/ui-design.md). Every move says where
 * the player is going in the world, and none lasts a second:
 *
 * - between tabs, the content slides the way the camera travels ([CAMERA_MS]): sideways along the
 *   coast, down into the water for the Dex;
 * - a screen opened from a menu grows out of it, and shrinks back into it faster on the way back;
 * - casting dives: the menu leaves by the top as the water comes up from below; collecting the
 *   catch surfaces again.
 *
 * With "Reduce motion" on ([calm]), every move is a short fade.
 */
object ScreenMotion {
    const val PUSH_MS = 240
    const val POP_MS = 170
    const val DIVE_MS = 600
    const val SURFACE_MS = 450
    const val CALM_MS = 120

    private val calmFade: ContentTransform get() = fadeIn(tween(CALM_MS)) togetherWith fadeOut(tween(CALM_MS))

    /** Between the tab layer (null) and pushed screens. */
    fun between(from: Screen?, to: Screen?, calm: Boolean): ContentTransform = when {
        calm -> calmFade
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
        // Back to the tabs: the screen shrinks away, faster than it came.
        to == null ->
            fadeIn(tween(POP_MS)) togetherWith (scaleOut(tween(POP_MS), targetScale = 0.94f) + fadeOut(tween(POP_MS)))
        else ->
            (scaleIn(tween(PUSH_MS, easing = FastOutSlowInEasing), initialScale = 0.94f) + fadeIn(tween(PUSH_MS))) togetherWith fadeOut(tween(PUSH_MS / 2))
    }

    /** Between two tabs: the way the camera goes from one place to the other. */
    fun betweenTabs(from: MainTab, to: MainTab, calm: Boolean): ContentTransform {
        if (calm) return calmFade
        val a = Place.of(from.screen)
        val b = Place.of(to.screen)
        val spec = tween<androidx.compose.ui.unit.IntOffset>(CAMERA_MS, easing = FastOutSlowInEasing)
        return when {
            // Down into the water, or back up to the surface.
            b.y > a.y -> (slideInVertically(spec) { it } + fadeIn(tween(CAMERA_MS))) togetherWith (slideOutVertically(spec) { -it } + fadeOut(tween(CAMERA_MS)))
            b.y < a.y -> (slideInVertically(spec) { -it } + fadeIn(tween(CAMERA_MS))) togetherWith (slideOutVertically(spec) { it } + fadeOut(tween(CAMERA_MS)))
            b.x >= a.x -> slideInHorizontally(spec) { it } togetherWith slideOutHorizontally(spec) { -it }
            else -> slideInHorizontally(spec) { -it } togetherWith slideOutHorizontally(spec) { it }
        }
    }
}
