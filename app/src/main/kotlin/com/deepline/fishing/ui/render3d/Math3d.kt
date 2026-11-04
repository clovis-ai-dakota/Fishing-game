package com.deepline.fishing.ui.render3d

import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/** A 3D vector (meters in world space). */
data class Vec3(val x: Float, val y: Float, val z: Float) {
    operator fun plus(o: Vec3) = Vec3(x + o.x, y + o.y, z + o.z)
    operator fun minus(o: Vec3) = Vec3(x - o.x, y - o.y, z - o.z)
    operator fun times(s: Float) = Vec3(x * s, y * s, z * s)
    operator fun unaryMinus() = Vec3(-x, -y, -z)
    infix fun dot(o: Vec3) = x * o.x + y * o.y + z * o.z
    infix fun cross(o: Vec3) = Vec3(y * o.z - z * o.y, z * o.x - x * o.z, x * o.y - y * o.x)
    fun length() = sqrt(this dot this)
    fun normalized(): Vec3 {
        val l = length()
        return if (l < 1e-8f) Vec3(0f, 1f, 0f) else this * (1f / l)
    }

    companion object {
        val ZERO = Vec3(0f, 0f, 0f)
        val UP = Vec3(0f, 1f, 0f)
        fun lerp(a: Vec3, b: Vec3, t: Float) = a + (b - a) * t
    }
}

/**
 * 4x4 matrices stored column-major in a FloatArray of 16, the OpenGL convention, so they can be
 * handed straight to `glUniformMatrix4fv` (and WebGL, for the preview harness).
 */
object Mat4 {
    fun identity() = FloatArray(16).also { it[0] = 1f; it[5] = 1f; it[10] = 1f; it[15] = 1f }

    fun multiply(a: FloatArray, b: FloatArray): FloatArray {
        val r = FloatArray(16)
        for (c in 0 until 4) for (row in 0 until 4) {
            var s = 0f
            for (k in 0 until 4) s += a[k * 4 + row] * b[c * 4 + k]
            r[c * 4 + row] = s
        }
        return r
    }

    fun translation(x: Float, y: Float, z: Float) = identity().also { it[12] = x; it[13] = y; it[14] = z }

    fun scale(x: Float, y: Float = x, z: Float = x) = FloatArray(16).also { it[0] = x; it[5] = y; it[10] = z; it[15] = 1f }

    fun rotationX(a: Float) = identity().also {
        val c = cos(a); val s = sin(a)
        it[5] = c; it[6] = s; it[9] = -s; it[10] = c
    }

    fun rotationY(a: Float) = identity().also {
        val c = cos(a); val s = sin(a)
        it[0] = c; it[2] = -s; it[8] = s; it[10] = c
    }

    fun rotationZ(a: Float) = identity().also {
        val c = cos(a); val s = sin(a)
        it[0] = c; it[1] = s; it[4] = -s; it[5] = c
    }

    /** Model matrix: translate, then rotate Z, Y, X (in that order applied to the object), then scale. */
    fun trs(t: Vec3, rotX: Float = 0f, rotY: Float = 0f, rotZ: Float = 0f, scale: Vec3 = Vec3(1f, 1f, 1f)): FloatArray {
        var m = translation(t.x, t.y, t.z)
        if (rotZ != 0f) m = multiply(m, rotationZ(rotZ))
        if (rotY != 0f) m = multiply(m, rotationY(rotY))
        if (rotX != 0f) m = multiply(m, rotationX(rotX))
        return multiply(m, scale(scale.x, scale.y, scale.z))
    }

    fun perspective(fovYRadians: Float, aspect: Float, near: Float, far: Float): FloatArray {
        val f = 1f / tan(fovYRadians / 2f)
        val r = FloatArray(16)
        r[0] = f / aspect
        r[5] = f
        r[10] = (far + near) / (near - far)
        r[11] = -1f
        r[14] = 2f * far * near / (near - far)
        return r
    }

    fun lookAt(eye: Vec3, center: Vec3, up: Vec3): FloatArray {
        val f = (center - eye).normalized()
        val s = (f cross up).normalized()
        val u = s cross f
        val r = identity()
        r[0] = s.x; r[4] = s.y; r[8] = s.z
        r[1] = u.x; r[5] = u.y; r[9] = u.z
        r[2] = -f.x; r[6] = -f.y; r[10] = -f.z
        r[12] = -(s dot eye); r[13] = -(u dot eye); r[14] = f dot eye
        return r
    }

    fun transformPoint(m: FloatArray, p: Vec3): Vec3 {
        val x = m[0] * p.x + m[4] * p.y + m[8] * p.z + m[12]
        val y = m[1] * p.x + m[5] * p.y + m[9] * p.z + m[13]
        val z = m[2] * p.x + m[6] * p.y + m[10] * p.z + m[14]
        val w = m[3] * p.x + m[7] * p.y + m[11] * p.z + m[15]
        return if (w != 0f && w != 1f) Vec3(x / w, y / w, z / w) else Vec3(x, y, z)
    }

    /** The inverse-transpose of the upper 3x3 (for normals), column-major in 9 floats. */
    fun normalMatrix(m: FloatArray): FloatArray {
        val a = m[0]; val b = m[4]; val c = m[8]
        val d = m[1]; val e = m[5]; val f = m[9]
        val g = m[2]; val h = m[6]; val i = m[10]
        val det = a * (e * i - f * h) - b * (d * i - f * g) + c * (d * h - e * g)
        val inv = if (det == 0f) 0f else 1f / det
        // The inverse-transpose is the cofactor matrix divided by the determinant; written here
        // column-major (cofactor of row r, column c at index c * 3 + r).
        return floatArrayOf(
            (e * i - f * h) * inv, -(b * i - c * h) * inv, (b * f - c * e) * inv,
            -(d * i - f * g) * inv, (a * i - c * g) * inv, -(a * f - c * d) * inv,
            (d * h - e * g) * inv, -(a * h - b * g) * inv, (a * e - b * d) * inv,
        )
    }

    /** General 4x4 inverse (column-major). */
    fun invert(m: FloatArray): FloatArray {
        val inv = FloatArray(16)
        inv[0] = m[5] * m[10] * m[15] - m[5] * m[11] * m[14] - m[9] * m[6] * m[15] + m[9] * m[7] * m[14] + m[13] * m[6] * m[11] - m[13] * m[7] * m[10]
        inv[4] = -m[4] * m[10] * m[15] + m[4] * m[11] * m[14] + m[8] * m[6] * m[15] - m[8] * m[7] * m[14] - m[12] * m[6] * m[11] + m[12] * m[7] * m[10]
        inv[8] = m[4] * m[9] * m[15] - m[4] * m[11] * m[13] - m[8] * m[5] * m[15] + m[8] * m[7] * m[13] + m[12] * m[5] * m[11] - m[12] * m[7] * m[9]
        inv[12] = -m[4] * m[9] * m[14] + m[4] * m[10] * m[13] + m[8] * m[5] * m[14] - m[8] * m[6] * m[13] - m[12] * m[5] * m[10] + m[12] * m[6] * m[9]
        inv[1] = -m[1] * m[10] * m[15] + m[1] * m[11] * m[14] + m[9] * m[2] * m[15] - m[9] * m[3] * m[14] - m[13] * m[2] * m[11] + m[13] * m[3] * m[10]
        inv[5] = m[0] * m[10] * m[15] - m[0] * m[11] * m[14] - m[8] * m[2] * m[15] + m[8] * m[3] * m[14] + m[12] * m[2] * m[11] - m[12] * m[3] * m[10]
        inv[9] = -m[0] * m[9] * m[15] + m[0] * m[11] * m[13] + m[8] * m[1] * m[15] - m[8] * m[3] * m[13] - m[12] * m[1] * m[11] + m[12] * m[3] * m[9]
        inv[13] = m[0] * m[9] * m[14] - m[0] * m[10] * m[13] - m[8] * m[1] * m[14] + m[8] * m[2] * m[13] + m[12] * m[1] * m[10] - m[12] * m[2] * m[9]
        inv[2] = m[1] * m[6] * m[15] - m[1] * m[7] * m[14] - m[5] * m[2] * m[15] + m[5] * m[3] * m[14] + m[13] * m[2] * m[7] - m[13] * m[3] * m[6]
        inv[6] = -m[0] * m[6] * m[15] + m[0] * m[7] * m[14] + m[4] * m[2] * m[15] - m[4] * m[3] * m[14] - m[12] * m[2] * m[7] + m[12] * m[3] * m[6]
        inv[10] = m[0] * m[5] * m[15] - m[0] * m[7] * m[13] - m[4] * m[1] * m[15] + m[4] * m[3] * m[13] + m[12] * m[1] * m[7] - m[12] * m[3] * m[5]
        inv[14] = -m[0] * m[5] * m[14] + m[0] * m[6] * m[13] + m[4] * m[1] * m[14] - m[4] * m[2] * m[13] - m[12] * m[1] * m[6] + m[12] * m[2] * m[5]
        inv[3] = -m[1] * m[6] * m[11] + m[1] * m[7] * m[10] + m[5] * m[2] * m[11] - m[5] * m[3] * m[10] - m[9] * m[2] * m[7] + m[9] * m[3] * m[6]
        inv[7] = m[0] * m[6] * m[11] - m[0] * m[7] * m[10] - m[4] * m[2] * m[11] + m[4] * m[3] * m[10] + m[8] * m[2] * m[7] - m[8] * m[3] * m[6]
        inv[11] = -m[0] * m[5] * m[11] + m[0] * m[7] * m[9] + m[4] * m[1] * m[11] - m[4] * m[3] * m[9] - m[8] * m[1] * m[7] + m[8] * m[3] * m[5]
        inv[15] = m[0] * m[5] * m[10] - m[0] * m[6] * m[9] - m[4] * m[1] * m[10] + m[4] * m[2] * m[9] + m[8] * m[1] * m[6] - m[8] * m[2] * m[5]
        var det = m[0] * inv[0] + m[1] * inv[4] + m[2] * inv[8] + m[3] * inv[12]
        if (det == 0f) return identity()
        det = 1f / det
        for (k in 0 until 16) inv[k] *= det
        return inv
    }
}
