package com.skrypko.dicefamiliar.data.navigation

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** Remembers the last selected bottom-bar tab so the app reopens on it. */
interface LastTabRepository {
    suspend fun load(): String?
    suspend fun save(tab: String)
}

@Singleton
class DataStoreLastTabRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : LastTabRepository {
    override suspend fun load(): String? = dataStore.data.map { it[LAST_TAB] }.first()

    override suspend fun save(tab: String) {
        dataStore.edit { it[LAST_TAB] = tab }
    }

    private companion object {
        val LAST_TAB = stringPreferencesKey("nav.last_tab")
    }
}
