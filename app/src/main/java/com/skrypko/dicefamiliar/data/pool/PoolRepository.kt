package com.skrypko.dicefamiliar.data.pool

import android.util.Log
import com.skrypko.dicefamiliar.data.dice.StreamSource
import com.skrypko.dicefamiliar.domain.pool.NameNormalizer
import com.skrypko.dicefamiliar.domain.pool.Pool
import com.skrypko.dicefamiliar.domain.pool.PoolItem
import com.skrypko.dicefamiliar.domain.pool.io.CsvItemParser
import com.skrypko.dicefamiliar.domain.pool.io.CsvItemWriter
import com.skrypko.dicefamiliar.domain.pool.io.CsvItemsResult
import com.skrypko.dicefamiliar.domain.pool.io.ExportItem
import com.skrypko.dicefamiliar.domain.pool.io.ImportFileType
import com.skrypko.dicefamiliar.domain.pool.io.ImportFileTypes
import com.skrypko.dicefamiliar.domain.pool.io.ImportedItem
import com.skrypko.dicefamiliar.domain.pool.io.PoolArchiveReader
import com.skrypko.dicefamiliar.domain.pool.io.PoolArchiveResult
import com.skrypko.dicefamiliar.domain.pool.io.PoolArchiveWriter
import com.skrypko.dicefamiliar.domain.pool.io.PoolImportError
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.withContext
import kotlin.time.Clock

/** A parsed import, its images already copied into the store (discard them with [PoolRepository.discardImages] on cancel). */
sealed interface PoolImportOutcome {
    data class Ready(val items: List<ImportedItem>, val images: Int, val missingImages: Int, val skippedFiles: Int) : PoolImportOutcome
    data class Failed(val error: PoolImportError) : PoolImportOutcome
}

interface PoolRepository {
    val pools: Flow<List<PoolSummary>>
    fun observePool(id: Long): Flow<Pool?>
    suspend fun pool(id: Long): Pool?

    /** Inserts ([Pool.id] = 0) or updates the pool and replaces its items; deletes images it no longer uses. Returns the id. */
    suspend fun save(pool: Pool): Long
    suspend fun delete(id: Long)
    suspend fun setShowNames(id: Long, show: Boolean)
    suspend fun setAside(poolId: Long, itemIds: Collection<Long>)
    suspend fun restoreAll(poolId: Long)

    /** Copies a picked image into the store; `null` if it isn't a readable PNG/JPG/WEBP. */
    suspend fun storeImage(source: StreamSource): String?

    /** Deletes stored images that were added to a draft but never saved. */
    suspend fun discardImages(paths: Collection<String>)
    fun imageFile(path: String): File

    /** The folder stored image paths are relative to. */
    val imageDir: File

    suspend fun readImport(source: StreamSource, fileName: String?, mime: String?): PoolImportOutcome

    /** Writes the pool as `name,color` CSV, or as a ZIP with `items.csv` + `images/`. */
    suspend fun export(items: List<PoolItem>, withImages: Boolean, out: OutputStream)

    /** Once per process: deletes stored images no pool references (left over from a crash or a cancelled edit). */
    suspend fun cleanUpImages()
}

@Singleton
class RoomPoolRepository @Inject constructor(
    private val dao: PoolDao,
    private val images: PoolImageStore,
) : PoolRepository {
    private val cleaned = AtomicBoolean(false)

    override val pools: Flow<List<PoolSummary>> = dao.observeSummaries()

    override fun observePool(id: Long): Flow<Pool?> =
        combine(dao.observePool(id), dao.observeItems(id)) { pool, items -> pool?.toDomain(items) }

    override suspend fun pool(id: Long): Pool? = dao.pool(id)?.toDomain(dao.items(id))

    override suspend fun save(pool: Pool): Long = withContext(Dispatchers.IO) {
        val before = if (pool.id == 0L) emptySet() else dao.items(pool.id).mapNotNull { it.imagePath }.toSet()
        val id = if (pool.id == 0L) {
            dao.insertPool(PoolEntity(name = pool.name.trim(), createdAt = Clock.System.now().toEpochMilliseconds(), showNames = pool.showNames))
        } else {
            dao.updatePool(pool.id, pool.name.trim(), pool.showNames)
            pool.id
        }
        dao.replaceItems(
            id,
            pool.items.mapIndexed { index, item ->
                PoolItemEntity(
                    poolId = id,
                    name = item.name.trim(),
                    nameNormalized = NameNormalizer.normalize(item.name),
                    imagePath = item.imagePath,
                    colorArgb = item.colorArgb,
                    position = index,
                    setAside = item.setAside,
                )
            },
        )
        images.delete(before - pool.items.mapNotNull { it.imagePath }.toSet())
        id
    }

    override suspend fun delete(id: Long) = withContext(Dispatchers.IO) {
        val paths = dao.items(id).mapNotNull { it.imagePath }
        dao.deletePool(id)
        images.delete(paths)
    }

    override suspend fun setShowNames(id: Long, show: Boolean) = dao.setShowNames(id, show)

    override suspend fun setAside(poolId: Long, itemIds: Collection<Long>) = dao.setAside(poolId, itemIds.toList())

    override suspend fun restoreAll(poolId: Long) = dao.restoreAll(poolId)

    override suspend fun storeImage(source: StreamSource): String? = withContext(Dispatchers.IO) {
        try {
            source.open()?.use { images.store(it) }
        } catch (e: IOException) {
            Log.w(TAG, "Can't open image", e)
            null
        }
    }

    override suspend fun discardImages(paths: Collection<String>) = withContext(Dispatchers.IO) {
        val used = dao.allImagePaths().toSet()
        images.delete(paths.filter { it !in used })
    }

    override fun imageFile(path: String): File = images.file(path)

    override val imageDir: File get() = images.dir

    override suspend fun readImport(source: StreamSource, fileName: String?, mime: String?): PoolImportOutcome =
        withContext(Dispatchers.IO) {
            val staging = images.newStagingDir()
            try {
                val input = try {
                    source.open()?.let { BufferedInputStream(it) }
                } catch (e: IOException) {
                    Log.w(TAG, "Can't open import", e)
                    null
                } ?: return@withContext PoolImportOutcome.Failed(PoolImportError.READ_FAILED)
                input.use { stream -> read(stream, fileName, mime, staging) }
            } finally {
                staging.deleteRecursively()
            }
        }

    private fun read(stream: BufferedInputStream, fileName: String?, mime: String?, staging: File): PoolImportOutcome {
        stream.mark(4)
        val head = ByteArray(2).also { it[0] = stream.read().toByte(); it[1] = stream.read().toByte() }
        stream.reset()
        return when (ImportFileTypes.detect(fileName, mime, head)) {
            ImportFileType.ZIP -> when (val result = PoolArchiveReader().read(stream, staging)) {
                is PoolArchiveResult.Failure -> PoolImportOutcome.Failed(result.error)
                is PoolArchiveResult.Success -> {
                    val stored = mutableMapOf<String, String?>()
                    var unreadable = 0
                    val items = result.items.map { item ->
                        val path = item.image?.let { image ->
                            stored.getOrPut(image) { images.store(File(result.rootDir, image)).also { if (it == null) unreadable++ } }
                        }
                        item.copy(image = path)
                    }
                    PoolImportOutcome.Ready(items, items.count { it.image != null }, result.missingImages + unreadable, result.skippedFiles)
                }
            }
            ImportFileType.CSV -> {
                val bytes = readCapped(stream, MAX_CSV_BYTES + 1)
                if (bytes.size > MAX_CSV_BYTES) return PoolImportOutcome.Failed(PoolImportError.TOO_LARGE)
                when (val parsed = CsvItemParser.parse(bytes.toString(Charsets.UTF_8), ignoreImages = true)) {
                    is CsvItemsResult.Failure -> PoolImportOutcome.Failed(parsed.error)
                    is CsvItemsResult.Success -> PoolImportOutcome.Ready(parsed.items, 0, 0, 0)
                }
            }
        }
    }

    override suspend fun export(items: List<PoolItem>, withImages: Boolean, out: OutputStream) = withContext(Dispatchers.IO) {
        val export = items.map { ExportItem(it.name, it.colorArgb, if (withImages) it.imagePath?.let(images::file) else null) }
        if (withImages) {
            PoolArchiveWriter.write(export, out)
        } else {
            out.write(CsvItemWriter.write(export).toByteArray(Charsets.UTF_8))
            out.flush()
        }
    }

    override suspend fun cleanUpImages() {
        if (!cleaned.compareAndSet(false, true)) return
        withContext(Dispatchers.IO) { images.deleteAllExcept(dao.allImagePaths().toSet()) }
    }

    private fun readCapped(input: InputStream, limit: Int): ByteArray {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(16 * 1024)
        while (out.size() < limit) {
            val n = input.read(buffer, 0, minOf(buffer.size, limit - out.size()))
            if (n < 0) break
            out.write(buffer, 0, n)
        }
        return out.toByteArray()
    }

    private companion object {
        const val TAG = "Pools"
        const val MAX_CSV_BYTES = 1024 * 1024
    }
}

internal fun PoolEntity.toDomain(items: List<PoolItemEntity>) = Pool(
    id = id,
    name = name,
    showNames = showNames,
    items = items.map { PoolItem(it.id, it.name, it.imagePath, it.colorArgb, it.setAside) },
)
