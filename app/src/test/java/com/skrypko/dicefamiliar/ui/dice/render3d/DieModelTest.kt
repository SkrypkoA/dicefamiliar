package com.skrypko.dicefamiliar.ui.dice.render3d

import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DieModelTest {

    private val faceSides = mapOf(4 to 3, 6 to 4, 8 to 3, 10 to 4, 12 to 5, 20 to 3)

    @Test
    fun meshesHaveExpectedFacesAndShapes() {
        faceSides.forEach { (sides, polygon) ->
            val mesh = DieModel.forSides(sides).mesh
            assertEquals("d$sides faces", sides, mesh.faces.size)
            mesh.faces.forEach { assertEquals("d$sides polygon", polygon, it.indices.size) }
        }
        assertEquals(10, DieModel.forSides(100).mesh.faces.size)
    }

    @Test
    fun facesArePlanarAndNormalsPointOutward() {
        (faceSides.keys + 0).forEach { sides ->
            val mesh = DieModel.forSides(sides).mesh
            mesh.faces.forEach { face ->
                assertTrue("outward d$sides", (face.normal dot face.centroid) > 0)
                face.indices.forEach { i ->
                    val off = (mesh.vertices[i] - face.centroid) dot face.normal
                    assertTrue("planar d$sides", abs(off) < 1e-6)
                }
            }
        }
    }

    @Test
    fun faceOrderIsCounterClockwiseFromOutside() {
        faceSides.keys.forEach { sides ->
            val mesh = DieModel.forSides(sides).mesh
            mesh.faces.forEach { face ->
                val p = face.indices.map { mesh.vertices[it] }
                val winding = (p[1] - p[0]) cross (p[2] - p[1])
                assertTrue("ccw d$sides", (winding dot face.normal) > 0)
            }
        }
    }

    @Test
    fun valuesAreOneToNAndOppositesSumToNPlusOne() {
        listOf(6, 8, 12, 20).forEach { sides ->
            val die = DieModel.forSides(sides)
            assertEquals((1..sides).toList(), die.faceValues.sorted())
            die.mesh.faces.forEachIndexed { i, face ->
                val opposite = die.mesh.faces.indexOfFirst { (it.normal dot face.normal) < -0.999 }
                assertEquals("d$sides opposite", sides + 1, die.faceValues[i] + die.faceValues[opposite])
            }
        }
        assertEquals((1..4).toList(), DieModel.forSides(4).faceValues.sorted())
    }

    @Test
    fun d10ShowsZeroForTenAndOppositeDigitsSumToNine() {
        val die = DieModel.forSides(10)
        val labels = die.mesh.faces.indices.map { die.label(it, 0, -1) }
        assertEquals((0..9).map { "$it" }, labels.sorted())
        val tenFace = die.restFace(10)
        assertEquals("0", die.label(tenFace, 10, tenFace))
        die.mesh.faces.forEachIndexed { i, face ->
            val opposite = die.mesh.faces.indexOfFirst { (it.normal dot face.normal) < -0.999 }
            assertEquals(9, die.faceValues[i] % 10 + die.faceValues[opposite] % 10)
        }
    }

    @Test
    fun restRotationTurnsResultFaceToViewerUpright() {
        listOf(4, 6, 8, 10, 12, 20, 100).forEach { sides ->
            val die = DieModel.forSides(sides)
            (1..sides).forEach { value ->
                val face = die.restFace(value)
                val rotation = die.restRotation(face)
                val normal = rotation * die.mesh.faces[face].normal
                val up = rotation * die.faceUp[face]
                assertTrue("d$sides v$value faces viewer", (normal dot Vec3.Z) > 0.999)
                assertTrue("d$sides v$value upright", (up dot Vec3.Y) > 0.999)
                val expected = if (sides == 10) (value % 10).toString() else value.toString()
                assertEquals(expected, die.label(face, value, face))
            }
        }
    }

    @Test
    fun customDiceUseBallWithOnlyRestFaceLabelled() {
        val die = DieModel.forSides(7)
        val rest = die.restFace(5)
        assertEquals("5", die.label(rest, 5, rest))
        assertEquals("", die.label((rest + 1) % die.mesh.faces.size, 5, rest))
        val d100 = DieModel.forSides(100)
        val tensLabels = d100.mesh.faces.indices.map { d100.label(it, value = 1, restFace = -1) }.sorted()
        assertEquals(listOf("00", "10", "20", "30", "40", "50", "60", "70", "80", "90"), tensLabels)
    }

    @Test
    fun animationEndsExactlyAtTargetAndSpinDecays() {
        val die = DieModel.forSides(20)
        val target = die.restRotation(die.restFace(17))
        val axis = DieAnimation.spinAxis(1.0, 0.0, 2)
        val end = DieAnimation.orientation(1.0, target, axis, 3.0)
        val n = die.mesh.faces[die.restFace(17)].normal
        assertTrue(((end * n) - (target * n)).length < 1e-9)
        val angles = (0..10).map { DieAnimation.spinAngle(it / 10.0, 3.0) }
        assertTrue(angles.zipWithNext().all { (a, b) -> b <= a })
        assertEquals(0.0, angles.last(), 1e-12)
        assertEquals(0.0, DieAnimation.bounce(1.0), 1e-12)
    }
}
