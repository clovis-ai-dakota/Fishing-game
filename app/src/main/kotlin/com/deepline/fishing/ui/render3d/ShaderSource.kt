package com.deepline.fishing.ui.render3d

/**
 * Shader sources live in `assets/shaders`. GLSL has no includes, so `#include "file"` lines are
 * replaced by that file's text here (the app and the WebGL preview both use this).
 */
object ShaderSource {
    fun resolve(name: String, read: (String) -> String): String {
        val seen = HashSet<String>()
        fun expand(file: String): String {
            if (!seen.add(file)) return ""
            return read(file).lines().joinToString("\n") { line ->
                val m = Regex("^\\s*#include\\s+\"([^\"]+)\"").find(line)
                if (m != null) expand(m.groupValues[1]) else line
            }
        }
        return expand(name)
    }
}
