package com.skrypko.dicefamiliar.ui.navigation

import com.skrypko.dicefamiliar.data.lan.LanNavigation
import com.skrypko.dicefamiliar.data.navigation.LastTabRepository
import com.skrypko.dicefamiliar.domain.settings.AppSettings
import com.skrypko.dicefamiliar.testing.FakeAppSettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class NavigationViewModelTest {

    private class FakeLastTab(var saved: String? = null) : LastTabRepository {
        override suspend fun load() = saved
        override suspend fun save(tab: String) { saved = tab }
    }

    private val dispatcher = UnconfinedTestDispatcher()

    private fun NavigationViewModel(repo: LastTabRepository, lan: Boolean = false) =
        com.skrypko.dicefamiliar.ui.navigation.NavigationViewModel(
            repo,
            FakeAppSettingsRepository(AppSettings(lanEnabled = lan)),
            LanNavigation(),
        )

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun startsOnNumberWhenNothingSaved() = runTest(dispatcher) {
        assertEquals(TopLevelDestination.NUMBER, NavigationViewModel(FakeLastTab()).startTab.value)
    }

    @Test
    fun startsOnLastSelectedTabAndSavesSelection() = runTest(dispatcher) {
        val repo = FakeLastTab("DICE")
        val vm = NavigationViewModel(repo)
        assertEquals(TopLevelDestination.DICE, vm.startTab.value)
        vm.onTabSelected(TopLevelDestination.CARDS)
        assertEquals("CARDS", repo.saved)
        assertEquals(TopLevelDestination.CARDS, NavigationViewModel(repo).startTab.value)
    }

    @Test
    fun unknownSavedValueFallsBackToDefault() = runTest(dispatcher) {
        assertEquals(TopLevelDestination.NUMBER, NavigationViewModel(FakeLastTab("REMOVED_TAB")).startTab.value)
    }

    @Test
    fun lanTabOnlyWithLanPlayOn() = runTest(dispatcher) {
        assertEquals(TopLevelDestination.NUMBER, NavigationViewModel(FakeLastTab("LAN"), lan = false).startTab.value)
        val vm = NavigationViewModel(FakeLastTab("LAN"), lan = true)
        assertEquals(TopLevelDestination.LAN, vm.startTab.value)
        assertEquals(true, vm.lanEnabled.value)
    }
}
