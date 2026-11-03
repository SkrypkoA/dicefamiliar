package com.skrypko.dicefamiliar.testing

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
}

class FakeNumberStateRepository(var state: NumberState = NumberState()) : NumberStateRepository {
    var saves = 0
    override suspend fun load(): NumberState = state
    override suspend fun save(state: NumberState) {
        saves++
        this.state = state.copy(lastResult = this.state.lastResult)
    }
    override suspend fun saveLastResult(result: NumberResult?) { state = state.copy(lastResult = result) }
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
