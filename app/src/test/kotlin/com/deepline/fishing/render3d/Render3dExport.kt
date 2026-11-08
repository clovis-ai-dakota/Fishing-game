package com.deepline.fishing.render3d

import com.deepline.fishing.ui.render3d.DrawItem
import com.deepline.fishing.ui.render3d.Frame
import com.deepline.fishing.ui.render3d.Meshes
import com.deepline.fishing.ui.render3d.MeshData
import com.deepline.fishing.ui.render3d.Program
import com.deepline.fishing.ui.render3d.ShaderSource
import java.io.File

/**
 * Writes a [Frame] as JSON (meshes, draw items, resolved shader sources) for the WebGL preview
 * in `tools/render3d-preview`, which draws it with the very same shaders.
 */
object Render3dExport {
    private val shaderDir = File("src/main/assets/shaders")

    fun write(frame: Frame, file: File) {
        val meshes = LinkedHashMap<String, MeshData>()
        val sb = StringBuilder()
        sb.append("{\"width\":").append(frame.widthPx).append(",\"height\":").append(frame.heightPx)
        sb.append(",\"globals\":").append(uniforms(frame.globals))
        sb.append(",\"items\":[")
        frame.items.forEachIndexed { i, item ->
            val key = item.meshKey ?: "dyn:$i"
            if (key !in meshes) meshes[key] = item.dynamicMesh ?: Meshes.get(key)
            if (i > 0) sb.append(',')
            item(sb, item, key)
        }
        sb.append("],\"meshes\":")
        meshes(sb, meshes)
        sb.append(",\"shaders\":").append(shaders()).append(",\"programs\":").append(programs())
        sb.append('}')
        file.parentFile.mkdirs()
        file.writeText(sb.toString())
    }

    internal fun item(sb: StringBuilder, item: DrawItem, key: String) {
        sb.append("{\"program\":\"").append(item.program.name).append("\",\"mesh\":\"").append(key).append('"')
        sb.append(",\"uniforms\":").append(uniforms(item.uniforms))
        sb.append(",\"blend\":\"").append(item.blend.name).append('"')
        sb.append(",\"outline\":").append(item.outlinePx)
        item.outlineColor?.let { sb.append(",\"outlineColor\":").append(floats(it)) }
        sb.append(",\"cull\":").append(item.cullBack).append(",\"depthTest\":").append(item.depthTest).append(",\"depthWrite\":").append(item.depthWrite)
        sb.append('}')
    }

    internal fun meshes(sb: StringBuilder, meshes: Map<String, MeshData>) {
        sb.append('{')
        meshes.entries.forEachIndexed { i, (k, m) ->
            if (i > 0) sb.append(',')
            sb.append('"').append(k).append("\":{\"p\":").append(floats(m.positions)).append(",\"n\":").append(floats(m.normals))
                .append(",\"uv\":").append(floats(m.uvs)).append(",\"part\":").append(floats(m.parts))
                .append(",\"c\":").append(m.colors?.let { floats(it) } ?: "null")
                .append(",\"i\":[").append(m.indices.joinToString(",")).append("]}")
        }
        sb.append('}')
    }

    /** Every program's shader sources, with `#include` resolved like the app's ShaderSource. */
    internal fun shaders(): String {
        val sb = StringBuilder("{")
        val names = Program.entries.flatMap { listOf(it.vertex, it.fragment) }.distinct()
        names.forEachIndexed { i, n ->
            if (i > 0) sb.append(',')
            val src = ShaderSource.resolve(n) { File(shaderDir, it).readText() }
            sb.append('"').append(n).append("\":\"").append(escape(src)).append('"')
        }
        return sb.append('}').toString()
    }

    internal fun programs(): String =
        Program.entries.joinToString(",", "{", "}") { p -> "\"${p.name}\":[\"${p.vertex}\",\"${p.fragment}\"]" }

    internal fun uniforms(u: Map<String, FloatArray>) =
        u.entries.joinToString(",", "{", "}") { (k, v) -> "\"$k\":${floats(v)}" }

    private fun floats(a: FloatArray) = a.joinToString(",", "[", "]") { v ->
        if (v.isNaN() || v.isInfinite()) "0" else {
            val r = Math.round(v * 100000.0) / 100000.0
            if (r == Math.floor(r) && kotlin.math.abs(r) < 1e9) r.toLong().toString() else r.toString()
        }
    }

    private fun escape(s: String) = s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")
}

/**
 * A sequence of frames for the promo video (`tools/promo-video`): the shaders, programs and
 * static meshes go once into `shared.json`; each frame file holds only its globals, items,
 * dynamic meshes and a little game state for the captions (`meta`). The WebGL preview draws them
 * with the same `drawFrame` as single frames.
 */
class Render3dSequence(private val dir: File) {
    private val staticMeshes = LinkedHashSet<String>()
    private var count = 0

    init {
        File(dir, "frames").mkdirs()
    }

    fun add(frame: Frame, meta: Map<String, Any?>) {
        val dynamic = LinkedHashMap<String, MeshData>()
        val sb = StringBuilder()
        sb.append("{\"width\":").append(frame.widthPx).append(",\"height\":").append(frame.heightPx)
        sb.append(",\"globals\":").append(Render3dExport.uniforms(frame.globals))
        sb.append(",\"items\":[")
        frame.items.forEachIndexed { i, item ->
            val key = item.meshKey ?: "dyn:$i"
            if (item.meshKey != null) staticMeshes += key else dynamic[key] = item.dynamicMesh!!
            if (i > 0) sb.append(',')
            Render3dExport.item(sb, item, key)
        }
        sb.append("],\"meshes\":")
        Render3dExport.meshes(sb, dynamic)
        sb.append(",\"meta\":").append(json(meta)).append('}')
        File(dir, "frames/%05d.json".format(count++)).writeText(sb.toString())
    }

    /** Writes shared.json; call once after the last frame. Returns the number of frames. */
    fun finish(): Int {
        val sb = StringBuilder("{\"meshes\":")
        Render3dExport.meshes(sb, staticMeshes.associateWith { Meshes.get(it) })
        sb.append(",\"shaders\":").append(Render3dExport.shaders()).append(",\"programs\":").append(Render3dExport.programs()).append('}')
        File(dir, "shared.json").writeText(sb.toString())
        return count
    }

    private fun json(v: Any?): String = when (v) {
        null -> "null"
        is String -> "\"" + v.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
        is Number, is Boolean -> v.toString()
        is Map<*, *> -> v.entries.joinToString(",", "{", "}") { (k, x) -> "\"$k\":${json(x)}" }
        is List<*> -> v.joinToString(",", "[", "]") { json(it) }
        else -> json(v.toString())
    }
}
