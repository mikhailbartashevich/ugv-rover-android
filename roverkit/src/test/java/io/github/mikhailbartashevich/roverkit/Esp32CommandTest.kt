package io.github.mikhailbartashevich.roverkit

import io.github.mikhailbartashevich.roverkit.net.RoverHttp
import io.github.mikhailbartashevich.roverkit.protocol.Esp32Command
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class Esp32CommandTest {
    @Test fun wheelsEncodesTFirstWithShortDecimals() {
        assertEquals("""{"T":1,"L":0.30,"R":-0.30}""", Esp32Command.wheels(0.3, -0.3).json)
    }

    @Test fun integralValuesHaveNoDecimals() {
        assertEquals("""{"T":1,"L":0,"R":0}""", Esp32Command.STOP.json)
        assertEquals("""{"T":13,"X":1,"Z":-2}""", Esp32Command.velocity(1.0, -2.0).json)
    }

    @Test fun velocityRoundsToTwoDecimals() {
        assertEquals("""{"T":13,"X":0.86,"Z":0.33}""", Esp32Command.velocity(0.858, 0.333333).json)
    }

    @Test fun negativeZeroAfterRoundingIsZero() {
        assertEquals("""{"T":13,"X":0.00,"Z":0.50}""", Esp32Command.velocity(-0.001, 0.5).json)
    }

    @Test fun gimbalHasSpeedAndAcceleration() {
        assertEquals("""{"T":133,"X":-45.50,"Y":30,"SPD":0,"ACC":128}""", Esp32Command.gimbal(-45.5, 30.0).json)
    }

    @Test fun encodingIgnoresDeviceLocale() {
        val saved = Locale.getDefault()
        try {
            Locale.setDefault(Locale.GERMANY)
            assertEquals("""{"T":1,"L":0.25,"R":0.25}""", Esp32Command.wheels(0.25, 0.25).json)
        } finally {
            Locale.setDefault(saved)
        }
    }

    @Test fun stringsAreEscaped() {
        val c = Esp32Command(999, listOf("s" to Esp32Command.Value.StringV("a\"b\\c")))
        assertEquals("""{"T":999,"s":"a\"b\\c"}""", c.json)
    }

    @Test fun motionDetection() {
        assertTrue(Esp32Command.velocity(0.3, 0.0).isMotion)
        assertTrue(Esp32Command.wheels(0.0, -0.2).isMotion)
        assertFalse(Esp32Command.STOP.isMotion)
        assertFalse(Esp32Command.velocity(0.0005, 0.0).isMotion)
        assertFalse(Esp32Command.gimbal(10.0, 10.0).isMotion)
    }

    @Test fun httpCommandLines() {
        assertEquals("""base -c {"T":1,"L":0,"R":0}""", RoverHttp.baseCommandLine(Esp32Command.STOP))
    }
}
