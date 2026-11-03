package com.skrypko.dicefamiliar.data.pool

import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PoolDaoTest {
    private lateinit var db: AppDatabase
    private lateinit var dao: PoolDao

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java).build()
        dao = db.poolDao()
    }

    @After
    fun tearDown() = db.close()

    private fun item(poolId: Long, name: String, position: Int, image: String? = null) =
        PoolItemEntity(poolId = poolId, name = name, nameNormalized = name.lowercase(), imagePath = image, position = position)

    @Test
    fun itemsComeBackInOrderAndSummariesCount() = runBlocking {
        val id = dao.insertPool(PoolEntity(name = "Fruit", createdAt = 1))
        dao.replaceItems(id, listOf(item(id, "Pear", 1, "p.png"), item(id, "Apple", 0), item(id, "Plum", 2, "q.png")))
        assertEquals(listOf("Apple", "Pear", "Plum"), dao.items(id).map { it.name })
        dao.setAside(id, dao.items(id).take(1).map { it.id })
        val summary = dao.observeSummaries().first().single()
        assertEquals(PoolSummary(id, "Fruit", 3, 2, "p.png"), summary)
        dao.restoreAll(id)
        assertEquals(3, dao.observeSummaries().first().single().leftCount)
    }

    @Test
    fun deletingAPoolCascadesToItems() = runBlocking {
        val id = dao.insertPool(PoolEntity(name = "Fruit", createdAt = 1))
        dao.insertItems(listOf(item(id, "Apple", 0, "a.png")))
        dao.deletePool(id)
        assertNull(dao.pool(id))
        assertEquals(emptyList<String>(), dao.allImagePaths())
    }

    @Test
    fun namesAreUniquePerPool() = runBlocking {
        val a = dao.insertPool(PoolEntity(name = "A", createdAt = 1))
        val b = dao.insertPool(PoolEntity(name = "B", createdAt = 1))
        dao.insertItems(listOf(item(a, "Apple", 0), item(b, "Apple", 0)))
        try {
            dao.insertItems(listOf(item(a, "Apple", 1)))
            fail("Duplicate name in one pool must be rejected")
        } catch (_: SQLiteConstraintException) {
        }
    }

    @Test
    fun showNamesIsStored() = runBlocking {
        val id = dao.insertPool(PoolEntity(name = "A", createdAt = 1))
        assertEquals(true, dao.pool(id)!!.showNames)
        dao.setShowNames(id, false)
        assertEquals(false, dao.pool(id)!!.showNames)
    }
}
