package com.skrypko.dicefamiliar.domain.remote

import kotlinx.serialization.KSerializer

/**
 * Typed descriptor of an action that can be executed locally or requested from another
 * participant (e.g. over LAN). Each feature defines its own action; this layer knows nothing
 * about any particular feature.
 */
interface RemoteAction<Req, Res> {
    /** Stable wire identifier, e.g. "dice.roll". */
    val type: String

    /** Payload schema version; a receiver rejects versions it does not support. */
    val version: Int
    val requestSerializer: KSerializer<Req>
    val resultSerializer: KSerializer<Res>
}

/** Who asked and who is answering. */
data class ActionContext(val requestedBy: Participant, val respondedBy: Participant)

sealed interface HandlerResult<out Res> {
    data class Completed<Res>(val result: Res) : HandlerResult<Res>
    data class Rejected(val errorCode: String) : HandlerResult<Nothing>
}

/** Executes one [RemoteAction] on this device. Must treat [request] as untrusted input. */
interface RemoteActionHandler<Req, Res> {
    val action: RemoteAction<Req, Res>
    suspend fun handle(request: Req, context: ActionContext): HandlerResult<Res>
}
