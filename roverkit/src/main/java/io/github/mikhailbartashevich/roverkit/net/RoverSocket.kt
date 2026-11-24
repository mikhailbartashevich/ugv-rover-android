package io.github.mikhailbartashevich.roverkit.net

import io.github.mikhailbartashevich.roverkit.protocol.CtrlCommand
import io.github.mikhailbartashevich.roverkit.protocol.Esp32Command
import io.github.mikhailbartashevich.roverkit.protocol.RoverAddress
import io.github.mikhailbartashevich.roverkit.protocol.SocketIo
import io.github.mikhailbartashevich.roverkit.protocol.SocketIo.Incoming
import io.github.mikhailbartashevich.roverkit.protocol.Telemetry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

/**
 * Minimal Socket.IO client for the rover over an OkHttp websocket. Joins `/ctrl`
 * and `/json`, answers pings, publishes telemetry, reconnects on its own.
 * Mirrors `RoverSocket.swift`. Thread-safe: callers may emit from any thread.
 */
class RoverSocket(private val http: OkHttpClient = RoverHttp.defaultClient) {

    enum class State { DISCONNECTED, CONNECTING, CONNECTED }

    private val _state = MutableStateFlow(State.DISCONNECTED)
    val state: StateFlow<State> = _state.asStateFlow()

    private val _jsonReady = MutableStateFlow(false)
    /** The `/json` namespace is joined, so ESP32 commands can go over the socket. */
    val jsonReady: StateFlow<Boolean> = _jsonReady.asStateFlow()

    private val _telemetry = MutableStateFlow(Telemetry())
    val telemetry: StateFlow<Telemetry> = _telemetry.asStateFlow()

    private val _lastUpdateMs = MutableStateFlow<Long?>(null)
    val lastUpdateMs: StateFlow<Long?> = _lastUpdateMs.asStateFlow()

    private val lock = Any()
    private var socket: WebSocket? = null
    private var address: RoverAddress? = null
    private var generation = 0
    private var reconnect: ScheduledFuture<*>? = null
    private val scheduler = Executors.newSingleThreadScheduledExecutor { r ->
        Thread(r, "rover-socket-reconnect").apply { isDaemon = true }
    }

    val isConnected: Boolean get() = _state.value == State.CONNECTED
    val canSendJson: Boolean get() = isConnected && _jsonReady.value

    fun connect(to: RoverAddress) {
        disconnect()
        synchronized(lock) { address = to }
        _telemetry.value = Telemetry()
        _lastUpdateMs.value = null
        open()
    }

    fun disconnect() {
        synchronized(lock) {
            generation++
            reconnect?.cancel(false)
            reconnect = null
            socket?.close(1001, null)
            socket = null
        }
        _jsonReady.value = false
        _state.value = State.DISCONNECTED
    }

    /** Mode/action command, same as the web UI's `cmdSend(cmd, mode)`. */
    fun emitCtrl(command: CtrlCommand): Boolean {
        if (!isConnected) return false
        return sendRaw(SocketIo.ctrlEvent(command))
    }

    /** ESP32 command over the `/json` namespace. */
    fun emit(command: Esp32Command): Boolean {
        if (!canSendJson) return false
        return sendRaw(SocketIo.jsonEvent(command))
    }

    private fun open() {
        val (addr, gen) = synchronized(lock) { (address ?: return) to generation }
        _jsonReady.value = false
        _state.value = State.CONNECTING
        val request = Request.Builder().url(SocketIo.webSocketUrl(addr).replaceFirst("ws://", "http://")).build()
        val ws = http.newWebSocket(request, Listener(gen))
        synchronized(lock) {
            if (gen == generation) socket = ws else ws.cancel()
        }
    }

    private inner class Listener(private val gen: Int) : WebSocketListener() {
        private fun current() = synchronized(lock) { gen == generation }

        override fun onMessage(webSocket: WebSocket, text: String) {
            if (!current()) return
            handle(webSocket, text)
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            if (current()) scheduleReconnect()
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            if (current()) scheduleReconnect()
        }
    }

    private fun handle(ws: WebSocket, frame: String) {
        when (val msg = SocketIo.parse(frame)) {
            Incoming.Open -> {
                ws.send(SocketIo.JOIN_CTRL)
                ws.send(SocketIo.JOIN_JSON)
            }
            Incoming.Ping -> ws.send(SocketIo.PONG)
            is Incoming.Joined -> when (msg.namespace) {
                SocketIo.NS_CTRL -> _state.value = State.CONNECTED
                SocketIo.NS_JSON -> _jsonReady.value = true
            }
            is Incoming.Update -> {
                val next = _telemetry.value.merged(msg.fields) ?: return
                _telemetry.value = next
                _lastUpdateMs.value = System.currentTimeMillis()
            }
            Incoming.Closed -> scheduleReconnect()
            Incoming.Ignored -> Unit
        }
    }

    private fun scheduleReconnect() {
        _state.value = State.DISCONNECTED
        _jsonReady.value = false
        synchronized(lock) {
            socket?.cancel()
            socket = null
            val gen = generation
            reconnect?.cancel(false)
            reconnect = scheduler.schedule({
                if (synchronized(lock) { gen == generation }) open()
            }, RECONNECT_DELAY_MS, TimeUnit.MILLISECONDS)
        }
    }

    private fun sendRaw(s: String): Boolean = synchronized(lock) { socket?.send(s) ?: false }

    companion object {
        const val RECONNECT_DELAY_MS = 2000L
    }
}
