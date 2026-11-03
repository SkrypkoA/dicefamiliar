package com.skrypko.dicefamiliar.domain.pool.io

import com.skrypko.dicefamiliar.domain.pool.ItemColors
import java.io.File
import java.io.OutputStream
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** An item to export; [image] is the stored image file, if any. */
data class ExportItem(val name: String, val colorArgb: Int? = null, val image: File? = null)

/** Writes pool CSV (RFC 4180, LF line ends, header row). */
object CsvItemWriter {

    /** `name,color`: the CSV-only export (images dropped). */
    fun write(items: List<ExportItem>): String = buildString {
        append("name,color\n")
        items.forEach { append(row(it.name, it.colorArgb?.let(ItemColors::format).orEmpty())) }
    }

    /** `name,image,color` with the given archive paths (`""` for no image). */
    fun writeWithImages(items: List<Pair<ExportItem, String>>): String = buildString {
        append("name,image,color\n")
        items.forEach { (item, path) -> append(row(item.name, path, item.colorArgb?.let(ItemColors::format).orEmpty())) }
    }

    private fun row(vararg cells: String) = cells.joinToString(",", postfix = "\n") { quote(it) }

    fun quote(cell: String): String =
        if (cell.any { it == ',' || it == '"' || it == '\n' || it == '\r' } || cell != cell.trim()) {
            "\"" + cell.replace("\"", "\"\"") + "\""
        } else {
            cell
        }
}

/** Writes a pool ZIP: `items.csv` + `images/<safe-unique-name>.<ext>`, readable by [PoolArchiveReader]. */
object PoolArchiveWriter {

    fun write(items: List<ExportItem>, out: OutputStream) {
        val names = SafeFileNames()
        val paths = items.map { item ->
            val image = item.image?.takeIf { it.isFile }
            item to (image?.let { "images/" + names.next(item.name, it.extension.ifEmpty { "png" }) } ?: "")
        }
        ZipOutputStream(out).use { zip ->
            zip.putNextEntry(ZipEntry(PoolArchiveReader.ITEMS_CSV))
            zip.write(CsvItemWriter.writeWithImages(paths).toByteArray(Charsets.UTF_8))
            zip.closeEntry()
            paths.forEach { (item, path) ->
                if (path.isEmpty()) return@forEach
                zip.putNextEntry(ZipEntry(path))
                item.image!!.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }
    }
}

/** File-system-safe, unique (case-insensitive) file names built from item names. */
class SafeFileNames {
    private val used = HashSet<String>()

    fun next(name: String, extension: String): String {
        val ext = extension.lowercase(Locale.ROOT)
        val base = safe(name)
        var candidate = "$base.$ext"
        var n = 2
        while (!used.add(candidate.lowercase(Locale.ROOT))) candidate = "${base}_${n++}.$ext"
        return candidate
    }

    companion object {
        /** Letters and digits (any script), `-` and `_`; other runs become `_`; at most 60 characters. */
        fun safe(name: String): String =
            name.trim().replace(Regex("[^\\p{L}\\p{N}_-]+"), "_").trim('_').take(60).ifEmpty { "item" }

        /** A safe export file name for a pool: `<pool-name>.<ext>`. */
        fun forPool(poolName: String, extension: String): String =
            poolName.replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]+"), "_").trim().take(80).ifEmpty { "pool" } + ".$extension"
    }
}
