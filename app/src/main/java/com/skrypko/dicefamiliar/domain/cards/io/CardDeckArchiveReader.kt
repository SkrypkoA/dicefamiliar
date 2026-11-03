package com.skrypko.dicefamiliar.domain.cards.io

import com.skrypko.dicefamiliar.domain.cards.CardFace
import com.skrypko.dicefamiliar.domain.cards.CardLimits
import java.io.BufferedInputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.zip.ZipInputStream

data class CardArchiveLimits(
    val maxEntries: Int = 1200,
    val maxTotalBytes: Long = 200L * 1024 * 1024,
    val maxImageBytes: Long = 10L * 1024 * 1024,
    val maxCsvBytes: Long = 1024L * 1024,
    val maxCards: Int = CardLimits.MAX_CARDS,
)

sealed interface CardArchiveResult {
    /**
     * The deck was extracted into [rootDir]. Card [CardFace.image] paths and [backImage] are relative
     * to [rootDir]. [skippedFiles] were unsupported or too large; [missingImages] are CSV references
     * to images that aren't in the archive (those cards are kept as text cards).
     */
    data class Success(
        val cards: List<CardFace>,
        val backImage: String?,
        val rootDir: File,
        val skippedFiles: Int,
        val missingImages: Int,
    ) : CardArchiveResult

    data class Failure(val error: CardDeckImportError) : CardArchiveResult
}

/**
 * Reads a card-deck ZIP into a staging folder. Two layouts (optionally inside one top-level folder):
 * 1. a `.csv` at the root (`name,value,suit,image`) plus the images it references;
 * 2. images only: one card per image, in natural file-name order, named after the file.
 * `back.png|jpg|jpeg|webp` at the root is the card back, never a card. Rejects zip-slip paths and caps
 * entries, sizes and the number of cards.
 */
class CardDeckArchiveReader(private val limits: CardArchiveLimits = CardArchiveLimits()) {

    fun read(input: InputStream, stagingDir: File): CardArchiveResult = try {
        extract(input, stagingDir)
    } catch (_: IOException) {
        CardArchiveResult.Failure(CardDeckImportError.READ_FAILED)
    } catch (_: IllegalArgumentException) {
        CardArchiveResult.Failure(CardDeckImportError.READ_FAILED)
    }

    private fun extract(input: InputStream, stagingDir: File): CardArchiveResult {
        val buffered = BufferedInputStream(input)
        buffered.mark(4)
        if (buffered.read() != 'P'.code || buffered.read() != 'K'.code) return CardArchiveResult.Failure(CardDeckImportError.NOT_A_ZIP)
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
                if (++entries > limits.maxEntries) return CardArchiveResult.Failure(CardDeckImportError.TOO_MANY_CARDS)
                val name = entry.name.replace('\\', '/')
                if (!isSafe(name)) return CardArchiveResult.Failure(CardDeckImportError.UNSAFE_PATH)
                if (entry.isDirectory || isJunk(name)) continue
                val cap = capFor(name)
                if (cap == null) {
                    skipped++
                    continue
                }
                val target = File(root, name).canonicalFile
                if (!target.path.startsWith(root.path + File.separator)) return CardArchiveResult.Failure(CardDeckImportError.UNSAFE_PATH)
                target.parentFile?.mkdirs()
                val copied = copyCapped(zip, target, cap)
                total += copied
                if (total > limits.maxTotalBytes) return CardArchiveResult.Failure(CardDeckImportError.TOO_LARGE)
                if (copied > cap) {
                    target.delete()
                    skipped++
                } else {
                    written += name
                }
            }
        }

        val prefix = wrapperPrefix(written)
        val files = written.filter { it.startsWith(prefix) }.map { it.removePrefix(prefix) }.toSet()
        val deckDir = if (prefix.isEmpty()) root else File(root, prefix.trimEnd('/'))
        val back = files.firstOrNull { '/' !in it && it.substringBeforeLast('.').lowercase() == "back" && isImage(it) }
        val csv = files.filter { '/' !in it && it.lowercase().endsWith(".csv") }.sorted().firstOrNull()

        return if (csv != null) {
            when (val parsed = CardCsvParser.parse(File(deckDir, csv).readText(), limits.maxCards)) {
                is CardCsvResult.Failure -> CardArchiveResult.Failure(parsed.error)
                is CardCsvResult.Success -> {
                    val byLower = files.associateBy { it.lowercase() }
                    var missing = 0
                    val cards = parsed.cards.map { card ->
                        val path = card.image?.trim()?.trimStart('/')?.let { byLower[it.lowercase()] }
                        if (card.image != null && path == null) missing++
                        card.copy(image = path)
                    }
                    CardArchiveResult.Success(cards, back, deckDir, skipped, missing)
                }
            }
        } else {
            val images = files.filter { isImage(it) && it != back }.sortedWith(NaturalOrder)
            when {
                images.isEmpty() -> CardArchiveResult.Failure(CardDeckImportError.NO_CARDS)
                images.size > limits.maxCards -> CardArchiveResult.Failure(CardDeckImportError.TOO_MANY_CARDS)
                else -> CardArchiveResult.Success(images.map { CardFace(nameFromFile(it), image = it) }, back, deckDir, skipped, 0)
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

    private fun isImage(name: String) = name.substringAfterLast('.', "").lowercase() in IMAGE_EXTENSIONS

    private fun capFor(name: String): Long? = when {
        isImage(name) -> limits.maxImageBytes
        name.lowercase().endsWith(".csv") -> limits.maxCsvBytes
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
        val IMAGE_EXTENSIONS = setOf("png", "jpg", "jpeg", "webp")

        /** `"cards/10_of_hearts.png"` → `"10 of hearts"`. */
        fun nameFromFile(path: String): String =
            path.substringAfterLast('/').substringBeforeLast('.').replace('_', ' ').replace('-', ' ').trim()
                .ifEmpty { path }.take(80)
    }
}

/** Compares paths so that `card2` comes before `card10`. */
object NaturalOrder : Comparator<String> {
    private val CHUNK = Regex("\\d+|\\D+")

    override fun compare(a: String, b: String): Int {
        val x = CHUNK.findAll(a.lowercase()).map { it.value }.toList()
        val y = CHUNK.findAll(b.lowercase()).map { it.value }.toList()
        for (i in 0 until minOf(x.size, y.size)) {
            val p = x[i]
            val q = y[i]
            val c = if (p[0].isDigit() && q[0].isDigit()) {
                p.trimStart('0').length.compareTo(q.trimStart('0').length).takeIf { it != 0 } ?: p.trimStart('0').compareTo(q.trimStart('0'))
            } else {
                p.compareTo(q)
            }
            if (c != 0) return c
        }
        return x.size.compareTo(y.size)
    }
}
