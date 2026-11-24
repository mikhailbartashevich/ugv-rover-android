package io.github.mikhailbartashevich.roverkit.protocol

/**
 * The rover's address as the user types it: `192.168.50.5` or `10.0.2.2:5055`.
 */
data class RoverAddress(val host: String, val port: Int = DEFAULT_PORT) {
    val baseUrl: String get() = "http://$host:$port"
    val videoUrl: String get() = "$baseUrl/video_feed"
    val sendCommandUrl: String get() = "$baseUrl/send_command"

    /** How it's shown and saved: the port only when it isn't the default. */
    override fun toString(): String = if (port == DEFAULT_PORT) host else "$host:$port"

    companion object {
        const val DEFAULT_PORT = 5000
        /** The rover's own hotspot address (AccessPopup). */
        const val HOTSPOT_HOST = "192.168.50.5"

        /** Parses `host`, `host:port` or a pasted `http://host:port/…` URL; null if unusable. */
        fun parse(text: String): RoverAddress? {
            var s = text.trim()
            s = s.substringAfter("://")
            s = s.substringBefore('/')
            if (s.isEmpty() || s.any { it.isWhitespace() }) return null
            val colon = s.lastIndexOf(':')
            if (colon < 0) return RoverAddress(s)
            val host = s.substring(0, colon)
            val port = s.substring(colon + 1).toIntOrNull() ?: return null
            if (host.isEmpty() || port !in 1..65535) return null
            return RoverAddress(host, port)
        }
    }
}
