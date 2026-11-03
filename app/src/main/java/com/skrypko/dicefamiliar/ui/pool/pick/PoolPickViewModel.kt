package com.skrypko.dicefamiliar.ui.pool.pick

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.skrypko.dicefamiliar.data.pool.PoolPickState
import com.skrypko.dicefamiliar.data.pool.PoolPickStateRepository
import com.skrypko.dicefamiliar.data.pool.PoolRepository
import com.skrypko.dicefamiliar.data.settings.AppSettingsRepository
import com.skrypko.dicefamiliar.domain.pool.PickError
import com.skrypko.dicefamiliar.domain.pool.PickResult
import com.skrypko.dicefamiliar.domain.pool.Pool
import com.skrypko.dicefamiliar.domain.pool.PoolItem
import com.skrypko.dicefamiliar.domain.pool.PoolLimits
import com.skrypko.dicefamiliar.domain.pool.PoolPicker
import com.skrypko.dicefamiliar.domain.remote.IdGenerator
import com.skrypko.dicefamiliar.domain.settings.AppSettings
import com.skrypko.dicefamiliar.ui.common.Sound
import com.skrypko.dicefamiliar.ui.common.SoundPlayer
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import javax.inject.Inject
import kotlin.time.Clock
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** One Pick tap: the items picked, in order. */
data class PickRecord(val id: String, val items: List<PoolItem>, val pickedAt: Long)

data class PoolPickUiState(
    val loaded: Boolean = false,
    val pool: Pool? = null,
    val count: String = "1",
    val allowRepeats: Boolean = false,
    val removePicked: Boolean = false,
    val countError: PickError? = null,
    val isPicking: Boolean = false,
    val latest: PickRecord? = null,
    val recent: List<PickRecord> = emptyList(),
    val settings: AppSettings = AppSettings(),
    val imageDir: File = File(""),
) {
    /** The items a pick can return: with "Remove picked items" on, the ones not set aside. */
    val available: List<PoolItem> get() = pool?.items.orEmpty().let { items -> if (removePicked) items.filterNot { it.setAside } else items }
    val setAsideCount: Int get() = pool?.items.orEmpty().count { it.setAside }
    val maxCount: Int get() = if (allowRepeats) PoolLimits.MAX_PICKS else available.size.coerceIn(1, PoolLimits.MAX_PICKS)
    val countValue: Int? get() = count.toIntOrNull()
}

sealed interface PoolPickEvent {
    data object Shown : PoolPickEvent
    data object Invalid : PoolPickEvent
}

@HiltViewModel
class PoolPickViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: PoolRepository,
    private val pickState: PoolPickStateRepository,
    private val settingsRepository: AppSettingsRepository,
    private val picker: PoolPicker,
    private val soundPlayer: SoundPlayer,
    private val ids: IdGenerator,
    private val clock: Clock,
) : ViewModel() {
    private val poolId: Long = savedStateHandle.get<Long>(ARG_POOL_ID) ?: 0L

    private val _uiState = MutableStateFlow(PoolPickUiState(imageDir = repository.imageDir))
    val uiState: StateFlow<PoolPickUiState> = _uiState.asStateFlow()

    private val _events = Channel<PoolPickEvent>(Channel.BUFFERED)
    val events: Flow<PoolPickEvent> = _events.receiveAsFlow()

    init {
        viewModelScope.launch {
            settingsRepository.settings.collect { settings -> _uiState.update { it.copy(settings = settings) } }
        }
        viewModelScope.launch {
            val saved = pickState.load(poolId)
            _uiState.update { it.copy(count = saved.count, allowRepeats = saved.allowRepeats, removePicked = saved.removePicked) }
            repository.observePool(poolId).collect { pool ->
                _uiState.update { validated(it.copy(pool = pool, loaded = true)) }
            }
        }
    }

    fun onCountChange(text: String) = edit { it.copy(count = text.filter(Char::isDigit).take(COUNT_DIGITS)) }

    fun onCountStep(delta: Int) = edit { state ->
        val current = state.countValue ?: 1
        state.copy(count = (current + delta).coerceIn(1, state.maxCount).toString())
    }

    fun onAllowRepeatsChange(allow: Boolean) = edit { it.copy(allowRepeats = allow) }

    fun onRemovePickedChange(remove: Boolean) = edit { it.copy(removePicked = remove) }

    fun onShowNamesChange(show: Boolean) {
        viewModelScope.launch { repository.setShowNames(poolId, show) }
    }

    /** Puts every set-aside item back into the pool. */
    fun putBackAll() {
        viewModelScope.launch { repository.restoreAll(poolId) }
    }

    /** Decides the result now (the UI never shows randomness), then reveals it. */
    fun pick() {
        val state = _uiState.value
        if (state.isPicking || state.pool == null) return
        val count = state.countValue ?: 0
        when (val result = picker.pick(state.available, count, state.allowRepeats)) {
            is PickResult.Failed -> {
                _uiState.update { it.copy(countError = result.error) }
                _events.trySend(PoolPickEvent.Invalid)
            }
            is PickResult.Picked -> {
                val record = PickRecord(ids.newId(), result.items, clock.now().toEpochMilliseconds())
                _uiState.update { it.copy(isPicking = true, countError = null) }
                viewModelScope.launch {
                    val settings = _uiState.value.settings
                    if (settings.animationsEnabled) delay(REVEAL_MS)
                    if (settings.soundsEnabled) soundPlayer.play(Sound.NUMBER_GENERATED)
                    _uiState.update {
                        it.copy(isPicking = false, latest = record, recent = (listOf(record) + it.recent).take(MAX_RECENT))
                    }
                    _events.trySend(PoolPickEvent.Shown)
                    if (state.removePicked) repository.setAside(poolId, record.items.map { it.id }.distinct())
                }
            }
        }
    }

    /** Empties the history; the latest result stays on screen. */
    fun clearHistory() = _uiState.update { it.copy(recent = listOfNotNull(it.latest)) }

    private fun edit(change: (PoolPickUiState) -> PoolPickUiState) {
        _uiState.update { validated(change(it)) }
        val state = _uiState.value
        viewModelScope.launch { pickState.save(poolId, PoolPickState(state.count, state.allowRepeats, state.removePicked)) }
    }

    /** Live error for the count field (none for an empty pool: the screen shows its own message). */
    private fun validated(state: PoolPickUiState): PoolPickUiState {
        val error = PoolPicker.check(state.available.size, state.countValue ?: 0, state.allowRepeats)
            ?.takeIf { it != PickError.EMPTY_POOL }
        return state.copy(countError = error)
    }

    companion object {
        /** The [com.skrypko.dicefamiliar.ui.navigation.PoolPickRoute] argument. */
        const val ARG_POOL_ID = "poolId"
        const val REVEAL_MS = 450L
        const val MAX_RECENT = 20
        private const val COUNT_DIGITS = 3
    }
}
