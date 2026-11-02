package com.skrypko.dicefamiliar.ui.settings

import com.skrypko.dicefamiliar.domain.settings.AppSettings
import com.skrypko.dicefamiliar.testing.FakeAppSettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
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
        val vm = SettingsViewModel(repository)
        backgroundScope.launch { vm.settings.collect {} }

        vm.setSounds(false)
        vm.setAnimations(false)
        vm.setHaptics(false)
        vm.setQuickRoll(true)

        val expected = AppSettings(soundsEnabled = false, animationsEnabled = false, hapticsEnabled = false, quickRoll = true)
        assertEquals(expected, repository.settings.value)
        assertEquals(expected, vm.settings.value)
    }
}
