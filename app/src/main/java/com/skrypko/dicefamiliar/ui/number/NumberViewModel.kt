package com.skrypko.dicefamiliar.ui.number

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

data class NumberUiState(val placeholder: Boolean = true)

@HiltViewModel
class NumberViewModel @Inject constructor() : ViewModel() {
    private val _uiState = MutableStateFlow(NumberUiState())
    val uiState: StateFlow<NumberUiState> = _uiState.asStateFlow()
}
