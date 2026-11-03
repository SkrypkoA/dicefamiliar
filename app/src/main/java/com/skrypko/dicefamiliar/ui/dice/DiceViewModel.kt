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
import com.skrypko.dicefamiliar.domain.dice.pool.DicePool
import com.skrypko.dicefamiliar.domain.dice.pool.PoolGroups
import com.skrypko.dicefamiliar.domain.dice.pool.PoolNotation
import com.skrypko.dicefamiliar.domain.dice.pool.SuccessCriterion
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
    /** Set for dice pool rolls: what was done and the whole pool afterwards. */
    val pool: PoolRollInfo? = null,
)

/** What a dice pool roll did. */
enum class PoolRollKind {
    /** The whole pool from the panel formula. */
    ROLL,

    /** The selected group's dice rerolled in place. */
    REROLL,

    /** A new pool with as many dice as the selected group had. */
    ROLL_GROUP,
}

/** A pool roll: its [kind], the group it acted on, the resulting [pool] and the group selected afterwards. */
data class PoolRollInfo(val kind: PoolRollKind, val criterion: SuccessCriterion?, val pool: DicePool, val selected: SuccessCriterion?)

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
    /** Dice pool mode: one die type, success groups, no sum. */
    val poolMode: Boolean = false,
    /** The current pool (shown as a grid once rolled). */
    val pool: DicePool? = null,
    val poolGroups: PoolGroups = PoolGroups(),
    /** The "How many" field in pool mode, as typed. */
    val poolCountText: String = "",
) {
    val canRoll: Boolean get() = !isRolling && notation.isNotBlank() && notationError == null

    /** The selected group's criterion, if any. */
    val selectedCriterion: SuccessCriterion? get() = poolGroups.selected?.criterion

    /** How many dice of the current pool the selected group holds. */
    val selectedCount: Int get() = selectedCriterion?.let { pool?.successes(it) } ?: 0

    /** Reroll / Roll group need a selected group with at least one die, and no roll in progress. */
    val canActOnGroup: Boolean get() = poolMode && !isRolling && selectedCount > 0
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
            val active = if (saved.poolMode) saved.poolNotation else saved.lastNotation
            otherNotation = if (saved.poolMode) saved.lastNotation else saved.poolNotation
            var groups = PoolGroups()
            saved.poolGroups.forEach { groups = groups.add(it) }
            _uiState.update { it.copy(
                    mode = saved.lastMode,
                    formulaVisible = saved.formulaVisible,
                    // With no dice selected the panel always opens, so the user can pick some.
                    panelVisible = saved.panelVisible || !NotationEditor.hasDice(active),
                    poolMode = saved.poolMode,
                    poolGroups = groups.copy(selectedId = null),
                ) }
            if (_uiState.value.notation.isEmpty()) setNotation(active, persist = false)
        }
    }

    /** The formula of the mode that isn't shown (sum or pool), kept while the other one is in use. */
    private var otherNotation: String = ""

    fun onNotationChange(text: String) = setNotation(text.take(DiceLimits.MAX_INPUT_LENGTH))

    fun onDieTapped(sides: Int) {
        val state = _uiState.value
        if (state.poolMode) setNotation(PoolNotation.add(state.notation, sides)) else applyEdit(NotationEditor.addDie(state.notation, sides))
    }

    fun onDieLongPressed(sides: Int) {
        val before = _uiState.value.notation
        if (_uiState.value.poolMode) {
            val current = PoolNotation.parse(before)?.second
            val target = if (sides == CUSTOM_DIE) current?.takeIf { it !in DiceLimits.STANDARD_DICE } else sides
            if (target != null) setNotation(PoolNotation.remove(before, target))
        } else {
            val edit = if (sides == CUSTOM_DIE) NotationEditor.removeCustomDie(before) else NotationEditor.removeDie(before, sides)
            applyEdit(edit)
        }
        if (_uiState.value.notation != before) _events.trySend(DiceEvent.DieRemoved)
    }

    /** Switches between summing a formula and the dice pool; each mode keeps its own formula. */
    fun setPoolMode(enabled: Boolean) {
        val state = _uiState.value
        if (state.poolMode == enabled || state.isRolling) return
        val next = otherNotation
        otherNotation = state.notation
        _uiState.update { it.copy(poolMode = enabled, latest = null, pending = null) }
        setNotation(next, persist = false)
        viewModelScope.launch { stateRepository.savePoolMode(enabled) }
    }

    /** Pool mode "How many" field: digits only; a valid number sets the pool size (1–100). */
    fun onPoolCountChange(text: String) {
        val digits = text.filter(Char::isDigit).take(3)
        _uiState.update { it.copy(poolCountText = digits) }
        digits.toIntOrNull()?.takeIf { it >= 1 }?.let { setNotation(PoolNotation.withCount(_uiState.value.notation, it)) }
    }

    fun onPoolCountStep(delta: Int) {
        val (count, _) = PoolNotation.parse(_uiState.value.notation) ?: return
        setNotation(PoolNotation.withCount(_uiState.value.notation, count + delta))
    }

    // --- Pool groups ------------------------------------------------------------------------------

    fun addGroup(criterion: SuccessCriterion) = editGroups { it.add(criterion.clampTo(poolSides())) }

    fun removeGroup(id: Int) = editGroups { it.remove(id) }

    /** Selects a group (or unselects it when it is already selected); one at a time. */
    fun toggleGroup(id: Int) = _uiState.update { it.copy(poolGroups = it.poolGroups.toggle(id)) }

    /** Rerolls the selected group's dice in place; the other dice keep their values. */
    fun rerollGroup() = actOnGroup(PoolRollKind.REROLL)

    /** Rolls a new pool with as many dice as the selected group holds. */
    fun rollGroup() = actOnGroup(PoolRollKind.ROLL_GROUP)

    private fun actOnGroup(kind: PoolRollKind, flingSpeed: Float = 0f) {
        val state = _uiState.value
        val pool = state.pool ?: return
        val criterion = state.selectedCriterion ?: return
        if (!state.canActOnGroup) return
        val dice = pool.matching(criterion)
        rollPool(PoolNotation.format(dice.size, pool.sides), kind, criterion, flingSpeed) { values ->
            when (kind) {
                PoolRollKind.REROLL -> pool.withValues(dice.map { it.id }.zip(values).toMap())
                else -> DicePool.of(pool.sides, values)
            }
        }
    }

    private fun poolSides(): Int = _uiState.value.pool?.sides ?: PoolNotation.parse(_uiState.value.notation)?.second ?: DiceLimits.MAX_SIDES

    private fun editGroups(change: (PoolGroups) -> PoolGroups) {
        _uiState.update { it.copy(poolGroups = change(it.poolGroups)) }
        val criteria = _uiState.value.poolGroups.groups.map { it.criterion }
        viewModelScope.launch { stateRepository.savePoolGroups(criteria) }
    }

    fun onModifier(delta: Int) = applyEdit(NotationEditor.adjustModifier(_uiState.value.notation, delta))

    /**
     * Empties the formula. In pool mode it also clears the rolled pool and the group selection, so the tray
     * starts over; the groups themselves (5+, =1, …) are kept.
     */
    fun onClear() {
        if (_uiState.value.isRolling) return
        if (_uiState.value.poolMode) {
            _uiState.update { it.copy(pool = null, latest = null, poolGroups = it.poolGroups.copy(selectedId = null)) }
        }
        setNotation("")
    }

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
        if (state.poolMode) {
            // With a group selected, a swipe (or Roll) rolls only that group's dice, in place.
            if (state.selectedCriterion != null && state.pool != null) {
                if (state.canActOnGroup) actOnGroup(PoolRollKind.REROLL, flingSpeed) else _events.trySend(DiceEvent.InvalidRoll)
                return
            }
            val (_, sides) = PoolNotation.parse(state.notation) ?: run {
                _events.trySend(DiceEvent.InvalidRoll)
                return
            }
            rollPool(state.notation, PoolRollKind.ROLL, null, flingSpeed) { values -> DicePool.of(sides, values) }
            return
        }
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
            reveal(record, flingSpeed)
        }
    }

    /**
     * Rolls [notation] (`kdS`) through the remote-action layer, like every roll, then builds the new pool from
     * the rolled values with [result]. The tumble shows only the dice being rolled.
     */
    private fun rollPool(
        notation: String,
        kind: PoolRollKind,
        criterion: SuccessCriterion?,
        flingSpeed: Float = 0f,
        result: (List<Int>) -> DicePool,
    ) {
        _uiState.update { it.copy(isRolling = true) }
        viewModelScope.launch {
            val self = participants.self()
            val (request, outcome) = executor.execute(DiceRollAction, DiceRollPayload(notation, RollMode.NORMAL), self)
            if (outcome !is ActionOutcome.Completed) {
                _uiState.update { it.copy(isRolling = false) }
                _events.trySend(DiceEvent.InvalidRoll)
                return@launch
            }
            val rolled = outcome.result.outcome
            val pool = result(rolled.groups.flatMap { group -> group.dice.map { it.value } })
            val record = RollRecord(
                id = request.id,
                notation = rolled.expression.toNotation(),
                mode = RollMode.NORMAL,
                label = request.label,
                outcome = rolled,
                requestedBy = request.from,
                rolledBy = outcome.response.respondedBy,
                rolledAt = outcome.response.respondedAt,
                pool = PoolRollInfo(kind, criterion, pool, _uiState.value.selectedCriterion),
            )
            reveal(record, flingSpeed) { it.copy(pool = pool) }
        }
    }

    /** Plays the sound and the tumble (when animations are on), then shows [record]. */
    private suspend fun reveal(record: RollRecord, flingSpeed: Float, extra: (DiceUiState) -> DiceUiState = { it }) {
        val settings = _uiState.value.settings
        if (settings.soundsEnabled) soundPlayer.play(Sound.DICE_ROLL)
        if (settings.animationsEnabled) {
            val duration = tumbleMillis(flingSpeed)
            _uiState.update { it.copy(pending = record, tumbleMillis = duration, rollCount = it.rollCount + 1) }
            delay(duration.toLong())
        }
        _uiState.update {
            extra(
                it.copy(
                    isRolling = false,
                    pending = null,
                    latest = record,
                    recent = (listOf(record) + it.recent).take(MAX_RECENT),
                    rollCount = it.rollCount + if (settings.animationsEnabled) 0 else 1,
                ),
            )
        }
        _events.trySend(DiceEvent.RollShown)
    }

    private fun applyEdit(result: EditResult) {
        if (result.error != null) {
            _uiState.update { it.copy(notationError = result.error) }
        } else {
            setNotation(result.text)
        }
    }

    private fun setNotation(text: String, persist: Boolean = true) {
        val poolMode = _uiState.value.poolMode
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
                poolCountText = PoolNotation.parse(text)?.first?.toString().orEmpty(),
            )
        }
        if (persist) {
            viewModelScope.launch { if (poolMode) stateRepository.savePoolNotation(text) else stateRepository.saveNotation(text) }
        }
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
