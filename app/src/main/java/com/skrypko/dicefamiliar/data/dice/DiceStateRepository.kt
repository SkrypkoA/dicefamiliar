package com.skrypko.dicefamiliar.data.dice

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.skrypko.dicefamiliar.domain.dice.RollMode
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

data class DiceState(
    val lastNotation: String = "",
    val lastMode: RollMode = RollMode.NORMAL,
    val formulaVisible: Boolean = false,
    val panelVisible: Boolean = true,
)

/** Last-used dice formula and roll mode. */
interface DiceStateRepository {
    suspend fun load(): DiceState
    suspend fun saveNotation(notation: String)
    suspend fun saveMode(mode: RollMode)
    suspend fun saveFormulaVisible(visible: Boolean)
    suspend fun savePanelVisible(visible: Boolean)
}

@Singleton
class DataStoreDiceStateRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : DiceStateRepository {

    override suspend fun load(): DiceState = dataStore.data.map { prefs ->
        DiceState(
            lastNotation = prefs[NOTATION].orEmpty(),
            lastMode = prefs[MODE]?.let { name -> RollMode.entries.firstOrNull { it.name == name } }
                ?: RollMode.NORMAL,
            formulaVisible = prefs[FORMULA_VISIBLE] ?: false,
            panelVisible = prefs[PANEL_VISIBLE] ?: true,
        )
    }.first()

    override suspend fun saveNotation(notation: String) {
        dataStore.edit { it[NOTATION] = notation }
    }

    override suspend fun saveMode(mode: RollMode) {
        dataStore.edit { it[MODE] = mode.name }
    }

    override suspend fun saveFormulaVisible(visible: Boolean) {
        dataStore.edit { it[FORMULA_VISIBLE] = visible }
    }

    override suspend fun savePanelVisible(visible: Boolean) {
        dataStore.edit { it[PANEL_VISIBLE] = visible }
    }

    private companion object {
        val PANEL_VISIBLE = booleanPreferencesKey("dice.panel_visible")
        val FORMULA_VISIBLE = booleanPreferencesKey("dice.formula_visible")
        val NOTATION = stringPreferencesKey("dice.last_notation")
        val MODE = stringPreferencesKey("dice.last_mode")
    }
}
