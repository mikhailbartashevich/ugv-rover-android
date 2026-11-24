package io.github.mikhailbartashevich.ugvcontrol.control

import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import io.github.mikhailbartashevich.roverkit.control.DriveMath
import io.github.mikhailbartashevich.roverkit.control.RoverController

/**
 * Bluetooth/USB gamepad mapping:
 *  - left stick or D-pad: drive · right stick: camera
 *  - A: photo · B: STOP · X: head light · Y: record
 *  - L1 / R1: slower / faster · right-stick click: look ahead
 *  - Select: chassis light · Start: zoom
 */
class GamepadInput(private val rover: RoverController) {
    private var driving = false
    private var looking = false

    /** Returns true if the event was a gamepad stick/D-pad move it handled. */
    fun onMotion(e: MotionEvent): Boolean {
        val fromPad = e.isFromSource(InputDevice.SOURCE_JOYSTICK) || e.isFromSource(InputDevice.SOURCE_GAMEPAD)
        if (!fromPad || e.action != MotionEvent.ACTION_MOVE) return false

        var lx = e.getAxisValue(MotionEvent.AXIS_X).toDouble()
        var ly = -e.getAxisValue(MotionEvent.AXIS_Y).toDouble()
        val hx = e.getAxisValue(MotionEvent.AXIS_HAT_X).toDouble()
        val hy = -e.getAxisValue(MotionEvent.AXIS_HAT_Y).toDouble()
        if (DriveMath.shape(lx) == 0.0 && DriveMath.shape(ly) == 0.0) { lx = hx; ly = hy }
        val drive = DriveMath.shape(lx) != 0.0 || DriveMath.shape(ly) != 0.0
        if (drive || driving) rover.drive(lx, ly)
        driving = drive

        val rx = e.getAxisValue(MotionEvent.AXIS_Z).toDouble()
        val ry = -e.getAxisValue(MotionEvent.AXIS_RZ).toDouble()
        val look = DriveMath.shape(rx) != 0.0 || DriveMath.shape(ry) != 0.0
        if (look || looking) rover.look(rx, ry)
        looking = look
        return true
    }

    /** Returns true if the key is a gamepad button it handled. */
    fun onKey(e: KeyEvent): Boolean {
        if (!KeyEvent.isGamepadButton(e.keyCode)) return false
        if (e.action != KeyEvent.ACTION_DOWN || e.repeatCount > 0) return true
        when (e.keyCode) {
            KeyEvent.KEYCODE_BUTTON_A -> rover.capturePhoto()
            KeyEvent.KEYCODE_BUTTON_B -> rover.emergencyStop()
            KeyEvent.KEYCODE_BUTTON_X -> rover.cycleHeadLight()
            KeyEvent.KEYCODE_BUTTON_Y -> rover.toggleRecording()
            KeyEvent.KEYCODE_BUTTON_L1 -> rover.setSpeed(rover.speed.value.slower)
            KeyEvent.KEYCODE_BUTTON_R1 -> rover.setSpeed(rover.speed.value.faster)
            KeyEvent.KEYCODE_BUTTON_THUMBR -> rover.lookAhead()
            KeyEvent.KEYCODE_BUTTON_SELECT -> rover.toggleChassisLight()
            KeyEvent.KEYCODE_BUTTON_START -> rover.cycleZoom()
            else -> return false
        }
        return true
    }
}
