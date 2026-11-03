package com.skrypko.dicefamiliar.domain.dice.skin

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** The example packs in docs/dice-examples (tools/gen_dice_examples.py) must import cleanly. */
class DiceSetExamplesTest {

    @get:Rule
    val temp = TemporaryFolder()

    private fun example(name: String): File =
        listOf(File("../docs/dice-examples", name), File("docs/dice-examples", name)).first { it.isFile }

    private fun read(name: String): DiceSetArchiveResult.Success {
        val result = example(name).inputStream().use {
            DiceSetArchiveReader().read(it, File(temp.root, name), name.substringBefore('.'))
        }
        assertTrue("$name: $result", result is DiceSetArchiveResult.Success)
        return result as DiceSetArchiveResult.Success
    }

    @Test
    fun woodIsAFullPack() {
        val wood = read("wood.zip")
        assertTrue(wood.hadManifest)
        assertEquals("wood", wood.manifest.id)
        assertEquals(DiceSetFormat.DIE_KEYS.toSet(), wood.manifest.dice.keys)
        assertEquals(0, wood.missingFiles)
        assertEquals(0, wood.skippedFiles)
        assertTrue(DiceSetValidator.validate(wood.manifest).isEmpty())
        assertEquals(true, wood.manifest.dice.getValue("d6").pips)
        DiceSetEditor.referencedPaths(wood.manifest).forEach { assertTrue(it, File(wood.rootDir, it).isFile) }
    }

    @Test
    fun neonSetsColoursAndFindsFilesByName() {
        val neon = read("neon.zip")
        assertTrue(neon.hadManifest)
        assertEquals(setOf("d6", "d20", "dN"), neon.manifest.dice.keys)
        assertEquals("3d/d20_face.png", neon.manifest.dice.getValue("d20").faceTexture3d)
        assertEquals("2d/d6.png", neon.manifest.dice.getValue("d6").image2d)
        assertEquals("fonts/segments.ttf", neon.manifest.style.font)
        // Light numbers on dark dice, so they stay readable in light and dark theme.
        assertEquals("#00E5FF", neon.manifest.style.text)
        assertEquals("#FF4081", neon.manifest.dice.getValue("d20").text)
        assertEquals(0, neon.missingFiles)
        assertTrue(DiceSetValidator.validate(neon.manifest).isEmpty())
    }
}
