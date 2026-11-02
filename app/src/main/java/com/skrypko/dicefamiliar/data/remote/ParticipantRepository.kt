package com.skrypko.dicefamiliar.data.remote

import android.os.Build
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.skrypko.dicefamiliar.domain.remote.IdGenerator
import com.skrypko.dicefamiliar.domain.remote.Participant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Identity of this device for local and (future) remote actions. */
interface ParticipantRepository {
    suspend fun self(): Participant
}

@Singleton
class DataStoreParticipantRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
    private val idGenerator: IdGenerator,
) : ParticipantRepository {
    private val mutex = Mutex()
    private var cached: Participant? = null

    override suspend fun self(): Participant = mutex.withLock {
        cached?.let { return it }
        var result: Participant? = null
        dataStore.edit { prefs ->
            val id = prefs[ID] ?: idGenerator.newId().also { prefs[ID] = it }
            val name = prefs[NAME] ?: Build.MODEL.orEmpty().ifBlank { "Android" }.also { prefs[NAME] = it }
            result = Participant(id, name)
        }
        return result!!.also { cached = it }
    }

    private companion object {
        val ID = stringPreferencesKey("participant.id")
        val NAME = stringPreferencesKey("participant.name")
    }
}
