package com.skrypko.dicefamiliar.domain.dice.pool

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SuccessCriterionTest {
    @Test
    fun matchesAndLabels() {
        val fivePlus = SuccessCriterion(5, Comparison.AT_LEAST)
        assertTrue(fivePlus.matches(5))
        assertTrue(fivePlus.matches(6))
        assertFalse(fivePlus.matches(4))
        assertEquals("5+", fivePlus.label)
        val twoMinus = SuccessCriterion(2, Comparison.AT_MOST)
        assertTrue(twoMinus.matches(1))
        assertFalse(twoMinus.matches(3))
        assertEquals("2−", twoMinus.label)
        val ones = SuccessCriterion(1, Comparison.EQUAL)
        assertTrue(ones.matches(1))
        assertFalse(ones.matches(2))
        assertEquals("=1", ones.label)
    }

    @Test
    fun clampsToTheDie() {
        assertEquals(6, SuccessCriterion(9).clampTo(6).target)
        assertEquals(1, SuccessCriterion(0).clampTo(6).target)
    }
}

class DicePoolTest {
    private val pool = DicePool.of(6, listOf(1, 1, 3, 4, 5, 5, 6, 6, 3, 4))

    @Test
    fun matchingAndSuccesses() {
        assertEquals(listOf(0, 1), pool.matching(SuccessCriterion(1, Comparison.EQUAL)).map { it.id })
        assertEquals(4, pool.successes(SuccessCriterion(5)))
        assertEquals(6, pool.successes(SuccessCriterion(4, Comparison.AT_MOST)))
    }

    @Test
    fun orderedPutsTheGroupFirst() {
        assertEquals(listOf(6, 6, 5, 5, 4, 4, 3, 3, 1, 1), pool.ordered(SuccessCriterion(5)).map { it.value })
        assertEquals(listOf(1, 1, 3, 3, 4, 4, 5, 5, 6, 6), pool.ordered(SuccessCriterion(2, Comparison.AT_MOST)).map { it.value })
        assertEquals(listOf(1, 1, 6, 6, 5, 5, 4, 4, 3, 3), pool.ordered(SuccessCriterion(1, Comparison.EQUAL)).map { it.value })
        // Ties keep the pool order (stable sort).
        assertEquals(listOf(6, 7), pool.ordered(SuccessCriterion(5)).take(2).map { it.id })
    }

    @Test
    fun rerollInPlaceKeepsIdsAndPositions() {
        val rerolled = pool.withValues(mapOf(0 to 1, 1 to 5))
        assertEquals(listOf(1, 5, 3, 4, 5, 5, 6, 6, 3, 4), rerolled.dice.map { it.value })
        assertEquals(pool.dice.map { it.id }, rerolled.dice.map { it.id })
        assertEquals(listOf(5, 5, 5, 6, 6), rerolled.matching(SuccessCriterion(5)).map { it.value }.sorted())
    }
}

class PoolGroupsTest {
    @Test
    fun addSelectsAndReusesTheSameCriterion() {
        val groups = PoolGroups().add(SuccessCriterion(1, Comparison.EQUAL)).add(SuccessCriterion(5))
        assertEquals(2, groups.groups.size)
        assertEquals(SuccessCriterion(5), groups.selected?.criterion)
        val again = groups.add(SuccessCriterion(1, Comparison.EQUAL))
        assertEquals(2, again.groups.size)
        assertEquals(SuccessCriterion(1, Comparison.EQUAL), again.selected?.criterion)
    }

    @Test
    fun toggleSelectsOneAtATime() {
        val groups = PoolGroups().add(SuccessCriterion(1, Comparison.EQUAL)).add(SuccessCriterion(5))
        val first = groups.groups.first().id
        val selected = groups.toggle(first)
        assertEquals(first, selected.selectedId)
        assertNull(selected.toggle(first).selectedId)
        assertNull(selected.toggle(99).selectedId)
    }

    @Test
    fun updateAndRemove() {
        val groups = PoolGroups().add(SuccessCriterion(5))
        val id = groups.groups.single().id
        assertEquals(SuccessCriterion(4), groups.update(id, SuccessCriterion(4)).selected?.criterion)
        val removed = groups.remove(id)
        assertTrue(removed.groups.isEmpty())
        assertNull(removed.selectedId)
    }

    @Test
    fun groupsAreCapped() {
        var groups = PoolGroups()
        (1..PoolGroups.MAX_GROUPS + 3).forEach { groups = groups.add(SuccessCriterion(it)) }
        assertEquals(PoolGroups.MAX_GROUPS, groups.groups.size)
    }
}

class PoolNotationTest {
    @Test
    fun oneTypeAtATime() {
        assertEquals("1d6", PoolNotation.add("", 6))
        assertEquals("2d6", PoolNotation.add("1d6", 6))
        assertEquals("1d8", PoolNotation.add("5d6", 8))
        assertEquals("4d6", PoolNotation.remove("5d6", 6))
        assertEquals("5d6", PoolNotation.remove("5d6", 8))
        assertEquals("", PoolNotation.remove("1d6", 6))
    }

    @Test
    fun countIsClamped() {
        assertEquals("10d6", PoolNotation.withCount("1d6", 10))
        assertEquals("100d6", PoolNotation.withCount("1d6", 500))
        assertEquals("1d6", PoolNotation.withCount("3d6", 0))
        assertEquals("", PoolNotation.withCount("", 5))
        assertEquals("100d6", PoolNotation.add("100d6", 6))
    }

    @Test
    fun parse() {
        assertEquals(10 to 6, PoolNotation.parse("10d6"))
        assertEquals(1 to 7, PoolNotation.parse("d7"))
        assertNull(PoolNotation.parse("2d6+1"))
        assertNull(PoolNotation.parse("0d6"))
    }
}
