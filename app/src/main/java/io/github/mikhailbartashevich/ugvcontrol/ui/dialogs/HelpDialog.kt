package io.github.mikhailbartashevich.ugvcontrol.ui.dialogs

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.foundation.shape.RoundedCornerShape
import io.github.mikhailbartashevich.ugvcontrol.ui.theme.RoverColors
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val sections = listOf(
    "Driving" to listOf(
        "Left thumb: touch anywhere on the left half — the stick appears under your thumb.",
        "Push up to drive forward, down to reverse, sideways to turn; the further you push, the faster.",
        "Let go and the rover stops. A tick in your fingers means you hit full deflection.",
        "Slow / Mid / Fast caps the top speed at 0.39 / 0.86 / 1.3 m/s.",
        "The red STOP button halts everything at once (it also goes out over HTTP as a backup).",
        "Leaving the app stops the rover.",
    ),
    "Camera" to listOf(
        "Right thumb: touch anywhere on the right half and drag to pan and tilt. The camera stays where you leave it.",
        "Double-tap the right half, or tap Ahead, to look straight forward.",
        "Zoom cycles 1× → 2× → 4×. Photo and Record save to the rover's storage.",
    ),
    "Lights" to listOf(
        "Head: Off → Auto (switches on in the dark) → On.",
        "Base: the chassis light on/off.",
    ),
    "Gamepad (Bluetooth or USB)" to listOf(
        "Left stick / D-pad drive · right stick aims the camera.",
        "A photo · B STOP · X head light · Y record.",
        "L1 / R1 slower / faster · right-stick click look ahead · Select base light · Start zoom.",
    ),
    "Status bar" to listOf(
        "Battery: voltage and charge of the 3S pack (9.6 V empty … 12.6 V full). LOW below 10 V.",
        "CPU, temperature and Wi‑Fi signal of the rover; video fps on this phone.",
        "Long-press any control to see what it does.",
    ),
)

@Composable
fun HelpDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = RoverColors.DialogGlass,
        shape = RoundedCornerShape(30.dp),
        title = { Text("How to drive") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                sections.forEach { (title, lines) ->
                    Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    lines.forEach { Text("• $it", fontSize = 13.sp) }
                    Spacer(Modifier.height(10.dp))
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Got it") } },
    )
}
