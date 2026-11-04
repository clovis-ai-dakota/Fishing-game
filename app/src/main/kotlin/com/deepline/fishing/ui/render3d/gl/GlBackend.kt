package com.deepline.fishing.ui.render3d.gl

import android.content.res.AssetManager
import android.opengl.GLES30
import android.opengl.GLSurfaceView
import android.util.Log
import com.deepline.fishing.ui.render3d.Blend
import com.deepline.fishing.ui.render3d.DrawItem
import com.deepline.fishing.ui.render3d.Frame
import com.deepline.fishing.ui.render3d.MeshData
import com.deepline.fishing.ui.render3d.Meshes
import com.deepline.fishing.ui.render3d.Program
import com.deepline.fishing.ui.render3d.ShaderSource
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.IntBuffer
import java.util.concurrent.atomic.AtomicReference
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/**
 * Draws [Frame]s with OpenGL ES 3.0. Frames are built on the main thread and handed over with
 * [submit]; this renderer only executes them (the same rules as tools/render3d-preview, which
 * checks the shaders with WebGL2). Any shader failure is reported once through [onError] so the
 * app can fall back to the 2D renderer.
 */
class GlBackend(
    private val assets: AssetManager,
    private val onError: (String) -> Unit,
    /** Called (on the GL thread) after each frame drawn from a submitted [Frame]. */
    private val onFrameDrawn: () -> Unit = {},
) : GLSurfaceView.Renderer {
    private val latest = AtomicReference<Frame?>(null)
    private val programs = HashMap<Program, GlProgram>()
    private val meshes = HashMap<String, GlMesh>()
    private val dynamic = ArrayList<GlMesh>()
    private var failed = false
    private var width = 1
    private var height = 1

    /** Opaque black for the game view; transparent for portraits drawn over a card. */
    var clearColor = floatArrayOf(0f, 0f, 0f, 1f)

    /** True once a shader or draw failed (the app then uses 2D). */
    val hasFailed get() = failed

    fun submit(frame: Frame) = latest.set(frame)

    override fun onSurfaceCreated(unused: GL10?, config: EGLConfig?) {
        // A new context: everything on the GPU is gone.
        programs.clear()
        meshes.clear()
        dynamic.clear()
        try {
            for (p in Program.entries) programs[p] = GlProgram.build(p, assets)
        } catch (e: Exception) {
            fail("Shaders: ${e.message}")
        }
    }

    override fun onSurfaceChanged(unused: GL10?, w: Int, h: Int) {
        width = w
        height = h
        GLES30.glViewport(0, 0, w, h)
    }

    override fun onDrawFrame(unused: GL10?) {
        GLES30.glClearColor(clearColor[0], clearColor[1], clearColor[2], clearColor[3])
        GLES30.glClearDepthf(1f)
        GLES30.glDepthMask(true)
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT or GLES30.GL_DEPTH_BUFFER_BIT)
        if (failed) return
        val frame = latest.get() ?: return
        try {
            draw(frame)
            if (!failed) onFrameDrawn()
        } catch (e: Exception) {
            fail("Draw: ${e.message}")
        }
    }

    private fun fail(message: String) {
        if (failed) return
        failed = true
        Log.e("DeepLine3D", message)
        onError(message)
    }

    private fun draw(frame: Frame) {
        var dynamicIndex = 0
        // The frame was laid out for its own size; the viewport follows the surface.
        val scaleOutline = width / 540f
        for (item in frame.items) {
            val mesh = if (item.dynamicMesh != null) {
                while (dynamic.size <= dynamicIndex) dynamic += GlMesh()
                dynamic[dynamicIndex++].also { it.upload(item.dynamicMesh, GLES30.GL_STREAM_DRAW) }
            } else {
                val key = item.meshKey ?: continue
                meshes.getOrPut(key) { GlMesh().also { it.upload(Meshes.get(key), GLES30.GL_STATIC_DRAW) } }
            }
            if (item.outlinePx > 0f) drawPass(programs[Program.OUTLINE] ?: return, item, mesh, frame, outline = true, outlineScale = scaleOutline)
            drawPass(programs[item.program] ?: return, item, mesh, frame, outline = false, outlineScale = scaleOutline)
        }
        GLES30.glBindVertexArray(0)
        val err = GLES30.glGetError()
        if (err != GLES30.GL_NO_ERROR) fail("GL error $err")
    }

    private fun drawPass(p: GlProgram, item: DrawItem, mesh: GlMesh, frame: Frame, outline: Boolean, outlineScale: Float) {
        GLES30.glUseProgram(p.id)
        for ((k, v) in DEFAULTS) p.set(k, v)
        for ((k, v) in frame.globals) p.set(k, v)
        for ((k, v) in item.uniforms) p.set(k, v)
        if (outline) {
            p.set("uOutlineWidth", floatArrayOf(item.outlinePx * outlineScale))
            p.set("uOutlineColor", item.outlineColor ?: INK)
            GLES30.glEnable(GLES30.GL_CULL_FACE)
            GLES30.glCullFace(GLES30.GL_FRONT)
            // Keep the ink behind the surface it outlines.
            GLES30.glEnable(GLES30.GL_POLYGON_OFFSET_FILL)
            GLES30.glPolygonOffset(1f, 2f)
        } else if (item.cullBack) {
            GLES30.glEnable(GLES30.GL_CULL_FACE)
            GLES30.glCullFace(GLES30.GL_BACK)
        } else {
            GLES30.glDisable(GLES30.GL_CULL_FACE)
        }
        when (item.blend) {
            Blend.OPAQUE -> GLES30.glDisable(GLES30.GL_BLEND)
            // The frame stays opaque: blending never changes the destination alpha.
            Blend.ALPHA -> {
                GLES30.glEnable(GLES30.GL_BLEND)
                GLES30.glBlendFuncSeparate(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE_MINUS_SRC_ALPHA, GLES30.GL_ZERO, GLES30.GL_ONE)
            }
            Blend.ADD -> {
                GLES30.glEnable(GLES30.GL_BLEND)
                GLES30.glBlendFuncSeparate(GLES30.GL_ONE, GLES30.GL_ONE, GLES30.GL_ZERO, GLES30.GL_ONE)
            }
        }
        if (!outline) GLES30.glDisable(GLES30.GL_POLYGON_OFFSET_FILL)
        if (item.depthTest) GLES30.glEnable(GLES30.GL_DEPTH_TEST) else GLES30.glDisable(GLES30.GL_DEPTH_TEST)
        GLES30.glDepthFunc(GLES30.GL_LEQUAL)
        GLES30.glDepthMask(item.depthWrite)
        GLES30.glBindVertexArray(mesh.vao)
        if (!mesh.hasColors) GLES30.glVertexAttrib4f(4, 1f, 1f, 1f, 1f)
        GLES30.glDrawElements(GLES30.GL_TRIANGLES, mesh.count, GLES30.GL_UNSIGNED_INT, 0)
    }

    companion object {
        private val INK = floatArrayOf(0.05f, 0.07f, 0.13f)

        /** Per-item uniforms reset before every draw (GL keeps uniform values per program). */
        private val DEFAULTS = mapOf(
            "uOpacity" to floatArrayOf(1f),
            "uFlags" to floatArrayOf(0f, 0f, 0f, 0f),
            "uSwim" to floatArrayOf(0f, 0f, 0f, 0f),
            "uFish" to floatArrayOf(0f, 0f, 0f, 0f),
            "uBase" to floatArrayOf(0.5f, 0.5f, 0.5f),
            "uAccent" to floatArrayOf(0.5f, 0.5f, 0.5f),
            "uExtra" to floatArrayOf(0.5f, 0.5f, 0.5f),
            "uExtra2" to floatArrayOf(0.9f, 0.9f, 0.85f),
            "uBoatMode" to floatArrayOf(0f, 0f, 0f, 0f),
            "uMaterial" to floatArrayOf(0f),
            "uMode" to floatArrayOf(0f),
            "uAdditive" to floatArrayOf(0f),
        )
    }
}

private class GlProgram(val id: Int) {
    private val locations = HashMap<String, Int>()

    fun set(name: String, v: FloatArray) {
        val loc = locations.getOrPut(name) { GLES30.glGetUniformLocation(id, name) }
        if (loc < 0) return
        when (v.size) {
            1 -> GLES30.glUniform1f(loc, v[0])
            2 -> GLES30.glUniform2fv(loc, 1, v, 0)
            3 -> GLES30.glUniform3fv(loc, 1, v, 0)
            4 -> GLES30.glUniform4fv(loc, 1, v, 0)
            9 -> GLES30.glUniformMatrix3fv(loc, 1, false, v, 0)
            16 -> GLES30.glUniformMatrix4fv(loc, 1, false, v, 0)
        }
    }

    companion object {
        fun build(p: Program, assets: AssetManager): GlProgram {
            fun read(name: String) = assets.open("shaders/$name").bufferedReader().use { it.readText() }
            val vs = compile(GLES30.GL_VERTEX_SHADER, ShaderSource.resolve(p.vertex, ::read), p.vertex)
            val fs = compile(GLES30.GL_FRAGMENT_SHADER, ShaderSource.resolve(p.fragment, ::read), p.fragment)
            val id = GLES30.glCreateProgram()
            GLES30.glAttachShader(id, vs)
            GLES30.glAttachShader(id, fs)
            GLES30.glLinkProgram(id)
            val ok = IntArray(1)
            GLES30.glGetProgramiv(id, GLES30.GL_LINK_STATUS, ok, 0)
            if (ok[0] == 0) error("${p.name} link: ${GLES30.glGetProgramInfoLog(id)}")
            GLES30.glDeleteShader(vs)
            GLES30.glDeleteShader(fs)
            return GlProgram(id)
        }

        private fun compile(type: Int, src: String, name: String): Int {
            val s = GLES30.glCreateShader(type)
            GLES30.glShaderSource(s, src)
            GLES30.glCompileShader(s)
            val ok = IntArray(1)
            GLES30.glGetShaderiv(s, GLES30.GL_COMPILE_STATUS, ok, 0)
            if (ok[0] == 0) error("$name: ${GLES30.glGetShaderInfoLog(s)}")
            return s
        }
    }
}

/** A mesh on the GPU: one VAO with a buffer per attribute and an index buffer. */
private class GlMesh {
    var vao = 0
    var count = 0
    var hasColors = false
    private val buffers = IntArray(6)
    private var created = false

    fun upload(m: MeshData, usage: Int) {
        if (!created) {
            val v = IntArray(1)
            GLES30.glGenVertexArrays(1, v, 0)
            vao = v[0]
            GLES30.glGenBuffers(6, buffers, 0)
            created = true
        }
        GLES30.glBindVertexArray(vao)
        attribute(0, buffers[0], m.positions, 3, usage)
        attribute(1, buffers[1], m.normals, 3, usage)
        attribute(2, buffers[2], m.uvs, 2, usage)
        attribute(3, buffers[3], m.parts, 1, usage)
        hasColors = m.colors != null
        if (m.colors != null) attribute(4, buffers[4], m.colors, 4, usage) else GLES30.glDisableVertexAttribArray(4)
        GLES30.glBindBuffer(GLES30.GL_ELEMENT_ARRAY_BUFFER, buffers[5])
        GLES30.glBufferData(GLES30.GL_ELEMENT_ARRAY_BUFFER, m.indices.size * 4, ints(m.indices), usage)
        count = m.indices.size
        GLES30.glBindVertexArray(0)
    }

    private fun attribute(location: Int, buffer: Int, data: FloatArray, size: Int, usage: Int) {
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, buffer)
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, data.size * 4, floats(data), usage)
        GLES30.glEnableVertexAttribArray(location)
        GLES30.glVertexAttribPointer(location, size, GLES30.GL_FLOAT, false, 0, 0)
    }

    // Upload buffers are reused (dynamic meshes are re-uploaded every frame).
    private var floatScratch: FloatBuffer? = null
    private var intScratch: IntBuffer? = null

    private fun floats(a: FloatArray): FloatBuffer {
        var buf = floatScratch
        if (buf == null || buf.capacity() < a.size) {
            buf = ByteBuffer.allocateDirect(a.size * 4 * 3 / 2 + 64).order(ByteOrder.nativeOrder()).asFloatBuffer()
            floatScratch = buf
        }
        buf!!.clear()
        buf.put(a)
        buf.position(0)
        return buf
    }

    private fun ints(a: IntArray): IntBuffer {
        var buf = intScratch
        if (buf == null || buf.capacity() < a.size) {
            buf = ByteBuffer.allocateDirect(a.size * 4 * 3 / 2 + 64).order(ByteOrder.nativeOrder()).asIntBuffer()
            intScratch = buf
        }
        buf!!.clear()
        buf.put(a)
        buf.position(0)
        return buf
    }
}
