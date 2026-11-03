package com.skrypko.dicefamiliar.ui.settings.dicesets

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.skrypko.dicefamiliar.data.dice.DiceSetRepository
import com.skrypko.dicefamiliar.data.dice.DieImageResult
import com.skrypko.dicefamiliar.data.dice.StreamSource
import com.skrypko.dicefamiliar.domain.dice.skin.DiceSetEditor
import com.skrypko.dicefamiliar.domain.dice.skin.DiceSetFormat
import com.skrypko.dicefamiliar.domain.dice.skin.DiceSetManifest
import com.skrypko.dicefamiliar.domain.dice.skin.DieImageSlot
import com.skrypko.dicefamiliar.ui.dice.skin.DiceSkin
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** One die in the editor grid. */
data class DieEntry(val key: String, val has2d: Boolean, val has3d: Boolean, val supports3d: Boolean)

data class DiceSetEditUiState(
    val loading: Boolean = true,
    /** The set was deleted or never existed. */
    val missing: Boolean = false,
    val name: String = "",
    val dice: List<DieEntry> = emptyList(),
    val skin: DiceSkin = DiceSkin.Default,
    /** The die and slot whose image is being stored. */
    val busy: Pair<String, DieImageSlot>? = null,
)

sealed interface DiceSetEditEvent {
    data class ImageFailed(val result: DieImageResult) : DiceSetEditEvent
}

@HiltViewModel
class DiceSetEditViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: DiceSetRepository,
) : ViewModel() {
    val setId: String = checkNotNull(savedStateHandle[ARG_SET_ID]) { "Missing $ARG_SET_ID" }

    private val _uiState = MutableStateFlow(DiceSetEditUiState())
    val uiState: StateFlow<DiceSetEditUiState> = _uiState.asStateFlow()

    private val _events = Channel<DiceSetEditEvent>(Channel.BUFFERED)
    val events: Flow<DiceSetEditEvent> = _events.receiveAsFlow()

    private var nameEdited = false
    private var renameJob: Job? = null
    private var pendingName: String? = null

    init {
        viewModelScope.launch {
            repository.details(setId).collect { details ->
                if (details == null || details.info.builtIn) {
                    _uiState.update { it.copy(loading = false, missing = true) }
                    return@collect
                }
                _uiState.update {
                    it.copy(
                        loading = false,
                        missing = false,
                        // Don't overwrite what the user is typing with the saved name.
                        name = if (nameEdited) it.name else details.manifest.name,
                        dice = entries(details.manifest),
                        skin = details.skin,
                    )
                }
            }
        }
    }

    fun onNameChange(name: String) {
        nameEdited = true
        val trimmedInput = name.take(MAX_NAME_LENGTH)
        _uiState.update { it.copy(name = trimmedInput) }
        renameJob?.cancel()
        if (trimmedInput.isBlank()) return
        pendingName = trimmedInput
        renameJob = viewModelScope.launch {
            delay(RENAME_DEBOUNCE_MS)
            repository.rename(setId, trimmedInput)
            pendingName = null
        }
    }

    override fun onCleared() {
        // Leaving within the debounce window must not lose the new name.
        pendingName?.let { name ->
            @OptIn(DelicateCoroutinesApi::class)
            GlobalScope.launch(NonCancellable) { repository.rename(setId, name) }
        }
    }

    fun onImagePicked(key: String, slot: DieImageSlot, source: StreamSource) {
        if (_uiState.value.busy != null) return
        _uiState.update { it.copy(busy = key to slot) }
        viewModelScope.launch {
            try {
                val result = repository.setDieImage(setId, key, slot, source)
                if (result != DieImageResult.OK) _events.send(DiceSetEditEvent.ImageFailed(result))
            } finally {
                _uiState.update { it.copy(busy = null) }
            }
        }
    }

    fun onRemoveImage(key: String, slot: DieImageSlot) {
        viewModelScope.launch { repository.removeDieImage(setId, key, slot) }
    }

    private fun entries(manifest: DiceSetManifest): List<DieEntry> = DiceSetFormat.DIE_KEYS.map { key ->
        DieEntry(
            key = key,
            has2d = DiceSetEditor.imagePath(manifest, key, DieImageSlot.IMAGE_2D) != null,
            has3d = DiceSetEditor.imagePath(manifest, key, DieImageSlot.FACE_3D) != null,
            supports3d = DiceSetEditor.supports(key, DieImageSlot.FACE_3D),
        )
    }

    companion object {
        /** Matches the property name in `DiceSetEditRoute`. */
        const val ARG_SET_ID = "setId"
        const val MAX_NAME_LENGTH = 40
        private const val RENAME_DEBOUNCE_MS = 400L
    }
}
