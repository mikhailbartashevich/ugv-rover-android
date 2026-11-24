package io.github.mikhailbartashevich.ugvcontrol.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.mikhailbartashevich.ugvcontrol.ui.theme.drawGlossyBall
import io.github.mikhailbartashevich.ugvcontrol.ui.theme.glass
import kotlin.math.hypot
import kotlin.math.roundToInt

/**
 * A floating analog stick: touch anywhere in this zone and the stick appears under
 * your thumb, so you never have to look for it. Dragging past the rim drags the base
 * along. Reports x right +, y up +, each −1…1. Double-tap calls [onDoubleTap].
 *
 * The base is a liquid-glass disc over the video; the knob is a glossy ball in [color].
 *
 * @param restAnchor where the idle stick rests, as fractions of the zone.
 */
@Composable
fun FloatingStick(
    label: String,
    hint: String,
    description: String,
    color: Color,
    onMove: (Double, Double) -> Unit,
    onRelease: () -> Unit,
    modifier: Modifier = Modifier,
    radius: Dp = 70.dp,
    restAnchor: Offset = Offset(0.5f, 0.6f),
    onDoubleTap: (() -> Unit)? = null,
) {
    val haptics = rememberHaptics()
    val move by rememberUpdatedState(onMove)
    val release by rememberUpdatedState(onRelease)
    val doubleTap by rememberUpdatedState(onDoubleTap)
    val density = LocalDensity.current
    val rPx = with(density) { radius.toPx() }

    var base by remember { mutableStateOf<Offset?>(null) }
    var knob by remember { mutableStateOf(Offset.Zero) }
    val active = base != null
    val presence by animateFloatAsState(if (active) 1f else 0f, spring(dampingRatio = 0.6f, stiffness = 500f))
    // The knob springs back to the centre on release instead of jumping.
    val knobX by animateFloatAsState(knob.x, if (active) spring(stiffness = 10_000f) else spring(dampingRatio = 0.45f))
    val knobY by animateFloatAsState(knob.y, if (active) spring(stiffness = 10_000f) else spring(dampingRatio = 0.45f))

    BoxWithConstraints(
        modifier
            .semantics { contentDescription = description }
            .pointerInput(rPx) {
                var lastUpTime = 0L
                var lastTapWasShort = false
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val downTime = down.uptimeMillis
                    if (lastTapWasShort && downTime - lastUpTime < 300) doubleTap?.invoke()
                    var origin = down.position
                    base = origin
                    knob = Offset.Zero
                    haptics.grab()
                    var atRim = false
                    var maxTravel = 0f
                    while (true) {
                        val ev = awaitPointerEvent()
                        val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                        if (!ch.pressed) { lastUpTime = ch.uptimeMillis; break }
                        var d = ch.position - origin
                        val len = hypot(d.x, d.y)
                        maxTravel = maxOf(maxTravel, len)
                        if (len > rPx) {
                            // Drag the base along so the thumb never "falls off" the stick.
                            val excess = d * ((len - rPx) / len)
                            origin += excess
                            d -= excess
                            base = origin
                        }
                        knob = d
                        val rim = hypot(d.x, d.y) >= rPx * 0.98f
                        if (rim && !atRim) haptics.rim()
                        atRim = rim
                        move((d.x / rPx).toDouble(), (-d.y / rPx).toDouble())
                        ch.consume()
                    }
                    lastTapWasShort = maxTravel < rPx * 0.15f && (lastUpTime - downTime) < 250
                    base = null
                    knob = Offset.Zero
                    haptics.release()
                    release()
                }
            },
    ) {
        val rest = Offset(constraints.maxWidth * restAnchor.x, constraints.maxHeight * restAnchor.y)
        val center = base ?: rest
        val diameter = radius * 2
        val topLeft = IntOffset((center.x - rPx).roundToInt(), (center.y - rPx).roundToInt())

        // Label above the resting stick.
        Column(
            Modifier
                .offset { IntOffset((rest.x - with(density) { 100.dp.toPx() }).roundToInt(), (rest.y - rPx - with(density) { 44.dp.toPx() }).roundToInt()) }
                .width(200.dp)
                .alpha(1f - presence),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(label, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, letterSpacing = 2.sp,
                textAlign = TextAlign.Center)
            Text(hint, color = Color.White.copy(alpha = 0.7f), fontSize = 11.sp, textAlign = TextAlign.Center)
        }

        // Glass base.
        Box(
            Modifier
                .offset { topLeft }
                .size(diameter)
                .scale(0.92f + 0.08f * presence)
                .glass(CircleShape, tint = color.copy(alpha = 0.06f + 0.10f * presence), elevation = 14.dp),
        )
        // Guide ring, cross-hair and the knob.
        Canvas(Modifier.fillMaxSize()) {
            drawCircle(color.copy(alpha = 0.25f + 0.55f * presence), rPx * 0.98f, center, style = Stroke(1.5.dp.toPx()))
            val tick = rPx * 0.12f
            val guide = Color.White.copy(alpha = 0.25f)
            for (a in listOf(Offset(1f, 0f), Offset(0f, 1f))) {
                drawLine(guide, center - a * (rPx * 0.85f), center - a * (rPx * 0.85f - tick), 1.5f)
                drawLine(guide, center + a * (rPx * 0.85f), center + a * (rPx * 0.85f - tick), 1.5f)
            }
            val k = center + Offset(knobX, knobY)
            drawCircle(Color.Black.copy(alpha = 0.3f), rPx * 0.42f, k + Offset(0f, 6f))
            drawGlossyBall(color, k, rPx * 0.40f, alpha = 0.55f + 0.45f * presence)
        }
    }
}
