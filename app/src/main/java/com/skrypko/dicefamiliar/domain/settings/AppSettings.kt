package com.skrypko.dicefamiliar.domain.settings

/** App-wide user preferences, shared by all features. */
data class AppSettings(
    val soundsEnabled: Boolean = true,
    val animationsEnabled: Boolean = true,
    val hapticsEnabled: Boolean = true,
    /** Dice: show a Roll button instead of swipe-to-roll. */
    val quickRoll: Boolean = false,
)
