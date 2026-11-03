package com.skrypko.dicefamiliar.ui.common

import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.skrypko.dicefamiliar.ui.theme.DiceFamiliarTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StepperNumberFieldTest {

    @get:Rule
    val compose = createComposeRule()

    private fun show(arrows: StepperArrows, initial: Int = 5, max: Int = 1000) = compose.setContent {
        DiceFamiliarTheme {
            var value by remember { mutableStateOf(initial) }
            StepperNumberField(
                value = value.toString(),
                onValueChange = { text -> text.toIntOrNull()?.let { value = it } },
                onStep = { value = (value + it).coerceIn(0, max) },
                increaseDescription = "Up",
                decreaseDescription = "Down",
                arrows = arrows,
                label = "Value",
                canIncrease = value < max,
                modifier = Modifier.width(240.dp).testTag("stepper"),
                fieldModifier = Modifier.testTag("field"),
            )
        }
    }

    private fun field() = compose.onNodeWithTag("field")
    private fun bounds(description: String) = compose.onNodeWithContentDescription(description).getUnclippedBoundsInRoot()

    @Test
    fun chevronsStep() {
        show(StepperArrows.ABOVE_BELOW)
        compose.onNodeWithContentDescription("Up").performClick()
        compose.onNodeWithContentDescription("Up").performClick()
        compose.onNodeWithContentDescription("Down").performClick()
        field().assertTextContains("6")
    }

    @Test
    fun swipingStepsUpAndDown() {
        show(StepperArrows.AFTER)
        compose.onNodeWithTag("stepper").performTouchInput { swipeUp() }
        compose.waitForIdle()
        val afterUp = field().fetchText()
        assertTrue("swipe up should increase, got $afterUp", afterUp > 5)
        compose.onNodeWithTag("stepper").performTouchInput { swipeDown() }
        assertTrue(field().fetchText() < afterUp)
    }

    @Test
    fun aboveBelow_placesUpOverTheFieldAndDownUnderIt() {
        show(StepperArrows.ABOVE_BELOW)
        val field = field().getUnclippedBoundsInRoot()
        assertTrue(bounds("Up").bottom <= field.top)
        assertTrue(bounds("Down").top >= field.bottom)
    }

    @Test
    fun after_placesArrowsRightOfTheField() {
        show(StepperArrows.AFTER)
        val field = field().getUnclippedBoundsInRoot()
        assertTrue(bounds("Up").left >= field.right)
        assertTrue(bounds("Down").left >= field.right)
        assertTrue(bounds("Up").bottom <= bounds("Down").top)
    }

    @Test
    fun before_placesArrowsLeftOfTheField() {
        show(StepperArrows.BEFORE)
        val field = field().getUnclippedBoundsInRoot()
        assertTrue(bounds("Up").right <= field.left)
        assertTrue(bounds("Down").right <= field.left)
    }

    @Test
    fun increaseIsDisabledAtTheLimit() {
        show(StepperArrows.AFTER, initial = 10, max = 10)
        compose.onNodeWithContentDescription("Up").assertIsNotEnabled()
    }

    private fun androidx.compose.ui.test.SemanticsNodeInteraction.fetchText(): Int =
        fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.EditableText].text.toInt()
}
