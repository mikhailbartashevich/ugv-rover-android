package io.github.mikhailbartashevich.roverkit

import io.github.mikhailbartashevich.roverkit.control.DriveMath
import io.github.mikhailbartashevich.roverkit.protocol.SpeedPreset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DriveMathTest {
    @Test fun deadZoneAndRim() {
        assertEquals(0.0, DriveMath.shape(0.05), 0.0)
        assertEquals(1.0, DriveMath.shape(1.0), 1e-9)
        assertEquals(-1.0, DriveMath.shape(-1.5), 1e-9)
        assertTrue(DriveMath.shape(0.5) < 0.5) // expo: gentler in the middle
    }

    @Test fun fullForwardAtEachPreset() {
        assertEquals("""{"T":13,"X":1.30,"Z":0}""", DriveMath.velocity(0.0, 1.0, SpeedPreset.FAST.rate).json)
        assertEquals("""{"T":13,"X":0.86,"Z":0}""", DriveMath.velocity(0.0, 1.0, SpeedPreset.MIDDLE.rate).json)
        assertEquals("""{"T":13,"X":0.39,"Z":0}""", DriveMath.velocity(0.0, 1.0, SpeedPreset.SLOW.rate).json)
    }

    @Test fun stickRightTurnsRightWhichIsNegativeZ() {
        assertEquals("""{"T":13,"X":0,"Z":-2.50}""", DriveMath.velocity(1.0, 0.0, 1.0).json)
        assertEquals("""{"T":13,"X":-1.30,"Z":0}""", DriveMath.velocity(0.0, -1.0, 1.0).json)
    }

    @Test fun stickIsClampedToCircle() {
        val (x, y) = DriveMath.clampToCircle(3.0, 4.0)
        assertEquals(0.6, x, 1e-9)
        assertEquals(0.8, y, 1e-9)
    }

    @Test fun gimbalStepIsRateBasedAndClamped() {
        val (p, t) = DriveMath.gimbalStep(0.0, 0.0, 1.0, 1.0, 0.1)
        assertEquals(6.0, p, 1e-9)
        assertEquals(4.0, t, 1e-9)
        val (p2, t2) = DriveMath.gimbalStep(178.0, -29.0, 1.0, -1.0, 1.0)
        assertEquals(180.0, p2, 1e-9)
        assertEquals(-30.0, t2, 1e-9)
    }
}
