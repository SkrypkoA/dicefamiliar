package com.skrypko.dicefamiliar.data.cards

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import androidx.core.graphics.scale
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.skrypko.dicefamiliar.R
import com.skrypko.dicefamiliar.data.dice.StreamSource
import com.skrypko.dicefamiliar.domain.cards.CardFace
import com.skrypko.dicefamiliar.domain.cards.CardLimits
import com.skrypko.dicefamiliar.domain.cards.DeckDefinition
import com.skrypko.dicefamiliar.domain.cards.DeckKind
import com.skrypko.dicefamiliar.domain.cards.DeckPresets
import com.skrypko.dicefamiliar.domain.cards.StandardCards
import com.skrypko.dicefamiliar.domain.cards.io.CardArchiveResult
import com.skrypko.dicefamiliar.domain.cards.io.CardCsvParser
import com.skrypko.dicefamiliar.domain.cards.io.CardCsvResult
import com.skrypko.dicefamiliar.domain.cards.io.CardDeckArchiveReader
import com.skrypko.dicefamiliar.domain.cards.io.CardDeckImportError
import com.skrypko.dicefamiliar.domain.dice.skin.DiceSetIds
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.BufferedInputStream
import java.io.File
import java.io.IOException
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
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/** A deck in the deck list. Presets have [presetSize] (the UI names them "52 cards"). */
data class DeckInfo(val id: String, val name: String, val kind: DeckKind, val cardCount: Int, val presetSize: Int? = null)

/** A custom deck opened in the editor. */
data class CustomDeck(val id: String, val name: String, val cardIds: List<String>)

sealed interface CardDeckImportOutcome {
    data class Imported(val id: String, val name: String, val cards: Int, val skippedFiles: Int, val missingImages: Int) :
        CardDeckImportOutcome

    data class Failed(val error: CardDeckImportError) : CardDeckImportOutcome
}

/** Presets, custom decks (picked from the 54 standard cards) and uploaded decks, plus the selected deck. */
interface CardDeckRepository {
    /** Presets first, then custom decks, then uploaded decks. */
    val decks: Flow<List<DeckInfo>>

    /** The selected deck; [DeckPresets.DEFAULT_ID] when none (or a deleted one) is selected. */
    val selectedDeckId: Flow<String>

    suspend fun select(id: String)
    suspend fun deck(id: String): DeckDefinition?
    suspend fun customDeck(id: String): CustomDeck?

    /** Creates (null [id]) or updates a custom deck; returns its id. */
    suspend fun saveCustom(id: String?, name: String, cardIds: List<String>): String
    suspend fun delete(id: String)

    /** A ZIP (CSV + images, or images only) or a plain CSV; the new deck becomes the selected one. */
    suspend fun importDeck(source: StreamSource, fileName: String?): CardDeckImportOutcome
}

/** How a user deck is stored in `filesDir/card_decks/<id>/deck.json` (image paths relative to that folder). */
@Serializable
internal data class StoredDeck(
    val name: String,
    val kind: DeckKind,
    val standardIds: List<String> = emptyList(),
    val cards: List<CardFace> = emptyList(),
    val back: String? = null,
)

@Singleton
class FileCardDeckRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dataStore: DataStore<Preferences>,
) : CardDeckRepository {
    private val root = File(context.filesDir, ROOT)
    private val json = Json { ignoreUnknownKeys = true }
    private val mutex = Mutex()
    private val revision = MutableStateFlow(0)

    override val decks: Flow<List<DeckInfo>> = revision.map { listDecks() }.flowOn(Dispatchers.IO)

    override val selectedDeckId: Flow<String> =
        combine(dataStore.data.map { it[SELECTED] ?: DeckPresets.DEFAULT_ID }, revision) { id, _ ->
            id.takeIf { DeckPresets.isPreset(it) || stored(it) != null } ?: DeckPresets.DEFAULT_ID
        }.distinctUntilChanged().flowOn(Dispatchers.IO)

    override suspend fun select(id: String) {
        dataStore.edit { it[SELECTED] = id }
    }

    override suspend fun deck(id: String): DeckDefinition? = withContext(Dispatchers.IO) {
        DeckPresets.get(id)?.let { return@withContext it }
        val stored = stored(id) ?: return@withContext null
        val dir = File(root, id)
        when (stored.kind) {
            DeckKind.CUSTOM -> DeckPresets.standardDeck(id, stored.name, DeckKind.CUSTOM, stored.standardIds)
            else -> DeckDefinition(
                id = id,
                name = stored.name,
                kind = stored.kind,
                cards = stored.cards.map { card -> card.copy(image = card.image?.let { File(dir, it).path }) },
                backImage = stored.back?.let { File(dir, it).path },
            )
        }
    }

    override suspend fun customDeck(id: String): CustomDeck? = withContext(Dispatchers.IO) {
        stored(id)?.takeIf { it.kind == DeckKind.CUSTOM }?.let { CustomDeck(id, it.name, it.standardIds) }
    }

    override suspend fun saveCustom(id: String?, name: String, cardIds: List<String>): String = mutate {
        val cleanName = name.trim().take(CardLimits.MAX_NAME_LENGTH).ifEmpty { context.getString(R.string.cards_custom_default_name) }
        // Keep the standard order (jokers first), whatever order the cards were ticked in.
        val ids = StandardCards.all54.filter { it in cardIds.toSet() }
        val deckId = id?.takeIf { stored(it)?.kind == DeckKind.CUSTOM } ?: newId(cleanName)
        write(deckId, StoredDeck(cleanName, DeckKind.CUSTOM, standardIds = ids))
        deckId
    }

    override suspend fun delete(id: String) {
        mutate { File(root, id).deleteRecursively() }
    }

    override suspend fun importDeck(source: StreamSource, fileName: String?): CardDeckImportOutcome = mutate {
        val name = fileName?.substringBeforeLast('.')?.trim()?.take(CardLimits.MAX_NAME_LENGTH)?.ifEmpty { null }
            ?: context.getString(R.string.cards_uploaded_default_name)
        val staging = File(context.cacheDir, "cards_import_${System.nanoTime()}")
        try {
            val input = try {
                source.open()?.let { BufferedInputStream(it) }
            } catch (e: IOException) {
                Log.w(TAG, "Can't open deck", e)
                null
            } ?: return@mutate CardDeckImportOutcome.Failed(CardDeckImportError.READ_FAILED)
            input.use { stream ->
                stream.mark(4)
                val zip = stream.read() == 'P'.code && stream.read() == 'K'.code
                stream.reset()
                if (zip) {
                    when (val result = CardDeckArchiveReader().read(stream, staging)) {
                        is CardArchiveResult.Failure -> CardDeckImportOutcome.Failed(result.error)
                        is CardArchiveResult.Success -> installArchive(name, result)
                    }
                } else {
                    val bytes = readCapped(stream, MAX_CSV_BYTES + 1)
                    if (bytes.size > MAX_CSV_BYTES) return@mutate CardDeckImportOutcome.Failed(CardDeckImportError.TOO_LARGE)
                    when (val parsed = CardCsvParser.parse(bytes.toString(Charsets.UTF_8))) {
                        is CardCsvResult.Failure -> CardDeckImportOutcome.Failed(
                            // Not a ZIP and not a readable CSV either.
                            if (parsed.error == CardDeckImportError.NO_CARDS && fileName?.lowercase()?.endsWith(".csv") != true) {
                                CardDeckImportError.NOT_A_ZIP
                            } else {
                                parsed.error
                            },
                        )
                        is CardCsvResult.Success -> {
                            val id = newId(name)
                            // A plain CSV has no images: every card is a text card.
                            write(id, StoredDeck(name, DeckKind.UPLOADED, cards = parsed.cards.map { it.copy(image = null) }))
                            select(id)
                            CardDeckImportOutcome.Imported(id, name, parsed.cards.size, 0, parsed.cards.count { it.image != null })
                        }
                    }
                }
            }
        } finally {
            staging.deleteRecursively()
        }
    }

    private suspend fun installArchive(name: String, result: CardArchiveResult.Success): CardDeckImportOutcome {
        val id = newId(name)
        val target = File(root, id)
        val images = File(target, IMAGES)
        images.mkdirs()
        var counter = 0
        fun copyImage(path: String): String {
            val ext = path.substringAfterLast('.').lowercase()
            val out = "$IMAGES/${counter++}.$ext"
            storeImage(File(result.rootDir, path), File(target, out))
            return out
        }
        return try {
            // One stored file per referenced image, even when several cards share it.
            val copied = mutableMapOf<String, String>()
            val cards = result.cards.map { card -> card.copy(image = card.image?.let { copied.getOrPut(it) { copyImage(it) } }) }
            val back = result.backImage?.let { copyImage(it) }
            write(id, StoredDeck(name, DeckKind.UPLOADED, cards = cards, back = back))
            select(id)
            CardDeckImportOutcome.Imported(id, name, cards.size, result.skippedFiles, result.missingImages)
        } catch (e: IOException) {
            Log.w(TAG, "Deck install failed", e)
            target.deleteRecursively()
            CardDeckImportOutcome.Failed(CardDeckImportError.READ_FAILED)
        }
    }

    /** Copies an image, scaling it down to at most [MAX_IMAGE_PX] on the long side. */
    private fun storeImage(source: File, target: File) {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(source.path, bounds)
        if (maxOf(bounds.outWidth, bounds.outHeight) <= MAX_IMAGE_PX) {
            source.copyTo(target, overwrite = true)
            return
        }
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_IMAGE_PX) sample *= 2
        val decoded = BitmapFactory.decodeFile(source.path, BitmapFactory.Options().apply { inSampleSize = sample })
            ?: return source.copyTo(target, overwrite = true).let {}
        val scale = MAX_IMAGE_PX.toFloat() / maxOf(decoded.width, decoded.height)
        val scaled = if (scale < 1f) {
            decoded.scale((decoded.width * scale).toInt().coerceAtLeast(1), (decoded.height * scale).toInt().coerceAtLeast(1))
        } else {
            decoded
        }
        val jpeg = target.extension == "jpg" || target.extension == "jpeg"
        target.outputStream().use {
            scaled.compress(if (jpeg) Bitmap.CompressFormat.JPEG else Bitmap.CompressFormat.PNG, JPEG_QUALITY, it)
        }
    }

    /** Reads at most [limit] bytes. */
    private fun readCapped(input: java.io.InputStream, limit: Int): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(16 * 1024)
        while (out.size() < limit) {
            val n = input.read(buffer, 0, minOf(buffer.size, limit - out.size()))
            if (n < 0) break
            out.write(buffer, 0, n)
        }
        return out.toByteArray()
    }

    private fun listDecks(): List<DeckInfo> {
        val presets = DeckPresets.ids.mapNotNull { id ->
            DeckPresets.get(id)?.let { DeckInfo(id, it.name, DeckKind.PRESET, it.cards.size, presetSize = it.cards.size) }
        }
        val user = root.listFiles()?.filter { it.isDirectory && !it.name.startsWith(".") }.orEmpty().mapNotNull { dir ->
            stored(dir.name)?.let { deck ->
                val count = if (deck.kind == DeckKind.CUSTOM) deck.standardIds.size else deck.cards.size
                DeckInfo(dir.name, deck.name, deck.kind, count)
            }
        }.sortedWith(compareBy<DeckInfo> { it.kind.ordinal }.thenBy { it.name.lowercase() })
        return presets + user
    }

    private fun stored(id: String): StoredDeck? {
        if (!DiceSetIds.isValid(id)) return null
        val file = File(File(root, id), DECK_FILE)
        if (!file.exists()) return null
        return try {
            json.decodeFromString<StoredDeck>(file.readText())
        } catch (e: SerializationException) {
            Log.w(TAG, "Bad deck $id", e)
            null
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "Bad deck $id", e)
            null
        }
    }

    private fun write(id: String, deck: StoredDeck) {
        val dir = File(root, id)
        dir.mkdirs()
        File(dir, DECK_FILE).writeText(json.encodeToString(deck))
    }

    private fun newId(name: String): String {
        val taken = (root.listFiles()?.map { it.name }.orEmpty() + DeckPresets.ids).toSet()
        return DiceSetIds.unique(DiceSetIds.fromName(name), taken)
    }

    private suspend fun <T> mutate(block: suspend () -> T): T = withContext(Dispatchers.IO) {
        mutex.withLock { block().also { revision.update { it + 1 } } }
    }

    private companion object {
        const val TAG = "CardDecks"
        const val ROOT = "card_decks"
        const val IMAGES = "images"
        const val DECK_FILE = "deck.json"
        const val MAX_IMAGE_PX = 1024
        const val MAX_CSV_BYTES = 1024 * 1024
        const val JPEG_QUALITY = 90
        val SELECTED = stringPreferencesKey("cards.selected_deck")
    }
}
