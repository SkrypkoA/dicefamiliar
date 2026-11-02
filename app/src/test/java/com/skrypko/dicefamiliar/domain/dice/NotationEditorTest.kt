package com.skrypko.dicefamiliar.domain.dice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NotationEditorTest {

    @Test
    fun addToBlankStartsFormula() {
        assertEquals(EditResult("1d6"), NotationEditor.addDie("", 6))
    }

    @Test
    fun addIncrementsExistingGroupOrAppendsNew() {
        var text = ""
        listOf(6, 6, 4).forEach { text = NotationEditor.addDie(text, it).text }
        assertEquals("2d6+1d4", text)
    }

    @Test
    fun newDiceGoBeforeModifier() {
        assertEquals("2d6+1d4+1", NotationEditor.addDie("2d6+1", 4).text)
    }

    @Test
    fun keepAndNegativeGroupsAreNotMerged() {
        assertEquals("4d6kh3+1d6", NotationEditor.addDie("4d6kh3", 6).text)
        assertEquals("1d8-1d6+1d6", NotationEditor.addDie("1d8-1d6", 6).text)
    }

    @Test
    fun invalidTextGetsDieAppended() {
        assertEquals("3d6+1d4", NotationEditor.addDie("3d6+", 4).text)
        assertEquals("2x+1d4", NotationEditor.addDie("2x", 4).text)
    }

    @Test
    fun limitsBlockAdd() {
        val result = NotationEditor.addDie("100d6", 6)
        assertEquals("100d6", result.text)
        assertEquals(NotationError.COUNT_OUT_OF_RANGE, result.error)
        assertEquals(NotationError.TOO_MANY_DICE, NotationEditor.addDie("100d6+100d8", 4).error)
    }

    @Test
    fun removeDecrementsAndDropsGroup() {
        assertEquals("1d6+1d4+1", NotationEditor.removeDie("2d6+1d4+1", 6).text)
        assertEquals("1d4+1", NotationEditor.removeDie("1d6+1d4+1", 6).text)
        assertEquals("", NotationEditor.removeDie("1d6", 6).text)
        assertEquals("1d4", NotationEditor.removeDie("1d4", 6).text)
        assertEquals("4d6kh3", NotationEditor.removeDie("4d6kh3", 6).text)
    }

    @Test
    fun customDiceCanBeCountedAndRemoved() {
        assertEquals(3, NotationEditor.countCustom("2d7+1d6+1d30"))
        assertEquals("2d7+1d6", NotationEditor.removeCustomDie("2d7+1d6+1d30").text)
        assertEquals("1d7+1d6", NotationEditor.removeCustomDie("2d7+1d6").text)
        assertEquals("1d6", NotationEditor.removeCustomDie("1d7+1d6").text)
        assertEquals("1d6+2", NotationEditor.removeCustomDie("1d6+2").text)
        assertEquals(0, NotationEditor.countCustom("1d6+1d100"))
    }

    @Test
    fun customSidesAndHasDice() {
        assertEquals(7, NotationEditor.customSides("1d6+2d7"))
        assertEquals(null, NotationEditor.customSides("1d6+1d100+3"))
        assertEquals(null, NotationEditor.customSides(""))
        assertEquals(true, NotationEditor.hasDice("1d6"))
        assertEquals(false, NotationEditor.hasDice("+3"))
        assertEquals(false, NotationEditor.hasDice(""))
    }

    @Test
    fun modifierMergesAndDisappearsAtZero() {
        assertEquals("2d6+1", NotationEditor.adjustModifier("2d6", 1).text)
        assertEquals("2d6+2", NotationEditor.adjustModifier("2d6+1", 1).text)
        assertEquals("2d6", NotationEditor.adjustModifier("2d6+1", -1).text)
        assertEquals("2d6-1", NotationEditor.adjustModifier("2d6", -1).text)
        assertEquals("2d6+2", NotationEditor.adjustModifier("2d6+1+3-1", -1).text)
        assertEquals("1", NotationEditor.adjustModifier("", 1).text)
        assertEquals(NotationError.CONSTANT_OUT_OF_RANGE, NotationEditor.adjustModifier("1d6+10000", 1).error)
        assertNull(NotationEditor.adjustModifier("1d6", 1).error)
    }

    @Test
    fun countsDiceBySides() {
        assertEquals(3, NotationEditor.countOf("2d6+1d6kh1+1d4", 6))
        assertEquals(1, NotationEditor.countOf("2d6+1d6kh1+1d4", 4))
        assertEquals(0, NotationEditor.countOf("2d6", 20))
        assertEquals(0, NotationEditor.countOf("2d6+", 6))
    }
}
