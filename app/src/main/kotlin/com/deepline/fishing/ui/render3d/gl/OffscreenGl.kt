package com.deepline.fishing.ui.render3d.gl

import android.content.Context
import android.graphics.Bitmap
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLSurface
import android.opengl.GLES30
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import com.deepline.fishing.ui.render3d.Frame
import kotlinx.coroutines.suspendCancellableCoroutine
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.coroutines.resume

/**
 * Renders frames into bitmaps on a background GL context: Dex portraits, the fish turntable and
 * the skins preview. Pictures can then sit in scrolling lists and rounded cards like any image.
 */
object OffscreenGl {
    /** Set when offscreen rendering failed: pictures then use the 2D art (casts keep their own 3D view). */
    @Volatile
    var failed = false
        private set

    /** Live previews share this flag: after any failure, pictures use the 2D art. */
    fun markFailed() {
        failed = true
    }
    private val thread by lazy { HandlerThread("DeepLine3D-offscreen").apply { start() } }
    private val handler by lazy { Handler(thread.looper) }

    private var display: EGLDisplay = EGL14.EGL_NO_DISPLAY
    private var context: EGLContext = EGL14.EGL_NO_CONTEXT
    private var surface: EGLSurface = EGL14.EGL_NO_SURFACE
    private var backend: GlBackend? = null
    private var fbo = 0
    private var color = 0
    private var depth = 0
    private var fboW = 0
    private var fboH = 0
    private var pixels: ByteBuffer? = null
    private var row: ByteArray = ByteArray(0)

    /**
     * Draws [frame] (transparent where nothing is drawn, unless [opaque]) into [into] if it has
     * the frame's size, else into a new bitmap. Null when 3D isn't available.
     */
    suspend fun render(appContext: Context, frame: Frame, opaque: Boolean = false, into: Bitmap? = null): Bitmap? =
        suspendCancellableCoroutine { cont ->
            handler.post {
                val result = try {
                    renderNow(appContext.applicationContext, frame, opaque, into)
                } catch (e: Exception) {
                    Log.e("DeepLine3D", "Offscreen render failed", e)
                    com.deepline.fishing.analytics.GameAnalytics.problem("render3d_offscreen", e)
                    failed = true
                    null
                }
                if (cont.isActive) cont.resume(result)
            }
        }

    private fun renderNow(appContext: Context, frame: Frame, opaque: Boolean, into: Bitmap?): Bitmap? {
        if (failed) return null
        if (!ensureContext(appContext)) {
            com.deepline.fishing.analytics.GameAnalytics.problem("render3d_offscreen", "no offscreen GL context")
            failed = true
            return null
        }
        val b = backend ?: return null
        val w = frame.widthPx
        val h = frame.heightPx
        ensureTarget(w, h)
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, fbo)
        b.clearColor = if (opaque) floatArrayOf(0f, 0f, 0f, 1f) else floatArrayOf(0f, 0f, 0f, 0f)
        b.onSurfaceChanged(null, w, h)
        b.submit(frame)
        b.onDrawFrame(null)
        if (b.hasFailed) return null
        val buf = pixels!!
        buf.clear()
        GLES30.glReadPixels(0, 0, w, h, GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, buf)
        // GL rows start at the bottom: flip them.
        val stride = w * 4
        if (row.size < stride) row = ByteArray(stride)
        val top = ByteArray(stride)
        for (y in 0 until h / 2) {
            buf.position(y * stride); buf.get(top, 0, stride)
            buf.position((h - 1 - y) * stride); buf.get(row, 0, stride)
            buf.position(y * stride); buf.put(row, 0, stride)
            buf.position((h - 1 - y) * stride); buf.put(top, 0, stride)
        }
        buf.position(0)
        val bitmap = into?.takeIf { it.width == w && it.height == h && it.isMutable } ?: Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        bitmap.copyPixelsFromBuffer(buf)
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
        return bitmap
    }

    private fun ensureContext(appContext: Context): Boolean {
        if (context != EGL14.EGL_NO_CONTEXT) return true
        display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        val version = IntArray(2)
        if (!EGL14.eglInitialize(display, version, 0, version, 1)) return false
        val attrs = intArrayOf(
            EGL14.EGL_RED_SIZE, 8, EGL14.EGL_GREEN_SIZE, 8, EGL14.EGL_BLUE_SIZE, 8, EGL14.EGL_ALPHA_SIZE, 8,
            EGL14.EGL_RENDERABLE_TYPE, 0x40, // EGL_OPENGL_ES3_BIT_KHR
            EGL14.EGL_SURFACE_TYPE, EGL14.EGL_PBUFFER_BIT,
            EGL14.EGL_NONE,
        )
        val configs = arrayOfNulls<EGLConfig>(1)
        val num = IntArray(1)
        if (!EGL14.eglChooseConfig(display, attrs, 0, configs, 0, 1, num, 0) || num[0] == 0) return false
        val config = configs[0]!!
        context = EGL14.eglCreateContext(display, config, EGL14.EGL_NO_CONTEXT, intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 3, EGL14.EGL_NONE), 0)
        if (context == EGL14.EGL_NO_CONTEXT) return false
        surface = EGL14.eglCreatePbufferSurface(display, config, intArrayOf(EGL14.EGL_WIDTH, 16, EGL14.EGL_HEIGHT, 16, EGL14.EGL_NONE), 0)
        if (!EGL14.eglMakeCurrent(display, surface, surface, context)) return false
        backend = GlBackend(appContext.assets, onError = { failed = true }).also { it.onSurfaceCreated(null, null) }
        return backend?.hasFailed == false
    }

    private fun ensureTarget(w: Int, h: Int) {
        if (fbo != 0 && fboW == w && fboH == h) return
        if (fbo != 0) {
            GLES30.glDeleteFramebuffers(1, intArrayOf(fbo), 0)
            GLES30.glDeleteRenderbuffers(2, intArrayOf(color, depth), 0)
        }
        val ids = IntArray(2)
        GLES30.glGenFramebuffers(1, ids, 0)
        fbo = ids[0]
        GLES30.glGenRenderbuffers(2, ids, 0)
        color = ids[0]
        depth = ids[1]
        GLES30.glBindRenderbuffer(GLES30.GL_RENDERBUFFER, color)
        GLES30.glRenderbufferStorage(GLES30.GL_RENDERBUFFER, GLES30.GL_RGBA8, w, h)
        GLES30.glBindRenderbuffer(GLES30.GL_RENDERBUFFER, depth)
        GLES30.glRenderbufferStorage(GLES30.GL_RENDERBUFFER, GLES30.GL_DEPTH_COMPONENT24, w, h)
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, fbo)
        GLES30.glFramebufferRenderbuffer(GLES30.GL_FRAMEBUFFER, GLES30.GL_COLOR_ATTACHMENT0, GLES30.GL_RENDERBUFFER, color)
        GLES30.glFramebufferRenderbuffer(GLES30.GL_FRAMEBUFFER, GLES30.GL_DEPTH_ATTACHMENT, GLES30.GL_RENDERBUFFER, depth)
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
        fboW = w
        fboH = h
        pixels = ByteBuffer.allocateDirect(w * h * 4).order(ByteOrder.nativeOrder())
    }
}
