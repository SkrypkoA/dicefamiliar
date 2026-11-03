package com.skrypko.dicefamiliar.ui.pool.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.skrypko.dicefamiliar.data.pool.PoolPickStateRepository
import com.skrypko.dicefamiliar.data.pool.PoolRepository
import com.skrypko.dicefamiliar.data.pool.PoolSummary
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class PoolListUiState(
    val pools: List<PoolSummary> = emptyList(),
    val loaded: Boolean = false,
    val imageDir: File = File(""),
)

@HiltViewModel
class PoolListViewModel @Inject constructor(
    private val repository: PoolRepository,
    private val pickState: PoolPickStateRepository,
) : ViewModel() {

    val uiState: StateFlow<PoolListUiState> = repository.pools
        .map { PoolListUiState(it, loaded = true, imageDir = repository.imageDir) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PoolListUiState(imageDir = repository.imageDir))

    init {
        viewModelScope.launch { repository.cleanUpImages() }
    }

    fun delete(id: Long) = viewModelScope.launch {
        repository.delete(id)
        pickState.clear(id)
    }
}
