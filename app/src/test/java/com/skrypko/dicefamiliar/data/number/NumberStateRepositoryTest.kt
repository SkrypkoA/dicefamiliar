package com.skrypko.dicefamiliar.data.number

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.skrypko.dicefamiliar.domain.number.NumberMode
import com.skrypko.dicefamiliar.domain.number.NumberResult
import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class NumberStateRepositoryTest {

    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun defaultsThenLastSelectedValuesAndResult() = runTest {
        val file = File(tmp.root, "settings.preferences_pb")
        val repository = DataStoreNumberStateRepository(PreferenceDataStoreFactory.create(scope = backgroundScope) { file })
        assertEquals(NumberState(), repository.load())

        val inputs = NumberState("-5", "5,5", "3", false, NumberMode.DECIMAL, 1)
        val result = NumberResult("r1", listOf(-50, 12, 55), 1, -50, 55, false, 123)
        repository.save(inputs)
        repository.saveLastResult(result)
        assertEquals(inputs.copy(lastResult = result), repository.load())

        repository.saveLastResult(null)
        assertEquals(inputs, repository.load())
    }
}
