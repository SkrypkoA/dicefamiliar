package com.skrypko.dicefamiliar.domain.pool.io

import com.skrypko.dicefamiliar.domain.pool.PoolLimits
import java.io.BufferedInputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.Locale
import java.util.zip.ZipInputStream

data class PoolArchiveLimits(
    val maxEntries: Int = 1000,
    val maxTotalBytes: Long = 200L * 1024 * 1024,
    val maxImageBytes: Long = 10L * 1024 * 1024,
    val maxCsvBytes: Long = 1024L * 1024,
    val maxItems: Int = PoolLimits.MAX_ITEMS,
)

sealed interface PoolArchiveResult {
    /**
     * Items read from `items.csv`; an item's [ImportedItem.image] is relative to [rootDir], or `null` when
     * the CSV had none. [missingImages] counts references to images that are absent, unsupported or too large
     * (those items are kept without an image); [skippedFiles] counts files of other types that were ignored.
     */
    data class Success(
        val items: List<ImportedItem>,
        val rootDir: File,
        val missingImages: Int,
        val skippedFiles: Int,
    ) : PoolArchiveResult

    data class Failure(val error: PoolImportError) : PoolArchiveResult
}

/**
 * Reads a pool ZIP (`items.csv` + `images/`, optionally inside one top-level folder) into [stagingDir].
 * Rejects zip-slip paths, caps entries and sizes, and never writes outside the staging folder.
 */
class PoolArchiveReader(private val limits: PoolArchiveLimits = PoolArchiveLimits()) {

    fun read(input: InputStream, stagingDir: File): PoolArchiveResult = try {
        extract(input, stagingDir)
    } catch (_: IOException) {
        PoolArchiveResult.Failure(PoolImportError.READ_FAILED)
    } catch (_: IllegalArgumentException) {
        PoolArchiveResult.Failure(PoolImportError.READ_FAILED)
    }

    private fun extract(input: InputStream, stagingDir: File): PoolArchiveResult {
        val buffered = BufferedInputStream(input)
        buffered.mark(4)
        if (buffered.read() != 'P'.code || buffered.read() != 'K'.code) return PoolArchiveResult.Failure(PoolImportError.NOT_A_ZIP)
        buffered.reset()

        stagingDir.mkdirs()
        val root = stagingDir.canonicalFile
        val written = linkedSetOf<String>()
        var entries = 0
        var total = 0L
        var skipped = 0
        ZipInputStream(buffered).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (++entries > limits.maxEntries) return PoolArchiveResult.Failure(PoolImportError.TOO_MANY_ITEMS)
                val name = entry.name.replace('\\', '/')
                if (!isSafe(name)) return PoolArchiveResult.Failure(PoolImportError.UNSAFE_PATH)
                if (entry.isDirectory || isJunk(name)) continue
                val cap = capFor(name)
                if (cap == null) {
                    skipped++
                    continue
                }
                val target = File(root, name).canonicalFile
                if (!target.path.startsWith(root.path + File.separator)) return PoolArchiveResult.Failure(PoolImportError.UNSAFE_PATH)
                target.parentFile?.mkdirs()
                val copied = copyCapped(zip, target, cap)
                total += copied
                if (total > limits.maxTotalBytes) return PoolArchiveResult.Failure(PoolImportError.TOO_LARGE)
                if (copied > cap) {
                    target.delete()
                    if (!isImage(name)) return PoolArchiveResult.Failure(PoolImportError.TOO_LARGE)
                } else {
                    written += name
                }
            }
        }

        val prefix = wrapperPrefix(written)
        val files = written.filter { it.startsWith(prefix) }.map { it.removePrefix(prefix) }.toSet()
        val poolDir = if (prefix.isEmpty()) root else File(root, prefix.trimEnd('/'))
        val rootCsvs = files.filter { '/' !in it && it.lowercase(Locale.ROOT).endsWith(".csv") }.sorted()
        val csv = rootCsvs.firstOrNull { it.equals(ITEMS_CSV, ignoreCase = true) } ?: rootCsvs.firstOrNull()
            ?: return PoolArchiveResult.Failure(PoolImportError.NO_ITEMS_CSV)

        return when (val parsed = CsvItemParser.parse(File(poolDir, csv).readText(), maxItems = limits.maxItems)) {
            is CsvItemsResult.Failure -> PoolArchiveResult.Failure(parsed.error)
            is CsvItemsResult.Success -> {
                val images = files.filter { isImage(it) }.associateBy { it.lowercase(Locale.ROOT) }
                var missing = 0
                val items = parsed.items.map { item ->
                    val path = item.image?.trimStart('/')?.replace('\\', '/')?.let { images[it.lowercase(Locale.ROOT)] }
                    if (item.image != null && path == null) missing++
                    item.copy(image = path)
                }
                PoolArchiveResult.Success(items, poolDir, missing, skipped)
            }
        }
    }

    /** `""`, or `folder/` when every file sits in one top-level folder. */
    private fun wrapperPrefix(files: Set<String>): String {
        if (files.isEmpty() || files.any { '/' !in it }) return ""
        val top = files.map { it.substringBefore('/') }.toSet().singleOrNull() ?: return ""
        return "$top/"
    }

    private fun isSafe(name: String) =
        name.isNotEmpty() && !name.startsWith("/") && ':' !in name && name.split('/').none { it == ".." }

    private fun isJunk(name: String) = name.startsWith("__MACOSX/") || name.substringAfterLast('/').startsWith(".")

    private fun isImage(name: String) = name.substringAfterLast('.', "").lowercase(Locale.ROOT) in IMAGE_EXTENSIONS

    private fun capFor(name: String): Long? = when {
        isImage(name) -> limits.maxImageBytes
        name.lowercase(Locale.ROOT).endsWith(".csv") -> limits.maxCsvBytes
        else -> null
    }

    private fun copyCapped(input: InputStream, target: File, cap: Long): Long {
        var copied = 0L
        val buffer = ByteArray(16 * 1024)
        target.outputStream().use { out ->
            while (copied <= cap) {
                val n = input.read(buffer)
                if (n < 0) break
                out.write(buffer, 0, n)
                copied += n
            }
        }
        return copied
    }

    companion object {
        const val ITEMS_CSV = "items.csv"
        val IMAGE_EXTENSIONS = setOf("png", "jpg", "jpeg", "webp")
    }
}
