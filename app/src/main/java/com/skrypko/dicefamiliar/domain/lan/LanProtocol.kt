package com.skrypko.dicefamiliar.domain.lan

import com.skrypko.dicefamiliar.domain.remote.ActionRequest
import com.skrypko.dicefamiliar.domain.remote.ActionResponse
import com.skrypko.dicefamiliar.domain.remote.Participant
import kotlinx.serialization.SerialName
import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Why a session ended. */
@Serializable
enum class ByeReason { LEFT, HOST_STOPPED, KICKED, VERSION_MISMATCH }

/**
 * Messages between a host and its players, one JSON object per line on any [com.skrypko.dicefamiliar.data.lan.transport.LanTransport].
 * Feature work travels as the existing remote-action envelopes ([ActionRequest] / [ActionResponse]).
 */
@Serializable
sealed interface LanMessage {
    /** Player → host, first message on a new connection. */
    @Serializable
    @SerialName("hello")
    data class Hello(val participant: Participant, val version: Int = LanProtocol.VERSION) : LanMessage

    /** Host → player, the answer to [Hello]. */
    @Serializable
    @SerialName("welcome")
    data class Welcome(val host: Participant, val players: List<Participant>) : LanMessage

    /** Host → players whenever the table changes. */
    @Serializable
    @SerialName("players")
    data class Players(val players: List<Participant>) : LanMessage

    @Serializable
    @SerialName("bye")
    data class Bye(val reason: ByeReason) : LanMessage

    @Serializable
    @SerialName("ping")
    data object Ping : LanMessage

    @Serializable
    @SerialName("pong")
    data object Pong : LanMessage

    /** Host → player: please run this action (e.g. `dice.roll`). */
    @Serializable
    @SerialName("request")
    data class Request(val request: ActionRequest) : LanMessage

    /** Player → host: the result (or a decline) for a [Request]. */
    @Serializable
    @SerialName("response")
    data class Response(val response: ActionResponse) : LanMessage

    /** Host → player: the request is no longer wanted. */
    @Serializable
    @SerialName("cancel")
    data class Cancel(val requestId: String) : LanMessage
}

object LanProtocol {
    const val VERSION = 1

    /** Longest accepted line (one message), in characters. */
    const val MAX_LINE = 64 * 1024

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        classDiscriminator = "t"
    }

    /** One line, without the line break. */
    fun encode(message: LanMessage): String = json.encodeToString(LanMessage.serializer(), message)

    /** The message on [line], or `null` for anything unknown, malformed or too long (it is ignored). */
    fun decode(line: String): LanMessage? {
        if (line.length > MAX_LINE || line.isBlank()) return null
        return try {
            json.decodeFromString(LanMessage.serializer(), line)
        } catch (_: SerializationException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        }
    }
}
