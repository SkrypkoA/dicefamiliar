package com.skrypko.dicefamiliar.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import com.skrypko.dicefamiliar.domain.settings.AppSettings
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** App-wide settings shared by all features. */
interface AppSettingsRepository {
    val settings: Flow<AppSettings>
    suspend fun setSoundsEnabled(enabled: Boolean)
    suspend fun setAnimationsEnabled(enabled: Boolean)
    suspend fun setHapticsEnabled(enabled: Boolean)
    suspend fun setQuickRoll(enabled: Boolean)
}

@Singleton
class DataStoreAppSettingsRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : AppSettingsRepository {

    override val settings: Flow<AppSettings> = dataStore.data.map { prefs ->
        val defaults = AppSettings()
        AppSettings(
            soundsEnabled = prefs[SOUNDS] ?: defaults.soundsEnabled,
            animationsEnabled = prefs[ANIMATIONS] ?: defaults.animationsEnabled,
            hapticsEnabled = prefs[HAPTICS] ?: defaults.hapticsEnabled,
            quickRoll = prefs[QUICK_ROLL] ?: defaults.quickRoll,
        )
    }.distinctUntilChanged()

    override suspend fun setSoundsEnabled(enabled: Boolean) = set(SOUNDS, enabled)
    override suspend fun setAnimationsEnabled(enabled: Boolean) = set(ANIMATIONS, enabled)
    override suspend fun setHapticsEnabled(enabled: Boolean) = set(HAPTICS, enabled)
    override suspend fun setQuickRoll(enabled: Boolean) = set(QUICK_ROLL, enabled)

    private suspend fun set(key: Preferences.Key<Boolean>, value: Boolean) {
        dataStore.edit { it[key] = value }
    }

    private companion object {
        val SOUNDS = booleanPreferencesKey("settings.sounds")
        val ANIMATIONS = booleanPreferencesKey("settings.animations")
        val HAPTICS = booleanPreferencesKey("settings.haptics")
        val QUICK_ROLL = booleanPreferencesKey("settings.dice_quick_roll")
    }
}
