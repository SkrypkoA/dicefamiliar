package com.skrypko.dicefamiliar.domain.number

import org.junit.Assert.assertEquals
import org.junit.Test

class NumberStepperTest {

    private fun int(text: String, delta: Int) = NumberStepper.stepBound(text, delta, NumberMode.INTEGER)
    private fun dec(text: String, delta: Int) = NumberStepper.stepBound(text, delta, NumberMode.DECIMAL)

    @Test
    fun integerSteps() {
        assertEquals("101", int("100", 1))
        assertEquals("-1", int("0", -1))
        assertEquals("5", int(" 4 ", 1))
        assertEquals("3", int("+2", 1))
    }

    @Test
    fun integerMode_roundsFractionsTowardsTheStep() {
        assertEquals("2", int("1.5", 1))
        assertEquals("1", int("1.5", -1))
        assertEquals("-2", int("-1.5", -1))
        assertEquals("3", int("1.0", 2))
    }

    @Test
    fun decimalMode_keepsTheFraction_andTheSeparator() {
        assertEquals("1.25", dec("0.25", 1))
        assertEquals("-0.75", dec("0.25", -1))
        assertEquals("2,5", dec("1,5", 1))
    }

    @Test
    fun invalidTextBecomesZero() {
        assertEquals("0", int("", 1))
        assertEquals("0", int("abc", -1))
        assertEquals("0", dec("-", 1))
    }

    @Test
    fun boundsAreClamped() {
        assertEquals("1000000000", int("1000000000", 1))
        assertEquals("-1000000000", int("-1000000000", -1))
        assertEquals("1000000000", int("5000000000", -1))
    }

    @Test
    fun countSteps() {
        assertEquals("2", NumberStepper.stepCount("1", 1))
        assertEquals("1", NumberStepper.stepCount("1", -1))
        assertEquals("1000", NumberStepper.stepCount("1000", 1))
        assertEquals("1", NumberStepper.stepCount("", 1))
        assertEquals("1000", NumberStepper.stepCount("2000", 0))
    }
}
