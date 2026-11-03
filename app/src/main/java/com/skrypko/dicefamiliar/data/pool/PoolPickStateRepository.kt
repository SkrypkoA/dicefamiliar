package com.skrypko.dicefamiliar.data.pool

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

/** The Pick screen's last inputs for one pool. [count] is the field text as typed. */
data class PoolPickState(val count: String = "1", val allowRepeats: Boolean = false, val removePicked: Boolean = false)

interface PoolPickStateRepository {
    suspend fun load(poolId: Long): PoolPickState
    suspend fun save(poolId: Long, state: PoolPickState)
    suspend fun clear(poolId: Long)

    /** The Pick screen's options panel is shown (one setting for every pool). */
    suspend fun panelVisible(): Boolean
    suspend fun savePanelVisible(visible: Boolean)
}

@Singleton
class DataStorePoolPickStateRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : PoolPickStateRepository {

    override suspend fun load(poolId: Long): PoolPickState {
        val prefs = dataStore.data.first()
        val defaults = PoolPickState()
        return PoolPickState(
            count = prefs[count(poolId)] ?: defaults.count,
            allowRepeats = prefs[repeats(poolId)] ?: defaults.allowRepeats,
            removePicked = prefs[remove(poolId)] ?: defaults.removePicked,
        )
    }

    override suspend fun save(poolId: Long, state: PoolPickState) {
        dataStore.edit {
            it[count(poolId)] = state.count
            it[repeats(poolId)] = state.allowRepeats
            it[remove(poolId)] = state.removePicked
        }
    }

    override suspend fun clear(poolId: Long) {
        dataStore.edit {
            it.remove(count(poolId))
            it.remove(repeats(poolId))
            it.remove(remove(poolId))
        }
    }

    override suspend fun panelVisible(): Boolean = dataStore.data.first()[PANEL_VISIBLE] ?: true

    override suspend fun savePanelVisible(visible: Boolean) {
        dataStore.edit { it[PANEL_VISIBLE] = visible }
    }

    private fun count(id: Long) = stringPreferencesKey("pool.pick.$id.count")
    private fun repeats(id: Long) = booleanPreferencesKey("pool.pick.$id.repeats")
    private fun remove(id: Long) = booleanPreferencesKey("pool.pick.$id.remove")

    private companion object {
        val PANEL_VISIBLE = booleanPreferencesKey("pool.panel_visible")
    }
}
