package com.skrypko.dicefamiliar.domain.pool.io

import com.skrypko.dicefamiliar.domain.io.CsvReader
import com.skrypko.dicefamiliar.domain.pool.ItemColors
import com.skrypko.dicefamiliar.domain.pool.PoolLimits
import java.util.Locale

enum class PoolImportError { NOT_A_ZIP, NO_ITEMS_CSV, NO_ITEMS, BAD_CSV, UNSAFE_PATH, TOO_LARGE, TOO_MANY_ITEMS, READ_FAILED }

/** A row read from an import: [image] is a path inside the archive (or staging folder), [colorArgb] is ARGB. */
data class ImportedItem(val name: String, val image: String? = null, val colorArgb: Int? = null)

sealed interface CsvItemsResult {
    data class Success(val items: List<ImportedItem>) : CsvItemsResult
    data class Failure(val error: PoolImportError) : CsvItemsResult
}

/**
 * Reads pool items from CSV: UTF-8, RFC 4180, optional header `name,image,color` (any order, and
 * `name,color` from a CSV export). Without a header the columns are `name,image,color`. Rows without a
 * name are skipped; an invalid colour is dropped. Names are not de-duplicated here (see UniqueNameResolver).
 */
object CsvItemParser {

    private val KNOWN = setOf("name", "image", "color", "colour")

    fun parse(text: String, ignoreImages: Boolean = false, maxItems: Int = PoolLimits.MAX_ITEMS): CsvItemsResult {
        val rows = try {
            CsvReader.read(text)
        } catch (_: IllegalArgumentException) {
            return CsvItemsResult.Failure(PoolImportError.BAD_CSV)
        }
        if (rows.isEmpty()) return CsvItemsResult.Failure(PoolImportError.NO_ITEMS)
        val first = rows.first().map { it.trim().lowercase(Locale.ROOT) }
        val hasHeader = "name" in first && first.all { it in KNOWN || it.isEmpty() }
        val nameCol = if (hasHeader) first.indexOf("name") else 0
        val imageCol = if (hasHeader) first.indexOf("image") else 1
        val colorCol = if (hasHeader) first.indexOfFirst { it == "color" || it == "colour" } else 2
        val body = if (hasHeader) rows.drop(1) else rows

        val items = mutableListOf<ImportedItem>()
        for (row in body) {
            fun cell(i: Int) = row.getOrNull(i)?.trim()?.takeIf { i >= 0 && it.isNotEmpty() }
            val name = cell(nameCol)?.take(PoolLimits.MAX_NAME_LENGTH) ?: continue
            if (items.size == maxItems) return CsvItemsResult.Failure(PoolImportError.TOO_MANY_ITEMS)
            items += ImportedItem(
                name = name,
                image = if (ignoreImages) null else cell(imageCol),
                colorArgb = ItemColors.parse(cell(colorCol)),
            )
        }
        return if (items.isEmpty()) CsvItemsResult.Failure(PoolImportError.NO_ITEMS) else CsvItemsResult.Success(items)
    }
}

enum class ImportFileType { CSV, ZIP }

object ImportFileTypes {
    private val ZIP_MIMES = setOf("application/zip", "application/x-zip-compressed", "application/x-zip")
    private val CSV_MIMES = setOf("text/csv", "text/comma-separated-values", "text/plain", "application/csv")

    /** The OpenDocument MIME filter for pool import. */
    val MIME_TYPES = arrayOf(
        "text/csv", "text/comma-separated-values", "text/plain", "application/zip", "application/x-zip-compressed",
    )

    /** From the file extension, then the MIME type, then the `PK` magic bytes in [head]. */
    fun detect(fileName: String?, mime: String?, head: ByteArray): ImportFileType {
        when (fileName?.substringAfterLast('.', "")?.lowercase(Locale.ROOT)) {
            "zip" -> return ImportFileType.ZIP
            "csv", "txt" -> return ImportFileType.CSV
        }
        when (mime?.lowercase(Locale.ROOT)) {
            in ZIP_MIMES -> return ImportFileType.ZIP
            in CSV_MIMES -> return ImportFileType.CSV
        }
        return if (head.size >= 2 && head[0] == 'P'.code.toByte() && head[1] == 'K'.code.toByte()) ImportFileType.ZIP else ImportFileType.CSV
    }
}
