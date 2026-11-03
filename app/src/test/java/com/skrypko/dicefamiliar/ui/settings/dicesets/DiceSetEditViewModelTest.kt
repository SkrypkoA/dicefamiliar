package com.skrypko.dicefamiliar.ui.settings.dicesets

import androidx.lifecycle.SavedStateHandle
import com.skrypko.dicefamiliar.data.dice.DieImageResult
import com.skrypko.dicefamiliar.domain.dice.skin.DieImageSlot
import com.skrypko.dicefamiliar.testing.FakeDiceSetRepository
import java.io.ByteArrayInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DiceSetEditViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val repository = FakeDiceSetRepository()
    private val bytes = { ByteArrayInputStream(byteArrayOf(1, 2, 3)) }

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private suspend fun viewModel(name: String = "Wood"): DiceSetEditViewModel {
        val id = repository.create(name)
        return DiceSetEditViewModel(SavedStateHandle(mapOf(DiceSetEditViewModel.ARG_SET_ID to id)), repository)
    }

    @Test
    fun loadsAllDice() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()
        val state = vm.uiState.value
        assertFalse(state.loading)
        assertEquals("Wood", state.name)
        assertEquals(listOf("d4", "d6", "d8", "d10", "d12", "d20", "d100", "dN"), state.dice.map { it.key })
        assertFalse(state.dice.first { it.key == "dN" }.supports3d)
        assertTrue(state.dice.first { it.key == "d6" }.supports3d)
    }

    @Test
    fun uploadAndRemoveImages() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()

        vm.onImagePicked("d6", DieImageSlot.IMAGE_2D, bytes)
        vm.onImagePicked("d20", DieImageSlot.FACE_3D, bytes) // ignored while busy
        advanceUntilIdle()
        vm.onImagePicked("d20", DieImageSlot.FACE_3D, bytes)
        advanceUntilIdle()

        val dice = vm.uiState.value.dice.associateBy { it.key }
        assertTrue(dice.getValue("d6").has2d)
        assertTrue(dice.getValue("d20").has3d)
        assertFalse(dice.getValue("d20").has2d)
        assertNull(vm.uiState.value.busy)

        vm.onRemoveImage("d6", DieImageSlot.IMAGE_2D)
        advanceUntilIdle()
        assertFalse(vm.uiState.value.dice.first { it.key == "d6" }.has2d)
    }

    @Test
    fun failedUploadSendsEvent() = runTest(dispatcher) {
        val vm = viewModel()
        val events = mutableListOf<DiceSetEditEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.events.collect { events += it } }
        advanceUntilIdle()
        repository.imageResult = DieImageResult.TOO_LARGE

        vm.onImagePicked("d6", DieImageSlot.IMAGE_2D, bytes)
        advanceUntilIdle()

        assertEquals(listOf<DiceSetEditEvent>(DiceSetEditEvent.ImageFailed(DieImageResult.TOO_LARGE)), events)
        assertFalse(vm.uiState.value.dice.first { it.key == "d6" }.has2d)
    }

    @Test
    fun renameIsDebounced() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()

        vm.onNameChange("Oak")
        vm.onNameChange("Oak Dice")
        advanceTimeBy(100)
        assertEquals("Wood", repository.manifests.value.getValue("wood").name)
        advanceUntilIdle()

        assertEquals("Oak Dice", repository.manifests.value.getValue("wood").name)
        assertEquals("Oak Dice", vm.uiState.value.name)
    }

    @Test
    fun blankNameIsNotSaved() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()
        vm.onNameChange("  ")
        advanceUntilIdle()
        assertEquals("Wood", repository.manifests.value.getValue("wood").name)
    }

    @Test
    fun builtInOrDeletedSetIsMissing() = runTest(dispatcher) {
        val builtIn = DiceSetEditViewModel(
            SavedStateHandle(mapOf(DiceSetEditViewModel.ARG_SET_ID to "default")),
            repository,
        )
        val vm = viewModel()
        advanceUntilIdle()
        assertTrue(builtIn.uiState.value.missing)

        repository.delete("wood")
        advanceUntilIdle()
        assertTrue(vm.uiState.value.missing)
    }
}
