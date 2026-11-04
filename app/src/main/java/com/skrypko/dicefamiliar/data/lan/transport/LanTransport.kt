package com.skrypko.dicefamiliar.data.lan.transport

import kotlinx.coroutines.flow.Flow

/** A host found by [LanTransport.discoverHosts]; [id] is meaningful only to the transport that found it. */
data class LanHostInfo(val id: String, val name: String)

/**
 * How phones find and talk to each other. One implementation per technology (Wi-Fi NSD + TCP today;
 * Nearby Connections or a relay later). Everything above it ([com.skrypko.dicefamiliar.data.lan.LanSessionManager])
 * only sees text lines, so transports can be swapped per build or in Settings ([LanTransports]).
 */
interface LanTransport {
    /** Stable id stored in settings, e.g. `wifi_nsd`. */
    val id: String

    /** Hosts currently visible; the list updates while collected and discovery stops when collection ends. */
    fun discoverHosts(): Flow<List<LanHostInfo>>

    /** Starts accepting players and makes this device visible as [name]. */
    suspend fun startHosting(name: String): LanServer

    /** Opens a connection to [host]; throws [java.io.IOException] when it can't. */
    suspend fun connect(host: LanHostInfo): LanConnection

    /** A host typed by hand ("Join by address", e.g. over a VPN), or `null` when this transport has no addresses. */
    fun addressHost(address: com.skrypko.dicefamiliar.domain.lan.LanAddress): LanHostInfo? = null
}

interface LanServer {
    /** The port players connect to, when the transport has one (shown with the host's addresses). */
    val port: Int? get() = null

    /** Every accepted connection, as it arrives; collect once. */
    val connections: Flow<LanConnection>

    suspend fun stop()
}

/** A two-way line channel. */
interface LanConnection {
    /** Lines from the other side; completes when the connection closes. Collect once. */
    val incoming: Flow<String>

    /** Sends one line; throws [java.io.IOException] when the connection is gone. */
    suspend fun send(line: String)

    suspend fun close()
}

/** All transports this build ships, by id; the active one comes from settings. */
class LanTransports(transports: Set<LanTransport>) {
    val all: Map<String, LanTransport> = transports.associateBy { it.id }

    /** The transport with [id], or the default one when [id] is unknown. */
    fun get(id: String?): LanTransport = all[id] ?: all[DEFAULT_ID] ?: all.values.first()

    companion object {
        const val DEFAULT_ID = "wifi_nsd"
    }
}
