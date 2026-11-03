package com.skrypko.dicefamiliar.domain.dice.skin

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DiceSetConventionsTest {

    @Test
    fun matchesFoldersAndRootFilesCaseInsensitively() {
        val manifest = DiceSetConventions.manifestFromFiles(
            "Wood Dice",
            listOf("2d/d6.png", "D20.WEBP", "3d/d6_face.png", "d8_face.webp", "fonts/Numbers.ttf", "readme.png"),
        )!!
        assertEquals("wood_dice", manifest.id)
        assertEquals("Wood Dice", manifest.name)
        assertEquals(DieStyleSpec(image2d = "2d/d6.png", faceTexture3d = "3d/d6_face.png"), manifest.dice["d6"])
        assertEquals(DieStyleSpec(image2d = "D20.WEBP"), manifest.dice["d20"])
        assertEquals(DieStyleSpec(faceTexture3d = "d8_face.webp"), manifest.dice["d8"])
        assertEquals("fonts/Numbers.ttf", manifest.style.font)
        assertEquals(setOf("d6", "d20", "d8"), manifest.dice.keys)
        assertTrue(DiceSetValidator.validate(manifest).isEmpty())
    }

    @Test
    fun customDieImageButNoFace() {
        val manifest = DiceSetConventions.manifestFromFiles("x", listOf("dn.png", "dn_face.png"))!!
        assertEquals(DieStyleSpec(image2d = "dn.png"), manifest.dice["dN"])
    }

    @Test
    fun ignoresNestedFonts() {
        assertNull(DiceSetConventions.manifestFromFiles("x", listOf("extra/fonts/a.ttf")))
    }

    @Test
    fun nothingRecognisedGivesNull() {
        assertNull(DiceSetConventions.manifestFromFiles("x", listOf("cover.png", "notes/d6.png")))
    }

    @Test
    fun fillsWhatTheManifestLeavesOut() {
        val manifest = DiceSetManifest(
            formatVersion = 1,
            id = "neon",
            name = "Neon",
            style = DieStyleSpec(text = "#00E5FF"),
            dice = mapOf(
                "d20" to DieStyleSpec(text = "#FF4081"),
                "d6" to DieStyleSpec(image2d = "art/six.png"),
            ),
        )
        val filled = DiceSetConventions.fillFromFiles(
            manifest,
            listOf("2d/d6.png", "2d/d20.png", "3d/d20_face.png", "fonts/segments.ttf", "art/six.png"),
        )
        assertEquals("#00E5FF", filled.style.text)
        assertEquals("fonts/segments.ttf", filled.style.font)
        assertEquals(DieStyleSpec(text = "#FF4081", image2d = "2d/d20.png", faceTexture3d = "3d/d20_face.png"), filled.dice["d20"])
        assertEquals("the manifest wins", "art/six.png", filled.dice.getValue("d6").image2d)
        assertEquals(setOf("d6", "d20"), filled.dice.keys)
    }

    @Test
    fun manifestFontWins() {
        val manifest = DiceSetEditor.newManifest("x", "X").let { it.copy(style = it.style.copy(font = "a.otf")) }
        assertEquals("a.otf", DiceSetConventions.fillFromFiles(manifest, listOf("fonts/b.ttf")).style.font)
    }
}
