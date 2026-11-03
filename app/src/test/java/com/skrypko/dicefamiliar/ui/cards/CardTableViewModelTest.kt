package com.skrypko.dicefamiliar.ui.cards

import androidx.lifecycle.SavedStateHandle
import com.skrypko.dicefamiliar.domain.cards.CardTable
import com.skrypko.dicefamiliar.domain.cards.StandardCards
import com.skrypko.dicefamiliar.domain.random.SeededRandomSource
import com.skrypko.dicefamiliar.domain.settings.AppSettings
import com.skrypko.dicefamiliar.testing.FakeAppSettingsRepository
import com.skrypko.dicefamiliar.testing.FakeCardDeckRepository
import com.skrypko.dicefamiliar.testing.FakeCardTableRepository
import com.skrypko.dicefamiliar.testing.FakeSoundPlayer
import com.skrypko.dicefamiliar.ui.common.Sound
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CardTableViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val decks = FakeCardDeckRepository()
    private val tables = FakeCardTableRepository()
    private val sound = FakeSoundPlayer()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.viewModel(settings: AppSettings = AppSettings()) =
        CardTableViewModel(decks, tables, FakeAppSettingsRepository(settings), SeededRandomSource(), sound).also { runCurrent() }

    @Test
    fun opensTheDefault52CardDeckShuffled() = runTest(dispatcher) {
        val state = viewModel().uiState.value
        assertEquals("preset-52", state.deck?.id)
        assertEquals(52, state.drawCount)
        assertTrue(state.table!!.isValidFor(52))
        assertEquals(state.table, tables.saved["preset-52"])
    }

    @Test
    fun restoresTheCurrentDecksTable() = runTest(dispatcher) {
        val saved = CardTable((2 until 52).toList(), listOf(0), listOf(1))
        tables.saved["preset-52"] = saved
        assertEquals(saved, viewModel().uiState.value.table)
    }

    @Test
    fun switchingDecksStartsFresh() = runTest(dispatcher) {
        val vm = viewModel()
        vm.draw()
        decks.select("preset-36")
        runCurrent()
        val state = vm.uiState.value
        assertEquals(36, state.drawCount)
        assertTrue(state.hand.isEmpty())
        assertEquals(setOf("preset-36"), tables.saved.keys)
    }

    @Test
    fun actionsChangeTheTablePlaySoundsAndSave() = runTest(dispatcher) {
        val vm = viewModel()
        val events = mutableListOf<CardEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.events.collect { events += it } }
        vm.draw()
        vm.draw()
        val first = vm.uiState.value.hand.first()
        vm.discard(first)
        vm.discardHand()
        vm.takeFromDiscard()
        vm.discardOntoDraw()
        advanceUntilIdle()
        val state = vm.uiState.value
        assertEquals(51, state.drawCount)
        assertEquals(1, state.hand.size)
        assertEquals(0, state.discardCount)
        assertTrue(state.table!!.isValidFor(52))
        assertEquals(state.table, tables.saved["preset-52"])
        assertEquals(
            listOf(CardEvent.DREW, CardEvent.DREW, CardEvent.DISCARDED, CardEvent.DISCARDED, CardEvent.DREW, CardEvent.DISCARDED),
            events,
        )
        // Moving cards between piles never sounds like a shuffle.
        assertTrue(Sound.CARD_SHUFFLE !in sound.played)
        assertEquals(Sound.CARD_FLICK, sound.played.first())
    }

    @Test
    fun noOpsAreSilent_andResetRestoresEverything() = runTest(dispatcher) {
        val vm = viewModel(AppSettings(soundsEnabled = false))
        vm.takeFromDiscard()
        vm.discardHand()
        vm.shuffleDiscard()
        assertTrue(sound.played.isEmpty())
        repeat(5) { vm.draw() }
        vm.reset()
        assertEquals(52, vm.uiState.value.drawCount)
        assertTrue(sound.played.isEmpty())
    }

    @Test
    fun cardsAndHandGoOnTopOfTheDeck() = runTest(dispatcher) {
        val vm = viewModel()
        vm.draw()
        val card = vm.uiState.value.hand.single()
        vm.putOnDraw(card)
        assertTrue(vm.uiState.value.hand.isEmpty())
        assertEquals(card, vm.uiState.value.table!!.drawPile.last())
        vm.draw()
        vm.draw()
        val hand = vm.uiState.value.hand
        vm.handToDraw()
        assertEquals(hand, vm.uiState.value.table!!.drawPile.takeLast(2))
        assertEquals(Sound.CARD_FLICK, sound.played.last())
    }

    @Test
    fun panelToggleIsRemembered() = runTest(dispatcher) {
        val vm = viewModel()
        assertTrue(vm.uiState.value.panelVisible)
        vm.togglePanel()
        runCurrent()
        assertEquals(false, viewModel().uiState.value.panelVisible)
    }

    @Test
    fun buttonsToggleIsRemembered() = runTest(dispatcher) {
        val vm = viewModel()
        vm.toggleButtons()
        runCurrent()
        assertTrue(vm.uiState.value.showButtons)
        assertTrue(viewModel().uiState.value.showButtons)
    }

    @Test
    fun customDeckEditorSavesSortedAndSelects() = runTest(dispatcher) {
        val editor = CustomDeckViewModel(SavedStateHandle(), decks)
        runCurrent()
        editor.onNameChange("Short")
        editor.toggleGroup(StandardCards.preset52) // all 52 were preselected: clears them
        editor.toggle("AS")
        editor.toggle(StandardCards.RED_JOKER)
        editor.save()
        runCurrent()
        val id = decks.selected.value
        assertEquals(listOf(StandardCards.RED_JOKER, "AS"), decks.deck(id)!!.cards.map { it.standardId }.let { ids ->
            StandardCards.all54.filter { it in ids }
        })
        assertEquals("Short", decks.customDeck(id)!!.name)
    }
}
