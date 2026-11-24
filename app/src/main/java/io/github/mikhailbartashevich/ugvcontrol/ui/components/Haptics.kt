package io.github.mikhailbartashevich.ugvcontrol.ui.components

import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView

/** Haptic feedback through the view, so it follows the system's touch-feedback setting. */
class Haptics(private val view: View) {
    private fun play(constant: Int) = view.performHapticFeedback(constant)

    /** Thumb lands on a stick. */
    fun grab() = play(HapticFeedbackConstants.GESTURE_START)
    /** Thumb lifts off a stick. */
    fun release() = play(HapticFeedbackConstants.GESTURE_END)
    /** Stick reaches full deflection. */
    fun rim() = play(HapticFeedbackConstants.GESTURE_THRESHOLD_ACTIVATE)
    /** Ordinary button. */
    fun click() = play(HapticFeedbackConstants.VIRTUAL_KEY)
    fun toggle(on: Boolean) = play(if (on) HapticFeedbackConstants.TOGGLE_ON else HapticFeedbackConstants.TOGGLE_OFF)
    fun confirm() = play(HapticFeedbackConstants.CONFIRM)
    /** Strong buzz for STOP. */
    fun stop() = play(HapticFeedbackConstants.REJECT)
}

@Composable
fun rememberHaptics(): Haptics {
    val view = LocalView.current
    return remember(view) { Haptics(view) }
}
