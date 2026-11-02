package com.skrypko.dicefamiliar.domain.remote

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/** Wire envelope for a request; [payload] is the action-specific request encoded as JSON. */
@Serializable
data class ActionRequest(
    val id: String,
    val type: String,
    val version: Int,
    val payload: JsonElement,
    val label: String? = null,
    val from: Participant,
    val to: Participant? = null,
    val createdAt: Long,
)

@Serializable
enum class ActionStatus { COMPLETED, REJECTED, DECLINED }

/** Wire envelope for a response to [ActionRequest] with the same [requestId]. */
@Serializable
data class ActionResponse(
    val requestId: String,
    val status: ActionStatus,
    val payload: JsonElement? = null,
    val errorCode: String? = null,
    val respondedBy: Participant,
    val respondedAt: Long,
)

/** Error codes produced by the remote layer itself (handlers add their own). */
object RemoteErrors {
    const val UNKNOWN_TYPE = "remote.unknown_type"
    const val UNSUPPORTED_VERSION = "remote.unsupported_version"
    const val MALFORMED_PAYLOAD = "remote.malformed_payload"
    const val MALFORMED_RESULT = "remote.malformed_result"
}
