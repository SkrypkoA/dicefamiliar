package com.skrypko.dicefamiliar.data.lan

import com.skrypko.dicefamiliar.data.lan.transport.LanConnection
import com.skrypko.dicefamiliar.data.lan.transport.LanHostInfo
import com.skrypko.dicefamiliar.data.lan.transport.LanServer
import com.skrypko.dicefamiliar.data.lan.transport.LanTransport
import java.io.IOException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ClosedSendChannelException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.map

/** An in-memory "network" shared by several [FakeLanTransport]s, so a host and players can talk inside a test. */
class FakeNetwork {
    val hosts = MutableStateFlow<Map<String, FakeServer>>(emptyMap())
    val lines = mutableListOf<String>()
}

class FakeServer(val name: String) : LanServer {
    val accepted = Channel<LanConnection>(Channel.UNLIMITED)
    override val connections: Flow<LanConnection> = accepted.consumeAsFlow()
    var stopped = false
    override suspend fun stop() {
        stopped = true
        accepted.close()
    }
}

/** One end of a connection: lines sent here arrive on [peer]. */
class FakeConnection(private val network: FakeNetwork) : LanConnection {
    val inbox = Channel<String>(Channel.UNLIMITED)
    lateinit var peer: FakeConnection
    var closed = false
    override val incoming: Flow<String> = inbox.consumeAsFlow()

    override suspend fun send(line: String) {
        if (closed) throw IOException("closed")
        network.lines += line
        try {
            peer.inbox.send(line)
        } catch (e: ClosedSendChannelException) {
            throw IOException("peer closed", e)
        }
    }

    override suspend fun close() {
        if (closed) return
        closed = true
        inbox.close()
        peer.closed = true
        peer.inbox.close()
    }

    /** This side stops sending (Wi-Fi off): no goodbye, nothing completes; the other side only notices the silence. */
    fun goSilent() {
        closed = true
    }
}

class FakeLanTransport(private val network: FakeNetwork) : LanTransport {
    override val id = "fake"
    var failConnect = false

    /** This device's end of the last connection it opened. */
    var last: FakeConnection? = null

    override fun discoverHosts(): Flow<List<LanHostInfo>> = network.hosts.map { hosts -> hosts.map { (id, server) -> LanHostInfo(id, server.name) } }

    override suspend fun startHosting(name: String): LanServer =
        FakeServer(name).also { server -> network.hosts.value = network.hosts.value + ("host-$name" to server) }

    override suspend fun connect(host: LanHostInfo): LanConnection {
        if (failConnect) throw IOException("unreachable")
        val server = network.hosts.value[host.id] ?: throw IOException("no host")
        val mine = FakeConnection(network)
        val theirs = FakeConnection(network)
        mine.peer = theirs
        theirs.peer = mine
        server.accepted.send(theirs)
        last = mine
        return mine
    }
}
