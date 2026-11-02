package com.skrypko.dicefamiliar.testing

import com.skrypko.dicefamiliar.data.dice.DiceSetRepository
import com.skrypko.dicefamiliar.data.dice.DiceState
import com.skrypko.dicefamiliar.data.dice.DiceStateRepository
import com.skrypko.dicefamiliar.data.remote.ParticipantRepository
import com.skrypko.dicefamiliar.data.settings.AppSettingsRepository
import com.skrypko.dicefamiliar.domain.dice.RollMode
import com.skrypko.dicefamiliar.domain.random.RandomSource
import com.skrypko.dicefamiliar.domain.remote.IdGenerator
import com.skrypko.dicefamiliar.domain.remote.Participant
import com.skrypko.dicefamiliar.domain.settings.AppSettings
import com.skrypko.dicefamiliar.ui.common.Sound
import com.skrypko.dicefamiliar.ui.common.SoundPlayer
import com.skrypko.dicefamiliar.ui.dice.skin.DiceSkin
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.flow.MutableStateFlow
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
}

class FakeDiceStateRepository(var state: DiceState = DiceState()) : DiceStateRepository {
    override suspend fun load(): DiceState = state
    override suspend fun saveNotation(notation: String) { state = state.copy(lastNotation = notation) }
    override suspend fun saveMode(mode: RollMode) { state = state.copy(lastMode = mode) }
    override suspend fun saveFormulaVisible(visible: Boolean) { state = state.copy(formulaVisible = visible) }
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

class FakeDiceSetRepository(private val skin: DiceSkin = DiceSkin.Default) : DiceSetRepository {
    override suspend fun activeSkin(): DiceSkin = skin
}
