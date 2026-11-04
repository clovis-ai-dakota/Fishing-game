package com.deepline.fishing.render3d

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
            sb.append("{\"program\":\"").append(item.program.name).append("\",\"mesh\":\"").append(key).append('"')
            sb.append(",\"uniforms\":").append(uniforms(item.uniforms))
            sb.append(",\"blend\":\"").append(item.blend.name).append('"')
            sb.append(",\"outline\":").append(item.outlinePx)
            item.outlineColor?.let { sb.append(",\"outlineColor\":").append(floats(it)) }
            sb.append(",\"cull\":").append(item.cullBack).append(",\"depthTest\":").append(item.depthTest).append(",\"depthWrite\":").append(item.depthWrite)
            sb.append('}')
        }
        sb.append("],\"meshes\":{")
        meshes.entries.forEachIndexed { i, (k, m) ->
            if (i > 0) sb.append(',')
            sb.append('"').append(k).append("\":{\"p\":").append(floats(m.positions)).append(",\"n\":").append(floats(m.normals))
                .append(",\"uv\":").append(floats(m.uvs)).append(",\"part\":").append(floats(m.parts))
                .append(",\"c\":").append(m.colors?.let { floats(it) } ?: "null")
                .append(",\"i\":[").append(m.indices.joinToString(",")).append("]}")
        }
        sb.append("},\"shaders\":{")
        val names = Program.entries.flatMap { listOf(it.vertex, it.fragment) }.distinct()
        names.forEachIndexed { i, n ->
            if (i > 0) sb.append(',')
            val src = ShaderSource.resolve(n) { File(shaderDir, it).readText() }
            sb.append('"').append(n).append("\":\"").append(escape(src)).append('"')
        }
        sb.append("},\"programs\":{")
        Program.entries.forEachIndexed { i, p ->
            if (i > 0) sb.append(',')
            sb.append('"').append(p.name).append("\":[\"").append(p.vertex).append("\",\"").append(p.fragment).append("\"]")
        }
        sb.append("}}")
        file.parentFile.mkdirs()
        file.writeText(sb.toString())
    }

    private fun uniforms(u: Map<String, FloatArray>) =
        u.entries.joinToString(",", "{", "}") { (k, v) -> "\"$k\":${floats(v)}" }

    private fun floats(a: FloatArray) = a.joinToString(",", "[", "]") { v ->
        if (v.isNaN() || v.isInfinite()) "0" else {
            val r = Math.round(v * 100000.0) / 100000.0
            if (r == Math.floor(r) && kotlin.math.abs(r) < 1e9) r.toLong().toString() else r.toString()
        }
    }

    private fun escape(s: String) = s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")
}
