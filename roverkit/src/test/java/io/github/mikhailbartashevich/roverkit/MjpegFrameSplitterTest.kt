package io.github.mikhailbartashevich.roverkit

import io.github.mikhailbartashevich.roverkit.net.MjpegFrameSplitter
import io.github.mikhailbartashevich.roverkit.net.RoverScanner
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MjpegFrameSplitterTest {
    private fun jpeg(vararg body: Int) = (listOf(0xFF, 0xD8) + body.toList() + listOf(0xFF, 0xD9)).map { it.toByte() }.toByteArray()

    @Test fun splitsFramesAcrossChunksAndHeaders() {
        val f1 = jpeg(1, 2, 3)
        val f2 = jpeg(4, 0xFF, 0x00, 5)
        val header = "--frame\r\nContent-Type: image/jpeg\r\n\r\n".toByteArray()
        val stream = header + f1 + "\r\n".toByteArray() + header + f2 + "\r\n".toByteArray()
        val out = mutableListOf<ByteArray>()
        val s = MjpegFrameSplitter()
        // Feed one byte at a time so markers straddle chunk boundaries.
        for (b in stream) s.feed(byteArrayOf(b)) { out += it }
        assertEquals(2, out.size)
        assertArrayEquals(f1, out[0])
        assertArrayEquals(f2, out[1])
    }

    @Test fun scannerHelpers() {
        assertEquals("192.168.1", RoverScanner.prefix24("192.168.1.37"))
        assertNull(RoverScanner.prefix24("fe80::1"))
        assertNull(RoverScanner.prefix24("300.1.1.1"))
        assertTrue(RoverScanner.looksLikeRover("<html><script src=\"socket.io.js\"></script>"))
        assertFalse(RoverScanner.looksLikeRover("<html>router admin</html>"))
    }
}
