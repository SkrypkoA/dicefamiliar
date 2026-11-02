package com.skrypko.dicefamiliar.ui.pool.list

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

data class PoolListUiState(val placeholder: Boolean = true)

@HiltViewModel
class PoolListViewModel @Inject constructor() : ViewModel() {
    private val _uiState = MutableStateFlow(PoolListUiState())
    val uiState: StateFlow<PoolListUiState> = _uiState.asStateFlow()
}
