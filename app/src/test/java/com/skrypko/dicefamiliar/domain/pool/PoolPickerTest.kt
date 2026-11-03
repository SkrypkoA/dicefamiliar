package com.skrypko.dicefamiliar.domain.pool

import com.skrypko.dicefamiliar.domain.random.SeededRandomSource
import com.skrypko.dicefamiliar.testing.ScriptedRandomSource
import org.junit.Assert.assertEquals
import org.junit.Test

class PoolPickerTest {
    private val items = listOf("a", "b", "c", "d")

    @Test
    fun withReplacementUsesNextIntPerPick() {
        val result = PoolPicker(ScriptedRandomSource(2, 2, 0)).pick(items, 3, withReplacement = true)
        assertEquals(PickResult.Picked(listOf("c", "c", "a")), result)
    }

    @Test
    fun withoutReplacementIsDistinct() {
        repeat(50) { seed ->
            val picked = (PoolPicker(SeededRandomSource(seed.toLong())).pick(items, 4, withReplacement = false) as PickResult.Picked).items
            assertEquals(items.toSet(), picked.toSet())
        }
    }

    @Test
    fun errors() {
        val picker = PoolPicker(SeededRandomSource())
        assertEquals(PickResult.Failed(PickError.EMPTY_POOL), picker.pick(emptyList<String>(), 1, true))
        assertEquals(PickResult.Failed(PickError.COUNT_TOO_SMALL), picker.pick(items, 0, true))
        assertEquals(PickResult.Failed(PickError.COUNT_EXCEEDS_POOL), picker.pick(items, 5, false))
        assertEquals(PickResult.Failed(PickError.COUNT_TOO_LARGE), picker.pick(items, PoolLimits.MAX_PICKS + 1, true))
        assertEquals(5, (picker.pick(items, 5, true) as PickResult.Picked).items.size)
    }
}
