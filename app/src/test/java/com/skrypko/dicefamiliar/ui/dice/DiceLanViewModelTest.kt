package com.skrypko.dicefamiliar.ui.dice

import androidx.lifecycle.SavedStateHandle
import com.skrypko.dicefamiliar.data.dice.DiceState
import com.skrypko.dicefamiliar.data.lan.LanDiceGateway
import com.skrypko.dicefamiliar.data.lan.LanNavigation
import com.skrypko.dicefamiliar.data.lan.LanPlayer
import com.skrypko.dicefamiliar.data.lan.LanRole
import com.skrypko.dicefamiliar.data.lan.LanState
import com.skrypko.dicefamiliar.domain.dice.DiceNotationParser
import com.skrypko.dicefamiliar.domain.dice.DiceRollHandler
import com.skrypko.dicefamiliar.domain.dice.DiceRollPayload
import com.skrypko.dicefamiliar.domain.dice.DiceRollResult
import com.skrypko.dicefamiliar.domain.dice.DiceRoller
import com.skrypko.dicefamiliar.domain.dice.NotationError
import com.skrypko.dicefamiliar.domain.dice.ParseResult
import com.skrypko.dicefamiliar.domain.dice.RollMode
import com.skrypko.dicefamiliar.domain.dice.pool.Comparison
import com.skrypko.dicefamiliar.domain.dice.pool.SuccessCriterion
import com.skrypko.dicefamiliar.domain.lan.LanRollEntry
import com.skrypko.dicefamiliar.domain.lan.LanRollState
import com.skrypko.dicefamiliar.domain.random.SeededRandomSource
import com.skrypko.dicefamiliar.domain.remote.ActionCodec
import com.skrypko.dicefamiliar.domain.remote.ActionOutcome
import com.skrypko.dicefamiliar.domain.remote.ActionRequest
import com.skrypko.dicefamiliar.domain.remote.ActionResponse
import com.skrypko.dicefamiliar.domain.remote.ActionStatus
import com.skrypko.dicefamiliar.domain.remote.LocalActionExecutor
import com.skrypko.dicefamiliar.domain.remote.Participant
import com.skrypko.dicefamiliar.domain.remote.RemoteActionRegistry
import com.skrypko.dicefamiliar.domain.settings.AppSettings
import com.skrypko.dicefamiliar.testing.FakeAppSettingsRepository
import com.skrypko.dicefamiliar.testing.FakeDiceSetRepository
import com.skrypko.dicefamiliar.testing.FakeDiceStateRepository
import com.skrypko.dicefamiliar.testing.FakeParticipantRepository
import com.skrypko.dicefamiliar.testing.FakeSoundPlayer
import com.skrypko.dicefamiliar.testing.FixedClock
import com.skrypko.dicefamiliar.testing.ScriptedRandomSource
import com.skrypko.dicefamiliar.testing.SequentialIds
import com.skrypko.dicefamiliar.ui.common.Sound
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** A scripted LAN session for the Dice screen. */
private class FakeLanGateway : LanDiceGateway {
    override val state = MutableStateFlow(LanState())
    val sent = mutableListOf<Pair<String, DiceRollPayload>>()
    val declined = mutableListOf<String>()
    val payloads = mutableMapOf<String, DiceRollPayload>()
    var answer: ((String) -> Pair<ActionRequest, ActionOutcome<DiceRollResult>>?)? = null

    override suspend fun sendRoll(playerId: String, payload: DiceRollPayload): Boolean {
        sent += playerId to payload
        return true
    }

    override suspend fun respond(requestId: String) = answer?.invoke(requestId)
    override suspend fun decline(requestId: String) { declined += requestId }
    override fun payloadOf(request: ActionRequest) = payloads[request.id]
}

@OptIn(ExperimentalCoroutinesApi::class)
class DiceLanViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val diceState = FakeDiceStateRepository()
    private val lan = FakeLanGateway()
    private val alex = Participant("a", "Alex")
    private val bob = Participant("b", "Bob")

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.viewModel(playerId: String? = null, navigation: LanNavigation = LanNavigation()): DiceViewModel {
        val json = Json { encodeDefaults = true }
        val clock = FixedClock()
        val registry = RemoteActionRegistry(listOf(DiceRollHandler(DiceRoller(SeededRandomSource()))), json, clock)
        val executor = LocalActionExecutor(registry, ActionCodec(json, SequentialIds(), clock))
        return DiceViewModel(
            executor,
            FakeAppSettingsRepository(AppSettings(animationsEnabled = false, soundsEnabled = false)),
            diceState,
            FakeParticipantRepository(),
            FakeSoundPlayer(),
            FakeDiceSetRepository(),
            SavedStateHandle(listOfNotNull(playerId?.let { DiceViewModel.ARG_PLAYER_ID to it }).toMap()),
            lan,
            navigation,
        ).also { runCurrent() }
    }

    private fun outcome(notation: String, vararg values: Int) =
        DiceRoller(ScriptedRandomSource(*values)).roll((DiceNotationParser.parse(notation) as ParseResult.Success).expression)

    @Test
    fun hostSendsInsteadOfRollingAndSeesTheResult() = runTest(dispatcher) {
        lan.state.value = LanState(role = LanRole.HOST, me = alex, players = listOf(LanPlayer(bob, true)))
        val vm = viewModel(playerId = bob.id)
        assertEquals(bob, vm.uiState.value.requestTarget)
        vm.onNotationChange("2d6+1")
        vm.roll()
        advanceUntilIdle()
        assertEquals(listOf(bob.id to DiceRollPayload("2d6+1")), lan.sent)
        assertNull(vm.uiState.value.latest)
        assertEquals("", diceState.state.lastNotation) // the host's own formula isn't touched

        val entry = LanRollEntry("r1", bob, "2d6+1", false, LanRollState.REQUESTED, 0)
        lan.state.value = lan.state.value.copy(logs = mapOf(bob.id to listOf(entry)))
        runCurrent()
        assertTrue(vm.uiState.value.awaitingReply)
        lan.state.value = lan.state.value.copy(logs = mapOf(bob.id to listOf(entry.copy(state = LanRollState.ROLLED, outcome = outcome("2d6+1", 3, 5)))))
        advanceUntilIdle()
        assertEquals(false, vm.uiState.value.awaitingReply)
        assertEquals(9, vm.uiState.value.latest?.outcome?.total)
        assertEquals(bob, vm.uiState.value.latest?.rolledBy)
        assertEquals(1, vm.uiState.value.recent.size)
    }

    @Test
    fun hostSendsPoolRequests() = runTest(dispatcher) {
        lan.state.value = LanState(role = LanRole.HOST, me = alex, players = listOf(LanPlayer(bob, true)))
        val vm = viewModel(playerId = bob.id)
        vm.setPoolMode(true)
        vm.onDieTapped(6)
        vm.onPoolCountChange("10")
        vm.roll()
        advanceUntilIdle()
        assertEquals(DiceRollPayload("10d6", pool = true), lan.sent.single().second)
        assertEquals(false, diceState.state.poolMode)
    }

    private fun incoming(id: String, notation: String, pool: Boolean = false): ActionRequest {
        val request = ActionRequest(id, "dice.roll", 1, JsonPrimitive(0), null, alex, bob, 0)
        lan.payloads[id] = DiceRollPayload(notation, pool = pool)
        return request
    }

    @Test
    fun playerRollsTheHostsDiceThenGetsTheirOwnBack() = runTest(dispatcher) {
        diceState.state = diceState.state.copy(lastNotation = "1d20+5")
        val vm = viewModel()
        val request = incoming("r1", "3d6")
        lan.state.value = LanState(role = LanRole.PLAYER, me = bob, host = alex, incoming = request)
        runCurrent()
        assertEquals("3d6", vm.uiState.value.incoming?.notation)
        vm.openRequest()
        assertEquals("3d6", vm.uiState.value.notation)
        assertEquals("3d6", vm.uiState.value.activeRequest?.notation)
        val rolled = outcome("3d6", 1, 2, 3)
        lan.answer = { id ->
            request to ActionOutcome.Completed(DiceRollResult(rolled), ActionResponse(id, ActionStatus.COMPLETED, null, null, bob, 1))
        }
        vm.roll()
        advanceUntilIdle()
        assertEquals(6, vm.uiState.value.latest?.outcome?.total)
        assertNull(vm.uiState.value.activeRequest)
        assertEquals("1d20+5", vm.uiState.value.notation)
        assertEquals("1d20+5", diceState.state.lastNotation)
    }

    @Test
    fun declineAndCancelRestoreTheOwnDice() = runTest(dispatcher) {
        diceState.state = diceState.state.copy(lastNotation = "2d8")
        val vm = viewModel()
        lan.state.value = LanState(role = LanRole.PLAYER, me = bob, host = alex, incoming = incoming("r1", "4d6"))
        runCurrent()
        vm.openRequest()
        vm.declineRequest()
        advanceUntilIdle()
        assertEquals(listOf("r1"), lan.declined)
        assertEquals("2d8", vm.uiState.value.notation)

        lan.state.value = lan.state.value.copy(incoming = incoming("r2", "5d10", pool = true))
        runCurrent()
        vm.openRequest()
        assertTrue(vm.uiState.value.poolMode)
        lan.state.value = lan.state.value.copy(incoming = null) // the host cancelled
        runCurrent()
        assertNull(vm.uiState.value.activeRequest)
        assertEquals(false, vm.uiState.value.poolMode)
        assertEquals("2d8", vm.uiState.value.notation)
    }

    @Test
    fun notificationTapOpensTheRequest() = runTest(dispatcher) {
        val navigation = LanNavigation()
        val vm = viewModel(navigation = navigation)
        navigation.requestOpened()
        lan.state.value = LanState(role = LanRole.PLAYER, me = bob, host = alex, incoming = incoming("r1", "2d6"))
        runCurrent()
        assertEquals("r1", vm.uiState.value.activeRequest?.id)
        assertEquals(false, navigation.openRequest.value)
    }
}
