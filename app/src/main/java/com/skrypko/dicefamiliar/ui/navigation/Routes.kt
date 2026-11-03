package com.skrypko.dicefamiliar.ui.navigation

import kotlinx.serialization.Serializable

@Serializable
data object NumberRoute

@Serializable
data object PoolListRoute

@Serializable
data object DiceRoute

@Serializable
data object CardsRoute

@Serializable
data object SettingsRoute

/** Editor of a user dice set; opened from Settings and shown under the Settings tab. */
@Serializable
data class DiceSetEditRoute(val setId: String)
