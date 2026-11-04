package com.skrypko.dicefamiliar.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.skrypko.dicefamiliar.domain.settings.DarkMode
import com.skrypko.dicefamiliar.domain.settings.LanNotifications
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
    suspend fun setThemeId(id: String)
    suspend fun setDarkMode(mode: DarkMode)
    suspend fun setDynamicColor(enabled: Boolean)
    suspend fun setLanEnabled(enabled: Boolean)
    suspend fun setLanTransport(id: String)
    suspend fun setLanNotifications(mode: LanNotifications)
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
            themeId = prefs[THEME] ?: defaults.themeId,
            darkMode = prefs[DARK_MODE]?.let { name -> DarkMode.entries.firstOrNull { it.name == name } }
                ?: defaults.darkMode,
            dynamicColor = prefs[DYNAMIC_COLOR] ?: defaults.dynamicColor,
            lanEnabled = prefs[LAN_ENABLED] ?: defaults.lanEnabled,
            lanTransport = prefs[LAN_TRANSPORT] ?: defaults.lanTransport,
            lanNotifications = prefs[LAN_NOTIFICATIONS]?.let { name -> LanNotifications.entries.firstOrNull { it.name == name } }
                ?: defaults.lanNotifications,
        )
    }.distinctUntilChanged()

    override suspend fun setSoundsEnabled(enabled: Boolean) = set(SOUNDS, enabled)
    override suspend fun setAnimationsEnabled(enabled: Boolean) = set(ANIMATIONS, enabled)
    override suspend fun setHapticsEnabled(enabled: Boolean) = set(HAPTICS, enabled)
    override suspend fun setQuickRoll(enabled: Boolean) = set(QUICK_ROLL, enabled)
    override suspend fun setThemeId(id: String) {
        dataStore.edit { it[THEME] = id }
    }
    override suspend fun setDarkMode(mode: DarkMode) {
        dataStore.edit { it[DARK_MODE] = mode.name }
    }
    override suspend fun setDynamicColor(enabled: Boolean) = set(DYNAMIC_COLOR, enabled)
    override suspend fun setLanEnabled(enabled: Boolean) = set(LAN_ENABLED, enabled)
    override suspend fun setLanTransport(id: String) {
        dataStore.edit { it[LAN_TRANSPORT] = id }
    }
    override suspend fun setLanNotifications(mode: LanNotifications) {
        dataStore.edit { it[LAN_NOTIFICATIONS] = mode.name }
    }

    private suspend fun set(key: Preferences.Key<Boolean>, value: Boolean) {
        dataStore.edit { it[key] = value }
    }

    private companion object {
        val SOUNDS = booleanPreferencesKey("settings.sounds")
        val ANIMATIONS = booleanPreferencesKey("settings.animations")
        val HAPTICS = booleanPreferencesKey("settings.haptics")
        val QUICK_ROLL = booleanPreferencesKey("settings.dice_quick_roll")
        val THEME = stringPreferencesKey("settings.theme")
        val DARK_MODE = stringPreferencesKey("settings.dark_mode")
        val DYNAMIC_COLOR = booleanPreferencesKey("settings.dynamic_color")
        val LAN_ENABLED = booleanPreferencesKey("settings.lan_enabled")
        val LAN_TRANSPORT = stringPreferencesKey("settings.lan_transport")
        val LAN_NOTIFICATIONS = stringPreferencesKey("settings.lan_notifications")
    }
}
