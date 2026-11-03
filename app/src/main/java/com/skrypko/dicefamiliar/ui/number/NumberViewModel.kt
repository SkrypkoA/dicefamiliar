package com.skrypko.dicefamiliar.ui.number

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.skrypko.dicefamiliar.data.number.NumberState
import com.skrypko.dicefamiliar.data.number.NumberStateRepository
import com.skrypko.dicefamiliar.data.settings.AppSettingsRepository
import com.skrypko.dicefamiliar.domain.number.NumberError
import com.skrypko.dicefamiliar.domain.number.NumberField
import com.skrypko.dicefamiliar.domain.number.NumberGenerator
import com.skrypko.dicefamiliar.domain.number.NumberInputParser
import com.skrypko.dicefamiliar.domain.number.NumberInputResult
import com.skrypko.dicefamiliar.domain.number.NumberLimits
import com.skrypko.dicefamiliar.domain.number.NumberMode
import com.skrypko.dicefamiliar.domain.number.NumberResult
import com.skrypko.dicefamiliar.domain.number.NumberStepper
import com.skrypko.dicefamiliar.domain.remote.IdGenerator
import com.skrypko.dicefamiliar.domain.settings.AppSettings
import com.skrypko.dicefamiliar.ui.common.Sound
import com.skrypko.dicefamiliar.ui.common.SoundPlayer
import dagger.hilt.android.lifecycle.HiltViewModel
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

data class NumberUiState(
    val min: String = NumberState().min,
    val max: String = NumberState().max,
    val count: String = NumberState().count,
    val allowDuplicates: Boolean = true,
    val mode: NumberMode = NumberMode.INTEGER,
    /** Decimal places used in decimal mode (kept while in integer mode). */
    val decimals: Int = NumberLimits.DEFAULT_DECIMALS,
    /** The first error of each field, re-validated on every change. */
    val errors: Map<NumberField, NumberError> = emptyMap(),
    /** The result is decided; the UI is running the reveal animation. */
    val isGenerating: Boolean = false,
    val latest: NumberResult? = null,
    /** Newest first; includes [latest]. */
    val recent: List<NumberResult> = emptyList(),
    val settings: AppSettings = AppSettings(),
)

sealed interface NumberEvent {
    /** A result became visible: haptic confirmation. */
    data object Shown : NumberEvent

    /** Generate was tapped with invalid input: reject haptic and shake. */
    data object Invalid : NumberEvent
}

@HiltViewModel
class NumberViewModel @Inject constructor(
    private val generator: NumberGenerator,
    private val settingsRepository: AppSettingsRepository,
    private val stateRepository: NumberStateRepository,
    private val soundPlayer: SoundPlayer,
    private val ids: IdGenerator,
    private val clock: Clock,
) : ViewModel() {

    private val _uiState = MutableStateFlow(NumberUiState())
    val uiState: StateFlow<NumberUiState> = _uiState.asStateFlow()

    private val _events = Channel<NumberEvent>(Channel.BUFFERED)
    val events: Flow<NumberEvent> = _events.receiveAsFlow()

    init {
        viewModelScope.launch {
            settingsRepository.settings.collect { settings -> _uiState.update { it.copy(settings = settings) } }
        }
        viewModelScope.launch {
            val saved = stateRepository.load()
            _uiState.update {
                validated(
                    it.copy(
                        min = saved.min,
                        max = saved.max,
                        count = saved.count,
                        allowDuplicates = saved.allowDuplicates,
                        mode = saved.mode,
                        decimals = saved.decimals,
                        latest = it.latest ?: saved.lastResult,
                        recent = it.recent.ifEmpty { listOfNotNull(saved.lastResult) },
                    ),
                )
            }
        }
    }

    fun onMinChange(text: String) = edit { it.copy(min = text.take(NumberLimits.MAX_INPUT_LENGTH)) }

    fun onMaxChange(text: String) = edit { it.copy(max = text.take(NumberLimits.MAX_INPUT_LENGTH)) }

    fun onCountChange(text: String) = edit { it.copy(count = text.filter(Char::isDigit).take(COUNT_DIGITS)) }

    /** Chevron tap or swipe on a field: one whole step up (+1) or down (-1). */
    fun onMinStep(delta: Int) = edit { it.copy(min = NumberStepper.stepBound(it.min, delta, it.mode)) }

    fun onMaxStep(delta: Int) = edit { it.copy(max = NumberStepper.stepBound(it.max, delta, it.mode)) }

    fun onCountStep(delta: Int) = edit { it.copy(count = NumberStepper.stepCount(it.count, delta)) }

    fun onAllowDuplicatesChange(allow: Boolean) = edit { it.copy(allowDuplicates = allow) }

    fun onModeChange(mode: NumberMode) = edit { it.copy(mode = mode) }

    fun onDecimalsChange(decimals: Int) =
        edit { it.copy(decimals = decimals.coerceIn(NumberLimits.MIN_DECIMALS, NumberLimits.MAX_DECIMALS)) }

    /** Decides the result now (the UI never shows randomness), then reveals it. */
    fun generate() {
        val state = _uiState.value
        if (state.isGenerating) return
        val parsed = parse(state)
        if (parsed is NumberInputResult.Invalid) {
            _uiState.update { it.copy(errors = parsed.errors.toFieldMap()) }
            _events.trySend(NumberEvent.Invalid)
            return
        }
        val spec = (parsed as NumberInputResult.Valid).spec
        val result = NumberResult(
            id = ids.newId(),
            values = generator.generate(spec),
            decimals = spec.decimals,
            min = spec.min,
            max = spec.max,
            allowDuplicates = spec.allowDuplicates,
            generatedAt = clock.now().toEpochMilliseconds(),
        )
        _uiState.update { it.copy(isGenerating = true) }
        viewModelScope.launch {
            val settings = _uiState.value.settings
            if (settings.animationsEnabled) delay(REVEAL_MS)
            if (settings.soundsEnabled) soundPlayer.play(Sound.NUMBER_GENERATED)
            _uiState.update {
                it.copy(
                    isGenerating = false,
                    latest = result,
                    recent = (listOf(result) + it.recent).take(MAX_RECENT),
                )
            }
            _events.trySend(NumberEvent.Shown)
            stateRepository.saveLastResult(result)
        }
    }

    /** Empties the history; the latest result stays on screen. */
    fun clearHistory() {
        _uiState.update { it.copy(recent = listOfNotNull(it.latest)) }
    }

    private fun edit(change: (NumberUiState) -> NumberUiState) {
        _uiState.update { validated(change(it)) }
        val state = _uiState.value
        viewModelScope.launch {
            stateRepository.save(
                NumberState(state.min, state.max, state.count, state.allowDuplicates, state.mode, state.decimals),
            )
        }
    }

    private fun validated(state: NumberUiState): NumberUiState {
        val parsed = parse(state)
        return state.copy(errors = (parsed as? NumberInputResult.Invalid)?.errors?.toFieldMap().orEmpty())
    }

    private fun parse(state: NumberUiState) =
        NumberInputParser.parse(state.min, state.max, state.count, state.mode, state.decimals, state.allowDuplicates)

    private fun List<NumberError>.toFieldMap(): Map<NumberField, NumberError> =
        groupBy { it.field }.mapValues { it.value.first() }

    companion object {
        const val MAX_RECENT = 20
        const val REVEAL_MS = 450L
        private const val COUNT_DIGITS = 4
    }
}
