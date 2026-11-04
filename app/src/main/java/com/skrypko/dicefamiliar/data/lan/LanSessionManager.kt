package com.skrypko.dicefamiliar.data.lan

import com.skrypko.dicefamiliar.data.lan.transport.LanConnection
import com.skrypko.dicefamiliar.data.lan.transport.LanHostInfo
import com.skrypko.dicefamiliar.data.lan.transport.LanServer
import com.skrypko.dicefamiliar.data.lan.transport.LanTransport
import com.skrypko.dicefamiliar.domain.dice.DiceRollAction
import com.skrypko.dicefamiliar.domain.dice.DiceRollPayload
import com.skrypko.dicefamiliar.domain.dice.DiceRollResult
import com.skrypko.dicefamiliar.domain.lan.ByeReason
import com.skrypko.dicefamiliar.domain.lan.LanAddress
import com.skrypko.dicefamiliar.domain.lan.LanMessage
import com.skrypko.dicefamiliar.domain.lan.LanProtocol
import com.skrypko.dicefamiliar.domain.lan.LanRollEntry
import com.skrypko.dicefamiliar.domain.lan.LanRollState
import com.skrypko.dicefamiliar.domain.remote.ActionCodec
import com.skrypko.dicefamiliar.domain.remote.ActionOutcome
import com.skrypko.dicefamiliar.domain.remote.ActionRequest
import com.skrypko.dicefamiliar.domain.remote.ActionResponse
import com.skrypko.dicefamiliar.domain.remote.ActionStatus
import com.skrypko.dicefamiliar.domain.remote.Participant
import com.skrypko.dicefamiliar.domain.remote.RemoteActionRegistry
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

enum class LanRole { NONE, HOST, PLAYER }

/** Why the last session ended or failed; shown once by the LAN screen. */
enum class LanError { CONNECT_FAILED, HOST_LOST, HOST_STOPPED, KICKED, VERSION_MISMATCH, HOSTING_FAILED }

data class LanPlayer(val participant: Participant, val connected: Boolean)

data class LanState(
    val role: LanRole = LanRole.NONE,
    val me: Participant? = null,
    /** Player: the table joined. */
    val host: Participant? = null,
    /** Host: everyone who joined this session (disconnected ones stay, greyed, with their logs). */
    val players: List<LanPlayer> = emptyList(),
    /** Roll logs by player id, newest first. A player keeps their own log under their own id. */
    val logs: Map<String, List<LanRollEntry>> = emptyMap(),
    /** Player: the host's pending request, if any (a newer one replaces it). */
    val incoming: ActionRequest? = null,
    val joining: Boolean = false,
    val error: LanError? = null,
    /** Host: the port players connect to (shown with this device's addresses for "Join by address"). */
    val port: Int? = null,
) {
    fun log(playerId: String): List<LanRollEntry> = logs[playerId].orEmpty()
}

/** What the Dice screen needs from LAN play (a seam for tests; [LanSessionManager] is the implementation). */
interface LanDiceGateway {
    val state: StateFlow<LanState>
    suspend fun sendRoll(playerId: String, payload: DiceRollPayload): Boolean
    suspend fun respond(requestId: String): Pair<ActionRequest, ActionOutcome<DiceRollResult>>?
    suspend fun decline(requestId: String)

    /** The dice asked for in [request], or `null` when it isn't a readable `dice.roll` request. */
    fun payloadOf(request: ActionRequest): DiceRollPayload?
}

/** No LAN (tests, previews). */
object NoLan : LanDiceGateway {
    override val state: StateFlow<LanState> = MutableStateFlow(LanState())
    override suspend fun sendRoll(playerId: String, payload: DiceRollPayload) = false
    override suspend fun respond(requestId: String): Pair<ActionRequest, ActionOutcome<DiceRollResult>>? = null
    override suspend fun decline(requestId: String) = Unit
    override fun payloadOf(request: ActionRequest): DiceRollPayload? = null
}

/**
 * The LAN session: hosting a table or playing at one. The only class that talks to the [LanTransport];
 * dice work travels as `dice.roll` remote-action envelopes and runs through [RemoteActionRegistry] on the
 * player's device (which re-parses the notation and owns the randomness).
 *
 * [now] is a monotonic millisecond clock for the heartbeat (virtual time in tests); [wallTime] stamps responses.
 * [log] receives debug messages (Logcat in the app, nothing in unit tests).
 */
class LanSessionManager(
    private val transportProvider: suspend () -> LanTransport,
    private val selfProvider: suspend () -> Participant,
    private val registry: RemoteActionRegistry,
    private val codec: ActionCodec,
    private val scope: CoroutineScope,
    private val now: () -> Long,
    private val wallTime: () -> Long,
    private val log: (String, Throwable?) -> Unit = { _, _ -> },
) : LanDiceGateway {
    private val _state = MutableStateFlow(LanState())
    override val state: StateFlow<LanState> = _state.asStateFlow()

    private val mutex = Mutex()
    private var server: LanServer? = null
    private var hostConnection: LanConnection? = null
    private val connections = ConcurrentHashMap<String, LanConnection>()
    private val lastSeen = ConcurrentHashMap<LanConnection, Long>()
    private val jobs = mutableListOf<Job>()

    // --- Discovery -----------------------------------------------------------------------------

    /** Hosts on the network, while collected. */
    suspend fun discoverHosts() = transportProvider().discoverHosts()

    // --- Host ----------------------------------------------------------------------------------

    suspend fun host() = mutex.withLock {
        if (_state.value.role != LanRole.NONE) return@withLock
        val me = selfProvider()
        val started = try {
            transportProvider().startHosting(me.name)
        } catch (e: IOException) {
            log("Can't host", e)
            _state.update { it.copy(error = LanError.HOSTING_FAILED) }
            return@withLock
        }
        server = started
        _state.value = LanState(role = LanRole.HOST, me = me, port = started.port)
        jobs += scope.launch { started.connections.collect { connection -> scope.launch { serve(connection, me) } } }
        jobs += scope.launch { heartbeat() }
    }

    /** One player's connection, from Hello to disconnect. */
    private suspend fun serve(connection: LanConnection, me: Participant) {
        var player: Participant? = null
        try {
            connection.incoming.collect { line ->
                val message = LanProtocol.decode(line) ?: return@collect
                lastSeen[connection] = now()
                val current = player
                if (current == null) {
                    if (message !is LanMessage.Hello) return@collect
                    if (message.version != LanProtocol.VERSION) {
                        connection.trySend(LanMessage.Bye(ByeReason.VERSION_MISMATCH))
                        connection.close()
                        return@collect
                    }
                    player = message.participant
                    addPlayer(message.participant, connection, me)
                    return@collect
                }
                when (message) {
                    LanMessage.Ping -> connection.trySend(LanMessage.Pong)
                    is LanMessage.Response -> onResponse(current, message.response)
                    is LanMessage.Bye -> connection.close()
                    else -> Unit
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            log("Player connection ended", e)
        } finally {
            lastSeen.remove(connection)
            player?.let { left(it, connection) }
        }
    }

    private suspend fun addPlayer(player: Participant, connection: LanConnection, me: Participant) {
        connections.put(player.id, connection)?.takeIf { it !== connection }?.close()
        _state.update { state ->
            val others = state.players.filterNot { it.participant.id == player.id }
            state.copy(players = others + LanPlayer(player, connected = true))
        }
        connection.trySend(LanMessage.Welcome(me, connectedPlayers()))
        broadcastPlayers()
    }

    private suspend fun left(player: Participant, connection: LanConnection) {
        if (!connections.remove(player.id, connection)) return
        _state.update { state ->
            state.copy(
                players = state.players.map { if (it.participant.id == player.id) it.copy(connected = false) else it },
                logs = state.logs.mapEntry(player.id) { entries ->
                    entries.map { if (it.state == LanRollState.REQUESTED) it.copy(state = LanRollState.DISCONNECTED) else it }
                },
            )
        }
        broadcastPlayers()
    }

    /** Removes [playerId] from the table (and their log). */
    suspend fun kick(playerId: String) {
        val connection = connections.remove(playerId)
        _state.update { state ->
            state.copy(players = state.players.filterNot { it.participant.id == playerId }, logs = state.logs - playerId)
        }
        connection?.let {
            it.trySend(LanMessage.Bye(ByeReason.KICKED))
            it.close()
        }
        broadcastPlayers()
    }

    /**
     * Asks [playerId] to roll [payload]; a still-pending request to the same player is cancelled first.
     * Returns `false` when the player isn't connected.
     */
    override suspend fun sendRoll(playerId: String, payload: DiceRollPayload): Boolean {
        val state = _state.value
        val me = state.me ?: return false
        if (state.role != LanRole.HOST) return false
        val player = state.players.firstOrNull { it.participant.id == playerId && it.connected }?.participant ?: return false
        val connection = connections[playerId] ?: return false
        state.log(playerId).filter { it.state == LanRollState.REQUESTED }.forEach { pending ->
            connection.trySend(LanMessage.Cancel(pending.requestId))
            updateEntry(playerId, pending.requestId) { it.copy(state = LanRollState.CANCELLED) }
        }
        val request = codec.request(DiceRollAction, payload, from = me, to = player)
        addEntry(playerId, LanRollEntry(request.id, player, payload.notation, payload.pool, LanRollState.REQUESTED, request.createdAt))
        return connection.trySend(LanMessage.Request(request)).also { sent ->
            if (!sent) updateEntry(playerId, request.id) { it.copy(state = LanRollState.DISCONNECTED) }
        }
    }

    /** Host: withdraws a pending request. */
    suspend fun cancel(playerId: String, requestId: String) {
        connections[playerId]?.trySend(LanMessage.Cancel(requestId))
        updateEntry(playerId, requestId) { if (it.state == LanRollState.REQUESTED) it.copy(state = LanRollState.CANCELLED) else it }
    }

    private fun onResponse(player: Participant, response: ActionResponse) {
        val outcome = codec.decode(DiceRollAction, response)
        updateEntry(player.id, response.requestId) { entry ->
            if (entry.state != LanRollState.REQUESTED) return@updateEntry entry
            when (outcome) {
                is ActionOutcome.Completed -> entry.copy(state = LanRollState.ROLLED, outcome = outcome.result.outcome)
                is ActionOutcome.Failed -> entry.copy(
                    state = if (outcome.status == ActionStatus.DECLINED) LanRollState.DECLINED else LanRollState.FAILED,
                    errorCode = outcome.errorCode,
                )
            }
        }
    }

    private fun connectedPlayers() = _state.value.players.filter { it.connected }.map { it.participant }

    private suspend fun broadcastPlayers() {
        val message = LanMessage.Players(connectedPlayers())
        connections.values.forEach { it.trySend(message) }
    }

    // --- Player --------------------------------------------------------------------------------

    /** Joins a host typed by hand (e.g. its VPN address). */
    suspend fun joinAddress(address: LanAddress) {
        val host = transportProvider().addressHost(address)
        if (host == null) {
            _state.update { it.copy(error = LanError.CONNECT_FAILED) }
            return
        }
        join(host)
    }

    suspend fun join(host: LanHostInfo) = mutex.withLock {
        if (_state.value.role != LanRole.NONE) return@withLock
        val me = selfProvider()
        _state.update { it.copy(joining = true, error = null) }
        val connection = try {
            transportProvider().connect(host)
        } catch (e: IOException) {
            log("Can't connect", e)
            _state.update { it.copy(joining = false, error = LanError.CONNECT_FAILED) }
            return@withLock
        }
        hostConnection = connection
        lastSeen[connection] = now()
        _state.update { it.copy(me = me) }
        jobs += scope.launch { play(connection, me) }
        jobs += scope.launch { heartbeat() }
        if (!connection.trySend(LanMessage.Hello(me))) {
            _state.update { it.copy(joining = false, error = LanError.CONNECT_FAILED) }
        }
    }

    private suspend fun play(connection: LanConnection, me: Participant) {
        var ended: LanError? = LanError.HOST_LOST
        try {
            connection.incoming.collect { line ->
                val message = LanProtocol.decode(line) ?: return@collect
                lastSeen[connection] = now()
                when (message) {
                    is LanMessage.Welcome -> _state.update {
                        it.copy(role = LanRole.PLAYER, host = message.host, joining = false, error = null)
                    }
                    LanMessage.Ping -> connection.trySend(LanMessage.Pong)
                    is LanMessage.Request -> onRequest(message.request, me)
                    is LanMessage.Cancel -> {
                        _state.update { state ->
                            state.copy(incoming = state.incoming.takeUnless { it?.id == message.requestId })
                        }
                        updateEntry(me.id, message.requestId) {
                            if (it.state == LanRollState.REQUESTED) it.copy(state = LanRollState.CANCELLED) else it
                        }
                    }
                    is LanMessage.Bye -> {
                        ended = when (message.reason) {
                            ByeReason.KICKED -> LanError.KICKED
                            ByeReason.VERSION_MISMATCH -> LanError.VERSION_MISMATCH
                            ByeReason.HOST_STOPPED, ByeReason.LEFT -> LanError.HOST_STOPPED
                        }
                        connection.close()
                    }
                    else -> Unit
                }
            }
        } catch (e: CancellationException) {
            ended = null
            throw e
        } catch (e: IOException) {
            log("Host connection ended", e)
        } finally {
            lastSeen.remove(connection)
            if (hostConnection === connection) reset(ended)
        }
    }

    private fun onRequest(request: ActionRequest, me: Participant) {
        if (request.type != DiceRollAction.type || request.to?.id != me.id) return
        val payload = try {
            codec.decodePayload(DiceRollAction, request)
        } catch (_: IllegalArgumentException) {
            return
        } catch (_: kotlinx.serialization.SerializationException) {
            return
        }
        _state.update { it.copy(incoming = request) }
        addEntry(me.id, LanRollEntry(request.id, me, payload.notation, payload.pool, LanRollState.REQUESTED, request.createdAt))
    }

    /**
     * Player: rolls the pending request on this device (through the registry, like a local roll) and sends
     * the result to the host. Returns the outcome for the dice screen, or `null` when nothing is pending.
     */
    override suspend fun respond(requestId: String): Pair<ActionRequest, ActionOutcome<DiceRollResult>>? {
        val state = _state.value
        val me = state.me ?: return null
        val request = state.incoming?.takeIf { it.id == requestId } ?: return null
        _state.update { it.copy(incoming = null) }
        val response = registry.dispatch(request, responder = me)
        hostConnection?.trySend(LanMessage.Response(response))
        val outcome = codec.decode(DiceRollAction, response)
        updateEntry(me.id, requestId) { entry ->
            when (outcome) {
                is ActionOutcome.Completed -> entry.copy(state = LanRollState.ROLLED, outcome = outcome.result.outcome)
                is ActionOutcome.Failed -> entry.copy(state = LanRollState.FAILED, errorCode = outcome.errorCode)
            }
        }
        return request to outcome
    }

    override fun payloadOf(request: ActionRequest): DiceRollPayload? = try {
        codec.decodePayload(DiceRollAction, request)
    } catch (_: IllegalArgumentException) {
        null
    } catch (_: kotlinx.serialization.SerializationException) {
        null
    }

    /** Player: refuses the pending request. */
    override suspend fun decline(requestId: String) {
        val state = _state.value
        val me = state.me ?: return
        if (state.incoming?.id != requestId) return
        _state.update { it.copy(incoming = null) }
        hostConnection?.trySend(
            LanMessage.Response(ActionResponse(requestId, ActionStatus.DECLINED, respondedBy = me, respondedAt = wallTime())),
        )
        updateEntry(me.id, requestId) { it.copy(state = LanRollState.DECLINED) }
    }

    // --- Both ----------------------------------------------------------------------------------

    /** Leaves the table, or closes it when hosting; the session's logs are cleared. */
    suspend fun leave() = mutex.withLock {
        when (_state.value.role) {
            LanRole.HOST -> {
                connections.values.forEach {
                    it.trySend(LanMessage.Bye(ByeReason.HOST_STOPPED))
                    it.close()
                }
                server?.stop()
            }
            else -> hostConnection?.let {
                it.trySend(LanMessage.Bye(ByeReason.LEFT))
                it.close()
            }
        }
        reset(null)
    }

    fun clearError() = _state.update { it.copy(error = null) }

    private fun reset(error: LanError?) {
        jobs.forEach { it.cancel() }
        jobs.clear()
        connections.clear()
        lastSeen.clear()
        server = null
        hostConnection = null
        _state.value = LanState(error = error)
    }

    /** Pings everyone every [PING_MS]; a connection silent for [TIMEOUT_MS] is closed (and treated as gone). */
    private suspend fun heartbeat() {
        while (true) {
            delay(PING_MS)
            val time = now()
            lastSeen.entries.toList().forEach { (connection, seen) ->
                if (time - seen > TIMEOUT_MS) {
                    lastSeen.remove(connection)
                    connection.close()
                } else {
                    connection.trySend(LanMessage.Ping)
                }
            }
        }
    }

    private fun addEntry(playerId: String, entry: LanRollEntry) =
        _state.update { it.copy(logs = it.logs + (playerId to (listOf(entry) + it.log(playerId)).take(MAX_LOG))) }

    private fun updateEntry(playerId: String, requestId: String, change: (LanRollEntry) -> LanRollEntry) =
        _state.update { state ->
            state.copy(logs = state.logs.mapEntry(playerId) { entries -> entries.map { if (it.requestId == requestId) change(it) else it } })
        }

    private fun <V> Map<String, V>.mapEntry(key: String, change: (V) -> V): Map<String, V> =
        get(key)?.let { this + (key to change(it)) } ?: this

    /** Sends [message]; `false` when the connection is gone. */
    private suspend fun LanConnection.trySend(message: LanMessage): Boolean = try {
        send(LanProtocol.encode(message))
        true
    } catch (e: IOException) {
        log("Send failed", e)
        false
    }

    companion object {
        const val PING_MS = 15_000L
        const val TIMEOUT_MS = 45_000L
        const val MAX_LOG = 100
    }
}
