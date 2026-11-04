package com.skrypko.dicefamiliar.data.lan

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.skrypko.dicefamiliar.data.lan.transport.WifiNsdTransport
import com.skrypko.dicefamiliar.domain.dice.DiceRollHandler
import com.skrypko.dicefamiliar.domain.dice.DiceRollPayload
import com.skrypko.dicefamiliar.domain.dice.DiceRoller
import com.skrypko.dicefamiliar.domain.lan.LanAddress
import com.skrypko.dicefamiliar.domain.lan.LanRollState
import com.skrypko.dicefamiliar.domain.random.KotlinRandomSource
import com.skrypko.dicefamiliar.domain.remote.ActionCodec
import com.skrypko.dicefamiliar.domain.remote.Participant
import com.skrypko.dicefamiliar.domain.remote.RemoteActionRegistry
import com.skrypko.dicefamiliar.domain.remote.UuidIdGenerator
import kotlin.random.Random
import kotlin.time.Clock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** The real Wi-Fi transport over localhost on one phone: host, join by address, request, roll, result. */
@RunWith(AndroidJUnit4::class)
class WifiTransportTest {
    private val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }

    private fun manager(me: Participant, scope: CoroutineScope): LanSessionManager {
        val transport = WifiNsdTransport(ApplicationProvider.getApplicationContext())
        val registry = RemoteActionRegistry(listOf(DiceRollHandler(DiceRoller(KotlinRandomSource(Random(1))))), json, Clock.System)
        return LanSessionManager({ transport }, { me }, registry, ActionCodec(json, UuidIdGenerator(), Clock.System), scope, { System.nanoTime() / 1_000_000 }, { 0L })
    }

    @Test
    fun hostAndPlayerTalkOverTcp() = runBlocking<Unit> {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val host = manager(Participant("a", "Alex"), scope)
        val player = manager(Participant("b", "Bob"), scope)
        try {
            host.host()
            val port = host.state.value.port!!
            player.joinAddress(LanAddress("127.0.0.1", port))
            withTimeout(5_000) { host.state.first { it.players.any { p -> p.connected } } }
            withTimeout(5_000) { player.state.first { it.host != null } }

            assertTrue(host.sendRoll("b", DiceRollPayload("4d6", pool = true)))
            val request = withTimeout(5_000) { player.state.first { it.incoming != null } }.incoming!!
            player.respond(request.id)
            val entry = withTimeout(5_000) { host.state.first { it.log("b").firstOrNull()?.state == LanRollState.ROLLED } }.log("b").first()
            assertEquals(4, entry.outcome!!.groups.single().dice.size)
            assertTrue(entry.pool)

            player.leave()
            withTimeout(5_000) { host.state.first { it.players.none { p -> p.connected } } }
        } finally {
            host.leave()
            scope.cancel()
        }
    }
}
