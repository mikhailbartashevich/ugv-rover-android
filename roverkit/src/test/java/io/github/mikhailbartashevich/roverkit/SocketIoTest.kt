package io.github.mikhailbartashevich.roverkit

import io.github.mikhailbartashevich.roverkit.protocol.CameraZoom
import io.github.mikhailbartashevich.roverkit.protocol.Esp32Command
import io.github.mikhailbartashevich.roverkit.protocol.HeadLight
import io.github.mikhailbartashevich.roverkit.protocol.RoverAddress
import io.github.mikhailbartashevich.roverkit.protocol.CtrlCommand
import io.github.mikhailbartashevich.roverkit.protocol.SocketIo
import io.github.mikhailbartashevich.roverkit.protocol.SocketIo.Incoming
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SocketIoTest {
    @Test fun jsonEventGoesToJsonNamespace() {
        assertEquals(
            """42/json,["json",{"T":1,"L":0.30,"R":0.30}]""",
            SocketIo.jsonEvent(Esp32Command.wheels(0.3, 0.3)),
        )
    }

    @Test fun ctrlEventWrapsAStringPayload() {
        assertEquals(
            """42/ctrl,["message","{\"cmd\":10201,\"mode\":0}"]""",
            SocketIo.ctrlEvent(CtrlCommand.CAPTURE_PHOTO),
        )
    }

    /** Same shapes as the web UI's cmdSend on the ugv_rpi refactor branch (and the macOS app). */
    @Test fun ctrlCommandShapes() {
        assertEquals("""{"cmd":10201,"mode":0}""", CtrlCommand.CAPTURE_PHOTO.json)
        assertEquals("""{"cmd":10203,"mode":1}""", CtrlCommand.recording(true).json)
        assertEquals("""{"cmd":10203,"mode":0}""", CtrlCommand.recording(false).json)
        assertEquals(listOf(1, 2, 4), CameraZoom.entries.map { CtrlCommand.zoom(it).mode })
        assertEquals("""{"cmd":10104,"mode":4}""", CtrlCommand.zoom(CameraZoom.X4).json)
        assertEquals(
            listOf("""{"cmd":102,"mode":10404}""", """{"cmd":102,"mode":10405}""", """{"cmd":102,"mode":10406}"""),
            HeadLight.entries.map { CtrlCommand.headLight(it).json },
        )
        assertEquals("""{"cmd":115,"mode":255}""", CtrlCommand.chassisLight(255).json)
        assertEquals("""{"cmd":115,"mode":0}""", CtrlCommand.chassisLight(-3).json)
        assertEquals("""{"cmd":114,"mode":10501}""", CtrlCommand.movementLocked(true).json)
        assertEquals("""{"cmd":114,"mode":10502}""", CtrlCommand.movementLocked(false).json)
        assertEquals(CameraZoom.X1, CameraZoom.X4.next)
        assertEquals(HeadLight.OFF, HeadLight.ON.next)
    }

    @Test fun handshakeFrames() {
        assertEquals(Incoming.Open, SocketIo.parse("""0{"sid":"abc","upgrades":[],"pingInterval":25000,"pingTimeout":20000}"""))
        assertEquals(Incoming.Ping, SocketIo.parse("2"))
        assertEquals(Incoming.Joined("/ctrl"), SocketIo.parse("""40/ctrl,{"sid":"x"}"""))
        assertEquals(Incoming.Joined("/json"), SocketIo.parse("""40/json,{"sid":"y"}"""))
        assertEquals(Incoming.Closed, SocketIo.parse("1"))
        assertEquals(Incoming.Closed, SocketIo.parse("41/ctrl,"))
        assertEquals("40/ctrl,", SocketIo.JOIN_CTRL)
        assertEquals("40/json,", SocketIo.JOIN_JSON)
        assertEquals("3", SocketIo.PONG)
    }

    @Test fun updateEventIsParsed() {
        val m = SocketIo.parse("""42/ctrl,["update",{"106":12.5,"112":1180,"102":2}]""")
        assertTrue(m is Incoming.Update)
        m as Incoming.Update
        assertEquals("1180", m.fields["112"]?.content)
    }

    @Test fun otherEventsAndGarbageAreIgnored() {
        assertEquals(Incoming.Ignored, SocketIo.parse("""42/ctrl,["other",{"a":1}]"""))
        assertEquals(Incoming.Ignored, SocketIo.parse("""42/ctrl,not json"""))
        assertEquals(Incoming.Ignored, SocketIo.parse("""42/json,["json",{}]"""))
    }

    @Test fun webSocketUrl() {
        assertEquals(
            "ws://10.0.2.2:5055/socket.io/?EIO=4&transport=websocket",
            SocketIo.webSocketUrl(RoverAddress("10.0.2.2", 5055)),
        )
    }

    @Test fun addressParsing() {
        assertEquals(RoverAddress("192.168.50.5", 5000), RoverAddress.parse(" 192.168.50.5 "))
        assertEquals(RoverAddress("10.0.2.2", 5055), RoverAddress.parse("10.0.2.2:5055"))
        assertEquals(RoverAddress("rover.local", 5000), RoverAddress.parse("http://rover.local:5000/video_feed"))
        assertNull(RoverAddress.parse(""))
        assertNull(RoverAddress.parse("host:notaport"))
        assertNull(RoverAddress.parse("a b"))
        assertEquals("10.0.2.2:5055", RoverAddress("10.0.2.2", 5055).toString())
        assertEquals("192.168.50.5", RoverAddress("192.168.50.5").toString())
        assertEquals("rtsp://192.168.1.158:8554/cam", RoverAddress("192.168.1.158").rtspUrl)
        assertEquals("rtsp://10.0.2.2:8609/cam", RoverAddress("10.0.2.2", 5055).rtspUrl)
        assertEquals("http://192.168.1.158:5000/media/pictures/a.jpg", RoverAddress("192.168.1.158").pictureUrl("a.jpg"))
    }
}
