package com.skrypko.dicefamiliar.testing

import com.skrypko.dicefamiliar.data.cards.CardDeckImportOutcome
import com.skrypko.dicefamiliar.data.cards.CardDeckRepository
import com.skrypko.dicefamiliar.data.cards.CardTableRepository
import com.skrypko.dicefamiliar.data.cards.CustomDeck
import com.skrypko.dicefamiliar.data.cards.DeckInfo
import com.skrypko.dicefamiliar.domain.cards.CardTable
import com.skrypko.dicefamiliar.domain.cards.DeckDefinition
import com.skrypko.dicefamiliar.domain.cards.DeckKind
import com.skrypko.dicefamiliar.domain.cards.DeckPresets
import com.skrypko.dicefamiliar.domain.cards.io.CardDeckImportError
import com.skrypko.dicefamiliar.data.dice.DiceSetDetails
import com.skrypko.dicefamiliar.data.dice.DiceSetImportOutcome
import com.skrypko.dicefamiliar.data.dice.DiceSetInfo
import com.skrypko.dicefamiliar.data.dice.DiceSetRepository
import com.skrypko.dicefamiliar.data.dice.DieImageResult
import com.skrypko.dicefamiliar.data.dice.StreamSource
import com.skrypko.dicefamiliar.domain.dice.skin.DiceSetEditor
import com.skrypko.dicefamiliar.domain.dice.skin.DiceSetIds
import com.skrypko.dicefamiliar.domain.dice.skin.DiceSetImportError
import com.skrypko.dicefamiliar.domain.dice.skin.DieImageSlot
import com.skrypko.dicefamiliar.data.dice.DiceState
import com.skrypko.dicefamiliar.data.dice.DiceStateRepository
import com.skrypko.dicefamiliar.data.number.NumberState
import com.skrypko.dicefamiliar.data.number.NumberStateRepository
import com.skrypko.dicefamiliar.data.remote.ParticipantRepository
import com.skrypko.dicefamiliar.domain.number.NumberResult
import com.skrypko.dicefamiliar.data.settings.AppSettingsRepository
import com.skrypko.dicefamiliar.domain.dice.RollMode
import com.skrypko.dicefamiliar.domain.random.RandomSource
import com.skrypko.dicefamiliar.domain.remote.IdGenerator
import com.skrypko.dicefamiliar.domain.remote.Participant
import com.skrypko.dicefamiliar.domain.settings.AppSettings
import com.skrypko.dicefamiliar.domain.settings.DarkMode
import com.skrypko.dicefamiliar.ui.common.Sound
import com.skrypko.dicefamiliar.ui.common.SoundPlayer
import com.skrypko.dicefamiliar.ui.dice.skin.DiceSkin
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.StateFlow

/** Returns scripted values for nextInt; fails if the script runs out or a value is out of range. */
class ScriptedRandomSource(vararg values: Int) : RandomSource {
    private val queue = ArrayDeque(values.toList())
    override fun nextInt(from: Int, until: Int): Int {
        val value = queue.removeFirst()
        require(value in from until until) { "Scripted $value not in [$from, $until)" }
        return value
    }
    override fun nextLong(from: Long, until: Long): Long = error("not scripted")
    override fun nextDouble(from: Double, until: Double): Double = error("not scripted")
}

class FixedClock(var millis: Long = 1_000L) : Clock {
    override fun now(): Instant = Instant.fromEpochMilliseconds(millis)
}

class SequentialIds(private val prefix: String = "id") : IdGenerator {
    private var next = 1
    override fun newId(): String = "$prefix-${next++}"
}

class FakeAppSettingsRepository(initial: AppSettings = AppSettings()) : AppSettingsRepository {
    private val state = MutableStateFlow(initial)
    override val settings: StateFlow<AppSettings> = state
    override suspend fun setSoundsEnabled(enabled: Boolean) { state.value = state.value.copy(soundsEnabled = enabled) }
    override suspend fun setAnimationsEnabled(enabled: Boolean) {
        state.value = state.value.copy(animationsEnabled = enabled)
    }
    override suspend fun setHapticsEnabled(enabled: Boolean) { state.value = state.value.copy(hapticsEnabled = enabled) }
    override suspend fun setQuickRoll(enabled: Boolean) { state.value = state.value.copy(quickRoll = enabled) }
    override suspend fun setThemeId(id: String) { state.value = state.value.copy(themeId = id) }
    override suspend fun setDarkMode(mode: DarkMode) { state.value = state.value.copy(darkMode = mode) }
    override suspend fun setDynamicColor(enabled: Boolean) { state.value = state.value.copy(dynamicColor = enabled) }
}

class FakeDiceStateRepository(var state: DiceState = DiceState()) : DiceStateRepository {
    override suspend fun load(): DiceState = state
    override suspend fun saveNotation(notation: String) { state = state.copy(lastNotation = notation) }
    override suspend fun saveMode(mode: RollMode) { state = state.copy(lastMode = mode) }
    override suspend fun saveFormulaVisible(visible: Boolean) { state = state.copy(formulaVisible = visible) }
    override suspend fun savePanelVisible(visible: Boolean) { state = state.copy(panelVisible = visible) }
    override suspend fun savePoolMode(enabled: Boolean) { state = state.copy(poolMode = enabled) }
    override suspend fun savePoolNotation(notation: String) { state = state.copy(poolNotation = notation) }
    override suspend fun savePoolGroups(groups: List<com.skrypko.dicefamiliar.domain.dice.pool.SuccessCriterion>) {
        state = state.copy(poolGroups = groups)
    }
}

class FakeNumberStateRepository(var state: NumberState = NumberState()) : NumberStateRepository {
    var saves = 0
    override suspend fun load(): NumberState = state
    override suspend fun save(state: NumberState) {
        saves++
        this.state = state.copy(lastResult = this.state.lastResult, panelVisible = this.state.panelVisible)
    }
    override suspend fun saveLastResult(result: NumberResult?) { state = state.copy(lastResult = result) }
    override suspend fun savePanelVisible(visible: Boolean) { state = state.copy(panelVisible = visible) }
}

class FakeParticipantRepository(private val self: Participant = Participant("me", "My phone")) :
    ParticipantRepository {
    override suspend fun self(): Participant = self
}

class FakeSoundPlayer : SoundPlayer {
    val played = mutableListOf<Sound>()
    override fun play(sound: Sound) { played += sound }
}

/** In-memory dice sets: `default` is built in; others behave like user sets. */
class FakeDiceSetRepository(private val skin: DiceSkin = DiceSkin.Default) : DiceSetRepository {
    val manifests = MutableStateFlow(mapOf("default" to DiceSetEditor.newManifest("default", "Default")))
    val active = MutableStateFlow("default")
    var importOutcome: DiceSetImportOutcome = DiceSetImportOutcome.Failed(DiceSetImportError.NOT_A_ZIP)
    var imageResult: DieImageResult = DieImageResult.OK

    override val sets: Flow<List<DiceSetInfo>> = manifests.map { all ->
        all.values.map { DiceSetInfo(it.id, it.name, builtIn = it.id == "default") }
    }
    override val activeSetId: Flow<String> = active
    override val activeSkin: Flow<DiceSkin> = MutableStateFlow(skin)

    override fun details(id: String): Flow<DiceSetDetails?> = manifests.map { all ->
        all[id]?.let { DiceSetDetails(DiceSetInfo(id, it.name, id == "default"), it, skin) }
    }

    override suspend fun select(id: String) { active.value = id }

    override suspend fun importArchive(source: StreamSource, fileName: String?): DiceSetImportOutcome {
        val outcome = importOutcome
        if (outcome is DiceSetImportOutcome.Imported) {
            manifests.value += outcome.id to DiceSetEditor.newManifest(outcome.id, outcome.name)
            active.value = outcome.id
        }
        return outcome
    }

    override suspend fun create(name: String): String {
        val id = DiceSetIds.unique(DiceSetIds.fromName(name), manifests.value.keys)
        manifests.value += id to DiceSetEditor.newManifest(id, name)
        active.value = id
        return id
    }

    override suspend fun rename(id: String, name: String) {
        manifests.value[id]?.let { manifests.value += id to it.copy(name = name.trim()) }
    }

    override suspend fun delete(id: String) {
        manifests.value -= id
        if (active.value == id) active.value = "default"
    }

    override suspend fun setDieImage(id: String, key: String, slot: DieImageSlot, source: StreamSource): DieImageResult {
        if (imageResult == DieImageResult.OK) {
            manifests.value[id]?.let {
                manifests.value += id to DiceSetEditor.withImage(it, key, slot, DiceSetEditor.fileFor(key, slot))
            }
        }
        return imageResult
    }

    override suspend fun removeDieImage(id: String, key: String, slot: DieImageSlot) {
        manifests.value[id]?.let { manifests.value += id to DiceSetEditor.withImage(it, key, slot, null) }
    }
}

/** In-memory decks: the three presets plus custom decks created through [saveCustom]. */
class FakeCardDeckRepository : CardDeckRepository {
    val custom = MutableStateFlow<Map<String, CustomDeck>>(emptyMap())
    val selected = MutableStateFlow(DeckPresets.DEFAULT_ID)
    var importOutcome: CardDeckImportOutcome = CardDeckImportOutcome.Failed(CardDeckImportError.NOT_A_ZIP)

    override val decks: Flow<List<DeckInfo>> = custom.map { all ->
        DeckPresets.ids.map { id -> DeckPresets.get(id)!!.let { DeckInfo(id, it.name, DeckKind.PRESET, it.cards.size, it.cards.size) } } +
            all.values.map { DeckInfo(it.id, it.name, DeckKind.CUSTOM, it.cardIds.size) }
    }
    override val selectedDeckId: Flow<String> = selected
    override suspend fun select(id: String) { selected.value = id }
    override suspend fun deck(id: String): DeckDefinition? =
        DeckPresets.get(id) ?: custom.value[id]?.let { DeckPresets.standardDeck(it.id, it.name, DeckKind.CUSTOM, it.cardIds) }
    override suspend fun customDeck(id: String): CustomDeck? = custom.value[id]
    override suspend fun saveCustom(id: String?, name: String, cardIds: List<String>): String {
        val deckId = id ?: "custom-${custom.value.size + 1}"
        custom.value += deckId to CustomDeck(deckId, name, cardIds)
        return deckId
    }
    override suspend fun delete(id: String) {
        custom.value -= id
        if (selected.value == id) selected.value = DeckPresets.DEFAULT_ID
    }
    override suspend fun importDeck(source: StreamSource, fileName: String?): CardDeckImportOutcome = importOutcome
}

class FakeCardTableRepository : CardTableRepository {
    val saved = mutableMapOf<String, CardTable>()
    private val buttons = MutableStateFlow(false)
    override suspend fun load(deckId: String, deckSize: Int): CardTable? = saved[deckId]?.takeIf { it.isValidFor(deckSize) }
    override suspend fun save(deckId: String, table: CardTable) {
        saved.clear()
        saved[deckId] = table
    }
    override val showButtons: Flow<Boolean> = buttons
    override suspend fun setShowButtons(show: Boolean) { buttons.value = show }
    private val panel = MutableStateFlow(true)
    override val panelVisible: Flow<Boolean> = panel
    override suspend fun setPanelVisible(visible: Boolean) { panel.value = visible }
}

/** In-memory pools; ids count up from 1, items get ids on save like Room. */
class FakePoolRepository : com.skrypko.dicefamiliar.data.pool.PoolRepository {
    val state = MutableStateFlow<Map<Long, com.skrypko.dicefamiliar.domain.pool.Pool>>(emptyMap())
    val discarded = mutableListOf<String>()
    var nextImport: com.skrypko.dicefamiliar.data.pool.PoolImportOutcome =
        com.skrypko.dicefamiliar.data.pool.PoolImportOutcome.Failed(com.skrypko.dicefamiliar.domain.pool.io.PoolImportError.READ_FAILED)
    var nextImage: String? = "picked.png"
    val exported = mutableListOf<Pair<List<com.skrypko.dicefamiliar.domain.pool.PoolItem>, Boolean>>()
    private var nextPoolId = 1L
    private var nextItemId = 1L

    override val pools: Flow<List<com.skrypko.dicefamiliar.data.pool.PoolSummary>> = state.map { pools ->
        pools.values.map { pool ->
            com.skrypko.dicefamiliar.data.pool.PoolSummary(
                pool.id, pool.name, pool.items.size, pool.items.count { !it.setAside }, pool.items.firstNotNullOfOrNull { it.imagePath },
            )
        }
    }

    override fun observePool(id: Long): Flow<com.skrypko.dicefamiliar.domain.pool.Pool?> = state.map { it[id] }
    override suspend fun pool(id: Long) = state.value[id]

    override suspend fun save(pool: com.skrypko.dicefamiliar.domain.pool.Pool): Long {
        val id = if (pool.id == 0L) nextPoolId++ else pool.id
        state.value = state.value + (id to pool.copy(id = id, items = pool.items.map { it.copy(id = nextItemId++) }))
        return id
    }

    override suspend fun delete(id: Long) { state.value = state.value - id }

    override suspend fun setShowNames(id: Long, show: Boolean) = update(id) { it.copy(showNames = show) }

    override suspend fun setAside(poolId: Long, itemIds: Collection<Long>) =
        update(poolId) { pool -> pool.copy(items = pool.items.map { if (it.id in itemIds) it.copy(setAside = true) else it }) }

    override suspend fun restoreAll(poolId: Long) = update(poolId) { pool -> pool.copy(items = pool.items.map { it.copy(setAside = false) }) }

    override suspend fun storeImage(source: StreamSource): String? = nextImage
    override suspend fun discardImages(paths: Collection<String>) { discarded += paths }
    override fun imageFile(path: String) = java.io.File(path)
    override val imageDir: java.io.File = java.io.File("images")
    override suspend fun readImport(source: StreamSource, fileName: String?, mime: String?) = nextImport

    override suspend fun export(items: List<com.skrypko.dicefamiliar.domain.pool.PoolItem>, withImages: Boolean, out: java.io.OutputStream) {
        exported += items to withImages
    }

    override suspend fun cleanUpImages() = Unit

    private fun update(id: Long, change: (com.skrypko.dicefamiliar.domain.pool.Pool) -> com.skrypko.dicefamiliar.domain.pool.Pool) {
        state.value[id]?.let { state.value = state.value + (id to change(it)) }
    }
}

class FakePoolPickStateRepository : com.skrypko.dicefamiliar.data.pool.PoolPickStateRepository {
    val saved = mutableMapOf<Long, com.skrypko.dicefamiliar.data.pool.PoolPickState>()
    override suspend fun load(poolId: Long) = saved[poolId] ?: com.skrypko.dicefamiliar.data.pool.PoolPickState()
    override suspend fun save(poolId: Long, state: com.skrypko.dicefamiliar.data.pool.PoolPickState) { saved[poolId] = state }
    override suspend fun clear(poolId: Long) { saved.remove(poolId) }
    var panel = true
    override suspend fun panelVisible() = panel
    override suspend fun savePanelVisible(visible: Boolean) { panel = visible }
}
