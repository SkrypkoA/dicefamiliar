package com.skrypko.dicefamiliar.domain.lan

import com.skrypko.dicefamiliar.domain.remote.ActionRequest
import com.skrypko.dicefamiliar.domain.remote.Participant
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class LanProtocolTest {
    private val alex = Participant("a", "Alex")

    @Test
    fun roundTrip() {
        val messages = listOf(
            LanMessage.Hello(alex),
            LanMessage.Welcome(alex, listOf(Participant("b", "Bob"))),
            LanMessage.Players(emptyList()),
            LanMessage.Bye(ByeReason.KICKED),
            LanMessage.Ping,
            LanMessage.Pong,
            LanMessage.Request(ActionRequest("1", "dice.roll", 1, JsonPrimitive("x"), null, alex, null, 5)),
            LanMessage.Cancel("1"),
        )
        messages.forEach { message ->
            val line = LanProtocol.encode(message)
            assertFalse(line, '\n' in line)
            assertEquals(message, LanProtocol.decode(line))
        }
    }

    @Test
    fun garbageAndUnknownAreIgnored() {
        assertNull(LanProtocol.decode("not json"))
        assertNull(LanProtocol.decode("""{"t":"teleport"}"""))
        assertNull(LanProtocol.decode(""))
        assertNull(LanProtocol.decode("""{"t":"ping","x":"""" + "a".repeat(LanProtocol.MAX_LINE) + "\"}"))
        assertEquals(LanMessage.Ping, LanProtocol.decode("""{"t":"ping","extra":1}"""))
    }

    @Test
    fun helloCarriesTheVersion() {
        assertEquals(LanProtocol.VERSION, (LanProtocol.decode(LanProtocol.encode(LanMessage.Hello(alex))) as LanMessage.Hello).version)
    }
}
