package io.github.mikhailbartashevich.roverkit.protocol

/**
 * The rover's address as the user types it: `192.168.50.5` or `10.0.2.2:5055`.
 */
data class RoverAddress(val host: String, val port: Int = DEFAULT_PORT) {
    val baseUrl: String get() = "http://$host:$port"
    /**
     * The camera as H.264 over RTSP from the rover's mediamtx (`rtsp://<ip>:8554/cam`). The RTSP
     * port sits [RTSP_PORT_OFFSET] above the web port, so a mock rover on :5055 streams on :8609.
     */
    val rtspUrl: String get() = "rtsp://$hostForUrl:${port + RTSP_PORT_OFFSET}/cam"
    /** A photo taken on the rover (served from /media/pictures/ since the refactor branch). */
    fun pictureUrl(name: String): String = "$baseUrl/media/pictures/$name"
    private val hostForUrl: String get() = if (':' in host) "[$host]" else host
    val sendCommandUrl: String get() = "$baseUrl/send_command"

    /** How it's shown and saved: the port only when it isn't the default. */
    override fun toString(): String = if (port == DEFAULT_PORT) host else "$host:$port"

    companion object {
        const val DEFAULT_PORT = 5000
        const val RTSP_PORT_OFFSET = 8554 - 5000
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
