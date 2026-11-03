package com.skrypko.dicefamiliar.domain.dice.skin

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DiceSetArchiveReaderTest {

    @get:Rule
    val temp = TemporaryFolder()

    private val png = byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte(), 1, 2, 3)

    private fun zip(vararg entries: Pair<String, ByteArray>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            entries.forEach { (name, bytes) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
        return out.toByteArray()
    }

    private fun read(bytes: ByteArray, limits: DiceSetArchiveLimits = DiceSetArchiveLimits()) =
        DiceSetArchiveReader(limits).read(ByteArrayInputStream(bytes), File(temp.root, "staging"), "My Pack")

    private val manifest = """
        {"formatVersion":1,"id":"wood","name":"Wood",
         "dice":{"d6":{"image2d":"2d/d6.png"},"d20":{"image2d":"2d/d20.png"}}}
    """.trimIndent().toByteArray()

    @Test
    fun readsSetWithManifest() {
        val result = read(zip("manifest.json" to manifest, "2d/d6.png" to png, "notes.txt" to "hi".toByteArray()))
        result as DiceSetArchiveResult.Success
        assertEquals("wood", result.manifest.id)
        assertTrue(result.hadManifest)
        assertEquals(1, result.skippedFiles)
        assertEquals("d20 image is missing", 1, result.missingFiles)
        assertTrue(File(result.rootDir, "2d/d6.png").readBytes().contentEquals(png))
        assertFalse(File(result.rootDir, "notes.txt").exists())
    }

    @Test
    fun stripsSingleWrapperFolder() {
        val result = read(zip("wood/manifest.json" to manifest, "wood/2d/d6.png" to png, "wood/2d/d20.png" to png))
        result as DiceSetArchiveResult.Success
        assertEquals(0, result.missingFiles)
        assertTrue(File(result.rootDir, DiceSetFormat.MANIFEST).isFile)
        assertTrue(File(result.rootDir, "2d/d6.png").isFile)
    }

    @Test
    fun buildsManifestFromFileNames() {
        val result = read(zip("d6.png" to png, "3d/d6_face.png" to png, "__MACOSX/._d6.png" to png))
        result as DiceSetArchiveResult.Success
        assertFalse(result.hadManifest)
        assertEquals("my_pack", result.manifest.id)
        assertEquals("My Pack", result.manifest.name)
        assertEquals(DieStyleSpec(image2d = "d6.png", faceTexture3d = "3d/d6_face.png"), result.manifest.dice["d6"])
        assertEquals(0, result.skippedFiles)
    }

    @Test
    fun conventionalFolderIsNotTreatedAsWrapper() {
        val result = read(zip("2d/d6.png" to png, "2d/d8.png" to png))
        result as DiceSetArchiveResult.Success
        assertEquals(setOf("d6", "d8"), result.manifest.dice.keys)
    }

    @Test
    fun rejectsZipSlip() {
        assertFailure(DiceSetImportError.UNSAFE_PATH, read(zip("../evil.png" to png)))
        assertFailure(DiceSetImportError.UNSAFE_PATH, read(zip("a/../../evil.png" to png)))
        assertFailure(DiceSetImportError.UNSAFE_PATH, read(zip("/abs/d6.png" to png)))
        assertFailure(DiceSetImportError.UNSAFE_PATH, read(zip("..\\evil.png" to png)))
        assertFalse(File(temp.root, "evil.png").exists())
    }

    @Test
    fun rejectsNonZip() {
        assertFailure(DiceSetImportError.NOT_A_ZIP, read("name,image\n".toByteArray()))
        assertFailure(DiceSetImportError.NOT_A_ZIP, read(ByteArray(0)))
    }

    @Test
    fun rejectsInvalidManifest() {
        assertFailure(DiceSetImportError.INVALID_MANIFEST, read(zip("manifest.json" to "{oops".toByteArray())))
        val v2 = """{"formatVersion":2,"id":"x","name":"X"}""".toByteArray()
        assertFailure(DiceSetImportError.INVALID_MANIFEST, read(zip("manifest.json" to v2)))
    }

    @Test
    fun nothingUsefulFails() {
        assertFailure(DiceSetImportError.NOTHING_TO_IMPORT, read(zip("readme.txt" to "x".toByteArray())))
        assertFailure(DiceSetImportError.NOTHING_TO_IMPORT, read(zip("cover.png" to png)))
    }

    @Test
    fun enforcesLimits() {
        val small = DiceSetArchiveLimits(maxEntries = 2, maxTotalBytes = 20, maxImageBytes = 10)
        assertFailure(
            DiceSetImportError.TOO_MANY_ENTRIES,
            read(zip("d4.png" to png, "d6.png" to png, "d8.png" to png), small),
        )
        assertFailure(
            DiceSetImportError.TOO_LARGE,
            read(zip("d4.png" to png, "d6.png" to ByteArray(15)), small.copy(maxEntries = 10, maxImageBytes = 100)),
        )
        val oversize = read(zip("d4.png" to png, "d6.png" to ByteArray(11)), small.copy(maxTotalBytes = 1000))
        oversize as DiceSetArchiveResult.Success
        assertEquals(1, oversize.skippedFiles)
        assertEquals(setOf("d4"), oversize.manifest.dice.keys)
    }

    private fun assertFailure(expected: DiceSetImportError, result: DiceSetArchiveResult) {
        assertTrue("expected $expected but was $result", result is DiceSetArchiveResult.Failure)
        assertEquals(expected, (result as DiceSetArchiveResult.Failure).error)
    }
}
