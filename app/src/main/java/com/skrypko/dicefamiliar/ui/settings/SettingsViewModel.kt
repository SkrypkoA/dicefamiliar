package com.skrypko.dicefamiliar.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.skrypko.dicefamiliar.data.dice.DiceSetImportOutcome
import com.skrypko.dicefamiliar.data.dice.DiceSetInfo
import com.skrypko.dicefamiliar.data.dice.DiceSetRepository
import com.skrypko.dicefamiliar.data.dice.StreamSource
import com.skrypko.dicefamiliar.data.settings.AppSettingsRepository
import com.skrypko.dicefamiliar.domain.dice.skin.DiceSetImportError
import com.skrypko.dicefamiliar.domain.settings.AppSettings
import com.skrypko.dicefamiliar.domain.settings.DarkMode
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class DiceSetsUiState(
    val sets: List<DiceSetInfo> = emptyList(),
    val activeId: String = "default",
    /** An import is running; the import button is disabled. */
    val importing: Boolean = false,
)

sealed interface SettingsEvent {
    data class Imported(val outcome: DiceSetImportOutcome.Imported) : SettingsEvent
    data class ImportFailed(val error: DiceSetImportError) : SettingsEvent
    data class OpenDiceSet(val id: String) : SettingsEvent
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repository: AppSettingsRepository,
    private val diceSets: DiceSetRepository,
) : ViewModel() {

    val settings: StateFlow<AppSettings> = repository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), AppSettings())

    private val importing = MutableStateFlow(false)

    val diceSetsState: StateFlow<DiceSetsUiState> =
        combine(diceSets.sets, diceSets.activeSetId, importing) { sets, active, busy ->
            DiceSetsUiState(sets, active, busy)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), DiceSetsUiState())

    private val _events = Channel<SettingsEvent>(Channel.BUFFERED)
    val events: Flow<SettingsEvent> = _events.receiveAsFlow()

    fun setSounds(enabled: Boolean) = launch { repository.setSoundsEnabled(enabled) }
    fun setAnimations(enabled: Boolean) = launch { repository.setAnimationsEnabled(enabled) }
    fun setHaptics(enabled: Boolean) = launch { repository.setHapticsEnabled(enabled) }
    fun setQuickRoll(enabled: Boolean) = launch { repository.setQuickRoll(enabled) }
    fun setTheme(id: String) = launch { repository.setThemeId(id) }
    fun setDarkMode(mode: DarkMode) = launch { repository.setDarkMode(mode) }
    fun setDynamicColor(enabled: Boolean) = launch { repository.setDynamicColor(enabled) }

    fun selectDiceSet(id: String) = launch { diceSets.select(id) }

    fun deleteDiceSet(id: String) = launch { diceSets.delete(id) }

    fun createDiceSet(name: String) = launch {
        val id = diceSets.create(name)
        _events.send(SettingsEvent.OpenDiceSet(id))
    }

    fun importDiceSet(source: StreamSource, fileName: String?) {
        if (importing.value) return
        importing.value = true
        launch {
            try {
                val event = when (val outcome = diceSets.importArchive(source, fileName)) {
                    is DiceSetImportOutcome.Imported -> SettingsEvent.Imported(outcome)
                    is DiceSetImportOutcome.Failed -> SettingsEvent.ImportFailed(outcome.error)
                }
                _events.send(event)
            } finally {
                importing.value = false
            }
        }
    }

    private fun launch(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
