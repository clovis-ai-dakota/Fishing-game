package com.deepline.fishing.ui.render3d

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Triangle mesh data, ready to upload: per vertex a position, a normal, a uv, a "part" id (which
 * part of a model it belongs to, so one shader can paint eyes, fins and body differently) and an
 * optional RGBA colour.
 */
class MeshData(
    val positions: FloatArray,
    val normals: FloatArray,
    val uvs: FloatArray,
    val parts: FloatArray,
    val colors: FloatArray?,
    val indices: IntArray,
) {
    val vertexCount get() = positions.size / 3
    val triangleCount get() = indices.size / 3

    /** Axis-aligned bounds: (min, max). */
    fun bounds(): Pair<Vec3, Vec3> {
        var minX = Float.MAX_VALUE; var minY = Float.MAX_VALUE; var minZ = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE; var maxY = -Float.MAX_VALUE; var maxZ = -Float.MAX_VALUE
        for (i in 0 until vertexCount) {
            val x = positions[i * 3]; val y = positions[i * 3 + 1]; val z = positions[i * 3 + 2]
            if (x < minX) minX = x; if (y < minY) minY = y; if (z < minZ) minZ = z
            if (x > maxX) maxX = x; if (y > maxY) maxY = y; if (z > maxZ) maxZ = z
        }
        return Vec3(minX, minY, minZ) to Vec3(maxX, maxY, maxZ)
    }
}

/** Which part of a model a vertex belongs to (read by the shaders as `aPart`). */
object Part {
    const val BODY = 0f
    const val FIN = 1f
    const val EYE = 2f
    const val LURE = 3f
    const val TEETH = 4f
    const val PECTORAL = 5f
    const val TENTACLE = 6f
    const val TAIL = 7f
    const val METAL = 10f
    const val WORM = 11f
    const val WORM_TAIL = 12f
    const val RUNE = 13f
    const val WOOD = 20f
    const val ROCK = 21f
    const val JELLY = 22f
    const val JELLY_TENTACLE = 23f
    const val HULL = 30f
    const val TRIM = 31f
    const val CABIN = 32f
    const val WINDOW = 33f
    const val SAIL = 34f
    const val FLAG = 35f
    const val MAST = 36f
    const val EMBLEM = 38f
    const val KELP = 40f
    const val DECOR_ROCK = 41f
    const val CORAL = 42f
}

/** Builds meshes vertex by vertex, with helpers for the shapes the game needs. */
class MeshBuilder {
    // Growable primitive arrays: no boxing, so building particles every frame stays cheap.
    private var pos = FloatArray(64 * 3)
    private var nor = FloatArray(64 * 3)
    private var uv = FloatArray(64 * 2)
    private var part = FloatArray(64)
    private var col = FloatArray(64 * 4)
    private var idx = IntArray(256)
    private var vCount = 0
    private var iCount = 0
    private var hasColors = false

    val vertexCount get() = vCount

    private fun ensureVertex() {
        if (vCount < part.size) return
        val n = part.size * 2
        pos = pos.copyOf(n * 3); nor = nor.copyOf(n * 3); uv = uv.copyOf(n * 2); part = part.copyOf(n); col = col.copyOf(n * 4)
    }

    private fun ensureIndex(extra: Int) {
        if (iCount + extra <= idx.size) return
        idx = idx.copyOf(maxOf(idx.size * 2, iCount + extra))
    }

    fun vertex(p: Vec3, n: Vec3, u: Float, v: Float, partId: Float, color: FloatArray? = null): Int {
        ensureVertex()
        val i = vCount
        pos[i * 3] = p.x; pos[i * 3 + 1] = p.y; pos[i * 3 + 2] = p.z
        nor[i * 3] = n.x; nor[i * 3 + 1] = n.y; nor[i * 3 + 2] = n.z
        uv[i * 2] = u; uv[i * 2 + 1] = v
        part[i] = partId
        if (color != null) {
            hasColors = true
            col[i * 4] = color[0]; col[i * 4 + 1] = color[1]; col[i * 4 + 2] = color[2]; col[i * 4 + 3] = color[3]
        } else {
            col[i * 4] = 1f; col[i * 4 + 1] = 1f; col[i * 4 + 2] = 1f; col[i * 4 + 3] = 1f
        }
        vCount++
        return i
    }

    fun triangle(a: Int, b: Int, c: Int) {
        ensureIndex(3)
        idx[iCount++] = a; idx[iCount++] = b; idx[iCount++] = c
    }

    fun quad(a: Int, b: Int, c: Int, d: Int) {
        triangle(a, b, c)
        triangle(a, c, d)
    }

    /** Adds [m], transformed by the column-major [transform]. */
    fun append(m: MeshData, transform: FloatArray = Mat4.identity()) {
        val base = vertexCount
        val nm = Mat4.normalMatrix(transform)
        for (i in 0 until m.vertexCount) {
            val p = Mat4.transformPoint(transform, Vec3(m.positions[i * 3], m.positions[i * 3 + 1], m.positions[i * 3 + 2]))
            val nx = m.normals[i * 3]; val ny = m.normals[i * 3 + 1]; val nz = m.normals[i * 3 + 2]
            val n = Vec3(
                nm[0] * nx + nm[3] * ny + nm[6] * nz,
                nm[1] * nx + nm[4] * ny + nm[7] * nz,
                nm[2] * nx + nm[5] * ny + nm[8] * nz,
            ).normalized()
            val c = m.colors?.let { floatArrayOf(it[i * 4], it[i * 4 + 1], it[i * 4 + 2], it[i * 4 + 3]) }
            vertex(p, n, m.uvs[i * 2], m.uvs[i * 2 + 1], m.parts[i], c)
        }
        ensureIndex(m.indices.size)
        for (k in m.indices) idx[iCount++] = base + k
    }

    /** Replaces the normals of vertices [from] until the end by smooth normals from the faces. */
    fun smoothNormals(from: Int = 0, triangleFrom: Int = 0) {
        val n = FloatArray((vertexCount - from) * 3)
        var t = triangleFrom * 3
        while (t < iCount) {
            val a = idx[t]; val b = idx[t + 1]; val c = idx[t + 2]
            t += 3
            if (a < from || b < from || c < from) continue
            val pa = p(a); val pb = p(b); val pc = p(c)
            val fn = (pb - pa) cross (pc - pa)
            for (v in intArrayOf(a, b, c)) {
                val o = (v - from) * 3
                n[o] += fn.x; n[o + 1] += fn.y; n[o + 2] += fn.z
            }
        }
        for (v in from until vertexCount) {
            val o = (v - from) * 3
            val nn = Vec3(n[o], n[o + 1], n[o + 2]).normalized()
            nor[v * 3] = nn.x; nor[v * 3 + 1] = nn.y; nor[v * 3 + 2] = nn.z
        }
    }

    val triangleCount get() = iCount / 3

    private fun p(i: Int) = Vec3(pos[i * 3], pos[i * 3 + 1], pos[i * 3 + 2])

    fun build() = MeshData(
        pos.copyOf(vCount * 3),
        nor.copyOf(vCount * 3),
        uv.copyOf(vCount * 2),
        part.copyOf(vCount),
        if (hasColors) col.copyOf(vCount * 4) else null,
        idx.copyOf(iCount),
    )

    // ---- Shapes ----------------------------------------------------------------------------

    /**
     * A body lofted along x: at each station, an ellipse in (y, z) centred at `centerY`, with
     * `top`/`bottom` half-heights above/below it and `halfWidth` in z. u runs along the stations
     * (0..1), v around the ring.
     */
    fun loft(stations: List<LoftStation>, ringSides: Int, partId: Float, capEnds: Boolean = true) {
        val first = vertexCount
        val firstTri = triangleCount
        val rings = stations.size
        for ((si, s) in stations.withIndex()) {
            val u = si / (rings - 1f)
            for (k in 0..ringSides) {
                val a = (k / ringSides.toFloat()) * 2f * PI.toFloat()
                val sy = sin(a)
                val cy = cos(a)
                val y = s.centerY + if (sy >= 0f) sy * s.top else sy * s.bottom
                val z = cy * s.halfWidth
                vertex(Vec3(s.x, y, z), Vec3(0f, sy, cy), u, k / ringSides.toFloat(), partId)
            }
        }
        val stride = ringSides + 1
        for (si in 0 until rings - 1) for (k in 0 until ringSides) {
            val a = first + si * stride + k
            val b = a + stride
            // Counter-clockwise seen from outside (stations ordered by increasing x).
            quad(a, b, b + 1, a + 1)
        }
        smoothNormals(first, firstTri)
        if (capEnds) {
            capRing(first, ringSides, stations.first(), outwardX = -1f, partId)
            capRing(first + (rings - 1) * stride, ringSides, stations.last(), outwardX = 1f, partId)
        }
    }

    private fun capRing(ringStart: Int, sides: Int, s: LoftStation, outwardX: Float, partId: Float) {
        val center = vertex(Vec3(s.x, s.centerY, 0f), Vec3(outwardX, 0f, 0f), if (outwardX > 0) 1f else 0f, 0.5f, partId)
        val ring = (0..sides).map { k ->
            val i = ringStart + k
            vertex(p(i), Vec3(outwardX, 0f, 0f), if (outwardX > 0) 1f else 0f, k / sides.toFloat(), partId)
        }
        for (k in 0 until sides) {
            if (outwardX > 0) triangle(center, ring[k + 1], ring[k]) else triangle(center, ring[k], ring[k + 1])
        }
    }

    fun sphere(center: Vec3, radius: Float, partId: Float, lat: Int = 10, lon: Int = 16, scale: Vec3 = Vec3(1f, 1f, 1f)) {
        val first = vertexCount
        for (i in 0..lat) {
            val th = i / lat.toFloat() * PI.toFloat()
            for (j in 0..lon) {
                val ph = j / lon.toFloat() * 2f * PI.toFloat()
                val n = Vec3(sin(th) * cos(ph), cos(th), sin(th) * sin(ph))
                val p = Vec3(center.x + n.x * radius * scale.x, center.y + n.y * radius * scale.y, center.z + n.z * radius * scale.z)
                vertex(p, Vec3(n.x / scale.x, n.y / scale.y, n.z / scale.z).normalized(), j / lon.toFloat(), i / lat.toFloat(), partId)
            }
        }
        val stride = lon + 1
        for (i in 0 until lat) for (j in 0 until lon) {
            val a = first + i * stride + j
            val b = a + stride
            quad(a, a + 1, b + 1, b)
        }
    }

    /**
     * A tube swept along [path] (at least 2 points), radius per point from [radius], with
     * rotation-minimising frames. u runs along the path, v around.
     */
    fun tube(path: List<Vec3>, radius: (Float) -> Float, sides: Int, partId: Float, cap: Boolean = true, flattenZ: Float = 1f) {
        if (path.size < 2) return
        val first = vertexCount
        var tangent = (path[1] - path[0]).normalized()
        var normal = (if (abs(tangent.z) < 0.9f) tangent cross Vec3(0f, 0f, 1f) else tangent cross Vec3(1f, 0f, 0f)).normalized()
        val rings = path.size
        for (i in 0 until rings) {
            val t = when (i) {
                0 -> (path[1] - path[0]).normalized()
                rings - 1 -> (path[i] - path[i - 1]).normalized()
                else -> (path[i + 1] - path[i - 1]).normalized()
            }
            // Carry the previous normal onto the new tangent (keeps the rings from twisting).
            val projected = normal - t * (normal dot t)
            if (projected.length() > 1e-6f) normal = projected.normalized()
            tangent = t
            val binormal = (t cross normal).normalized()
            val u = i / (rings - 1f)
            val r = radius(u)
            for (k in 0..sides) {
                val a = k / sides.toFloat() * 2f * PI.toFloat()
                val dir = normal * cos(a) + binormal * sin(a)
                val d = Vec3(dir.x, dir.y, dir.z * flattenZ)
                vertex(path[i] + d * r, dir, u, k / sides.toFloat(), partId)
            }
        }
        val stride = sides + 1
        for (i in 0 until rings - 1) for (k in 0 until sides) {
            val a = first + i * stride + k
            val b = a + stride
            quad(a, a + 1, b + 1, b)
        }
        if (cap) {
            sphere(path.last(), radius(1f) * 0.98f, partId, lat = 4, lon = sides)
            sphere(path.first(), radius(0f) * 0.98f, partId, lat = 4, lon = sides)
        }
    }

    /**
     * A flat shape (outline in the XY plane) extruded to [thickness] in z, centred on z = [z].
     * The outline must be a simple polygon; it is triangulated by ear clipping.
     */
    fun extrude(outline: List<Pair<Float, Float>>, thickness: Float, partId: Float, z: Float = 0f, uvFrom: Pair<Float, Float>? = null) {
        val pts = cleanPolygon(outline)
        if (pts.size < 3) return
        val tris = triangulate(pts)
        val half = thickness / 2f
        val (minX, maxX) = pts.minOf { it.first } to pts.maxOf { it.first }
        val (minY, maxY) = pts.minOf { it.second } to pts.maxOf { it.second }
        fun uvOf(p: Pair<Float, Float>) = (p.first - minX) / max(1e-6f, maxX - minX) to (p.second - minY) / max(1e-6f, maxY - minY)
        for (side in listOf(1f, -1f)) {
            val start = vertexCount
            for (p in pts) {
                val (u, v) = uvOf(p)
                vertex(Vec3(p.first, p.second, z + side * half), Vec3(0f, 0f, side), u, v, partId)
            }
            for ((a, b, c) in tris) {
                if (side > 0) triangle(start + a, start + b, start + c) else triangle(start + a, start + c, start + b)
            }
        }
        // Side walls.
        val ccw = signedArea(pts) > 0f
        for (i in pts.indices) {
            val p0 = pts[i]; val p1 = pts[(i + 1) % pts.size]
            val ex = p1.first - p0.first; val ey = p1.second - p0.second
            var n = Vec3(ey, -ex, 0f).normalized()
            if (!ccw) n = -n
            val (u0, v0) = uvOf(p0); val (u1, v1) = uvOf(p1)
            val a = vertex(Vec3(p0.first, p0.second, z + half), n, u0, v0, partId)
            val b = vertex(Vec3(p1.first, p1.second, z + half), n, u1, v1, partId)
            val c = vertex(Vec3(p1.first, p1.second, z - half), n, u1, v1, partId)
            val d = vertex(Vec3(p0.first, p0.second, z - half), n, u0, v0, partId)
            if (ccw) quad(a, d, c, b) else quad(a, b, c, d)
        }
    }

    /** A surface of revolution around the y axis: [profile] is (radius, y) from top to bottom. */
    fun lathe(profile: List<Pair<Float, Float>>, sides: Int, partId: Float, center: Vec3 = Vec3.ZERO) {
        val first = vertexCount
        val firstTri = triangleCount
        for ((i, pr) in profile.withIndex()) {
            for (k in 0..sides) {
                val a = k / sides.toFloat() * 2f * PI.toFloat()
                vertex(Vec3(center.x + cos(a) * pr.first, center.y + pr.second, center.z + sin(a) * pr.first), Vec3.UP, k / sides.toFloat(), i / (profile.size - 1f), partId)
            }
        }
        val stride = sides + 1
        for (i in 0 until profile.size - 1) for (k in 0 until sides) {
            val a = first + i * stride + k
            val b = a + stride
            quad(a, a + 1, b + 1, b)
        }
        smoothNormals(first, firstTri)
    }

    fun box(center: Vec3, size: Vec3, partId: Float) {
        val h = size * 0.5f
        val faces = listOf(
            Vec3(1f, 0f, 0f), Vec3(-1f, 0f, 0f), Vec3(0f, 1f, 0f), Vec3(0f, -1f, 0f), Vec3(0f, 0f, 1f), Vec3(0f, 0f, -1f),
        )
        for (n in faces) {
            val u = if (abs(n.y) > 0.5f) Vec3(1f, 0f, 0f) else Vec3(0f, 1f, 0f)
            val v = n cross u
            val corners = listOf(-1f to -1f, 1f to -1f, 1f to 1f, -1f to 1f).map { (a, b) ->
                val p = n + u * a + v * b
                Vec3(center.x + p.x * h.x, center.y + p.y * h.y, center.z + p.z * h.z)
            }
            val i = corners.mapIndexed { k, c -> vertex(c, n, if (k == 1 || k == 2) 1f else 0f, if (k >= 2) 1f else 0f, partId) }
            quad(i[0], i[1], i[2], i[3])
        }
    }

    companion object {
        private fun signedArea(p: List<Pair<Float, Float>>): Float {
            var a = 0f
            for (i in p.indices) {
                val (x0, y0) = p[i]; val (x1, y1) = p[(i + 1) % p.size]
                a += x0 * y1 - x1 * y0
            }
            return a / 2f
        }

        private fun cleanPolygon(p: List<Pair<Float, Float>>): List<Pair<Float, Float>> {
            val out = ArrayList<Pair<Float, Float>>()
            for (q in p) {
                val last = out.lastOrNull()
                if (last == null || abs(last.first - q.first) + abs(last.second - q.second) > 1e-5f) out += q
            }
            if (out.size > 1 && abs(out.first().first - out.last().first) + abs(out.first().second - out.last().second) < 1e-5f) out.removeAt(out.size - 1)
            return out
        }

        /** Ear clipping; returns triangles as index triples, counter-clockwise. */
        fun triangulate(poly: List<Pair<Float, Float>>): List<Triple<Int, Int, Int>> {
            val n = poly.size
            val idx = (0 until n).toMutableList()
            if (signedArea(poly) < 0) idx.reverse()
            val out = ArrayList<Triple<Int, Int, Int>>()
            fun cross(o: Pair<Float, Float>, a: Pair<Float, Float>, b: Pair<Float, Float>) =
                (a.first - o.first) * (b.second - o.second) - (a.second - o.second) * (b.first - o.first)
            fun inside(p: Pair<Float, Float>, a: Pair<Float, Float>, b: Pair<Float, Float>, c: Pair<Float, Float>) =
                cross(a, b, p) >= 0 && cross(b, c, p) >= 0 && cross(c, a, p) >= 0
            var guard = 0
            while (idx.size > 3 && guard < n * n) {
                guard++
                var clipped = false
                for (i in idx.indices) {
                    val ia = idx[(i + idx.size - 1) % idx.size]; val ib = idx[i]; val ic = idx[(i + 1) % idx.size]
                    val a = poly[ia]; val b = poly[ib]; val c = poly[ic]
                    if (cross(a, b, c) <= 1e-9f) continue
                    if (idx.any { it != ia && it != ib && it != ic && inside(poly[it], a, b, c) }) continue
                    out += Triple(ia, ib, ic)
                    idx.removeAt(i)
                    clipped = true
                    break
                }
                if (!clipped) break // degenerate: fan the rest
            }
            if (idx.size == 3) out += Triple(idx[0], idx[1], idx[2])
            else for (k in 1 until idx.size - 1) out += Triple(idx[0], idx[k], idx[k + 1])
            return out
        }
    }
}

data class LoftStation(val x: Float, val centerY: Float, val top: Float, val bottom: Float, val halfWidth: Float)

/** Points along a cubic Bézier. */
fun cubic(p0: Pair<Float, Float>, p1: Pair<Float, Float>, p2: Pair<Float, Float>, p3: Pair<Float, Float>, steps: Int): List<Pair<Float, Float>> =
    (0..steps).map { i ->
        val t = i / steps.toFloat()
        val u = 1 - t
        val a = u * u * u; val b = 3 * u * u * t; val c = 3 * u * t * t; val d = t * t * t
        (a * p0.first + b * p1.first + c * p2.first + d * p3.first) to (a * p0.second + b * p1.second + c * p2.second + d * p3.second)
    }

fun quadratic(p0: Pair<Float, Float>, p1: Pair<Float, Float>, p2: Pair<Float, Float>, steps: Int): List<Pair<Float, Float>> =
    (0..steps).map { i ->
        val t = i / steps.toFloat()
        val u = 1 - t
        (u * u * p0.first + 2 * u * t * p1.first + t * t * p2.first) to (u * u * p0.second + 2 * u * t * p1.second + t * t * p2.second)
    }

internal fun smoothstep(e0: Float, e1: Float, x: Float): Float {
    val t = ((x - e0) / (e1 - e0)).coerceIn(0f, 1f)
    return t * t * (3 - 2 * t)
}

internal fun hash01(i: Int, k: Int, salt: Int): Float {
    var x = i * 374761393 + k * 668265263 + salt * 1274126177
    x = (x xor (x ushr 13)) * 1274126177
    x = x xor (x ushr 16)
    return (x and 0xFFFFFF) / 16777215f
}

internal fun sqrtf(x: Float) = sqrt(x)
