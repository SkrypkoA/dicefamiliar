package com.skrypko.dicefamiliar.data.lan

import com.skrypko.dicefamiliar.data.lan.transport.LanHostInfo
import com.skrypko.dicefamiliar.domain.dice.DiceRollHandler
import com.skrypko.dicefamiliar.domain.dice.DiceRollPayload
import com.skrypko.dicefamiliar.domain.dice.DiceRoller
import com.skrypko.dicefamiliar.domain.lan.LanMessage
import com.skrypko.dicefamiliar.domain.lan.LanProtocol
import com.skrypko.dicefamiliar.domain.lan.LanRollState
import com.skrypko.dicefamiliar.domain.remote.ActionCodec
import com.skrypko.dicefamiliar.domain.remote.ActionOutcome
import com.skrypko.dicefamiliar.domain.remote.Participant
import com.skrypko.dicefamiliar.domain.remote.RemoteActionRegistry
import com.skrypko.dicefamiliar.testing.FixedClock
import com.skrypko.dicefamiliar.testing.ScriptedRandomSource
import com.skrypko.dicefamiliar.testing.SequentialIds
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LanSessionManagerTest {
    private val network = FakeNetwork()
    private val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }
    private val alex = Participant("a", "Alex")
    private val bob = Participant("b", "Bob")
    private val cara = Participant("c", "Cara")

    private fun TestScope.manager(me: Participant, vararg dice: Int, transport: FakeLanTransport = FakeLanTransport(network)): LanSessionManager {
        val clock = FixedClock()
        val registry = RemoteActionRegistry(listOf(DiceRollHandler(DiceRoller(ScriptedRandomSource(*dice)))), json, clock)
        return LanSessionManager(
            transportProvider = { transport },
            selfProvider = { me },
            registry = registry,
            codec = ActionCodec(json, SequentialIds(me.id), clock),
            scope = backgroundScope,
            now = { testScheduler.currentTime },
            wallTime = { 1_000L },
        )
    }

    private val table = LanHostInfo("host-Alex", "Alex")

    private suspend fun TestScope.tableWith(vararg players: LanSessionManager): LanSessionManager {
        val host = manager(alex)
        host.host()
        runCurrent()
        players.forEach {
            it.join(table)
            runCurrent()
        }
        return host
    }

    @Test
    fun playersJoinAndSeeTheHost() = runTest {
        val b = manager(bob)
        val c = manager(cara)
        val host = tableWith(b, c)
        assertEquals(LanRole.HOST, host.state.value.role)
        assertEquals(listOf(bob, cara), host.state.value.players.map { it.participant })
        assertEquals(LanRole.PLAYER, b.state.value.role)
        assertEquals(alex, b.state.value.host)
    }

    @Test
    fun hostRequestsPlayerRollsHostSeesTheResult() = runTest {
        val b = manager(bob, 3, 5)
        val host = tableWith(b)
        assertTrue(host.sendRoll(bob.id, DiceRollPayload("2d6+1")))
        runCurrent()
        val incoming = assertNotNull(b.state.value.incoming).let { b.state.value.incoming!! }
        assertEquals(alex, incoming.from)
        assertEquals(LanRollState.REQUESTED, host.state.value.log(bob.id).single().state)

        val (_, outcome) = b.respond(incoming.id)!!
        runCurrent()
        assertEquals(9, (outcome as ActionOutcome.Completed).result.outcome.total)
        assertNull(b.state.value.incoming)
        val entry = host.state.value.log(bob.id).single()
        assertEquals(LanRollState.ROLLED, entry.state)
        assertEquals(9, entry.outcome?.total)
        assertEquals("2d6+1", entry.notation)
        assertEquals(LanRollState.ROLLED, b.state.value.log(bob.id).single().state)
    }

    @Test
    fun poolRequestsKeepTheirFlag() = runTest {
        val b = manager(bob, 1, 5, 6)
        val host = tableWith(b)
        host.sendRoll(bob.id, DiceRollPayload("3d6", pool = true))
        runCurrent()
        b.respond(b.state.value.incoming!!.id)
        runCurrent()
        val entry = host.state.value.log(bob.id).single()
        assertTrue(entry.pool)
        assertEquals(listOf(1, 5, 6), entry.outcome!!.groups.single().dice.map { it.value })
    }

    @Test
    fun declineAndANewRequestCancelTheOld() = runTest {
        val b = manager(bob)
        val host = tableWith(b)
        host.sendRoll(bob.id, DiceRollPayload("1d20"))
        runCurrent()
        val first = b.state.value.incoming!!.id
        host.sendRoll(bob.id, DiceRollPayload("2d20"))
        runCurrent()
        assertEquals(LanRollState.CANCELLED, host.state.value.log(bob.id).last().state)
        val second = b.state.value.incoming!!
        assertTrue(second.id != first)
        b.decline(second.id)
        runCurrent()
        assertEquals(LanRollState.DECLINED, host.state.value.log(bob.id).first().state)
        assertNull(b.respond(second.id))
    }

    @Test
    fun hostOnlyAsksConnectedPlayers() = runTest {
        val host = tableWith()
        assertFalse(host.sendRoll("nobody", DiceRollPayload("1d6")))
    }

    @Test
    fun kickEndsThePlayersSession() = runTest {
        val b = manager(bob)
        val host = tableWith(b)
        host.kick(bob.id)
        runCurrent()
        assertTrue(host.state.value.players.isEmpty())
        assertEquals(LanRole.NONE, b.state.value.role)
        assertEquals(LanError.KICKED, b.state.value.error)
    }

    @Test
    fun hostStoppingTellsPlayers() = runTest {
        val b = manager(bob)
        val host = tableWith(b)
        host.leave()
        runCurrent()
        assertEquals(LanRole.NONE, host.state.value.role)
        assertEquals(LanError.HOST_STOPPED, b.state.value.error)
    }

    @Test
    fun playerLeavingIsShownDisconnectedWithPendingRequests() = runTest {
        val b = manager(bob)
        val host = tableWith(b)
        host.sendRoll(bob.id, DiceRollPayload("1d6"))
        runCurrent()
        b.leave()
        runCurrent()
        assertFalse(host.state.value.players.single().connected)
        assertEquals(LanRollState.DISCONNECTED, host.state.value.log(bob.id).single().state)
        // Rejoining is the same player again.
        b.join(table)
        runCurrent()
        assertTrue(host.state.value.players.single().connected)
        assertEquals(1, host.state.value.log(bob.id).size)
    }

    @Test
    fun silentPlayerTimesOut() = runTest {
        val transport = FakeLanTransport(network)
        val b = manager(bob, transport = transport)
        val host = tableWith(b)
        transport.last!!.goSilent()
        advanceTimeBy(LanSessionManager.TIMEOUT_MS - 1)
        runCurrent()
        assertTrue(host.state.value.players.single().connected)
        advanceTimeBy(LanSessionManager.TIMEOUT_MS)
        runCurrent()
        assertFalse(host.state.value.players.single().connected)
    }

    @Test
    fun connectFailureIsReported() = runTest {
        val transport = FakeLanTransport(network).apply { failConnect = true }
        val b = manager(bob, transport = transport)
        tableWith()
        b.join(table)
        runCurrent()
        assertEquals(LanRole.NONE, b.state.value.role)
        assertEquals(LanError.CONNECT_FAILED, b.state.value.error)
    }

    @Test
    fun heartbeatKeepsALiveSessionOpen() = runTest {
        val b = manager(bob)
        val host = tableWith(b)
        advanceTimeBy(LanSessionManager.TIMEOUT_MS * 3)
        runCurrent()
        assertTrue(host.state.value.players.single().connected)
        assertEquals(LanRole.PLAYER, b.state.value.role)
        assertTrue(network.lines.any { LanProtocol.decode(it) == LanMessage.Ping })
    }
}
