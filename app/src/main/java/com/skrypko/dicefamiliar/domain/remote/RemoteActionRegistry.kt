package com.skrypko.dicefamiliar.domain.remote

import kotlin.time.Clock
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/**
 * Routes [ActionRequest]s to the handler registered for their type. Used for local actions
 * now and, later, for requests arriving from other devices over a transport.
 */
class RemoteActionRegistry(
    handlers: Collection<RemoteActionHandler<*, *>>,
    private val json: Json,
    private val clock: Clock,
) {
    private val byType: Map<String, RemoteActionHandler<*, *>> = handlers.associateBy { it.action.type }

    init {
        require(byType.size == handlers.size) { "Duplicate action types registered" }
    }

    fun supports(type: String): Boolean = type in byType

    suspend fun dispatch(request: ActionRequest, responder: Participant): ActionResponse {
        val handler = byType[request.type]
            ?: return reject(request, responder, RemoteErrors.UNKNOWN_TYPE)
        if (handler.action.version != request.version) {
            return reject(request, responder, RemoteErrors.UNSUPPORTED_VERSION)
        }
        return dispatchTyped(handler, request, responder)
    }

    private suspend fun <Req, Res> dispatchTyped(
        handler: RemoteActionHandler<Req, Res>,
        request: ActionRequest,
        responder: Participant,
    ): ActionResponse {
        val payload = try {
            json.decodeFromJsonElement(handler.action.requestSerializer, request.payload)
        } catch (e: SerializationException) {
            return reject(request, responder, RemoteErrors.MALFORMED_PAYLOAD)
        } catch (e: IllegalArgumentException) {
            return reject(request, responder, RemoteErrors.MALFORMED_PAYLOAD)
        }
        val context = ActionContext(requestedBy = request.from, respondedBy = responder)
        return when (val result = handler.handle(payload, context)) {
            is HandlerResult.Completed -> ActionResponse(
                requestId = request.id,
                status = ActionStatus.COMPLETED,
                payload = json.encodeToJsonElement(handler.action.resultSerializer, result.result),
                respondedBy = responder,
                respondedAt = now(),
            )
            is HandlerResult.Rejected -> reject(request, responder, result.errorCode)
        }
    }

    private fun reject(request: ActionRequest, responder: Participant, code: String) = ActionResponse(
        requestId = request.id,
        status = ActionStatus.REJECTED,
        errorCode = code,
        respondedBy = responder,
        respondedAt = now(),
    )

    private fun now() = clock.now().toEpochMilliseconds()
}
