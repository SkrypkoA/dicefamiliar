package com.skrypko.dicefamiliar.ui.pool

import androidx.lifecycle.SavedStateHandle
import com.skrypko.dicefamiliar.data.pool.PoolImportOutcome
import com.skrypko.dicefamiliar.domain.pool.PickError
import com.skrypko.dicefamiliar.domain.pool.Pool
import com.skrypko.dicefamiliar.domain.pool.PoolError
import com.skrypko.dicefamiliar.domain.pool.PoolItem
import com.skrypko.dicefamiliar.domain.pool.PoolPicker
import com.skrypko.dicefamiliar.domain.pool.io.ImportedItem
import com.skrypko.dicefamiliar.domain.settings.AppSettings
import com.skrypko.dicefamiliar.testing.FakeAppSettingsRepository
import com.skrypko.dicefamiliar.testing.FakePoolPickStateRepository
import com.skrypko.dicefamiliar.testing.FakePoolRepository
import com.skrypko.dicefamiliar.testing.FakeSoundPlayer
import com.skrypko.dicefamiliar.testing.FixedClock
import com.skrypko.dicefamiliar.testing.ScriptedRandomSource
import com.skrypko.dicefamiliar.testing.SequentialIds
import com.skrypko.dicefamiliar.ui.common.Sound
import com.skrypko.dicefamiliar.ui.pool.edit.PoolEditEvent
import com.skrypko.dicefamiliar.ui.pool.edit.PoolEditViewModel
import com.skrypko.dicefamiliar.ui.pool.pick.PoolPickViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
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
class PoolEditViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val repo = FakePoolRepository()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.editor(poolId: Long = 0L) =
        PoolEditViewModel(SavedStateHandle(mapOf(PoolEditViewModel.ARG_POOL_ID to poolId)), repo).also { runCurrent() }

    private fun PoolEditViewModel.add(name: String) {
        addItem()
        onEditorNameChange(name)
        saveEditor()
    }

    @Test
    fun duplicateNameBlocksTheItem() = runTest(dispatcher) {
        val vm = editor()
        vm.add("Apple")
        vm.addItem()
        vm.onEditorNameChange(" apple ")
        assertEquals(PoolError.DUPLICATE_ITEM_NAME, vm.uiState.value.editor?.error)
        assertFalse(vm.uiState.value.editor!!.canSave)
        vm.saveEditor()
        assertEquals(1, vm.uiState.value.items.size)
    }

    @Test
    fun saveNeedsAName() = runTest(dispatcher) {
        val vm = editor()
        vm.add("Apple")
        vm.save()
        advanceUntilIdle()
        assertTrue(vm.uiState.value.nameError)
        assertTrue(repo.state.value.isEmpty())
    }

    @Test
    fun savesNewPoolInOrder() = runTest(dispatcher) {
        val vm = editor()
        vm.onNameChange("Fruit")
        vm.add("Apple")
        vm.add("Pear")
        vm.move(vm.uiState.value.items.last().key, -1)
        vm.save()
        advanceUntilIdle()
        val saved = repo.state.value.values.single()
        assertEquals("Fruit", saved.name)
        assertEquals(listOf("Pear", "Apple"), saved.items.map { it.name })
    }

    @Test
    fun showNamesNeedsImagesOnEveryItem() = runTest(dispatcher) {
        val vm = editor()
        vm.add("Plain")
        assertFalse(vm.uiState.value.canHideNames)
        vm.editItem(vm.uiState.value.items.single().key)
        vm.onEditorImagePicked { null }
        advanceUntilIdle()
        vm.saveEditor()
        assertTrue(vm.uiState.value.canHideNames)
        vm.onShowNamesChange(false)
        assertEquals(com.skrypko.dicefamiliar.domain.pool.PoolDisplayMode.IMAGES_ONLY, vm.uiState.value.displayMode)
    }

    @Test
    fun importExtendRenamesDuplicates() = runTest(dispatcher) {
        repo.nextImport = PoolImportOutcome.Ready(listOf(ImportedItem("Apple", "a.png"), ImportedItem("Kiwi")), 1, 0, 0)
        val vm = editor()
        vm.add("Apple")
        val events = vm.events
        vm.import({ null }, "x.zip", null)
        advanceUntilIdle()
        assertEquals(2, vm.uiState.value.preview?.items?.size)
        vm.applyImport(replace = false)
        assertEquals(listOf("Apple", "Apple #2", "Kiwi"), vm.uiState.value.items.map { it.item.name })
        advanceUntilIdle()
        assertEquals(PoolEditEvent.Imported(2, 1, 0), events.first { it is PoolEditEvent.Imported })
    }

    @Test
    fun importReplaceAndCancelledImportDiscardsImages() = runTest(dispatcher) {
        repo.nextImport = PoolImportOutcome.Ready(listOf(ImportedItem("Kiwi", "k.png")), 1, 0, 0)
        val vm = editor()
        vm.add("Apple")
        vm.import({ null }, "x.zip", null)
        advanceUntilIdle()
        vm.cancelImport()
        advanceUntilIdle()
        assertEquals(listOf("k.png"), repo.discarded)
        assertEquals(listOf("Apple"), vm.uiState.value.items.map { it.item.name })

        vm.import({ null }, "x.zip", null)
        advanceUntilIdle()
        vm.applyImport(replace = true)
        assertEquals(listOf("Kiwi"), vm.uiState.value.items.map { it.item.name })
    }

    @Test
    fun discardDeletesImagesAddedInThisEdit() = runTest(dispatcher) {
        val vm = editor()
        vm.addItem()
        vm.onEditorNameChange("Apple")
        vm.onEditorImagePicked { null }
        advanceUntilIdle()
        vm.saveEditor()
        vm.discard()
        advanceUntilIdle()
        assertEquals(listOf("picked.png"), repo.discarded)
    }

    @Test
    fun editsAnExistingPool() = runTest(dispatcher) {
        val id = repo.save(Pool(name = "Old", items = listOf(PoolItem(name = "A"))))
        val vm = editor(id)
        runCurrent()
        assertEquals("Old", vm.uiState.value.name)
        vm.editItem(vm.uiState.value.items.single().key)
        vm.onEditorNameChange("B")
        vm.saveEditor()
        vm.save()
        advanceUntilIdle()
        assertEquals(listOf("B"), repo.state.value[id]!!.items.map { it.name })
    }

}

@OptIn(ExperimentalCoroutinesApi::class)
class PoolPickViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val repo = FakePoolRepository()
    private val pickState = FakePoolPickStateRepository()
    private val sound = FakeSoundPlayer()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private suspend fun TestScope.picker(vararg random: Int, settings: AppSettings = AppSettings(animationsEnabled = false)): PoolPickViewModel {
        val id = repo.save(Pool(name = "Fruit", items = listOf("Apple", "Pear", "Plum").map { PoolItem(name = it) }))
        return PoolPickViewModel(
            SavedStateHandle(mapOf(PoolPickViewModel.ARG_POOL_ID to id)),
            repo,
            pickState,
            FakeAppSettingsRepository(settings),
            PoolPicker(ScriptedRandomSource(*random)),
            sound,
            SequentialIds(),
            FixedClock(),
        ).also { runCurrent() }
    }

    @Test
    fun picksWithRepeats() = runTest(dispatcher) {
        val vm = picker(1, 1)
        vm.onAllowRepeatsChange(true)
        vm.onCountChange("2")
        vm.pick()
        advanceUntilIdle()
        assertEquals(listOf("Pear", "Pear"), vm.uiState.value.latest?.items?.map { it.name })
        assertEquals(listOf(Sound.NUMBER_GENERATED), sound.played)
        assertEquals("2", pickState.saved.values.single().count)
    }

    @Test
    fun countAboveThePoolIsAnErrorWithoutRepeats() = runTest(dispatcher) {
        val vm = picker()
        vm.onCountChange("4")
        assertEquals(PickError.COUNT_EXCEEDS_POOL, vm.uiState.value.countError)
        vm.pick()
        advanceUntilIdle()
        assertNull(vm.uiState.value.latest)
        vm.onCountStep(+1)
        assertEquals("3", vm.uiState.value.count)
    }

    @Test
    fun removePickedSetsAsideUntilPutBack() = runTest(dispatcher) {
        // Shuffle of 3 items takes 2 draws (i = 2, 1): [Apple, Pear, Plum] -> swap(2,0) -> [Plum, Pear, Apple] -> swap(1,1).
        val vm = picker(0, 1)
        vm.onRemovePickedChange(true)
        vm.pick()
        advanceUntilIdle()
        assertEquals(listOf("Plum"), vm.uiState.value.latest?.items?.map { it.name })
        assertEquals(2, vm.uiState.value.available.size)
        assertEquals(1, vm.uiState.value.setAsideCount)
        vm.putBackAll()
        advanceUntilIdle()
        assertEquals(3, vm.uiState.value.available.size)
    }

    @Test
    fun panelVisibilityIsRemembered() = runTest(dispatcher) {
        pickState.panel = false
        val vm = picker()
        assertFalse(vm.uiState.value.panelVisible)
        vm.togglePanel()
        advanceUntilIdle()
        assertTrue(vm.uiState.value.panelVisible)
        assertTrue(pickState.panel)
    }
}
