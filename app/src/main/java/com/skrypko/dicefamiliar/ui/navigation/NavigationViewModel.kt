package com.skrypko.dicefamiliar.ui.navigation

import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.SharingStarted
import com.skrypko.dicefamiliar.data.lan.LanNavigation
import com.skrypko.dicefamiliar.data.settings.AppSettingsRepository
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
    private val settings: AppSettingsRepository,
    private val lanNavigation: LanNavigation,
) : ViewModel() {

    /** LAN play on: the LAN tab is shown. */
    val lanEnabled: StateFlow<Boolean> = settings.settings.map { it.lanEnabled }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    /** A roll-request notification was tapped: show the Dice tab (its screen opens the request). */
    val openRequest: StateFlow<Boolean> = lanNavigation.openRequest

    /** Tab to start on; null until loaded from storage. */
    private val _startTab = MutableStateFlow<TopLevelDestination?>(null)
    val startTab: StateFlow<TopLevelDestination?> = _startTab.asStateFlow()

    init {
        viewModelScope.launch {
            val saved = lastTab.load()
            val tab = TopLevelDestination.entries.firstOrNull { it.name == saved } ?: DEFAULT_TAB
            // The LAN tab only exists with LAN play on.
            _startTab.value = if (tab == TopLevelDestination.LAN && !settings.settings.first().lanEnabled) DEFAULT_TAB else tab
        }
    }

    /** The LAN tab's request card: same as a notification tap. */
    fun openRequestFromLan() = lanNavigation.requestOpened()

    fun onTabSelected(tab: TopLevelDestination) {
        viewModelScope.launch { lastTab.save(tab.name) }
    }

    companion object {
        val DEFAULT_TAB = TopLevelDestination.NUMBER
    }
}
