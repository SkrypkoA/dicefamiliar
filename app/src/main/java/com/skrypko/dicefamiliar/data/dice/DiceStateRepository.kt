package com.skrypko.dicefamiliar.data.dice

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.skrypko.dicefamiliar.domain.dice.RollMode
import com.skrypko.dicefamiliar.domain.dice.pool.SuccessCriterion
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

data class DiceState(
    val lastNotation: String = "",
    val lastMode: RollMode = RollMode.NORMAL,
    val formulaVisible: Boolean = false,
    val panelVisible: Boolean = true,
    /** Dice pool mode (one die type, success groups) instead of summing a formula. */
    val poolMode: Boolean = false,
    /** The pool formula (`NdS`), kept apart from the sum formula. */
    val poolNotation: String = "",
    /** The user's pool groups, in order. */
    val poolGroups: List<SuccessCriterion> = emptyList(),
)

/** Last-used dice formula and roll mode, and the dice pool mode, formula and groups. */
interface DiceStateRepository {
    suspend fun load(): DiceState
    suspend fun saveNotation(notation: String)
    suspend fun saveMode(mode: RollMode)
    suspend fun saveFormulaVisible(visible: Boolean)
    suspend fun savePanelVisible(visible: Boolean)
    suspend fun savePoolMode(enabled: Boolean)
    suspend fun savePoolNotation(notation: String)
    suspend fun savePoolGroups(groups: List<SuccessCriterion>)
}

@Singleton
class DataStoreDiceStateRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : DiceStateRepository {
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun load(): DiceState = dataStore.data.map { prefs ->
        DiceState(
            lastNotation = prefs[NOTATION].orEmpty(),
            lastMode = prefs[MODE]?.let { name -> RollMode.entries.firstOrNull { it.name == name } }
                ?: RollMode.NORMAL,
            formulaVisible = prefs[FORMULA_VISIBLE] ?: false,
            panelVisible = prefs[PANEL_VISIBLE] ?: true,
            poolMode = prefs[POOL_MODE] ?: false,
            poolNotation = prefs[POOL_NOTATION].orEmpty(),
            poolGroups = prefs[POOL_GROUPS]?.let(::decodeGroups).orEmpty(),
        )
    }.first()

    private fun decodeGroups(text: String): List<SuccessCriterion>? = try {
        json.decodeFromString<List<SuccessCriterion>>(text)
    } catch (_: SerializationException) {
        null
    } catch (_: IllegalArgumentException) {
        null
    }

    override suspend fun savePoolMode(enabled: Boolean) {
        dataStore.edit { it[POOL_MODE] = enabled }
    }

    override suspend fun savePoolNotation(notation: String) {
        dataStore.edit { it[POOL_NOTATION] = notation }
    }

    override suspend fun savePoolGroups(groups: List<SuccessCriterion>) {
        dataStore.edit { it[POOL_GROUPS] = json.encodeToString(groups) }
    }

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
        val POOL_MODE = booleanPreferencesKey("dice.pool_mode")
        val POOL_NOTATION = stringPreferencesKey("dice.pool_notation")
        val POOL_GROUPS = stringPreferencesKey("dice.pool_groups")
    }
}
