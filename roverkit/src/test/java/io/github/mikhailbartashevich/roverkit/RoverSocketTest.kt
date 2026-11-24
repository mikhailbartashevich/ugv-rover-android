package io.github.mikhailbartashevich.roverkit

import io.github.mikhailbartashevich.roverkit.net.RoverSocket
import io.github.mikhailbartashevich.roverkit.protocol.Esp32Command
import io.github.mikhailbartashevich.roverkit.protocol.RoverAddress
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/** Runs the real client against a scripted Socket.IO server. */
class RoverSocketTest {
    private val server = MockWebServer()
    private val received = LinkedBlockingQueue<String>()

    @Before fun setUp() {
        server.enqueue(MockResponse.Builder().webSocketUpgrade(object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                webSocket.send("""0{"sid":"s","upgrades":[],"pingInterval":25000,"pingTimeout":20000}""")
            }
            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(1000, null)
            }
            override fun onMessage(webSocket: WebSocket, text: String) {
                received += text
                when (text) {
                    "40/ctrl," -> {
                        webSocket.send("""40/ctrl,{"sid":"c"}""")
                        webSocket.send("2")
                        webSocket.send("""42/ctrl,["update",{"112":0,"106":99}]""")
                        webSocket.send("""42/ctrl,["update",{"112":1150,"106":12.5}]""")
                    }
                    "40/json," -> webSocket.send("""40/json,{"sid":"j"}""")
                }
            }
        }).build())
        server.start()
    }

    @After fun tearDown() { runCatching { server.close() } }

    private fun next() = received.poll(3, TimeUnit.SECONDS)

    private fun waitFor(cond: () -> Boolean) {
        val end = System.currentTimeMillis() + 3000
        while (!cond() && System.currentTimeMillis() < end) Thread.sleep(10)
        assertTrue(cond())
    }

    @Test fun handshakeTelemetryAndEmit() {
        val socket = RoverSocket()
        socket.connect(RoverAddress(server.hostName, server.port))
        assertEquals("40/ctrl,", next())
        assertEquals("40/json,", next())
        assertEquals("3", next())
        waitFor { socket.canSendJson && socket.telemetry.value.voltage != null }
        assertEquals(RoverSocket.State.CONNECTED, socket.state.value)
        assertEquals(11.5, socket.telemetry.value.voltage!!, 1e-9)
        assertEquals(12.5, socket.telemetry.value.cpu!!, 1e-9) // the 112:0 update was ignored

        assertTrue(socket.emit(Esp32Command.velocity(0.3, 0.0)))
        assertEquals("""42/json,["json",{"T":13,"X":0.30,"Z":0}]""", next())
        assertTrue(socket.emitCtrl(10406))
        assertEquals("""42/ctrl,["message","{\"A\":10406,\"B\":0,\"C\":0}"]""", next())
        socket.disconnect()
        assertEquals(RoverSocket.State.DISCONNECTED, socket.state.value)
    }
}
