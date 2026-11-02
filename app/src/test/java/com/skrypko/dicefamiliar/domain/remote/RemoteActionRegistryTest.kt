package com.skrypko.dicefamiliar.domain.remote

import com.skrypko.dicefamiliar.testing.FixedClock
import com.skrypko.dicefamiliar.testing.SequentialIds
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Uses a feature-independent echo action to prove the layer is reusable. */
class RemoteActionRegistryTest {

    @Serializable
    data class EchoRequest(val text: String)

    @Serializable
    data class EchoResult(val text: String, val by: String)

    object EchoAction : RemoteAction<EchoRequest, EchoResult> {
        override val type = "test.echo"
        override val version = 2
        override val requestSerializer: KSerializer<EchoRequest> = EchoRequest.serializer()
        override val resultSerializer: KSerializer<EchoResult> = EchoResult.serializer()
    }

    object EchoHandler : RemoteActionHandler<EchoRequest, EchoResult> {
        override val action = EchoAction
        override suspend fun handle(request: EchoRequest, context: ActionContext): HandlerResult<EchoResult> =
            if (request.text.isEmpty()) {
                HandlerResult.Rejected("echo.empty")
            } else {
                HandlerResult.Completed(EchoResult(request.text.uppercase(), context.respondedBy.id))
            }
    }

    private val json = Json { ignoreUnknownKeys = true }
    private val clock = FixedClock(42_000)
    private val codec = ActionCodec(json, SequentialIds("req"), clock)
    private val registry = RemoteActionRegistry(listOf(EchoHandler), json, clock)
    private val alice = Participant("a", "Alice")
    private val bob = Participant("b", "Bob")

    @Test
    fun dispatchesToHandlerAndEncodesResult() = runTest {
        val request = codec.request(EchoAction, EchoRequest("hi"), from = alice, to = bob, label = "test")
        assertEquals("req-1", request.id)
        assertEquals(42_000, request.createdAt)

        val response = registry.dispatch(request, responder = bob)

        assertEquals(ActionStatus.COMPLETED, response.status)
        assertEquals("req-1", response.requestId)
        assertEquals(bob, response.respondedBy)
        val outcome = codec.decode(EchoAction, response) as ActionOutcome.Completed
        assertEquals(EchoResult("HI", "b"), outcome.result)
    }

    @Test
    fun handlerRejectionIsReported() = runTest {
        val response = registry.dispatch(codec.request(EchoAction, EchoRequest(""), alice), bob)
        assertEquals(ActionStatus.REJECTED, response.status)
        assertEquals("echo.empty", response.errorCode)
        assertNull(response.payload)
        assertTrue(codec.decode(EchoAction, response) is ActionOutcome.Failed)
    }

    @Test
    fun unknownTypeVersionAndMalformedPayloadAreRejected() = runTest {
        val valid = codec.request(EchoAction, EchoRequest("x"), alice)
        assertEquals(RemoteErrors.UNKNOWN_TYPE, registry.dispatch(valid.copy(type = "nope"), bob).errorCode)
        assertEquals(RemoteErrors.UNSUPPORTED_VERSION, registry.dispatch(valid.copy(version = 1), bob).errorCode)
        val malformed = valid.copy(payload = buildJsonObject { put("wrong", JsonPrimitive(1)) })
        assertEquals(RemoteErrors.MALFORMED_PAYLOAD, registry.dispatch(malformed, bob).errorCode)
    }

    @Test
    fun envelopesRoundTripThroughJson() = runTest {
        val request = codec.request(EchoAction, EchoRequest("hi"), from = alice, to = bob, label = "Stealth")
        val response = registry.dispatch(request, bob)
        assertEquals(request, json.decodeFromString<ActionRequest>(json.encodeToString(request)))
        assertEquals(response, json.decodeFromString<ActionResponse>(json.encodeToString(response)))
    }

    @Test(expected = IllegalArgumentException::class)
    fun duplicateTypesAreRefused() {
        RemoteActionRegistry(listOf(EchoHandler, EchoHandler), json, clock)
    }
}
