package com.deepline.fishing.ui.render3d.gl

import android.content.Context
import android.graphics.SurfaceTexture
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLSurface
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.view.Choreographer
import android.view.TextureView
import com.deepline.fishing.ui.render3d.Frame

/** Builds the frames of a live preview, on its render thread. */
fun interface FrameSource {
    fun frame(widthPx: Int, heightPx: Int, timeSeconds: Float): Frame
}

/**
 * A small live 3D view (the boat on the Fish tab, the skins preview, the Dex turntable). Unlike
 * a SurfaceView it scrolls, clips to rounded corners and can be transparent like any view. It
 * renders on its own thread, paced by the display, at a reduced resolution and frame rate.
 */
class GlTextureView(
    context: Context,
    private val source: FrameSource,
    private val transparent: Boolean,
    private val maxRenderWidth: Int = 480,
    private val everyNthVsync: Int = 2,
) : TextureView(context), TextureView.SurfaceTextureListener {
    private var renderer: RenderThread? = null

    init {
        isOpaque = !transparent
        surfaceTextureListener = this
    }

    fun setPaused(paused: Boolean) {
        renderer?.setPaused(paused)
    }

    override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
        val (rw, rh) = renderSize(width, height)
        surface.setDefaultBufferSize(rw, rh)
        renderer = RenderThread(context.applicationContext, surface, rw, rh, source, transparent, everyNthVsync).also { it.begin() }
    }

    override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {
        val (rw, rh) = renderSize(width, height)
        surface.setDefaultBufferSize(rw, rh)
        renderer?.resize(rw, rh)
    }

    override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
        renderer?.end()
        renderer = null
        return true
    }

    override fun onSurfaceTextureUpdated(surface: SurfaceTexture) = Unit

    private fun renderSize(w: Int, h: Int): Pair<Int, Int> {
        val k = minOf(1f, maxRenderWidth / w.coerceAtLeast(1).toFloat())
        return (w * k).toInt().coerceAtLeast(16) to (h * k).toInt().coerceAtLeast(16)
    }
}

private class RenderThread(
    private val appContext: Context,
    private val texture: SurfaceTexture,
    @Volatile private var width: Int,
    @Volatile private var height: Int,
    private val source: FrameSource,
    private val transparent: Boolean,
    private val everyNthVsync: Int,
) : Choreographer.FrameCallback {
    private val thread = HandlerThread("DeepLine3D-preview")
    private lateinit var handler: Handler
    private var display: EGLDisplay = EGL14.EGL_NO_DISPLAY
    private var context: EGLContext = EGL14.EGL_NO_CONTEXT
    private var surface: EGLSurface = EGL14.EGL_NO_SURFACE
    private var backend: GlBackend? = null
    private var running = false
    @Volatile private var paused = false
    private var vsyncs = 0
    private val start = System.nanoTime()

    fun begin() {
        thread.start()
        handler = Handler(thread.looper)
        handler.post {
            running = setUp()
            if (running) Choreographer.getInstance().postFrameCallback(this)
        }
    }

    fun resize(w: Int, h: Int) {
        width = w
        height = h
    }

    fun setPaused(p: Boolean) {
        paused = p
    }

    fun end() {
        handler.post {
            running = false
            Choreographer.getInstance().removeFrameCallback(this)
            tearDown()
            thread.quitSafely()
        }
    }

    override fun doFrame(frameTimeNanos: Long) {
        if (!running) return
        Choreographer.getInstance().postFrameCallback(this)
        if (paused || vsyncs++ % everyNthVsync != 0) return
        val b = backend ?: return
        try {
            val t = (System.nanoTime() - start) / 1_000_000_000f
            val w = width
            val h = height
            b.submit(source.frame(w, h, t))
            b.onSurfaceChanged(null, w, h)
            b.onDrawFrame(null)
            EGL14.eglSwapBuffers(display, surface)
            if (b.hasFailed) stopWithError("Preview draw failed")
        } catch (e: Exception) {
            stopWithError("Preview: ${e.message}")
        }
    }

    private fun stopWithError(message: String) {
        Log.e("DeepLine3D", message)
        running = false
        OffscreenGl.markFailed()
    }

    private fun setUp(): Boolean {
        display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        val v = IntArray(2)
        if (!EGL14.eglInitialize(display, v, 0, v, 1)) return false
        val config = chooseConfig() ?: return false
        context = EGL14.eglCreateContext(display, config, EGL14.EGL_NO_CONTEXT, intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 3, EGL14.EGL_NONE), 0)
        if (context == EGL14.EGL_NO_CONTEXT) return false
        surface = EGL14.eglCreateWindowSurface(display, config, texture, intArrayOf(EGL14.EGL_NONE), 0)
        if (surface == EGL14.EGL_NO_SURFACE) return false
        if (!EGL14.eglMakeCurrent(display, surface, surface, context)) return false
        val b = GlBackend(appContext.assets, onError = { OffscreenGl.markFailed() })
        b.clearColor = if (transparent) floatArrayOf(0f, 0f, 0f, 0f) else floatArrayOf(0f, 0f, 0f, 1f)
        b.onSurfaceCreated(null, null)
        backend = b
        return !b.hasFailed
    }

    private fun chooseConfig(): EGLConfig? {
        for (samples in listOf(4, 0)) {
            val attrs = intArrayOf(
                EGL14.EGL_RED_SIZE, 8, EGL14.EGL_GREEN_SIZE, 8, EGL14.EGL_BLUE_SIZE, 8, EGL14.EGL_ALPHA_SIZE, 8,
                EGL14.EGL_DEPTH_SIZE, 16,
                EGL14.EGL_RENDERABLE_TYPE, 0x40, // EGL_OPENGL_ES3_BIT_KHR
                EGL14.EGL_SURFACE_TYPE, EGL14.EGL_WINDOW_BIT,
                EGL14.EGL_SAMPLE_BUFFERS, if (samples > 0) 1 else 0,
                EGL14.EGL_SAMPLES, samples,
                EGL14.EGL_NONE,
            )
            val configs = arrayOfNulls<EGLConfig>(1)
            val num = IntArray(1)
            if (EGL14.eglChooseConfig(display, attrs, 0, configs, 0, 1, num, 0) && num[0] > 0) return configs[0]
        }
        return null
    }

    private fun tearDown() {
        if (display != EGL14.EGL_NO_DISPLAY) {
            EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
            if (surface != EGL14.EGL_NO_SURFACE) EGL14.eglDestroySurface(display, surface)
            if (context != EGL14.EGL_NO_CONTEXT) EGL14.eglDestroyContext(display, context)
        }
        surface = EGL14.EGL_NO_SURFACE
        context = EGL14.EGL_NO_CONTEXT
        backend = null
    }
}
