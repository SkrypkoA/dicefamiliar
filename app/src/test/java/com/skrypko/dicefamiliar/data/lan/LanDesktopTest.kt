package com.skrypko.dicefamiliar.data.lan

import com.skrypko.dicefamiliar.domain.dice.DiceRollAction
import com.skrypko.dicefamiliar.domain.dice.DiceRollHandler
import com.skrypko.dicefamiliar.domain.dice.DiceRollPayload
import com.skrypko.dicefamiliar.domain.dice.DiceRoller
import com.skrypko.dicefamiliar.domain.dice.RollMode
import com.skrypko.dicefamiliar.domain.lan.LanAddress
import com.skrypko.dicefamiliar.domain.lan.LanMessage
import com.skrypko.dicefamiliar.domain.lan.LanProtocol
import com.skrypko.dicefamiliar.domain.lan.LanRollState
import com.skrypko.dicefamiliar.domain.random.KotlinRandomSource
import com.skrypko.dicefamiliar.domain.remote.ActionCodec
import com.skrypko.dicefamiliar.domain.remote.ActionOutcome
import com.skrypko.dicefamiliar.domain.remote.ActionStatus
import com.skrypko.dicefamiliar.domain.remote.Participant
import com.skrypko.dicefamiliar.domain.remote.RemoteActionRegistry
import com.skrypko.dicefamiliar.domain.remote.UuidIdGenerator
import java.io.File
import java.util.concurrent.TimeUnit
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
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/** The desktop test tool (`tools/lan_desktop`) speaks the app's LAN protocol. */
class LanDesktopCompatTest {
    private val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }
    private val codec = ActionCodec(json, UuidIdGenerator(), Clock.System)

    private fun resource(name: String) = File(javaClass.classLoader!!.getResource("lan/$name")!!.toURI())

    private val messages: Map<String, String> by lazy {
        resource("desktop-messages.jsonl").readLines().filter { it.isNotBlank() }.associate { it.substringBefore('\t') to it.substringAfter('\t') }
    }

    @Test
    fun everyDesktopMessageDecodes() {
        val expectedTypes = mapOf(
            "hello" to LanMessage.Hello::class, "welcome" to LanMessage.Welcome::class, "players" to LanMessage.Players::class,
            "ping" to LanMessage.Ping::class, "pong" to LanMessage.Pong::class, "request" to LanMessage.Request::class,
            "cancel" to LanMessage.Cancel::class, "bye" to LanMessage.Bye::class, "response_rolled" to LanMessage.Response::class,
            "response_pool" to LanMessage.Response::class, "response_declined" to LanMessage.Response::class,
        )
        expectedTypes.forEach { (key, type) ->
            val decoded = assertNotNull(key, LanProtocol.decode(messages.getValue(key))).let { LanProtocol.decode(messages.getValue(key))!! }
            assertTrue(key, type.isInstance(decoded))
        }
        assertEquals(Participant("desk-1", "Desk"), (LanProtocol.decode(messages.getValue("hello")) as LanMessage.Hello).participant)
    }

    @Test
    fun desktopRequestsAndResultsDecodeLikeTheApps() {
        val expected = json.parseToJsonElement(resource("desktop-expected.json").readText()).jsonObject
        val request = (LanProtocol.decode(messages.getValue("request")) as LanMessage.Request).request
        assertEquals(DiceRollPayload("3d6+2", RollMode.NORMAL, pool = false), codec.decodePayload(DiceRollAction, request))

        val rolled = (LanProtocol.decode(messages.getValue("response_rolled")) as LanMessage.Response).response
        val outcome = (codec.decode(DiceRollAction, rolled) as ActionOutcome.Completed).result.outcome
        assertEquals(expected.getValue("response_rolled_total").jsonPrimitive.int, outcome.total)
        assertEquals("2d6kh1+1d4-3", outcome.expression.toNotation())
        assertEquals(1, outcome.groups[0].dice.count { it.kept })

        val pool = (LanProtocol.decode(messages.getValue("response_pool")) as LanMessage.Response).response
        val poolOutcome = (codec.decode(DiceRollAction, pool) as ActionOutcome.Completed).result.outcome
        assertEquals(expected.getValue("response_pool_values").jsonArray.map { it.jsonPrimitive.int }, poolOutcome.groups.single().dice.map { it.value })

        val declined = (LanProtocol.decode(messages.getValue("response_declined")) as LanMessage.Response).response
        assertEquals(ActionStatus.DECLINED, (codec.decode(DiceRollAction, declined) as ActionOutcome.Failed).status)
    }
}

/** Live: the app's session manager against the desktop tool over real TCP (skipped without python3). */
class LanDesktopInteropTest {
    private val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }

    private fun script(): File? = listOf(File("../tools/lan_desktop/dicefamiliar_lan.py"), File("tools/lan_desktop/dicefamiliar_lan.py")).firstOrNull { it.isFile }

    private fun pythonAvailable() = try {
        ProcessBuilder("python3", "--version").start().waitFor(5, TimeUnit.SECONDS)
    } catch (_: Exception) {
        false
    }

    private fun manager(me: Participant, scope: CoroutineScope) = LanSessionManager(
        transportProvider = { JvmTcpTransport() },
        selfProvider = { me },
        registry = RemoteActionRegistry(listOf(DiceRollHandler(DiceRoller(KotlinRandomSource(Random(3))))), json, Clock.System),
        codec = ActionCodec(json, UuidIdGenerator(), Clock.System),
        scope = scope,
        now = { System.nanoTime() / 1_000_000 },
        wallTime = { System.currentTimeMillis() },
    )

    @Test
    fun appHostsDesktopPlays() = runBlocking<Unit> {
        val path = script()
        assumeTrue(path != null && pythonAvailable())
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val host = manager(Participant("phone", "Phone"), scope)
        host.host()
        val port = host.state.value.port!!
        val desktop = ProcessBuilder("python3", path!!.path, "--auto-player", "127.0.0.1:$port", "--name", "Desk").redirectErrorStream(true).start()
        try {
            val joined = withTimeout(10_000) { host.state.first { it.players.any { p -> p.connected } } }
            val player = joined.players.single().participant
            assertEquals("Desk", player.name)
            listOf(DiceRollPayload("2d6+1"), DiceRollPayload("8d6", pool = true), DiceRollPayload("1d20", RollMode.ADVANTAGE)).forEach { payload ->
                assertTrue(host.sendRoll(player.id, payload))
                val entry = withTimeout(10_000) { host.state.first { it.log(player.id).first().state != LanRollState.REQUESTED } }.log(player.id).first()
                assertEquals(LanRollState.ROLLED, entry.state)
                val outcome = entry.outcome!!
                when {
                    payload.pool -> assertEquals(8, outcome.groups.single().dice.size)
                    payload.mode == RollMode.ADVANTAGE -> assertEquals("2d20kh1", outcome.expression.toNotation())
                    else -> assertEquals(outcome.groups.sumOf { it.subtotal } + 1, outcome.total)
                }
            }
            host.leave()
            assertTrue(desktop.waitFor(10, TimeUnit.SECONDS))
        } finally {
            desktop.destroy()
            scope.cancel()
        }
    }

    @Test
    fun desktopHostsAppPlays() = runBlocking<Unit> {
        val path = script()
        assumeTrue(path != null && pythonAvailable())
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val desktop = ProcessBuilder("python3", path!!.path, "--auto-host", "0", "--requests", "3d6+2;10d6:pool;1d20:DISADVANTAGE").start()
        val output = desktop.inputStream.bufferedReader()
        try {
            val port = json.parseToJsonElement(output.readLine()).jsonObject.getValue("port").jsonPrimitive.int
            val player = manager(Participant("phone", "Phone"), scope)
            player.joinAddress(LanAddress("127.0.0.1", port))
            withTimeout(10_000) { player.state.first { it.host != null } }
            repeat(3) {
                val request = withTimeout(10_000) { player.state.first { it.incoming != null } }.incoming!!
                val (_, outcome) = player.respond(request.id)!!
                assertTrue(outcome is ActionOutcome.Completed)
                val line = json.parseToJsonElement(output.readLine()).jsonObject
                assertEquals("ROLLED", line.getValue("state").jsonPrimitive.content)
                assertEquals((outcome as ActionOutcome.Completed).result.outcome.total, line.getValue("total").jsonPrimitive.int)
                if (it == 2) assertEquals("2d20kl1", line.getValue("rolled").jsonPrimitive.content)
            }
            assertTrue(desktop.waitFor(10, TimeUnit.SECONDS))
            assertEquals(0, desktop.exitValue())
        } finally {
            desktop.destroy()
            scope.cancel()
        }
    }
}
