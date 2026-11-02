package com.skrypko.dicefamiliar.domain.remote

import kotlin.time.Clock
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/** Typed outcome of decoding an [ActionResponse]. */
sealed interface ActionOutcome<out Res> {
    data class Completed<Res>(val result: Res, val response: ActionResponse) : ActionOutcome<Res>
    data class Failed(val status: ActionStatus, val errorCode: String?, val response: ActionResponse) :
        ActionOutcome<Nothing>
}

/** Builds typed [ActionRequest]s and decodes [ActionResponse]s. */
class ActionCodec(
    private val json: Json,
    private val idGenerator: IdGenerator,
    private val clock: Clock,
) {
    fun <Req, Res> request(
        action: RemoteAction<Req, Res>,
        payload: Req,
        from: Participant,
        to: Participant? = null,
        label: String? = null,
    ): ActionRequest = ActionRequest(
        id = idGenerator.newId(),
        type = action.type,
        version = action.version,
        payload = json.encodeToJsonElement(action.requestSerializer, payload),
        label = label,
        from = from,
        to = to,
        createdAt = clock.now().toEpochMilliseconds(),
    )

    fun <Req, Res> decodePayload(action: RemoteAction<Req, Res>, request: ActionRequest): Req =
        json.decodeFromJsonElement(action.requestSerializer, request.payload)

    fun <Req, Res> decode(action: RemoteAction<Req, Res>, response: ActionResponse): ActionOutcome<Res> {
        if (response.status != ActionStatus.COMPLETED || response.payload == null) {
            return ActionOutcome.Failed(response.status, response.errorCode, response)
        }
        return try {
            ActionOutcome.Completed(json.decodeFromJsonElement(action.resultSerializer, response.payload), response)
        } catch (e: SerializationException) {
            ActionOutcome.Failed(ActionStatus.REJECTED, RemoteErrors.MALFORMED_RESULT, response)
        } catch (e: IllegalArgumentException) {
            ActionOutcome.Failed(ActionStatus.REJECTED, RemoteErrors.MALFORMED_RESULT, response)
        }
    }
}
