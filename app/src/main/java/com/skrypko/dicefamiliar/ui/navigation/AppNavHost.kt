package com.skrypko.dicefamiliar.ui.navigation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.skrypko.dicefamiliar.ui.cards.CardsScreen
import com.skrypko.dicefamiliar.ui.dice.DiceScreen
import com.skrypko.dicefamiliar.ui.number.NumberScreen
import com.skrypko.dicefamiliar.ui.pool.list.PoolListScreen
import com.skrypko.dicefamiliar.ui.settings.SettingsScreen
import com.skrypko.dicefamiliar.ui.settings.dicesets.DiceSetEditScreen

/**
 * No top app bar: every section, including Settings, is a tab in the bottom navigation bar.
 * The app reopens on the last selected tab.
 */
@Composable
fun AppNavHost(
    navController: NavHostController = rememberNavController(),
    navigationViewModel: NavigationViewModel = hiltViewModel(),
) {
    // Wait for the saved tab (a few ms) because NavHost's start destination can't change later.
    val startTab by navigationViewModel.startTab.collectAsStateWithLifecycle()
    val start = startTab ?: return
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val currentTab = TopLevelDestination.entries.firstOrNull { tab ->
        currentDestination?.hierarchy?.any { it.hasRoute(tab.routeClass) } == true
    } ?: TopLevelDestination.SETTINGS.takeIf { currentDestination?.hasRoute(DiceSetEditRoute::class) == true }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            NavigationBar {
                TopLevelDestination.entries.forEach { destination ->
                    NavigationBarItem(
                        selected = destination == currentTab,
                        onClick = {
                            navigationViewModel.onTabSelected(destination)
                            navController.navigate(destination.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(destination.icon, contentDescription = null) },
                        label = { Text(stringResource(destination.labelRes), maxLines = 1) },
                    )
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = start.route,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable<NumberRoute> { NumberScreen() }
            composable<PoolListRoute> { PoolListScreen() }
            composable<DiceRoute> { DiceScreen() }
            composable<CardsRoute> { CardsScreen() }
            composable<SettingsRoute> {
                SettingsScreen(onEditDiceSet = { id -> navController.navigate(DiceSetEditRoute(id)) })
            }
            composable<DiceSetEditRoute> {
                DiceSetEditScreen(onBack = { navController.popBackStack() })
            }
        }
    }
}
