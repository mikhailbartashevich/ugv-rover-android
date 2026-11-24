package io.github.mikhailbartashevich.roverkit

import io.github.mikhailbartashevich.roverkit.protocol.HeadLight
import io.github.mikhailbartashevich.roverkit.protocol.SocketIo
import io.github.mikhailbartashevich.roverkit.protocol.Telemetry
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TelemetryTest {
    private fun update(frame: String) = (SocketIo.parse(frame) as SocketIo.Incoming.Update).fields

    @Test fun parsesAllKeys() {
        val t = Telemetry().merged(update(
            """42/ctrl,["update",{"106":23.5,"107":51.2,"108":40,"111":-58,"112":1186,"113":24.9,"102":1,"115":1,"109":10,"110":-5}]""",
        ))!!
        assertEquals(23.5, t.cpu!!, 1e-9)
        assertEquals(51.2, t.temp!!, 1e-9)
        assertEquals(40.0, t.ram!!, 1e-9)
        assertEquals(-58.0, t.rssi!!, 1e-9)
        assertEquals(11.86, t.voltage!!, 1e-9)
        assertEquals(24.9, t.fps!!, 1e-9)
        assertEquals(HeadLight.AUTO, t.headLight)
        assertEquals(true, t.chassisLightOn)
        assertEquals(10.0, t.pan!!, 1e-9)
        assertEquals(-5.0, t.tilt!!, 1e-9)
    }

    /** The refactor-branch host sends floats (101/103 = 10303.0), led_mode 0/1/2 and base_light as PWM. */
    @Test fun refactorBranchFormats() {
        val t = Telemetry().merged(update(
            """42/ctrl,["update",{"101":10303.0,"103":10401.0,"102":2.0,"114":true,"115":128,"112":1150}]""",
        ))!!
        assertEquals(HeadLight.ON, t.headLight)
        assertEquals(true, t.chassisLightOn)
        val off = t.merged(update("""42/ctrl,["update",{"102":0,"115":0}]"""))!!
        assertEquals(HeadLight.OFF, off.headLight)
        assertEquals(false, off.chassisLightOn)
    }

    @Test fun zeroVoltageUpdatesAreIgnored() {
        assertNull(Telemetry().merged(update("""42/ctrl,["update",{"106":5,"112":0}]""")))
    }

    @Test fun partialUpdatesKeepPreviousValues() {
        val a = Telemetry().merged(update("""42/ctrl,["update",{"106":10,"112":1200}]"""))!!
        val b = a.merged(update("""42/ctrl,["update",{"107":45}]"""))!!
        assertEquals(10.0, b.cpu!!, 1e-9)
        assertEquals(12.0, b.voltage!!, 1e-9)
        assertEquals(45.0, b.temp!!, 1e-9)
    }

    @Test fun numericStringsAndBooleansAreAccepted() {
        val t = Telemetry().merged(mapOf("112" to JsonPrimitive("1110"), "115" to JsonPrimitive(false)))!!
        assertEquals(11.1, t.voltage!!, 1e-9)
        assertEquals(false, t.chassisLightOn)
    }

    @Test fun batteryScaleIs3S() {
        assertEquals(0.0, Telemetry(voltage = 9.6).batteryFraction!!, 1e-9)
        assertEquals(1.0, Telemetry(voltage = 12.6).batteryFraction!!, 1e-9)
        assertEquals(0.5, Telemetry(voltage = 11.1).batteryFraction!!, 1e-9)
        assertEquals(0.0, Telemetry(voltage = 8.0).batteryFraction!!, 1e-9)
        assertEquals(1.0, Telemetry(voltage = 13.0).batteryFraction!!, 1e-9)
        assertEquals(50, Telemetry(voltage = 11.1).batteryPercent)
        assertNull(Telemetry().batteryFraction)
        assertNull(Telemetry(voltage = 0.5).batteryFraction)
    }

    @Test fun lowBatteryBelowTenVolts() {
        assertTrue(Telemetry(voltage = 9.9).batteryLow)
        assertFalse(Telemetry(voltage = 10.0).batteryLow)
        assertFalse(Telemetry(voltage = 0.0).batteryLow)
        assertFalse(Telemetry().batteryLow)
    }
}
