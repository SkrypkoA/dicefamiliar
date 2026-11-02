package com.skrypko.dicefamiliar.domain.dice

import com.skrypko.dicefamiliar.domain.remote.ActionCodec
import com.skrypko.dicefamiliar.domain.remote.ActionOutcome
import com.skrypko.dicefamiliar.domain.remote.ActionStatus
import com.skrypko.dicefamiliar.domain.remote.Participant
import com.skrypko.dicefamiliar.domain.remote.RemoteActionRegistry
import com.skrypko.dicefamiliar.testing.FixedClock
import com.skrypko.dicefamiliar.testing.ScriptedRandomSource
import com.skrypko.dicefamiliar.testing.SequentialIds
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class DiceRollHandlerTest {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val clock = FixedClock()
    private val codec = ActionCodec(json, SequentialIds(), clock)
    private val gm = Participant("gm", "Game master")
    private val player = Participant("p1", "Player")

    private fun registry(vararg values: Int) =
        RemoteActionRegistry(listOf(DiceRollHandler(DiceRoller(ScriptedRandomSource(*values)))), json, clock)

    @Test
    fun remoteRequestIsRolledByResponder() = runTest {
        val request = codec.request(DiceRollAction, DiceRollPayload("d20+5", RollMode.ADVANTAGE), gm, player, "Stealth")
        val response = registry(7, 15).dispatch(request, responder = player)

        assertEquals(ActionStatus.COMPLETED, response.status)
        assertEquals(player, response.respondedBy)
        val outcome = (codec.decode(DiceRollAction, response) as ActionOutcome.Completed).result.outcome
        assertEquals("2d20kh1+5", outcome.expression.toNotation())
        assertEquals(20, outcome.total)
    }

    @Test
    fun invalidOrOversizedNotationIsRejected() = runTest {
        val bad = registry().dispatch(codec.request(DiceRollAction, DiceRollPayload("3d6+"), gm), player)
        assertEquals(ActionStatus.REJECTED, bad.status)
        assertEquals(NotationError.INCOMPLETE.remoteCode, bad.errorCode)

        val huge = registry().dispatch(codec.request(DiceRollAction, DiceRollPayload("100d6+100d6+100d6"), gm), player)
        assertEquals(NotationError.fromRemoteCode(huge.errorCode), NotationError.TOO_MANY_DICE)
    }
}
