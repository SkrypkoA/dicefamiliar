package com.skrypko.dicefamiliar.ui.number

import com.skrypko.dicefamiliar.data.number.NumberState
import com.skrypko.dicefamiliar.domain.number.NumberError
import com.skrypko.dicefamiliar.domain.number.NumberField
import com.skrypko.dicefamiliar.domain.number.NumberGenerator
import com.skrypko.dicefamiliar.domain.number.NumberMode
import com.skrypko.dicefamiliar.domain.number.NumberResult
import com.skrypko.dicefamiliar.domain.random.SeededRandomSource
import com.skrypko.dicefamiliar.domain.settings.AppSettings
import com.skrypko.dicefamiliar.testing.FakeAppSettingsRepository
import com.skrypko.dicefamiliar.testing.FakeNumberStateRepository
import com.skrypko.dicefamiliar.testing.FakeSoundPlayer
import com.skrypko.dicefamiliar.testing.FixedClock
import com.skrypko.dicefamiliar.testing.SequentialIds
import com.skrypko.dicefamiliar.ui.common.Sound
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class NumberViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val sound = FakeSoundPlayer()
    private val repo = FakeNumberStateRepository()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.viewModel(settings: AppSettings = AppSettings()) = NumberViewModel(
        NumberGenerator(SeededRandomSource()),
        FakeAppSettingsRepository(settings),
        repo,
        sound,
        SequentialIds(),
        FixedClock(5_000),
    ).also { runCurrent() }

    @Test
    fun defaults_areOneToHundred() = runTest(dispatcher) {
        val state = viewModel().uiState.value
        assertEquals("1", state.min)
        assertEquals("100", state.max)
        assertEquals("1", state.count)
        assertEquals(NumberMode.INTEGER, state.mode)
        assertTrue(state.errors.isEmpty())
        assertNull(state.latest)
    }

    @Test
    fun restoresLastSelectedValues_andLastResult() = runTest(dispatcher) {
        val last = NumberResult("old", listOf(250), 2, 0, 1000, true, 1)
        repo.state = NumberState("0", "10", "2", false, NumberMode.DECIMAL, 2, last)
        val state = viewModel().uiState.value
        assertEquals("0", state.min)
        assertEquals("10", state.max)
        assertEquals("2", state.count)
        assertEquals(false, state.allowDuplicates)
        assertEquals(NumberMode.DECIMAL, state.mode)
        assertEquals(last, state.latest)
        assertEquals(listOf(last), state.recent)
    }

    @Test
    fun edits_areValidatedAndSaved() = runTest(dispatcher) {
        val vm = viewModel()
        vm.onMaxChange("0")
        vm.onCountChange("5x")
        runCurrent()
        assertEquals(NumberError.MIN_ABOVE_MAX, vm.uiState.value.errors[NumberField.MAX])
        assertEquals("5", vm.uiState.value.count)
        assertEquals("0", repo.state.max)
        assertEquals("5", repo.state.count)
        vm.onMaxChange("50")
        vm.onModeChange(NumberMode.DECIMAL)
        vm.onDecimalsChange(9)
        runCurrent()
        assertTrue(vm.uiState.value.errors.isEmpty())
        assertEquals(NumberMode.DECIMAL, repo.state.mode)
        assertEquals(6, repo.state.decimals)
    }

    @Test
    fun steppers_changeFields_andAreSaved() = runTest(dispatcher) {
        val vm = viewModel()
        vm.onMaxStep(1)
        vm.onMinStep(-1)
        vm.onCountStep(1)
        vm.onCountStep(-1)
        vm.onCountStep(-1)
        runCurrent()
        val state = vm.uiState.value
        assertEquals("101", state.max)
        assertEquals("0", state.min)
        assertEquals("1", state.count)
        assertEquals("101", repo.state.max)
        vm.onModeChange(NumberMode.DECIMAL)
        vm.onMinChange("0.25")
        vm.onMinStep(1)
        assertEquals("1.25", vm.uiState.value.min)
    }

    @Test
    fun invalidGenerate_sendsInvalid_andProducesNothing() = runTest(dispatcher) {
        val vm = viewModel()
        val events = mutableListOf<NumberEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.events.collect { events += it } }
        vm.onCountChange("101")
        vm.onAllowDuplicatesChange(false)
        vm.generate()
        advanceUntilIdle()
        assertEquals(listOf<NumberEvent>(NumberEvent.Invalid), events)
        assertEquals(NumberError.COUNT_EXCEEDS_RANGE, vm.uiState.value.errors[NumberField.COUNT])
        assertNull(vm.uiState.value.latest)
        assertTrue(sound.played.isEmpty())
    }

    @Test
    fun generate_revealsAfterAnimation_playsSound_andRemembersResult() = runTest(dispatcher) {
        val vm = viewModel()
        val events = mutableListOf<NumberEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.events.collect { events += it } }
        vm.onCountChange("3")
        vm.generate()
        runCurrent()
        assertTrue(vm.uiState.value.isGenerating)
        assertNull(vm.uiState.value.latest)
        advanceTimeBy(NumberViewModel.REVEAL_MS + 1)
        val state = vm.uiState.value
        val latest = state.latest!!
        assertEquals(false, state.isGenerating)
        assertEquals(3, latest.values.size)
        assertTrue(latest.values.all { it in 1L..100L })
        assertEquals(5_000L, latest.generatedAt)
        assertEquals(listOf(latest), state.recent)
        assertEquals(listOf(Sound.NUMBER_GENERATED), sound.played)
        assertEquals(listOf<NumberEvent>(NumberEvent.Shown), events)
        assertEquals(latest, repo.state.lastResult)
    }

    @Test
    fun withoutAnimationsOrSound_resultIsImmediate_andSilent() = runTest(dispatcher) {
        val vm = viewModel(AppSettings(soundsEnabled = false, animationsEnabled = false))
        vm.generate()
        runCurrent()
        assertEquals(1, vm.uiState.value.latest?.values?.size)
        assertTrue(sound.played.isEmpty())
    }

    @Test
    fun history_keepsNewestFirst_andClearKeepsLatest() = runTest(dispatcher) {
        val vm = viewModel(AppSettings(animationsEnabled = false))
        repeat(3) {
            vm.generate()
            runCurrent()
        }
        val state = vm.uiState.value
        assertEquals(listOf("id-3", "id-2", "id-1"), state.recent.map { it.id })
        vm.clearHistory()
        assertEquals(listOf("id-3"), vm.uiState.value.recent.map { it.id })
        assertEquals("id-3", vm.uiState.value.latest?.id)
    }

    @Test
    fun history_isCapped() = runTest(dispatcher) {
        val vm = viewModel(AppSettings(animationsEnabled = false))
        repeat(NumberViewModel.MAX_RECENT + 5) {
            vm.generate()
            runCurrent()
        }
        assertEquals(NumberViewModel.MAX_RECENT, vm.uiState.value.recent.size)
    }

    @Test
    fun panelVisibilityIsRememberedAndSurvivesInputSaves() = runTest(dispatcher) {
        repo.state = NumberState(panelVisible = false)
        val vm = viewModel()
        assertEquals(false, vm.uiState.value.panelVisible)
        vm.togglePanel()
        vm.onCountChange("3")
        advanceUntilIdle()
        assertEquals(true, vm.uiState.value.panelVisible)
        assertEquals(true, repo.state.panelVisible)
    }
}
