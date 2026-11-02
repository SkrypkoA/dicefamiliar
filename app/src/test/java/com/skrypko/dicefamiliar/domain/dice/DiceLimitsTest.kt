package com.skrypko.dicefamiliar.domain.dice

import org.junit.Assert.assertEquals
import org.junit.Test

class DiceLimitsTest {

    @Test
    fun stepSidesClampsToAllowedRange() {
        assertEquals(DiceLimits.MIN_SIDES, DiceLimits.stepSides(null, 1))
        assertEquals(DiceLimits.MIN_SIDES, DiceLimits.stepSides(null, -1))
        assertEquals(8, DiceLimits.stepSides(7, 1))
        assertEquals(6, DiceLimits.stepSides(7, -1))
        assertEquals(DiceLimits.MIN_SIDES, DiceLimits.stepSides(DiceLimits.MIN_SIDES, -1))
        assertEquals(DiceLimits.MAX_SIDES, DiceLimits.stepSides(DiceLimits.MAX_SIDES, 1))
        assertEquals(DiceLimits.MAX_SIDES, DiceLimits.stepSides(9999, -1))
    }
}
