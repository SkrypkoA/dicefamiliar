package com.skrypko.dicefamiliar.ui.cards

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.skrypko.dicefamiliar.domain.cards.CardTable
import com.skrypko.dicefamiliar.domain.cards.DeckPresets
import com.skrypko.dicefamiliar.domain.settings.AppSettings
import com.skrypko.dicefamiliar.ui.theme.DiceFamiliarTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CardTableContentTest {

    @get:Rule
    val compose = createComposeRule()

    private val calls = mutableListOf<String>()
    private val actions = CardTableActions(
        draw = { calls += "draw" },
        takeFromDiscard = { calls += "take" },
        discard = { calls += "discard$it" },
        putOnDraw = { calls += "put$it" },
        handToDraw = { calls += "handToDraw" },
        discardHand = { calls += "discardHand" },
        shuffleDraw = { calls += "shuffle" },
        shuffleDiscard = { calls += "shuffleDiscard" },
        discardOntoDraw = { calls += "intoDraw" },
        reset = { calls += "reset" },
        toggleButtons = { calls += "toggle" },
        togglePanel = { calls += "panel" },
    )

    private val shared = mutableListOf<String>()
    private val images = mutableListOf<androidx.compose.ui.graphics.ImageBitmap>()

    private fun show(showButtons: Boolean = false, hand: List<Int> = listOf(48, 49, 50), panelVisible: Boolean = true) {
        val deck = DeckPresets.get(DeckPresets.DEFAULT_ID)!!
        val table = CardTable((0 until 48).toList(), hand, (48 until 52).filter { it !in hand })
        compose.setContent {
            DiceFamiliarTheme {
                CardTableContent(
                    state = CardTableUiState(deck, table, showButtons, panelVisible, AppSettings(animationsEnabled = false)),
                    onOpenDecks = { calls += "decks" },
                    actions = actions,
                    onShare = { shared += it },
                    onShareImage = { images += it },
                    onCopy = { shared += "copy: $it" },
                )
            }
        }
    }

    @Test
    fun tapAndLongPressThePiles() {
        show()
        compose.onNodeWithText("52 cards").assertIsDisplayed()
        compose.onNodeWithTag(DRAW_PILE_TAG).performClick()
        compose.onNodeWithTag(DRAW_PILE_TAG).performTouchInput { longClick() }
        compose.onNodeWithTag(DISCARD_PILE_TAG).performClick()
        compose.onNodeWithTag(DISCARD_PILE_TAG).performTouchInput { longClick() }
        assertEquals(listOf("draw", "shuffle", "take", "shuffleDiscard"), calls)
    }

    @Test
    fun dragDiscardOntoTheDeck() {
        show()
        val from = compose.onNodeWithTag(DISCARD_PILE_TAG).getUnclippedBoundsInRoot()
        val to = compose.onNodeWithTag(DRAW_PILE_TAG).getUnclippedBoundsInRoot()
        val dx = (to.left + to.right) / 2 - (from.left + from.right) / 2
        compose.onNodeWithTag(DISCARD_PILE_TAG).performTouchInput {
            swipe(center, center + Offset(dx.toPx(), 0f), durationMillis = 600)
        }
        assertEquals(listOf("intoDraw"), calls)
    }

    @Test
    fun swipeUpDiscardsACard_andTapViewsIt() {
        show()
        compose.onNodeWithTag("${HAND_CARD_TAG}50").performTouchInput { swipeUp(startY = centerY, endY = top - 300f) }
        assertEquals(listOf("discard50"), calls)
        compose.onNodeWithTag("${HAND_CARD_TAG}48").performClick()
        compose.onNodeWithTag(VIEWER_TAG, useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun dragAHandCardOntoTheDeckPutsItOnTop() {
        show()
        val from = compose.onNodeWithTag("${HAND_CARD_TAG}48").getUnclippedBoundsInRoot()
        val to = compose.onNodeWithTag(DRAW_PILE_TAG).getUnclippedBoundsInRoot()
        val dx = (to.left + to.right) / 2 - (from.left + from.right) / 2
        val dy = (to.top + to.bottom) / 2 - (from.top + from.bottom) / 2
        compose.onNodeWithTag("${HAND_CARD_TAG}48").performTouchInput {
            // Start straight up (so the card, not the hand, takes the drag), then over to the deck.
            down(center)
            moveBy(Offset(0f, -60f))
            moveBy(Offset(dx.toPx(), dy.toPx() + 60f))
            up()
        }
        assertEquals(listOf("put48"), calls)
    }

    @Test
    fun dragAHandCardOntoTheDiscardPile() {
        show()
        val from = compose.onNodeWithTag("${HAND_CARD_TAG}50").getUnclippedBoundsInRoot()
        val to = compose.onNodeWithTag(DISCARD_PILE_TAG).getUnclippedBoundsInRoot()
        val dx = (to.left + to.right) / 2 - (from.left + from.right) / 2
        val dy = (to.top + to.bottom) / 2 - (from.top + from.bottom) / 2
        compose.onNodeWithTag("${HAND_CARD_TAG}50").performTouchInput {
            down(center)
            moveBy(Offset(0f, -60f))
            moveBy(Offset(dx.toPx(), dy.toPx() + 60f))
            up()
        }
        assertEquals(listOf("discard50"), calls)
    }

    @Test
    fun longPressPicksUpTheHandAndDropsItOnAPile() {
        show()
        fun dragHandTo(tag: String) {
            val hand = compose.onNodeWithTag(HAND_TAG).getUnclippedBoundsInRoot()
            val to = compose.onNodeWithTag(tag).getUnclippedBoundsInRoot()
            compose.onNodeWithTag(HAND_TAG).performTouchInput {
                val start = center
                down(start)
                advanceEventTime(viewConfiguration.longPressTimeoutMillis + 100)
                val target = Offset(((to.left + to.right) / 2 - hand.left).toPx(), ((to.top + to.bottom) / 2 - hand.top).toPx())
                moveTo(Offset((start.x + target.x) / 2, (start.y + target.y) / 2))
                moveTo(target)
                up()
            }
        }
        dragHandTo(DISCARD_PILE_TAG)
        dragHandTo(DRAW_PILE_TAG)
        assertEquals(listOf("discardHand", "handToDraw"), calls)
    }

    @Test
    fun carriedHandAndDiscardAreShownAsAPile() {
        show()
        compose.onNodeWithTag(CARRIED_TAG).assertDoesNotExist()
        compose.onNodeWithTag(HAND_TAG).performTouchInput {
            down(center)
            advanceEventTime(viewConfiguration.longPressTimeoutMillis + 100)
            moveBy(Offset(0f, -150f))
            moveBy(Offset(0f, -150f))
        }
        compose.onNodeWithTag(CARRIED_TAG).assertIsDisplayed()
        compose.onNodeWithTag(HAND_TAG).performTouchInput { up() }
        compose.onNodeWithTag(CARRIED_TAG).assertDoesNotExist()

        compose.onNodeWithTag(DISCARD_PILE_TAG).performTouchInput {
            down(center)
            moveBy(Offset(-60f, 40f))
            moveBy(Offset(-60f, 40f))
        }
        compose.onNodeWithTag(CARRIED_TAG).assertIsDisplayed()
        compose.onNodeWithTag(DISCARD_PILE_TAG).performTouchInput { up() }
        compose.onNodeWithTag(CARRIED_TAG).assertDoesNotExist()
        assertEquals(emptyList<String>(), calls)
    }

    @Test
    fun sidewaysSwipeNoLongerDiscardsTheHand() {
        show()
        compose.onNodeWithTag(HAND_TAG).performTouchInput { swipeLeft(startX = right - 5f, endX = left + 5f) }
        assertEquals(emptyList<String>(), calls)
    }

    @Test
    fun handStaysInsideTheTable() {
        show(hand = (0 until 12).toList())
        val area = compose.onNodeWithTag(HAND_TAG).getUnclippedBoundsInRoot()
        (0 until 12).forEach { card ->
            val b = compose.onNodeWithTag("${HAND_CARD_TAG}$card").getUnclippedBoundsInRoot()
            assertTrue("card $card left ${b.left} < ${area.left}", b.left >= area.left - 1.dp)
            assertTrue("card $card bottom ${b.bottom} > ${area.bottom}", b.bottom <= area.bottom + 1.dp)
        }
    }

    @Test
    fun shareAndCollapsibleTopBar() {
        show()
        compose.onNodeWithContentDescription("Share hand").performClick()
        compose.onNodeWithText("Share as text").performClick()
        compose.onNodeWithContentDescription("Share hand").performClick()
        compose.onNodeWithText("Copy").performClick()
        val hand = "52 cards: Jack of Clubs, Queen of Clubs, King of Clubs"
        assertEquals(listOf(hand, "copy: $hand"), shared)
        compose.onNodeWithContentDescription("Hide table controls").performClick()
        assertEquals(listOf("panel"), calls)
    }

    @Test
    fun shareAsImageRendersTheHand() {
        show()
        compose.onNodeWithContentDescription("Share hand").performClick()
        compose.onNodeWithText("Share as image").performClick()
        compose.waitUntil(timeoutMillis = 5_000) { images.isNotEmpty() }
        val image = images.single()
        assertTrue("image ${image.width}x${image.height}", image.width > 200 && image.height > 200)
    }

    @Test
    fun collapsedTopBarHidesItsButtonsButKeepsShare() {
        show(panelVisible = false)
        compose.onNodeWithContentDescription("Choose a deck").assertDoesNotExist()
        compose.onNodeWithContentDescription("Share hand").assertIsDisplayed()
        compose.onNodeWithContentDescription("Show table controls").assertIsDisplayed()
        compose.onNodeWithContentDescription("How to use the card table").assertIsDisplayed()
    }

    @Test
    fun crowdedHandKeepsValuesVisibleAndScrolls() {
        show(hand = (0 until 30).toList())
        val first = compose.onNodeWithTag("${HAND_CARD_TAG}0").getUnclippedBoundsInRoot()
        val second = compose.onNodeWithTag("${HAND_CARD_TAG}1").getUnclippedBoundsInRoot()
        val width = first.right - first.left
        // Each card shows at least 30% of itself (its value corner).
        assertTrue(second.left - first.left >= width * 0.29f)
        // A sideways drag scrolls the hand (and doesn't discard it).
        compose.onNodeWithTag(HAND_TAG).performTouchInput { swipe(Offset(right - 20f, bottom - 10f), Offset(right - 420f, bottom - 10f), 400) }
        compose.waitForIdle()
        val moved = compose.onNodeWithTag("${HAND_CARD_TAG}0").getUnclippedBoundsInRoot()
        assertTrue("hand should scroll", moved.left < first.left)
        assertEquals(emptyList<String>(), calls)
    }

    @Test
    fun buttonsDuplicateTheGestures() {
        show(showButtons = true)
        listOf("Draw", "Shuffle deck", "Take discard", "Discard → deck", "Discard hand", "Hand → deck").forEach {
            compose.onNodeWithText(it).performClick()
        }
        assertEquals(listOf("draw", "shuffle", "take", "intoDraw", "discardHand", "handToDraw"), calls)
    }

    @Test
    fun topBarActions() {
        show()
        compose.onNodeWithContentDescription("Choose a deck").performClick()
        compose.onNodeWithContentDescription("Show action buttons").performClick()
        compose.onNodeWithContentDescription("Reset").performClick()
        compose.onNodeWithText("Reset the table?").assertIsDisplayed()
        compose.onNodeWithText("Reset").performClick()
        compose.onNodeWithContentDescription("How to use the card table").performClick()
        compose.onNodeWithText("Long-press your hand to pick up all of it", substring = true).assertExists()
        assertEquals(listOf("decks", "toggle", "reset"), calls)
    }

    @Test
    fun emptyHandHint() {
        show(hand = emptyList())
        compose.onNodeWithText("Tap the deck to draw a card").assertIsDisplayed()
    }
}
