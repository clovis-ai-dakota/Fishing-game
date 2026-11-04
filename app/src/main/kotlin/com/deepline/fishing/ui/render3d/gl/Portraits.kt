package com.deepline.fishing.ui.render3d.gl

import android.graphics.Bitmap
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import com.deepline.fishing.core.CastSession
import com.deepline.fishing.core.FishSpecies
import com.deepline.fishing.core.RopeSpecs
import com.deepline.fishing.core.World
import com.deepline.fishing.ui.Skins
import com.deepline.fishing.ui.render3d.PortraitLook
import com.deepline.fishing.ui.render3d.PortraitScene
import com.deepline.fishing.ui.render3d.SceneBuilder
import kotlinx.coroutines.isActive
import kotlin.math.min
import kotlin.math.sin

/** Dex thumbnails, by species, look and size. */
private val thumbnails = object : LruCache<String, Bitmap>(48 * 1024 * 1024) {
    override fun sizeOf(key: String, value: Bitmap) = value.byteCount
}

private const val THUMB_W = 384
private const val THUMB_H = 240

/**
 * A 3D render of [species] for the Dex, or [fallback] (the 2D art) while it renders and where 3D
 * isn't available. [large] gives a live turntable: it sways, and a horizontal drag turns it.
 */
@Composable
fun FishPortrait3d(
    species: FishSpecies,
    look: PortraitLook,
    modifier: Modifier = Modifier,
    large: Boolean = false,
    sparkling: Boolean = false,
    fallback: @Composable androidx.compose.foundation.layout.BoxScope.() -> Unit,
) {
    val context = LocalContext.current
    val supported = remember { Render3d.isSupported(context) } && !OffscreenGl.failed
    if (!supported) {
        Box(modifier) { fallback() }
        return
    }
    if (large) {
        FishTurntable(species, look, sparkling, modifier, fallback)
        return
    }
    val key = "${species.id}:${look.name}:$sparkling"
    val bitmap by produceState(thumbnails.get(key), key) {
        if (value == null) {
            value = OffscreenGl.render(context, PortraitScene.build(species, look, THUMB_W, THUMB_H, SceneBuilder.FACING_RIGHT, 0.4f, sparkling))
                ?.also { thumbnails.put(key, it) }
        }
    }
    Box(modifier) {
        val b = bitmap
        if (b != null) {
            Image(b.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize(), filterQuality = FilterQuality.High)
        } else {
            fallback()
        }
    }
}

@Composable
private fun FishTurntable(species: FishSpecies, look: PortraitLook, sparkling: Boolean, modifier: Modifier, fallback: @Composable androidx.compose.foundation.layout.BoxScope.() -> Unit) {
    val context = LocalContext.current
    var turn by remember(species) { mutableFloatStateOf(0f) }
    var shown by remember(species, look) { mutableStateOf<ImageBitmap?>(null) }
    BoxWithConstraints(modifier.pointerInput(species) { detectHorizontalDragGestures { _, dx -> turn += dx * 0.012f } }) {
        val w = min(constraints.maxWidth.takeIf { it > 0 } ?: 600, 720)
        val h = (w / 1.5f).toInt().coerceAtLeast(64)
        LaunchedEffect(species, look, w, h) {
            val buffers = arrayOf(Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888), Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888))
            var i = 0
            val start = System.nanoTime()
            while (isActive) {
                val t = (System.nanoTime() - start) / 1_000_000_000f
                val yaw = SceneBuilder.FACING_RIGHT + sin(t * 0.6f) * 0.45f + turn
                val frame = PortraitScene.build(species, look, w, h, yaw, t, sparkling)
                val bmp = OffscreenGl.render(context, frame, into = buffers[i]) ?: break
                shown = bmp.asImageBitmap()
                i = 1 - i
                withFrameNanos { }
            }
        }
        val s = shown
        if (s != null) Image(s, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize(), filterQuality = FilterQuality.High)
        else fallback()
    }
}

/**
 * The boat, line and hook at the surface wearing [skins], animated (for the Skins screen), or
 * [fallback] where 3D isn't available.
 */
@Composable
fun SkinPreview3d(skins: Skins, modifier: Modifier = Modifier, fallback: @Composable androidx.compose.foundation.layout.BoxScope.() -> Unit) {
    val context = LocalContext.current
    val supported = remember { Render3d.isSupported(context) } && !OffscreenGl.failed
    if (!supported) {
        Box(modifier) { fallback() }
        return
    }
    val session = remember {
        CastSession(RopeSpecs(120f, 10f, 9f), false, World(emptyList(), emptyList())).also {
            while (it.hookDepth < 5f) it.update(1f / 60f, 2f)
        }
    }
    var shown by remember { mutableStateOf<ImageBitmap?>(null) }
    BoxWithConstraints(modifier) {
        // Rendered at up to 540 px wide and scaled up: plenty for a preview, and light on the GPU.
        val fullW = constraints.maxWidth.takeIf { it > 0 } ?: 540
        val fullH = constraints.maxHeight.takeIf { it in 1..4000 } ?: (fullW * 1.2f).toInt()
        val k = min(1f, 540f / fullW)
        val w = (fullW * k).toInt().coerceAtLeast(64)
        val h = (fullH * k).toInt().coerceAtLeast(64)
        LaunchedEffect(skins, w, h) {
            val builder = SceneBuilder()
            val buffers = arrayOf(Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888), Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888))
            var i = 0
            val start = System.nanoTime()
            while (isActive) {
                val t = 0.8f + (System.nanoTime() - start) / 1_000_000_000f
                val bmp = OffscreenGl.render(context, builder.build(session, t, w, h, skins, cameraTop = -6.5f), opaque = true, into = buffers[i]) ?: break
                shown = bmp.asImageBitmap()
                i = 1 - i
                withFrameNanos { }
            }
        }
        val s = shown
        if (s != null) Image(s, contentDescription = null, contentScale = ContentScale.FillBounds, modifier = Modifier.fillMaxSize(), filterQuality = FilterQuality.High)
        else fallback()
    }
}
