package com.skrypko.dicefamiliar.ui.common

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.skrypko.dicefamiliar.domain.dice.DiceNotationParser
import com.skrypko.dicefamiliar.domain.dice.DiceRoller
import com.skrypko.dicefamiliar.domain.dice.ParseResult
import com.skrypko.dicefamiliar.domain.dice.RollMode
import com.skrypko.dicefamiliar.domain.number.NumberResult
import com.skrypko.dicefamiliar.domain.random.RandomSource
import com.skrypko.dicefamiliar.domain.remote.Participant
import com.skrypko.dicefamiliar.domain.settings.AppSettings
import com.skrypko.dicefamiliar.ui.dice.DiceContent
import com.skrypko.dicefamiliar.ui.dice.DiceUiState
import com.skrypko.dicefamiliar.ui.dice.RollRecord
import com.skrypko.dicefamiliar.ui.number.NumberContent
import com.skrypko.dicefamiliar.ui.number.NumberUiState
import com.skrypko.dicefamiliar.ui.theme.DiceFamiliarTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Dice and number results can be shared as text or as an image, through the same share menu. */
@RunWith(AndroidJUnit4::class)
class ShareAsImageTest {

    @get:Rule
    val compose = createComposeRule()

    private val texts = mutableListOf<String>()
    private val images = mutableListOf<ImageBitmap>()
    private val settings = AppSettings(animationsEnabled = false)

    private fun roll(): RollRecord {
        val queue = ArrayDeque(listOf(3, 5, 2))
        val random = object : RandomSource {
            override fun nextInt(from: Int, until: Int) = queue.removeFirst()
            override fun nextLong(from: Long, until: Long) = error("unused")
            override fun nextDouble(from: Double, until: Double) = error("unused")
        }
        val expression = (DiceNotationParser.parse("2d6+1d4+3") as ParseResult.Success).expression
        val me = Participant("me", "Me")
        return RollRecord("1", "2d6+1d4+3", RollMode.NORMAL, null, DiceRoller(random).roll(expression), me, me, 0L)
    }

    private fun shareBoth(button: String) {
        compose.onNodeWithContentDescription(button).performClick()
        compose.onNodeWithText("Share as text").performClick()
        compose.onNodeWithContentDescription(button).performClick()
        compose.onNodeWithText("Share as image").performClick()
        compose.waitUntil(timeoutMillis = 5_000) { images.isNotEmpty() }
        assertTrue(images.single().width > 200 && images.single().height > 100)
    }

    @Test
    fun diceRoll() {
        val latest = roll()
        compose.setContent {
            DiceFamiliarTheme {
                DiceContent(
                    DiceUiState(notation = "2d6+1d4+3", latest = latest, recent = listOf(latest), settings = settings),
                    {}, {}, {}, {}, {}, {}, {},
                    onShare = { texts += it },
                    onShareImage = { images += it },
                )
            }
        }
        shareBoth("Share roll")
        assertEquals(listOf("2d6+1d4+3 = 13 · [3 5] [2]"), texts)
    }

    @Test
    fun numberResult() {
        val result = NumberResult("1", listOf(7, 42, 13), 0, 1, 100, true, 0)
        compose.setContent {
            DiceFamiliarTheme {
                NumberContent(
                    state = NumberUiState(latest = result, recent = listOf(result), settings = settings),
                    onMinChange = {}, onMaxChange = {}, onCountChange = {}, onAllowDuplicatesChange = {},
                    onModeChange = {}, onDecimalsChange = {}, onGenerate = {},
                    onShare = { texts += it.text },
                    onShareImage = { images += it },
                )
            }
        }
        shareBoth("Share result")
        assertEquals(listOf("7, 42, 13"), texts)
    }
}
