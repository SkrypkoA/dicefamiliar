package com.skrypko.dicefamiliar.data.lan

import com.skrypko.dicefamiliar.data.lan.transport.LanConnection
import com.skrypko.dicefamiliar.data.lan.transport.LanHostInfo
import com.skrypko.dicefamiliar.data.lan.transport.LanServer
import com.skrypko.dicefamiliar.data.lan.transport.LanTransport
import com.skrypko.dicefamiliar.data.lan.transport.readCappedLine
import com.skrypko.dicefamiliar.domain.lan.LanAddress
import com.skrypko.dicefamiliar.domain.lan.LanProtocol
import java.io.IOException
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Plain TCP lines on the JVM (the Wi-Fi transport without NSD), for talking to the desktop tool in tests. */
class JvmTcpTransport : LanTransport {
    override val id = "jvm_tcp"

    override fun discoverHosts(): Flow<List<LanHostInfo>> = emptyFlow()

    override suspend fun startHosting(name: String): LanServer = withContext(Dispatchers.IO) {
        val socket = ServerSocket(0)
        object : LanServer {
            override val port: Int get() = socket.localPort
            override val connections: Flow<LanConnection> = flow {
                while (!socket.isClosed) {
                    val client = try {
                        socket.accept()
                    } catch (_: IOException) {
                        break
                    }
                    emit(TcpConnection(client))
                }
            }.flowOn(Dispatchers.IO)

            override suspend fun stop() = withContext(Dispatchers.IO) { socket.close() }
        }
    }

    override suspend fun connect(host: LanHostInfo): LanConnection = withContext(Dispatchers.IO) {
        val address = LanAddress.parse(host.id) ?: throw IOException("bad address")
        TcpConnection(Socket().apply { connect(InetSocketAddress(address.host, address.port), 5_000) })
    }

    override fun addressHost(address: LanAddress) = LanHostInfo(address.toString(), address.toString())

    private class TcpConnection(private val socket: Socket) : LanConnection {
        private val writer = socket.getOutputStream().bufferedWriter(Charsets.UTF_8)
        private val mutex = Mutex()

        override val incoming: Flow<String> = flow {
            val reader = socket.getInputStream().bufferedReader(Charsets.UTF_8)
            while (true) {
                val line = try {
                    reader.readCappedLine(LanProtocol.MAX_LINE)
                } catch (_: IOException) {
                    null
                } ?: break
                emit(line)
            }
        }.flowOn(Dispatchers.IO)

        override suspend fun send(line: String) = withContext(Dispatchers.IO) {
            mutex.withLock {
                writer.write(line)
                writer.write("\n")
                writer.flush()
            }
        }

        override suspend fun close() = withContext(Dispatchers.IO) {
            try {
                socket.close()
            } catch (_: IOException) {
            }
        }
    }
}
