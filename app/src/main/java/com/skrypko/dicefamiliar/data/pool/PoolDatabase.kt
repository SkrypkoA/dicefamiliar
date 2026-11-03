package com.skrypko.dicefamiliar.data.pool

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "pools")
data class PoolEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long,
    @ColumnInfo(defaultValue = "1") val showNames: Boolean = true,
)

@Entity(
    tableName = "pool_items",
    foreignKeys = [ForeignKey(entity = PoolEntity::class, parentColumns = ["id"], childColumns = ["poolId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index(value = ["poolId", "nameNormalized"], unique = true), Index("poolId")],
)
data class PoolItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val poolId: Long,
    val name: String,
    val nameNormalized: String,
    /** Relative to `filesDir/pool_images/`. */
    val imagePath: String? = null,
    val colorArgb: Int? = null,
    val position: Int,
    @ColumnInfo(defaultValue = "0") val setAside: Boolean = false,
)

/** A row of the pool list. */
data class PoolSummary(val id: Long, val name: String, val itemCount: Int, val leftCount: Int, val cover: String?)

@Dao
interface PoolDao {
    @Query(
        """
        SELECT p.id, p.name,
               (SELECT COUNT(*) FROM pool_items i WHERE i.poolId = p.id) AS itemCount,
               (SELECT COUNT(*) FROM pool_items i WHERE i.poolId = p.id AND i.setAside = 0) AS leftCount,
               (SELECT i.imagePath FROM pool_items i WHERE i.poolId = p.id AND i.imagePath IS NOT NULL ORDER BY i.position LIMIT 1) AS cover
        FROM pools p ORDER BY p.name COLLATE NOCASE, p.id
        """,
    )
    fun observeSummaries(): Flow<List<PoolSummary>>

    @Query("SELECT * FROM pools WHERE id = :id")
    fun observePool(id: Long): Flow<PoolEntity?>

    @Query("SELECT * FROM pool_items WHERE poolId = :poolId ORDER BY position")
    fun observeItems(poolId: Long): Flow<List<PoolItemEntity>>

    @Query("SELECT * FROM pools WHERE id = :id")
    suspend fun pool(id: Long): PoolEntity?

    @Query("SELECT * FROM pool_items WHERE poolId = :poolId ORDER BY position")
    suspend fun items(poolId: Long): List<PoolItemEntity>

    @Query("SELECT imagePath FROM pool_items WHERE imagePath IS NOT NULL")
    suspend fun allImagePaths(): List<String>

    @Insert
    suspend fun insertPool(pool: PoolEntity): Long

    @Query("UPDATE pools SET name = :name, showNames = :showNames WHERE id = :id")
    suspend fun updatePool(id: Long, name: String, showNames: Boolean)

    @Query("UPDATE pools SET showNames = :showNames WHERE id = :id")
    suspend fun setShowNames(id: Long, showNames: Boolean)

    @Query("DELETE FROM pools WHERE id = :id")
    suspend fun deletePool(id: Long)

    @Insert
    suspend fun insertItems(items: List<PoolItemEntity>)

    @Query("DELETE FROM pool_items WHERE poolId = :poolId")
    suspend fun deleteItems(poolId: Long)

    @Transaction
    suspend fun replaceItems(poolId: Long, items: List<PoolItemEntity>) {
        deleteItems(poolId)
        insertItems(items)
    }

    @Query("UPDATE pool_items SET setAside = 1 WHERE poolId = :poolId AND id IN (:ids)")
    suspend fun setAside(poolId: Long, ids: List<Long>)

    @Query("UPDATE pool_items SET setAside = 0 WHERE poolId = :poolId")
    suspend fun restoreAll(poolId: Long)
}

@Database(entities = [PoolEntity::class, PoolItemEntity::class], version = 1, exportSchema = true)
abstract class AppDatabase : RoomDatabase() {
    abstract fun poolDao(): PoolDao

    companion object {
        const val NAME = "dicefamiliar.db"
    }
}
