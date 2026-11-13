package com.deepline.fishing.ui.render3d.gl

import android.app.ActivityManager
import android.content.Context
import android.opengl.GLSurfaceView
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.deepline.fishing.ui.render3d.Frame
import javax.microedition.khronos.egl.EGL10
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.egl.EGLDisplay

/** Whether the 3D renderer can run here (and hasn't failed): otherwise the app draws in 2D. */
object Render3d {
    /** Set when the GL renderer failed on this device: the rest of the session uses 2D. */
    var failed by mutableStateOf(false)

    /**
     * Store screenshots only (`StoreScreenshotsTest`, never set by the app): the cast screen
     * draws just what goes over the 3D view (the 2D overlay and the HUD) on a transparent
     * background, with no GL view, so the WebGL preview can draw the same frame underneath.
     */
    @androidx.annotation.VisibleForTesting
    internal var overlayOnly = false

    fun isSupported(context: Context): Boolean {
        if (failed) return false
        // Robolectric (the UI tests) has no GPU.
        if (Build.FINGERPRINT == "robolectric") return false
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager ?: return false
        return am.deviceConfigurationInfo.reqGlEsVersion >= 0x30000
    }
}

/** A GL surface that draws whatever [Frame] it was last given. */
class Scene3dView(context: Context, onFirstFrame: () -> Unit = {}) : GLSurfaceView(context) {
    private var drewOne = false
    private val backend = GlBackend(
        context.assets,
        onError = { post { Render3d.failed = true } },
        onFrameDrawn = {
            if (!drewOne) {
                drewOne = true
                post(onFirstFrame)
            }
        },
    )

    init {
        setEGLContextClientVersion(3)
        setEGLConfigChooser(ConfigChooser())
        preserveEGLContextOnPause = true
        setRenderer(backend)
        renderMode = RENDERMODE_WHEN_DIRTY
    }

    /** The size frames are rendered at: at most [MAX_RENDER_WIDTH] wide, scaled up to the view. */
    var renderWidth = 0
        private set
    var renderHeight = 0
        private set

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val k = minOf(1f, MAX_RENDER_WIDTH / w.coerceAtLeast(1).toFloat())
        renderWidth = (w * k).toInt().coerceAtLeast(1)
        renderHeight = (h * k).toInt().coerceAtLeast(1)
        holder.setFixedSize(renderWidth, renderHeight)
    }

    fun show(frame: Frame) {
        backend.submit(frame)
        requestRender()
    }

    companion object {
        /**
         * Full HD phones render at about two thirds of their width: the fill-rate cost drops by
         * more than half, the compositor scales it up for free, and the 2D overlay (labels, aim,
         * HUD) stays sharp because it is drawn at full resolution on top.
         */
        const val MAX_RENDER_WIDTH = 720f
    }
}

/** Lets a screen push frames to its 3D view from its own frame loop. */
class Scene3dHandle {
    internal var view: Scene3dView? = null

    /** True once the 3D view has drawn a frame (until then, the screen shows the 2D scene). */
    var ready by mutableStateOf(false)
        internal set

    /** Builds a frame for the view's current size (once it has one) and shows it. */
    fun render(build: (widthPx: Int, heightPx: Int) -> Frame) {
        val v = view ?: return
        if (v.renderWidth <= 0 || v.renderHeight <= 0) return
        v.show(build(v.renderWidth, v.renderHeight))
    }
}

@Composable
fun Scene3d(handle: Scene3dHandle, modifier: Modifier = Modifier) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    AndroidView(
        factory = { ctx -> Scene3dView(ctx) { handle.ready = true }.also { handle.view = it } },
        modifier = modifier,
        onRelease = { if (handle.view === it) handle.view = null },
    )
    DisposableEffect(lifecycle, handle) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> handle.view?.onPause()
                Lifecycle.Event.ON_RESUME -> handle.view?.onResume()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
}

/** RGB888 with depth, 4x multisampling when available (smooth ink outlines). */
private class ConfigChooser : GLSurfaceView.EGLConfigChooser {
    override fun chooseConfig(egl: EGL10, display: EGLDisplay): EGLConfig {
        val es3 = 0x40 // EGL_OPENGL_ES3_BIT_KHR
        val es2 = 4 // EGL_OPENGL_ES2_BIT
        val tries = listOf(24, 16).flatMap { depth -> listOf(4, 0).flatMap { samples -> listOf(Triple(depth, samples, es3), Triple(depth, samples, es2)) } }
        for ((depth, samples, renderable) in tries) {
            val attrs = intArrayOf(
                EGL10.EGL_RED_SIZE, 8, EGL10.EGL_GREEN_SIZE, 8, EGL10.EGL_BLUE_SIZE, 8,
                EGL10.EGL_DEPTH_SIZE, depth,
                0x3040, renderable, // EGL_RENDERABLE_TYPE
                EGL10.EGL_SAMPLE_BUFFERS, if (samples > 0) 1 else 0,
                EGL10.EGL_SAMPLES, samples,
                EGL10.EGL_NONE,
            )
            val num = IntArray(1)
            val configs = arrayOfNulls<EGLConfig>(1)
            if (egl.eglChooseConfig(display, attrs, configs, 1, num) && num[0] > 0 && configs[0] != null) return configs[0]!!
        }
        error("No EGL config")
    }
}
