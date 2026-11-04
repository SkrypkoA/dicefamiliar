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

/** Deck selection; shown under the Cards tab. */
@Serializable
data object DeckListRoute

/** Custom deck editor: a null [deckId] creates a new deck. Shown under the Cards tab. */
@Serializable
data class CustomDeckRoute(val deckId: String? = null)

/** Edit Pool form: [poolId] 0 creates a new pool. Shown under the Pool tab. */
@Serializable
data class PoolEditRoute(val poolId: Long = 0L)

/** Pick screen of one pool. Shown under the Pool tab. */
@Serializable
data class PoolPickRoute(val poolId: Long)

/** LAN play tab (shown when LAN mode is on in Settings). */
@Serializable
data object LanRoute

/** Host: the dice screen in request mode for one player. Shown under the LAN tab. */
@Serializable
data class LanRequestRoute(val playerId: String)
