package com.skrypko.dicefamiliar.ui.cards

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.skrypko.dicefamiliar.data.cards.CardDeckRepository
import com.skrypko.dicefamiliar.data.cards.DeckInfo
import com.skrypko.dicefamiliar.domain.cards.CardLimits
import com.skrypko.dicefamiliar.domain.cards.DeckPresets
import com.skrypko.dicefamiliar.domain.cards.StandardCards
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DeckListUiState(val decks: List<DeckInfo> = emptyList(), val selectedId: String = DeckPresets.DEFAULT_ID)

@HiltViewModel
class DeckListViewModel @Inject constructor(private val repository: CardDeckRepository) : ViewModel() {

    val uiState: StateFlow<DeckListUiState> = combine(repository.decks, repository.selectedDeckId) { decks, id ->
        DeckListUiState(decks, id)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DeckListUiState())

    fun select(id: String) = viewModelScope.launch { repository.select(id) }

    fun delete(id: String) = viewModelScope.launch { repository.delete(id) }
}

data class CustomDeckUiState(
    val name: String = "",
    val selected: Set<String> = emptySet(),
    val loaded: Boolean = false,
) {
    val canSave: Boolean get() = loaded && selected.isNotEmpty()
}

/** Creates (no `deckId` argument) or edits a custom deck picked from the 54 standard cards. */
@HiltViewModel
class CustomDeckViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: CardDeckRepository,
) : ViewModel() {
    private val deckId: String? = savedStateHandle[ARG_DECK_ID]

    private val _uiState = MutableStateFlow(CustomDeckUiState(loaded = deckId == null, selected = StandardCards.preset52.toSet()))
    val uiState: StateFlow<CustomDeckUiState> = _uiState.asStateFlow()

    private val _saved = Channel<String>(Channel.BUFFERED)

    /** Emits the deck id after saving; the screen goes back. */
    val saved: Flow<String> = _saved.receiveAsFlow()

    init {
        if (deckId != null) {
            viewModelScope.launch {
                val deck = repository.customDeck(deckId)
                _uiState.update { CustomDeckUiState(deck?.name.orEmpty(), deck?.cardIds.orEmpty().toSet(), loaded = true) }
            }
        }
    }

    fun onNameChange(name: String) = _uiState.update { it.copy(name = name.take(CardLimits.MAX_NAME_LENGTH)) }

    fun toggle(id: String) = _uiState.update { it.copy(selected = if (id in it.selected) it.selected - id else it.selected + id) }

    /** Selects all of [ids], or clears them when they are all selected already. */
    fun toggleGroup(ids: List<String>) = _uiState.update {
        it.copy(selected = if (it.selected.containsAll(ids)) it.selected - ids.toSet() else it.selected + ids)
    }

    fun save() {
        val state = _uiState.value
        if (!state.canSave) return
        viewModelScope.launch {
            val id = repository.saveCustom(deckId, state.name, state.selected.toList())
            repository.select(id)
            _saved.send(id)
        }
    }

    companion object {
        const val ARG_DECK_ID = "deckId"
    }
}
