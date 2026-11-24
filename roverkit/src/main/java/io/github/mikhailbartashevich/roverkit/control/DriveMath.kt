package io.github.mikhailbartashevich.roverkit.control

import io.github.mikhailbartashevich.roverkit.protocol.Esp32Command
import io.github.mikhailbartashevich.roverkit.protocol.RoverLimits
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.sign

/**
 * Turns stick positions into rover commands. Sticks use screen-style axes:
 * x −1 (left) … 1 (right), y −1 (back/down) … 1 (forward/up).
 */
object DriveMath {
    const val DEAD_ZONE = 0.08
    /** Camera rate at full deflection, degrees per second (same as the Mac app's gamepad: 6°/4° per 0.1 s). */
    const val PAN_DEG_PER_S = 60.0
    const val TILT_DEG_PER_S = 40.0

    /**
     * Dead zone plus a gentle expo curve: fine control near the centre, full power at the rim.
     * Keeps the sign; output is −1…1.
     */
    fun shape(v: Double, deadZone: Double = DEAD_ZONE, expo: Double = 0.35): Double {
        val a = abs(v).coerceAtMost(1.0)
        if (a < deadZone) return 0.0
        val t = (a - deadZone) / (1 - deadZone)
        return sign(v) * ((1 - expo) * t + expo * t * t * t)
    }

    /** Clamps a stick vector to the unit circle. */
    fun clampToCircle(x: Double, y: Double): Pair<Double, Double> {
        val len = hypot(x, y)
        return if (len <= 1.0) x to y else (x / len) to (y / len)
    }

    /**
     * T:13 for a drive stick: forward = y, turn right = x (the rover's angular Z is
     * left-positive, so it's negated). Scaled by the speed preset's rate.
     */
    fun velocity(x: Double, y: Double, rate: Double): Esp32Command {
        val linear = shape(y) * RoverLimits.MAX_WHEEL_SPEED * rate
        val angular = -shape(x) * RoverLimits.MAX_TURN_RATE * rate
        return Esp32Command.velocity(linear, angular)
    }

    /** New (pan, tilt) after holding the camera stick at (x, y) for [dtSeconds]. */
    fun gimbalStep(pan: Double, tilt: Double, x: Double, y: Double, dtSeconds: Double): Pair<Double, Double> {
        val p = (pan + shape(x) * PAN_DEG_PER_S * dtSeconds).coerceIn(RoverLimits.PAN_RANGE)
        val t = (tilt + shape(y) * TILT_DEG_PER_S * dtSeconds).coerceIn(RoverLimits.TILT_RANGE)
        return p to t
    }
}
