package com.skrypko.dicefamiliar.domain.random

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RandomSourceTest {

    @Test
    fun nextInt_staysWithinRange_andHitsBothEnds() {
        val random = SeededRandomSource()
        val seen = HashSet<Int>()
        repeat(10_000) {
            val value = random.nextInt(1, 7)
            assertTrue("value $value out of range", value in 1..6)
            seen += value
        }
        assertEquals((1..6).toSet(), seen)
    }

    @Test
    fun nextDouble_staysWithinRange() {
        val random = SeededRandomSource()
        repeat(10_000) {
            val value = random.nextDouble(-1.5, 2.5)
            assertTrue("value $value out of range", value >= -1.5 && value < 2.5)
        }
    }

    @Test
    fun sameSeed_producesSameSequence() {
        val a = SeededRandomSource(7)
        val b = SeededRandomSource(7)
        val seqA = List(100) { a.nextLong(0, 1_000_000) }
        val seqB = List(100) { b.nextLong(0, 1_000_000) }
        assertEquals(seqA, seqB)
    }

    @Test
    fun shuffle_returnsPermutation_andLeavesInputUntouched() {
        val input = (1..52).toList()
        val shuffled = SeededRandomSource().shuffle(input)
        assertEquals(input.size, shuffled.size)
        assertEquals(input.toSet(), shuffled.toSet())
        assertEquals((1..52).toList(), input)
    }

    @Test
    fun shuffle_isDeterministicForSeed_andActuallyReorders() {
        val input = (1..52).toList()
        val first = SeededRandomSource(1).shuffle(input)
        val second = SeededRandomSource(1).shuffle(input)
        assertEquals(first, second)
        assertTrue(first != input)
    }

    @Test
    fun shuffle_handlesEmptyAndSingleton() {
        val random = SeededRandomSource()
        assertEquals(emptyList<Int>(), random.shuffle(emptyList<Int>()))
        assertEquals(listOf("x"), random.shuffle(listOf("x")))
    }
}
