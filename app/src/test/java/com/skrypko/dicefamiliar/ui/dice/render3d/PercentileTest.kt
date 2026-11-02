package com.skrypko.dicefamiliar.ui.dice.render3d

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PercentileTest {

    @Test
    fun splitsIntoTensAndUnitsLikeTabletopPercentileDice() {
        assertEquals("70" to "3", Percentile.labels(73))
        assertEquals("00" to "0", Percentile.labels(100))
        assertEquals("00" to "5", Percentile.labels(5))
        assertEquals("10" to "0", Percentile.labels(10))
        assertEquals("00" to "1", Percentile.labels(1))
        assertEquals("90" to "9", Percentile.labels(99))
    }

    @Test
    fun d100IsDrawnAsTwoD10sThatLandOnTheirDigits() {
        val dice = Die3D.of(100, 73, kept = false)
        assertEquals(listOf(DieKind.PERCENTILE_TENS, DieKind.PERCENTILE_UNITS), dice.map { it.kind })
        assertTrue(dice.none { it.kept })

        val d10 = DieModel.forSides(10)
        val tensFace = d10.restFace(7, DieKind.PERCENTILE_TENS)
        val unitsFace = d10.restFace(3, DieKind.PERCENTILE_UNITS)
        assertEquals("70", d10.label(tensFace, 7, tensFace, DieKind.PERCENTILE_TENS))
        assertEquals("3", d10.label(unitsFace, 3, unitsFace, DieKind.PERCENTILE_UNITS))
        val up = d10.restRotation(tensFace) * d10.faceUp[tensFace]
        assertTrue((up dot Vec3.Y) > 0.999)
    }

    @Test
    fun percentileFaceLabelSets() {
        val d10 = DieModel.forSides(10)
        val faces = d10.mesh.faces.indices
        assertEquals(
            (0..9).map { Percentile.tensLabel(it) }.sorted(),
            faces.map { d10.label(it, 0, -1, DieKind.PERCENTILE_TENS) }.sorted(),
        )
        assertEquals((0..9).map { "$it" }, faces.map { d10.label(it, 0, -1, DieKind.PERCENTILE_UNITS) }.sorted())
        assertEquals(listOf(listOf(100, 10)), listOf(Die3D.of(100, 100, true).map { it.sides }))
    }
}
