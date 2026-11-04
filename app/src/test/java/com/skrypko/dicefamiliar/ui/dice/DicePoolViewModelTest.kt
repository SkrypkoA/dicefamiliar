package com.skrypko.dicefamiliar.ui.dice

import androidx.lifecycle.SavedStateHandle
import com.skrypko.dicefamiliar.data.dice.DiceState
import com.skrypko.dicefamiliar.domain.dice.DiceRollHandler
import com.skrypko.dicefamiliar.domain.dice.DiceRoller
import com.skrypko.dicefamiliar.domain.dice.NotationError
import com.skrypko.dicefamiliar.domain.dice.RollMode
import com.skrypko.dicefamiliar.domain.dice.pool.Comparison
import com.skrypko.dicefamiliar.domain.dice.pool.SuccessCriterion
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
class DicePoolViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val diceState = FakeDiceStateRepository()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.viewModel(vararg dice: Int): DiceViewModel {
        val json = Json { encodeDefaults = true }
        val clock = FixedClock()
        val registry = RemoteActionRegistry(listOf(DiceRollHandler(DiceRoller(ScriptedRandomSource(*dice)))), json, clock)
        val executor = LocalActionExecutor(registry, ActionCodec(json, SequentialIds(), clock))
        return DiceViewModel(
            executor,
            FakeAppSettingsRepository(AppSettings(animationsEnabled = false, soundsEnabled = false)),
            diceState,
            FakeParticipantRepository(),
            FakeSoundPlayer(),
            FakeDiceSetRepository(),
            SavedStateHandle(),
        ).also { runCurrent() }
    }

    private fun DiceViewModel.values() = uiState.value.pool!!.dice.map { it.value }

    /** To hit: 10d6, reroll the 1s, take the 5+ to wound, count the 4+. */
    @Test
    fun warhammerToHitRerollOnesToWound() = runTest(dispatcher) {
        val vm = viewModel(1, 1, 3, 4, 5, 5, 6, 6, 3, 4, /* reroll */ 1, 5, /* to wound */ 1, 4, 3, 5, 6)
        vm.setPoolMode(true)
        vm.onDieTapped(6)
        vm.onPoolCountChange("10")
        assertEquals("10d6", vm.uiState.value.notation)
        vm.roll()
        advanceUntilIdle()
        assertEquals(listOf(1, 1, 3, 4, 5, 5, 6, 6, 3, 4), vm.values())

        vm.addGroup(SuccessCriterion(1, Comparison.EQUAL))
        assertEquals(2, vm.uiState.value.selectedCount)
        vm.rerollGroup()
        advanceUntilIdle()
        assertEquals(listOf(1, 5, 3, 4, 5, 5, 6, 6, 3, 4), vm.values())
        assertEquals(PoolRollKind.REROLL, vm.uiState.value.latest?.pool?.kind)

        vm.addGroup(SuccessCriterion(5))
        assertEquals(listOf(5, 5, 5, 6, 6), vm.uiState.value.pool!!.matching(SuccessCriterion(5)).map { it.value }.sorted())
        assertEquals(5, vm.uiState.value.selectedCount)
        vm.rollGroup()
        advanceUntilIdle()
        assertEquals(listOf(1, 4, 3, 5, 6), vm.values())
        assertEquals("10d6", vm.uiState.value.notation)

        vm.addGroup(SuccessCriterion(4))
        assertEquals(3, vm.uiState.value.selectedCount)
        advanceUntilIdle()
        assertEquals(listOf(SuccessCriterion(1, Comparison.EQUAL), SuccessCriterion(5), SuccessCriterion(4)), diceState.state.poolGroups)
    }

    @Test
    fun poolModeKeepsOneDieTypeAndItsOwnFormula() = runTest(dispatcher) {
        diceState.state = DiceState(lastNotation = "2d6+1d4+3")
        val vm = viewModel()
        assertEquals("2d6+1d4+3", vm.uiState.value.notation)
        vm.setPoolMode(true)
        assertEquals("", vm.uiState.value.notation)
        vm.onDieTapped(6)
        vm.onDieTapped(6)
        vm.onDieTapped(8)
        assertEquals("1d8", vm.uiState.value.notation)
        vm.onPoolCountStep(+1)
        assertEquals("2d8", vm.uiState.value.notation)
        vm.onDieLongPressed(8)
        assertEquals("1d8", vm.uiState.value.notation)
        advanceUntilIdle()
        assertEquals(true, diceState.state.poolMode)
        assertEquals("1d8", diceState.state.poolNotation)
        vm.setPoolMode(false)
        assertEquals("2d6+1d4+3", vm.uiState.value.notation)
        assertEquals("2d6+1d4+3", diceState.state.lastNotation)
    }

    @Test
    fun groupsToggleOneAtATimeAndActionsNeedDice() = runTest(dispatcher) {
        val vm = viewModel(2, 3)
        vm.setPoolMode(true)
        vm.onDieTapped(6)
        vm.onDieTapped(6)
        vm.roll()
        advanceUntilIdle()
        vm.addGroup(SuccessCriterion(6))
        assertEquals(0, vm.uiState.value.selectedCount)
        assertEquals(false, vm.uiState.value.canActOnGroup)
        val id = vm.uiState.value.poolGroups.selectedId!!
        vm.toggleGroup(id)
        assertNull(vm.uiState.value.selectedCriterion)
        vm.addGroup(SuccessCriterion(9))
        assertEquals(SuccessCriterion(6), vm.uiState.value.selectedCriterion)
    }

    @Test
    fun swipeWithAGroupSelectedRollsOnlyThatGroup() = runTest(dispatcher) {
        val vm = viewModel(1, 4, 6, /* swipe rerolls the =1 group */ 3)
        vm.setPoolMode(true)
        vm.onDieTapped(6)
        vm.onPoolCountChange("3")
        vm.roll()
        advanceUntilIdle()
        vm.addGroup(SuccessCriterion(1, Comparison.EQUAL))
        vm.roll(flingSpeed = 3000f)
        advanceUntilIdle()
        assertEquals(listOf(3, 4, 6), vm.values())
        assertEquals(PoolRollKind.REROLL, vm.uiState.value.latest?.pool?.kind)
        // Nothing left in the group: a swipe doesn't roll the whole pool either.
        vm.roll()
        advanceUntilIdle()
        assertEquals(listOf(3, 4, 6), vm.values())
    }

    @Test
    fun clearEmptiesThePoolButKeepsTheGroups() = runTest(dispatcher) {
        val vm = viewModel(5, 2, 6)
        vm.setPoolMode(true)
        vm.onDieTapped(6)
        vm.onPoolCountChange("3")
        vm.roll()
        advanceUntilIdle()
        vm.addGroup(SuccessCriterion(5))
        assertEquals(2, vm.uiState.value.selectedCount)
        vm.onClear()
        advanceUntilIdle()
        val state = vm.uiState.value
        assertEquals("", state.notation)
        assertEquals("", state.poolCountText)
        assertNull(state.pool)
        assertNull(state.latest)
        assertNull(state.selectedCriterion)
        assertEquals(listOf(SuccessCriterion(5)), state.poolGroups.groups.map { it.criterion })
        assertEquals("", diceState.state.poolNotation)
    }
}
