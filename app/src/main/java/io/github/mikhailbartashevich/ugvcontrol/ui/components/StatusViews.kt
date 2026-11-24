package io.github.mikhailbartashevich.ugvcontrol.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.mikhailbartashevich.roverkit.net.RoverSocket
import io.github.mikhailbartashevich.roverkit.protocol.RoverAddress
import io.github.mikhailbartashevich.ugvcontrol.ui.theme.RoverColors
import io.github.mikhailbartashevich.ugvcontrol.ui.theme.glass

/** Cross-hair in the middle of the video, with a dark halo so it reads on bright frames. */
@Composable
fun Reticle(modifier: Modifier = Modifier) {
    Canvas(modifier.size(56.dp).semantics { contentDescription = "Centre reticle" }) {
        val c = Offset(size.width / 2, size.height / 2)
        val gap = size.width * 0.14f
        val len = size.width * 0.5f
        for ((col, w) in listOf(Color.Black.copy(alpha = 0.35f) to 3.5.dp.toPx(), Color.White.copy(alpha = 0.85f) to 1.5.dp.toPx())) {
            drawLine(col, Offset(c.x - len, c.y), Offset(c.x - gap, c.y), w)
            drawLine(col, Offset(c.x + gap, c.y), Offset(c.x + len, c.y), w)
            drawLine(col, Offset(c.x, c.y - len), Offset(c.x, c.y - gap), w)
            drawLine(col, Offset(c.x, c.y + gap), Offset(c.x, c.y + len), w)
        }
        drawCircle(Color.White.copy(alpha = 0.9f), 2.dp.toPx(), c)
    }
}

/** Status dot (with a soft glow) + address; tap for connection settings. Sits inside the top glass bar. */
@Composable
fun ConnectionStatus(
    state: RoverSocket.State, reachable: Boolean?, searching: Boolean, address: String, onClick: () -> Unit,
) {
    val (color, text) = when {
        searching -> RoverColors.Warn to "Searching…"
        state == RoverSocket.State.CONNECTED -> RoverColors.Ok to address
        state == RoverSocket.State.CONNECTING -> RoverColors.Warn to "Connecting…"
        reachable == false -> RoverColors.Danger to "Offline"
        else -> Color.Gray to "Not connected"
    }
    Hinted("Connection: $text. Tap to change the rover address or search for it.") {
        Row(
            Modifier.clip(CircleShape)
                .clickable(role = Role.Button, onClickLabel = "Open connection settings", onClick = onClick)
                .semantics { contentDescription = "Connection status: $text. Tap for connection settings." }
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (searching) CircularProgressIndicator(Modifier.size(10.dp), strokeWidth = 2.dp, color = color)
            else Box(
                Modifier.size(9.dp)
                    .dropShadow(CircleShape, Shadow(radius = 6.dp, color = color.copy(alpha = 0.9f)))
                    .background(color, CircleShape),
            )
            Spacer(Modifier.width(7.dp))
            Text(text, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

/** Compact glass card shown while there's no connection; sits between the top controls and the bottom ones. */
@Composable
fun OfflineCard(
    address: String, searching: Boolean, reachable: Boolean?, localNetworkAllowed: Boolean,
    onGrant: () -> Unit, onFind: () -> Unit, onSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier.width(440.dp).glass(RoundedCornerShape(26.dp), elevation = 18.dp).padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            when {
                !localNetworkAllowed -> "Local network access needed"
                searching -> "Looking for the rover…"
                reachable == false -> "Rover not reachable"
                else -> "Connecting…"
            },
            color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            if (!localNetworkAllowed) "Android only lets the app reach the rover with the Nearby devices permission."
            else "$address · join the rover's Wi‑Fi (${RoverAddress.HOTSPOT_HOST})",
            color = Color.White.copy(alpha = 0.72f), fontSize = 12.sp, maxLines = 1,
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (!localNetworkAllowed) Button(
                onClick = onGrant,
                colors = ButtonDefaults.buttonColors(containerColor = RoverColors.Accent.copy(alpha = 0.85f)),
                modifier = Modifier.semantics { contentDescription = "Grant local network access so the app can reach the rover" },
            ) { Text("Grant access") } else Button(
                onClick = onFind, enabled = !searching,
                colors = ButtonDefaults.buttonColors(containerColor = RoverColors.Accent.copy(alpha = 0.85f)),
                modifier = Modifier.semantics {
                    contentDescription = "Find rover: try the saved address, the rover hotspot, then scan this Wi‑Fi network"
                },
            ) { Text(if (searching) "Searching…" else "Find rover") }
            TextButton(
                onClick = onSettings,
                modifier = Modifier.semantics { contentDescription = "Enter the rover address by hand" },
            ) { Text("Enter address", color = Color.White) }
        }
    }
}
