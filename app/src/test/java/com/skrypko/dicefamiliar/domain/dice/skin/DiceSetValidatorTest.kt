package com.skrypko.dicefamiliar.domain.dice.skin

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DiceSetValidatorTest {

    private fun parsed(json: String) = DiceSetValidator.parse(json) as DiceSetParseResult.Parsed

    private val valid = """
        {
          "formatVersion": 1, "id": "wood_set", "name": "Wood",
          "style": { "fill": "#8D6E63", "edge": "#FF3E2723", "text": "theme", "edgeWidth": 1.5,
                     "font": "fonts/numbers.ttf" },
          "dice": {
            "d6": { "image2d": "2d/d6.png", "faceTexture3d": "3d/d6_face.webp", "pips": true },
            "d100": { "fill": "#5D4037" },
            "dN": { "image2d": "2d/dN.png" }
          },
          "futureField": 42
        }
    """.trimIndent()

    @Test
    fun validManifestHasNoIssues() {
        val result = parsed(valid)
        assertEquals(emptyList<DiceSetIssue>(), result.issues)
        assertEquals("wood_set", result.manifest.id)
        assertEquals(true, result.manifest.dice["d6"]?.pips)
    }

    @Test
    fun bundledDefaultSetIsValid() {
        val text = java.io.File("src/main/assets/dice_sets/default/manifest.json").readText()
        assertEquals(emptyList<DiceSetIssue>(), parsed(text).issues)
    }

    @Test
    fun reportsEachProblem() {
        val issues = parsed(
            """
            {
              "formatVersion": 1, "id": "Bad Id", "name": "",
              "style": { "fill": "red", "edgeWidth": 20, "font": "../fonts/x.ttf" },
              "dice": {
                "d7": {},
                "d8": { "image2d": "/abs/d8.png", "pips": true },
                "d10": { "faceTexture3d": "3d/face.jpg" },
                "d12": { "image2d": "2d\\d12.png" }
              }
            }
            """.trimIndent(),
        ).issues.map { it.path }.toSet()
        assertEquals(
            setOf(
                "id", "name", "style.fill", "style.edgeWidth", "style.font", "dice.d7",
                "dice.d8.image2d", "dice.d8.pips", "dice.d10.faceTexture3d", "dice.d12.image2d",
            ),
            issues,
        )
    }

    @Test
    fun unsupportedVersionAndBrokenJsonFail() {
        assertTrue(DiceSetValidator.parse("""{"formatVersion":2,"id":"x","name":"X"}""") is DiceSetParseResult.Failed)
        assertTrue(DiceSetValidator.parse("{ not json") is DiceSetParseResult.Failed)
        assertTrue(DiceSetValidator.parse("""{"id":"x","name":"X"}""") is DiceSetParseResult.Failed)
    }

    @Test
    fun keysForDice() {
        assertEquals("d20", DiceSetFormat.keyFor(20))
        assertEquals("d100", DiceSetFormat.keyFor(100))
        assertEquals("dN", DiceSetFormat.keyFor(7))
    }
}
