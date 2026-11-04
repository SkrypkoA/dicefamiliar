package com.skrypko.dicefamiliar.ui.dice

import androidx.lifecycle.SavedStateHandle
import com.skrypko.dicefamiliar.data.dice.DiceState
import com.skrypko.dicefamiliar.domain.dice.DiceRollHandler
import com.skrypko.dicefamiliar.domain.dice.DiceRoller
import com.skrypko.dicefamiliar.domain.dice.NotationError
import com.skrypko.dicefamiliar.domain.dice.RollMode
import com.skrypko.dicefamiliar.domain.random.SeededRandomSource
import com.skrypko.dicefamiliar.domain.remote.ActionCodec
import com.skrypko.dicefamiliar.domain.remote.LocalActionExecutor
import com.skrypko.dicefamiliar.domain.remote.RemoteActionRegistry
import com.skrypko.dicefamiliar.domain.settings.AppSettings
import com.skrypko.dicefamiliar.testing.FakeAppSettingsRepository
import com.skrypko.dicefamiliar.testing.FakeDiceSetRepository
import com.skrypko.dicefamiliar.testing.FakeDiceStateRepository
import com.skrypko.dicefamiliar.testing.FakeParticipantRepository
import com.skrypko.dicefamiliar.testing.FakeSoundPlayer
import com.skrypko.dicefamiliar.testing.FixedClock
import com.skrypko.dicefamiliar.testing.ScriptedRandomSource
import com.skrypko.dicefamiliar.testing.SequentialIds
import com.skrypko.dicefamiliar.ui.common.Sound
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DiceViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val sound = FakeSoundPlayer()
    private val diceState = FakeDiceStateRepository()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.viewModel(
        settings: AppSettings = AppSettings(),
        random: com.skrypko.dicefamiliar.domain.random.RandomSource = SeededRandomSource(),
    ): DiceViewModel {
        val json = Json { encodeDefaults = true }
        val clock = FixedClock()
        val registry = RemoteActionRegistry(listOf(DiceRollHandler(DiceRoller(random))), json, clock)
        val executor = LocalActionExecutor(registry, ActionCodec(json, SequentialIds(), clock))
        return DiceViewModel(
            executor,
            FakeAppSettingsRepository(settings),
            diceState,
            FakeParticipantRepository(),
            sound,
            FakeDiceSetRepository(),
            SavedStateHandle(),
        ).also { runCurrent() }
    }

    @Test
    fun liveValidationShowsError() = runTest(dispatcher) {
        val vm = viewModel()
        vm.onNotationChange("3d6+")
        assertEquals(NotationError.INCOMPLETE, vm.uiState.value.notationError)
        assertFalse(vm.uiState.value.canRoll)
        vm.onNotationChange("3d6+1")
        assertNull(vm.uiState.value.notationError)
        assertTrue(vm.uiState.value.canRoll)
    }

    @Test
    fun paletteBuildsFormulaAndCounts() = runTest(dispatcher) {
        val vm = viewModel()
        vm.onDieTapped(6)
        vm.onDieTapped(6)
        vm.onDieTapped(4)
        vm.onModifier(1)
        assertEquals("2d6+1d4+1", vm.uiState.value.notation)
        assertEquals(2, vm.uiState.value.paletteCounts[6])
        assertEquals(1, vm.uiState.value.paletteCounts[4])
        vm.onDieLongPressed(6)
        assertEquals("1d6+1d4+1", vm.uiState.value.notation)
        vm.onClear()
        assertEquals("", vm.uiState.value.notation)
        advanceUntilIdle()
        assertEquals("", diceState.state.lastNotation)
    }

    @Test
    fun animatedRollWaitsForTumbleAndPlaysSound() = runTest(dispatcher) {
        val vm = viewModel(random = ScriptedRandomSource(4, 18))
        vm.onModeChange(RollMode.ADVANTAGE)
        vm.onNotationChange("d20")
        vm.roll(flingSpeed = 0f)
        runCurrent()

        val rolling = vm.uiState.value
        assertTrue(rolling.isRolling)
        // The result is decided before the tumble so the 3D dice can land on it.
        assertEquals(18, rolling.pending!!.outcome.total)
        assertEquals(listOf(4, 18), rolling.pending!!.outcome.groups.single().dice.map { it.value })
        assertEquals(DiceViewModel.MIN_TUMBLE_MS, rolling.tumbleMillis)
        assertNull(rolling.latest)
        assertEquals(listOf(Sound.DICE_ROLL), sound.played)

        advanceTimeBy(DiceViewModel.MIN_TUMBLE_MS.toLong() + 1)
        val done = vm.uiState.value
        assertFalse(done.isRolling)
        assertNull(done.pending)
        assertEquals(18, done.latest!!.outcome.total)
        assertEquals("2d20kh1", done.latest!!.notation)
        assertEquals("me", done.latest!!.rolledBy.id)
    }

    @Test
    fun fasterFlingTumblesLonger() {
        assertEquals(DiceViewModel.MIN_TUMBLE_MS, DiceViewModel.tumbleMillis(0f))
        assertTrue(DiceViewModel.tumbleMillis(4000f) > DiceViewModel.tumbleMillis(1000f))
        assertEquals(DiceViewModel.MAX_TUMBLE_MS, DiceViewModel.tumbleMillis(1_000_000f))
    }

    @Test
    fun withoutAnimationsResultIsImmediate() = runTest(dispatcher) {
        val vm = viewModel(AppSettings(animationsEnabled = false, soundsEnabled = false))
        val events = mutableListOf<DiceEvent>()
        backgroundScope.launch { vm.events.toList(events) }
        vm.onNotationChange("2d6")
        vm.roll()
        runCurrent()
        val state = vm.uiState.value
        assertFalse(state.isRolling)
        assertNull(state.pending)
        assertTrue(state.latest != null)
        assertTrue(sound.played.isEmpty())
        assertEquals(listOf<DiceEvent>(DiceEvent.RollShown), events)
    }

    @Test
    fun invalidRollEmitsEventAndDoesNotRoll() = runTest(dispatcher) {
        val vm = viewModel()
        val events = mutableListOf<DiceEvent>()
        backgroundScope.launch { vm.events.toList(events) }
        vm.onNotationChange("2d")
        vm.roll()
        runCurrent()
        assertNull(vm.uiState.value.latest)
        assertEquals(listOf<DiceEvent>(DiceEvent.InvalidRoll), events)
    }

    @Test
    fun recentIsCappedAndNewestFirst() = runTest(dispatcher) {
        val vm = viewModel(AppSettings(animationsEnabled = false))
        vm.onNotationChange("1d6")
        repeat(DiceViewModel.MAX_RECENT + 5) {
            vm.roll()
            runCurrent()
        }
        val state = vm.uiState.value
        assertEquals(DiceViewModel.MAX_RECENT, state.recent.size)
        assertEquals(state.latest, state.recent.first())
    }

    @Test
    fun modeAppliesOnlyToASingleDieOfAnyType() = runTest(dispatcher) {
        val vm = viewModel(AppSettings(animationsEnabled = false))
        vm.onModeChange(RollMode.ADVANTAGE)

        vm.onNotationChange("2d6")
        assertFalse(vm.uiState.value.modeApplicable)
        vm.roll()
        runCurrent()
        assertEquals("2d6", vm.uiState.value.latest!!.notation)
        assertEquals(RollMode.NORMAL, vm.uiState.value.latest!!.mode)

        vm.onNotationChange("1d12")
        assertTrue(vm.uiState.value.modeApplicable)
        vm.roll()
        runCurrent()
        assertEquals("2d12kh1", vm.uiState.value.latest!!.notation)
        assertEquals(RollMode.ADVANTAGE, vm.uiState.value.latest!!.mode)
        assertEquals(RollMode.ADVANTAGE, vm.uiState.value.mode)
    }

    @Test
    fun formulaHiddenByDefaultAndTogglePersists() = runTest(dispatcher) {
        val vm = viewModel()
        assertFalse(vm.uiState.value.formulaVisible)
        vm.toggleFormula()
        advanceUntilIdle()
        assertTrue(vm.uiState.value.formulaVisible)
        assertTrue(diceState.state.formulaVisible)
    }

    @Test
    fun customDieLongPressRemovesIt() = runTest(dispatcher) {
        val vm = viewModel()
        vm.onDieTapped(7)
        vm.onDieTapped(7)
        vm.onDieTapped(6)
        assertEquals("2d7+1d6", vm.uiState.value.notation)
        assertEquals(2, vm.uiState.value.paletteCounts[DiceViewModel.CUSTOM_DIE])
        vm.onDieLongPressed(DiceViewModel.CUSTOM_DIE)
        vm.onDieLongPressed(DiceViewModel.CUSTOM_DIE)
        assertEquals("1d6", vm.uiState.value.notation)
        assertEquals(0, vm.uiState.value.paletteCounts[DiceViewModel.CUSTOM_DIE])
    }

    @Test
    fun customDieValueIsKeptUntilAllCustomDiceAreRemoved() = runTest(dispatcher) {
        val vm = viewModel()
        assertNull(vm.uiState.value.customSides)
        vm.onDieTapped(7) // first dN tap: value chosen in the dialog
        assertEquals(7, vm.uiState.value.customSides)
        vm.onDieTapped(vm.uiState.value.customSides!!) // next dN taps reuse it
        assertEquals("2d7", vm.uiState.value.notation)
        vm.onDieLongPressed(DiceViewModel.CUSTOM_DIE)
        assertEquals(7, vm.uiState.value.customSides)
        vm.onDieLongPressed(DiceViewModel.CUSTOM_DIE)
        assertNull(vm.uiState.value.customSides) // all removed: a new value can be chosen
        vm.onDieTapped(9)
        vm.onClear()
        assertNull(vm.uiState.value.customSides)
    }

    @Test
    fun panelOpensWhenNoDiceSelectedEvenIfSavedHidden() = runTest(dispatcher) {
        diceState.state = DiceState(lastNotation = "", panelVisible = false)
        assertTrue(viewModel().uiState.value.panelVisible)
        diceState.state = DiceState(lastNotation = "+2", panelVisible = false)
        assertTrue(viewModel().uiState.value.panelVisible)
        diceState.state = DiceState(lastNotation = "2d6", panelVisible = false)
        assertFalse(viewModel().uiState.value.panelVisible)
    }

    @Test
    fun panelVisibleByDefaultAndTogglePersists() = runTest(dispatcher) {
        val vm = viewModel()
        assertTrue(vm.uiState.value.panelVisible)
        vm.togglePanel()
        advanceUntilIdle()
        assertFalse(vm.uiState.value.panelVisible)
        assertFalse(diceState.state.panelVisible)
    }

    @Test
    fun restoresSavedFormulaAndMode() = runTest(dispatcher) {
        diceState.state = DiceState("2d6+1", RollMode.DISADVANTAGE)
        val vm = viewModel()
        assertEquals("2d6+1", vm.uiState.value.notation)
        assertEquals(RollMode.DISADVANTAGE, vm.uiState.value.mode)
        assertEquals(2, vm.uiState.value.paletteCounts[6])
    }
}
