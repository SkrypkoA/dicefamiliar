package com.skrypko.dicefamiliar.domain.dice.skin

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DiceSetEditorTest {

    private val empty = DiceSetEditor.newManifest("wood", " Wood ")

    @Test
    fun newManifestIsValidAndFollowsTheme() {
        assertEquals("Wood", empty.name)
        assertEquals(DiceSetFormat.THEME, empty.style.fill)
        assertTrue(DiceSetValidator.validate(empty).isEmpty())
    }

    @Test
    fun setsAndRemovesImages() {
        val path2d = DiceSetEditor.fileFor("d6", DieImageSlot.IMAGE_2D)
        val path3d = DiceSetEditor.fileFor("d6", DieImageSlot.FACE_3D)
        assertEquals("2d/d6.png", path2d)
        assertEquals("3d/d6_face.png", path3d)

        val both = DiceSetEditor.withImage(
            DiceSetEditor.withImage(empty, "d6", DieImageSlot.IMAGE_2D, path2d),
            "d6", DieImageSlot.FACE_3D, path3d,
        )
        assertEquals(path2d, DiceSetEditor.imagePath(both, "d6", DieImageSlot.IMAGE_2D))
        assertEquals(setOf(path2d, path3d), DiceSetEditor.referencedPaths(both))

        val only3d = DiceSetEditor.withImage(both, "d6", DieImageSlot.IMAGE_2D, null)
        assertNull(DiceSetEditor.imagePath(only3d, "d6", DieImageSlot.IMAGE_2D))
        val none = DiceSetEditor.withImage(only3d, "d6", DieImageSlot.FACE_3D, null)
        assertFalse("empty entries are dropped", "d6" in none.dice)
    }

    @Test
    fun keepsOtherFieldsOfADie() {
        val styled = empty.copy(dice = mapOf("d6" to DieStyleSpec(fill = "#FF0000", pips = true)))
        val updated = DiceSetEditor.withImage(styled, "d6", DieImageSlot.IMAGE_2D, "2d/d6.png")
        assertEquals(DieStyleSpec(fill = "#FF0000", pips = true, image2d = "2d/d6.png"), updated.dice["d6"])
        assertEquals(
            DieStyleSpec(fill = "#FF0000", pips = true),
            DiceSetEditor.withImage(updated, "d6", DieImageSlot.IMAGE_2D, null).dice["d6"],
        )
    }

    @Test
    fun customDieHasNoFaceTexture() {
        assertTrue(DiceSetEditor.supports("dN", DieImageSlot.IMAGE_2D))
        assertFalse(DiceSetEditor.supports("dN", DieImageSlot.FACE_3D))
        assertFalse(DiceSetEditor.supports("d7", DieImageSlot.IMAGE_2D))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsUnsupportedSlot() {
        DiceSetEditor.withImage(empty, "dN", DieImageSlot.FACE_3D, "3d/dN_face.png")
    }

    @Test
    fun encodeRoundTripsThroughValidator() {
        val manifest = DiceSetEditor.withImage(empty, "d20", DieImageSlot.IMAGE_2D, "2d/d20.png")
        val text = DiceSetEditor.encode(manifest)
        assertFalse("nulls are omitted", text.contains("null"))
        val parsed = DiceSetValidator.parse(text) as DiceSetParseResult.Parsed
        assertEquals(manifest, parsed.manifest)
        assertTrue(parsed.issues.isEmpty())
    }
}
