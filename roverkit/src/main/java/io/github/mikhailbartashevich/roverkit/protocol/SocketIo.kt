package io.github.mikhailbartashevich.roverkit.protocol

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive

/**
 * Text frames of Socket.IO v5 over Engine.IO v4, limited to what the rover's
 * Flask-SocketIO server uses: namespaces `/ctrl` (modes, telemetry) and `/json`
 * (ESP32 commands), ping/pong and events.
 */
object SocketIo {
    const val NS_CTRL = "/ctrl"
    const val NS_JSON = "/json"

    const val PONG = "3"
    val JOIN_CTRL = "40$NS_CTRL,"
    val JOIN_JSON = "40$NS_JSON,"

    /** `/socket.io/?EIO=4&transport=websocket` on the rover's web port. */
    fun webSocketUrl(address: RoverAddress): String =
        "ws://${address.host}:${address.port}/socket.io/?EIO=4&transport=websocket"

    /** ESP32 command, like the web UI's `socketJson.emit('json', …)`. */
    fun jsonEvent(command: Esp32Command): String = "42$NS_JSON,[\"json\",${command.json}]"

    /** Mode/action command, same as the web UI's `cmdSend(cmd, mode)`: the payload is a JSON *string*. */
    fun ctrlEvent(command: CtrlCommand): String =
        "42$NS_CTRL," + JsonArray(listOf(JsonPrimitive("message"), JsonPrimitive(command.json))).toString()

    /** What an incoming frame means for the client. */
    sealed interface Incoming {
        /** Engine.IO open (`0{…}`) — join the namespaces. */
        data object Open : Incoming
        data object Ping : Incoming
        data class Joined(val namespace: String) : Incoming
        data class Update(val fields: Map<String, JsonPrimitive>) : Incoming
        /** Engine.IO close or namespace disconnect — reconnect. */
        data object Closed : Incoming
        data object Ignored : Incoming
    }

    fun parse(frame: String): Incoming = when {
        frame.startsWith("0{") -> Incoming.Open
        frame == "2" -> Incoming.Ping
        frame.startsWith("40$NS_CTRL") -> Incoming.Joined(NS_CTRL)
        frame.startsWith("40$NS_JSON") -> Incoming.Joined(NS_JSON)
        frame.startsWith("42$NS_CTRL,") -> parseEvent(frame.removePrefix("42$NS_CTRL,"))
        frame.startsWith("1") || frame.startsWith("41$NS_CTRL") -> Incoming.Closed
        else -> Incoming.Ignored
    }

    private fun parseEvent(body: String): Incoming {
        val arr = runCatching { Json.parseToJsonElement(body) as? JsonArray }.getOrNull() ?: return Incoming.Ignored
        val name = runCatching { arr.firstOrNull()?.jsonPrimitive?.content }.getOrNull()
        val obj = arr.getOrNull(1) as? JsonObject
        if (name != "update" || obj == null) return Incoming.Ignored
        return Incoming.Update(obj.mapNotNull { (k, v) -> (v as? JsonPrimitive)?.let { k to it } }.toMap())
    }
}
