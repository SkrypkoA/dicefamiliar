package com.skrypko.dicefamiliar.data.number

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.skrypko.dicefamiliar.domain.number.NumberLimits
import com.skrypko.dicefamiliar.domain.number.NumberMode
import com.skrypko.dicefamiliar.domain.number.NumberResult
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/** Last-used number generator inputs (field text as typed) and the last generated result. */
data class NumberState(
    val min: String = "1",
    val max: String = "100",
    val count: String = "1",
    val allowDuplicates: Boolean = true,
    val mode: NumberMode = NumberMode.INTEGER,
    val decimals: Int = NumberLimits.DEFAULT_DECIMALS,
    val lastResult: NumberResult? = null,
    /** The options panel at the top of the screen is shown. */
    val panelVisible: Boolean = true,
)

interface NumberStateRepository {
    suspend fun load(): NumberState

    /** Saves the inputs; [NumberState.lastResult] is ignored (see [saveLastResult]). */
    suspend fun save(state: NumberState)
    suspend fun saveLastResult(result: NumberResult?)
    suspend fun savePanelVisible(visible: Boolean)
}

@Singleton
class DataStoreNumberStateRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : NumberStateRepository {

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun load(): NumberState {
        val prefs = dataStore.data.first()
        val defaults = NumberState()
        return NumberState(
            min = prefs[MIN] ?: defaults.min,
            max = prefs[MAX] ?: defaults.max,
            count = prefs[COUNT] ?: defaults.count,
            allowDuplicates = prefs[DUPLICATES] ?: defaults.allowDuplicates,
            mode = prefs[MODE]?.let { name -> NumberMode.entries.firstOrNull { it.name == name } } ?: defaults.mode,
            panelVisible = prefs[PANEL_VISIBLE] ?: defaults.panelVisible,
            decimals = (prefs[DECIMALS] ?: defaults.decimals).coerceIn(NumberLimits.MIN_DECIMALS, NumberLimits.MAX_DECIMALS),
            lastResult = prefs[LAST_RESULT]?.let { text ->
                try {
                    json.decodeFromString<NumberResult>(text)
                } catch (_: SerializationException) {
                    null
                } catch (_: IllegalArgumentException) {
                    null
                }
            },
        )
    }

    override suspend fun save(state: NumberState) {
        dataStore.edit {
            it[MIN] = state.min
            it[MAX] = state.max
            it[COUNT] = state.count
            it[DUPLICATES] = state.allowDuplicates
            it[MODE] = state.mode.name
            it[DECIMALS] = state.decimals
        }
    }

    override suspend fun savePanelVisible(visible: Boolean) {
        dataStore.edit { it[PANEL_VISIBLE] = visible }
    }

    override suspend fun saveLastResult(result: NumberResult?) {
        dataStore.edit {
            if (result == null) it.remove(LAST_RESULT) else it[LAST_RESULT] = json.encodeToString(result)
        }
    }

    private companion object {
        val MIN = stringPreferencesKey("number.min")
        val MAX = stringPreferencesKey("number.max")
        val COUNT = stringPreferencesKey("number.count")
        val DUPLICATES = booleanPreferencesKey("number.duplicates")
        val MODE = stringPreferencesKey("number.mode")
        val DECIMALS = intPreferencesKey("number.decimals")
        val LAST_RESULT = stringPreferencesKey("number.last_result")
        val PANEL_VISIBLE = booleanPreferencesKey("number.panel_visible")
    }
}
