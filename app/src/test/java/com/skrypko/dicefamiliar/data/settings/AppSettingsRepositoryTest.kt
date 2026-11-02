package com.skrypko.dicefamiliar.data.settings

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.skrypko.dicefamiliar.domain.settings.AppSettings
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class AppSettingsRepositoryTest {

    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun defaultsThenPersistedValues() = runTest {
        val file = File(tmp.root, "settings.preferences_pb")
        val repository = DataStoreAppSettingsRepository(
            PreferenceDataStoreFactory.create(scope = backgroundScope) { file },
        )
        assertEquals(AppSettings(), repository.settings.first())

        repository.setQuickRoll(true)
        repository.setAnimationsEnabled(false)

        assertEquals(AppSettings(animationsEnabled = false, quickRoll = true), repository.settings.first())
    }
}
