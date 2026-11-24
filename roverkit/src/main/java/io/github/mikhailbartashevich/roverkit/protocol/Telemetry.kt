package io.github.mikhailbartashevich.roverkit.protocol

import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull

/**
 * Live data the rover's host program pushes over Socket.IO (`update` on /ctrl).
 * Keys are the numeric `fb` codes from ugv_rpi's config.yaml. Mirrors `Telemetry.swift`.
 */
data class Telemetry(
    val cpu: Double? = null,
    val ram: Double? = null,
    val temp: Double? = null,
    val rssi: Double? = null,
    val fps: Double? = null,
    /** Volts (the rover sends volts × 100 under key 112). */
    val voltage: Double? = null,
    val pan: Double? = null,
    val tilt: Double? = null,
    val headLight: HeadLight? = null,
    val chassisLightOn: Boolean? = null,
) {
    /** 3S Li-ion pack: ~9.6 V empty … 12.6 V full. */
    val batteryFraction: Double?
        get() {
            val v = voltage ?: return null
            if (v <= 1) return null
            return ((v - EMPTY_V) / (FULL_V - EMPTY_V)).coerceIn(0.0, 1.0)
        }

    val batteryPercent: Int? get() = batteryFraction?.let { Math.round(it * 100).toInt() }

    val batteryLow: Boolean
        get() {
            val v = voltage ?: return false
            return v > 1 && v < LOW_V
        }

    /**
     * Merges an update. Returns null when the update must be ignored: the host
     * sends zeros (112 == 0) until the chassis reports in; the web UI ignores those too.
     */
    fun merged(update: Map<String, JsonPrimitive>): Telemetry? {
        fun num(k: Int) = update[k.toString()]?.let { p -> if (p.isString) p.content.toDoubleOrNull() else p.doubleOrNull }
        if (num(KEY_VOLTAGE) == 0.0) return null
        return copy(
            cpu = num(106) ?: cpu,
            temp = num(107) ?: temp,
            ram = num(108) ?: ram,
            rssi = num(111) ?: rssi,
            fps = num(113) ?: fps,
            voltage = num(KEY_VOLTAGE)?.let { it / 100 } ?: voltage,
            pan = num(109) ?: pan,
            tilt = num(110) ?: tilt,
            headLight = num(102)?.let { HeadLight.fromIndex(it.toInt()) } ?: headLight,
            chassisLightOn = (update["115"]?.let { it.booleanOrNull ?: it.doubleOrNull?.let { d -> d != 0.0 } })
                ?: chassisLightOn,
        )
    }

    companion object {
        const val KEY_VOLTAGE = 112
        const val EMPTY_V = 9.6
        const val FULL_V = 12.6
        const val LOW_V = 10.0
    }
}
