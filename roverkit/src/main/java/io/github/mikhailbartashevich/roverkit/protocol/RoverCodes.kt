package io.github.mikhailbartashevich.roverkit.protocol

/**
 * A command on the rover's Socket.IO `/ctrl` channel: `{"cmd": X, "mode": Y}`, the way the
 * stock web UI's `cmdSend(cmd, mode)` sends it (ugv_rpi refactor branch). `cmd` picks the
 * action and `mode` is its argument; both are codes from ugv_rpi's config.yaml.
 * Mirrors `CtrlCommand` in the macOS app.
 */
data class CtrlCommand(val cmd: Int, val mode: Int = 0) {
    val json: String get() = "{\"cmd\":$cmd,\"mode\":$mode}"

    companion object {
        val CAPTURE_PHOTO = CtrlCommand(RoverCode.CAPTURE_PHOTO)
        fun recording(on: Boolean) = CtrlCommand(RoverCode.VIDEO, if (on) 1 else 0)
        fun zoom(z: CameraZoom) = CtrlCommand(RoverCode.ZOOM, z.factor)
        fun headLight(l: HeadLight) = CtrlCommand(RoverCode.HEAD_LIGHT, l.code)
        fun movementLocked(locked: Boolean) =
            CtrlCommand(RoverCode.MOVEMENT_LOCK, if (locked) RoverCode.LOCK_ON else RoverCode.LOCK_OFF)
        /** Chassis lights: `mode` is the PWM value, 0…255. */
        fun chassisLight(pwm: Int) = CtrlCommand(RoverCode.CHASSIS_LIGHT, pwm.coerceIn(0, 255))
    }
}

/** Codes from ugv_rpi's config.yaml. Feedback keys double as the command that sets them. */
object RoverCode {
    // Commands (`cmd`).
    const val DETECT_TYPE = 101      // fb.detect_type, mode = vision mode 10301…10309
    const val HEAD_LIGHT = 102       // fb.led_mode, mode = 10404 off / 10405 auto / 10406 on
    const val DETECT_REACT = 103     // fb.detect_react, mode = 10401…10403
    const val MOVEMENT_LOCK = 114    // fb.cv_movtion_mode, mode = LOCK_ON / LOCK_OFF
    const val CHASSIS_LIGHT = 115    // fb.base_light, mode = PWM 0…255
    const val ZOOM = 10104           // mode = 1, 2 or 4
    const val CAPTURE_PHOTO = 10201
    const val VIDEO = 10203          // mode = 1 start, 0 stop
    // Arguments (`mode`).
    const val LOCK_ON = 10501
    const val LOCK_OFF = 10502
}

/** Head light mode. `code` is the `mode` sent with cmd 102; the rover reports the index (0/1/2) as telemetry key 102. */
enum class HeadLight(val index: Int, val code: Int, val title: String) {
    OFF(0, 10404, "Off"),
    AUTO(1, 10405, "Auto"),
    ON(2, 10406, "On");

    /** Off → Auto → On → Off, for a single cycling button. */
    val next: HeadLight get() = entries[(ordinal + 1) % entries.size]

    companion object {
        fun fromIndex(i: Int): HeadLight? = entries.firstOrNull { it.index == i }
    }
}

/** Digital zoom of the camera stream, done on the rover; `factor` is the `mode` sent with cmd 10104. */
enum class CameraZoom(val factor: Int) {
    X1(1),
    X2(2),
    X4(4);

    val title: String get() = "$factor×"
    val next: CameraZoom get() = entries[(ordinal + 1) % entries.size]
}

/** Speed presets, matching the web UI's Slow / Middle / Fast (min/mid/max_rate). */
enum class SpeedPreset(val rate: Double, val title: String) {
    SLOW(0.3, "Slow"),
    MIDDLE(0.66, "Middle"),
    FAST(1.0, "Fast");

    val slower: SpeedPreset get() = entries[maxOf(0, ordinal - 1)]
    val faster: SpeedPreset get() = entries[minOf(entries.size - 1, ordinal + 1)]
}

object RoverLimits {
    /** m/s at 100 % rate (config.yaml `max_speed`). */
    const val MAX_WHEEL_SPEED = 1.3
    /** rad/s per unit of stick at 100 % rate — same factor the macOS app's gamepad uses. */
    const val MAX_TURN_RATE = 2.5
    val PAN_RANGE = -180.0..180.0
    val TILT_RANGE = -30.0..90.0
}
