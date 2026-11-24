package io.github.mikhailbartashevich.roverkit.protocol

import java.util.Locale
import kotlin.math.abs
import kotlin.math.round

/**
 * A JSON command for the ESP32 sub-controller (`{"T": type, …}`).
 * Mirrors `ESP32Command.swift`: "T" first, integral numbers without decimals,
 * everything else with two decimals.
 */
data class Esp32Command(val type: Int, val fields: List<Pair<String, Value>> = emptyList()) {

    sealed interface Value {
        data class IntV(val v: Int) : Value
        data class DoubleV(val v: Double) : Value
        data class StringV(val v: String) : Value
    }

    /** A wheel command that actually moves (T:1 / T:11 / T:13 with a non-zero value). */
    val isMotion: Boolean
        get() = type in setOf(1, 11, 13) && fields.any { (_, v) ->
            when (v) {
                is Value.IntV -> v.v != 0
                is Value.DoubleV -> abs(v.v) > 0.001
                is Value.StringV -> false
            }
        }

    /** Compact JSON with "T" first and short decimals. */
    val json: String
        get() = (listOf("T" to Value.IntV(type)) + fields)
            .joinToString(",", "{", "}") { (k, v) -> "\"$k\":${encode(v)}" }

    companion object {
        /** T:1 — closed-loop wheel speeds in m/s. */
        fun wheels(left: Double, right: Double) =
            Esp32Command(1, listOf("L" to Value.DoubleV(left), "R" to Value.DoubleV(right)))

        /** T:13 — linear (m/s) and angular (rad/s, left positive) velocity. */
        fun velocity(linear: Double, angular: Double) =
            Esp32Command(13, listOf("X" to Value.DoubleV(linear), "Z" to Value.DoubleV(angular)))

        val STOP = wheels(0.0, 0.0)

        /** T:133 — move the pan-tilt to an absolute angle. */
        fun gimbal(pan: Double, tilt: Double) = Esp32Command(
            133,
            listOf(
                "X" to Value.DoubleV(pan),
                "Y" to Value.DoubleV(tilt),
                "SPD" to Value.IntV(0),
                "ACC" to Value.IntV(128),
            ),
        )

        internal fun encode(v: Value): String = when (v) {
            is Value.IntV -> v.v.toString()
            is Value.DoubleV -> {
                val d = v.v
                when {
                    d == round(d) -> d.toLong().toString()
                    else -> {
                        val s = String.format(Locale.US, "%.2f", d)
                        if (s == "-0.00") "0.00" else s
                    }
                }
            }
            is Value.StringV -> "\"" + v.v.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
        }
    }
}
