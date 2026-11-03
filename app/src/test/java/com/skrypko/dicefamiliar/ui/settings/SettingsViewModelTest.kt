package com.skrypko.dicefamiliar.ui.settings

import com.skrypko.dicefamiliar.domain.settings.AppSettings
import com.skrypko.dicefamiliar.data.dice.DiceSetImportOutcome
import com.skrypko.dicefamiliar.domain.dice.skin.DiceSetImportError
import com.skrypko.dicefamiliar.testing.FakeAppSettingsRepository
import com.skrypko.dicefamiliar.testing.FakeDiceSetRepository
import java.io.ByteArrayInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun defaultsAreSoundsAnimationsHapticsOnQuickRollOff() {
        assertEquals(AppSettings(true, true, true, false), AppSettings())
    }

    @Test
    fun togglesWriteThrough() = runTest(dispatcher) {
        val repository = FakeAppSettingsRepository()
        val vm = SettingsViewModel(repository, FakeDiceSetRepository())
        backgroundScope.launch { vm.settings.collect {} }

        vm.setSounds(false)
        vm.setAnimations(false)
        vm.setHaptics(false)
        vm.setQuickRoll(true)

        val expected = AppSettings(soundsEnabled = false, animationsEnabled = false, hapticsEnabled = false, quickRoll = true)
        assertEquals(expected, repository.settings.value)
        assertEquals(expected, vm.settings.value)
    }

    @Test
    fun listsSetsAndSelects() = runTest(dispatcher) {
        val sets = FakeDiceSetRepository()
        val vm = SettingsViewModel(FakeAppSettingsRepository(), sets)
        backgroundScope.launch { vm.diceSetsState.collect {} }
        val id = sets.create("Wood")

        vm.selectDiceSet("default")

        assertEquals(listOf("default", "wood"), vm.diceSetsState.value.sets.map { it.id })
        assertEquals("default", vm.diceSetsState.value.activeId)
        assertEquals("wood", id)
    }

    @Test
    fun createOpensEditor() = runTest(dispatcher) {
        val vm = SettingsViewModel(FakeAppSettingsRepository(), FakeDiceSetRepository())
        val events = mutableListOf<SettingsEvent>()
        backgroundScope.launch { vm.events.collect { events += it } }

        vm.createDiceSet("My Dice")

        assertEquals(listOf<SettingsEvent>(SettingsEvent.OpenDiceSet("my_dice")), events)
    }

    @Test
    fun importReportsOutcome() = runTest(dispatcher) {
        val sets = FakeDiceSetRepository()
        val vm = SettingsViewModel(FakeAppSettingsRepository(), sets)
        val events = mutableListOf<SettingsEvent>()
        backgroundScope.launch { vm.events.collect { events += it } }
        backgroundScope.launch { vm.diceSetsState.collect {} }

        vm.importDiceSet({ ByteArrayInputStream(ByteArray(0)) }, "x.zip")
        val imported = DiceSetImportOutcome.Imported("wood", "Wood", replaced = false, skippedFiles = 1, missingFiles = 0)
        sets.importOutcome = imported
        vm.importDiceSet({ ByteArrayInputStream(ByteArray(0)) }, "wood.zip")

        assertEquals(
            listOf(SettingsEvent.ImportFailed(DiceSetImportError.NOT_A_ZIP), SettingsEvent.Imported(imported)),
            events,
        )
        assertEquals("wood", vm.diceSetsState.value.activeId)
        assertFalse(vm.diceSetsState.value.importing)
    }

    @Test
    fun deleteActiveSetFallsBackToDefault() = runTest(dispatcher) {
        val sets = FakeDiceSetRepository()
        val vm = SettingsViewModel(FakeAppSettingsRepository(), sets)
        backgroundScope.launch { vm.diceSetsState.collect {} }
        sets.create("Wood")

        vm.deleteDiceSet("wood")

        assertEquals(listOf("default"), vm.diceSetsState.value.sets.map { it.id })
        assertEquals("default", vm.diceSetsState.value.activeId)
    }
}
