package com.skrypko.dicefamiliar.domain.cards.io

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class CardDeckArchiveReaderTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun zip(vararg entries: Pair<String, String>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            entries.forEach { (name, content) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(content.toByteArray())
                zip.closeEntry()
            }
        }
        return out.toByteArray()
    }

    private fun read(bytes: ByteArray, limits: CardArchiveLimits = CardArchiveLimits()) =
        CardDeckArchiveReader(limits).read(ByteArrayInputStream(bytes), tmp.newFolder())

    @Test
    fun csvWithImages() {
        val result = read(
            zip(
                "cards.csv" to "name,value,suit,image\nRam One,1,Rams,images/r1.png\nGhost,2,Crows,images/missing.png\n",
                "images/r1.png" to "png",
                "back.png" to "png",
                "notes.txt" to "x",
            ),
        ) as CardArchiveResult.Success
        assertEquals(listOf("Ram One", "Ghost"), result.cards.map { it.name })
        assertEquals("images/r1.png", result.cards[0].image)
        assertNull(result.cards[1].image)
        assertEquals(1, result.missingImages)
        assertEquals("back.png", result.backImage)
        assertEquals(1, result.skippedFiles)
        assertTrue(java.io.File(result.rootDir, "images/r1.png").exists())
    }

    @Test
    fun imagesOnlyInNaturalOrderInsideAWrapperFolder() {
        val result = read(
            zip("deck/card10.png" to "x", "deck/card2.png" to "x", "deck/the_fool.webp" to "x", "deck/back.jpg" to "x"),
        ) as CardArchiveResult.Success
        assertEquals(listOf("card2", "card10", "the fool"), result.cards.map { it.name })
        assertEquals("back.jpg", result.backImage)
        assertTrue(result.rootDir.path.endsWith("deck"))
    }

    @Test
    fun rejections() {
        fun error(bytes: ByteArray, limits: CardArchiveLimits = CardArchiveLimits()) =
            (read(bytes, limits) as CardArchiveResult.Failure).error
        assertEquals(CardDeckImportError.NOT_A_ZIP, error("name\nA".toByteArray()))
        assertEquals(CardDeckImportError.UNSAFE_PATH, error(zip("../evil.png" to "x")))
        assertEquals(CardDeckImportError.NO_CARDS, error(zip("readme.txt" to "x", "back.png" to "x")))
        assertEquals(
            CardDeckImportError.TOO_MANY_CARDS,
            error(zip("a.png" to "x", "b.png" to "x", "c.png" to "x"), CardArchiveLimits(maxCards = 2)),
        )
        assertEquals(CardDeckImportError.TOO_LARGE, error(zip("a.png" to "x".repeat(100)), CardArchiveLimits(maxTotalBytes = 10)))
    }
}
