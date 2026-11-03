package com.skrypko.dicefamiliar.data.dice

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Typeface
import android.util.Log
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.skrypko.dicefamiliar.R
import com.skrypko.dicefamiliar.domain.dice.skin.DiceSetArchiveReader
import com.skrypko.dicefamiliar.domain.dice.skin.DiceSetArchiveResult
import com.skrypko.dicefamiliar.domain.dice.skin.DiceSetEditor
import com.skrypko.dicefamiliar.domain.dice.skin.DiceSetFormat
import com.skrypko.dicefamiliar.domain.dice.skin.DiceSetIds
import com.skrypko.dicefamiliar.domain.dice.skin.DiceSetImportError
import com.skrypko.dicefamiliar.domain.dice.skin.DiceSetManifest
import com.skrypko.dicefamiliar.domain.dice.skin.DiceSetParseResult
import com.skrypko.dicefamiliar.domain.dice.skin.DiceSetValidator
import com.skrypko.dicefamiliar.domain.dice.skin.DieImageSlot
import com.skrypko.dicefamiliar.domain.dice.skin.DieStyleSpec
import com.skrypko.dicefamiliar.ui.dice.skin.DiceSkin
import com.skrypko.dicefamiliar.ui.dice.skin.DieSkin
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Opens the bytes of a picked file (a content Uri in the app, a byte array in tests). */
fun interface StreamSource {
    fun open(): InputStream?
}

data class DiceSetInfo(val id: String, val name: String, val builtIn: Boolean)

/** A set opened in the editor: its manifest and the resolved look for previews. */
data class DiceSetDetails(val info: DiceSetInfo, val manifest: DiceSetManifest, val skin: DiceSkin)

sealed interface DiceSetImportOutcome {
    data class Imported(
        val id: String,
        val name: String,
        val replaced: Boolean,
        val skippedFiles: Int,
        val missingFiles: Int,
    ) : DiceSetImportOutcome

    data class Failed(val error: DiceSetImportError) : DiceSetImportOutcome
}

enum class DieImageResult { OK, UNREADABLE, TOO_LARGE }

/** Bundled sets (`assets/dice_sets/`) plus sets the user imported or built (`filesDir/dice_sets/`). */
interface DiceSetRepository {
    val sets: Flow<List<DiceSetInfo>>
    val activeSetId: Flow<String>
    val activeSkin: Flow<DiceSkin>
    fun details(id: String): Flow<DiceSetDetails?>
    suspend fun select(id: String)
    suspend fun importArchive(source: StreamSource, fileName: String?): DiceSetImportOutcome
    /** Creates an empty user set, makes it active and returns its id. */
    suspend fun create(name: String): String
    suspend fun rename(id: String, name: String)
    suspend fun delete(id: String)
    suspend fun setDieImage(id: String, key: String, slot: DieImageSlot, source: StreamSource): DieImageResult
    suspend fun removeDieImage(id: String, key: String, slot: DieImageSlot)
}

@Singleton
class DefaultDiceSetRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dataStore: DataStore<Preferences>,
) : DiceSetRepository {
    private val userRoot = File(context.filesDir, USER_ROOT)
    private val reader = DiceSetArchiveReader()
    private val mutex = Mutex()

    /** Bumped after every change on disk, so flows reload. */
    private val revision = MutableStateFlow(0)
    private val cache = mutableMapOf<String, Pair<Int, DiceSkin>>()

    override val sets: Flow<List<DiceSetInfo>> = revision.map { listSets() }.flowOn(Dispatchers.IO)

    override val activeSetId: Flow<String> =
        combine(dataStore.data.map { it[ACTIVE_SET] ?: DEFAULT_SET }, revision) { id, _ ->
            id.takeIf { exists(it) } ?: DEFAULT_SET
        }.distinctUntilChanged().flowOn(Dispatchers.IO)

    override val activeSkin: Flow<DiceSkin> =
        combine(activeSetId, revision) { id, rev -> skin(id, rev) }.flowOn(Dispatchers.IO)

    override fun details(id: String): Flow<DiceSetDetails?> = revision.map { rev ->
        val info = listSets().firstOrNull { it.id == id } ?: return@map null
        val manifest = manifest(id) ?: return@map null
        DiceSetDetails(info, manifest, skin(id, rev))
    }.flowOn(Dispatchers.IO)

    override suspend fun select(id: String) {
        dataStore.edit { it[ACTIVE_SET] = id }
    }

    override suspend fun importArchive(source: StreamSource, fileName: String?): DiceSetImportOutcome =
        mutate {
            val fallbackName = fileName?.substringBeforeLast('.')?.trim()?.takeIf { it.isNotEmpty() }
                ?: context.getString(R.string.dice_sets_default_name)
            val staging = File(context.cacheDir, "dice_import_${System.nanoTime()}")
            try {
                val result = try {
                    source.open()?.use { reader.read(it, staging, fallbackName) }
                } catch (e: IOException) {
                    Log.w(TAG, "Can't open dice set archive", e)
                    null
                } ?: return@mutate DiceSetImportOutcome.Failed(DiceSetImportError.READ_FAILED)
                when (result) {
                    is DiceSetArchiveResult.Failure -> {
                        Log.w(TAG, "Dice set import failed: ${result.error} ${result.detail.orEmpty()}")
                        DiceSetImportOutcome.Failed(result.error)
                    }
                    is DiceSetArchiveResult.Success -> install(result, fallbackName)
                }
            } finally {
                staging.deleteRecursively()
            }
        }

    private suspend fun install(result: DiceSetArchiveResult.Success, fallbackName: String): DiceSetImportOutcome {
        // A pack with the id of a bundled set is renamed; one with the id of a user set updates it.
        val base = DiceSetIds.fromName(result.manifest.id)
        val bundled = bundledIds()
        val id = if (base in bundled) DiceSetIds.unique(base, bundled + userIds()) else base
        val name = result.manifest.name.trim().ifEmpty { fallbackName }
        val manifest = result.manifest.copy(id = id, name = name)
        File(result.rootDir, DiceSetFormat.MANIFEST).writeText(DiceSetEditor.encode(manifest))

        val target = File(userRoot, id)
        val replaced = target.exists()
        val temp = File(userRoot, ".import_$id")
        temp.deleteRecursively()
        userRoot.mkdirs()
        if (!result.rootDir.renameTo(temp)) result.rootDir.copyRecursively(temp, overwrite = true)
        target.deleteRecursively()
        if (!temp.renameTo(target)) {
            temp.deleteRecursively()
            return DiceSetImportOutcome.Failed(DiceSetImportError.READ_FAILED)
        }
        select(id)
        return DiceSetImportOutcome.Imported(id, name, replaced, result.skippedFiles, result.missingFiles)
    }

    override suspend fun create(name: String): String = mutate {
        val id = DiceSetIds.unique(DiceSetIds.fromName(name), bundledIds() + userIds())
        val dir = File(userRoot, id).apply { mkdirs() }
        File(dir, DiceSetFormat.MANIFEST).writeText(DiceSetEditor.encode(DiceSetEditor.newManifest(id, name)))
        select(id)
        id
    }

    override suspend fun rename(id: String, name: String): Unit = mutate {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return@mutate
        val manifest = userManifest(id) ?: return@mutate
        if (manifest.name != trimmed) writeManifest(id, manifest.copy(name = trimmed))
    }

    override suspend fun delete(id: String): Unit = mutate {
        val dir = userDir(id) ?: return@mutate
        dir.deleteRecursively()
        synchronized(cache) { cache.remove(id) }
    }

    override suspend fun setDieImage(
        id: String,
        key: String,
        slot: DieImageSlot,
        source: StreamSource,
    ): DieImageResult = mutate {
        val manifest = userManifest(id) ?: return@mutate DieImageResult.UNREADABLE
        if (!DiceSetEditor.supports(key, slot)) return@mutate DieImageResult.UNREADABLE
        val bytes = try {
            source.open()?.use { readCapped(it, MAX_UPLOAD_BYTES) }
        } catch (e: IOException) {
            Log.w(TAG, "Can't read picked image", e)
            null
        } ?: return@mutate DieImageResult.UNREADABLE
        if (bytes.size > MAX_UPLOAD_BYTES) return@mutate DieImageResult.TOO_LARGE
        val bitmap = decodeScaled(bytes) ?: return@mutate DieImageResult.UNREADABLE

        val path = DiceSetEditor.fileFor(key, slot)
        val file = File(File(userRoot, id), path).apply { parentFile?.mkdirs() }
        try {
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, PNG_QUALITY, it) }
        } catch (e: IOException) {
            Log.w(TAG, "Can't store die image", e)
            return@mutate DieImageResult.UNREADABLE
        } finally {
            bitmap.recycle()
        }
        replaceImage(id, manifest, key, slot, path)
        DieImageResult.OK
    }

    override suspend fun removeDieImage(id: String, key: String, slot: DieImageSlot): Unit = mutate {
        val manifest = userManifest(id) ?: return@mutate
        if (DiceSetEditor.supports(key, slot)) replaceImage(id, manifest, key, slot, null)
    }

    /** Points the manifest at [path] and deletes the old file once nothing refers to it. */
    private fun replaceImage(id: String, manifest: DiceSetManifest, key: String, slot: DieImageSlot, path: String?) {
        val old = DiceSetEditor.imagePath(manifest, key, slot)
        val updated = DiceSetEditor.withImage(manifest, key, slot, path)
        writeManifest(id, updated)
        if (old != null && old != path && old !in DiceSetEditor.referencedPaths(updated) &&
            DiceSetValidator.checkPath("old", old, DiceSetFormat.IMAGE_EXTENSIONS) == null
        ) {
            File(File(userRoot, id), old).delete()
        }
    }

    /** Runs a change on disk under the lock, then tells every flow to reload. */
    private suspend fun <T> mutate(block: suspend () -> T): T = withContext(Dispatchers.IO) {
        mutex.withLock {
            try {
                block()
            } finally {
                revision.update { it + 1 }
            }
        }
    }

    // ---- Listing ----

    private fun bundledIds(): Set<String> =
        (context.assets.list(ASSET_ROOT) ?: emptyArray()).filter(DiceSetIds::isValid).toSet()

    private fun userIds(): Set<String> =
        (userRoot.listFiles() ?: emptyArray())
            .filter { it.isDirectory && DiceSetIds.isValid(it.name) && File(it, DiceSetFormat.MANIFEST).isFile }
            .map { it.name }
            .toSet()

    private fun exists(id: String): Boolean = id in bundledIds() || userDir(id) != null

    private fun listSets(): List<DiceSetInfo> {
        val bundled = bundledIds().sorted().map { id ->
            DiceSetInfo(id, manifest(id)?.name ?: id, builtIn = true)
        }
        val user = (userIds() - bundledIds()).map { id ->
            DiceSetInfo(id, manifest(id)?.name ?: id, builtIn = false)
        }.sortedBy { it.name.lowercase() }
        return bundled.sortedByDescending { it.id == DEFAULT_SET } + user
    }

    /** The folder of a user set, or null for invalid ids, bundled ids and missing sets. */
    private fun userDir(id: String): File? {
        if (!DiceSetIds.isValid(id) || id in bundledIds()) return null
        return File(userRoot, id).takeIf { File(it, DiceSetFormat.MANIFEST).isFile }
    }

    private fun userManifest(id: String): DiceSetManifest? = userDir(id)?.let { manifest(id) }

    private fun writeManifest(id: String, manifest: DiceSetManifest) {
        File(File(userRoot, id), DiceSetFormat.MANIFEST).writeText(DiceSetEditor.encode(manifest))
    }

    private fun manifest(id: String): DiceSetManifest? {
        val text = filesFor(id)?.readText(DiceSetFormat.MANIFEST) ?: return null
        return (DiceSetValidator.parse(text) as? DiceSetParseResult.Parsed)?.manifest
    }

    private fun filesFor(id: String): SetFiles? = when {
        !DiceSetIds.isValid(id) -> null
        id in bundledIds() -> AssetFiles("$ASSET_ROOT/$id")
        else -> userDir(id)?.let(::DirFiles)
    }

    // ---- Loading ----

    private fun skin(id: String, rev: Int): DiceSkin {
        synchronized(cache) { cache[id]?.takeIf { it.first == rev }?.let { return it.second } }
        val skin = load(id)
        synchronized(cache) { cache[id] = rev to skin }
        return skin
    }

    /** Loads a set; anything missing or invalid falls back to the built-in look. */
    private fun load(id: String): DiceSkin {
        val files = filesFor(id) ?: return DiceSkin.Default.also { Log.w(TAG, "Unknown dice set '$id'") }
        val text = files.readText(DiceSetFormat.MANIFEST) ?: return DiceSkin.Default.also {
            Log.w(TAG, "No manifest for dice set '$id'")
        }
        val parsed = when (val result = DiceSetValidator.parse(text)) {
            is DiceSetParseResult.Failed -> {
                Log.w(TAG, "Dice set '$id': ${result.issue}")
                return DiceSkin.Default
            }
            is DiceSetParseResult.Parsed -> result
        }
        val badPaths = parsed.issues.map { it.path }.toSet()
        parsed.issues.forEach { Log.w(TAG, "Dice set '$id': ${it.path}: ${it.message}") }

        // image2d is per die only; a silhouette image never applies to every die type.
        val base = resolve(files, "style", parsed.manifest.style, DieSkin(), badPaths).copy(image2d = null)
        val perDie = parsed.manifest.dice
            .filterKeys { it in DiceSetFormat.DIE_KEYS }
            .mapValues { (key, spec) -> resolve(files, "dice.$key", spec, base, badPaths) }
        return DiceSkin(parsed.manifest.id, parsed.manifest.name, base, perDie)
    }

    private fun resolve(files: SetFiles, path: String, spec: DieStyleSpec, parent: DieSkin, bad: Set<String>): DieSkin {
        fun ok(field: String) = "$path.$field" !in bad
        return DieSkin(
            fill = if (ok("fill")) color(spec.fill, parent.fill) else parent.fill,
            edge = if (ok("edge")) color(spec.edge, parent.edge) else parent.edge,
            text = if (ok("text")) color(spec.text, parent.text) else parent.text,
            edgeWidth = spec.edgeWidth?.takeIf { ok("edgeWidth") }?.dp ?: parent.edgeWidth,
            typeface = spec.font?.takeIf { ok("font") }?.let(files::font) ?: parent.typeface,
            image2d = spec.image2d?.takeIf { ok("image2d") }?.let { image(files, it) },
            faceTexture3d = spec.faceTexture3d?.takeIf { ok("faceTexture3d") }?.let { image(files, it) }
                ?: parent.faceTexture3d,
            pips = spec.pips ?: parent.pips,
        )
    }

    /** Omitted inherits [parent]; `theme` gives null (Material colours); otherwise the parsed colour. */
    private fun color(value: String?, parent: Color?): Color? {
        if (value == null) return parent
        if (value == DiceSetFormat.THEME) return null
        val hex = value.removePrefix("#")
        val argb = if (hex.length == 6) "FF$hex" else hex
        return Color(argb.toLong(16).toInt())
    }

    private fun image(files: SetFiles, path: String): ImageBitmap? = try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        files.open(path).use { BitmapFactory.decodeStream(it, null, bounds) }
        val options = BitmapFactory.Options().apply { inSampleSize = sampleSize(bounds) }
        files.open(path).use { BitmapFactory.decodeStream(it, null, options) }?.asImageBitmap()
    } catch (e: IOException) {
        Log.w(TAG, "Missing image ${files.describe(path)}", e)
        null
    }

    private fun decodeScaled(bytes: ByteArray): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        val options = BitmapFactory.Options().apply { inSampleSize = sampleSize(bounds) }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
    }

    private fun sampleSize(bounds: BitmapFactory.Options): Int {
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > MAX_IMAGE_PX) sample *= 2
        return sample
    }

    /** Reads up to [cap] + 1 bytes, so the caller can tell an oversize file apart. */
    private fun readCapped(input: InputStream, cap: Int): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(BUFFER_SIZE)
        while (out.size() <= cap) {
            val n = input.read(buffer)
            if (n < 0) break
            out.write(buffer, 0, n)
        }
        return out.toByteArray()
    }

    /** Files of one set, wherever it lives. Paths were checked by [DiceSetValidator]. */
    private interface SetFiles {
        fun open(path: String): InputStream
        fun font(path: String): Typeface?
        fun describe(path: String): String
        fun readText(path: String): String? = try {
            open(path).bufferedReader().use { it.readText() }
        } catch (e: IOException) {
            null
        }
    }

    private inner class AssetFiles(private val dir: String) : SetFiles {
        override fun open(path: String): InputStream = context.assets.open("$dir/$path")
        override fun describe(path: String) = "$dir/$path"
        override fun font(path: String): Typeface? = try {
            Typeface.createFromAsset(context.assets, "$dir/$path")
        } catch (e: RuntimeException) {
            Log.w(TAG, "Bad font $dir/$path", e)
            null
        }
    }

    private class DirFiles(private val dir: File) : SetFiles {
        override fun open(path: String): InputStream = File(dir, path).inputStream()
        override fun describe(path: String) = File(dir, path).path
        override fun font(path: String): Typeface? {
            val file = File(dir, path).takeIf { it.isFile } ?: return null
            return try {
                Typeface.createFromFile(file)
            } catch (e: RuntimeException) {
                Log.w(TAG, "Bad font ${file.path}", e)
                null
            }
        }
    }

    private companion object {
        const val TAG = "DiceSets"
        const val ASSET_ROOT = "dice_sets"
        const val USER_ROOT = "dice_sets"
        const val DEFAULT_SET = "default"
        const val MAX_IMAGE_PX = 1024
        const val MAX_UPLOAD_BYTES = 10 * 1024 * 1024
        const val PNG_QUALITY = 100
        const val BUFFER_SIZE = 16 * 1024
        val ACTIVE_SET = stringPreferencesKey("dice.active_set")
    }
}
