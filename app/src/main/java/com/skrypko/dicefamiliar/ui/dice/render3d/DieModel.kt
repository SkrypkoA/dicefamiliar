package com.skrypko.dicefamiliar.ui.dice.render3d

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * A die as a convex mesh plus the number printed on each face.
 * Rendering helpers only: values always come from the roll result.
 */
class DieModel private constructor(
    val sides: Int,
    val mesh: ConvexMesh,
    /** Value printed on each face (index-aligned with mesh.faces); 0 means blank. */
    val faceValues: List<Int>,
) {
    /** Custom dN dice are drawn as spheres; the mesh only places the rolled number. */
    val isSphere: Boolean get() = sides == 0

    /** Direction of each face's "up" in object space, used to keep numbers upright at rest. */
    val faceUp: List<Vec3> = mesh.faces.map { face -> upOf(face) }

    /**
     * Face that shows [value] when the die comes to rest. For percentile kinds [value] is the
     * digit 0..9 (face value 10 carries digit 0).
     */
    fun restFace(value: Int, kind: DieKind = DieKind.STANDARD): Int = when {
        kind != DieKind.STANDARD -> faceValues.indexOfFirst { it % 10 == value }.coerceAtLeast(0)
        sides == 100 -> faceValues.indexOf((value - 1) / 10 + 1).coerceAtLeast(0)
        sides in STANDARD -> faceValues.indexOf(value).coerceAtLeast(0)
        else -> 0
    }

    /** Text printed on [face]; on custom dice only the resting face shows the actual [value]. */
    fun label(face: Int, value: Int, restFace: Int, kind: DieKind = DieKind.STANDARD): String = when {
        kind == DieKind.PERCENTILE_TENS -> Percentile.tensLabel(faceValues[face] % 10)
        kind == DieKind.PERCENTILE_UNITS -> Percentile.unitsLabel(faceValues[face] % 10)
        sides == 10 -> (faceValues[face] % 10).toString() // real d10s print 0 for 10
        face == restFace -> value.toString()
        sides == 100 -> Percentile.tensLabel(faceValues[face] % 10)
        sides in STANDARD -> faceValues[face].toString()
        else -> ""
    }

    /** Rotation that turns [face] toward the viewer (+z) with its up direction pointing to screen-up (+y). */
    fun restRotation(face: Int): Mat3 {
        val n = mesh.faces[face].normal
        val up = faceUp[face]
        val right = up cross n
        return Mat3.rows(right, up, n)
    }

    private fun upOf(face: MeshFace): Vec3 {
        val pts = face.indices.map { mesh.vertices[it] }
        val c = face.centroid
        val raw = if (sides == 6) {
            // Square faces: number parallel to an edge.
            (pts[0] + pts[1]) * 0.5 - c
        } else {
            // Point to the farthest vertex (the kite apex on d10); first wins ties.
            pts.maxBy { (it - c).length - 1e-9 * pts.indexOf(it) } - c
        }
        // Remove any normal component, so up lies in the face plane.
        return (raw - face.normal * (raw dot face.normal)).normalized()
    }

    companion object {
        val STANDARD = setOf(4, 6, 8, 10, 12, 20, 100)
        private val PHI = (1 + sqrt(5.0)) / 2

        private val cache = HashMap<Int, DieModel>()

        fun forSides(sides: Int): DieModel = synchronized(cache) {
            val key = if (sides in STANDARD) sides else 0
            cache.getOrPut(key) { build(key) }
        }

        private fun build(sides: Int): DieModel {
            val mesh = ConvexMesh.fromVertices(normalize(vertices(sides)))
            val faceCount = mesh.faces.size
            val values = when (sides) {
                4 -> List(faceCount) { it + 1 }
                0 -> List(faceCount) { 0 }
                // d10 faces read 0..9 (value 10 = "0"); opposite digits sum to 9 like real d10s.
                10, 100 -> oppositeValues(mesh, faceCount, pairSum = 9, digitOf = { it % 10 })
                else -> oppositeValues(mesh, faceCount)
            }
            return DieModel(if (sides == 0) 0 else sides, mesh, values)
        }

        private fun vertices(sides: Int): List<Vec3> = when (sides) {
            4 -> listOf(Vec3(1.0, 1.0, 1.0), Vec3(1.0, -1.0, -1.0), Vec3(-1.0, 1.0, -1.0), Vec3(-1.0, -1.0, 1.0))
            6 -> signs3().map { (a, b, c) -> Vec3(a, b, c) }
            8 -> listOf(Vec3.X, -Vec3.X, Vec3.Y, -Vec3.Y, Vec3.Z, -Vec3.Z)
            10, 100 -> trapezohedron()
            12 -> signs3().map { (a, b, c) -> Vec3(a, b, c) } +
                signs2().flatMap { (a, b) ->
                    listOf(Vec3(0.0, a / PHI, b * PHI), Vec3(a / PHI, b * PHI, 0.0), Vec3(a * PHI, 0.0, b / PHI))
                }
            else -> icosahedron() // d20 and the "ball" fallback for custom dice
        }

        private fun icosahedron() = signs2().flatMap { (a, b) ->
            listOf(Vec3(0.0, a, b * PHI), Vec3(a, b * PHI, 0.0), Vec3(a * PHI, 0.0, b))
        }

        /** Pentagonal trapezohedron; apex height makes each kite planar. */
        fun trapezohedron(ringZ: Double = 0.1): List<Vec3> {
            val c = cos(PI / 5)
            val apex = ringZ * (1 + c) / (1 - c)
            val ring = List(10) { k ->
                val angle = k * PI / 5
                Vec3(cos(angle), sin(angle), if (k % 2 == 0) ringZ else -ringZ)
            }
            return ring + Vec3(0.0, 0.0, apex) + Vec3(0.0, 0.0, -apex)
        }

        private fun signs2() = listOf(1.0 to 1.0, 1.0 to -1.0, -1.0 to 1.0, -1.0 to -1.0)

        private fun signs3() = signs2().flatMap { (a, b) -> listOf(Triple(a, b, 1.0), Triple(a, b, -1.0)) }

        private fun normalize(points: List<Vec3>): List<Vec3> {
            val r = points.maxOf { it.length }
            return points.map { it * (1.0 / r) }
        }

        /**
         * Numbers 1..N with opposite faces summing to N+1, assigned in a stable order. With
         * [digitOf]/[pairSum] the sum rule applies to the printed digits instead (d10: 0..9, sum 9).
         */
        private fun oppositeValues(
            mesh: ConvexMesh,
            count: Int,
            pairSum: Int = count + 1,
            digitOf: (Int) -> Int = { it },
        ): List<Int> {
            val values = IntArray(count)
            val order = mesh.faces.indices.sortedWith(
                compareByDescending<Int> { mesh.faces[it].normal.y }
                    .thenByDescending { mesh.faces[it].normal.x }
                    .thenByDescending { mesh.faces[it].normal.z },
            )
            for (f in order) {
                if (values[f] != 0) continue
                val next = (1..count).first { candidate -> candidate !in values }
                values[f] = next
                val opposite = mesh.faces.indices.firstOrNull { g ->
                    values[g] == 0 && (mesh.faces[g].normal dot mesh.faces[f].normal) < -0.999
                }
                if (opposite != null) {
                    val partnerDigit = pairSum - digitOf(next)
                    values[opposite] = (1..count).first { it !in values && digitOf(it) == partnerDigit }
                }
            }
            return values.toList()
        }
    }
}
