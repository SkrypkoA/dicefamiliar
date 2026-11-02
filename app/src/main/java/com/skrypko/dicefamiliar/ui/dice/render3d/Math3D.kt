package com.skrypko.dicefamiliar.ui.dice.render3d

import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** Minimal 3D vector; pure Kotlin so geometry is unit-testable. */
data class Vec3(val x: Double, val y: Double, val z: Double) {
    operator fun plus(o: Vec3) = Vec3(x + o.x, y + o.y, z + o.z)
    operator fun minus(o: Vec3) = Vec3(x - o.x, y - o.y, z - o.z)
    operator fun times(s: Double) = Vec3(x * s, y * s, z * s)
    operator fun unaryMinus() = Vec3(-x, -y, -z)
    infix fun dot(o: Vec3) = x * o.x + y * o.y + z * o.z
    infix fun cross(o: Vec3) = Vec3(y * o.z - z * o.y, z * o.x - x * o.z, x * o.y - y * o.x)
    val length: Double get() = sqrt(this dot this)
    fun normalized(): Vec3 = length.let { if (it == 0.0) this else this * (1.0 / it) }

    companion object {
        val ZERO = Vec3(0.0, 0.0, 0.0)
        val X = Vec3(1.0, 0.0, 0.0)
        val Y = Vec3(0.0, 1.0, 0.0)
        val Z = Vec3(0.0, 0.0, 1.0)
    }
}

/** Row-major 3x3 matrix used for rotations. */
class Mat3(private val m: DoubleArray) {
    init {
        require(m.size == 9)
    }

    operator fun times(v: Vec3) = Vec3(
        m[0] * v.x + m[1] * v.y + m[2] * v.z,
        m[3] * v.x + m[4] * v.y + m[5] * v.z,
        m[6] * v.x + m[7] * v.y + m[8] * v.z,
    )

    operator fun times(o: Mat3): Mat3 = Mat3(
        DoubleArray(9) { i ->
            val r = i / 3
            val c = i % 3
            m[r * 3] * o.m[c] + m[r * 3 + 1] * o.m[3 + c] + m[r * 3 + 2] * o.m[6 + c]
        },
    )

    fun transposed() = Mat3(doubleArrayOf(m[0], m[3], m[6], m[1], m[4], m[7], m[2], m[5], m[8]))

    companion object {
        val IDENTITY = Mat3(doubleArrayOf(1.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 1.0))

        fun rows(a: Vec3, b: Vec3, c: Vec3) = Mat3(doubleArrayOf(a.x, a.y, a.z, b.x, b.y, b.z, c.x, c.y, c.z))

        /** Rodrigues rotation of [angle] radians about [axis]. */
        fun rotation(axis: Vec3, angle: Double): Mat3 {
            val (x, y, z) = axis.normalized()
            val c = cos(angle)
            val s = sin(angle)
            val t = 1 - c
            return Mat3(
                doubleArrayOf(
                    t * x * x + c, t * x * y - s * z, t * x * z + s * y,
                    t * x * y + s * z, t * y * y + c, t * y * z - s * x,
                    t * x * z - s * y, t * y * z + s * x, t * z * z + c,
                ),
            )
        }
    }
}
