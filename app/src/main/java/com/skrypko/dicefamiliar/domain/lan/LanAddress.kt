package com.skrypko.dicefamiliar.domain.lan

/** A host typed by hand ("Join by address"), e.g. `192.168.1.20`, `100.101.2.3:47321` or `[fd7a::1]:47321`. */
data class LanAddress(val host: String, val port: Int) {
    override fun toString(): String = if (':' in host) "[$host]:$port" else "$host:$port"

    companion object {
        /** The port a host listens on when it can (so typing the address alone is enough). */
        const val DEFAULT_PORT = 47321

        /** Parses what the user typed; `null` when it isn't an IPv4/IPv6 address or a host name with an optional port. */
        fun parse(text: String): LanAddress? {
            val input = text.trim()
            if (input.isEmpty() || input.any { it.isWhitespace() }) return null
            val (host, portText) = when {
                input.startsWith("[") -> {
                    val end = input.indexOf(']')
                    if (end < 0) return null
                    val rest = input.substring(end + 1)
                    if (rest.isNotEmpty() && !rest.startsWith(":")) return null
                    input.substring(1, end) to rest.removePrefix(":").ifEmpty { null }
                }
                input.count { it == ':' } > 1 -> input to null // bare IPv6
                ':' in input -> input.substringBefore(':') to input.substringAfter(':')
                else -> input to null
            }
            if (host.isEmpty() || !host.all { it.isLetterOrDigit() || it in ".-:%" }) return null
            val port = portText?.toIntOrNull() ?: if (portText == null) DEFAULT_PORT else return null
            if (port !in 1..65535) return null
            return LanAddress(host, port)
        }
    }
}
