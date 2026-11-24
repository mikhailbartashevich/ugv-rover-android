package io.github.mikhailbartashevich.ugvcontrol.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.mikhailbartashevich.roverkit.protocol.Telemetry
import io.github.mikhailbartashevich.ugvcontrol.R
import io.github.mikhailbartashevich.ugvcontrol.ui.theme.RoverColors
import java.util.Locale

/** Battery, CPU, temperature, Wi‑Fi and video fps, laid out to sit inside a glass bar. */
@Composable
fun TelemetryReadouts(t: Telemetry, videoFps: Double, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        val v = t.voltage
        val pct = t.batteryPercent
        val battColor = when {
            v == null -> Color.White.copy(alpha = 0.6f)
            t.batteryLow -> RoverColors.Danger
            (pct ?: 100) < 35 -> RoverColors.Warn
            else -> RoverColors.Ok
        }
        val battIcon = when {
            pct == null -> R.drawable.ic_battery_unknown
            t.batteryLow -> R.drawable.ic_battery_alert
            pct >= 85 -> R.drawable.ic_battery_full
            pct >= 50 -> R.drawable.ic_battery_5_bar
            else -> R.drawable.ic_battery_3_bar
        }
        val battText = if (v == null) "— V" else
            String.format(Locale.US, "%.2f V  %d%%", v, pct ?: 0) + if (t.batteryLow) "  LOW" else ""
        Readout(battIcon, battText, battColor,
            "Battery: $battText. 3S pack, 9.6 V empty to 12.6 V full; LOW below 10 V, charge soon.")
        Divider()
        Readout(R.drawable.ic_memory, t.cpu?.let { "${it.toInt()}%" } ?: "—", Color.White, "Rover CPU load")
        val temp = t.temp
        Readout(R.drawable.ic_thermostat, temp?.let { "${it.toInt()}°" } ?: "—",
            if (temp != null && temp >= 70) RoverColors.Warn else Color.White,
            "Raspberry Pi temperature; above 70 °C it may throttle")
        val rssi = t.rssi
        Readout(R.drawable.ic_wifi, rssi?.let { "${it.toInt()} dBm" } ?: "—",
            if (rssi != null && rssi < -75) RoverColors.Warn else Color.White,
            "Rover Wi‑Fi signal (RSSI). Below −75 dBm control may lag, so drive closer")
        Readout(R.drawable.ic_videocam, if (videoFps > 0) "${videoFps.toInt()} fps" else "—", Color.White,
            "Video frames per second arriving on this phone")
    }
}

@Composable
private fun Divider() = Box(Modifier.width(1.dp).height(16.dp).background(Color.White.copy(alpha = 0.18f)))

@Composable
private fun Readout(@DrawableRes icon: Int, text: String, tint: Color, hint: String) {
    Hinted(hint) {
        Row(Modifier.semantics { contentDescription = hint }.padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(painterResource(icon), null, tint = tint, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text(text, color = tint, fontSize = 12.sp, fontWeight = FontWeight.Medium)
        }
    }
}
