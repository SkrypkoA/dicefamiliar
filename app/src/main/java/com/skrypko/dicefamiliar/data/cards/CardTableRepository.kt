package com.skrypko.dicefamiliar.data.cards

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.skrypko.dicefamiliar.domain.cards.CardTable
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/** The table of the **current** deck only (switching decks starts fresh), and the buttons toggle. */
interface CardTableRepository {
    /** The saved table if it belongs to [deckId] and still fits a deck of [deckSize] cards. */
    suspend fun load(deckId: String, deckSize: Int): CardTable?
    suspend fun save(deckId: String, table: CardTable)
    val showButtons: Flow<Boolean>
    suspend fun setShowButtons(show: Boolean)
    val panelVisible: Flow<Boolean>
    suspend fun setPanelVisible(visible: Boolean)
}

@Serializable
private data class SavedTable(val deckId: String, val table: CardTable)

@Singleton
class DataStoreCardTableRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : CardTableRepository {
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun load(deckId: String, deckSize: Int): CardTable? {
        val text = dataStore.data.first()[TABLE] ?: return null
        val saved = try {
            json.decodeFromString<SavedTable>(text)
        } catch (_: SerializationException) {
            return null
        } catch (_: IllegalArgumentException) {
            return null
        }
        return saved.table.takeIf { saved.deckId == deckId && it.isValidFor(deckSize) }
    }

    override suspend fun save(deckId: String, table: CardTable) {
        dataStore.edit { it[TABLE] = json.encodeToString(SavedTable(deckId, table)) }
    }

    override val showButtons: Flow<Boolean> = dataStore.data.map { it[SHOW_BUTTONS] ?: false }

    override suspend fun setShowButtons(show: Boolean) {
        dataStore.edit { it[SHOW_BUTTONS] = show }
    }

    override val panelVisible: Flow<Boolean> = dataStore.data.map { it[PANEL_VISIBLE] ?: true }

    override suspend fun setPanelVisible(visible: Boolean) {
        dataStore.edit { it[PANEL_VISIBLE] = visible }
    }

    private companion object {
        val PANEL_VISIBLE = booleanPreferencesKey("cards.panel_visible")
        val TABLE = stringPreferencesKey("cards.table")
        val SHOW_BUTTONS = booleanPreferencesKey("cards.show_buttons")
    }
}
