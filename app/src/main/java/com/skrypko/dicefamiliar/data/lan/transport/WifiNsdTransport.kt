package com.skrypko.dicefamiliar.data.lan.transport

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.util.Log
import com.skrypko.dicefamiliar.domain.lan.LanAddress
import com.skrypko.dicefamiliar.domain.lan.LanProtocol
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.BufferedReader
import java.io.IOException
import java.io.Writer
import java.net.BindException
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Same Wi-Fi network (or a phone's hotspot, or a VPN by typed address): hosts are advertised with Network Service
 * Discovery (`_dicefamiliar._tcp`, the display name in the TXT record) and players connect over TCP, one message
 * per line. Hosts listen on [LanAddress.DEFAULT_PORT] when it is free, so "Join by address" needs only the IP.
 */
@Singleton
class WifiNsdTransport @Inject constructor(@ApplicationContext context: Context) : LanTransport {
    override val id = LanTransports.DEFAULT_ID

    private val nsd = context.getSystemService(NsdManager::class.java)
    private val wifi = context.applicationContext.getSystemService(WifiManager::class.java)
    private val resolveMutex = Mutex()

    override fun discoverHosts(): Flow<List<LanHostInfo>> = callbackFlow {
        val lock = wifi?.createMulticastLock("dicefamiliar")?.apply {
            setReferenceCounted(false)
            acquire()
        }
        val found = ConcurrentHashMap<String, LanHostInfo>()
        fun publish() = trySend(found.values.sortedBy { it.name.lowercase() })
        val listener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) = Unit
            override fun onDiscoveryStopped(serviceType: String) = Unit
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                close(IOException("Discovery failed: $errorCode"))
            }
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) = Unit
            override fun onServiceFound(info: NsdServiceInfo) {
                launch {
                    resolve(info)?.let {
                        found[info.serviceName] = it
                        publish()
                    }
                }
            }
            override fun onServiceLost(info: NsdServiceInfo) {
                found.remove(info.serviceName)
                publish()
            }
        }
        publish()
        nsd.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, listener)
        awaitClose {
            try {
                nsd.stopServiceDiscovery(listener)
            } catch (_: IllegalArgumentException) {
                // Already stopped.
            }
            lock?.release()
        }
    }

    /** NSD resolves one service at a time. */
    @Suppress("DEPRECATION") // resolveService: its replacement needs API 34; this works on every version.
    private suspend fun resolve(info: NsdServiceInfo): LanHostInfo? = resolveMutex.withLock {
        suspendCancellableCoroutine { continuation ->
            nsd.resolveService(info, object : NsdManager.ResolveListener {
                override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                    if (continuation.isActive) continuation.resume(null)
                }

                override fun onServiceResolved(resolved: NsdServiceInfo) {
                    val host = resolved.host?.hostAddress
                    val name = resolved.attributes[TXT_NAME]?.decodeToString()?.takeIf { it.isNotBlank() } ?: resolved.serviceName
                    val result = host?.let { LanHostInfo(addressId(LanAddress(it.substringBefore('%'), resolved.port)), name) }
                    if (continuation.isActive) continuation.resume(result)
                }
            })
        }
    }

    override suspend fun startHosting(name: String): LanServer = withContext(Dispatchers.IO) {
        val socket = try {
            ServerSocket(LanAddress.DEFAULT_PORT)
        } catch (_: BindException) {
            ServerSocket(0)
        }
        val info = NsdServiceInfo().apply {
            serviceName = name.take(MAX_SERVICE_NAME).ifBlank { "Dice Familiar" }
            serviceType = SERVICE_TYPE
            port = socket.localPort
            setAttribute(TXT_NAME, name.take(MAX_SERVICE_NAME))
        }
        val registration = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(serviceInfo: NsdServiceInfo) = Unit
            override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) = Log.w(TAG, "NSD registration failed: $errorCode").let {}
            override fun onServiceUnregistered(serviceInfo: NsdServiceInfo) = Unit
            override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) = Unit
        }
        nsd.registerService(info, NsdManager.PROTOCOL_DNS_SD, registration)
        SocketServer(socket) {
            try {
                nsd.unregisterService(registration)
            } catch (_: IllegalArgumentException) {
                // Never registered.
            }
        }
    }

    override suspend fun connect(host: LanHostInfo): LanConnection = withContext(Dispatchers.IO) {
        val address = parseAddressId(host.id) ?: throw IOException("Unknown host ${host.id}")
        val socket = Socket()
        try {
            socket.connect(InetSocketAddress(address.host, address.port), CONNECT_TIMEOUT_MS)
        } catch (e: IOException) {
            socket.close()
            throw e
        } catch (e: IllegalArgumentException) {
            socket.close()
            throw IOException(e)
        }
        SocketConnection(socket)
    }

    override fun addressHost(address: LanAddress): LanHostInfo = LanHostInfo(addressId(address), address.toString())

    private class SocketServer(private val socket: ServerSocket, private val onStop: () -> Unit) : LanServer {
        override val port: Int get() = socket.localPort

        override val connections: Flow<LanConnection> = flow {
            while (!socket.isClosed) {
                val client = try {
                    socket.accept()
                } catch (_: IOException) {
                    break
                }
                emit(SocketConnection(client))
            }
        }.flowOn(Dispatchers.IO)

        override suspend fun stop() = withContext(Dispatchers.IO) {
            onStop()
            socket.close()
        }
    }

    /** One TCP socket carrying UTF-8 lines. */
    private class SocketConnection(private val socket: Socket) : LanConnection {
        private val writeMutex = Mutex()
        private val writer: Writer = socket.getOutputStream().bufferedWriter(Charsets.UTF_8)

        init {
            socket.tcpNoDelay = true
            socket.keepAlive = true
        }

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
            writeMutex.withLock {
                writer.write(line)
                writer.write("\n")
                writer.flush()
            }
        }

        override suspend fun close() = withContext(Dispatchers.IO) {
            try {
                socket.close()
            } catch (_: IOException) {
                // Already closed.
            }
        }
    }

    companion object {
        const val SERVICE_TYPE = "_dicefamiliar._tcp"
        private const val TXT_NAME = "name"
        private const val MAX_SERVICE_NAME = 40
        private const val CONNECT_TIMEOUT_MS = 5_000
        private const val TAG = "LanWifi"
        private const val ADDRESS_PREFIX = "addr:"

        fun addressId(address: LanAddress) = ADDRESS_PREFIX + address

        fun parseAddressId(id: String): LanAddress? = id.removePrefix(ADDRESS_PREFIX).takeIf { id.startsWith(ADDRESS_PREFIX) }?.let(LanAddress::parse)

        /** This device's IPv4 addresses that others can type, labelled Wi-Fi or VPN (tun/wg/ppp/ipsec interfaces). */
        fun localAddresses(port: Int): List<LocalAddress> = try {
            NetworkInterface.getNetworkInterfaces().toList()
                .filter { it.isUp && !it.isLoopback }
                .flatMap { nic ->
                    nic.inetAddresses.toList().filterIsInstance<Inet4Address>().map { address ->
                        val vpn = VPN_PREFIXES.any { nic.name.startsWith(it) } || nic.isPointToPoint
                        LocalAddress(LanAddress(address.hostAddress.orEmpty(), port), vpn)
                    }
                }
                .sortedBy { it.vpn }
        } catch (_: IOException) {
            emptyList()
        }

        private val VPN_PREFIXES = listOf("tun", "wg", "ppp", "ipsec", "tailscale", "zt")
    }
}

/** An address of this device: [vpn] for VPN interfaces (Tailscale, ZeroTier, WireGuard…). */
data class LocalAddress(val address: LanAddress, val vpn: Boolean)

/** Reads one line (without the break); a longer line than [max] is skipped and returned empty. `null` at the end. */
internal fun BufferedReader.readCappedLine(max: Int): String? {
    val line = StringBuilder()
    var tooLong = false
    while (true) {
        val c = read()
        if (c < 0) return if (line.isEmpty() && !tooLong) null else if (tooLong) "" else line.toString()
        if (c == '\n'.code) return if (tooLong) "" else line.toString().trimEnd('\r')
        if (!tooLong) {
            if (line.length >= max) {
                tooLong = true
                line.setLength(0)
            } else {
                line.append(c.toChar())
            }
        }
    }
}
