package com.skrypko.dicefamiliar.ui.navigation

import com.skrypko.dicefamiliar.data.navigation.LastTabRepository
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
}
