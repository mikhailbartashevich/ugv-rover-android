package io.github.mikhailbartashevich.ugvcontrol.ui.theme

import androidx.compose.foundation.border
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.HazeColorEffect
import dev.chrisbanes.haze.blur.hazeBlur

/** The backdrop (live video) that glass surfaces blur. Provided by the screen. */
val LocalGlassBackdrop = staticCompositionLocalOf<HazeState?> { null }

/** Liquid-glass look: frosted blur of whatever is behind, a tint, a light-catching rim and a top sheen. */
object Glass {
    val Clear = Color.White.copy(alpha = 0.06f)
    val Smoke = Color(0xFF0B0F14).copy(alpha = 0.32f)

    /** Rim: bright where light hits the top-left, faint at the sides, a soft reflection bottom-right. */
    val rim = Brush.linearGradient(
        0f to Color.White.copy(alpha = 0.55f),
        0.35f to Color.White.copy(alpha = 0.10f),
        0.7f to Color.White.copy(alpha = 0.06f),
        1f to Color.White.copy(alpha = 0.28f),
        start = Offset.Zero, end = Offset.Infinite,
    )
}

/**
 * Turns any layout into a liquid-glass surface of [shape].
 * @param tint colour mixed into the frosted backdrop (e.g. red for STOP, accent when active).
 */
@Composable
fun Modifier.glass(
    shape: Shape,
    tint: Color = Glass.Smoke,
    blur: Dp = 22.dp,
    elevation: Dp = 10.dp,
    rimWidth: Dp = 1.dp,
): Modifier {
    val backdrop = LocalGlassBackdrop.current
    val style = remember(tint, blur) {
        HazeBlurStyle {
            blurRadius(blur)
            noiseFactor(0.04f)
            colorEffects(listOf(HazeColorEffect.tint(tint)))
            fallbackColorEffect(HazeColorEffect.tint(tint.copy(alpha = (tint.alpha + 0.45f).coerceAtMost(0.9f))))
        }
    }
    return this
        .dropShadow(shape, Shadow(radius = elevation, color = Color.Black.copy(alpha = 0.35f), offset = DpOffset(0.dp, elevation / 3)))
        .clip(shape)
        .then(if (backdrop != null) Modifier.hazeBlur(HazeInput.Sources(backdrop), style) else Modifier)
        .drawWithContent {
            drawContent()
            // Sheen across the top half, as if lit from above.
            drawRect(
                Brush.verticalGradient(
                    0f to Color.White.copy(alpha = 0.16f),
                    0.45f to Color.White.copy(alpha = 0.03f),
                    1f to Color.Transparent,
                ),
            )
        }
        .border(rimWidth, Glass.rim, shape)
}

/** Draws a glossy glass ball: coloured body, darker rim, bright highlight up-left, soft bottom glow. */
fun DrawScope.drawGlossyBall(color: Color, center: Offset, radius: Float, alpha: Float = 1f) {
    drawCircle(
        Brush.radialGradient(
            0f to color.copy(alpha = 0.95f * alpha),
            0.75f to color.copy(alpha = 0.75f * alpha),
            1f to Color.Black.copy(alpha = 0.45f * alpha),
            center = center, radius = radius,
        ),
        radius, center,
    )
    // Specular highlight.
    val hl = center + Offset(-radius * 0.3f, -radius * 0.38f)
    drawCircle(
        Brush.radialGradient(
            0f to Color.White.copy(alpha = 0.75f * alpha),
            1f to Color.White.copy(alpha = 0f),
            center = hl, radius = radius * 0.55f,
        ),
        radius * 0.55f, hl,
    )
    // Light bouncing up from below.
    val glow = center + Offset(0f, radius * 0.55f)
    drawCircle(
        Brush.radialGradient(
            0f to Color.White.copy(alpha = 0.22f * alpha),
            1f to Color.White.copy(alpha = 0f),
            center = glow, radius = radius * 0.5f,
        ),
        radius * 0.5f, glow,
    )
    drawCircle(Color.White.copy(alpha = 0.45f * alpha), radius, center, style = Stroke(radius * 0.04f))
}
