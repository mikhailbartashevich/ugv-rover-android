package io.github.mikhailbartashevich.ugvcontrol.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.mikhailbartashevich.roverkit.protocol.RoverLimits
import io.github.mikhailbartashevich.roverkit.protocol.SpeedPreset
import io.github.mikhailbartashevich.ugvcontrol.R
import io.github.mikhailbartashevich.ugvcontrol.ui.theme.RoverColors
import io.github.mikhailbartashevich.ugvcontrol.ui.theme.drawGlossyBall
import io.github.mikhailbartashevich.ugvcontrol.ui.theme.glass
import java.util.Locale

/** A glass capsule holding related buttons, like a macOS toolbar group. */
@Composable
fun GlassGroup(modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    Row(
        modifier.glass(RoundedCornerShape(22.dp)).padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

/** Icon + label button for a [GlassGroup]; glows in [activeColor] while [active]. Shrinks a touch when pressed. */
@Composable
fun GroupButton(
    @DrawableRes icon: Int, label: String, active: Boolean, hint: String,
    activeColor: Color = RoverColors.Accent, onClick: () -> Unit,
) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.9f else 1f, spring(dampingRatio = 0.5f, stiffness = 800f))
    val bg by animateColorAsState(if (active) activeColor.copy(alpha = 0.32f) else Color.Transparent)
    val fg by animateColorAsState(if (active) activeColor else Color.White)
    Hinted(hint) {
        Column(
            Modifier.scale(scale).widthIn(min = 58.dp).clip(RoundedCornerShape(18.dp)).background(bg)
                .clickable(source, indication = null, role = Role.Button, onClick = onClick)
                .semantics { contentDescription = "$label. $hint" }
                .padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(painterResource(icon), null, tint = fg, modifier = Modifier.size(22.dp))
            Spacer(Modifier.height(2.dp))
            Text(label, color = Color.White.copy(alpha = 0.92f), fontSize = 10.sp, fontWeight = FontWeight.Medium)
        }
    }
}

/** Round glass icon button. */
@Composable
fun RoundAction(@DrawableRes icon: Int, label: String, hint: String, onClick: () -> Unit) {
    Hinted(hint) {
        Box(
            Modifier.size(40.dp).glass(CircleShape)
                .clickable(role = Role.Button, onClick = onClick)
                .semantics { contentDescription = "$label. $hint" },
            contentAlignment = Alignment.Center,
        ) { Icon(painterResource(icon), null, tint = Color.White, modifier = Modifier.size(20.dp)) }
    }
}

/** Slow / Mid / Fast as a glass capsule with a sliding selection pill. */
@Composable
fun SpeedSelector(speed: SpeedPreset, onSelect: (SpeedPreset) -> Unit) {
    val segment = 66.dp
    val pillX by animateDpAsState(segment * speed.ordinal, spring(dampingRatio = 0.7f, stiffness = 600f))
    Box(Modifier.glass(CircleShape).padding(4.dp)) {
        Box(
            Modifier.offset(x = pillX).width(segment).height(30.dp)
                .glass(CircleShape, tint = RoverColors.Accent.copy(alpha = 0.45f), elevation = 4.dp, blur = 8.dp),
        )
        Row {
            SpeedPreset.entries.forEach { p ->
                val hint = "${p.title} speed: up to ${String.format(Locale.US, "%.2f", RoverLimits.MAX_WHEEL_SPEED * p.rate)} m/s"
                Hinted(hint) {
                    Box(
                        Modifier.width(segment).height(30.dp).clip(CircleShape)
                            .clickable(role = Role.RadioButton) { onSelect(p) }
                            .semantics { contentDescription = hint; selected = p == speed; role = Role.RadioButton },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(if (p == SpeedPreset.MIDDLE) "Mid" else p.title,
                            color = Color.White.copy(alpha = if (p == speed) 1f else 0.7f),
                            fontSize = 12.sp, fontWeight = if (p == speed) FontWeight.SemiBold else FontWeight.Normal)
                    }
                }
            }
        }
    }
}

/** The big emergency stop: a glossy red glass button whose rim lights up while driving. */
@Composable
fun StopButton(driving: Boolean, onClick: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.9f else 1f, spring(dampingRatio = 0.45f, stiffness = 900f))
    val ring by animateFloatAsState(if (driving) 1f else 0f)
    Hinted("Emergency STOP: halts the wheels and the camera at once") {
        Box(
            Modifier.size(92.dp).scale(scale)
                .glass(CircleShape, tint = RoverColors.Danger.copy(alpha = 0.55f), elevation = 16.dp, rimWidth = 1.5.dp)
                .clickable(source, indication = null, role = Role.Button, onClickLabel = "Stop the rover", onClick = onClick)
                .semantics { contentDescription = "Emergency stop. Halts the rover immediately." },
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val c = Offset(size.width / 2, size.height / 2)
                drawGlossyBall(RoverColors.Danger, c, size.minDimension * 0.36f, alpha = 0.85f)
                drawCircle(Color.White.copy(alpha = 0.25f + 0.7f * ring), size.minDimension * 0.47f, c,
                    style = androidx.compose.ui.graphics.drawscope.Stroke((1.5f + 2.5f * ring) * density))
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(painterResource(R.drawable.ic_stop), null, tint = Color.White, modifier = Modifier.size(24.dp))
                Text("STOP", color = Color.White, fontWeight = FontWeight.Black, fontSize = 13.sp, letterSpacing = 1.sp)
            }
        }
    }
}

/** Red "REC m:ss" capsule with a pulsing dot. */
@Composable
fun RecordingBadge(seconds: Long) {
    Row(
        Modifier.glass(CircleShape, tint = RoverColors.Danger.copy(alpha = 0.5f))
            .semantics { contentDescription = "Recording for $seconds seconds" }
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(8.dp).background(Color.White.copy(alpha = if (seconds % 2 == 0L) 1f else 0.45f), CircleShape))
        Spacer(Modifier.width(6.dp))
        Text(String.format(Locale.US, "REC %d:%02d", seconds / 60, seconds % 60),
            color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

/** Thin vertical separator inside a glass bar. */
@Composable
fun GlassSeparator() = Box(Modifier.width(1.dp).fillMaxHeight(0.6f).background(Color.White.copy(alpha = 0.15f)))
