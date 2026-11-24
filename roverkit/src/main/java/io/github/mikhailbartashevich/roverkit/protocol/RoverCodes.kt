package io.github.mikhailbartashevich.roverkit.protocol

/**
 * Action codes the rover's host program accepts on the Socket.IO `/ctrl` channel
 * (`{"A": code, "B": 0, "C": 0}`), as defined in ugv_rpi's config.yaml.
 * Mirrors `RoverCodes.swift` in the macOS app.
 */
object RoverCode {
    const val CAPTURE_PHOTO = 10201
    const val RECORD_START = 10202
    const val RECORD_STOP = 10203
    const val MOVEMENT_LOCK = 10501
    const val MOVEMENT_UNLOCK = 10502
    const val CHASSIS_LIGHT_OFF = 10407
    const val CHASSIS_LIGHT_ON = 10408
}

/** Head light mode. The rover reports the index (0/1/2) as telemetry key 102. */
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

/** Digital zoom of the camera stream, done on the rover. */
enum class CameraZoom(val factor: Int, val code: Int) {
    X1(1, 10104),
    X2(2, 10105),
    X4(4, 10106);

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
