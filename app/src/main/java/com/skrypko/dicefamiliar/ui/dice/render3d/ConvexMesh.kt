package com.skrypko.dicefamiliar.ui.dice.render3d

import kotlin.math.abs
import kotlin.math.atan2

/** A planar face of a convex mesh; [indices] are ordered counter-clockwise seen from outside. */
data class MeshFace(val indices: List<Int>, val normal: Vec3, val centroid: Vec3)

class ConvexMesh private constructor(val vertices: List<Vec3>, val faces: List<MeshFace>) {

    companion object {
        private const val EPS = 1e-6

        /** Builds the convex hull faces of [points], merging coplanar points into polygons. */
        fun fromVertices(points: List<Vec3>): ConvexMesh {
            val planes = mutableListOf<Pair<Vec3, Double>>()
            for (i in points.indices) for (j in i + 1 until points.size) for (k in j + 1 until points.size) {
                val raw = (points[j] - points[i]) cross (points[k] - points[i])
                if (raw.length < EPS) continue
                var normal = raw.normalized()
                var d = normal dot points[i]
                val sides = points.map { (normal dot it) - d }
                when {
                    sides.all { it <= EPS } -> Unit
                    sides.all { it >= -EPS } -> {
                        normal = -normal
                        d = -d
                    }
                    else -> continue
                }
                if (planes.none { (n, pd) -> (n - normal).length < 1e-4 && abs(pd - d) < 1e-4 }) {
                    planes += normal to d
                }
            }
            val faces = planes.map { (normal, d) ->
                val members = points.indices.filter { abs((normal dot points[it]) - d) < 1e-4 }
                val centroid = members.fold(Vec3.ZERO) { acc, i -> acc + points[i] } * (1.0 / members.size)
                val u = (points[members.first()] - centroid).normalized()
                val v = normal cross u
                val ordered = members.sortedBy { i ->
                    val p = points[i] - centroid
                    atan2(p dot v, p dot u)
                }
                MeshFace(ordered, normal, centroid)
            }
            return ConvexMesh(points, faces)
        }
    }
}
