package com.skrypko.dicefamiliar.domain.dice.skin

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DiceSetIdsTest {

    @Test
    fun slugifiesNames() {
        assertEquals("wood_set", DiceSetIds.fromName("Wood Set"))
        assertEquals("neon-2", DiceSetIds.fromName("  Neon-2!! "))
        assertEquals("a_b", DiceSetIds.fromName("a   ..  b"))
    }

    @Test
    fun emptyOrNonLatinFallsBack() {
        assertEquals(DiceSetIds.FALLBACK, DiceSetIds.fromName(""))
        assertEquals(DiceSetIds.FALLBACK, DiceSetIds.fromName("Кубики"))
    }

    @Test
    fun capsLength() {
        val id = DiceSetIds.fromName("x".repeat(100))
        assertEquals(DiceSetIds.MAX_LENGTH, id.length)
        assertTrue(DiceSetIds.isValid(id))
    }

    @Test
    fun uniqueAddsSuffix() {
        assertEquals("wood", DiceSetIds.unique("wood", setOf("default")))
        assertEquals("wood_2", DiceSetIds.unique("wood", setOf("wood")))
        assertEquals("wood_3", DiceSetIds.unique("wood", setOf("wood", "wood_2")))
        val long = "y".repeat(DiceSetIds.MAX_LENGTH)
        val unique = DiceSetIds.unique(long, setOf(long))
        assertEquals(DiceSetIds.MAX_LENGTH, unique.length)
        assertTrue(unique.endsWith("_2"))
    }

    @Test
    fun validation() {
        assertTrue(DiceSetIds.isValid("default"))
        assertFalse(DiceSetIds.isValid("../etc"))
        assertFalse(DiceSetIds.isValid(".import_wood"))
        assertFalse(DiceSetIds.isValid(""))
    }
}
