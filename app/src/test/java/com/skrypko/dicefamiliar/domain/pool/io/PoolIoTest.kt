package com.skrypko.dicefamiliar.domain.pool.io

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class CsvItemParserTest {
    private fun ok(text: String, ignoreImages: Boolean = false) =
        (CsvItemParser.parse(text, ignoreImages) as CsvItemsResult.Success).items

    @Test
    fun headerInAnyOrder() {
        val items = ok("color,name,image\n#E53935,Apple,images/apple.png\n,Cherry,\n")
        assertEquals(listOf(ImportedItem("Apple", "images/apple.png", 0xFFE53935.toInt()), ImportedItem("Cherry")), items)
    }

    @Test
    fun noHeaderReadsNameImageColor() {
        assertEquals(listOf(ImportedItem("Apple", "a.png", 0xFF000000.toInt())), ok("Apple,a.png,#000000\n"))
    }

    @Test
    fun exportedNameColorHeader() {
        assertEquals(listOf(ImportedItem("Pear", null, 0xFF43A047.toInt())), ok("name,color\nPear,#43A047\n"))
    }

    @Test
    fun quotingBlankRowsEmptyNamesAndBadColours() {
        val items = ok("name,image,color\n\"Apple, red\",,nope\n\n,x.png,#FFFFFF\n\"Say \"\"hi\"\"\",,\n")
        assertEquals(listOf(ImportedItem("Apple, red"), ImportedItem("Say \"hi\"")), items)
    }

    @Test
    fun plainCsvIgnoresImages() {
        assertNull(ok("name,image\nApple,a.png\n", ignoreImages = true).single().image)
    }

    @Test
    fun errors() {
        assertEquals(CsvItemsResult.Failure(PoolImportError.NO_ITEMS), CsvItemParser.parse("name,image,color\n"))
        assertEquals(CsvItemsResult.Failure(PoolImportError.BAD_CSV), CsvItemParser.parse("\"open"))
        assertEquals(CsvItemsResult.Failure(PoolImportError.TOO_MANY_ITEMS), CsvItemParser.parse("a\nb\nc\n", maxItems = 2))
    }
}

class ImportFileTypesTest {
    private val pk = byteArrayOf('P'.code.toByte(), 'K'.code.toByte())

    @Test
    fun detect() {
        assertEquals(ImportFileType.ZIP, ImportFileTypes.detect("pool.ZIP", "text/plain", ByteArray(0)))
        assertEquals(ImportFileType.CSV, ImportFileTypes.detect("pool.csv", "application/zip", pk))
        assertEquals(ImportFileType.ZIP, ImportFileTypes.detect(null, "application/x-zip-compressed", ByteArray(0)))
        assertEquals(ImportFileType.CSV, ImportFileTypes.detect("pool", "text/comma-separated-values", pk))
        assertEquals(ImportFileType.ZIP, ImportFileTypes.detect("download", "application/octet-stream", pk))
        assertEquals(ImportFileType.CSV, ImportFileTypes.detect("download", null, "na".toByteArray()))
    }
}

class PoolArchiveReaderTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private fun zip(vararg entries: Pair<String, ByteArray>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            entries.forEach { (name, content) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(content)
                zip.closeEntry()
            }
        }
        return out.toByteArray()
    }

    private fun read(bytes: ByteArray, limits: PoolArchiveLimits = PoolArchiveLimits()) =
        PoolArchiveReader(limits).read(ByteArrayInputStream(bytes), tmp.newFolder())

    @Test
    fun itemsWithImagesAndAMissingOne() {
        val result = read(
            zip(
                "items.csv" to "name,image,color\nApple,images/Apple.PNG,#E53935\nGhost,images/none.png,\nCherry,,\n".toByteArray(),
                "images/apple.png" to "png".toByteArray(),
                "notes.txt" to "x".toByteArray(),
            ),
        ) as PoolArchiveResult.Success
        assertEquals(listOf("Apple", "Ghost", "Cherry"), result.items.map { it.name })
        assertEquals("images/apple.png", result.items[0].image)
        assertNull(result.items[1].image)
        assertEquals(1, result.missingImages)
        assertEquals(1, result.skippedFiles)
        assertTrue(File(result.rootDir, "images/apple.png").isFile)
    }

    @Test
    fun oneWrapperFolderIsFine() {
        val result = read(zip("pool/items.csv" to "Apple,images/a.png\n".toByteArray(), "pool/images/a.png" to "p".toByteArray()))
            as PoolArchiveResult.Success
        assertEquals("images/a.png", result.items.single().image)
        assertTrue(File(result.rootDir, "images/a.png").isFile)
    }

    @Test
    fun oversizedImageIsDroppedNotFatal() {
        val result = read(
            zip("items.csv" to "Apple,big.png\n".toByteArray(), "big.png" to ByteArray(20)),
            PoolArchiveLimits(maxImageBytes = 10),
        ) as PoolArchiveResult.Success
        assertNull(result.items.single().image)
        assertEquals(1, result.missingImages)
    }

    @Test
    fun failures() {
        assertEquals(PoolArchiveResult.Failure(PoolImportError.NOT_A_ZIP), read("name\nApple\n".toByteArray()))
        assertEquals(PoolArchiveResult.Failure(PoolImportError.NO_ITEMS_CSV), read(zip("a.png" to "p".toByteArray())))
        assertEquals(PoolArchiveResult.Failure(PoolImportError.UNSAFE_PATH), read(zip("../evil.csv" to "x".toByteArray())))
        assertEquals(PoolArchiveResult.Failure(PoolImportError.UNSAFE_PATH), read(zip("/abs.csv" to "x".toByteArray())))
        assertEquals(
            PoolArchiveResult.Failure(PoolImportError.TOO_MANY_ITEMS),
            read(zip("items.csv" to "a".toByteArray(), "b.png" to "p".toByteArray()), PoolArchiveLimits(maxEntries = 1)),
        )
        assertEquals(
            PoolArchiveResult.Failure(PoolImportError.TOO_LARGE),
            read(zip("items.csv" to "a".toByteArray(), "b.png" to ByteArray(30)), PoolArchiveLimits(maxTotalBytes = 20)),
        )
    }
}

class PoolExportTest {
    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun csvOnly() {
        val csv = CsvItemWriter.write(listOf(ExportItem("Apple, red", 0xFFE53935.toInt()), ExportItem("Plain")))
        assertEquals("name,color\n\"Apple, red\",#E53935\nPlain,\n", csv)
    }

    @Test
    fun safeUniqueFileNames() {
        val names = SafeFileNames()
        assertEquals("Apple_Pie.png", names.next("Apple / Pie", "PNG"))
        assertEquals("apple_pie_2.png", names.next("apple pie", "png"))
        assertEquals("item.jpg", names.next("???", "jpg"))
        assertEquals("Fruit_ mix.zip", SafeFileNames.forPool("Fruit: mix", "zip"))
    }

    /** Export then import: names, colours and image bytes survive. */
    @Test
    fun roundTrip() {
        val apple = tmp.newFile("a1.png").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        val apple2 = tmp.newFile("a2.jpg").apply { writeBytes(byteArrayOf(4, 5)) }
        val items = listOf(
            ExportItem("Apple", 0xFFE53935.toInt(), apple),
            ExportItem("apple", null, apple2),
            ExportItem("Cherry \"sour\", dark", 0x80112233.toInt()),
        )
        val out = ByteArrayOutputStream()
        PoolArchiveWriter.write(items, out)
        ZipInputStream(ByteArrayInputStream(out.toByteArray())).use { zip ->
            assertEquals("items.csv", zip.nextEntry!!.name)
        }

        val result = PoolArchiveReader().read(ByteArrayInputStream(out.toByteArray()), tmp.newFolder()) as PoolArchiveResult.Success
        assertEquals(items.map { it.name }, result.items.map { it.name })
        assertEquals(items.map { it.colorArgb }, result.items.map { it.colorArgb })
        assertEquals(0, result.missingImages)
        assertArrayEquals(byteArrayOf(1, 2, 3), File(result.rootDir, result.items[0].image!!).readBytes())
        assertArrayEquals(byteArrayOf(4, 5), File(result.rootDir, result.items[1].image!!).readBytes())
        assertNull(result.items[2].image)

        val csv = CsvItemParser.parse(CsvItemWriter.write(items)) as CsvItemsResult.Success
        assertEquals(items.map { it.name to it.colorArgb }, csv.items.map { it.name to it.colorArgb })
    }
}

/** The committed example pools in docs/pool-examples (tools/gen_pool_examples.py) must import cleanly. */
class PoolExamplesTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private fun example(name: String) =
        listOf(File("../docs/pool-examples", name), File("docs/pool-examples", name)).first { it.isFile }

    @Test
    fun classesHaveAnImageAndColourEach() {
        val result = example("classes.zip").inputStream().use { PoolArchiveReader().read(it, tmp.newFolder()) } as PoolArchiveResult.Success
        assertEquals(13, result.items.size)
        assertEquals("Warrior", result.items.first().name)
        assertEquals(0, result.missingImages)
        result.items.forEach { item ->
            assertTrue(item.name, File(result.rootDir, item.image!!).isFile)
            assertTrue(item.name, item.colorArgb != null)
        }
    }

    @Test
    fun drinksAreNamesWithColours() {
        val items = (CsvItemParser.parse(example("drinks.csv").readText(), ignoreImages = true) as CsvItemsResult.Success).items
        assertEquals(24, items.size)
        assertEquals("Lemonade", items.first().name)
        assertTrue(items.all { it.image == null && it.colorArgb != null })
    }
}
