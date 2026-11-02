package com.skrypko.dicefamiliar.ui.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.skrypko.dicefamiliar.data.navigation.LastTabRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class NavigationViewModel @Inject constructor(
    private val lastTab: LastTabRepository,
) : ViewModel() {

    /** Tab to start on; null until loaded from storage. */
    private val _startTab = MutableStateFlow<TopLevelDestination?>(null)
    val startTab: StateFlow<TopLevelDestination?> = _startTab.asStateFlow()

    init {
        viewModelScope.launch {
            val saved = lastTab.load()
            _startTab.value = TopLevelDestination.entries.firstOrNull { it.name == saved } ?: DEFAULT_TAB
        }
    }

    fun onTabSelected(tab: TopLevelDestination) {
        viewModelScope.launch { lastTab.save(tab.name) }
    }

    companion object {
        val DEFAULT_TAB = TopLevelDestination.NUMBER
    }
}
