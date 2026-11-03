package com.skrypko.dicefamiliar.domain.dice.skin

import java.io.BufferedInputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.zip.ZipInputStream

data class DiceSetArchiveLimits(
    val maxEntries: Int = 300,
    val maxTotalBytes: Long = 50L * 1024 * 1024,
    val maxImageBytes: Long = 10L * 1024 * 1024,
    val maxFontBytes: Long = 5L * 1024 * 1024,
    val maxManifestBytes: Long = 256L * 1024,
)

enum class DiceSetImportError {
    NOT_A_ZIP,
    UNSAFE_PATH,
    TOO_MANY_ENTRIES,
    TOO_LARGE,
    NOTHING_TO_IMPORT,
    INVALID_MANIFEST,
    READ_FAILED,
}

sealed interface DiceSetArchiveResult {
    /**
     * The pack was extracted; [rootDir] holds the set files (manifest not yet written when [hadManifest] is false).
     * [skippedFiles] were unsupported or too large; [missingFiles] are referenced by the manifest but absent.
     */
    data class Success(
        val manifest: DiceSetManifest,
        val rootDir: File,
        val hadManifest: Boolean,
        val skippedFiles: Int,
        val missingFiles: Int,
    ) : DiceSetArchiveResult

    data class Failure(val error: DiceSetImportError, val detail: String? = null) : DiceSetArchiveResult
}

/**
 * Extracts a dice-set ZIP into a staging folder. The pack is either a set folder (with `manifest.json`)
 * or plain images named by convention (see [DiceSetConventions]); both may sit inside one top-level folder.
 * Rejects zip-slip paths, caps entry count and sizes, and only keeps manifest, image and font files.
 */
class DiceSetArchiveReader(private val limits: DiceSetArchiveLimits = DiceSetArchiveLimits()) {

    fun read(input: InputStream, stagingDir: File, fallbackName: String): DiceSetArchiveResult = try {
        extract(input, stagingDir, fallbackName)
    } catch (e: IOException) {
        DiceSetArchiveResult.Failure(DiceSetImportError.READ_FAILED, e.message)
    } catch (e: IllegalArgumentException) {
        // ZipInputStream throws this for malformed entry names.
        DiceSetArchiveResult.Failure(DiceSetImportError.READ_FAILED, e.message)
    }

    private fun extract(input: InputStream, stagingDir: File, fallbackName: String): DiceSetArchiveResult {
        val buffered = BufferedInputStream(input)
        buffered.mark(MAGIC_LENGTH)
        if (buffered.read() != 'P'.code || buffered.read() != 'K'.code) {
            return DiceSetArchiveResult.Failure(DiceSetImportError.NOT_A_ZIP)
        }
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
                if (++entries > limits.maxEntries) return DiceSetArchiveResult.Failure(DiceSetImportError.TOO_MANY_ENTRIES)
                val name = entry.name.replace('\\', '/')
                if (!isSafe(name)) return DiceSetArchiveResult.Failure(DiceSetImportError.UNSAFE_PATH, entry.name)
                if (entry.isDirectory || isJunk(name)) continue
                val cap = capFor(name)
                if (cap == null) {
                    skipped++
                    continue
                }
                val target = File(root, name).canonicalFile
                if (!target.path.startsWith(root.path + File.separator)) {
                    return DiceSetArchiveResult.Failure(DiceSetImportError.UNSAFE_PATH, entry.name)
                }
                target.parentFile?.mkdirs()
                val copied = copyCapped(zip, target, cap)
                total += copied
                if (total > limits.maxTotalBytes) return DiceSetArchiveResult.Failure(DiceSetImportError.TOO_LARGE)
                if (copied > cap) {
                    target.delete()
                    skipped++
                } else {
                    written += name
                }
            }
        }
        if (written.isEmpty()) return DiceSetArchiveResult.Failure(DiceSetImportError.NOTHING_TO_IMPORT)

        val prefix = rootPrefix(written)
        val files = written.filter { it.startsWith(prefix) }.map { it.removePrefix(prefix) }.toSet()
        val setDir = if (prefix.isEmpty()) root else File(root, prefix.trimEnd('/'))

        val hadManifest = DiceSetFormat.MANIFEST in files
        val manifest = if (hadManifest) {
            when (val parsed = DiceSetValidator.parse(File(setDir, DiceSetFormat.MANIFEST).readText())) {
                is DiceSetParseResult.Failed ->
                    return DiceSetArchiveResult.Failure(DiceSetImportError.INVALID_MANIFEST, parsed.issue.message)
                // Files named by convention fill in what the manifest leaves out.
                is DiceSetParseResult.Parsed -> DiceSetConventions.fillFromFiles(parsed.manifest, files)
            }
        } else {
            DiceSetConventions.manifestFromFiles(fallbackName, files)
                ?: return DiceSetArchiveResult.Failure(DiceSetImportError.NOTHING_TO_IMPORT)
        }
        val missing = DiceSetEditor.referencedPaths(manifest).count { it !in files }
        return DiceSetArchiveResult.Success(manifest, setDir, hadManifest, skipped, missing)
    }

    /** `""`, or `folder/` when every file sits in one top-level folder and there is no root manifest. */
    private fun rootPrefix(files: Set<String>): String {
        if (DiceSetFormat.MANIFEST in files) return ""
        if (files.any { '/' !in it }) return ""
        val tops = files.map { it.substringBefore('/') }.toSet()
        val top = tops.singleOrNull() ?: return ""
        // A conventional folder name (2d/, 3d/, fonts/) is part of the set, not a wrapper.
        return if (top.lowercase() in SET_FOLDERS) "" else "$top/"
    }

    private fun isSafe(name: String): Boolean =
        name.isNotEmpty() && !name.startsWith("/") && ':' !in name && name.split('/').none { it == ".." }

    private fun isJunk(name: String): Boolean =
        name.startsWith("__MACOSX/") || name.substringAfterLast('/').startsWith(".")

    private fun capFor(name: String): Long? {
        if (name.substringAfterLast('/') == DiceSetFormat.MANIFEST) return limits.maxManifestBytes
        return when (name.substringAfterLast('.', "").lowercase()) {
            in DiceSetFormat.IMAGE_EXTENSIONS -> limits.maxImageBytes
            in DiceSetFormat.FONT_EXTENSIONS -> limits.maxFontBytes
            else -> null
        }
    }

    /** Copies at most [cap] + 1 bytes, so an oversize entry is detected without reading it all. */
    private fun copyCapped(input: InputStream, target: File, cap: Long): Long {
        var copied = 0L
        val buffer = ByteArray(BUFFER_SIZE)
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

    private companion object {
        const val MAGIC_LENGTH = 4
        const val BUFFER_SIZE = 16 * 1024
        val SET_FOLDERS = setOf("2d", "3d", "fonts")
    }
}
