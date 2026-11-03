package com.skrypko.dicefamiliar.ui.cards

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.skrypko.dicefamiliar.data.cards.CardDeckRepository
import com.skrypko.dicefamiliar.data.cards.CardTableRepository
import com.skrypko.dicefamiliar.data.settings.AppSettingsRepository
import com.skrypko.dicefamiliar.domain.cards.CardFace
import com.skrypko.dicefamiliar.domain.cards.CardTable
import com.skrypko.dicefamiliar.domain.cards.DeckDefinition
import com.skrypko.dicefamiliar.domain.random.RandomSource
import com.skrypko.dicefamiliar.domain.settings.AppSettings
import com.skrypko.dicefamiliar.ui.common.Sound
import com.skrypko.dicefamiliar.ui.common.SoundPlayer
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CardTableUiState(
    val deck: DeckDefinition? = null,
    val table: CardTable? = null,
    val showButtons: Boolean = false,
    /** The top bar (deck, share, buttons toggle, reset, help) is expanded. */
    val panelVisible: Boolean = true,
    val settings: AppSettings = AppSettings(),
) {
    fun face(card: Int): CardFace? = deck?.cards?.getOrNull(card)
    val topDiscard: CardFace? get() = table?.discard?.lastOrNull()?.let(::face)
    val drawCount: Int get() = table?.drawPile?.size ?: 0
    val discardCount: Int get() = table?.discard?.size ?: 0
    val hand: List<Int> get() = table?.hand.orEmpty()
}

/** What the screen should feel (haptics). Sounds are played by the ViewModel. */
enum class CardEvent { DREW, DISCARDED, SHUFFLED }

@HiltViewModel
class CardTableViewModel @Inject constructor(
    private val decks: CardDeckRepository,
    private val tables: CardTableRepository,
    settingsRepository: AppSettingsRepository,
    private val random: RandomSource,
    private val soundPlayer: SoundPlayer,
) : ViewModel() {

    private val _uiState = MutableStateFlow(CardTableUiState())
    val uiState: StateFlow<CardTableUiState> = _uiState.asStateFlow()

    private val _events = Channel<CardEvent>(Channel.BUFFERED)
    val events: Flow<CardEvent> = _events.receiveAsFlow()

    init {
        viewModelScope.launch { settingsRepository.settings.collect { s -> _uiState.update { it.copy(settings = s) } } }
        viewModelScope.launch { tables.showButtons.collect { show -> _uiState.update { it.copy(showButtons = show) } } }
        viewModelScope.launch { tables.panelVisible.collect { show -> _uiState.update { it.copy(panelVisible = show) } } }
        viewModelScope.launch {
            decks.selectedDeckId.distinctUntilChanged().collect { id -> open(id) }
        }
    }

    /** Opens [id]: the saved table if it is this deck's, otherwise a freshly shuffled one. */
    private suspend fun open(id: String) {
        val deck = decks.deck(id) ?: return
        val table = tables.load(deck.id, deck.cards.size) ?: CardTable.fresh(deck.cards.size, random).also { tables.save(deck.id, it) }
        _uiState.update { it.copy(deck = deck, table = table) }
    }

    fun draw() = apply(CardEvent.DREW) { it.draw() }
    fun takeFromDiscard() = apply(CardEvent.DREW) { it.takeFromDiscard() }
    fun discard(card: Int) = apply(CardEvent.DISCARDED) { it.discard(card) }
    fun putOnDraw(card: Int) = apply(CardEvent.DISCARDED) { it.putOnDraw(card) }
    fun handToDraw() = apply(CardEvent.DISCARDED) { it.handToDraw() }
    fun discardHand() = apply(CardEvent.DISCARDED) { it.discardHand() }
    fun shuffleDraw() = apply(CardEvent.SHUFFLED, onlyIf = { it.drawPile.size > 1 }) { it.shuffleDraw(random) }
    fun shuffleDiscard() = apply(CardEvent.SHUFFLED, onlyIf = { it.discard.size > 1 }) { it.shuffleDiscard(random) }
    /** Moves only (no shuffle): the discard flick, never the shuffle sound. */
    fun discardOntoDraw() = apply(CardEvent.DISCARDED) { it.discardOntoDraw() }
    fun reset() = apply(CardEvent.SHUFFLED) { it.reset(random) }

    fun togglePanel() {
        val show = !_uiState.value.panelVisible
        _uiState.update { it.copy(panelVisible = show) }
        viewModelScope.launch { tables.setPanelVisible(show) }
    }

    fun toggleButtons() {
        val show = !_uiState.value.showButtons
        _uiState.update { it.copy(showButtons = show) }
        viewModelScope.launch { tables.setShowButtons(show) }
    }

    private fun apply(event: CardEvent, onlyIf: (CardTable) -> Boolean = { true }, change: (CardTable) -> CardTable) {
        val state = _uiState.value
        val deck = state.deck ?: return
        val table = state.table ?: return
        if (!onlyIf(table)) return
        val next = change(table)
        // A shuffle always "happens" (same set of cards); other no-ops are ignored.
        if (next == table && event != CardEvent.SHUFFLED) return
        _uiState.update { it.copy(table = next) }
        if (state.settings.soundsEnabled) soundPlayer.play(if (event == CardEvent.SHUFFLED) Sound.CARD_SHUFFLE else Sound.CARD_FLICK)
        _events.trySend(event)
        viewModelScope.launch { tables.save(deck.id, next) }
    }
}
