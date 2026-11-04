package com.skrypko.dicefamiliar.ui.navigation

import androidx.compose.runtime.LaunchedEffect
import com.skrypko.dicefamiliar.ui.lan.LanScreen
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
import com.skrypko.dicefamiliar.ui.cards.CardTableScreen
import com.skrypko.dicefamiliar.ui.cards.CustomDeckScreen
import com.skrypko.dicefamiliar.ui.cards.DeckListScreen
import com.skrypko.dicefamiliar.ui.dice.DiceScreen
import com.skrypko.dicefamiliar.ui.number.NumberScreen
import com.skrypko.dicefamiliar.ui.pool.list.PoolListScreen
import com.skrypko.dicefamiliar.ui.pool.edit.PoolEditScreen
import com.skrypko.dicefamiliar.ui.pool.pick.PoolPickScreen
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
    val lanEnabled by navigationViewModel.lanEnabled.collectAsStateWithLifecycle()
    val openRequest by navigationViewModel.openRequest.collectAsStateWithLifecycle()
    // A roll request (notification or the LAN tab card) opens on the Dice tab.
    LaunchedEffect(openRequest) {
        if (openRequest) {
            navController.navigate(DiceRoute) {
                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
        }
    }
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val currentTab = TopLevelDestination.entries.firstOrNull { tab ->
        currentDestination?.hierarchy?.any { it.hasRoute(tab.routeClass) } == true
    } ?: when {
        currentDestination?.hasRoute(DiceSetEditRoute::class) == true -> TopLevelDestination.SETTINGS
        currentDestination?.hasRoute(DeckListRoute::class) == true ||
            currentDestination?.hasRoute(CustomDeckRoute::class) == true -> TopLevelDestination.CARDS
        currentDestination?.hasRoute(LanRequestRoute::class) == true -> TopLevelDestination.LAN
        currentDestination?.hasRoute(PoolEditRoute::class) == true ||
            currentDestination?.hasRoute(PoolPickRoute::class) == true -> TopLevelDestination.POOL
        else -> null
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            NavigationBar {
                TopLevelDestination.entries.filter { it != TopLevelDestination.LAN || lanEnabled }.forEach { destination ->
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
                        icon = { Icon(destination.themedIcon(), contentDescription = null) },
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
            composable<PoolListRoute> {
                PoolListScreen(
                    onOpenPool = { pool ->
                        navController.navigate(if (pool.itemCount == 0) PoolEditRoute(pool.id) else PoolPickRoute(pool.id))
                    },
                    onEditPool = { id -> navController.navigate(PoolEditRoute(id)) },
                    onNewPool = { navController.navigate(PoolEditRoute()) },
                )
            }
            composable<PoolEditRoute> {
                PoolEditScreen(onDone = { savedId ->
                    val cameFromList = navController.previousBackStackEntry?.destination?.hasRoute(PoolListRoute::class) == true
                    navController.popBackStack()
                    // A new (or empty) pool opened from the list goes straight to Pick once it has items.
                    if (savedId != null && cameFromList) {
                        navController.navigate(PoolPickRoute(savedId))
                    }
                })
            }
            composable<PoolPickRoute> {
                PoolPickScreen(
                    onBack = { navController.popBackStack() },
                    onEdit = { id -> navController.navigate(PoolEditRoute(id)) },
                )
            }
            composable<DiceRoute> { DiceScreen() }
            composable<LanRoute> {
                LanScreen(
                    onOpenPlayer = { id -> navController.navigate(LanRequestRoute(id)) },
                    onOpenRequest = { navigationViewModel.openRequestFromLan() },
                )
            }
            composable<LanRequestRoute> { DiceScreen(onBack = { navController.popBackStack() }) }
            composable<CardsRoute> { CardTableScreen(onOpenDecks = { navController.navigate(DeckListRoute) }) }
            composable<DeckListRoute> {
                DeckListScreen(
                    onBack = { navController.popBackStack() },
                    onEditCustom = { id -> navController.navigate(CustomDeckRoute(id)) },
                )
            }
            composable<CustomDeckRoute> { CustomDeckScreen(onDone = { navController.popBackStack() }) }
            composable<SettingsRoute> {
                SettingsScreen(onEditDiceSet = { id -> navController.navigate(DiceSetEditRoute(id)) })
            }
            composable<DiceSetEditRoute> {
                DiceSetEditScreen(onBack = { navController.popBackStack() })
            }
        }
    }
}
