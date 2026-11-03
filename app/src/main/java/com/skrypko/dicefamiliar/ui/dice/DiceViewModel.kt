package com.skrypko.dicefamiliar.ui.dice

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.skrypko.dicefamiliar.data.dice.DiceSetRepository
import com.skrypko.dicefamiliar.data.dice.DiceStateRepository
import com.skrypko.dicefamiliar.data.remote.ParticipantRepository
import com.skrypko.dicefamiliar.data.settings.AppSettingsRepository
import com.skrypko.dicefamiliar.domain.dice.DiceLimits
import com.skrypko.dicefamiliar.domain.dice.DiceNotationParser
import com.skrypko.dicefamiliar.domain.dice.DiceRollAction
import com.skrypko.dicefamiliar.domain.dice.DiceRollPayload
import com.skrypko.dicefamiliar.domain.dice.EditResult
import com.skrypko.dicefamiliar.domain.dice.NotationEditor
import com.skrypko.dicefamiliar.domain.dice.NotationError
import com.skrypko.dicefamiliar.domain.dice.ParseResult
import com.skrypko.dicefamiliar.domain.dice.RollMode
import com.skrypko.dicefamiliar.domain.dice.RollOutcome
import com.skrypko.dicefamiliar.domain.remote.ActionOutcome
import com.skrypko.dicefamiliar.domain.remote.LocalActionExecutor
import com.skrypko.dicefamiliar.domain.remote.Participant
import com.skrypko.dicefamiliar.domain.settings.AppSettings
import com.skrypko.dicefamiliar.ui.common.Sound
import com.skrypko.dicefamiliar.ui.common.SoundPlayer
import com.skrypko.dicefamiliar.ui.dice.skin.DiceSkin
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** One finished roll, with who asked for it and who rolled (the same device for local rolls). */
data class RollRecord(
    val id: String,
    val notation: String,
    val mode: RollMode,
    val label: String?,
    val outcome: RollOutcome,
    val requestedBy: Participant,
    val rolledBy: Participant,
    val rolledAt: Long,
)

data class DiceUiState(
    val notation: String = "",
    val notationError: NotationError? = null,
    val mode: RollMode = RollMode.NORMAL,
    /** Normal/Advantage/Disadvantage apply only to a single plain die (any type). */
    val modeApplicable: Boolean = false,
    /** Whether the editable formula field is shown (hidden by default). */
    val formulaVisible: Boolean = false,
    /** Whether the dice panel (palette, formula, controls) is expanded. */
    val panelVisible: Boolean = true,
    val paletteCounts: Map<Int, Int> = emptyMap(),
    /** Sides of the custom die in the formula; the dN button adds this value until it is cleared. */
    val customSides: Int? = null,
    val isRolling: Boolean = false,
    /**
     * The roll being animated: already decided by the roller, so the 3D dice can land on
     * their real faces. Values are revealed in the UI only when the dice settle.
     */
    val pending: RollRecord? = null,
    val tumbleMillis: Int = 0,
    val rollCount: Int = 0,
    val latest: RollRecord? = null,
    val recent: List<RollRecord> = emptyList(),
    val settings: AppSettings = AppSettings(),
    val skin: DiceSkin = DiceSkin.Default,
) {
    val canRoll: Boolean get() = !isRolling && notation.isNotBlank() && notationError == null
}

sealed interface DiceEvent {
    /** A result became visible: haptic confirmation. */
    data object RollShown : DiceEvent

    /** User tried to roll an invalid formula: shake the field. */
    data object InvalidRoll : DiceEvent

    /** Palette long-press removed a die: light haptic tick. */
    data object DieRemoved : DiceEvent
}

@HiltViewModel
class DiceViewModel @Inject constructor(
    private val executor: LocalActionExecutor,
    private val settingsRepository: AppSettingsRepository,
    private val stateRepository: DiceStateRepository,
    private val participants: ParticipantRepository,
    private val soundPlayer: SoundPlayer,
    private val diceSets: DiceSetRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(DiceUiState())
    val uiState: StateFlow<DiceUiState> = _uiState.asStateFlow()

    private val _events = Channel<DiceEvent>(Channel.BUFFERED)
    val events: Flow<DiceEvent> = _events.receiveAsFlow()

    init {
        viewModelScope.launch {
            settingsRepository.settings.collect { settings -> _uiState.update { it.copy(settings = settings) } }
        }
        viewModelScope.launch {
            diceSets.activeSkin.collect { skin -> _uiState.update { it.copy(skin = skin) } }
        }
        viewModelScope.launch {
            val saved = stateRepository.load()
            _uiState.update { it.copy(
                    mode = saved.lastMode,
                    formulaVisible = saved.formulaVisible,
                    // With no dice selected the panel always opens, so the user can pick some.
                    panelVisible = saved.panelVisible || !NotationEditor.hasDice(saved.lastNotation),
                ) }
            if (_uiState.value.notation.isEmpty()) setNotation(saved.lastNotation, persist = false)
        }
    }

    fun onNotationChange(text: String) = setNotation(text.take(DiceLimits.MAX_INPUT_LENGTH))

    fun onDieTapped(sides: Int) = applyEdit(NotationEditor.addDie(_uiState.value.notation, sides))

    fun onDieLongPressed(sides: Int) {
        val before = _uiState.value.notation
        val edit = if (sides == CUSTOM_DIE) NotationEditor.removeCustomDie(before) else NotationEditor.removeDie(before, sides)
        applyEdit(edit)
        if (_uiState.value.notation != before) _events.trySend(DiceEvent.DieRemoved)
    }

    fun onModifier(delta: Int) = applyEdit(NotationEditor.adjustModifier(_uiState.value.notation, delta))

    fun onClear() = setNotation("")

    fun toggleFormula() {
        val visible = !_uiState.value.formulaVisible
        _uiState.update { it.copy(formulaVisible = visible) }
        viewModelScope.launch { stateRepository.saveFormulaVisible(visible) }
    }

    fun togglePanel() {
        val visible = !_uiState.value.panelVisible
        _uiState.update { it.copy(panelVisible = visible) }
        viewModelScope.launch { stateRepository.savePanelVisible(visible) }
    }

    fun onModeChange(mode: RollMode) {
        _uiState.update { it.copy(mode = mode) }
        viewModelScope.launch { stateRepository.saveMode(mode) }
    }

    /**
     * Rolls the current formula. [flingSpeed] (px/s of the swipe) only lengthens the tumble
     * animation; it never influences the result.
     */
    fun roll(flingSpeed: Float = 0f) {
        val state = _uiState.value
        if (state.isRolling) return
        val parsed = DiceNotationParser.parse(state.notation)
        if (parsed is ParseResult.Error) {
            _uiState.update { it.copy(notationError = parsed.error) }
            _events.trySend(DiceEvent.InvalidRoll)
            return
        }
        _uiState.update { it.copy(isRolling = true) }
        // The saved mode is kept for later, but only used when it applies to this formula.
        val expression = (parsed as ParseResult.Success).expression
        val mode = if (RollMode.isApplicable(expression)) state.mode else RollMode.NORMAL
        viewModelScope.launch {
            val self = participants.self()
            val payload = DiceRollPayload(state.notation, mode)
            val (request, outcome) = executor.execute(DiceRollAction, payload, self)
            if (outcome !is ActionOutcome.Completed) {
                val error = NotationError.fromRemoteCode((outcome as ActionOutcome.Failed).errorCode)
                _uiState.update { it.copy(isRolling = false, notationError = error ?: NotationError.UNEXPECTED_CHAR) }
                _events.trySend(DiceEvent.InvalidRoll)
                return@launch
            }
            val record = RollRecord(
                id = request.id,
                notation = outcome.result.outcome.expression.toNotation(),
                mode = mode,
                label = request.label,
                outcome = outcome.result.outcome,
                requestedBy = request.from,
                rolledBy = outcome.response.respondedBy,
                rolledAt = outcome.response.respondedAt,
            )
            val settings = _uiState.value.settings
            if (settings.soundsEnabled) soundPlayer.play(Sound.DICE_ROLL)
            if (settings.animationsEnabled) {
                val duration = tumbleMillis(flingSpeed)
                _uiState.update { it.copy(pending = record, tumbleMillis = duration, rollCount = it.rollCount + 1) }
                delay(duration.toLong())
            }
            _uiState.update {
                it.copy(
                    isRolling = false,
                    pending = null,
                    latest = record,
                    recent = (listOf(record) + it.recent).take(MAX_RECENT),
                    rollCount = it.rollCount + if (settings.animationsEnabled) 0 else 1,
                )
            }
            _events.trySend(DiceEvent.RollShown)
        }
    }

    private fun applyEdit(result: EditResult) {
        if (result.error != null) {
            _uiState.update { it.copy(notationError = result.error) }
        } else {
            setNotation(result.text)
        }
    }

    private fun setNotation(text: String, persist: Boolean = true) {
        val parsed = DiceNotationParser.parse(text)
        val error = if (text.isBlank()) null else (parsed as? ParseResult.Error)?.error
        _uiState.update {
            it.copy(
                notation = text,
                notationError = error,
                modeApplicable = (parsed as? ParseResult.Success)?.let { ok -> RollMode.isApplicable(ok.expression) }
                    ?: false,
                paletteCounts = DiceLimits.STANDARD_DICE.associateWith { sides -> NotationEditor.countOf(text, sides) } +
                    (CUSTOM_DIE to NotationEditor.countCustom(text)),
                customSides = NotationEditor.customSides(text),
            )
        }
        if (persist) viewModelScope.launch { stateRepository.saveNotation(text) }
    }

    companion object {
        /** Palette key for the dN (custom die) button. */
        const val CUSTOM_DIE = 0
        const val MAX_RECENT = 20
        const val MIN_TUMBLE_MS = 600
        const val MAX_TUMBLE_MS = 1200

        /** A swipe at or above this speed gives the longest tumble. */
        const val FAST_FLING_PX_PER_S = 8000f

        fun tumbleMillis(flingSpeed: Float): Int {
            val strength = (flingSpeed / FAST_FLING_PX_PER_S).coerceIn(0f, 1f)
            return MIN_TUMBLE_MS + ((MAX_TUMBLE_MS - MIN_TUMBLE_MS) * strength).toInt()
        }
    }
}
