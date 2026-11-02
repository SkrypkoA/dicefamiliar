package com.skrypko.dicefamiliar.ui.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.Numbers
import androidx.compose.material.icons.filled.Style
import androidx.compose.ui.graphics.vector.ImageVector
import com.skrypko.dicefamiliar.R
import kotlin.reflect.KClass

enum class TopLevelDestination(
    val route: Any,
    val routeClass: KClass<*>,
    val icon: ImageVector,
    @param:StringRes val labelRes: Int,
) {
    NUMBER(NumberRoute, NumberRoute::class, Icons.Filled.Numbers, R.string.tab_number),
    POOL(PoolListRoute, PoolListRoute::class, Icons.AutoMirrored.Filled.List, R.string.tab_pool),
    DICE(DiceRoute, DiceRoute::class, Icons.Filled.Casino, R.string.tab_dice),
    CARDS(CardsRoute, CardsRoute::class, Icons.Filled.Style, R.string.tab_cards),
}
