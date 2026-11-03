package com.skrypko.dicefamiliar.domain.number

import com.skrypko.dicefamiliar.domain.random.SeededRandomSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NumberGeneratorTest {

    private val generator = NumberGenerator(SeededRandomSource())

    @Test
    fun withDuplicates_staysInRange_andHitsBothEnds() {
        val values = generator.generate(NumberSpec(1, 6, 1000, allowDuplicates = true, decimals = 0))
        assertEquals(1000, values.size)
        assertTrue(values.all { it in 1L..6L })
        assertEquals((1L..6L).toSet(), values.toSet())
    }

    @Test
    fun singleValueRange() {
        assertEquals(listOf(7L, 7L), generator.generate(NumberSpec(7, 7, 2, true, 0)))
        assertEquals(listOf(7L), generator.generate(NumberSpec(7, 7, 1, false, 0)))
    }

    @Test
    fun withoutDuplicates_valuesAreDistinct_andInRange() {
        repeat(200) {
            val values = generator.generate(NumberSpec(-10, 10, 15, allowDuplicates = false, decimals = 0))
            assertEquals(15, values.size)
            assertEquals(15, values.toSet().size)
            assertTrue(values.all { it in -10L..10L })
        }
    }

    @Test
    fun withoutDuplicates_fullRange_isAPermutation() {
        val values = generator.generate(NumberSpec(1, 50, 50, allowDuplicates = false, decimals = 0))
        assertEquals((1L..50L).toList(), values.sorted())
        assertTrue("order should be shuffled", values != values.sorted())
    }

    @Test
    fun withoutDuplicates_hugeRange() {
        val values = generator.generate(NumberSpec(-1_000_000_000_000_000, 1_000_000_000_000_000, 1000, false, 6))
        assertEquals(1000, values.toSet().size)
    }

    @Test
    fun withoutDuplicates_isRoughlyUniform() {
        val counts = LongArray(10)
        repeat(20_000) {
            generator.generate(NumberSpec(0, 9, 3, allowDuplicates = false, decimals = 0)).forEach { counts[it.toInt()]++ }
        }
        // 60 000 picks over 10 values: 6 000 each.
        counts.forEach { assertTrue("count $it", it in 5_600..6_400) }
    }

    @Test
    fun withDuplicates_isRoughlyUniform() {
        val counts = LongArray(5)
        generator.generate(NumberSpec(0, 4, 1000, true, 0)).let { first -> first.forEach { counts[it.toInt()]++ } }
        repeat(49) { generator.generate(NumberSpec(0, 4, 1000, true, 0)).forEach { counts[it.toInt()]++ } }
        counts.forEach { assertTrue("count $it", it in 9_500..10_500) }
    }

    @Test
    fun formatter_isExact() {
        assertEquals("42", NumberFormatter.format(42, 0))
        assertEquals("-3", NumberFormatter.format(-3, 0))
        assertEquals("0.50", NumberFormatter.format(50, 2))
        assertEquals("-0.05", NumberFormatter.format(-5, 2))
        assertEquals("1000000000.000001", NumberFormatter.format(1_000_000_000_000_001, 6))
        assertEquals("1.5, 0.1", NumberFormatter.joined(listOf(15, 1), 1))
    }

    @Test
    fun result_textAndSum() {
        val result = NumberResult("1", listOf(150, 25), 2, 0, 200, true, 0)
        assertEquals("1.50, 0.25", result.text)
        assertEquals(175L, result.sum)
    }
}
