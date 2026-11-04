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
    // The drag angle is read by the render thread.
    val turn = remember(species) { floatArrayOf(0f) }
    val source = remember(species, look, sparkling) {
        FrameSource { w, h, t ->
            PortraitScene.build(species, look, w, h, SceneBuilder.FACING_RIGHT + sin(t * 0.6f) * 0.45f + turn[0], t, sparkling)
        }
    }
    Box(modifier.pointerInput(species) { detectHorizontalDragGestures { _, dx -> turn[0] += dx * 0.012f } }) {
        LivePreview(source, transparent = true, modifier = Modifier.matchParentSize(), key = "$species$look$sparkling")
    }
}

/**
 * The boat, line and hook at the surface wearing [skins], animated (the Fish tab and the Skins
 * screens), or [fallback] where 3D isn't available.
 */
@Composable
fun SkinPreview3d(skins: Skins, modifier: Modifier = Modifier, fallback: @Composable androidx.compose.foundation.layout.BoxScope.() -> Unit) {
    val context = LocalContext.current
    val supported = remember { Render3d.isSupported(context) } && !OffscreenGl.failed
    if (!supported) {
        Box(modifier) { fallback() }
        return
    }
    // The skins can change while the preview runs: the render thread reads the latest.
    val current = remember { arrayOf(skins) }
    current[0] = skins
    val source = remember {
        val session = CastSession(RopeSpecs(120f, 10f, 9f), false, World(emptyList(), emptyList())).also {
            while (it.hookDepth < 5f) it.update(1f / 60f, 2f)
        }
        val builder = SceneBuilder()
        FrameSource { w, h, t -> builder.build(session, 0.8f + t, w, h, current[0], cameraTop = -6.5f) }
    }
    Box(modifier) {
        LivePreview(source, transparent = false, modifier = Modifier.matchParentSize(), key = "skins")
    }
}

/** Hosts a [GlTextureView], paused with the screen's lifecycle. */
@Composable
private fun LivePreview(source: FrameSource, transparent: Boolean, modifier: Modifier, key: Any) {
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    val viewRef = remember(key) { arrayOfNulls<GlTextureView>(1) }
    androidx.compose.runtime.key(key) {
        androidx.compose.ui.viewinterop.AndroidView(
            factory = { ctx -> GlTextureView(ctx, source, transparent).also { viewRef[0] = it } },
            modifier = modifier,
        )
    }
    androidx.compose.runtime.DisposableEffect(lifecycle, key) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            when (event) {
                androidx.lifecycle.Lifecycle.Event.ON_PAUSE -> viewRef[0]?.setPaused(true)
                androidx.lifecycle.Lifecycle.Event.ON_RESUME -> viewRef[0]?.setPaused(false)
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
}
