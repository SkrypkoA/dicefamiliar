package com.skrypko.dicefamiliar.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.skrypko.dicefamiliar.data.settings.AppSettingsRepository
import com.skrypko.dicefamiliar.domain.settings.AppSettings
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repository: AppSettingsRepository,
) : ViewModel() {

    val settings: StateFlow<AppSettings> = repository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), AppSettings())

    fun setSounds(enabled: Boolean) = launch { repository.setSoundsEnabled(enabled) }
    fun setAnimations(enabled: Boolean) = launch { repository.setAnimationsEnabled(enabled) }
    fun setHaptics(enabled: Boolean) = launch { repository.setHapticsEnabled(enabled) }
    fun setQuickRoll(enabled: Boolean) = launch { repository.setQuickRoll(enabled) }

    private fun launch(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
